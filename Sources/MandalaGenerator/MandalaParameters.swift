import Foundation

// MARK: - Base Layer

enum BaseLayerType: String, CaseIterable, Identifiable, Codable {
    case auto, color, gradient, sunburst, grain, image
    var id: String { rawValue }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = (try? container.decode(String.self)) ?? ""
        self = BaseLayerType(rawValue: raw) ?? .gradient
    }
    var displayName: String {
        switch self {
        case .auto:     return "Auto"
        case .color:    return "Color"
        case .gradient: return "Gradient"
        case .sunburst: return "Sunburst"
        case .grain:    return "Grain"
        case .image:    return "Image"
        }
    }
    var sfSymbol: String {
        switch self {
        case .auto:     return "wand.and.stars"
        case .color:    return "square.fill"
        case .gradient: return "circle.lefthalf.filled"
        case .sunburst: return "rays"
        case .grain:    return "film.stack"
        case .image:    return "photo"
        }
    }
}

struct BaseLayerSettings: Equatable, Codable {
    var isEnabled: Bool = false
    var type: BaseLayerType = .gradient
    // Primary color (HSB 0-1)
    var hue: Double        = 0.75
    var saturation: Double = 0.8
    var brightness: Double = 0.18
    // Secondary color (HSB 0-1) — gradients and patterns
    var hue2: Double        = 0.88
    var saturation2: Double = 0.9
    var brightness2: Double = 0.04
    // Gradient
    var isRadial: Bool      = true
    var gradientAngle: Double = 0.0   // 0-1 → 0-360°, for linear mode
    // Pattern (0=Checkerboard, 1=Stripes, 2=Diagonal, 3=Crosshatch)
    var patternType: Int    = 0
    var patternScale: Double = 0.3    // 0-1
    var patternSharpness: Double = 0.7 // 0-1
    // Grain
    var grainAmount: Double = 0.4     // 0-1
    var grainColored: Bool  = true
    // Image
    var imageURL: URL?      = nil
    var imageBlend: Double  = 1.0     // 0-1 opacity
    // Overall
    var opacity: Double     = 1.0

    enum CodingKeys: String, CodingKey {
        case isEnabled, type, hue, saturation, brightness, hue2, saturation2, brightness2
        case isRadial, gradientAngle, patternType, patternScale, patternSharpness
        case grainAmount, grainColored, imageURL, imageBlend, opacity
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        isEnabled        = c.decodeSafe(Bool.self,           forKey: .isEnabled,        default: false)
        type             = c.decodeSafe(BaseLayerType.self,  forKey: .type,             default: .gradient)
        hue              = c.decodeSafe(Double.self,         forKey: .hue,              default: 0.75)
        saturation       = c.decodeSafe(Double.self,         forKey: .saturation,       default: 0.8)
        brightness       = c.decodeSafe(Double.self,         forKey: .brightness,       default: 0.18)
        hue2             = c.decodeSafe(Double.self,         forKey: .hue2,             default: 0.88)
        saturation2      = c.decodeSafe(Double.self,         forKey: .saturation2,      default: 0.9)
        brightness2      = c.decodeSafe(Double.self,         forKey: .brightness2,      default: 0.04)
        isRadial         = c.decodeSafe(Bool.self,           forKey: .isRadial,         default: true)
        gradientAngle    = c.decodeSafe(Double.self,         forKey: .gradientAngle,    default: 0.0)
        patternType      = c.decodeSafe(Int.self,            forKey: .patternType,      default: 0)
        patternScale     = c.decodeSafe(Double.self,         forKey: .patternScale,     default: 0.3)
        patternSharpness = c.decodeSafe(Double.self,         forKey: .patternSharpness, default: 0.7)
        grainAmount      = c.decodeSafe(Double.self,         forKey: .grainAmount,      default: 0.4)
        grainColored     = c.decodeSafe(Bool.self,           forKey: .grainColored,     default: true)
        imageURL         = c.decodeSafe(URL?.self,           forKey: .imageURL,         default: nil)
        imageBlend       = c.decodeSafe(Double.self,         forKey: .imageBlend,       default: 1.0)
        opacity          = c.decodeSafe(Double.self,         forKey: .opacity,          default: 1.0)
    }
}

// MARK: - Effects Layer

struct EffectsLayerSettings: Equatable, Codable {
    var isEnabled: Bool     = false
    var dimming: Double     = 0.0   // random dark blotches (multiply)
    var erasure: Double     = 0.0   // burn-through holes
    var highlights: Double  = 0.0   // additive bright glowing spots
    var stars: Double       = 0.0   // sharp bright sparkle points
    var vignette: Double    = 0.3   // edge darkening
    var chromatic: Double   = 0.0   // chromatic aberration RGB shift
    var brightness: Double  = 0.5   // 0=dark, 0.5=neutral, 1=bright
    var contrast: Double    = 0.5   // 0=flat, 0.5=neutral, 1=punchy
    var relief: Double      = 0.0   // 3D emboss/relief depth
    var reliefAngle: Double = 0.125 // 0–1 → 0–360° light direction
    // New tonal / atmospheric effects
    var wash: Double        = 0.0   // bleached / washed-out (push toward white)
    var sepia: Double       = 0.0   // warm sepia tone
    var fade: Double        = 0.0   // fade to flat gray / matte finish
    var bloom: Double       = 0.0   // soft wide glow bleed
    var grain: Double       = 0.0   // film grain / noise
    var glitter: Double     = 0.0   // dense tiny rainbow sparkles
    var localContrast: Double = 0.0 // mid-range clarity / local contrast enhancement
    // Per-effect seeds — each can be randomized independently
    var dimmingSeed: UInt64    = 11
    var erasureSeed: UInt64    = 22
    var highlightsSeed: UInt64 = 33
    var starsSeed: UInt64      = 44
    var glitterSeed: UInt64    = 55

    enum CodingKeys: String, CodingKey {
        case isEnabled, dimming, erasure, highlights, stars, vignette, chromatic
        case brightness, contrast, relief, reliefAngle
        case wash, sepia, fade, bloom, grain, glitter, localContrast
        case dimmingSeed, erasureSeed, highlightsSeed, starsSeed, glitterSeed
    }

    init(isEnabled: Bool = false) {
        self.isEnabled = isEnabled
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        isEnabled      = c.decodeSafe(Bool.self,   forKey: .isEnabled,      default: false)
        dimming        = c.decodeSafe(Double.self,  forKey: .dimming,        default: 0.0)
        erasure        = c.decodeSafe(Double.self,  forKey: .erasure,        default: 0.0)
        highlights     = c.decodeSafe(Double.self,  forKey: .highlights,     default: 0.0)
        stars          = c.decodeSafe(Double.self,  forKey: .stars,          default: 0.0)
        vignette       = c.decodeSafe(Double.self,  forKey: .vignette,       default: 0.3)
        chromatic      = c.decodeSafe(Double.self,  forKey: .chromatic,      default: 0.0)
        brightness     = c.decodeSafe(Double.self,  forKey: .brightness,     default: 0.5)
        contrast       = c.decodeSafe(Double.self,  forKey: .contrast,       default: 0.5)
        relief         = c.decodeSafe(Double.self,  forKey: .relief,         default: 0.0)
        reliefAngle    = c.decodeSafe(Double.self,  forKey: .reliefAngle,    default: 0.125)
        wash           = c.decodeSafe(Double.self,  forKey: .wash,           default: 0.0)
        sepia          = c.decodeSafe(Double.self,  forKey: .sepia,          default: 0.0)
        fade           = c.decodeSafe(Double.self,  forKey: .fade,           default: 0.0)
        bloom          = c.decodeSafe(Double.self,  forKey: .bloom,          default: 0.0)
        grain          = c.decodeSafe(Double.self,  forKey: .grain,          default: 0.0)
        glitter        = c.decodeSafe(Double.self,  forKey: .glitter,        default: 0.0)
        localContrast  = c.decodeSafe(Double.self,  forKey: .localContrast,  default: 0.0)
        dimmingSeed    = c.decodeSafe(UInt64.self,  forKey: .dimmingSeed,    default: 11)
        erasureSeed    = c.decodeSafe(UInt64.self,  forKey: .erasureSeed,    default: 22)
        highlightsSeed = c.decodeSafe(UInt64.self,  forKey: .highlightsSeed, default: 33)
        starsSeed      = c.decodeSafe(UInt64.self,  forKey: .starsSeed,      default: 44)
        glitterSeed    = c.decodeSafe(UInt64.self,  forKey: .glitterSeed,    default: 55)
    }
}

// MARK: - Drawing Layer

struct DrawStroke: Equatable, Codable {
    var xs: [Double]   // normalized 0–1, 0.5 = horizontal center
    var ys: [Double]   // normalized 0–1, 0.5 = vertical center
    var hue: Double        = 0.6
    var saturation: Double = 1.0
    var brightness: Double = 0.9

    enum CodingKeys: String, CodingKey { case xs, ys, hue, saturation, brightness }

    init(xs: [Double], ys: [Double], hue: Double = 0.6, saturation: Double = 1.0, brightness: Double = 0.9) {
        self.xs = xs
        self.ys = ys
        self.hue = hue
        self.saturation = saturation
        self.brightness = brightness
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        xs         = c.decodeSafe([Double].self, forKey: .xs,         default: [])
        ys         = c.decodeSafe([Double].self, forKey: .ys,         default: [])
        hue        = c.decodeSafe(Double.self,   forKey: .hue,        default: 0.6)
        saturation = c.decodeSafe(Double.self,   forKey: .saturation, default: 1.0)
        brightness = c.decodeSafe(Double.self,   forKey: .brightness, default: 0.9)
    }
}

struct DrawingLayerSettings: Equatable, Codable {
    var isEnabled: Bool    = true
    var strokes: [DrawStroke] = []
    var symmetry: Int      = 6
    var glowIntensity: Double = 0.5
    var strokeWeight: Double  = 0.4   // 0–1
    var blendMode: LayerBlendMode = .screen
    var opacity: Double    = 1.0
    // Current tool color (used for new strokes, not rendered directly)
    var currentHue: Double        = 0.6
    var currentSaturation: Double = 1.0
    var currentBrightness: Double = 0.9

    enum CodingKeys: String, CodingKey {
        case isEnabled, strokes, symmetry, glowIntensity
        case strokeWeight, blendMode, opacity
        case currentHue, currentSaturation, currentBrightness
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        isEnabled        = c.decodeSafe(Bool.self,           forKey: .isEnabled,        default: true)
        strokes          = c.decodeSafe([DrawStroke].self,   forKey: .strokes,          default: [])
        symmetry         = c.decodeSafe(Int.self,            forKey: .symmetry,         default: 6)
        glowIntensity    = c.decodeSafe(Double.self,         forKey: .glowIntensity,    default: 0.5)
        strokeWeight     = c.decodeSafe(Double.self,         forKey: .strokeWeight,     default: 0.4)
        blendMode        = c.decodeSafe(LayerBlendMode.self, forKey: .blendMode,        default: .screen)
        opacity          = c.decodeSafe(Double.self,         forKey: .opacity,          default: 1.0)
        currentHue        = c.decodeSafe(Double.self,        forKey: .currentHue,        default: 0.6)
        currentSaturation = c.decodeSafe(Double.self,        forKey: .currentSaturation, default: 1.0)
        currentBrightness = c.decodeSafe(Double.self,        forKey: .currentBrightness, default: 0.9)
    }

    // Tool-state fields (current*) do not affect rendering — exclude from equality.
    static func == (lhs: DrawingLayerSettings, rhs: DrawingLayerSettings) -> Bool {
        lhs.isEnabled     == rhs.isEnabled &&
        lhs.strokes       == rhs.strokes &&
        lhs.symmetry      == rhs.symmetry &&
        lhs.glowIntensity == rhs.glowIntensity &&
        lhs.strokeWeight  == rhs.strokeWeight &&
        lhs.blendMode     == rhs.blendMode &&
        lhs.opacity       == rhs.opacity
    }
}

// MARK: - Graffiti / Spray Layer

struct SprayStroke: Equatable, Codable {
    var xs: [Double]
    var ys: [Double]
    var brushSize: Double  = 0.05   // fraction of canvas width (0.01–0.20)
    var hue: Double        = 0.5
    var saturation: Double = 0.8
    var brightness: Double = 0.9
    var opacity: Double    = 0.6

    enum CodingKeys: String, CodingKey { case xs, ys, brushSize, hue, saturation, brightness, opacity }

    init(xs: [Double], ys: [Double], brushSize: Double = 0.05,
         hue: Double = 0.5, saturation: Double = 0.8, brightness: Double = 0.9, opacity: Double = 0.6) {
        self.xs = xs; self.ys = ys
        self.brushSize = brushSize
        self.hue = hue; self.saturation = saturation; self.brightness = brightness
        self.opacity = opacity
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        xs         = c.decodeSafe([Double].self, forKey: .xs,         default: [])
        ys         = c.decodeSafe([Double].self, forKey: .ys,         default: [])
        brushSize  = c.decodeSafe(Double.self,   forKey: .brushSize,  default: 0.05)
        hue        = c.decodeSafe(Double.self,   forKey: .hue,        default: 0.5)
        saturation = c.decodeSafe(Double.self,   forKey: .saturation, default: 0.8)
        brightness = c.decodeSafe(Double.self,   forKey: .brightness, default: 0.9)
        opacity    = c.decodeSafe(Double.self,   forKey: .opacity,    default: 0.6)
    }
}

struct GraffitiLayerSettings: Equatable, Codable {
    var isEnabled: Bool    = false
    var strokes: [SprayStroke] = []
    var symmetry: Int      = 1
    var blendMode: LayerBlendMode = .screen
    var opacity: Double    = 1.0
    var softness: Double   = 0.7    // 0=hard, 1=very soft edge
    // Current tool state
    var currentHue: Double        = 0.5
    var currentSaturation: Double = 0.8
    var currentBrightness: Double = 0.9
    var currentBrushSize: Double  = 0.05
    var currentOpacity: Double    = 0.6

    enum CodingKeys: String, CodingKey {
        case isEnabled, strokes, symmetry, blendMode, opacity, softness
        case currentHue, currentSaturation, currentBrightness, currentBrushSize, currentOpacity
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        isEnabled         = c.decodeSafe(Bool.self,             forKey: .isEnabled,         default: false)
        strokes           = c.decodeSafe([SprayStroke].self,    forKey: .strokes,           default: [])
        symmetry          = c.decodeSafe(Int.self,              forKey: .symmetry,          default: 1)
        blendMode         = c.decodeSafe(LayerBlendMode.self,   forKey: .blendMode,         default: .screen)
        opacity           = c.decodeSafe(Double.self,           forKey: .opacity,           default: 1.0)
        softness          = c.decodeSafe(Double.self,           forKey: .softness,          default: 0.7)
        currentHue        = c.decodeSafe(Double.self,           forKey: .currentHue,        default: 0.5)
        currentSaturation = c.decodeSafe(Double.self,           forKey: .currentSaturation, default: 0.8)
        currentBrightness = c.decodeSafe(Double.self,           forKey: .currentBrightness, default: 0.9)
        currentBrushSize  = c.decodeSafe(Double.self,           forKey: .currentBrushSize,  default: 0.05)
        currentOpacity    = c.decodeSafe(Double.self,           forKey: .currentOpacity,    default: 0.6)
    }

    // Tool-state fields (current*) do not affect rendering — exclude from equality.
    static func == (lhs: GraffitiLayerSettings, rhs: GraffitiLayerSettings) -> Bool {
        lhs.isEnabled == rhs.isEnabled &&
        lhs.strokes   == rhs.strokes &&
        lhs.symmetry  == rhs.symmetry &&
        lhs.blendMode == rhs.blendMode &&
        lhs.opacity   == rhs.opacity &&
        lhs.softness  == rhs.softness
    }
}

// MARK: - Text Layer

struct TextLayerSettings: Equatable, Codable {
    var isEnabled: Bool    = false
    var text: String       = ""
    var fontName: String   = "Georgia"
    var fontSize: Double   = 0.07   // fraction of canvas width (0.02–0.30)
    var glow: Double       = 0.0    // glow intensity around the text
    var shadowOpacity: Double = 0.0     // drop shadow darkness
    var shadowBlur: Double = 0.5        // shadow softness (0–1)
    var shadowOffsetX: Double = 0.3     // horizontal offset (-1 left … 1 right)
    var shadowOffsetY: Double = -0.3    // vertical offset (-1 up … 1 down)
    var shadowHue: Double = 0.0         // shadow color hue
    var shadowSaturation: Double = 0.0  // 0 = black/grey shadow
    var shadowBrightness: Double = 0.0  // 0 = dark shadow, 1 = white/bright shadow
    var cloudOpacity: Double = 0.0      // diffuse halo/cloud drawn behind text
    var cloudRadius: Double = 0.5       // cloud blur radius and padding (relative to font size)
    var cloudHue: Double = 0.0          // cloud color hue
    var cloudSaturation: Double = 0.0   // 0 = black/white cloud
    var cloudBrightness: Double = 0.0   // 0 = dark cloud, 1 = white/bright cloud
    var blur: Double       = 0.0        // Gaussian blur on the whole text layer
    var opacity: Double    = 1.0
    var hue: Double        = 0.0
    var saturation: Double = 0.0    // 0 = white/grey, 1 = saturated
    var brightness: Double = 1.0
    var tracking: Double   = 0.0    // letter spacing (−1 tight … 1 loose)
    var offsetX: Double    = 0.5    // 0–1, 0.5 = horizontal center
    var offsetY: Double    = 0.38   // 0–1, 0=bottom 1=top; slightly below center
    var blendMode: LayerBlendMode = .normal
    var showAuthor: Bool   = true   // append "— Author" line for quotes
    var customAuthor: String = ""  // if non-empty, overrides database lookup for author
    var authorScale: Double = 0.667 // author line font size relative to main font size
    var authorItalic: Bool = true   // render author line in italic

    enum CodingKeys: String, CodingKey {
        case isEnabled, text, fontName, fontSize, glow
        case shadowOpacity, shadowBlur, shadowOffsetX, shadowOffsetY
        case shadowHue, shadowSaturation, shadowBrightness
        case cloudOpacity, cloudRadius, cloudHue, cloudSaturation, cloudBrightness, blur, opacity
        case hue, saturation, brightness, tracking
        case offsetX, offsetY, blendMode, showAuthor, customAuthor, authorScale, authorItalic
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        isEnabled     = c.decodeSafe(Bool.self,           forKey: .isEnabled,     default: false)
        text          = c.decodeSafe(String.self,         forKey: .text,          default: "")
        fontName      = c.decodeSafe(String.self,         forKey: .fontName,      default: "Georgia")
        fontSize      = c.decodeSafe(Double.self,         forKey: .fontSize,      default: 0.07)
        glow          = c.decodeSafe(Double.self,         forKey: .glow,          default: 0.0)
        shadowOpacity    = c.decodeSafe(Double.self,      forKey: .shadowOpacity,    default: 0.0)
        shadowBlur       = c.decodeSafe(Double.self,      forKey: .shadowBlur,       default: 0.5)
        shadowOffsetX    = c.decodeSafe(Double.self,      forKey: .shadowOffsetX,    default: 0.3)
        shadowOffsetY    = c.decodeSafe(Double.self,      forKey: .shadowOffsetY,    default: -0.3)
        shadowHue        = c.decodeSafe(Double.self,      forKey: .shadowHue,        default: 0.0)
        shadowSaturation = c.decodeSafe(Double.self,      forKey: .shadowSaturation, default: 0.0)
        shadowBrightness = c.decodeSafe(Double.self,      forKey: .shadowBrightness, default: 0.0)
        cloudOpacity      = c.decodeSafe(Double.self, forKey: .cloudOpacity,      default: 0.0)
        cloudRadius       = c.decodeSafe(Double.self, forKey: .cloudRadius,       default: 0.5)
        cloudHue          = c.decodeSafe(Double.self, forKey: .cloudHue,          default: 0.0)
        cloudSaturation   = c.decodeSafe(Double.self, forKey: .cloudSaturation,   default: 0.0)
        cloudBrightness   = c.decodeSafe(Double.self, forKey: .cloudBrightness,   default: 0.0)
        blur          = c.decodeSafe(Double.self,         forKey: .blur,          default: 0.0)
        opacity       = c.decodeSafe(Double.self,         forKey: .opacity,       default: 1.0)
        hue           = c.decodeSafe(Double.self,         forKey: .hue,           default: 0.0)
        saturation    = c.decodeSafe(Double.self,         forKey: .saturation,    default: 0.0)
        brightness    = c.decodeSafe(Double.self,         forKey: .brightness,    default: 1.0)
        tracking      = c.decodeSafe(Double.self,         forKey: .tracking,      default: 0.0)
        offsetX       = c.decodeSafe(Double.self,         forKey: .offsetX,       default: 0.5)
        offsetY       = c.decodeSafe(Double.self,         forKey: .offsetY,       default: 0.38)
        blendMode     = c.decodeSafe(LayerBlendMode.self, forKey: .blendMode,     default: .normal)
        showAuthor    = c.decodeSafe(Bool.self,           forKey: .showAuthor,    default: true)
        customAuthor  = c.decodeSafe(String.self,         forKey: .customAuthor,  default: "")
        authorScale   = c.decodeSafe(Double.self,         forKey: .authorScale,   default: 0.667)
        authorItalic  = c.decodeSafe(Bool.self,           forKey: .authorItalic,  default: true)
    }
}

// MARK: - Layer Blend Mode

enum LayerBlendMode: String, CaseIterable, Identifiable, Codable {
    case screen, add, normal, multiply
    var id: String { rawValue }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = (try? container.decode(String.self)) ?? ""
        self = LayerBlendMode(rawValue: raw) ?? .screen
    }
    var displayName: String {
        switch self {
        case .screen:   return "Screen"
        case .add:      return "Add"
        case .normal:   return "Normal"
        case .multiply: return "Multiply"
        }
    }
}

// MARK: - Mandala Style

enum MandalaStyle: String, CaseIterable, Identifiable, Codable {
    // ── Parametric Curves ──────────────────────────────────────────
    case spirograph, roseCurves, floral, butterfly, epitrochoid
    case lissajous, hypocycloid, phyllotaxis, fractal, superformula, waveInterference
    // ── Geometric & Pattern ────────────────────────────────────────
    case geometric, sacredGeometry, sunburst, starBurst, stringArt
    case spiderWeb, weave, radialMesh, moire
    // ── Organic & Flow ─────────────────────────────────────────────
    case flowField, tendril, voronoi, strangeAttractor
    case constellationWeb, deepNebula
    // ── Optical & Interference ────────────────────────────────────
    case interferenceBloom
    // ── 3D ────────────────────────────────────────────────────────
    case hyperboloid, torus, nautilus, sphereGrid, torusKnot, tesseract
    // ── Special ───────────────────────────────────────────────────
    case universe, symbols, mixed

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = (try? container.decode(String.self)) ?? ""
        self = MandalaStyle(rawValue: raw) ?? .mixed
    }
    var id: String { rawValue }
    var displayName: String {
        switch self {
        // Parametric Curves
        case .spirograph:       return "Spirograph"
        case .roseCurves:       return "Rose Curves"
        case .floral:           return "Floral"
        case .butterfly:        return "Butterfly"
        case .epitrochoid:      return "Epitrochoid"
        case .lissajous:        return "Lissajous"
        case .hypocycloid:      return "Hypocycloid"
        case .phyllotaxis:      return "Phyllotaxis"
        case .fractal:          return "Fractal"
        case .superformula:     return "Superformula"
        case .waveInterference: return "Wave Interference"
        // Geometric & Pattern
        case .geometric:        return "Geometric"
        case .sacredGeometry:   return "Sacred Geometry"
        case .sunburst:         return "Sunburst"
        case .starBurst:        return "Star Burst"
        case .stringArt:        return "String Art"
        case .spiderWeb:        return "Spider Web"
        case .weave:            return "Weave"
        case .radialMesh:       return "Radial Mesh"
        case .moire:            return "Moiré"
        // Organic & Flow
        case .flowField:        return "Flow Field"
        case .tendril:          return "Tendril"
        case .voronoi:          return "Voronoi"
        case .strangeAttractor: return "Strange Attractor"
        case .constellationWeb: return "Constellation Web"
        case .deepNebula:       return "Deep Nebula"
        case .interferenceBloom:return "Interference Bloom"
        // 3D
        case .hyperboloid:      return "Hyperboloid"
        case .torus:            return "Torus"
        case .nautilus:         return "Nautilus"
        case .sphereGrid:       return "Sphere Grid"
        case .torusKnot:        return "Torus Knot"
        case .tesseract:        return "Tesseract"
        // Special
        case .universe:         return "Universe"
        case .symbols:          return "Symbols"
        case .mixed:            return "Mixed"
        }
    }
    var sfSymbol: String {
        switch self {
        // Parametric Curves
        case .spirograph:       return "circle.hexagongrid"
        case .roseCurves:       return "allergens"
        case .floral:           return "leaf"
        case .butterfly:        return "wind"
        case .epitrochoid:      return "atom"
        case .lissajous:        return "waveform.path"
        case .hypocycloid:      return "star.circle"
        case .phyllotaxis:      return "circle.grid.3x3"
        case .fractal:          return "snowflake"
        case .superformula:     return "aqi.medium"
        case .waveInterference: return "waveform.path.ecg"
        // Geometric & Pattern
        case .geometric:        return "seal"
        case .sacredGeometry:   return "hexagon"
        case .sunburst:         return "sun.max"
        case .starBurst:        return "rays"
        case .stringArt:        return "network"
        case .spiderWeb:        return "circle.grid.cross"
        case .weave:            return "square.grid.4x3.fill"
        case .radialMesh:       return "chart.pie"
        case .moire:            return "circle.grid.2x1"
        // Organic & Flow
        case .flowField:        return "tornado"
        case .tendril:          return "arrow.triangle.branch"
        case .voronoi:          return "rectangle.split.3x3"
        case .strangeAttractor: return "scribble"
        case .constellationWeb: return "point.3.connected.trianglepath.dotted"
        case .deepNebula:       return "sparkles.tv"
        case .interferenceBloom:return "circle.hexagongrid"
        // 3D
        case .hyperboloid:      return "cylinder.split.1x2"
        case .torus:            return "circle.dotted"
        case .nautilus:         return "arrow.clockwise.circle"
        case .sphereGrid:       return "globe"
        case .torusKnot:        return "hurricane"
        case .tesseract:        return "square.on.square"
        // Special
        case .universe:         return "sparkles"
        case .symbols:          return "heart.circle"
        case .mixed:            return "sparkles"
        }
    }
}

/// All settings for one rendered layer.
struct StyleLayer: Equatable, Codable {
    var isEnabled: Bool = true
    var style: MandalaStyle = .mixed
    var scale: Double = 1.0
    var paletteIndex: Int = 0
    var colorOffset: Double = 0.0
    var complexity: Double = 0.6
    var density: Double = 0.5
    var glowIntensity: Double = 0.6
    var colorDrift: Double = 0.4
    var ripple: Double = 0.0
    var wash: Double = 0.0
    var abstractLevel: Double = 0.3
    var saturation: Double = 0.7
    var brightness: Double = 0.5
    var rotation: Double = 0.0   // 0–1 → 0–360°
    var opacity: Double = 1.0    // 0–1 layer opacity
    var blendMode: LayerBlendMode = .screen
    var symmetry: Int = 6
    var seed: UInt64 = 42

    enum CodingKeys: String, CodingKey {
        case isEnabled, style, scale, paletteIndex, colorOffset, complexity, density
        case glowIntensity, colorDrift, ripple, wash, abstractLevel
        case saturation, brightness, rotation, opacity, blendMode, symmetry, seed
    }

    init(isEnabled: Bool = true, style: MandalaStyle = .mixed, scale: Double = 1.0,
         paletteIndex: Int = 0, colorOffset: Double = 0.0, complexity: Double = 0.6,
         density: Double = 0.5, glowIntensity: Double = 0.6, colorDrift: Double = 0.4,
         ripple: Double = 0.0, wash: Double = 0.0, abstractLevel: Double = 0.3,
         saturation: Double = 0.7, brightness: Double = 0.5, rotation: Double = 0.0,
         opacity: Double = 1.0, blendMode: LayerBlendMode = .screen,
         symmetry: Int = 6, seed: UInt64 = 42) {
        self.isEnabled = isEnabled
        self.style = style
        self.scale = scale
        self.paletteIndex = paletteIndex
        self.colorOffset = colorOffset
        self.complexity = complexity
        self.density = density
        self.glowIntensity = glowIntensity
        self.colorDrift = colorDrift
        self.ripple = ripple
        self.wash = wash
        self.abstractLevel = abstractLevel
        self.saturation = saturation
        self.brightness = brightness
        self.rotation = rotation
        self.opacity = opacity
        self.blendMode = blendMode
        self.symmetry = symmetry
        self.seed = seed
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        isEnabled     = c.decodeSafe(Bool.self,           forKey: .isEnabled,     default: true)
        style         = c.decodeSafe(MandalaStyle.self,   forKey: .style,         default: .mixed)
        scale         = c.decodeSafe(Double.self,         forKey: .scale,         default: 1.0)
        paletteIndex  = c.decodeSafe(Int.self,            forKey: .paletteIndex,  default: 0)
        colorOffset   = c.decodeSafe(Double.self,         forKey: .colorOffset,   default: 0.0)
        complexity    = c.decodeSafe(Double.self,         forKey: .complexity,    default: 0.6)
        density       = c.decodeSafe(Double.self,         forKey: .density,       default: 0.5)
        glowIntensity = c.decodeSafe(Double.self,         forKey: .glowIntensity, default: 0.6)
        colorDrift    = c.decodeSafe(Double.self,         forKey: .colorDrift,    default: 0.4)
        ripple        = c.decodeSafe(Double.self,         forKey: .ripple,        default: 0.0)
        wash          = c.decodeSafe(Double.self,         forKey: .wash,          default: 0.0)
        abstractLevel = c.decodeSafe(Double.self,         forKey: .abstractLevel, default: 0.3)
        saturation    = c.decodeSafe(Double.self,         forKey: .saturation,    default: 0.7)
        brightness    = c.decodeSafe(Double.self,         forKey: .brightness,    default: 0.5)
        rotation      = c.decodeSafe(Double.self,         forKey: .rotation,      default: 0.0)
        opacity       = c.decodeSafe(Double.self,         forKey: .opacity,       default: 1.0)
        blendMode     = c.decodeSafe(LayerBlendMode.self, forKey: .blendMode,     default: .screen)
        symmetry      = c.decodeSafe(Int.self,            forKey: .symmetry,      default: 6)
        seed          = c.decodeSafe(UInt64.self,         forKey: .seed,          default: 42)
    }
}

struct MandalaParameters: Equatable, Codable {
    var layers: [StyleLayer] = [StyleLayer()]
    var baseLayer: BaseLayerSettings = BaseLayerSettings()
    var effectsLayer: EffectsLayerSettings = EffectsLayerSettings()
    var graffitiLayer: GraffitiLayerSettings = GraffitiLayerSettings()
    var drawingLayer: DrawingLayerSettings = DrawingLayerSettings()
    var textLayer: TextLayerSettings = TextLayerSettings()

    // Truly global — render setup
    var seed: UInt64 = 42   // used for background/grass; each layer also has its own seed
    var previewSize: Int = 800          // resolution used for the canvas preview
    var outputSize: Int = 1024          // export resolution preset (0 = custom)
    var outputSizeCustom: Int = 2048   // used when outputSize == 0
    var outputFormat: String = "png"
    var outputShape: String = "square"  // "square", "circle", "squircle"

    // Transient: populated by AppState before rendering; not persisted.
    var resolvedPalettes: [ColorPalette] = []

    // Internal working fields used by renderer function signatures — overwritten per layer in render loop. Do NOT expose in UI.
    var symmetry: Int = 6
    var complexity: Double = 0.6
    var density: Double = 0.5
    var glowIntensity: Double = 0.6
    var colorDrift: Double = 0.4
    var ripple: Double = 0.0
    var wash: Double = 0.0
    var abstractLevel: Double = 0.3
    var paletteIndex: Int = 0

    var style: MandalaStyle {
        get { layers.first?.style ?? .mixed }
        set {
            if layers.isEmpty { layers = [StyleLayer(style: newValue)] }
            else { layers[0].style = newValue }
        }
    }

    enum CodingKeys: String, CodingKey {
        case layers, baseLayer, effectsLayer, graffitiLayer, drawingLayer, textLayer
        case seed, previewSize, outputSize, outputSizeCustom, outputFormat, outputShape
        case symmetry, complexity, density, glowIntensity, colorDrift, ripple, wash, abstractLevel, paletteIndex
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        layers          = c.decodeSafe([StyleLayer].self,              forKey: .layers,          default: [StyleLayer()])
        baseLayer       = c.decodeSafe(BaseLayerSettings.self,         forKey: .baseLayer,       default: BaseLayerSettings())
        effectsLayer    = c.decodeSafe(EffectsLayerSettings.self,      forKey: .effectsLayer,    default: EffectsLayerSettings())
        graffitiLayer   = c.decodeSafe(GraffitiLayerSettings.self,     forKey: .graffitiLayer,   default: GraffitiLayerSettings())
        drawingLayer    = c.decodeSafe(DrawingLayerSettings.self,       forKey: .drawingLayer,    default: DrawingLayerSettings())
        textLayer       = c.decodeSafe(TextLayerSettings.self,         forKey: .textLayer,       default: TextLayerSettings())
        seed            = c.decodeSafe(UInt64.self,                forKey: .seed,            default: 42)
        previewSize     = c.decodeSafe(Int.self,                   forKey: .previewSize,     default: 800)
        outputSize      = c.decodeSafe(Int.self,                   forKey: .outputSize,      default: 1024)
        outputSizeCustom = c.decodeSafe(Int.self,                  forKey: .outputSizeCustom,default: 2048)
        outputFormat    = c.decodeSafe(String.self,                forKey: .outputFormat,    default: "png")
        outputShape     = c.decodeSafe(String.self,                forKey: .outputShape,     default: "square")
        symmetry        = c.decodeSafe(Int.self,                   forKey: .symmetry,        default: 6)
        complexity      = c.decodeSafe(Double.self,                forKey: .complexity,      default: 0.6)
        density         = c.decodeSafe(Double.self,                forKey: .density,         default: 0.5)
        glowIntensity   = c.decodeSafe(Double.self,                forKey: .glowIntensity,   default: 0.6)
        colorDrift      = c.decodeSafe(Double.self,                forKey: .colorDrift,      default: 0.4)
        ripple          = c.decodeSafe(Double.self,                forKey: .ripple,          default: 0.0)
        wash            = c.decodeSafe(Double.self,                forKey: .wash,            default: 0.0)
        abstractLevel   = c.decodeSafe(Double.self,                forKey: .abstractLevel,   default: 0.3)
        paletteIndex    = c.decodeSafe(Int.self,                   forKey: .paletteIndex,    default: 0)
    }
}

extension MandalaParameters {
    static func == (lhs: MandalaParameters, rhs: MandalaParameters) -> Bool {
        lhs.layers == rhs.layers &&
        lhs.baseLayer == rhs.baseLayer &&
        lhs.effectsLayer == rhs.effectsLayer &&
        lhs.graffitiLayer == rhs.graffitiLayer &&
        lhs.drawingLayer == rhs.drawingLayer &&
        lhs.textLayer == rhs.textLayer &&
        lhs.seed == rhs.seed &&
        lhs.previewSize == rhs.previewSize &&
        lhs.outputSize == rhs.outputSize &&
        lhs.outputSizeCustom == rhs.outputSizeCustom &&
        lhs.outputFormat == rhs.outputFormat &&
        lhs.outputShape == rhs.outputShape &&
        lhs.symmetry == rhs.symmetry &&
        lhs.complexity == rhs.complexity &&
        lhs.density == rhs.density &&
        lhs.glowIntensity == rhs.glowIntensity &&
        lhs.colorDrift == rhs.colorDrift &&
        lhs.ripple == rhs.ripple &&
        lhs.wash == rhs.wash &&
        lhs.abstractLevel == rhs.abstractLevel &&
        lhs.paletteIndex == rhs.paletteIndex
        // resolvedPalettes is transient, excluded from equality
    }
}

// MARK: - Safe decoding helper

extension KeyedDecodingContainer {
    func decodeSafe<T: Decodable>(_ type: T.Type, forKey key: Key, default def: T) -> T {
        (try? decodeIfPresent(type, forKey: key)) ?? def
    }
}
