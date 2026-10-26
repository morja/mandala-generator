package com.mathis.mandala.app

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.DisplayMetrics
import android.view.SurfaceHolder
import android.view.WindowManager
import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.Framing
import com.mathis.mandala.core.LayerBlendMode
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.RenderCancelledException
import com.mathis.mandala.core.RenderOptions
import com.mathis.mandala.core.SettingsJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import java.io.File
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * How the live wallpaper moves (ADR-0014); it always turns slowly. `layered`: the mandala layers turn
 * against each other while background, emblem, text and drawing stay still (UX-1, a choice to try).
 */
data class LiveSettings(
    val breathing: Boolean = false,
    val colourDrift: Boolean = false,
    val palettesInTurn: Boolean = false,
    val layered: Boolean = false,
)

/**
 * The live wallpaper (ADR-0014, Pro): the mandala is rendered once, as a square as large as the
 * screen's diagonal so no corner shows while it turns, and then only moved on the graphics chip.
 * The service runs in its own process (`:live`), so the editor's images do not take its memory; it
 * reads its mandala and settings from two files, which it checks while it is visible.
 */
object LiveWallpaper {
    fun settings(context: Context): LiveSettings {
        val words = runCatching { settingsFile(context).readText().split(',').toSet() }.getOrDefault(emptySet())
        return LiveSettings(BREATHING in words, DRIFT in words, PALETTES in words, LAYERS in words)
    }

    fun setSettings(context: Context, s: LiveSettings) {
        val words = listOfNotNull(BREATHING.takeIf { s.breathing }, DRIFT.takeIf { s.colourDrift }, PALETTES.takeIf { s.palettesInTurn },
            LAYERS.takeIf { s.layered })
        settingsFile(context).writeText(words.joinToString(","))
    }

    /** The mandala the live wallpaper shows; a change fades over to the new one. */
    fun setMandala(context: Context, params: MandalaParameters) = mandalaFile(context).writeText(SettingsJson.encode(params))

    fun mandala(context: Context): MandalaParameters? =
        runCatching { SettingsJson.decode(mandalaFile(context).readText()) }.getOrNull()

    /** Changes whenever the mandala does. */
    fun version(context: Context): Long = mandalaFile(context).lastModified()

    /** Whether the live wallpaper is the one on the phone now. */
    fun isActive(context: Context): Boolean =
        WallpaperManager.getInstance(context).wallpaperInfo?.packageName == context.packageName

    /** Android's own screen that previews the live wallpaper and sets it when the user agrees. */
    fun pickIntent(context: Context): Intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, LiveWallpaperService::class.java))

    /**
     * Renders the wallpaper's image ahead, in the app, into the cache the service reads; Android's
     * preview then shows it at once instead of black while it renders.
     */
    suspend fun prepare(context: Context, params: MandalaParameters) {
        val (w, h) = screenSize(context)
        LiveWallpaperService.scene(context, params, w, h, settings(context).layered)?.recycle()
    }

    /** The whole display in its natural orientation, as large as the wallpaper's surface. */
    private fun screenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            DisplayMetrics().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
        }
        return min(w, h) to max(w, h)
    }

    private fun mandalaFile(context: Context) = File(context.filesDir, "live-mandala.json")
    private fun settingsFile(context: Context) = File(context.filesDir, "live-settings.txt")

    private const val BREATHING = "breathing"
    private const val DRIFT = "drift"
    private const val PALETTES = "palettes"
    private const val LAYERS = "layers"
}

class LiveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = MandalaEngine()

    private inner class MandalaEngine : Engine() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private var width = 0
        private var height = 0
        private var visible = false
        private var shown: Scene? = null
        private var incoming: Scene? = null
        private var fadeStart = 0L
        private var settings = LiveSettings()
        private var paletteShift = 0
        private var lastCycle = 0L
        private var drawnVersion = -1L
        /** The version being rendered now; the check every few seconds must not restart that render. */
        private var loadingVersion = -1L
        private var loading: Job? = null
        private var lastCheck = 0L

        private val frame = object : Runnable {
            override fun run() {
                draw()
                if (visible) handler.postDelayed(this, FRAME_MS)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            settings = LiveWallpaper.settings(this@LiveWallpaperService)
        }

        /** The app writes the files from its own process; a new mandala starts over with its own palette. */
        private fun checkFiles() {
            val context = this@LiveWallpaperService
            val layeredBefore = settings.layered
            settings = LiveWallpaper.settings(context)
            val version = LiveWallpaper.version(context)
            if (settings.layered != layeredBefore) {
                load()
            } else if (version != drawnVersion && version != loadingVersion) {
                paletteShift = 0
                load()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            if (w == width && h == height) return
            width = w
            height = h
            load()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(frame)
            if (!isVisible) return
            checkFiles()
            handler.post(frame)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            scope.cancel()
            super.onDestroy()
        }

        /** Renders the mandala (in the next palette after `paletteShift` steps) and fades it in when ready. */
        private fun load() {
            if (width <= 0 || height <= 0) return
            val context = this@LiveWallpaperService
            val base = LiveWallpaper.mandala(context) ?: return
            val version = LiveWallpaper.version(context)
            val params = shifted(base, paletteShift)
            val w = width
            val h = height
            val layered = settings.layered
            loading?.cancel()
            loadingVersion = version
            loading = scope.launch {
                val self = coroutineContext.job
                val bitmap = try {
                    scene(context, params, w, h, layered)
                } catch (e: RenderCancelledException) {
                    null
                } catch (e: OutOfMemoryError) {
                    null
                }
                handler.post {
                    // Only the latest render counts; a failed one is tried again at the next check.
                    if (loading !== self) {
                        bitmap?.recycle()
                        return@post
                    }
                    loadingVersion = -1L
                    if (bitmap == null) return@post
                    drawnVersion = version
                    if (shown == null) shown = bitmap else {
                        incoming?.recycle()
                        incoming = bitmap
                        fadeStart = SystemClock.elapsedRealtime()
                    }
                }
            }
        }

        private fun draw() {
            val holder = surfaceHolder
            val now = SystemClock.elapsedRealtime()
            if (now - lastCheck > CHECK_MS) {
                lastCheck = now
                checkFiles()
            }
            if (settings.palettesInTurn && shown != null && incoming == null && now - lastCycle > CYCLE_MS) {
                if (lastCycle != 0L) {
                    paletteShift++
                    load()
                }
                lastCycle = now
            }
            val canvas: Canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: holder.lockCanvas() ?: return
            try {
                canvas.drawColor(Color.BLACK)
                val current = shown ?: return
                val seconds = now / 1000.0
                val turn = ((seconds / TURN_SECONDS * 360) % 360).toFloat()
                drawScene(canvas, current, turn, filter(seconds), 255)
                val next = incoming
                if (next != null) {
                    val t = ((now - fadeStart).toFloat() / FADE_MS).coerceIn(0f, 1f)
                    drawScene(canvas, next, turn, filter(seconds), (t * 255).toInt())
                    if (t >= 1f) {
                        shown = next
                        incoming = null
                        current.recycle()
                    }
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        /**
         * The still bottom, the turning parts (every second one the other way round) and the still top.
         * A fading-in scene goes into its own layer at `alpha`, so its blend modes act within it.
         */
        private fun drawScene(canvas: Canvas, scene: Scene, turn: Float, colour: ColorMatrixColorFilter?, alpha: Int) {
            val faded = alpha < 255
            if (faded) canvas.saveLayerAlpha(null, alpha)
            if (faded) canvas.drawColor(Color.BLACK)
            paint.colorFilter = colour
            scene.bottom?.let { paint.blendMode = BlendMode.SRC_OVER; drawCentred(canvas, it, paint) }
            for ((i, part) in scene.turning.withIndex()) {
                paint.blendMode = part.blend
                canvas.save()
                canvas.rotate(if (i % 2 == 0) turn else -turn, width / 2f, height / 2f)
                drawCentred(canvas, part.bitmap, paint)
                canvas.restore()
            }
            scene.top?.let {
                paint.blendMode = BlendMode.SCREEN
                dest.set(0f, 0f, width.toFloat(), height.toFloat())
                canvas.drawBitmap(it, null, dest, paint)
            }
            paint.blendMode = BlendMode.SRC_OVER
            if (faded) canvas.restore()
        }

        /** The bitmap, drawn smaller than the screen's diagonal, is scaled up to it on the graphics chip. */
        private fun drawCentred(canvas: Canvas, bitmap: Bitmap, p: Paint) {
            val side = hypot(width.toDouble(), height.toDouble()).toFloat()
            val left = (width - side) / 2f
            val top = (height - side) / 2f
            dest.set(left, top, left + side, top + side)
            canvas.drawBitmap(bitmap, null, dest, p)
        }

        private val dest = RectF()

        /** Breathing: brightness up and down by about a tenth; colour drift: the hue turning slowly. */
        private fun filter(seconds: Double): ColorMatrixColorFilter? {
            if (!settings.breathing && !settings.colourDrift) return null
            val m = ColorMatrix()
            if (settings.colourDrift) m.postConcat(hueRotation((seconds / DRIFT_SECONDS * 2 * PI) % (2 * PI)))
            if (settings.breathing) {
                val s = (1 + BREATH_DEPTH * sin(seconds / BREATH_SECONDS * 2 * PI)).toFloat()
                m.postConcat(ColorMatrix().apply { setScale(s, s, s, 1f) })
            }
            return ColorMatrixColorFilter(m)
        }
    }

    internal companion object {
        /** About 30 frames a second, and only while the wallpaper is visible. */
        const val FRAME_MS = 33L
        /** One full turn. */
        const val TURN_SECONDS = 240.0
        const val BREATH_SECONDS = 8.0
        const val BREATH_DEPTH = 0.1
        const val DRIFT_SECONDS = 180.0
        const val FADE_MS = 3000L
        /** The next palette every ten minutes on screen. */
        const val CYCLE_MS = 10 * 60 * 1000L
        /** How often a visible wallpaper looks for a new mandala or settings from the app. */
        const val CHECK_MS = 3000L
        /** Drawn at this share of the diagonal and scaled up: about half the memory, and it is moving. */
        const val RENDER_SCALE = 0.75
        /** Layered scenes hold up to seven pictures, so each is smaller. */
        const val LAYERED_SCALE = 0.6
        /** Rendered pictures kept in the cache: a whole layered scene and the last whole picture. */
        const val CACHE_KEPT = 9
        /** Fewer threads, less memory at once; the wallpaper is not in a hurry. */
        const val MAX_THREADS = 4

        /** All layers `steps` palettes on (own palettes give way to built-in ones); the mandala's colours change, its lines stay. */
        fun shifted(params: MandalaParameters, steps: Int): MandalaParameters {
            if (steps == 0) return params
            val n = BuiltInPalettes.all.size
            return params.copy(layers = params.layers.map { it.copy(paletteIndex = (it.paletteIndex + steps).mod(n), customPalette = null) })
        }

        /**
         * What the wallpaper draws: the whole mandala turning as one, or (`layered`) a still bottom with
         * the background, each layer on its own and a still top with drawing, emblem and text. Layered
         * leaves the scene effects out: they act on the whole picture, which never exists as one here.
         */
        suspend fun scene(context: Context, params: MandalaParameters, w: Int, h: Int, layered: Boolean): Scene? =
            renderLock.withLock {
                if (!layered) return@withLock renderNow(context, params, w, h, RENDER_SCALE)?.let { Scene(null, listOf(Turning(it, BlendMode.SRC_OVER)), null) }
                val bare = params.copy(effectsLayer = params.effectsLayer.copy(isEnabled = false))
                val nothing = bare.copy(
                    layers = bare.layers.map { it.copy(isEnabled = false) },
                    emblemLayer = bare.emblemLayer.copy(isEnabled = false),
                    textLayer = bare.textLayer.copy(isEnabled = false),
                    drawingLayer = bare.drawingLayer.copy(isEnabled = false),
                    graffitiLayer = bare.graffitiLayer.copy(isEnabled = false),
                )
                val parts = mutableListOf<Bitmap>()
                try {
                    val bottom = if (bare.baseLayer.isEnabled) renderNow(context, nothing, w, h, LAYERED_SCALE, params)?.also(parts::add) ?: return@withLock null else null
                    val turning = bare.layers.withIndex().filter { it.value.isEnabled }.map { (i, layer) ->
                        // Alone on black: drawn with "screen" it is the layer itself; its own mode acts on the chip.
                        val alone = nothing.copy(baseLayer = nothing.baseLayer.copy(isEnabled = false),
                            layers = nothing.layers.mapIndexed { j, l -> if (j == i) layer.copy(blendMode = LayerBlendMode.SCREEN) else l })
                        val bitmap = renderNow(context, alone, w, h, LAYERED_SCALE, params)?.also(parts::add) ?: return@withLock null
                        Turning(bitmap, blendOf(layer.blendMode))
                    }
                    val hasTop = bare.emblemLayer.isEnabled || (bare.textLayer.isEnabled && bare.textLayer.text.isNotBlank()) ||
                        (bare.drawingLayer.isEnabled && bare.drawingLayer.strokes.isNotEmpty()) || (bare.graffitiLayer.isEnabled && bare.graffitiLayer.strokes.isNotEmpty())
                    val top = if (hasTop) {
                        val still = bare.copy(layers = nothing.layers, baseLayer = nothing.baseLayer.copy(isEnabled = false))
                        // It does not turn, so it is as large as the screen: the words sit where they do in a still wallpaper.
                        renderNow(context, still, w, h, LAYERED_SCALE, params, screenShaped = true)?.also(parts::add) ?: return@withLock null
                    } else null
                    Scene(bottom, turning, top)
                } catch (e: Throwable) {
                    parts.forEach { it.recycle() }
                    throw e
                }
            }

        private fun blendOf(mode: LayerBlendMode): BlendMode = when (mode) {
            LayerBlendMode.SCREEN -> BlendMode.SCREEN
            LayerBlendMode.ADD -> BlendMode.PLUS
            LayerBlendMode.NORMAL -> BlendMode.LIGHTEN
            LayerBlendMode.MULTIPLY -> BlendMode.MULTIPLY
        }

        /** One render at a time in this process, whichever engine asks (Android's preview makes several). */
        private val renderLock = Mutex()

        /**
         * `params` on a square as large as the screen's diagonal times `scale`, framed as `framedAs` fills
         * the screen (all parts of a layered scene share the whole mandala's framing). Kept in the cache.
         */
        private suspend fun renderNow(
            context: Context, params: MandalaParameters, w: Int, h: Int, scale: Double, framedAs: MandalaParameters = params,
            screenShaped: Boolean = false,
        ): Bitmap? {
            val side = ceil(hypot(w.toDouble(), h.toDouble()) * scale).toInt().let { it + (it and 1) }
            // The JSON's hash is the same in every process (enum hash codes are not).
            val key = (SettingsJson.encode(params).hashCode().toLong() * 31 + side) * 2 + (if (screenShaped) 1 else 0)
            val file = File(context.cacheDir, "live-${key.toString(16)}.png")
            if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { return it }
            val short = min(w, h)
            val aspect = max(w, h).toDouble() / short
            val threads = min(MAX_THREADS, Runtime.getRuntime().availableProcessors())
            val job = coroutineContext.job
            if (!job.isActive) return null
            val fill = MandalaRenderer.fillZoom(framedAs, aspect, threads)
            // On the screen's shape the shorter side is the screen's; on the square it is the diagonal.
            val zoom = if (screenShaped) fill else fill * short * scale / side
            val (outW, outH) = if (screenShaped) (min(w, h) * scale).toInt() to (max(w, h) * scale).toInt() else side to side
            val image = MandalaRenderer.render(
                params,
                RenderOptions(
                    width = outW, height = outH, supersample = false, framing = Framing(zoom = zoom.coerceIn(Framing.MIN_ZOOM, Framing.MAX_ZOOM)),
                    threads = threads, textRenderer = AndroidTextRenderer(), imageLoader = AndroidImageLoader(),
                    isCancelled = { !job.isActive },
                ),
            ).image
            val bitmap = image.toBitmap()
            // Keep the latest few, so the parts of a layered scene do not push each other out.
            context.cacheDir.listFiles { f -> f.name.startsWith("live-") }?.sortedByDescending { it.lastModified() }?.drop(CACHE_KEPT)?.forEach { it.delete() }
            runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            return bitmap
        }

        /** A colour matrix turning the hue by `angle` radians, keeping the luma. */
        fun hueRotation(angle: Double): ColorMatrix {
            val c = cos(angle).toFloat()
            val s = sin(angle).toFloat()
            val lr = 0.213f
            val lg = 0.715f
            val lb = 0.072f
            return ColorMatrix(
                floatArrayOf(
                    lr + c * (1 - lr) + s * -lr, lg + c * -lg + s * -lg, lb + c * -lb + s * (1 - lb), 0f, 0f,
                    lr + c * -lr + s * 0.143f, lg + c * (1 - lg) + s * 0.14f, lb + c * -lb + s * -0.283f, 0f, 0f,
                    lr + c * -lr + s * -(1 - lr), lg + c * -lg + s * lg, lb + c * (1 - lb) + s * lb, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
        }
    }
}

/** One turning part of the live wallpaper and how it blends with what lies below. */
class Turning(val bitmap: Bitmap, val blend: BlendMode)

/** What the live wallpaper draws (see [LiveWallpaperService.scene]). */
class Scene(val bottom: Bitmap?, val turning: List<Turning>, val top: Bitmap?) {
    fun recycle() {
        bottom?.recycle()
        turning.forEach { it.bitmap.recycle() }
        top?.recycle()
    }
}
