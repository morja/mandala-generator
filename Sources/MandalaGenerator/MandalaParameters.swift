import Foundation

enum MandalaStyle: String, CaseIterable, Identifiable {
    case spirograph
    case roseCurves
    case stringArt
    case sunburst
    case epitrochoid
    case floral
    case lissajous
    case butterfly
    case geometric
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
        case .lissajous:    return "Lissajous"
        case .butterfly:    return "Butterfly"
        case .geometric:    return "Geometric"
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
        case .lissajous:    return "waveform.path"
        case .butterfly:    return "wind"
        case .geometric:    return "seal"
        case .mixed:        return "sparkles"
        }
    }
}

/// A single style layer with its own style, scale, and color offset.
struct StyleLayer: Equatable {
    var style: MandalaStyle
    var scale: Double   // 0.1 … 1.0  — relative radius
    var colorOffset: Double  // 0 … 1 — palette shift for this layer
}

struct MandalaParameters: Equatable {
    // Multi-layer style composition
    var layers: [StyleLayer] = [StyleLayer(style: .mixed, scale: 1.0, colorOffset: 0.0)]

    var paletteIndex: Int = 0
    var paletteBlend: Double = 0.0      // 0 = pure palette, 1 = pure blendPalette
    var blendPaletteIndex: Int = 1      // second palette for blending
    var abstractLevel: Double = 0.3
    var complexity: Double = 0.6
    var density: Double = 0.5
    var glowIntensity: Double = 0.6
    var colorDrift: Double = 0.4
    var symmetry: Int = 1
    var seed: UInt64 = 42
    var outputSize: Int = 800
    // Distortion
    var ripple: Double = 0.0
    var wash: Double = 0.0
    // Colour grading
    var saturation: Double = 0.5
    var brightness: Double = 0.5

    // Convenience: primary style (first layer)
    var style: MandalaStyle {
        get { layers.first?.style ?? .mixed }
        set {
            if layers.isEmpty { layers = [StyleLayer(style: newValue, scale: 1.0, colorOffset: 0.0)] }
            else { layers[0].style = newValue }
        }
    }
}
