package com.mathis.mandala.core

/**
 * The few editing rules the iPhone app's first build (PLAN-4 WP-1) needs, with plain types for Swift.
 *
 * TEMPORARY (WP-3): these mirror `Editing.kt` of the Android app (style order, offered palettes,
 * slider ranges, which styles read density and ripple, the file name). WP-3 moves the Android rules
 * into the shared module `android/editing`; then this file goes and the iPhone app calls those.
 * Keep it this small until then: no rule here that the Android app does not have.
 */
object IosEditing {
    /** The style picker's order on Android: by English name (`Editing.stylesByName`). */
    fun stylesByName(): List<MandalaStyle> =
        MandalaStyle.entries.filter { it in MandalaRenderer.supportedStyles }.sortedBy { it.displayName }

    /** All built-in palettes; a layer's `paletteIndex` points into this list. */
    fun palettes(): List<ColorPalette> = BuiltInPalettes.all

    /** Android's offered palettes in rows of three by family, plus `current` at the end if it is a hidden one (`Editing.paletteChoices`). */
    fun paletteChoices(current: Int): List<Int> =
        if (current in BuiltInPalettes.all.indices && current !in OFFERED_PALETTES) OFFERED_PALETTES + current else OFFERED_PALETTES

    /** The palette a layer draws with: its own one if it has one, else the built-in one. */
    fun paletteOf(layer: StyleLayer): ColorPalette = OwnPalettes.resolve(layer.paletteIndex, layer.customPalette, BuiltInPalettes.all)

    /** The sliders shown for a layer of `style`, in Android's order (Shape, then Light and colour). */
    fun sliders(style: MandalaStyle): List<LayerSlider> = LayerSlider.entries.filter { it.appliesTo(style) }

    fun value(params: MandalaParameters, layer: Int, slider: LayerSlider): Double =
        params.layers.getOrNull(layer)?.let(slider.get) ?: slider.defaultValue

    fun withValue(params: MandalaParameters, layer: Int, slider: LayerSlider, value: Double): MandalaParameters =
        update(params, layer) { slider.set(it, value) }

    fun withStyle(params: MandalaParameters, layer: Int, style: MandalaStyle): MandalaParameters =
        update(params, layer) { it.copy(style = style) }

    /** A built-in palette replaces an own one, as tapping a swatch does on Android. */
    fun withPalette(params: MandalaParameters, layer: Int, index: Int): MandalaParameters =
        update(params, layer) { it.copy(paletteIndex = index, customPalette = null) }

    fun withVisible(params: MandalaParameters, layer: Int, visible: Boolean): MandalaParameters =
        update(params, layer) { it.copy(isEnabled = visible) }

    /** Whether the layer uses a built-in palette (no own one). */
    fun usesBuiltInPalette(layer: StyleLayer): Boolean = layer.customPalette?.stops?.isNotEmpty() != true

    /** The macOS app's file name scheme, as `Editing.fileName` on Android (the 1024 square has no size suffix). */
    fun fileName(params: MandalaParameters): String {
        val styles = params.layers.take(2).joinToString("+") { it.style.displayName.take(4).lowercase() }
        val first = params.layers.first()
        val sym = if (first.symmetry > 1) "-x${first.symmetry}" else ""
        var h = 0xcbf29ce484222325uL
        for (b in SettingsJson.encode(params).encodeToByteArray()) h = (h xor (b.toULong() and 0xFFuL)) * 0x100000001b3uL
        return "mandala-$styles-p${first.paletteIndex}$sym-${h.toString(16).padStart(16, '0').take(8)}.png"
    }

    private fun update(params: MandalaParameters, index: Int, change: (StyleLayer) -> StyleLayer): MandalaParameters {
        if (index !in params.layers.indices) return params
        return params.copy(layers = params.layers.toMutableList().also { it[index] = change(it[index]) })
    }

    private val OFFERED_PALETTES = listOf(0, 3, 13, 4, 16, 17, 19, 7, 9, 14, 6, 1, 11, 20, 18)

    /** `Editing.densityStyles` on Android. */
    internal val DENSITY_STYLES: Set<MandalaStyle> = MandalaStyle.entries.toSet() - setOf(
        MandalaStyle.SPIROGRAPH, MandalaStyle.ROSE_CURVES, MandalaStyle.BUTTERFLY, MandalaStyle.EPITROCHOID,
        MandalaStyle.LISSAJOUS, MandalaStyle.HYPOCYCLOID, MandalaStyle.GEOMETRIC, MandalaStyle.SUNBURST, MandalaStyle.STRING_ART,
    )

    /** `Editing.rippleStyles` on Android. */
    internal val RIPPLE_STYLES: Set<MandalaStyle> = MandalaStyle.entries.toSet() - setOf(
        MandalaStyle.SACRED_GEOMETRY, MandalaStyle.SUNBURST, MandalaStyle.STAR_BURST, MandalaStyle.STRING_ART,
        MandalaStyle.STRANGE_ATTRACTOR, MandalaStyle.CONSTELLATION_WEB, MandalaStyle.DEEP_NEBULA, MandalaStyle.INTERFERENCE_BLOOM,
        MandalaStyle.HYPERBOLOID, MandalaStyle.TORUS, MandalaStyle.NAUTILUS, MandalaStyle.UNIVERSE, MandalaStyle.SYMBOLS,
    )
}

/** Which group of the Layers tab a slider sits in. */
enum class SliderGroup { SHAPE, LIGHT }

/**
 * One slider of the Layers tab with its range (`Editing.Ranges` on Android), its default (a new
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
    SCALE(SliderGroup.SHAPE, 0.2, 1.2, false, { it.scale }, { l, v -> l.copy(scale = v) }),
    COMPLEXITY(SliderGroup.SHAPE, 0.15, 1.0, false, { it.complexity }, { l, v -> l.copy(complexity = v) }),
    SPOKES(SliderGroup.SHAPE, 0.0, 1.0, false, { it.spokes }, { l, v -> l.copy(spokes = v) }),
    DENSITY(SliderGroup.SHAPE, 0.0, 1.0, false, { it.density }, { l, v -> l.copy(density = v) }),
    RIPPLE(SliderGroup.SHAPE, 0.0, 1.0, false, { it.ripple }, { l, v -> l.copy(ripple = v) }),
    GLOW(SliderGroup.LIGHT, 0.0, 1.0, false, { it.glowIntensity }, { l, v -> l.copy(glowIntensity = v) }),
    COLOUR_DRIFT(SliderGroup.LIGHT, 0.0, 1.0, false, { it.colorDrift }, { l, v -> l.copy(colorDrift = v) }),
    PAINTED(SliderGroup.LIGHT, 0.25, 0.85, false, { it.abstractLevel }, { l, v -> l.copy(abstractLevel = v) }),
    SATURATION(SliderGroup.LIGHT, 0.0, 1.0, false, { it.saturation }, { l, v -> l.copy(saturation = v) }),
    BRIGHTNESS(SliderGroup.LIGHT, 0.15, 0.85, false, { it.brightness }, { l, v -> l.copy(brightness = v) }),
    ;

    // Not `default`: a keyword in Swift.
    val defaultValue: Double get() = get(StyleLayer())

    /** The upper end for a layer of `style`: Strange Attractor burns to a white disc above 0.5 complexity. */
    fun maxFor(style: MandalaStyle): Double =
        if (this == COMPLEXITY && style == MandalaStyle.STRANGE_ATTRACTOR) 0.5 else max

    internal fun appliesTo(style: MandalaStyle): Boolean = when (this) {
        SPOKES -> style == MandalaStyle.RADIAL_MESH
        DENSITY -> style in IosEditing.DENSITY_STYLES
        RIPPLE -> style in IosEditing.RIPPLE_STYLES
        else -> true
    }
}
