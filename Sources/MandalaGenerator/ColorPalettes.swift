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
        // Find surrounding stops
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
              let bc = b.usingColorSpace(.displayP3) else {
            return a
        }
        let r = ac.redComponent + (bc.redComponent - ac.redComponent) * CGFloat(t)
        let g = ac.greenComponent + (bc.greenComponent - ac.greenComponent) * CGFloat(t)
        let bv = ac.blueComponent + (bc.blueComponent - ac.blueComponent) * CGFloat(t)
        let alpha = ac.alphaComponent + (bc.alphaComponent - ac.alphaComponent) * CGFloat(t)
        return NSColor(displayP3Red: r, green: g, blue: bv, alpha: alpha)
    }
}

struct ColorPalettes {
    static let all: [ColorPalette] = [
        fire,
        violet,
        ocean,
        emerald,
        rose,
        gold,
        ice,
        candy,
        neonGreen,
        ruby,
        cosmic,
        tropical,
        psychedelic,
        tealLime
    ]

    static let fire = ColorPalette(
        id: "fire",
        name: "Fire",
        stops: [
            (0.0, NSColor(red: 0.6,  green: 0.0,  blue: 0.0,  alpha: 1)),
            (0.3, NSColor(red: 1.0,  green: 0.2,  blue: 0.0,  alpha: 1)),
            (0.6, NSColor(red: 1.0,  green: 0.55, blue: 0.0,  alpha: 1)),
            (1.0, NSColor(red: 1.0,  green: 0.95, blue: 0.4,  alpha: 1))
        ]
    )

    static let violet = ColorPalette(
        id: "violet",
        name: "Violet",
        stops: [
            (0.0, NSColor(red: 0.3,  green: 0.0,  blue: 0.5,  alpha: 1)),
            (0.4, NSColor(red: 0.8,  green: 0.0,  blue: 0.8,  alpha: 1)),
            (0.7, NSColor(red: 1.0,  green: 0.2,  blue: 0.6,  alpha: 1)),
            (1.0, NSColor(red: 1.0,  green: 0.6,  blue: 0.8,  alpha: 1))
        ]
    )

    static let ocean = ColorPalette(
        id: "ocean",
        name: "Ocean",
        stops: [
            (0.0, NSColor(red: 0.0,  green: 0.05, blue: 0.3,  alpha: 1)),
            (0.35, NSColor(red: 0.0,  green: 0.4,  blue: 0.7,  alpha: 1)),
            (0.7, NSColor(red: 0.0,  green: 0.8,  blue: 0.9,  alpha: 1)),
            (1.0, NSColor(red: 0.5,  green: 1.0,  blue: 0.95, alpha: 1))
        ]
    )

    static let emerald = ColorPalette(
        id: "emerald",
        name: "Emerald",
        stops: [
            (0.0, NSColor(red: 0.0,  green: 0.2,  blue: 0.05, alpha: 1)),
            (0.4, NSColor(red: 0.0,  green: 0.6,  blue: 0.2,  alpha: 1)),
            (0.75, NSColor(red: 0.1,  green: 0.9,  blue: 0.3,  alpha: 1)),
            (1.0, NSColor(red: 0.6,  green: 1.0,  blue: 0.3,  alpha: 1))
        ]
    )

    static let rose = ColorPalette(
        id: "rose",
        name: "Rose",
        stops: [
            (0.0, NSColor(red: 0.55, green: 0.0,  blue: 0.1,  alpha: 1)),
            (0.35, NSColor(red: 0.9,  green: 0.1,  blue: 0.35, alpha: 1)),
            (0.65, NSColor(red: 1.0,  green: 0.45, blue: 0.6,  alpha: 1)),
            (1.0, NSColor(red: 1.0,  green: 0.8,  blue: 0.85, alpha: 1))
        ]
    )

    static let gold = ColorPalette(
        id: "gold",
        name: "Gold",
        stops: [
            (0.0, NSColor(red: 0.3,  green: 0.15, blue: 0.0,  alpha: 1)),
            (0.35, NSColor(red: 0.7,  green: 0.35, blue: 0.0,  alpha: 1)),
            (0.65, NSColor(red: 1.0,  green: 0.65, blue: 0.1,  alpha: 1)),
            (1.0, NSColor(red: 1.0,  green: 0.95, blue: 0.5,  alpha: 1))
        ]
    )

    static let ice = ColorPalette(
        id: "ice",
        name: "Ice",
        stops: [
            (0.0,  NSColor(red: 0.1,  green: 0.0,  blue: 0.4,  alpha: 1)),
            (0.3,  NSColor(red: 0.1,  green: 0.3,  blue: 0.9,  alpha: 1)),
            (0.6,  NSColor(red: 0.4,  green: 0.7,  blue: 1.0,  alpha: 1)),
            (0.85, NSColor(red: 0.8,  green: 0.92, blue: 1.0,  alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 1.0,  blue: 1.0,  alpha: 1))
        ]
    )

    static let candy = ColorPalette(
        id: "candy",
        name: "Candy",
        stops: [
            (0.0,  NSColor(red: 1.0,  green: 0.1,  blue: 0.5,  alpha: 1)),
            (0.3,  NSColor(red: 1.0,  green: 0.5,  blue: 0.1,  alpha: 1)),
            (0.6,  NSColor(red: 0.1,  green: 0.9,  blue: 0.9,  alpha: 1)),
            (1.0,  NSColor(red: 0.6,  green: 0.1,  blue: 1.0,  alpha: 1))
        ]
    )

    static let neonGreen = ColorPalette(
        id: "neon-green",
        name: "Neon Green",
        stops: [
            (0.0, NSColor(red: 0.0,  green: 0.3,  blue: 0.0,  alpha: 1)),
            (0.4, NSColor(red: 0.1,  green: 0.9,  blue: 0.1,  alpha: 1)),
            (0.7, NSColor(red: 0.6,  green: 1.0,  blue: 0.1,  alpha: 1)),
            (1.0, NSColor(red: 0.9,  green: 1.0,  blue: 0.6,  alpha: 1))
        ]
    )

    static let ruby = ColorPalette(
        id: "ruby",
        name: "Ruby",
        stops: [
            (0.0, NSColor(red: 0.4,  green: 0.0,  blue: 0.05, alpha: 1)),
            (0.4, NSColor(red: 0.85, green: 0.0,  blue: 0.15, alpha: 1)),
            (0.7, NSColor(red: 1.0,  green: 0.2,  blue: 0.2,  alpha: 1)),
            (1.0, NSColor(red: 1.0,  green: 0.7,  blue: 0.5,  alpha: 1))
        ]
    )

    static let cosmic = ColorPalette(
        id: "cosmic",
        name: "Cosmic",
        stops: [
            (0.0,  NSColor(red: 0.15, green: 0.0,  blue: 0.35, alpha: 1)),
            (0.25, NSColor(red: 0.4,  green: 0.0,  blue: 0.7,  alpha: 1)),
            (0.5,  NSColor(red: 0.1,  green: 0.6,  blue: 0.9,  alpha: 1)),
            (0.75, NSColor(red: 0.9,  green: 0.7,  blue: 0.1,  alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 0.95, blue: 0.6,  alpha: 1))
        ]
    )

    static let tropical = ColorPalette(
        id: "tropical",
        name: "Tropical",
        stops: [
            (0.0,  NSColor(red: 0.0,  green: 0.5,  blue: 0.3,  alpha: 1)),
            (0.3,  NSColor(red: 0.0,  green: 0.8,  blue: 0.6,  alpha: 1)),
            (0.55, NSColor(red: 0.9,  green: 0.85, blue: 0.1,  alpha: 1)),
            (0.8,  NSColor(red: 1.0,  green: 0.45, blue: 0.1,  alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 0.15, blue: 0.45, alpha: 1))
        ]
    )

    static let psychedelic = ColorPalette(
        id: "psychedelic",
        name: "Psychedelic",
        stops: [
            (0.0,  NSColor(red: 1.0,  green: 0.0,  blue: 0.5,  alpha: 1)),
            (0.2,  NSColor(red: 1.0,  green: 0.5,  blue: 0.0,  alpha: 1)),
            (0.4,  NSColor(red: 0.5,  green: 1.0,  blue: 0.0,  alpha: 1)),
            (0.6,  NSColor(red: 0.0,  green: 1.0,  blue: 0.8,  alpha: 1)),
            (0.8,  NSColor(red: 0.5,  green: 0.0,  blue: 1.0,  alpha: 1)),
            (1.0,  NSColor(red: 1.0,  green: 0.0,  blue: 0.5,  alpha: 1))
        ]
    )

    static let tealLime = ColorPalette(
        id: "teal-lime",
        name: "Teal Lime",
        stops: [
            (0.0,  NSColor(red: 0.0,  green: 0.35, blue: 0.35, alpha: 1)),
            (0.35, NSColor(red: 0.0,  green: 0.65, blue: 0.6,  alpha: 1)),
            (0.65, NSColor(red: 0.4,  green: 0.85, blue: 0.4,  alpha: 1)),
            (1.0,  NSColor(red: 0.8,  green: 1.0,  blue: 0.2,  alpha: 1))
        ]
    )
}
