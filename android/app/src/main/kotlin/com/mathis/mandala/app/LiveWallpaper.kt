package com.mathis.mandala.app

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

/** How the live wallpaper moves (ADR-0014); it always turns slowly. */
data class LiveSettings(val breathing: Boolean = false, val colourDrift: Boolean = false, val palettesInTurn: Boolean = false)

/**
 * The live wallpaper (ADR-0014, Pro): the mandala is rendered once, as a square as large as the
 * screen's diagonal so no corner shows while it turns, and then only moved on the graphics chip.
 * The service runs in its own process (`:live`), so the editor's images do not take its memory; it
 * reads its mandala and settings from two files, which it checks while it is visible.
 */
object LiveWallpaper {
    fun settings(context: Context): LiveSettings {
        val words = runCatching { settingsFile(context).readText().split(',').toSet() }.getOrDefault(emptySet())
        return LiveSettings(BREATHING in words, DRIFT in words, PALETTES in words)
    }

    fun setSettings(context: Context, s: LiveSettings) {
        val words = listOfNotNull(BREATHING.takeIf { s.breathing }, DRIFT.takeIf { s.colourDrift }, PALETTES.takeIf { s.palettesInTurn })
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
        LiveWallpaperService.rendered(context, params, w, h)?.recycle()
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
}

class LiveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = MandalaEngine()

    private inner class MandalaEngine : Engine() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val fadePaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private var width = 0
        private var height = 0
        private var visible = false
        private var shown: Bitmap? = null
        private var incoming: Bitmap? = null
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
            settings = LiveWallpaper.settings(context)
            val version = LiveWallpaper.version(context)
            if (version != drawnVersion && version != loadingVersion) {
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
            loading?.cancel()
            loadingVersion = version
            loading = scope.launch {
                val self = coroutineContext.job
                val bitmap = try {
                    rendered(context, params, w, h)
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
                paint.colorFilter = filter(seconds)
                fadePaint.colorFilter = paint.colorFilter
                canvas.save()
                canvas.rotate(((seconds / TURN_SECONDS * 360) % 360).toFloat(), width / 2f, height / 2f)
                drawCentred(canvas, current, paint)
                val next = incoming
                if (next != null) {
                    val t = ((now - fadeStart).toFloat() / FADE_MS).coerceIn(0f, 1f)
                    fadePaint.alpha = (t * 255).toInt()
                    drawCentred(canvas, next, fadePaint)
                    if (t >= 1f) {
                        shown = next
                        incoming = null
                        current.recycle()
                    }
                }
                canvas.restore()
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
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
        /** Fewer threads, less memory at once; the wallpaper is not in a hurry. */
        const val MAX_THREADS = 4

        /** All layers `steps` palettes on; the mandala's colours change, its lines stay. */
        fun shifted(params: MandalaParameters, steps: Int): MandalaParameters {
            if (steps == 0) return params
            val n = BuiltInPalettes.all.size
            return params.copy(layers = params.layers.map { it.copy(paletteIndex = (it.paletteIndex + steps).mod(n)) })
        }

        /**
         * The mandala as large as in a filled wallpaper, on a square as large as the screen's diagonal;
         * kept in the cache, so turning the screen on again or a restart does not render it again.
         */
        suspend fun rendered(context: Context, params: MandalaParameters, w: Int, h: Int): Bitmap? = renderLock.withLock { renderNow(context, params, w, h) }

        /** One render at a time in this process, whichever engine asks (Android's preview makes several). */
        private val renderLock = Mutex()

        private suspend fun renderNow(context: Context, params: MandalaParameters, w: Int, h: Int): Bitmap? {
            val side = ceil(hypot(w.toDouble(), h.toDouble()) * RENDER_SCALE).toInt().let { it + (it and 1) }
            // The JSON's hash is the same in every process (enum hash codes are not).
            val file = File(context.cacheDir, "live-${(SettingsJson.encode(params).hashCode().toLong() * 31 + side).toString(16)}.png")
            if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { return it }
            val short = min(w, h)
            val aspect = max(w, h).toDouble() / short
            val threads = min(MAX_THREADS, Runtime.getRuntime().availableProcessors())
            val job = coroutineContext.job
            if (!job.isActive) return null
            val zoom = MandalaRenderer.fillZoom(params, aspect, threads) * short * RENDER_SCALE / side
            val image = MandalaRenderer.render(
                params,
                RenderOptions(
                    outputSize = side, supersample = false, framing = Framing(zoom = zoom.coerceIn(Framing.MIN_ZOOM, Framing.MAX_ZOOM)),
                    threads = threads, textRenderer = AndroidTextRenderer(), imageLoader = AndroidImageLoader(),
                    isCancelled = { !job.isActive },
                ),
            ).image
            val bitmap = image.toBitmap()
            context.cacheDir.listFiles { f -> f.name.startsWith("live-") && f.name != file.name }?.forEach { it.delete() }
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
