import Foundation

// MARK: - Base Layer

enum BaseLayerType: String, CaseIterable, Identifiable, Codable {
    case color, gradient, pattern, grain, image
    var id: String { rawValue }
    var displayName: String {
        switch self {
        case .color:    return "Color"
        case .gradient: return "Gradient"
        case .pattern:  return "Pattern"
        case .grain:    return "Grain"
        case .image:    return "Image"
        }
    }
    var sfSymbol: String {
        switch self {
        case .color:    return "square.fill"
        case .gradient: return "circle.lefthalf.filled"
        case .pattern:  return "squareshape.split.2x2"
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
    // Per-effect seeds — each can be randomized independently
    var dimmingSeed: UInt64    = 11
    var erasureSeed: UInt64    = 22
    var highlightsSeed: UInt64 = 33
    var starsSeed: UInt64      = 44
}

// MARK: - Layer Blend Mode

enum LayerBlendMode: String, CaseIterable, Identifiable, Codable {
    case screen, add, normal, multiply
    var id: String { rawValue }
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
    case spirograph, roseCurves, stringArt, sunburst, epitrochoid, floral, lissajous, butterfly, geometric, fractal, mixed
    case phyllotaxis, hypocycloid, waveInterference, spiderWeb, weave, sacredGeometry, radialMesh, flowField, tendril, moire, voronoi
    var id: String { rawValue }
    var displayName: String {
        switch self {
        case .spirograph:      return "Spirograph"
        case .roseCurves:      return "Rose Curves"
        case .stringArt:       return "String Art"
        case .sunburst:        return "Sunburst"
        case .epitrochoid:     return "Epitrochoid"
        case .floral:          return "Floral"
        case .lissajous:       return "Lissajous"
        case .butterfly:       return "Butterfly"
        case .geometric:       return "Geometric"
        case .fractal:         return "Fractal"
        case .mixed:           return "Mixed"
        case .phyllotaxis:     return "Phyllotaxis"
        case .hypocycloid:     return "Hypocycloid"
        case .waveInterference: return "Wave Interference"
        case .spiderWeb:       return "Spider Web"
        case .weave:           return "Weave"
        case .sacredGeometry:  return "Sacred Geometry"
        case .radialMesh:      return "Radial Mesh"
        case .flowField:       return "Flow Field"
        case .tendril:         return "Tendril"
        case .moire:           return "Moiré"
        case .voronoi:         return "Voronoi"
        }
    }
    var sfSymbol: String {
        switch self {
        case .spirograph:      return "circle.hexagongrid"
        case .roseCurves:      return "allergens"
        case .stringArt:       return "network"
        case .sunburst:        return "sun.max"
        case .epitrochoid:     return "atom"
        case .floral:          return "leaf"
        case .lissajous:       return "waveform.path"
        case .butterfly:       return "wind"
        case .geometric:       return "seal"
        case .fractal:         return "snowflake"
        case .mixed:           return "sparkles"
        case .phyllotaxis:     return "circle.grid.3x3"
        case .hypocycloid:     return "star.circle"
        case .waveInterference: return "waveform.path.ecg"
        case .spiderWeb:       return "circle.grid.cross"
        case .weave:           return "square.grid.4x3.fill"
        case .sacredGeometry:  return "hexagon"
        case .radialMesh:      return "chart.pie"
        case .flowField:       return "tornado"
        case .tendril:         return "arrow.triangle.branch"
        case .moire:           return "circle.grid.2x1"
        case .voronoi:         return "rectangle.split.3x3"
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
    var blendMode: LayerBlendMode = .screen
    var symmetry: Int = 6
    var seed: UInt64 = 42
}

struct MandalaParameters: Equatable, Codable {
    var layers: [StyleLayer] = [StyleLayer()]
    var baseLayer: BaseLayerSettings = BaseLayerSettings()
    var effectsLayer: EffectsLayerSettings = EffectsLayerSettings()

    // Truly global — render setup
    var seed: UInt64 = 42   // used for background/grass; each layer also has its own seed
    var outputSize: Int = 800
    var outputFormat: String = "png"
    var outputShape: String = "square"  // "square", "circle", "squircle"

    // Internal working fields used by renderer function signatures — overwritten per layer in render loop. Do NOT expose in UI.
    var symmetry: Int = 6
    var complexity: Double = 0.6
    var density: Double = 0.5
    var glowIntensity: Double = 0.6
    var colorDrift: Double = 0.4
    var ripple: Double = 0.0
    var wash: Double = 0.0
    var paletteIndex: Int = 0

    var style: MandalaStyle {
        get { layers.first?.style ?? .mixed }
        set {
            if layers.isEmpty { layers = [StyleLayer(style: newValue)] }
            else { layers[0].style = newValue }
        }
    }
}
