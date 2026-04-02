import Foundation

enum StyleDrawing {
    static func paletteColor(_ palette: ColorPalette, at t: Double, colorOffset: Double = 0) -> (r: Float, g: Float, b: Float) {
        let c = palette.color(at: (t + colorOffset).truncatingRemainder(dividingBy: 1.0))
        return (Float(c.redComponent), Float(c.greenComponent), Float(c.blueComponent))
    }

    static func addPolyline(buffer: PixelBuffer, points: [(Float, Float)],
                            color: (r: Float, g: Float, b: Float), weight: Float) {
        guard points.count >= 2 else { return }
        for i in 0..<(points.count - 1) {
            buffer.addLine(x0: points[i].0, y0: points[i].1,
                           x1: points[i + 1].0, y1: points[i + 1].1,
                           color: color, weight: weight)
        }
    }

    static func addCircle(buffer: PixelBuffer, cx: Float, cy: Float, radius: Float,
                          color: (r: Float, g: Float, b: Float), weight: Float,
                          steps: Int = 96, start: Float = 0, end: Float = .pi * 2) {
        guard radius > 0.5, steps > 1 else { return }
        let dt = (end - start) / Float(steps)
        var pts: [(Float, Float)] = []
        pts.reserveCapacity(steps + 1)
        for i in 0...steps {
            let t = start + Float(i) * dt
            pts.append((cx + cos(t) * radius, cy + sin(t) * radius))
        }
        addPolyline(buffer: buffer, points: pts, color: color, weight: weight)
    }

    static func addEllipse(buffer: PixelBuffer, cx: Float, cy: Float, rx: Float, ry: Float,
                           rotation: Float = 0,
                           color: (r: Float, g: Float, b: Float), weight: Float,
                           steps: Int = 96, start: Float = 0, end: Float = .pi * 2) {
        guard rx > 0.5, ry > 0.5, steps > 1 else { return }
        let dt = (end - start) / Float(steps)
        let ca = cos(rotation)
        let sa = sin(rotation)
        var pts: [(Float, Float)] = []
        pts.reserveCapacity(steps + 1)
        for i in 0...steps {
            let t = start + Float(i) * dt
            let px = cos(t) * rx
            let py = sin(t) * ry
            pts.append((cx + px * ca - py * sa, cy + px * sa + py * ca))
        }
        addPolyline(buffer: buffer, points: pts, color: color, weight: weight)
    }

    static func drawTinyStar(buffer: PixelBuffer, cx: Float, cy: Float,
                             radius: Float, rotation: Float,
                             color: (r: Float, g: Float, b: Float),
                             coreWeight: Float, spikeWeight: Float) {
        let longR = radius
        let shortR = radius * 0.45
        for i in 0..<4 {
            let a = rotation + Float(i) * Float.pi * 0.5
            buffer.addLine(x0: cx - cos(a) * longR, y0: cy - sin(a) * longR,
                           x1: cx + cos(a) * longR, y1: cy + sin(a) * longR,
                           color: color, weight: spikeWeight)
        }
        for i in 0..<4 {
            let a = rotation + Float.pi * 0.25 + Float(i) * Float.pi * 0.5
            buffer.addLine(x0: cx - cos(a) * shortR, y0: cy - sin(a) * shortR,
                           x1: cx + cos(a) * shortR, y1: cy + sin(a) * shortR,
                           color: color, weight: spikeWeight * 0.55)
        }
        addCircle(buffer: buffer, cx: cx, cy: cy, radius: max(1.2, radius * 0.16),
                  color: color, weight: coreWeight, steps: 10)
    }
}
