package com.mathis.mandala.editing

import com.mathis.mandala.core.BaseLayerSettings
import com.mathis.mandala.core.BaseLayerType
import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.CustomPalette
import com.mathis.mandala.core.CustomPaletteStop
import com.mathis.mandala.core.EffectsLayerSettings
import com.mathis.mandala.core.LayerBlendMode
import com.mathis.mandala.core.SettingsJson
import com.mathis.mandala.editing.Editing.addLayer
import com.mathis.mandala.editing.Editing.changeOwnPalettes
import com.mathis.mandala.editing.Editing.duplicateLayer
import com.mathis.mandala.editing.Editing.moveLayer
import com.mathis.mandala.editing.Editing.newLightPattern
import com.mathis.mandala.editing.Editing.newSparklePattern
import com.mathis.mandala.editing.Editing.removeLayer
import com.mathis.mandala.editing.Editing.reseedLayer
import com.mathis.mandala.editing.Editing.reset
import com.mathis.mandala.editing.Editing.switchedOnOff
import com.mathis.mandala.editing.Editing.switchedTo
import com.mathis.mandala.editing.Editing.updateBackground
import com.mathis.mandala.editing.Editing.updateEffects
import com.mathis.mandala.editing.Editing.withEmblem
import com.mathis.mandala.editing.Editing.withOwnPalette
import com.mathis.mandala.editing.Randomize.randomLayer
import com.mathis.mandala.editing.Randomize.randomVariant
import com.mathis.mandala.editing.Randomize.randomized
import kotlin.random.Random
import com.mathis.mandala.core.ColorPalette
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaStyle
import com.mathis.mandala.core.OwnPalettes
import com.mathis.mandala.core.StyleLayer
import com.mathis.mandala.editing.Editing.updateLayer
import com.mathis.mandala.editing.Editing.withText
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy

/**
 * The iPhone app's door to the shared editing rules (PLAN-4 section 3.1), with plain types for Swift:
 * no extension functions, receivers or default arguments. Every rule here calls [Editing]; none is
 * written twice.
 */
object IosEditing {
    /** The style picker's order: by English name ([Editing.stylesByName]). */
    fun stylesByName(): List<MandalaStyle> = Editing.stylesByName

    /** All built-in palettes; a layer's `paletteIndex` points into this list. */
    fun palettes(): List<ColorPalette> = BuiltInPalettes.all

    /** The offered palettes, plus `current` at the end if it is a hidden one ([Editing.paletteChoices]). */
    fun paletteChoices(current: Int): List<Int> = Editing.paletteChoices(current)

    /** The palette a layer draws with: its own one if it has one, else the built-in one. */
    fun paletteOf(layer: StyleLayer): ColorPalette = OwnPalettes.resolve(layer.paletteIndex, layer.customPalette, BuiltInPalettes.all)

    /** The sliders shown for a layer of `style`, in Android's order (Shape, then Light and colour). */
    fun sliders(style: MandalaStyle): List<LayerSlider> = LayerSlider.entries.filter { it.appliesTo(style) }

    fun value(params: MandalaParameters, layer: Int, slider: LayerSlider): Double =
        params.layers.getOrNull(layer)?.let(slider.get) ?: slider.defaultValue

    fun withValue(params: MandalaParameters, layer: Int, slider: LayerSlider, value: Double): MandalaParameters =
        params.updateLayer(layer) { slider.set(it, value) }

    fun withStyle(params: MandalaParameters, layer: Int, style: MandalaStyle): MandalaParameters =
        params.updateLayer(layer) { it.copy(style = style) }

    /** A built-in palette replaces an own one, as tapping a swatch does on Android. */
    fun withPalette(params: MandalaParameters, layer: Int, index: Int): MandalaParameters =
        params.updateLayer(layer) { it.copy(paletteIndex = index, customPalette = null) }

    fun withVisible(params: MandalaParameters, layer: Int, visible: Boolean): MandalaParameters =
        params.updateLayer(layer) { it.copy(isEnabled = visible) }

    /** Whether the layer uses a built-in palette (no own one). */
    fun usesBuiltInPalette(layer: StyleLayer): Boolean = layer.customPalette?.stops?.isNotEmpty() != true

    /** The macOS app's file name scheme ([Editing.fileName]; the 1024 square has no size suffix). */
    fun fileName(params: MandalaParameters): String = Editing.fileName(params)

    // ── Layers: adding, copying, removing, moving and the dice ──

    const val MAX_LAYERS: Int = Editing.MAX_LAYERS

    private fun seed(): ULong = Random.nextLong().toULong()

    fun addLayer(params: MandalaParameters): Edited = params.addLayer(seed()).let { Edited(it.first, it.second) }

    fun duplicateLayer(params: MandalaParameters, layer: Int): Edited = params.duplicateLayer(layer, seed()).let { Edited(it.first, it.second) }

    fun removeLayer(params: MandalaParameters, layer: Int): Edited = params.removeLayer(layer).let { Edited(it.first, it.second) }

    /** Moves a layer by `delta` places, positive = further up (drawn later). */
    fun moveLayer(params: MandalaParameters, layer: Int, delta: Int): Edited = params.moveLayer(layer, delta).let { Edited(it.first, it.second) }

    /** The layer die: a new look for the layer ([Randomize.randomLayer]). */
    fun randomLayer(params: MandalaParameters, layer: Int): MandalaParameters = params.randomLayer(layer, Random)

    /** The Style group's "Variation" ([Randomize.randomVariant]). */
    fun randomVariant(params: MandalaParameters, layer: Int): MandalaParameters = params.randomVariant(layer, Random)

    /** The Shape group's arrangement die: same look, new seed. */
    fun reseedLayer(params: MandalaParameters, layer: Int): MandalaParameters = params.reseedLayer(layer, seed())

    fun blendModes(): List<LayerBlendMode> = LayerBlendMode.entries

    fun withBlend(params: MandalaParameters, layer: Int, mode: LayerBlendMode): MandalaParameters =
        params.updateLayer(layer) { it.copy(blendMode = mode) }

    // ── Own palettes (ADR-0021) ──

    /** Index and own palette as given (Cancel puts back what the layer had). */
    fun withLayerPalette(params: MandalaParameters, layer: Int, index: Int, own: CustomPalette?): MandalaParameters =
        params.updateLayer(layer) { it.copy(paletteIndex = index, customPalette = own) }

    /** Chooses an own palette: its look-alike becomes the built-in fallback ([Editing.withOwnPalette]). */
    fun withOwnPalette(params: MandalaParameters, layer: Int, own: CustomPalette): MandalaParameters =
        params.updateLayer(layer) { it.withOwnPalette(own) }

    /** Layers and the emblem using palette `saved` take its new version. */
    fun withPaletteSaved(params: MandalaParameters, saved: CustomPalette): MandalaParameters =
        params.changeOwnPalettes { if (it.id == saved.id) saved else it }

    /** Layers using palette `id` go back to the built-in palette that looks most like it. */
    fun withPaletteDeleted(params: MandalaParameters, id: String): MandalaParameters =
        params.changeOwnPalettes { if (it.id == id) null else it }

    /** A palette that came with a favourite or a file becomes the user's (`kept`, maybe with a new id). */
    fun withPaletteKept(params: MandalaParameters, old: CustomPalette, kept: CustomPalette): MandalaParameters =
        params.changeOwnPalettes { if (it == old) kept else it }

    fun putPalette(list: List<CustomPalette>, palette: CustomPalette): List<CustomPalette> = MyPalettes.put(list, palette)

    fun removePalette(list: List<CustomPalette>, id: String): List<CustomPalette> = MyPalettes.remove(list, id)

    fun addedStop(stops: List<CustomPaletteStop>, selected: Int, newId: String): List<CustomPaletteStop> = MyPalettes.addedStop(stops, selected, newId)

    fun defaultPaletteStops(): List<CustomPaletteStop> = OwnPalettes.defaultStops()

    fun stopsOf(palette: ColorPalette): List<CustomPaletteStop> = OwnPalettes.stopsOf(palette)

    const val MIN_STOPS: Int = OwnPalettes.MIN_STOPS
    const val MAX_STOPS: Int = OwnPalettes.MAX_STOPS

    fun decodePalettes(json: String): List<CustomPalette> = runCatching { SettingsJson.decodePalettes(json) }.getOrDefault(emptyList())

    fun encodePalettes(palettes: List<CustomPalette>): String = SettingsJson.encodePalettes(palettes)

    /** Colours of a picture (sRGB RGBA bytes, alpha ignored), dark to bright ([OwnPalettes.fromPixels]); ids are the caller's. */
    @OptIn(ExperimentalForeignApi::class)
    fun coloursFromPicture(rgba: NSData): List<CustomPaletteStop> {
        val n = (rgba.length / 4u).toInt()
        val bytes = ByteArray(n * 4)
        if (n > 0) bytes.usePinned { memcpy(it.addressOf(0), rgba.bytes, (n * 4).toULong()) }
        val argb = IntArray(n) { p ->
            (0xFF shl 24) or ((bytes[p * 4].toInt() and 0xFF) shl 16) or ((bytes[p * 4 + 1].toInt() and 0xFF) shl 8) or (bytes[p * 4 + 2].toInt() and 0xFF)
        }
        return OwnPalettes.fromPixels(argb)
    }

    // ── Scene: background ──

    /** Background types offered, Grain only while it is the current one ([Editing.backgroundChoices]). */
    fun backgroundChoices(current: BaseLayerSettings): List<BaseLayerType> = Editing.backgroundChoices(current)

    /** Whether the settings' background type is one the app can draw. */
    fun isOffered(type: BaseLayerType): Boolean = type in Editing.backgrounds

    fun withBackgroundSwitched(params: MandalaParameters, on: Boolean): MandalaParameters = params.updateBackground { it.switchedOnOff(on) }

    fun withBackgroundType(params: MandalaParameters, type: BaseLayerType): MandalaParameters = params.updateBackground { it.switchedTo(type) }

    fun withBackgroundPicture(params: MandalaParameters, url: String): MandalaParameters =
        params.updateBackground { it.copy(isEnabled = true, type = BaseLayerType.IMAGE, imageURL = url) }

    fun withBackgroundRadial(params: MandalaParameters, radial: Boolean): MandalaParameters = params.updateBackground { it.copy(isRadial = radial) }

    fun withColouredGrain(params: MandalaParameters, coloured: Boolean): MandalaParameters = params.updateBackground { it.copy(grainColored = coloured) }

    fun value(params: MandalaParameters, slider: BackgroundSlider): Double = slider.get(params.baseLayer)

    fun withValue(params: MandalaParameters, slider: BackgroundSlider, value: Double): MandalaParameters =
        params.updateBackground { slider.set(it, value) }

    // ── Scene: effects ──

    /** A new `EffectsLayerSettings`, for comparing against its defaults. */
    fun defaultEffects(): EffectsLayerSettings = EffectsLayerSettings()

    fun value(params: MandalaParameters, slider: EffectSlider): Double = slider.get(params.effectsLayer)

    fun withValue(params: MandalaParameters, slider: EffectSlider, value: Double): MandalaParameters =
        params.updateEffects { slider.set(it, value) }

    fun withEffectsEnabled(params: MandalaParameters, on: Boolean): MandalaParameters = params.updateEffects { it.copy(isEnabled = on) }

    /** The effects die beside the switch: a new look ([Randomize.randomized]). */
    fun randomizedEffects(params: MandalaParameters): MandalaParameters = params.updateEffects { it.randomized(Random) }

    fun resetEffects(params: MandalaParameters): MandalaParameters = params.updateEffects { it.reset() }

    fun newSparklePattern(params: MandalaParameters): MandalaParameters = params.updateEffects { it.newSparklePattern(::seed) }

    fun newLightPattern(params: MandalaParameters): MandalaParameters = params.updateEffects { it.newLightPattern(::seed) }

    fun withEffectsOnEmblem(params: MandalaParameters, on: Boolean): MandalaParameters = params.updateEffects { it.withEmblem(on) }

    fun withEffectsOnText(params: MandalaParameters, on: Boolean): MandalaParameters = params.updateEffects { it.withText(on) }

    /** The picture while a finger holds the canvas: the same settings with the effects off. */
    fun withoutEffects(params: MandalaParameters): MandalaParameters = params.updateEffects { it.copy(isEnabled = false) }
}

/** The settings after a layer operation and the layer to select. */
class Edited(val params: MandalaParameters, val selected: Int)

/**
 * One slider of the Effects cards with its range ([Editing.Ranges]) and default (a new
 * `EffectsLayerSettings`), as [LayerSlider] is for a layer.
 */
enum class EffectSlider(
    val min: Double,
    val max: Double,
    internal val get: (EffectsLayerSettings) -> Double,
    internal val set: (EffectsLayerSettings, Double) -> EffectsLayerSettings,
) {
    BRIGHTNESS(0.0, 1.0, { it.brightness }, { e, v -> e.copy(brightness = v) }),
    CONTRAST(0.0, 1.0, { it.contrast }, { e, v -> e.copy(contrast = v) }),
    VIGNETTE(Editing.Ranges.effectsVignette.start, Editing.Ranges.effectsVignette.endInclusive, { it.vignette }, { e, v -> e.copy(vignette = v) }),
    BLOOM(Editing.Ranges.effectsBloom.start, Editing.Ranges.effectsBloom.endInclusive, { it.bloom }, { e, v -> e.copy(bloom = v) }),
    SEPIA(0.0, 1.0, { it.sepia }, { e, v -> e.copy(sepia = v) }),

    /** "Fade": the `wash` field. */
    FADE(0.0, 1.0, { it.wash }, { e, v -> e.copy(wash = v) }),
    CLARITY(0.0, 1.0, { it.localContrast }, { e, v -> e.copy(localContrast = v) }),
    GRAIN(Editing.Ranges.effectsGrain.start, Editing.Ranges.effectsGrain.endInclusive, { it.grain }, { e, v -> e.copy(grain = v) }),
    SMOOTHER(0.0, 1.0, { it.smoother }, { e, v -> e.copy(smoother = v) }),
    STARS(0.0, 1.0, { it.stars }, { e, v -> e.copy(stars = v) }),
    GLITTER(Editing.Ranges.effectsGlitter.start, Editing.Ranges.effectsGlitter.endInclusive, { it.glitter }, { e, v -> e.copy(glitter = v) }),
    HIGHLIGHTS(0.0, 1.0, { it.highlights }, { e, v -> e.copy(highlights = v) }),
    DIMMING(0.0, 1.0, { it.dimming }, { e, v -> e.copy(dimming = v) }),
    ERASURE(0.0, 1.0, { it.erasure }, { e, v -> e.copy(erasure = v) }),
    RELIEF(0.0, 1.0, { it.relief }, { e, v -> e.copy(relief = v) }),
    RELIEF_ANGLE(0.0, 1.0, { it.reliefAngle }, { e, v -> e.copy(reliefAngle = v) }),
    CHROMATIC(0.0, 1.0, { it.chromatic }, { e, v -> e.copy(chromatic = v) }),

    /** "Old fade": the `fade` field, shown only in settings that use it. */
    OLD_FADE(0.0, 1.0, { it.fade }, { e, v -> e.copy(fade = v) }),
    ;

    val defaultValue: Double get() = get(EffectsLayerSettings())
}

/** One slider of the background controls, with its range and default (a new `BaseLayerSettings`). */
enum class BackgroundSlider(
    val min: Double,
    val max: Double,
    internal val get: (BaseLayerSettings) -> Double,
    internal val set: (BaseLayerSettings, Double) -> BaseLayerSettings,
) {
    OPACITY(0.0, 1.0, { it.opacity }, { b, v -> b.copy(opacity = v) }),
    PICTURE_STRENGTH(0.0, 1.0, { it.imageBlend }, { b, v -> b.copy(imageBlend = v) }),
    HUE(0.0, 1.0, { it.hue }, { b, v -> b.copy(hue = v) }),
    SATURATION(0.0, 1.0, { it.saturation }, { b, v -> b.copy(saturation = v) }),
    BRIGHTNESS(Editing.Ranges.backgroundBrightness.start, Editing.Ranges.backgroundBrightness.endInclusive, { it.brightness }, { b, v -> b.copy(brightness = v) }),
    HUE2(0.0, 1.0, { it.hue2 }, { b, v -> b.copy(hue2 = v) }),
    SATURATION2(0.0, 1.0, { it.saturation2 }, { b, v -> b.copy(saturation2 = v) }),
    BRIGHTNESS2(Editing.Ranges.backgroundBrightness.start, Editing.Ranges.backgroundBrightness.endInclusive, { it.brightness2 }, { b, v -> b.copy(brightness2 = v) }),
    ANGLE(0.0, 1.0, { it.gradientAngle }, { b, v -> b.copy(gradientAngle = v) }),
    RAYS(0.0, 1.0, { it.patternScale }, { b, v -> b.copy(patternScale = v) }),
    SHARPNESS(0.0, 1.0, { it.patternSharpness }, { b, v -> b.copy(patternSharpness = v) }),
    GRAIN_AMOUNT(0.0, 1.0, { it.grainAmount }, { b, v -> b.copy(grainAmount = v) }),
    ;

    val defaultValue: Double get() = get(BaseLayerSettings())
}


/** Which group of the Layers tab a slider sits in. */
enum class SliderGroup { SHAPE, LIGHT, BLENDING, POSITION }

/**
 * One slider of the Layers tab with its range ([Editing.Ranges]), its default (a new
 * `StyleLayer`) and whether it moves in whole steps. A saved value outside the range is kept until
 * the slider is moved, as on Android.
 */
enum class LayerSlider(
    val group: SliderGroup,
    val min: Double,
    val max: Double,
    val stepped: Boolean,
    internal val get: (StyleLayer) -> Double,
    internal val set: (StyleLayer, Double) -> StyleLayer,
) {
    SYMMETRY(SliderGroup.SHAPE, 1.0, 8.0, true, { it.symmetry.toDouble() }, { l, v -> l.copy(symmetry = kotlin.math.round(v).toInt()) }),
    SCALE(SliderGroup.SHAPE, Editing.Ranges.layerScale.start, Editing.Ranges.layerScale.endInclusive, false, { it.scale }, { l, v -> l.copy(scale = v) }),
    COMPLEXITY(SliderGroup.SHAPE, Editing.Ranges.layerComplexity.start, Editing.Ranges.layerComplexity.endInclusive, false, { it.complexity }, { l, v -> l.copy(complexity = v) }),
    SPOKES(SliderGroup.SHAPE, 0.0, 1.0, false, { it.spokes }, { l, v -> l.copy(spokes = v) }),
    DENSITY(SliderGroup.SHAPE, 0.0, 1.0, false, { it.density }, { l, v -> l.copy(density = v) }),
    RIPPLE(SliderGroup.SHAPE, 0.0, 1.0, false, { it.ripple }, { l, v -> l.copy(ripple = v) }),
    OPACITY(SliderGroup.BLENDING, 0.0, 1.0, false, { it.opacity }, { l, v -> l.copy(opacity = v) }),
    OFFSET_X(SliderGroup.POSITION, -Editing.MAX_OFFSET, Editing.MAX_OFFSET, false, { it.offsetX }, { l, v -> l.copy(offsetX = v) }),
    OFFSET_Y(SliderGroup.POSITION, -Editing.MAX_OFFSET, Editing.MAX_OFFSET, false, { it.offsetY }, { l, v -> l.copy(offsetY = v) }),
    GLOW(SliderGroup.LIGHT, 0.0, 1.0, false, { it.glowIntensity }, { l, v -> l.copy(glowIntensity = v) }),
    COLOUR_DRIFT(SliderGroup.LIGHT, 0.0, 1.0, false, { it.colorDrift }, { l, v -> l.copy(colorDrift = v) }),
    PAINTED(SliderGroup.LIGHT, Editing.Ranges.layerPainted.start, Editing.Ranges.layerPainted.endInclusive, false, { it.abstractLevel }, { l, v -> l.copy(abstractLevel = v) }),
    SATURATION(SliderGroup.LIGHT, 0.0, 1.0, false, { it.saturation }, { l, v -> l.copy(saturation = v) }),
    BRIGHTNESS(SliderGroup.LIGHT, Editing.Ranges.layerBrightness.start, Editing.Ranges.layerBrightness.endInclusive, false, { it.brightness }, { l, v -> l.copy(brightness = v) }),
    ;

    // Not `default`: a keyword in Swift.
    val defaultValue: Double get() = get(StyleLayer())

    /** The upper end for a layer of `style`: Strange Attractor burns to a white disc above 0.5 complexity. */
    fun maxFor(style: MandalaStyle): Double =
        if (this == COMPLEXITY) Editing.Ranges.layerComplexity(style).endInclusive else max

    internal fun appliesTo(style: MandalaStyle): Boolean = when (this) {
        SPOKES -> style == MandalaStyle.RADIAL_MESH
        DENSITY -> style in Editing.densityStyles
        RIPPLE -> style in Editing.rippleStyles
        else -> true
    }
}
