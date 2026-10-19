package com.mathis.mandala.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

// Settings model, field for field compatible with the macOS app's MandalaParameters.swift.
// Defaults mirror the Swift `decodeSafe` defaults so partial or older files load the same way.

@Serializable
enum class MandalaStyle {
    // Parametric curves
    @SerialName("spirograph") SPIROGRAPH,
    @SerialName("roseCurves") ROSE_CURVES,
    @SerialName("floral") FLORAL,
    @SerialName("butterfly") BUTTERFLY,
    @SerialName("epitrochoid") EPITROCHOID,
    @SerialName("lissajous") LISSAJOUS,
    @SerialName("hypocycloid") HYPOCYCLOID,
    @SerialName("phyllotaxis") PHYLLOTAXIS,
    @SerialName("fractal") FRACTAL,
    @SerialName("superformula") SUPERFORMULA,
    @SerialName("waveInterference") WAVE_INTERFERENCE,

    // Geometric and pattern
    @SerialName("geometric") GEOMETRIC,
    @SerialName("sacredGeometry") SACRED_GEOMETRY,
    @SerialName("sunburst") SUNBURST,
    @SerialName("starBurst") STAR_BURST,
    @SerialName("stringArt") STRING_ART,
    @SerialName("spiderWeb") SPIDER_WEB,
    @SerialName("weave") WEAVE,
    @SerialName("radialMesh") RADIAL_MESH,
    @SerialName("moire") MOIRE,

    // Organic and flow
    @SerialName("flowField") FLOW_FIELD,
    @SerialName("tendril") TENDRIL,
    @SerialName("voronoi") VORONOI,
    @SerialName("strangeAttractor") STRANGE_ATTRACTOR,
    @SerialName("constellationWeb") CONSTELLATION_WEB,
    @SerialName("deepNebula") DEEP_NEBULA,

    // Optical and interference
    @SerialName("interferenceBloom") INTERFERENCE_BLOOM,

    // 3D
    @SerialName("hyperboloid") HYPERBOLOID,
    @SerialName("torus") TORUS,
    @SerialName("nautilus") NAUTILUS,
    @SerialName("sphereGrid") SPHERE_GRID,
    @SerialName("torusKnot") TORUS_KNOT,
    @SerialName("tesseract") TESSERACT,

    // Special
    @SerialName("universe") UNIVERSE,
    @SerialName("symbols") SYMBOLS,
    @SerialName("mixed") MIXED,
}

@Serializable
enum class LayerBlendMode {
    @SerialName("screen") SCREEN,
    @SerialName("add") ADD,
    @SerialName("normal") NORMAL,
    @SerialName("multiply") MULTIPLY,
}

@Serializable
enum class BaseLayerType {
    @SerialName("auto") AUTO,
    @SerialName("color") COLOR,
    @SerialName("gradient") GRADIENT,
    @SerialName("sunburst") SUNBURST,
    @SerialName("grain") GRAIN,
    @SerialName("image") IMAGE,
}

@Serializable
data class StyleLayer(
    val isEnabled: Boolean = true,
    val style: MandalaStyle = MandalaStyle.MIXED,
    val scale: Double = 1.0,
    val paletteIndex: Int = 0,
    val colorOffset: Double = 0.0,
    val complexity: Double = 0.6,
    val density: Double = 0.5,
    val glowIntensity: Double = 0.6,
    val colorDrift: Double = 0.4,
    val ripple: Double = 0.0,
    val wash: Double = 0.0,
    val abstractLevel: Double = 0.3,
    val saturation: Double = 0.7,
    val brightness: Double = 0.5,
    val rotation: Double = 0.0,
    val opacity: Double = 1.0,
    val blendMode: LayerBlendMode = LayerBlendMode.SCREEN,
    val symmetry: Int = 6,
    val seed: ULong = 42uL,
)

@Serializable
data class BaseLayerSettings(
    val isEnabled: Boolean = false,
    val type: BaseLayerType = BaseLayerType.GRADIENT,
    val hue: Double = 0.75,
    val saturation: Double = 0.8,
    val brightness: Double = 0.18,
    val hue2: Double = 0.88,
    val saturation2: Double = 0.9,
    val brightness2: Double = 0.04,
    val isRadial: Boolean = true,
    val gradientAngle: Double = 0.0,
    val patternType: Int = 0,
    val patternScale: Double = 0.3,
    val patternSharpness: Double = 0.7,
    val grainAmount: Double = 0.4,
    val grainColored: Boolean = true,
    val imageURL: String? = null,
    val imageBlend: Double = 1.0,
    val opacity: Double = 1.0,
)

@Serializable
data class EffectsLayerSettings(
    val isEnabled: Boolean = false,
    val dimming: Double = 0.0,
    val erasure: Double = 0.0,
    val highlights: Double = 0.0,
    val stars: Double = 0.0,
    val vignette: Double = 0.3,
    val chromatic: Double = 0.0,
    val brightness: Double = 0.5,
    val contrast: Double = 0.5,
    val relief: Double = 0.0,
    val reliefAngle: Double = 0.125,
    val wash: Double = 0.0,
    val sepia: Double = 0.0,
    val fade: Double = 0.0,
    val bloom: Double = 0.0,
    val grain: Double = 0.0,
    val glitter: Double = 0.0,
    val localContrast: Double = 0.0,
    val dimmingSeed: ULong = 11uL,
    val erasureSeed: ULong = 22uL,
    val highlightsSeed: ULong = 33uL,
    val starsSeed: ULong = 44uL,
    val glitterSeed: ULong = 55uL,
)

@Serializable
data class MandalaParameters(
    val layers: List<StyleLayer> = listOf(StyleLayer()),
    val baseLayer: BaseLayerSettings = BaseLayerSettings(),
    val effectsLayer: EffectsLayerSettings = EffectsLayerSettings(),
    // Not rendered yet; kept as raw JSON so loading and saving preserves them.
    val graffitiLayer: JsonObject? = null,
    val drawingLayer: JsonObject? = null,
    val textLayer: JsonObject? = null,
    val seed: ULong = 42uL,
    val previewSize: Int = 800,
    val outputSize: Int = 1024,
    val outputSizeCustom: Int = 2048,
    val outputFormat: String = "png",
    val outputShape: String = "square",
    // Legacy single-layer fields, still written by the macOS app.
    val symmetry: Int = 6,
    val complexity: Double = 0.6,
    val density: Double = 0.5,
    val glowIntensity: Double = 0.6,
    val colorDrift: Double = 0.4,
    val ripple: Double = 0.0,
    val wash: Double = 0.0,
    val abstractLevel: Double = 0.3,
    val paletteIndex: Int = 0,
) {
    /** Export size after resolving the "custom" preset (outputSize == 0). */
    val resolvedOutputSize: Int
        get() = if (outputSize == 0) outputSizeCustom.coerceIn(64, 8192) else outputSize
}

internal fun JsonObject?.isEnabledLayer(): Boolean =
    this?.get("isEnabled")?.jsonPrimitive?.booleanOrNull == true
