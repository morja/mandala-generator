package com.mathis.mandala.editing

import com.mathis.mandala.core.ImageLoader
import com.mathis.mandala.core.IosBridge
import com.mathis.mandala.core.LayerCache
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.RenderCancelledException
import com.mathis.mandala.core.RenderOptions
import com.mathis.mandala.core.RenderTicket
import com.mathis.mandala.core.Rendered
import com.mathis.mandala.core.TextLayerSettings
import com.mathis.mandala.core.TextRenderer
import com.mathis.mandala.editing.Editing.updateText

/**
 * The text's two colours for Swift (DM-24 A2, QA-12), in the manner of [IosEditingParts]: the rules
 * are [TextColours] and [TextColourSheet], used here on the whole settings, plus the two small renders
 * Android's `EditorViewModel` makes for them (the picture without its text for the swatches and
 * "Stand out", and the colour sheet's live picture).
 */
object IosTextColours {
    // ── The two colours ──

    fun text(params: MandalaParameters): Hsb = TextColours.text(params.textLayer)

    fun glow(params: MandalaParameters): Hsb = TextColours.glow(params.textLayer)

    fun of(params: MandalaParameters, target: TextColourTarget): Hsb = TextColours.of(params.textLayer, target)

    /** A swatch or "Stand out": keeps a text brightness boost above 1 ([TextColours.withPickedColour]). */
    fun withPicked(params: MandalaParameters, target: TextColourTarget, colour: Hsb): MandalaParameters =
        params.updateText { TextColours.withPickedColour(it, target, colour) }

    /** A "Fine tune" slider: the colour as given ([TextColours.withColour]). */
    fun withColour(params: MandalaParameters, target: TextColourTarget, colour: Hsb): MandalaParameters =
        params.updateText { TextColours.withColour(it, target, colour) }

    /** A point of the sheet's field, x to the right and y down, both 0–1 ([TextColours.withFieldPoint]). */
    fun withFieldPoint(params: MandalaParameters, target: TextColourTarget, x: Double, y: Double): MandalaParameters =
        params.updateText { TextColours.withColour(it, target) { c -> TextColours.withFieldPoint(c, x, y) } }

    /** A hue from the sheet's strip ([TextColours.withHueFromStrip]). */
    fun withStripHue(params: MandalaParameters, target: TextColourTarget, hue: Double): MandalaParameters =
        params.updateText { TextColours.withColour(it, target) { c -> TextColours.withHueFromStrip(c, hue) } }

    /** "Same as text" for the glow ([TextColours.sameAsText]). */
    fun withSameAsText(params: MandalaParameters, on: Boolean): MandalaParameters = params.updateText { TextColours.sameAsText(it, on) }

    fun matches(a: Hsb, b: Hsb): Boolean = TextColours.matches(a, b)

    /** Whether the group's head shows the changed dot: the text colour differs from its default, or the glow has its own. */
    fun changed(params: MandalaParameters): Boolean = TextColours.changed(params.textLayer)

    /** The "Fine tune" defaults for the double tap: the text's own, and for the glow the letters' colour. */
    fun fineTuneDefaults(params: MandalaParameters, target: TextColourTarget): Hsb = TextColours.fineTuneDefaults(params.textLayer, target)

    /** The "Fine tune" brightness's upper end: the text keeps its boost (up to 2), the glow goes to 1. */
    fun brightnessMax(target: TextColourTarget): Double = TextColours.brightnessRange(target).endInclusive

    fun pictureSwatchCount(): Int = TextColours.PICTURE_SWATCHES

    fun white(): Hsb = Hsb.WHITE

    /** Where the text sits, as fractions of the picture (x0, y0, x1, y1, y down), for the before/after crop. */
    fun textArea(params: MandalaParameters): List<Double> = TextColours.textArea(params.textLayer).toList()

    // ── The colour sheet ──

    fun openSheet(params: MandalaParameters): TextColourSheet = TextColourSheet.open(params.textLayer)

    /** Cancel: both colours as they were when the sheet opened, the rest as now. */
    fun cancelled(params: MandalaParameters, sheet: TextColourSheet): MandalaParameters = params.updateText { sheet.cancelled(it) }

    // ── The picture without its text: swatches and "Stand out" ──

    /** What the swatches depend on: everything but the text. */
    fun sampleKey(params: MandalaParameters): MandalaParameters = params.copy(textLayer = TextLayerSettings())

    /**
     * The picture without its text, small ([Editing.SAMPLE_SIZE], no supersampling, the quick glow),
     * with its colours plus white; null when `ticket` was cancelled. As Android's `samplePicture`.
     */
    fun renderSample(params: MandalaParameters, threads: Int, ticket: RenderTicket, imageLoader: ImageLoader?): TextPictureSample? = try {
        val key = sampleKey(params)
        val image = MandalaRenderer.render(
            key,
            RenderOptions(
                outputSize = Editing.SAMPLE_SIZE, supersample = false, glowReferenceWidth = Editing.QUICK_GLOW_REFERENCE,
                threads = threads, imageLoader = imageLoader, isCancelled = ticket::isCancelled,
            ),
        ).image
        val argb = image.toArgbPixels()
        TextPictureSample(key, argb, image.width, TextColours.pictureSwatches(argb))
    } catch (e: RenderCancelledException) {
        null
    }

    /** "Stand out" on the sample of the picture; null when the text area is empty ([TextColours.standOut]). */
    fun withStandOut(params: MandalaParameters, sample: TextPictureSample): MandalaParameters? {
        val result = TextColours.standOut(sample.argb, sample.size, sample.size, TextColours.textArea(params.textLayer), sample.swatches) ?: return null
        return params.updateText { TextColours.applyStandOut(it, result) }
    }

    // ── The colour sheet's live picture ──

    /**
     * The small picture drawn at once while the colour sheet is open ([Editing.LIVE_SIZE], no
     * supersampling), with its own layer cache so a colour drag redraws only the text; null when
     * cancelled. As Android's `renderQuickPreview(…, LIVE_SIZE, …)`.
     */
    fun renderLive(params: MandalaParameters, cache: LayerCache, threads: Int, ticket: RenderTicket, imageLoader: ImageLoader?, textRenderer: TextRenderer?): Rendered? = try {
        val result = MandalaRenderer.render(
            params,
            RenderOptions(
                outputSize = Editing.LIVE_SIZE, glowReferenceWidth = Editing.LIVE_GLOW_REFERENCE, supersample = false, layerCache = cache,
                threads = threads, textRenderer = textRenderer, imageLoader = imageLoader, isCancelled = ticket::isCancelled,
            ),
        )
        Rendered(IosBridge.rgba(result.image), result.image.width, result.image.height, result.millis)
    } catch (e: RenderCancelledException) {
        null
    }
}

/** The picture without its text, small, and its colours plus white (Android's `PictureSample`). */
class TextPictureSample internal constructor(val key: MandalaParameters, internal val argb: IntArray, val size: Int, val swatches: List<Hsb>)
