import AppKit
import CoreImage

// MARK: - Emblem layer
//
// Draws the emblem geometry as a coverage mask, colours it from a palette by distance from the
// centre, then adds glow and opacity and blends it over the image like the other layers.

extension MandalaRenderer {

    static func applyEmblemLayer(image: CGImage, settings s: EmblemLayerSettings,
                                 palettes: [ColorPalette], size: Int) -> CGImage {
        let geometry = EmblemShapes.geometry(for: s)
        // Unit space → pixels: the frame ring (radius 0.92) gets a diameter of `size` × canvas.
        let unit = max(0.1, min(1.0, s.size)) * Double(size) * 0.5 / EmblemShapes.ringRadius
        guard let mask = emblemMask(geometry, rendering: s.rendering, weight: s.weight,
                                    unit: unit, size: size) else { return image }

        let palette = palettes[max(0, min(palettes.count - 1, s.paletteIndex))]
        guard var overlay = colourise(mask: mask, palette: palette, position: s.colorPosition,
                                      spread: s.colorSpread, unit: unit, size: size) else { return image }
        overlay = applyGlow(image: overlay, intensity: s.glow)
        if s.opacity < 0.999 {
            overlay = fadeEmblem(overlay, opacity: s.opacity)
        }
        let mode: String
        switch s.blendMode {
        case .normal:   mode = "CISourceOverCompositing"
        case .screen:   mode = "CIScreenBlendMode"
        case .add:      mode = "CIAdditionCompositing"
        case .multiply: mode = "CIMultiplyBlendMode"
        }
        return blendComposite(base: image, overlay: overlay, mode: mode)
    }

    // MARK: Mask

    /// 8-bit coverage of the emblem; memory row 0 is the top of the image.
    private static func emblemMask(_ g: EmblemGeometry, rendering: EmblemRendering, weight: Double,
                                   unit: Double, size: Int) -> [UInt8]? {
        var bytes = [UInt8](repeating: 0, count: size * size)
        let ok = bytes.withUnsafeMutableBytes { raw -> Bool in
            guard let ctx = CGContext(data: raw.baseAddress, width: size, height: size, bitsPerComponent: 8,
                                      bytesPerRow: size, space: CGColorSpaceCreateDeviceGray(),
                                      bitmapInfo: CGImageAlphaInfo.none.rawValue) else { return false }
            // y down, origin in the centre, one unit = `unit` pixels.
            ctx.translateBy(x: CGFloat(size) / 2, y: CGFloat(size) / 2)
            ctx.scaleBy(x: CGFloat(unit), y: -CGFloat(unit))
            ctx.setLineCap(.round)
            ctx.setLineJoin(.round)
            ctx.setShouldAntialias(true)

            let w = weight.clamped01
            let thick = 0.4 + 1.6 * w                   // scales the motif's own widths
            let line = 0.006 + 0.03 * w                 // uniform line width, in units
            let white = CGColor(gray: 1, alpha: 1), black = CGColor(gray: 0, alpha: 1)

            func strokeSegments(_ pts: [EmblemPoint], extra: Double, colour: CGColor) {
                ctx.setStrokeColor(colour)
                for i in 1..<pts.count {
                    let width = (pts[i - 1].w + pts[i].w) * 0.5 * thick + extra
                    guard width > 0.0005 else { continue }
                    ctx.setLineWidth(CGFloat(width))
                    ctx.move(to: CGPoint(x: pts[i - 1].x, y: pts[i - 1].y))
                    ctx.addLine(to: CGPoint(x: pts[i].x, y: pts[i].y))
                    ctx.strokePath()
                }
            }
            func fillDot(_ d: EmblemDot, radius: Double, colour: CGColor) {
                guard radius > 0 else { return }
                ctx.setFillColor(colour)
                ctx.fillEllipse(in: CGRect(x: d.x - radius, y: d.y - radius, width: radius * 2, height: radius * 2))
            }

            switch rendering {
            case .line:
                ctx.setStrokeColor(white)
                ctx.setLineWidth(CGFloat(line))
                for pts in g.strokes where pts.count > 1 {
                    ctx.addLines(between: pts.map { CGPoint(x: $0.x, y: $0.y) })
                    ctx.strokePath()
                }
                for d in g.dots {
                    ctx.strokeEllipse(in: CGRect(x: d.x - d.r, y: d.y - d.r, width: d.r * 2, height: d.r * 2))
                }
            case .filled:
                ctx.setFillColor(white)
                for group in g.fills {
                    for poly in group where poly.count > 2 {
                        ctx.addLines(between: poly.map { CGPoint(x: $0.x, y: $0.y) })
                        ctx.closePath()
                    }
                    ctx.fillPath(using: .evenOdd)
                }
                for pts in g.strokes where pts.count > 1 { strokeSegments(pts, extra: 0, colour: white) }
                for d in g.dots { fillDot(d, radius: d.r * thick.squareRoot(), colour: white) }
            case .doubleLine:
                // Outline of the filled shape: draw it widened, then cut out the narrowed shape.
                for pts in g.strokes where pts.count > 1 { strokeSegments(pts, extra: line, colour: white) }
                for d in g.dots { fillDot(d, radius: d.r * thick.squareRoot() + line / 2, colour: white) }
                for pts in g.strokes where pts.count > 1 { strokeSegments(pts, extra: -line, colour: black) }
                for d in g.dots { fillDot(d, radius: d.r * thick.squareRoot() - line / 2, colour: black) }
            case .beads:
                for pts in g.strokes where pts.count > 1 {
                    for b in beads(along: pts, thick: thick, minRadius: line * 0.8) {
                        fillDot(b, radius: b.r, colour: white)
                    }
                }
                for d in g.dots { fillDot(d, radius: d.r * thick.squareRoot(), colour: white) }
            }
            return true
        }
        return ok ? bytes : nil
    }

    /// Dots spaced along a stroke by arc length, sized by the stroke width there.
    private static func beads(along raw: [EmblemPoint], thick: Double, minRadius: Double) -> [EmblemDot] {
        // Smooth the widths first so neighbouring beads grow and shrink gently.
        let pts: [EmblemPoint] = raw.indices.map { i in
            let lo = max(0, i - 4), hi = min(raw.count - 1, i + 4)
            var p = raw[i]
            p.w = raw[lo...hi].reduce(0) { $0 + $1.w } / Double(hi - lo + 1)
            return p
        }
        var out: [EmblemDot] = []
        func radius(_ p: EmblemPoint) -> Double { max(minRadius, p.w * thick * 0.42) }
        var next = 0.0          // arc length where the next bead goes
        var walked = 0.0
        for i in 1..<pts.count {
            let a = pts[i - 1], b = pts[i]
            let len = hypot(b.x - a.x, b.y - a.y)
            while next <= walked + len {
                let t = len > 0 ? (next - walked) / len : 0
                let p = EmblemPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t, w: a.w + (b.w - a.w) * t)
                let r = radius(p)
                out.append(EmblemDot(x: p.x, y: p.y, r: r))
                next += max(r * 2.6, 0.004)
            }
            walked += len
        }
        return out
    }

    // MARK: Colour

    /// Premultiplied RGBA: palette colour by distance from the centre, alpha = coverage.
    private static func colourise(mask: [UInt8], palette: ColorPalette, position: Double, spread: Double,
                                  unit: Double, size: Int) -> CGImage? {
        var lut = [(Float, Float, Float)](repeating: (0, 0, 0), count: 256)
        for i in 0..<256 {
            let c = palette.color(at: Double(i) / 255).usingColorSpace(.deviceRGB) ?? .white
            lut[i] = (Float(c.redComponent), Float(c.greenComponent), Float(c.blueComponent))
        }
        let half = Double(size) / 2
        // The colour reaches `position + spread` at the outer edge of the lotus or gate frame.
        let outer = unit * 1.45
        var rgba = [UInt8](repeating: 0, count: size * size * 4)
        rgba.withUnsafeMutableBufferPointer { out in
            mask.withUnsafeBufferPointer { m in
                for y in 0..<size {
                    let dy = Double(y) + 0.5 - half
                    for x in 0..<size {
                        let a = m[y * size + x]
                        guard a > 0 else { continue }
                        let dx = Double(x) + 0.5 - half
                        // Run along the palette and back instead of jumping from its end to its start.
                        var t = (position + spread * min(1, (dx * dx + dy * dy).squareRoot() / outer))
                            .truncatingRemainder(dividingBy: 2)
                        if t < 0 { t += 2 }
                        if t > 1 { t = 2 - t }
                        let c = lut[Int(t * 255)]
                        let af = Float(a) / 255
                        let o = (y * size + x) * 4
                        out[o]     = UInt8(min(255, c.0 * af * 255 + 0.5))
                        out[o + 1] = UInt8(min(255, c.1 * af * 255 + 0.5))
                        out[o + 2] = UInt8(min(255, c.2 * af * 255 + 0.5))
                        out[o + 3] = a
                    }
                }
            }
        }
        guard let provider = CGDataProvider(data: Data(rgba) as CFData) else { return nil }
        return CGImage(width: size, height: size, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: size * 4,
                       space: CGColorSpaceCreateDeviceRGB(),
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                       provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)
    }

    /// Lowers alpha only, so the emblem fades instead of darkening.
    private static func fadeEmblem(_ image: CGImage, opacity: Double) -> CGImage {
        let ci = CIImage(cgImage: image)
        let s = CGFloat(max(0, min(1, opacity)))
        guard let f = CIFilter(name: "CIColorMatrix") else { return image }
        f.setValue(ci, forKey: kCIInputImageKey)
        // Core Image applies the matrix to unpremultiplied colour, so only alpha changes.
        f.setValue(CIVector(x: 0, y: 0, z: 0, w: s), forKey: "inputAVector")
        let ctx = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])
        guard let out = f.outputImage?.cropped(to: ci.extent) else { return image }
        return ctx.createCGImage(out, from: ci.extent) ?? image
    }
}

private extension Double {
    var clamped01: Double { Swift.max(0, Swift.min(1, self)) }
}
