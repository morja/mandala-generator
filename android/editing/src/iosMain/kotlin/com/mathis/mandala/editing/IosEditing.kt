package com.mathis.mandala.editing

import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.ColorPalette
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaStyle
import com.mathis.mandala.core.OwnPalettes
import com.mathis.mandala.core.StyleLayer
import com.mathis.mandala.editing.Editing.updateLayer

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
}

/** Which group of the Layers tab a slider sits in. */
enum class SliderGroup { SHAPE, LIGHT }

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
