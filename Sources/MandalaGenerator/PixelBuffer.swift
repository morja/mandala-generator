import Accelerate
import CoreGraphics
import Foundation

/// Float32 RGB pixel buffer with additive blending (light painting effect).
/// Values accumulate beyond 1.0 and are tone-mapped at export.
class PixelBuffer {
    let width: Int
    let height: Int
    var data: [Float]

    init(width: Int, height: Int) {
        self.width  = width
        self.height = height
        self.data   = [Float](repeating: 0, count: width * height * 3)
    }

    func clear() {
        data.withUnsafeMutableBufferPointer { ptr in
            guard let base = ptr.baseAddress, ptr.count > 0 else { return }
            vDSP_vclr(base, 1, vDSP_Length(ptr.count))
        }
    }

    /// Add another buffer into this one additively using vDSP SIMD.
    func mergeAdding(_ other: PixelBuffer) {
        guard other.data.count == data.count, !data.isEmpty else { return }
        data.withUnsafeMutableBufferPointer { dst in
            other.data.withUnsafeBufferPointer { src in
                guard let d = dst.baseAddress, let s = src.baseAddress else { return }
                vDSP_vadd(d, 1, s, 1, d, 1, vDSP_Length(dst.count))
            }
        }
    }

    /// Add a colour additively at pixel (x, y).
    @inline(__always)
    func addPixel(x: Int, y: Int, color: (r: Float, g: Float, b: Float), weight: Float) {
        guard x >= 0, y >= 0, x < width, y < height else { return }
        let base = (y * width + x) * 3
        data[base]     += color.r * weight
        data[base + 1] += color.g * weight
        data[base + 2] += color.b * weight
    }

    /// Wu anti-aliased line drawing with additive blending.
    /// Guards against NaN, infinity, and Int-overflow from extreme curve values.
    func addLine(x0: Float, y0: Float, x1: Float, y1: Float,
                 color: (r: Float, g: Float, b: Float), weight: Float) {
        // Reject degenerate inputs — prevents Int() overflow trap
        guard x0.isFinite, y0.isFinite, x1.isFinite, y1.isFinite else { return }
        let limit = Float(max(width, height) * 4)
        guard x0 > -limit, x0 < limit, y0 > -limit, y0 < limit,
              x1 > -limit, x1 < limit, y1 > -limit, y1 < limit else { return }

        var x0 = x0, y0 = y0, x1 = x1, y1 = y1
        let steep = abs(y1 - y0) > abs(x1 - x0)
        if steep { swap(&x0, &y0); swap(&x1, &y1) }
        if x0 > x1 { swap(&x0, &x1); swap(&y0, &y1) }

        let dx = x1 - x0
        let dy = y1 - y0
        let gradient: Float = dx == 0 ? 1.0 : dy / dx

        // First endpoint
        let xend0  = Float(toInt(x0 + 0.5))
        let yend0  = y0 + gradient * (xend0 - x0)
        let xgap0  = 1.0 - frac(x0 + 0.5)
        let xpxl1  = toInt(xend0)
        let ypxl1  = toInt(yend0)
        let yf1    = frac(yend0)
        if steep {
            addPixel(x: ypxl1,     y: xpxl1, color: color, weight: weight * (1 - yf1) * xgap0)
            addPixel(x: ypxl1 + 1, y: xpxl1, color: color, weight: weight * yf1 * xgap0)
        } else {
            addPixel(x: xpxl1, y: ypxl1,     color: color, weight: weight * (1 - yf1) * xgap0)
            addPixel(x: xpxl1, y: ypxl1 + 1, color: color, weight: weight * yf1 * xgap0)
        }
        var intery = yend0 + gradient

        // Second endpoint
        let xend1  = Float(toInt(x1 + 0.5))
        let yend1  = y1 + gradient * (xend1 - x1)
        let xgap1  = frac(x1 + 0.5)
        let xpxl2  = toInt(xend1)
        let ypxl2  = toInt(yend1)
        let yf2    = frac(yend1)
        if steep {
            addPixel(x: ypxl2,     y: xpxl2, color: color, weight: weight * (1 - yf2) * xgap1)
            addPixel(x: ypxl2 + 1, y: xpxl2, color: color, weight: weight * yf2 * xgap1)
        } else {
            addPixel(x: xpxl2, y: ypxl2,     color: color, weight: weight * (1 - yf2) * xgap1)
            addPixel(x: xpxl2, y: ypxl2 + 1, color: color, weight: weight * yf2 * xgap1)
        }

        // Middle pixels
        let loX = xpxl1 + 1
        let hiX = xpxl2
        guard loX < hiX else { return }
        if steep {
            for x in loX..<hiX {
                let iy = toInt(intery)
                let f  = frac(intery)
                addPixel(x: iy,     y: x, color: color, weight: weight * (1 - f))
                addPixel(x: iy + 1, y: x, color: color, weight: weight * f)
                intery += gradient
            }
        } else {
            for x in loX..<hiX {
                let iy = toInt(intery)
                let f  = frac(intery)
                addPixel(x: x, y: iy,     color: color, weight: weight * (1 - f))
                addPixel(x: x, y: iy + 1, color: color, weight: weight * f)
                intery += gradient
            }
        }
    }

    func addThickLine(x0: Float, y0: Float, x1: Float, y1: Float,
                      color: (r: Float, g: Float, b: Float), weight: Float, thickness: Int) {
        addLine(x0: x0, y0: y0, x1: x1, y1: y1, color: color, weight: weight)
        guard thickness > 1 else { return }
        let dx  = x1 - x0
        let dy  = y1 - y0
        let len = sqrt(dx * dx + dy * dy)
        guard len > 0 else { return }
        let nx   = -dy / len
        let ny   =  dx / len
        let half = Float(thickness) * 0.5
        for t in 1..<thickness {
            let off = (Float(t) - half) * 0.6
            let w   = weight * (1.0 - Float(t) / Float(thickness) * 0.5)
            addLine(x0: x0 + nx * off, y0: y0 + ny * off,
                    x1: x1 + nx * off, y1: y1 + ny * off,
                    color: color, weight: w)
        }
    }

    /// Convert to CGImage using filmic tone-mapping x/(x+1). Never crashes.
    func toCGImage() -> CGImage? {
        let pixelCount  = width * height
        var bytes       = [UInt8](repeating: 0, count: pixelCount * 4)
        let inv255      = Float(255.0)
        for i in 0..<pixelCount {
            let b3 = i * 3
            let r  = data[b3];     let g = data[b3 + 1]; let b = data[b3 + 2]
            let b4 = i * 4
            bytes[b4]     = toU8(r / (r + 0.55) * inv255)
            bytes[b4 + 1] = toU8(g / (g + 0.55) * inv255)
            bytes[b4 + 2] = toU8(b / (b + 0.55) * inv255)
            bytes[b4 + 3] = 255
        }
        guard let cfData   = CFDataCreate(nil, bytes, bytes.count),
              let provider = CGDataProvider(data: cfData) else { return nil }
        let space = CGColorSpace(name: CGColorSpace.displayP3) ?? CGColorSpaceCreateDeviceRGB()
        return CGImage(width: width, height: height,
                       bitsPerComponent: 8, bitsPerPixel: 32,
                       bytesPerRow: width * 4,
                       space: space,
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
                       provider: provider,
                       decode: nil,
                       shouldInterpolate: false,
                       intent: .defaultIntent)
    }

    // MARK: - Safe helpers

    /// Float → Int clamped to buffer bounds — never overflows.
    @inline(__always)
    private func toInt(_ x: Float) -> Int {
        Int(max(Float(Int.min / 2), min(Float(Int.max / 2), x)))
    }

    @inline(__always)
    private func toU8(_ x: Float) -> UInt8 {
        UInt8(max(0, min(255, Int(x + 0.5))))
    }

    @inline(__always)
    private func frac(_ x: Float) -> Float {
        x - floor(x)   // floor() handles negatives correctly; Int() does not
    }
}
