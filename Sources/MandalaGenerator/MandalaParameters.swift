import Foundation

enum MandalaStyle: String, CaseIterable, Identifiable {
    case spirograph
    case roseCurves
    case stringArt
    case sunburst
    case epitrochoid
    case floral
    case mixed

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .spirograph:   return "Spirograph"
        case .roseCurves:   return "Rose Curves"
        case .stringArt:    return "String Art"
        case .sunburst:     return "Sunburst"
        case .epitrochoid:  return "Epitrochoid"
        case .floral:       return "Floral"
        case .mixed:        return "Mixed"
        }
    }

    var sfSymbol: String {
        switch self {
        case .spirograph:   return "circle.hexagongrid"
        case .roseCurves:   return "allergens"
        case .stringArt:    return "network"
        case .sunburst:     return "sun.max"
        case .epitrochoid:  return "atom"
        case .floral:       return "leaf"
        case .mixed:        return "sparkles"
        }
    }
}

struct MandalaParameters: Equatable {
    var style: MandalaStyle = .mixed
    var paletteIndex: Int = 0
    var abstractLevel: Double = 0.3
    var complexity: Double = 0.6
    var density: Double = 0.5
    var glowIntensity: Double = 0.6
    var colorDrift: Double = 0.4
    var symmetry: Int = 1
    var seed: UInt64 = 42
    var outputSize: Int = 800
    // Distortion
    var ripple: Double = 0.0   // sine-wave ripple applied to curve points (0 = none)
    var wash: Double = 0.0     // watercolour colour-bleed blurred onto base (0 = none)
    // Colour grading
    var saturation: Double = 0.5   // colour intensity (0 = greyscale, 1 = vivid)
    var brightness: Double = 0.5   // overall luminance / exposure
}
