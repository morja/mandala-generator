package com.mathis.mandala.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

// Settings model, field for field compatible with the macOS app's MandalaParameters.swift.
// Defaults mirror the Swift `decodeSafe` defaults so partial or older files load the same way.

@Serializable
/** Drawing styles; `displayName` is the label the macOS app shows. */
enum class MandalaStyle(val displayName: String) {
    // Parametric curves
    @SerialName("spirograph") SPIROGRAPH("Spirograph"),
    @SerialName("roseCurves") ROSE_CURVES("Rose Curves"),
    @SerialName("floral") FLORAL("Floral"),
    @SerialName("butterfly") BUTTERFLY("Butterfly"),
    @SerialName("epitrochoid") EPITROCHOID("Epitrochoid"),
    @SerialName("lissajous") LISSAJOUS("Lissajous"),
    @SerialName("hypocycloid") HYPOCYCLOID("Hypocycloid"),
    @SerialName("phyllotaxis") PHYLLOTAXIS("Phyllotaxis"),
    @SerialName("fractal") FRACTAL("Fractal"),
    @SerialName("superformula") SUPERFORMULA("Superformula"),
    @SerialName("waveInterference") WAVE_INTERFERENCE("Wave Interference"),

    // Geometric and pattern
    @SerialName("geometric") GEOMETRIC("Geometric"),
    @SerialName("sacredGeometry") SACRED_GEOMETRY("Sacred Geometry"),
    @SerialName("sunburst") SUNBURST("Sunburst"),
    @SerialName("starBurst") STAR_BURST("Star Burst"),
    @SerialName("stringArt") STRING_ART("String Art"),
    @SerialName("spiderWeb") SPIDER_WEB("Spider Web"),
    @SerialName("weave") WEAVE("Weave"),
    @SerialName("radialMesh") RADIAL_MESH("Radial Mesh"),
    @SerialName("moire") MOIRE("Moiré"),

    // Organic and flow
    @SerialName("flowField") FLOW_FIELD("Flow Field"),
    @SerialName("tendril") TENDRIL("Tendril"),
    @SerialName("voronoi") VORONOI("Voronoi"),
    @SerialName("strangeAttractor") STRANGE_ATTRACTOR("Strange Attractor"),
    @SerialName("constellationWeb") CONSTELLATION_WEB("Constellation Web"),
    @SerialName("deepNebula") DEEP_NEBULA("Deep Nebula"),

    // Optical and interference
    @SerialName("interferenceBloom") INTERFERENCE_BLOOM("Interference Bloom"),

    // 3D
    @SerialName("hyperboloid") HYPERBOLOID("Hyperboloid"),
    @SerialName("torus") TORUS("Torus"),
    @SerialName("nautilus") NAUTILUS("Nautilus"),
    @SerialName("sphereGrid") SPHERE_GRID("Sphere Grid"),
    @SerialName("torusKnot") TORUS_KNOT("Torus Knot"),
    @SerialName("tesseract") TESSERACT("Tesseract"),

    // Special
    @SerialName("universe") UNIVERSE("Universe"),
    @SerialName("symbols") SYMBOLS("Symbols"),
    @SerialName("mixed") MIXED("Mixed"),
    ;
}

/** How a layer is combined with the layers below; `displayName` as in the macOS app. */
@Serializable
enum class LayerBlendMode(val displayName: String) {
    @SerialName("screen") SCREEN("Screen"),
    @SerialName("add") ADD("Add"),
    @SerialName("normal") NORMAL("Normal"),
    @SerialName("multiply") MULTIPLY("Multiply"),
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
    /** Radial Mesh only: brightness of the spokes, 0 = rings only. */
    val spokes: Double = 1.0,
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

/** Like the macOS renderer: a drawing or graffiti layer only counts when it has strokes. */
internal fun JsonObject?.hasStrokes(): Boolean =
    isEnabledLayer() && ((this?.get("strokes") as? JsonArray)?.isNotEmpty() == true)
