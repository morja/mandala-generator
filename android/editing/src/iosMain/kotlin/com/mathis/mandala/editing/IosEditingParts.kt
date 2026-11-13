package com.mathis.mandala.editing

import com.mathis.mandala.core.CustomPalette
import com.mathis.mandala.core.DrawingLayerSettings
import com.mathis.mandala.core.EmblemFlowerVariant
import com.mathis.mandala.core.EmblemFrame
import com.mathis.mandala.core.EmblemGlowMode
import com.mathis.mandala.core.EmblemLayerSettings
import com.mathis.mandala.core.EmblemMotif
import com.mathis.mandala.core.EmblemRendering
import com.mathis.mandala.core.GraffitiLayerSettings
import com.mathis.mandala.core.LayerBlendMode
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.ProFeature
import com.mathis.mandala.core.ProFeatures
import com.mathis.mandala.core.Quote
import com.mathis.mandala.core.QuoteCollection
import com.mathis.mandala.core.Quotes
import com.mathis.mandala.core.SettingsJson
import com.mathis.mandala.core.TextLayerSettings
import com.mathis.mandala.editing.Editing.addStroke
import com.mathis.mandala.editing.Editing.clearStrokes
import com.mathis.mandala.editing.Editing.newShape
import com.mathis.mandala.editing.Editing.removeLastStroke
import com.mathis.mandala.editing.Editing.reset
import com.mathis.mandala.editing.Editing.strokeCount
import com.mathis.mandala.editing.Editing.switched
import com.mathis.mandala.editing.Editing.updateDrawing
import com.mathis.mandala.editing.Editing.updateEmblem
import com.mathis.mandala.editing.Editing.updateGraffiti
import com.mathis.mandala.editing.Editing.updateText
import com.mathis.mandala.editing.Editing.withOwnPalette
import com.mathis.mandala.editing.Editing.withQuote
import com.mathis.mandala.editing.Randomize.randomized
import kotlin.random.Random

/**
 * The Emblem, Text and Draw tabs, moving by hand and the quotes for Swift (PLAN-4 WP-6), in the
 * manner of [IosEditing]: plain functions over the shared rules, sliders as enums with their ranges.
 */
object IosEditingParts {
    // ── Emblem ──

    fun emblemMotifs(): List<EmblemMotif> = EmblemMotif.entries

    fun flowerVariants(): List<EmblemFlowerVariant> = EmblemFlowerVariant.entries

    fun emblemRenderings(): List<EmblemRendering> = EmblemRendering.entries

    fun emblemFrames(): List<EmblemFrame> = EmblemFrame.entries

    fun emblemGlowModes(): List<EmblemGlowMode> = EmblemGlowMode.entries

    /** Petal counts the Lotus offers ([Editing.lotusPetals]). */
    fun lotusPetals(): List<Int> = Editing.lotusPetals

    /** A new `EmblemLayerSettings`, for comparing against its defaults. */
    fun defaultEmblem(): EmblemLayerSettings = EmblemLayerSettings()

    /** On or off; switched on it starts with a light glow ([Editing.switched]). */
    fun emblemSwitched(params: MandalaParameters, on: Boolean): MandalaParameters = params.updateEmblem { it.switched(on) }

    fun emblemRandomized(params: MandalaParameters): MandalaParameters = params.updateEmblem { it.randomized(Random) }

    fun emblemReset(params: MandalaParameters): MandalaParameters = params.updateEmblem { it.reset() }

    /** "New shape" of the tree ([Editing.newShape]). */
    fun emblemNewShape(params: MandalaParameters): MandalaParameters = params.updateEmblem { it.newShape(Random) }

    fun emblemMotif(params: MandalaParameters, motif: EmblemMotif): MandalaParameters = params.updateEmblem { it.copy(motif = motif) }

    fun emblemFlowerVariant(params: MandalaParameters, variant: EmblemFlowerVariant): MandalaParameters = params.updateEmblem { it.copy(flowerVariant = variant) }

    fun emblemPetals(params: MandalaParameters, petals: Int): MandalaParameters = params.updateEmblem { it.copy(petals = petals) }

    fun emblemRendering(params: MandalaParameters, rendering: EmblemRendering): MandalaParameters = params.updateEmblem { it.copy(rendering = rendering) }

    fun emblemFrame(params: MandalaParameters, frame: EmblemFrame): MandalaParameters = params.updateEmblem { it.copy(frame = frame) }

    fun emblemGlowMode(params: MandalaParameters, mode: EmblemGlowMode): MandalaParameters = params.updateEmblem { it.copy(glowMode = mode) }

    fun emblemBlend(params: MandalaParameters, mode: LayerBlendMode): MandalaParameters = params.updateEmblem { it.copy(blendMode = mode) }

    fun emblemRoots(params: MandalaParameters, roots: Boolean): MandalaParameters = params.updateEmblem { it.copy(roots = roots) }

    /** A built-in palette for the emblem (replaces an own one). */
    fun emblemPalette(params: MandalaParameters, index: Int): MandalaParameters = params.updateEmblem { it.copy(paletteIndex = index, customPalette = null) }

    fun emblemOwnPalette(params: MandalaParameters, own: CustomPalette): MandalaParameters = params.updateEmblem { it.withOwnPalette(own) }

    /** Index and own palette exactly as given (Cancel in the palette editor). */
    fun emblemPaletteRestored(params: MandalaParameters, index: Int, own: CustomPalette?): MandalaParameters =
        params.updateEmblem { it.copy(paletteIndex = index, customPalette = own) }

    fun emblemValue(params: MandalaParameters, slider: EmblemSlider): Double = slider.get(params.emblemLayer)

    fun emblemWithValue(params: MandalaParameters, slider: EmblemSlider, value: Double): MandalaParameters =
        params.updateEmblem { slider.set(it, value) }

    /** Both offsets at once (the Position group's sliders and "Centre"). */
    fun emblemPosition(params: MandalaParameters, x: Double, y: Double): MandalaParameters = params.updateEmblem { it.copy(offsetX = x, offsetY = y) }

    // ── Text ──

    /** A new `TextLayerSettings`, for comparing against its defaults. */
    fun defaultText(): TextLayerSettings = TextLayerSettings()

    /** Fonts offered for the text: settings name and display name ([Quotes.fonts]). */
    fun fontNames(): List<String> = Quotes.fonts.map { it.first }

    fun fontDisplayName(name: String): String = Quotes.fonts.firstOrNull { it.first == name }?.second ?: name

    fun textSwitched(params: MandalaParameters, on: Boolean): MandalaParameters = params.updateText { it.copy(isEnabled = on) }

    /** The text die: a new look, and with no words yet a quote from [pool] too, as on Android. */
    fun textRandomized(params: MandalaParameters, pool: List<Quote>): MandalaParameters {
        val worded = if (params.textLayer.text.isBlank() && pool.isNotEmpty()) withQuote(params, pool.random()) else params
        return worded.updateText { it.randomized(Random) }
    }

    fun textReset(params: MandalaParameters): MandalaParameters = params.updateText { it.reset() }

    /** Puts a quote in and shows the text ([Editing.withQuote]). */
    fun withQuote(params: MandalaParameters, quote: Quote): MandalaParameters = params.updateText { it.withQuote(quote) }

    /** A random quote from [pool]; unchanged when the pool is empty. */
    fun withRandomQuote(params: MandalaParameters, pool: List<Quote>): MandalaParameters =
        if (pool.isEmpty()) params else withQuote(params, pool.random())

    fun withWords(params: MandalaParameters, text: String): MandalaParameters = params.updateText { it.copy(text = text) }

    fun withAuthor(params: MandalaParameters, author: String): MandalaParameters = params.updateText { it.copy(customAuthor = author) }

    fun withShowAuthor(params: MandalaParameters, show: Boolean): MandalaParameters = params.updateText { it.copy(showAuthor = show) }

    fun withFont(params: MandalaParameters, name: String): MandalaParameters = params.updateText { it.copy(fontName = name) }

    fun textValue(params: MandalaParameters, slider: TextSlider): Double = slider.get(params.textLayer)

    fun textWithValue(params: MandalaParameters, slider: TextSlider, value: Double): MandalaParameters =
        params.updateText { slider.set(it, value) }

    // ── The text shadow's angle, distance and softness (QA-13, rules in [TextShadow]) ──

    fun shadowDirectionRange(): List<Double> = listOf(TextShadow.directionRange.start, TextShadow.directionRange.endInclusive)

    fun shadowDistanceRange(): List<Double> = listOf(TextShadow.distanceRange.start, TextShadow.distanceRange.endInclusive)

    fun shadowSoftnessRange(): List<Double> = listOf(TextShadow.softnessRange.start, TextShadow.softnessRange.endInclusive)

    fun shadowDefaultDirection(): Double = TextShadow.DEFAULT_DIRECTION

    /** The direction as a fraction of a turn, or null (as a Swift optional) while the shadow sits under the letters. */
    fun shadowDirection(text: TextLayerSettings): Double? = TextShadow.direction(text)

    fun shadowDistance(text: TextLayerSettings): Double = TextShadow.distance(text)

    fun withShadowDirection(params: MandalaParameters, direction: Double): MandalaParameters =
        params.updateText { TextShadow.withDirection(it, direction) }

    fun withShadowDistance(params: MandalaParameters, distance: Double, fallbackDirection: Double): MandalaParameters =
        params.updateText { TextShadow.withDistance(it, distance, fallbackDirection) }

    fun withShadowSoftness(params: MandalaParameters, softness: Double): MandalaParameters =
        params.updateText { TextShadow.withSoftness(it, softness) }

    // ── Quotes (ADR-0019) ──

    fun quoteCollections(): List<QuoteCollection> = Quotes.collections

    fun quoteCollectionsFor(languages: List<String>): List<QuoteCollection> = Quotes.collectionsFor(languages)

    fun quoteLanguages(saved: List<String>?, preferred: List<String>): List<String> = QuoteChoice.languages(saved, preferred)

    /** Null when it would switch off the last language. */
    fun quoteLanguageToggled(current: List<String>, language: String): List<String>? = QuoteChoice.toggled(current, language)

    fun quotePool(languages: List<String>, mine: List<Quote>): List<Quote> = QuoteChoice.pool(languages, mine)

    fun makeQuote(text: String, author: String): Quote = Quote(text, author)

    fun myQuoteAdded(list: List<Quote>, text: String, author: String): List<Quote> = MyQuotes.add(list, Quote(text, author))

    fun myQuoteRemoved(list: List<Quote>, quote: Quote): List<Quote> = MyQuotes.remove(list, quote)

    fun myQuotesContain(list: List<Quote>, text: String, author: String): Boolean = MyQuotes.contains(list, text, author)

    fun myQuoteWordsRemoved(list: List<Quote>, text: String, author: String): List<Quote> = MyQuotes.removeWords(list, text, author)

    fun decodeQuotes(json: String): List<Quote> = runCatching { SettingsJson.decodeQuotes(json) }.getOrDefault(emptyList())

    fun encodeQuotes(quotes: List<Quote>): String = SettingsJson.encodeQuotes(quotes)

    // ── Draw ──

    fun drawTools(): List<DrawTool> = DrawTool.entries

    fun strokeCount(params: MandalaParameters, tool: DrawTool): Int = params.strokeCount(tool)

    /** A finished finger stroke, points as fractions of the canvas ([Editing.addStroke]). */
    fun withStroke(params: MandalaParameters, tool: DrawTool, xs: List<Double>, ys: List<Double>): MandalaParameters = params.addStroke(tool, xs, ys)

    fun withoutLastStroke(params: MandalaParameters, tool: DrawTool): MandalaParameters = params.removeLastStroke(tool)

    fun withoutStrokes(params: MandalaParameters, tool: DrawTool): MandalaParameters = params.clearStrokes(tool)

    /** Whether the tool's layer shows. */
    fun drawShown(params: MandalaParameters, tool: DrawTool): Boolean = when (tool) {
        DrawTool.LINES -> params.drawingLayer.isEnabled
        DrawTool.SPRAY -> params.graffitiLayer.isEnabled
    }

    fun drawWithShown(params: MandalaParameters, tool: DrawTool, on: Boolean): MandalaParameters = when (tool) {
        DrawTool.LINES -> params.updateDrawing { it.copy(isEnabled = on) }
        DrawTool.SPRAY -> params.updateGraffiti { it.copy(isEnabled = on) }
    }

    fun drawBlend(params: MandalaParameters, tool: DrawTool): LayerBlendMode = when (tool) {
        DrawTool.LINES -> params.drawingLayer.blendMode
        DrawTool.SPRAY -> params.graffitiLayer.blendMode
    }

    fun drawWithBlend(params: MandalaParameters, tool: DrawTool, mode: LayerBlendMode): MandalaParameters = when (tool) {
        DrawTool.LINES -> params.updateDrawing { it.copy(blendMode = mode) }
        DrawTool.SPRAY -> params.updateGraffiti { it.copy(blendMode = mode) }
    }

    /** The default blend mode of the tool's layer, for the group's "changed" dot. */
    fun drawDefaultBlend(tool: DrawTool): LayerBlendMode = when (tool) {
        DrawTool.LINES -> DrawingLayerSettings().blendMode
        DrawTool.SPRAY -> GraffitiLayerSettings().blendMode
    }

    fun drawValue(params: MandalaParameters, slider: DrawSlider): Double = slider.get(params)

    fun drawWithValue(params: MandalaParameters, slider: DrawSlider, value: Double): MandalaParameters = slider.set(params, value)

    // ── Moving by hand (ADR-0011, UX-1 20) ──

    fun moveMark(params: MandalaParameters, selected: Int, target: MoveTarget?): MoveMark? = Moving.mark(params, selected, target)

    /** One gesture state per editor; it keeps the snapping of both axes. */
    fun makeMoveGesture(): MoveGesture = MoveGesture()

    fun moved(gesture: MoveGesture, params: MandalaParameters, selected: Int, target: MoveTarget, dx: Double, dy: Double, zoom: Double, turnDegrees: Double): MandalaParameters =
        gesture.moved(params, selected, target, dx, dy, zoom, turnDegrees)

    // ── Pro ──

    /** The Pro features the picture uses, for the Pro mark on the picture ([ProFeatures.of]). */
    fun proFeatures(params: MandalaParameters): List<ProFeature> = ProFeatures.of(params).toList()
}

/** One slider of the Emblem tab with its range and default (a new `EmblemLayerSettings`). */
enum class EmblemSlider(
    val min: Double,
    val max: Double,
    internal val get: (EmblemLayerSettings) -> Double,
    internal val set: (EmblemLayerSettings, Double) -> EmblemLayerSettings,
) {
    ORGANIC(0.0, 1.0, { it.organic }, { e, v -> e.copy(organic = v) }),
    BRANCHES(0.0, 1.0, { it.branching }, { e, v -> e.copy(branching = v) }),
    SIZE(Editing.Ranges.emblemSize.start, Editing.Ranges.emblemSize.endInclusive, { it.size }, { e, v -> e.copy(size = v) }),
    WEIGHT(Editing.Ranges.emblemWeight.start, Editing.Ranges.emblemWeight.endInclusive, { it.weight }, { e, v -> e.copy(weight = v) }),
    ROTATE(Editing.Ranges.emblemRotation.start, Editing.Ranges.emblemRotation.endInclusive, { it.rotation }, { e, v -> e.copy(rotation = v) }),
    OFFSET_X(-Editing.MAX_OFFSET, Editing.MAX_OFFSET, { it.offsetX }, { e, v -> e.copy(offsetX = v) }),
    OFFSET_Y(-Editing.MAX_OFFSET, Editing.MAX_OFFSET, { it.offsetY }, { e, v -> e.copy(offsetY = v) }),
    COLOUR_POSITION(0.0, 1.0, { it.colorPosition }, { e, v -> e.copy(colorPosition = v) }),
    SPREAD(0.0, 1.0, { it.colorSpread }, { e, v -> e.copy(colorSpread = v) }),
    GLOW(0.0, 1.0, { it.glow }, { e, v -> e.copy(glow = v) }),
    OPACITY(0.0, 1.0, { it.opacity }, { e, v -> e.copy(opacity = v) }),
    ;

    val defaultValue: Double get() = get(EmblemLayerSettings())
}

/** One slider of the Text tab with its range ([Editing.Ranges]) and default (a new `TextLayerSettings`). */
enum class TextSlider(
    val min: Double,
    val max: Double,
    internal val get: (TextLayerSettings) -> Double,
    internal val set: (TextLayerSettings, Double) -> TextLayerSettings,
) {
    SIZE(Editing.Ranges.textSize.start, Editing.Ranges.textSize.endInclusive, { it.fontSize }, { t, v -> t.copy(fontSize = v) }),
    WIDTH(Editing.Ranges.textWidth.start, Editing.Ranges.textWidth.endInclusive, { it.fontWidth }, { t, v -> t.copy(fontWidth = v) }),
    TRACKING(Editing.Ranges.textTracking.start, Editing.Ranges.textTracking.endInclusive, { it.tracking }, { t, v -> t.copy(tracking = v) }),
    HEIGHT(0.0, 1.0, { it.offsetY }, { t, v -> t.copy(offsetY = v) }),
    GLOW(Editing.Ranges.textGlow.start, Editing.Ranges.textGlow.endInclusive, { it.glow }, { t, v -> t.copy(glow = v) }),
    CLOUD(0.0, 1.0, { it.cloudOpacity }, { t, v -> t.copy(cloudOpacity = v) }),
    CLOUD_SIZE(Editing.Ranges.textCloudSize.start, Editing.Ranges.textCloudSize.endInclusive, { it.cloudScale }, { t, v -> t.copy(cloudScale = v) }),
    CLOUD_HUE(0.0, 1.0, { it.cloudHue }, { t, v -> t.copy(cloudHue = v) }),
    CLOUD_SATURATION(0.0, 1.0, { it.cloudSaturation }, { t, v -> t.copy(cloudSaturation = v) }),
    CLOUD_BRIGHTNESS(0.0, 1.0, { it.cloudBrightness }, { t, v -> t.copy(cloudBrightness = v) }),
    SHADOW(0.0, 1.0, { it.shadowOpacity }, { t, v -> t.copy(shadowOpacity = v) }),
    SHADOW_HUE(0.0, 1.0, { it.shadowHue }, { t, v -> t.copy(shadowHue = v) }),
    SHADOW_SATURATION(0.0, 1.0, { it.shadowSaturation }, { t, v -> t.copy(shadowSaturation = v) }),
    SHADOW_BRIGHTNESS(0.0, 1.0, { it.shadowBrightness }, { t, v -> t.copy(shadowBrightness = v) }),
    HUE(0.0, 1.0, { it.hue }, { t, v -> t.copy(hue = v) }),
    SATURATION(0.0, 1.0, { it.saturation }, { t, v -> t.copy(saturation = v) }),
    BRIGHTNESS(Editing.Ranges.textBrightness.start, Editing.Ranges.textBrightness.endInclusive, { it.brightness }, { t, v -> t.copy(brightness = v) }),
    ;

    val defaultValue: Double get() = get(TextLayerSettings())
}

/** One slider of the Draw tab, for the lines (drawing layer) or the spray (graffiti layer). */
enum class DrawSlider(
    val tool: DrawTool,
    val min: Double,
    val max: Double,
    val stepped: Boolean,
    internal val get: (MandalaParameters) -> Double,
    internal val set: (MandalaParameters, Double) -> MandalaParameters,
    internal val fresh: () -> Double,
) {
    LINE_SYMMETRY(DrawTool.LINES, Editing.Ranges.drawSymmetry.first.toDouble(), Editing.Ranges.drawSymmetry.last.toDouble(), true,
        { it.drawingLayer.symmetry.toDouble() }, { p, v -> p.updateDrawing { it.copy(symmetry = kotlin.math.round(v).toInt()) } }, { DrawingLayerSettings().symmetry.toDouble() }),
    LINE_HUE(DrawTool.LINES, 0.0, 1.0, false, { it.drawingLayer.currentHue }, { p, v -> p.updateDrawing { it.copy(currentHue = v) } }, { DrawingLayerSettings().currentHue }),
    LINE_SATURATION(DrawTool.LINES, 0.0, 1.0, false, { it.drawingLayer.currentSaturation }, { p, v -> p.updateDrawing { it.copy(currentSaturation = v) } }, { DrawingLayerSettings().currentSaturation }),
    LINE_BRIGHTNESS(DrawTool.LINES, 0.0, 1.0, false, { it.drawingLayer.currentBrightness }, { p, v -> p.updateDrawing { it.copy(currentBrightness = v) } }, { DrawingLayerSettings().currentBrightness }),
    LINE_WEIGHT(DrawTool.LINES, 0.0, 1.0, false, { it.drawingLayer.strokeWeight }, { p, v -> p.updateDrawing { it.copy(strokeWeight = v) } }, { DrawingLayerSettings().strokeWeight }),
    LINE_GLOW(DrawTool.LINES, 0.0, 1.0, false, { it.drawingLayer.glowIntensity }, { p, v -> p.updateDrawing { it.copy(glowIntensity = v) } }, { DrawingLayerSettings().glowIntensity }),
    LINE_OPACITY(DrawTool.LINES, 0.0, 1.0, false, { it.drawingLayer.opacity }, { p, v -> p.updateDrawing { it.copy(opacity = v) } }, { DrawingLayerSettings().opacity }),
    SPRAY_SYMMETRY(DrawTool.SPRAY, Editing.Ranges.drawSymmetry.first.toDouble(), Editing.Ranges.drawSymmetry.last.toDouble(), true,
        { it.graffitiLayer.symmetry.toDouble() }, { p, v -> p.updateGraffiti { it.copy(symmetry = kotlin.math.round(v).toInt()) } }, { GraffitiLayerSettings().symmetry.toDouble() }),
    BRUSH_SIZE(DrawTool.SPRAY, Editing.Ranges.sprayBrush.start, Editing.Ranges.sprayBrush.endInclusive, false,
        { it.graffitiLayer.currentBrushSize }, { p, v -> p.updateGraffiti { it.copy(currentBrushSize = v) } }, { GraffitiLayerSettings().currentBrushSize }),
    PAINT(DrawTool.SPRAY, 0.0, 1.0, false, { it.graffitiLayer.currentOpacity }, { p, v -> p.updateGraffiti { it.copy(currentOpacity = v) } }, { GraffitiLayerSettings().currentOpacity }),
    SPRAY_HUE(DrawTool.SPRAY, 0.0, 1.0, false, { it.graffitiLayer.currentHue }, { p, v -> p.updateGraffiti { it.copy(currentHue = v) } }, { GraffitiLayerSettings().currentHue }),
    SPRAY_SATURATION(DrawTool.SPRAY, 0.0, 1.0, false, { it.graffitiLayer.currentSaturation }, { p, v -> p.updateGraffiti { it.copy(currentSaturation = v) } }, { GraffitiLayerSettings().currentSaturation }),
    SPRAY_BRIGHTNESS(DrawTool.SPRAY, 0.0, 1.0, false, { it.graffitiLayer.currentBrightness }, { p, v -> p.updateGraffiti { it.copy(currentBrightness = v) } }, { GraffitiLayerSettings().currentBrightness }),
    SOFTNESS(DrawTool.SPRAY, 0.0, 1.0, false, { it.graffitiLayer.softness }, { p, v -> p.updateGraffiti { it.copy(softness = v) } }, { GraffitiLayerSettings().softness }),
    SPRAY_OPACITY(DrawTool.SPRAY, 0.0, 1.0, false, { it.graffitiLayer.opacity }, { p, v -> p.updateGraffiti { it.copy(opacity = v) } }, { GraffitiLayerSettings().opacity }),
    ;

    val defaultValue: Double get() = fresh()
}
