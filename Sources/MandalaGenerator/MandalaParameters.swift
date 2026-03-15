import Foundation

enum MandalaStyle: String, CaseIterable, Identifiable {
    case spirograph, roseCurves, stringArt, sunburst, epitrochoid, floral, lissajous, butterfly, geometric, mixed
    var id: String { rawValue }
    var displayName: String {
        switch self {
        case .spirograph:  return "Spirograph"
        case .roseCurves:  return "Rose Curves"
        case .stringArt:   return "String Art"
        case .sunburst:    return "Sunburst"
        case .epitrochoid: return "Epitrochoid"
        case .floral:      return "Floral"
        case .lissajous:   return "Lissajous"
        case .butterfly:   return "Butterfly"
        case .geometric:   return "Geometric"
        case .mixed:       return "Mixed"
        }
    }
    var sfSymbol: String {
        switch self {
        case .spirograph:  return "circle.hexagongrid"
        case .roseCurves:  return "allergens"
        case .stringArt:   return "network"
        case .sunburst:    return "sun.max"
        case .epitrochoid: return "atom"
        case .floral:      return "leaf"
        case .lissajous:   return "waveform.path"
        case .butterfly:   return "wind"
        case .geometric:   return "seal"
        case .mixed:       return "sparkles"
        }
    }
}

/// All settings for one rendered layer.
struct StyleLayer: Equatable {
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
    var symmetry: Int = 6
    var seed: UInt64 = 42
}

struct MandalaParameters: Equatable {
    var layers: [StyleLayer] = [StyleLayer()]

    // Truly global — render setup
    var seed: UInt64 = 42   // used for background/grass; each layer also has its own seed
    var outputSize: Int = 800
    var outputFormat: String = "png"

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
