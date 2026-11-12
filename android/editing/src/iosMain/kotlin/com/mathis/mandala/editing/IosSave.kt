package com.mathis.mandala.editing

import com.mathis.mandala.core.Finish
import com.mathis.mandala.core.Framing
import com.mathis.mandala.core.Image8
import com.mathis.mandala.core.ImageLoader
import com.mathis.mandala.core.IosBridge
import com.mathis.mandala.core.LayerAnimation
import com.mathis.mandala.core.LayerCache
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.Parallel
import com.mathis.mandala.core.ProFeatures
import com.mathis.mandala.core.RenderCancelledException
import com.mathis.mandala.core.RenderOptions
import com.mathis.mandala.core.RenderTicket
import com.mathis.mandala.core.Rendered
import com.mathis.mandala.core.TextRenderer
import com.mathis.mandala.core.VectorExport
import com.mathis.mandala.core.VideoFrames
import com.mathis.mandala.editing.Editing.SaveFormat
import com.mathis.mandala.editing.Editing.moved
import com.mathis.mandala.editing.Editing.zoomedAround
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.plus
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import platform.posix.memcpy

/** Width and height in pixels, for Swift. */
class PixelSize(val width: Int, val height: Int)

/**
 * The save sheet's door to the shared rules (PLAN-4 WP-8, ADR-0010, ADR-0013, ADR-0014): formats and
 * sizes, the save choice and its placements, framing, the renders a save needs, the moving picture's
 * frames and the line drawing, in plain types for Swift. Every rule is the shared module's or core's.
 */
object IosSave {
    // ── Formats and sizes ──

    fun formats(): List<SaveFormat> = SaveFormat.entries

    fun aspect(format: SaveFormat, screenAspect: Double): Double = Editing.aspect(format, screenAspect)

    /** The saved picture: 1024 px on the shorter side, or with `large` (Pro) 2048 px. */
    fun exportSize(format: SaveFormat, screenAspect: Double, large: Boolean): PixelSize =
        Editing.exportSize(format, screenAspect, large).let { PixelSize(it.first, it.second) }

    /** The video: 720 or (`large`) 1080 on the shorter side, at most 1920 on the longer one, both even. */
    fun videoSize(format: SaveFormat, screenAspect: Double, large: Boolean): PixelSize =
        Editing.videoSize(format, screenAspect, large).let { PixelSize(it.first, it.second) }

    /** The save sheet's frame preview: 360 px on the shorter side. */
    fun frameSize(format: SaveFormat, screenAspect: Double): PixelSize =
        Editing.size(format, screenAspect, Editing.FRAME_PREVIEW_WIDTH).let { PixelSize(it.first, it.second) }

    fun fileName(params: MandalaParameters, width: Int, height: Int): String = Editing.fileName(params, width, height)

    fun videoFileName(params: MandalaParameters, width: Int, height: Int, seconds: Int): String =
        Editing.videoFileName(params, width, height, seconds)

    /** As Android's `suggestedLineDrawingName`. */
    fun lineDrawingName(params: MandalaParameters, look: VectorExport.Look): String =
        Editing.fileName(params).removeSuffix(".png") + (if (look == VectorExport.Look.LINES) "-lines" else "") + ".svg"

    fun videoSeconds(): List<Int> = LayerAnimation.SECONDS

    fun fps(): Int = LayerAnimation.FPS

    fun frameCount(seconds: Int): Int = LayerAnimation.frameCount(seconds)

    // ── The save choice (SaveChoice, kept across starts like Android's SaveChoiceStore) ──

    fun defaultChoice(): SaveChoice = SaveChoice()

    fun withFormat(choice: SaveChoice, format: SaveFormat): SaveChoice = choice.withFormat(format)

    /** "Fill" or "Whole", free in every format; with Pro it ends placing by hand. */
    fun withFill(choice: SaveChoice, fill: Boolean): SaveChoice = choice.copy(fit = if (fill) Fit.FILL else Fit.WHOLE)

    /** Placed by hand (Pro): the caller checks Pro, as Android's `setFraming`. */
    fun withHandFraming(choice: SaveChoice, framing: Framing): SaveChoice =
        choice.copy(framing = framing.clamped(), fit = Fit.HAND, framedByHand = true)

    fun withTextFollows(choice: SaveChoice, follows: Boolean): SaveChoice = choice.copy(textFollowsMandala = follows)

    fun withFinish(choice: SaveChoice, finish: Finish): SaveChoice = choice.copy(finish = finish.clamped())

    fun withMoving(choice: SaveChoice, moving: Boolean): SaveChoice = choice.copy(moving = moving)

    fun withVideoSeconds(choice: SaveChoice, seconds: Int): SaveChoice =
        if (seconds in LayerAnimation.SECONDS) choice.copy(videoSeconds = seconds) else choice

    fun makeFinish(brightness: Double, contrast: Double, saturation: Double): Finish = Finish(brightness, contrast, saturation)

    fun identityFinish(): Finish = Finish()

    fun finishRanges(): List<Double> = listOf(Finish.MIN_BRIGHTNESS, Finish.MAX_BRIGHTNESS, Finish.MIN_CONTRAST, Finish.MAX_CONTRAST, 0.0, Finish.MAX_SATURATION)

    /** The choice as plain strings for the app's preferences, with the same keys and fallbacks as Android's store. */
    fun encodeChoice(choice: SaveChoice): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for ((f, p) in choice.placements) {
            out["fit_${f.name}"] = p.fit.name
            out["centre_x_${f.name}"] = p.framing.centreX.toString()
            out["centre_y_${f.name}"] = p.framing.centreY.toString()
            out["zoom_${f.name}"] = p.framing.zoom.toString()
        }
        out["format"] = choice.format.name
        out["centre_x"] = choice.framing.centreX.toString()
        out["centre_y"] = choice.framing.centreY.toString()
        out["zoom"] = choice.framing.zoom.toString()
        out["text_follows"] = choice.textFollowsMandala.toString()
        out["fit"] = choice.fit.name
        out["finish_brightness"] = choice.finish.brightness.toString()
        out["finish_contrast"] = choice.finish.contrast.toString()
        out["finish_saturation"] = choice.finish.saturation.toString()
        out["home_brightness"] = choice.homeBrightness.toString()
        out["moving"] = choice.moving.toString()
        out["video_seconds"] = choice.videoSeconds.toString()
        out["framed_by_hand"] = choice.framedByHand.toString()
        return out
    }

    fun decodeChoice(saved: Map<String, String>): SaveChoice {
        fun d(key: String, default: Double) = saved[key]?.toDoubleOrNull() ?: default
        fun b(key: String) = saved[key] == "true"
        val format = saved["format"]?.let { n -> SaveFormat.entries.firstOrNull { it.name == n } } ?: SaveFormat.SCREEN
        val framing = Framing(d("centre_x", 0.5), d("centre_y", 0.5), d("zoom", 1.0)).clamped()
        val fit = saved["fit"]?.let { n -> Fit.entries.firstOrNull { it.name == n } } ?: if (framing == Framing()) Fit.WHOLE else Fit.HAND
        val finish = Finish(d("finish_brightness", 1.0), d("finish_contrast", 1.0), d("finish_saturation", 1.0)).clamped()
        val home = d("home_brightness", SaveChoice.DEFAULT_HOME_BRIGHTNESS).coerceIn(SaveChoice.MIN_HOME_BRIGHTNESS, 1.0)
        val placements = SaveFormat.entries.filter { it != format && saved.containsKey("fit_${it.name}") }.associateWith { f ->
            Placement(
                Fit.entries.firstOrNull { it.name == saved["fit_${f.name}"] } ?: Fit.WHOLE,
                Framing(d("centre_x_${f.name}", 0.5), d("centre_y_${f.name}", 0.5), d("zoom_${f.name}", 1.0)).clamped(),
            )
        }
        val seconds = saved["video_seconds"]?.toIntOrNull()?.takeIf { it in LayerAnimation.SECONDS } ?: LayerAnimation.DEFAULT_SECONDS
        return SaveChoice(format, framing, b("text_follows"), fit, finish, home, placements, b("moving"), seconds, b("framed_by_hand"))
    }

    // ── Framing ──

    /** The framing an image is drawn with (`Editing.framingFor`); `fillZoom` matters only for Fill. */
    fun framingFor(choice: SaveChoice, fillZoom: Double, pro: Boolean): Framing = Editing.framingFor(choice, fillZoom, pro)

    /** Measures the mandala for "Fill" (about a tenth of a second); call it off the main thread. */
    fun fillZoom(params: MandalaParameters, aspect: Double, threads: Int): Double = MandalaRenderer.fillZoom(params, aspect, threads)

    fun moved(framing: Framing, dx: Double, dy: Double): Framing = framing.moved(dx, dy)

    fun zoomedAround(framing: Framing, px: Double, py: Double, factor: Double): Framing = framing.zoomedAround(px, py, factor)

    fun centred(): Framing = Framing()

    // ── Pro (ADR-0016) ──

    /** The mandala without its Pro features, as the Pro sheet's "without them" (Android's `continueWithoutPro`). */
    fun withoutPro(params: MandalaParameters): MandalaParameters = ProFeatures.removed(params)

    // ── Renders ──

    /**
     * The saved picture: `width` × `height` with the framing, finish and quote placement; supersampled
     * at 1024, drawn at its size at 2048 (Android's `renderFullSize`). `progress` gets whole percents.
     */
    fun renderFramed(
        params: MandalaParameters, width: Int, height: Int, framing: Framing, textFollows: Boolean, supersample: Boolean, finish: Finish,
        threads: Int, ticket: RenderTicket, imageLoader: ImageLoader?, textRenderer: TextRenderer?, progress: (Int) -> Unit,
    ): Rendered? = try {
        var shown = -1
        val result = MandalaRenderer.render(
            params,
            RenderOptions(
                width = width, height = height, framing = framing, textFollowsMandala = textFollows, supersample = supersample, finish = finish,
                threads = threads, isCancelled = ticket::isCancelled, imageLoader = imageLoader, textRenderer = textRenderer,
                onProgress = { share ->
                    val percent = (share * 100).toInt()
                    if (percent > shown) {
                        shown = percent
                        progress(percent)
                    }
                },
            ),
        )
        Rendered(IosBridge.rgba(result.image), result.image.width, result.image.height, result.millis)
    } catch (e: RenderCancelledException) {
        null
    } catch (e: Throwable) {
        // Swift cannot catch a Kotlin exception; the caller tells "not cancelled" from the ticket and says it failed.
        null
    }

    /** The frame preview without the finish, which [DrawnFrame.finished] applies so its sliders need no new render. */
    fun drawFrame(
        params: MandalaParameters, width: Int, height: Int, framing: Framing, textFollows: Boolean, threads: Int, ticket: RenderTicket,
        imageLoader: ImageLoader?, textRenderer: TextRenderer?,
    ): DrawnFrame? = try {
        val image = MandalaRenderer.render(
            params,
            RenderOptions(
                width = width, height = height, framing = framing, textFollowsMandala = textFollows, threads = threads,
                isCancelled = ticket::isCancelled, imageLoader = imageLoader, textRenderer = textRenderer,
            ),
        ).image
        DrawnFrame(image, threads)
    } catch (e: Throwable) {
        null
    }

    /** The frame while Video is chosen: the layers turning, small and with the fast glow (Android's `animateFramePreview`). */
    fun movingFrames(
        params: MandalaParameters, width: Int, height: Int, framing: Framing, textFollows: Boolean, finish: Finish, threads: Int,
        ticket: RenderTicket, imageLoader: ImageLoader?, textRenderer: TextRenderer?,
    ): MovingFrames = MovingFrames(
        params,
        LayerAnimation.options(
            RenderOptions(
                width = width, height = height, framing = framing, textFollowsMandala = textFollows, supersample = false, finish = finish,
                threads = threads, glowReferenceWidth = Editing.FAST_GLOW_REFERENCE, isCancelled = ticket::isCancelled,
                imageLoader = imageLoader, textRenderer = textRenderer,
            ),
            LayerCache(),
        ),
    )

    /** The video's frames (Android's `saveVideo` render loop), written as NV12 into the encoder's buffers. */
    fun videoFrames(
        params: MandalaParameters, width: Int, height: Int, framing: Framing, textFollows: Boolean, finish: Finish, seconds: Int,
        threads: Int, ticket: RenderTicket, imageLoader: ImageLoader?, textRenderer: TextRenderer?,
    ): VideoFrameSource = VideoFrameSource(
        params, width, height, LayerAnimation.frameCount(seconds), threads,
        LayerAnimation.options(
            RenderOptions(
                width = width, height = height, framing = framing, textFollowsMandala = textFollows, supersample = false, finish = finish,
                threads = threads, isCancelled = ticket::isCancelled, imageLoader = imageLoader, textRenderer = textRenderer,
            ),
            LayerCache(),
        ),
    )

    // ── The line drawing (ADR-0013) ──

    fun hasPointLayers(params: MandalaParameters): Boolean = VectorExport.hasPointLayers(params)

    /** The SVG text at Android's size (`Editing.EXPORT_SIZE` units). */
    fun lineDrawing(params: MandalaParameters, look: VectorExport.Look): String =
        StringBuilder().also { VectorExport.svg(params, Editing.EXPORT_SIZE, look, it) }.toString()

    fun lineDrawingLooks(): List<VectorExport.Look> = VectorExport.Look.entries
}

/** A frame preview as drawn, before its finish. */
class DrawnFrame internal constructor(private val image: Image8, private val threads: Int) {
    val width: Int get() = image.width
    val height: Int get() = image.height

    fun finished(finish: Finish): Rendered {
        val done = finish.apply(image, threads)
        return Rendered(IosBridge.rgba(done), done.width, done.height, 0)
    }
}

/** The turning frame preview; the layers are drawn once and kept, later frames only turn and blend them. */
class MovingFrames internal constructor(private val params: MandalaParameters, private val options: RenderOptions) {
    /** The picture at `t` through the loop (0…1), or null when the ticket was cancelled. */
    fun at(t: Double): Rendered? = try {
        val image = MandalaRenderer.render(LayerAnimation.at(params, t), options).image
        Rendered(IosBridge.rgba(image), image.width, image.height, 0)
    } catch (e: Throwable) {
        null
    }

    /** Lets the kept layer images go. */
    fun close() = options.layerCache?.clear()
}

/**
 * The frames of the moving picture: each is rendered by core and converted by core to Y′CbCr 4:2:0 with
 * the BT.709 matrix in limited range ([VideoFrames], as on Android); Swift hands the encoder's two
 * planes, Y and interleaved CbCr (NV12), and this writes into them in place.
 */
@OptIn(ExperimentalForeignApi::class)
class VideoFrameSource internal constructor(
    private val params: MandalaParameters,
    val width: Int,
    val height: Int,
    val frameCount: Int,
    threads: Int,
    private val options: RenderOptions,
) {
    private val par = Parallel(threads)
    private val y = ByteArray(width * height)
    private val u = ByteArray(width * height / 4)
    private val v = ByteArray(width * height / 4)

    /**
     * Renders frame `index` and writes it into the planes (rows `yStride` and `uvStride` bytes apart);
     * false when the ticket was cancelled or the frame could not be drawn (Swift tells the two apart by the ticket).
     */
    fun write(index: Int, yPlane: COpaquePointer, yStride: Int, uvPlane: COpaquePointer, uvStride: Int): Boolean = try {
        writeFrame(index, yPlane, yStride, uvPlane, uvStride)
        true
    } catch (e: Throwable) {
        false
    }

    private fun writeFrame(index: Int, yPlane: COpaquePointer, yStride: Int, uvPlane: COpaquePointer, uvStride: Int) {
        val image = MandalaRenderer.render(LayerAnimation.frame(params, index, frameCount), options).image
        VideoFrames.toI420(image, y, u, v, par)
        y.usePinned { pinned ->
            for (r in 0 until height) memcpy(yPlane.reinterpret<ByteVar>() + r.toLong() * yStride, pinned.addressOf(r * width), width.toULong())
        }
        // NV12: Cb and Cr side by side in one plane, half the width and height in pairs.
        val cw = width / 2
        val uv = uvPlane.reinterpret<ByteVar>()
        par.forRange(height / 2) { from, until ->
            for (r in from until until) {
                val row = r.toLong() * uvStride
                val src = r * cw
                for (c in 0 until cw) {
                    uv[row + 2 * c] = u[src + c]
                    uv[row + 2 * c + 1] = v[src + c]
                }
            }
        }
    }

    /** Ends the worker pool's use and lets the kept layer images go. */
    fun close() {
        par.close()
        options.layerCache?.clear()
    }
}
