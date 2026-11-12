import CoreGraphics
import CoreText
import Foundation
@preconcurrency import MandalaCore

/// Core's `TextRenderer` on Core Text (PLAN-4 section 3.3). Core keeps the line breaking and the placement;
/// this class answers font metrics and widths and draws the placed lines into a coverage mask. Core calls it
/// from its render threads, so nothing here is shared but the font cache behind a lock; every `drawLines`
/// makes its own bitmap context.
///
/// Fonts: the settings name a macOS font (`Quotes.fonts`). iOS has most of them by the same PostScript or
/// family name; the others map through `FontMap` to the closest installed family. Metrics are rounded the
/// way the JVM's `FontMetrics` rounds them (AWT on the Mac is the iPhone's parity reference), so lines sit
/// where the JVM puts them and only the glyph masks differ.
final class CoreTextRenderer: NSObject, TextRenderer, @unchecked Sendable {
    private let lock = NSLock()
    private var fonts: [FontKey: CTFont] = [:]

    private struct FontKey: Hashable {
        let name: String
        let size: Float
        let italic: Bool
    }

    // ── TextRenderer ──

    func metrics(font: TextFont) -> TextRendererMetrics {
        let ct = ctFont(font)
        let ascent = CTFontGetAscent(ct), descent = CTFontGetDescent(ct), leading = CTFontGetLeading(ct)
        // AWT's FontDesignMetrics: ascent and descent rounded up from 0.05 below an integer, leading as the
        // difference that keeps their sum rounded the same way.
        let up = 0.95
        let a = floor(up + ascent), d = floor(up + descent)
        let l = floor(up + descent + leading) - d
        return TextRendererMetrics(ascent: Float(a), descent: Float(d), leading: Float(l))
    }

    func width(text: String, font: TextFont) -> Float {
        Float(CTLineGetTypographicBounds(line(text, font), nil, nil, nil))
    }

    func drawLines(width: Int32, height: Int32, lines: [PlacedLine]) -> KotlinFloatArray {
        let w = Int(width), h = Int(height)
        // One byte of coverage per pixel (white letters on black in a grey bitmap); rows aligned, so they may be longer than w.
        let bytesPerRow = (w + 15) / 16 * 16
        var alpha = Data(count: bytesPerRow * max(h, 1))
        alpha.withUnsafeMutableBytes { raw in
            guard w > 0, h > 0,
                  let ctx = CGContext(data: raw.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: bytesPerRow,
                                      space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGImageAlphaInfo.none.rawValue)
            else { return }
            ctx.setAllowsAntialiasing(true)
            ctx.setShouldAntialias(true)
            ctx.setAllowsFontSubpixelPositioning(true)
            ctx.setShouldSubpixelPositionFonts(true)
            ctx.setAllowsFontSmoothing(false)
            ctx.setFillColor(gray: 1, alpha: 1)
            // Core places lines with y down from the top; Core Graphics counts up from the bottom.
            for placed in lines {
                ctx.textMatrix = .identity
                ctx.textPosition = CGPoint(x: CGFloat(placed.x), y: CGFloat(h) - CGFloat(placed.baseline))
                CTLineDraw(line(placed.text, placed.font), ctx)
            }
        }
        // Core Graphics keeps row 0 of the buffer at the top of the picture.
        return IosBridge.shared.coverage(alpha: alpha, width: width, height: height, bytesPerRow: Int32(bytesPerRow))
    }

    // ── Fonts ──

    private func line(_ text: String, _ font: TextFont) -> CTLine {
        var attributes: [NSAttributedString.Key: Any] = [
            NSAttributedString.Key(kCTFontAttributeName as String): ctFont(font),
            NSAttributedString.Key(kCTForegroundColorFromContextAttributeName as String): true,
        ]
        // Tracking in pixels after every letter, as AWT's TRACKING (a share of the size) gives.
        if font.trackingPx != 0 { attributes[NSAttributedString.Key(kCTKernAttributeName as String)] = CGFloat(font.trackingPx) }
        return CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attributes))
    }

    private func ctFont(_ font: TextFont) -> CTFont {
        let key = FontKey(name: font.name, size: font.sizePx, italic: font.italic)
        lock.lock()
        defer { lock.unlock() }
        if let known = fonts[key] { return known }
        let base = CTFontCreateWithName(FontMap.installedName(for: font) as CFString, CGFloat(max(font.sizePx, 0.5)), nil)
        var made = base
        if font.italic {
            // The family's own italic where it has one, else the upright face slanted like AWT's oblique.
            if let italic = CTFontCreateCopyWithSymbolicTraits(base, 0, nil, .traitItalic, .traitItalic) {
                made = italic
            } else {
                var slant = CGAffineTransform(a: 1, b: 0, c: FontMap.obliqueSlant, d: 1, tx: 0, ty: 0)
                made = CTFontCreateCopyWithAttributes(base, CGFloat(max(font.sizePx, 0.5)), &slant, nil)
            }
        }
        fonts[key] = made
        return made
    }
}

/// Which installed iOS font draws each macOS font name of the settings (PLAN-4 section 3.3: "one table").
enum FontMap {
    /// A slant of about 12°, for an author line in a family without an italic face.
    static let obliqueSlant: CGFloat = 0.21

    /// The macOS name when iOS has it by that name, else the stand-in of this table, else one per family.
    static func installedName(for font: TextFont) -> String {
        if isInstalled(font.name) { return font.name }
        if let mapped = standIns[font.name], isInstalled(mapped) { return mapped }
        switch font.family {
        case .sans: return "HelveticaNeue"
        case .mono: return "Courier"
        case .script: return "SnellRoundhand"
        default: return "Georgia"
        }
    }

    /// Fonts of the macOS list that iOS lacks under their own name (checked in the simulator, IOS-5).
    static let standIns: [String: String] = [
        "HoeflerText-Regular": "Georgia",
        "Palatino-Roman": "Palatino-Roman",
        "Optima-Regular": "Optima-Regular",
    ]

    /// Whether Core Text finds the font itself, not its fallback (it hands back Helvetica for unknown names).
    static func isInstalled(_ name: String) -> Bool {
        let probe = CTFontCreateWithName(name as CFString, 12, nil)
        let found = CTFontCopyPostScriptName(probe) as String
        let family = CTFontCopyFamilyName(probe) as String
        return found == name || family == name || family.replacingOccurrences(of: " ", with: "") == name
    }
}
