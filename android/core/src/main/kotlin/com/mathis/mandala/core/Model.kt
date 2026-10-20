package com.mathis.mandala.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    /** Where the layer's centre sits, in fractions of the mandala's square from its centre (right, down); 0 = centred (ADR-0011). */
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
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

/** A line drawn by hand; points are normalised to the canvas (0.5 = centre, y downwards). */
@Serializable
data class DrawStroke(
    val xs: List<Double> = emptyList(),
    val ys: List<Double> = emptyList(),
    val hue: Double = 0.6,
    val saturation: Double = 1.0,
    val brightness: Double = 0.9,
)

/** Hand-drawn glowing lines, repeated around the centre, as in the macOS DrawingLayerSettings. */
@Serializable
data class DrawingLayerSettings(
    val isEnabled: Boolean = true,
    val strokes: List<DrawStroke> = emptyList(),
    val symmetry: Int = 6,
    val glowIntensity: Double = 0.5,
    val strokeWeight: Double = 0.4,
    val blendMode: LayerBlendMode = LayerBlendMode.SCREEN,
    val opacity: Double = 1.0,
    // Colour for new strokes (tool state, not rendered).
    val currentHue: Double = 0.6,
    val currentSaturation: Double = 1.0,
    val currentBrightness: Double = 0.9,
)

/** A spray-paint stroke; brush size is a fraction of the canvas width. */
@Serializable
data class SprayStroke(
    val xs: List<Double> = emptyList(),
    val ys: List<Double> = emptyList(),
    val brushSize: Double = 0.05,
    val hue: Double = 0.5,
    val saturation: Double = 0.8,
    val brightness: Double = 0.9,
    val opacity: Double = 0.6,
)

/** Soft spray paint, as in the macOS GraffitiLayerSettings. */
@Serializable
data class GraffitiLayerSettings(
    val isEnabled: Boolean = false,
    val strokes: List<SprayStroke> = emptyList(),
    val symmetry: Int = 1,
    val blendMode: LayerBlendMode = LayerBlendMode.SCREEN,
    val opacity: Double = 1.0,
    /** 0 = hard edge, 1 = very soft. */
    val softness: Double = 0.7,
    // Tool state for new strokes (not rendered).
    val currentHue: Double = 0.5,
    val currentSaturation: Double = 0.8,
    val currentBrightness: Double = 0.9,
    val currentBrushSize: Double = 0.05,
    val currentOpacity: Double = 0.6,
)

/** Text over the mandala (quotes), as in the macOS TextLayerSettings. Defaults match its decoder. */
@Serializable
data class TextLayerSettings(
    val isEnabled: Boolean = false,
    val text: String = "",
    val fontName: String = "Georgia",
    /** Fraction of the canvas width. */
    val fontSize: Double = 0.07,
    val glow: Double = 0.0,
    val shadowOpacity: Double = 0.0,
    val shadowBlur: Double = 0.5,
    val shadowOffsetX: Double = 0.3,
    val shadowOffsetY: Double = -0.3,
    val shadowHue: Double = 0.0,
    val shadowSaturation: Double = 0.0,
    val shadowBrightness: Double = 0.0,
    val cloudOpacity: Double = 0.0,
    val cloudScale: Double = 0.5,
    val cloudRadius: Double = 0.5,
    val cloudHue: Double = 0.0,
    val cloudSaturation: Double = 0.0,
    val cloudBrightness: Double = 0.0,
    val blur: Double = 0.0,
    val opacity: Double = 1.0,
    val hue: Double = 0.0,
    val saturation: Double = 0.0,
    val brightness: Double = 1.0,
    val tracking: Double = 0.0,
    /** Text box width as a fraction of the canvas. */
    val fontWidth: Double = 0.82,
    val offsetX: Double = 0.5,
    /** Vertical centre of the text block, 0 = bottom, 1 = top. */
    val offsetY: Double = 0.38,
    val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    val showAuthor: Boolean = true,
    val customAuthor: String = "",
    val authorScale: Double = 0.667,
    val authorItalic: Boolean = true,
)

/** One motif drawn large and upright in the centre (not repeated by symmetry), as in the macOS EmblemMotif. */
@Serializable
enum class EmblemMotif(val displayName: String, /** Whether the Organic slider changes this motif. */ val isOrganic: Boolean) {
    @SerialName("om") OM("Om", true),
    @SerialName("treeOfLife") TREE_OF_LIFE("Tree of Life", true),
    @SerialName("flowerOfLife") FLOWER_OF_LIFE("Flower of Life", false),
    @SerialName("lotus") LOTUS("Lotus", true),
    @SerialName("yinYang") YIN_YANG("Yin Yang", false),
    @SerialName("dharmaWheel") DHARMA_WHEEL("Dharma Wheel", false),
    @SerialName("unalome") UNALOME("Unalome", true),
    @SerialName("tripleMoon") TRIPLE_MOON("Triple Moon", false),
    @SerialName("sriYantra") SRI_YANTRA("Sri Yantra", false),
}

/** The members of the Flower of Life family. */
@Serializable
enum class EmblemFlowerVariant(val displayName: String) {
    @SerialName("seed") SEED("Seed"),
    @SerialName("flower") FLOWER("Flower"),
    @SerialName("fruit") FRUIT("Fruit"),
    @SerialName("metatron") METATRON("Metatron"),
}

/** How the motif's strokes are drawn. */
@Serializable
enum class EmblemRendering(val displayName: String) {
    @SerialName("line") LINE("Line"),
    @SerialName("doubleLine") DOUBLE_LINE("Outline"),
    @SerialName("filled") FILLED("Filled"),
    @SerialName("beads") BEADS("Beads"),
}

/** Frame around the motif. */
@Serializable
enum class EmblemFrame(val displayName: String) {
    @SerialName("none") NONE("None"),
    @SerialName("ring") RING("Ring"),
    @SerialName("doubleRing") DOUBLE_RING("Double Ring"),
    @SerialName("lotus") LOTUS("Lotus Ring"),
    @SerialName("gates") GATES("Square Gates"),
    @SerialName("enso") ENSO("Ensō"),
}

/** One motif large in the centre, as in the macOS EmblemLayerSettings. Defaults match its decoder. */
@Serializable
data class EmblemLayerSettings(
    val isEnabled: Boolean = false,
    val motif: EmblemMotif = EmblemMotif.OM,
    val rendering: EmblemRendering = EmblemRendering.FILLED,
    val frame: EmblemFrame = EmblemFrame.RING,
    /** Frame ring diameter as a fraction of the canvas (0.1–1.0). */
    val size: Double = 0.6,
    /** Stroke thickness (0–1). */
    val weight: Double = 0.5,
    val paletteIndex: Int = 0,
    /** Where on the palette the centre colour sits (0–1). */
    val colorPosition: Double = 0.2,
    /** How far the colour moves along the palette towards the edge (0–1). */
    val colorSpread: Double = 0.3,
    val glow: Double = 0.5,
    val opacity: Double = 1.0,
    /** Normal by default: stays visible on bright mandalas, where Screen cannot brighten white. */
    val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    /** Tree of Life: shape of the tree. */
    val seed: ULong = 1uL,
    /** Tree of Life: number of twigs (0–1). */
    val branching: Double = 0.5,
    val roots: Boolean = true,
    /** Om, Tree of Life, Lotus, Unalome: calligraphic strokes, flowing branches, uneven petals (0–1). */
    val organic: Double = 0.5,
    val flowerVariant: EmblemFlowerVariant = EmblemFlowerVariant.FLOWER,
    /** Lotus: petals in the inner rings. */
    val petals: Int = 8,
    /** Degrees, motif and frame together (-180–180). */
    val rotation: Double = 0.0,
    /** Where the emblem's centre sits, in fractions of the mandala's square from its centre (right, down); 0 = centred (ADR-0011). */
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
)

@Serializable
data class MandalaParameters(
    val layers: List<StyleLayer> = listOf(StyleLayer()),
    val baseLayer: BaseLayerSettings = BaseLayerSettings(),
    val effectsLayer: EffectsLayerSettings = EffectsLayerSettings(),
    val graffitiLayer: GraffitiLayerSettings = GraffitiLayerSettings(),
    val drawingLayer: DrawingLayerSettings = DrawingLayerSettings(),
    val textLayer: TextLayerSettings = TextLayerSettings(),
    val emblemLayer: EmblemLayerSettings = EmblemLayerSettings(),
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
