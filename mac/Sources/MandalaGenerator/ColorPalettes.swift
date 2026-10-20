import AppKit
import Foundation

struct ColorPalette: Identifiable {
    let id: String
    let name: String
    let stops: [(Double, NSColor)]

    func color(at t: Double) -> NSColor {
        let clamped = max(0, min(1, t))
        guard stops.count >= 2 else {
            return stops.first?.1 ?? .white
        }
        var lower = stops[0]
        var upper = stops[stops.count - 1]
        for i in 0..<(stops.count - 1) {
            if clamped >= stops[i].0 && clamped <= stops[i + 1].0 {
                lower = stops[i]
                upper = stops[i + 1]
                break
            }
        }
        let range = upper.0 - lower.0
        let localT = range > 0 ? (clamped - lower.0) / range : 0
        return interpolate(lower.1, upper.1, t: localT)
    }

    private func interpolate(_ a: NSColor, _ b: NSColor, t: Double) -> NSColor {
        guard let ac = a.usingColorSpace(.displayP3),
              let bc = b.usingColorSpace(.displayP3) else { return a }
        let r = ac.redComponent   + (bc.redComponent   - ac.redComponent)   * CGFloat(t)
        let g = ac.greenComponent + (bc.greenComponent - ac.greenComponent) * CGFloat(t)
        let bv = ac.blueComponent + (bc.blueComponent  - ac.blueComponent)  * CGFloat(t)
        return NSColor(displayP3Red: r, green: g, blue: bv, alpha: 1)
    }

    /// Blend two palettes together. `mix` = 0 → self, `mix` = 1 → other.
    func blended(with other: ColorPalette, mix: Double) -> ColorPalette {
        let allPositions = Set(stops.map(\.0) + other.stops.map(\.0)).sorted()
        let blendedStops: [(Double, NSColor)] = allPositions.map { pos in
            let c1 = self.color(at: pos)
            let c2 = other.color(at: pos)
            guard let a = c1.usingColorSpace(.displayP3),
                  let b = c2.usingColorSpace(.displayP3) else { return (pos, c1) }
            let t = CGFloat(mix)
            let r  = a.redComponent   + (b.redComponent   - a.redComponent)   * t
            let g  = a.greenComponent + (b.greenComponent - a.greenComponent) * t
            let bv = a.blueComponent  + (b.blueComponent  - a.blueComponent)  * t
            return (pos, NSColor(displayP3Red: r, green: g, blue: bv, alpha: 1))
        }
        return ColorPalette(id: "\(id)+\(other.id)", name: "\(name)/\(other.name)",
                            stops: blendedStops)
    }
}

struct ColorPalettes {
    static let all: [ColorPalette] = [
        psychedelic,
        aurora,
        nebula,
        neonCity,
        sunset,
        prism,
        cosmic,
        fire,
        ocean,
        bioluminescence,
        dragon,
        violet,
        candy,
        tropical,
        ice,
        synthwave,
        lava,
        gold,
    ]

    // MARK: - Rich multi-colour palettes

    /// Full-spectrum rainbow cycling — maximum colour diversity
    static let psychedelic = ColorPalette(
        id: "psychedelic", name: "Psychedelic",
        stops: [
            (0.0,  NSColor(red: 1.0,  green: 0.0,  blue: 0.5,  alpha: 1)),
            (0.17, NSColor(red: 1.0,  green: 0.45, blue: 0.0,  alpha: 1)),
            (0.33, NSColor(red: 0.85, green: 1.0,  blue: 0.0,  alpha: 1)),
            (0.5,  NSColor(red: 0.0,  green: 1.0,  blue: 0.6,  alpha: 1)),
            (0.67, NSColor(red: 0.0,  green: 0.7,  blue: 1.0,  alpha: 1)),
            (0.83, NSColor(red: 0.6,  green: 0.0,  blue: 1.0,  alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 0.0,  blue: 0.5,  alpha: 1)),
        ]
    )

    /// Northern lights: electric green → cyan → indigo → pink
    static let aurora = ColorPalette(
        id: "aurora", name: "Aurora",
        stops: [
            (0.0,  NSColor(red: 0.0,  green: 0.05, blue: 0.2,  alpha: 1)),
            (0.2,  NSColor(red: 0.0,  green: 0.85, blue: 0.45, alpha: 1)),
            (0.4,  NSColor(red: 0.0,  green: 0.95, blue: 0.9,  alpha: 1)),
            (0.6,  NSColor(red: 0.35, green: 0.4,  blue: 1.0,  alpha: 1)),
            (0.8,  NSColor(red: 0.85, green: 0.0,  blue: 0.9,  alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 0.4,  blue: 0.7,  alpha: 1)),
        ]
    )

    /// Deep space: dark indigo → vivid purple → hot pink → orange → gold
    static let nebula = ColorPalette(
        id: "nebula", name: "Nebula",
        stops: [
            (0.0,  NSColor(red: 0.05, green: 0.0,  blue: 0.25, alpha: 1)),
            (0.2,  NSColor(red: 0.35, green: 0.0,  blue: 0.75, alpha: 1)),
            (0.45, NSColor(red: 0.9,  green: 0.0,  blue: 0.6,  alpha: 1)),
            (0.65, NSColor(red: 1.0,  green: 0.3,  blue: 0.0,  alpha: 1)),
            (0.85, NSColor(red: 1.0,  green: 0.75, blue: 0.0,  alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 0.98, blue: 0.6,  alpha: 1)),
        ]
    )

    /// Cyberpunk: hot pink → orange → electric cyan → neon violet
    static let neonCity = ColorPalette(
        id: "neon-city", name: "Neon City",
        stops: [
            (0.0,  NSColor(red: 1.0,  green: 0.05, blue: 0.55, alpha: 1)),
            (0.25, NSColor(red: 1.0,  green: 0.45, blue: 0.0,  alpha: 1)),
            (0.5,  NSColor(red: 0.0,  green: 0.95, blue: 0.95, alpha: 1)),
            (0.75, NSColor(red: 0.45, green: 0.0,  blue: 1.0,  alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 0.05, blue: 0.55, alpha: 1)),
        ]
    )

    /// Dusk sky: deep violet → fuchsia → burnt orange → pale gold
    static let sunset = ColorPalette(
        id: "sunset", name: "Sunset",
        stops: [
            (0.0,  NSColor(red: 0.12, green: 0.0,  blue: 0.35, alpha: 1)),
            (0.22, NSColor(red: 0.6,  green: 0.0,  blue: 0.7,  alpha: 1)),
            (0.45, NSColor(red: 1.0,  green: 0.05, blue: 0.45, alpha: 1)),
            (0.65, NSColor(red: 1.0,  green: 0.4,  blue: 0.0,  alpha: 1)),
            (0.85, NSColor(red: 1.0,  green: 0.75, blue: 0.1,  alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 0.97, blue: 0.75, alpha: 1)),
        ]
    )

    /// Clean spectrum prism — saturated, evenly spaced hues
    static let prism = ColorPalette(
        id: "prism", name: "Prism",
        stops: [
            (0.0,  NSColor(red: 1.0,  green: 0.0,  blue: 0.15, alpha: 1)),
            (0.2,  NSColor(red: 1.0,  green: 0.55, blue: 0.0,  alpha: 1)),
            (0.38, NSColor(red: 0.8,  green: 1.0,  blue: 0.0,  alpha: 1)),
            (0.55, NSColor(red: 0.0,  green: 1.0,  blue: 0.35, alpha: 1)),
            (0.72, NSColor(red: 0.0,  green: 0.6,  blue: 1.0,  alpha: 1)),
            (0.88, NSColor(red: 0.65, green: 0.0,  blue: 1.0,  alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 0.0,  blue: 0.15, alpha: 1)),
        ]
    )

    /// Purple haze → electric blue → gold — cinematic sci-fi
    static let cosmic = ColorPalette(
        id: "cosmic", name: "Cosmic",
        stops: [
            (0.0,  NSColor(red: 0.15, green: 0.0,  blue: 0.35, alpha: 1)),
            (0.25, NSColor(red: 0.4,  green: 0.0,  blue: 0.7,  alpha: 1)),
            (0.5,  NSColor(red: 0.05, green: 0.55, blue: 0.95, alpha: 1)),
            (0.75, NSColor(red: 0.95, green: 0.7,  blue: 0.05, alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 0.97, blue: 0.6,  alpha: 1)),
        ]
    )

    // MARK: - Focused-colour palettes

    /// Neon acid: near-black → toxic green → acid lime → pale yellow-white
    static let fire = ColorPalette(
        id: "fire", name: "Toxic",
        stops: [
            (0.0,  NSColor(red: 0.0,  green: 0.04, blue: 0.0,  alpha: 1)),
            (0.2,  NSColor(red: 0.0,  green: 0.3,  blue: 0.02, alpha: 1)),
            (0.5,  NSColor(red: 0.05, green: 0.95, blue: 0.0,  alpha: 1)),
            (0.75, NSColor(red: 0.65, green: 1.0,  blue: 0.0,  alpha: 1)),
            (0.9,  NSColor(red: 0.92, green: 1.0,  blue: 0.5,  alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 1.0,  blue: 0.85, alpha: 1)),
        ]
    )

    static let ocean = ColorPalette(
        id: "ocean", name: "Ocean",
        stops: [
            (0.0,  NSColor(red: 0.0,  green: 0.03, blue: 0.25, alpha: 1)),
            (0.3,  NSColor(red: 0.0,  green: 0.35, blue: 0.75, alpha: 1)),
            (0.6,  NSColor(red: 0.0,  green: 0.75, blue: 0.95, alpha: 1)),
            (0.85, NSColor(red: 0.2,  green: 0.95, blue: 0.85, alpha: 1)),
            (1.0,  NSColor(red: 0.7,  green: 1.0,  blue: 0.95, alpha: 1)),
        ]
    )

    /// Deep navy → electric cyan → neon green → white
    static let bioluminescence = ColorPalette(
        id: "bioluminescence", name: "Bio Glow",
        stops: [
            (0.0,  NSColor(red: 0.0,  green: 0.02, blue: 0.2,  alpha: 1)),
            (0.25, NSColor(red: 0.0,  green: 0.4,  blue: 0.9,  alpha: 1)),
            (0.5,  NSColor(red: 0.0,  green: 0.95, blue: 0.85, alpha: 1)),
            (0.75, NSColor(red: 0.3,  green: 1.0,  blue: 0.3,  alpha: 1)),
            (1.0,  NSColor(red: 0.85, green: 1.0,  blue: 1.0,  alpha: 1)),
        ]
    )

    /// Dark purple → scarlet → orange → bright gold — dramatic & warm
    static let dragon = ColorPalette(
        id: "dragon", name: "Dragon",
        stops: [
            (0.0,  NSColor(red: 0.0,  green: 0.0,  blue: 0.25, alpha: 1)),
            (0.2,  NSColor(red: 0.0,  green: 0.05, blue: 0.8,  alpha: 1)),
            (0.45, NSColor(red: 0.4,  green: 0.0,  blue: 0.9,  alpha: 1)),
            (0.65, NSColor(red: 0.85, green: 0.0,  blue: 0.2,  alpha: 1)),
            (0.82, NSColor(red: 1.0,  green: 0.15, blue: 0.15, alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 0.7,  blue: 0.75, alpha: 1)),
        ]
    )

    static let violet = ColorPalette(
        id: "violet", name: "Violet",
        stops: [
            (0.0,  NSColor(red: 0.2,  green: 0.0,  blue: 0.4,  alpha: 1)),
            (0.35, NSColor(red: 0.7,  green: 0.0,  blue: 0.85, alpha: 1)),
            (0.65, NSColor(red: 1.0,  green: 0.15, blue: 0.65, alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 0.65, blue: 0.9,  alpha: 1)),
        ]
    )

    static let candy = ColorPalette(
        id: "candy", name: "Candy",
        stops: [
            (0.0,  NSColor(red: 1.0,  green: 0.05, blue: 0.5,  alpha: 1)),
            (0.3,  NSColor(red: 1.0,  green: 0.5,  blue: 0.05, alpha: 1)),
            (0.6,  NSColor(red: 0.05, green: 0.9,  blue: 0.9,  alpha: 1)),
            (1.0,  NSColor(red: 0.55, green: 0.05, blue: 1.0,  alpha: 1)),
        ]
    )

    static let tropical = ColorPalette(
        id: "tropical", name: "Tropical",
        stops: [
            (0.0,  NSColor(red: 0.0,  green: 0.5,  blue: 0.3,  alpha: 1)),
            (0.3,  NSColor(red: 0.0,  green: 0.85, blue: 0.6,  alpha: 1)),
            (0.55, NSColor(red: 0.95, green: 0.88, blue: 0.05, alpha: 1)),
            (0.8,  NSColor(red: 1.0,  green: 0.4,  blue: 0.05, alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 0.1,  blue: 0.45, alpha: 1)),
        ]
    )

    static let ice = ColorPalette(
        id: "ice", name: "Ice",
        stops: [
            (0.0,  NSColor(red: 0.05, green: 0.0,  blue: 0.35, alpha: 1)),
            (0.3,  NSColor(red: 0.05, green: 0.25, blue: 0.9,  alpha: 1)),
            (0.6,  NSColor(red: 0.3,  green: 0.65, blue: 1.0,  alpha: 1)),
            (0.85, NSColor(red: 0.75, green: 0.9,  blue: 1.0,  alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 1.0,  blue: 1.0,  alpha: 1)),
        ]
    )

    /// Retro synth: deep teal → magenta → neon violet → hot pink
    static let synthwave = ColorPalette(
        id: "synthwave", name: "Synthwave",
        stops: [
            (0.0,  NSColor(red: 0.0,  green: 0.15, blue: 0.35, alpha: 1)),
            (0.25, NSColor(red: 0.0,  green: 0.65, blue: 0.8,  alpha: 1)),
            (0.5,  NSColor(red: 0.75, green: 0.0,  blue: 0.95, alpha: 1)),
            (0.75, NSColor(red: 1.0,  green: 0.05, blue: 0.75, alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 0.5,  blue: 0.9,  alpha: 1)),
        ]
    )

    /// Volcanic: black → deep red → magma orange → blinding white-yellow
    static let lava = ColorPalette(
        id: "lava", name: "Lava",
        stops: [
            (0.0,  NSColor(red: 0.2,  green: 0.0,  blue: 0.35, alpha: 1)),
            (0.2,  NSColor(red: 0.7,  green: 0.0,  blue: 0.05, alpha: 1)),
            (0.45, NSColor(red: 1.0,  green: 0.15, blue: 0.0,  alpha: 1)),
            (0.7,  NSColor(red: 1.0,  green: 0.6,  blue: 0.0,  alpha: 1)),
            (0.9,  NSColor(red: 1.0,  green: 0.9,  blue: 0.2,  alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 1.0,  blue: 0.8,  alpha: 1)),
        ]
    )

    /// Mostly yellow: dark amber → deep gold → bright yellow → pale cream
    static let gold = ColorPalette(
        id: "gold", name: "Gold",
        stops: [
            (0.0,  NSColor(red: 0.18, green: 0.09, blue: 0.0,  alpha: 1)),
            (0.2,  NSColor(red: 0.55, green: 0.28, blue: 0.0,  alpha: 1)),
            (0.45, NSColor(red: 0.95, green: 0.65, blue: 0.0,  alpha: 1)),
            (0.65, NSColor(red: 1.0,  green: 0.88, blue: 0.05, alpha: 1)),
            (0.82, NSColor(red: 1.0,  green: 0.97, blue: 0.45, alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 1.0,  blue: 0.88, alpha: 1)),
        ]
    )
}
