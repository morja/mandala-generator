import CoreGraphics
import Foundation

/// Float32 RGB pixel buffer with additive blending (light painting effect).
/// Values accumulate beyond 1.0 and are tone-mapped at export.
class PixelBuffer {
    let width: Int
    let height: Int
    var data: [Float]

    init(width: Int, height: Int) {
        self.width = width
        self.height = height
        self.data = [Float](repeating: 0, count: width * height * 3)
    }

    func clear() {
        for i in data.indices { data[i] = 0 }
    }

    /// Add a color additively at pixel (x, y). Weight is a brightness multiplier.
    @inline(__always)
    func addPixel(x: Int, y: Int, color: (r: Float, g: Float, b: Float), weight: Float) {
        guard x >= 0, y >= 0, x < width, y < height else { return }
        let base = (y * width + x) * 3
        data[base]     += color.r * weight
        data[base + 1] += color.g * weight
        data[base + 2] += color.b * weight
    }

    /// Wu anti-aliased line drawing with additive blending.
    func addLine(x0: Float, y0: Float, x1: Float, y1: Float,
                 color: (r: Float, g: Float, b: Float), weight: Float) {
        var x0 = x0, y0 = y0, x1 = x1, y1 = y1
        let steep = abs(y1 - y0) > abs(x1 - x0)
        if steep {
            swap(&x0, &y0)
            swap(&x1, &y1)
        }
        if x0 > x1 {
            swap(&x0, &x1)
            swap(&y0, &y1)
        }
        let dx = x1 - x0
        let dy = y1 - y0
        let gradient: Float = dx == 0 ? 1.0 : dy / dx

        // First endpoint
        let xend0 = Float(Int(x0 + 0.5))
        let yend0 = y0 + gradient * (xend0 - x0)
        let xgap0 = 1.0 - frac(x0 + 0.5)
        let xpxl1 = Int(xend0)
        let ypxl1 = Int(yend0)
        let yf1 = frac(yend0)
        if steep {
            addPixel(x: ypxl1,     y: xpxl1, color: color, weight: weight * (1.0 - yf1) * xgap0)
            addPixel(x: ypxl1 + 1, y: xpxl1, color: color, weight: weight * yf1 * xgap0)
        } else {
            addPixel(x: xpxl1, y: ypxl1,     color: color, weight: weight * (1.0 - yf1) * xgap0)
            addPixel(x: xpxl1, y: ypxl1 + 1, color: color, weight: weight * yf1 * xgap0)
        }
        var intery = yend0 + gradient

        // Second endpoint
        let xend1 = Float(Int(x1 + 0.5))
        let yend1 = y1 + gradient * (xend1 - x1)
        let xgap1 = frac(x1 + 0.5)
        let xpxl2 = Int(xend1)
        let ypxl2 = Int(yend1)
        let yf2 = frac(yend1)
        if steep {
            addPixel(x: ypxl2,     y: xpxl2, color: color, weight: weight * (1.0 - yf2) * xgap1)
            addPixel(x: ypxl2 + 1, y: xpxl2, color: color, weight: weight * yf2 * xgap1)
        } else {
            addPixel(x: xpxl2, y: ypxl2,     color: color, weight: weight * (1.0 - yf2) * xgap1)
            addPixel(x: xpxl2, y: ypxl2 + 1, color: color, weight: weight * yf2 * xgap1)
        }

        // Middle pixels
        let loX = xpxl1 + 1
        let hiX = xpxl2
        guard loX < hiX else { return }
        if steep {
            for x in loX..<hiX {
                let iy = Int(intery)
                let f = frac(intery)
                addPixel(x: iy,     y: x, color: color, weight: weight * (1.0 - f))
                addPixel(x: iy + 1, y: x, color: color, weight: weight * f)
                intery += gradient
            }
        } else {
            for x in loX..<hiX {
                let iy = Int(intery)
                let f = frac(intery)
                addPixel(x: x, y: iy,     color: color, weight: weight * (1.0 - f))
                addPixel(x: x, y: iy + 1, color: color, weight: weight * f)
                intery += gradient
            }
        }
    }

    /// Draw thick line by drawing multiple parallel offset lines.
    func addThickLine(x0: Float, y0: Float, x1: Float, y1: Float,
                      color: (r: Float, g: Float, b: Float), weight: Float, thickness: Int) {
        addLine(x0: x0, y0: y0, x1: x1, y1: y1, color: color, weight: weight)
        guard thickness > 1 else { return }
        let dx = x1 - x0
        let dy = y1 - y0
        let len = sqrt(dx * dx + dy * dy)
        guard len > 0 else { return }
        let nx = -dy / len
        let ny = dx / len
        let half = Float(thickness) * 0.5
        for t in 1..<thickness {
            let offset = (Float(t) - half) * 0.6
            addLine(
                x0: x0 + nx * offset, y0: y0 + ny * offset,
                x1: x1 + nx * offset, y1: y1 + ny * offset,
                color: color, weight: weight * (1.0 - Float(t) / Float(thickness) * 0.5)
            )
        }
    }

    /// Convert to CGImage using filmic tone-mapping x/(x+1).
    func toCGImage() -> CGImage {
        var bytes = [UInt8](repeating: 0, count: width * height * 4)
        let pixelCount = width * height
        for i in 0..<pixelCount {
            let base = i * 3
            let r = data[base]
            let g = data[base + 1]
            let b = data[base + 2]
            // Filmic tone-map each channel
            let tr = r / (r + 1.0)
            let tg = g / (g + 1.0)
            let tb = b / (b + 1.0)
            let outBase = i * 4
            bytes[outBase]     = UInt8(min(255, Int(tr * 255.0 + 0.5)))
            bytes[outBase + 1] = UInt8(min(255, Int(tg * 255.0 + 0.5)))
            bytes[outBase + 2] = UInt8(min(255, Int(tb * 255.0 + 0.5)))
            bytes[outBase + 3] = 255
        }
        let cfData = CFDataCreate(nil, bytes, bytes.count)!
        let provider = CGDataProvider(data: cfData)!
        return CGImage(
            width: width, height: height,
            bitsPerComponent: 8, bitsPerPixel: 32,
            bytesPerRow: width * 4,
            space: CGColorSpace(name: CGColorSpace.displayP3) ?? CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
            provider: provider,
            decode: nil,
            shouldInterpolate: false,
            intent: .defaultIntent
        )!
    }

    @inline(__always)
    private func frac(_ x: Float) -> Float {
        return x - Float(Int(x))
    }
}
