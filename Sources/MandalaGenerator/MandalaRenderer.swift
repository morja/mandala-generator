import AppKit
import CoreImage
import CoreImage.CIFilterBuiltins
import Foundation

// MARK: - SeededRNG

struct SeededRNG {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed == 0 ? 6364136223846793005 : seed
    }

    mutating func next() -> UInt64 {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }

    mutating func nextDouble() -> Double {
        return Double(next() & 0x7FFFFFFFFFFFFFFF) / Double(0x7FFFFFFFFFFFFFFF)
    }

    mutating func nextFloat() -> Float {
        return Float(nextDouble())
    }

    mutating func nextDouble(in range: ClosedRange<Double>) -> Double {
        return range.lowerBound + nextDouble() * (range.upperBound - range.lowerBound)
    }
}

// MARK: - MandalaRenderer

struct MandalaRenderer {

    static func render(params: MandalaParameters) -> NSImage {
        let bufferSize = params.outputSize * 2
        let buffer = PixelBuffer(width: bufferSize, height: bufferSize)

        var rng = SeededRNG(seed: params.seed)
        let palettes = ColorPalettes.all
        let palette = palettes[max(0, min(palettes.count - 1, params.paletteIndex))]

        // 1. Background
        drawBackground(buffer: buffer, palette: palette, seed: params.seed)

        // 2. Grass/fiber fill
        drawGrassFibers(buffer: buffer, params: params, palette: palette, rng: &rng)

        // 3. Structural layers
        let cx = Float(bufferSize) * 0.5
        let cy = Float(bufferSize) * 0.5
        let baseRadius = Double(bufferSize) * 0.42

        let layerCount = max(2, Int(params.complexity * 8) + 1)
        let symmetry = max(1, min(8, params.symmetry))

        switch params.style {
        case .spirograph:
            drawSpirographLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                                 params: params, palette: palette, rng: &rng,
                                 layerCount: layerCount, symmetry: symmetry)
        case .roseCurves:
            drawRoseLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                           params: params, palette: palette, rng: &rng,
                           layerCount: layerCount, symmetry: symmetry)
        case .stringArt:
            drawStringArtLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                                params: params, palette: palette, rng: &rng,
                                layerCount: layerCount, symmetry: symmetry)
        case .sunburst:
            drawSunburstLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                               params: params, palette: palette, rng: &rng,
                               layerCount: layerCount, symmetry: symmetry)
        case .epitrochoid:
            drawEpitrochoidLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                                  params: params, palette: palette, rng: &rng,
                                  layerCount: layerCount, symmetry: symmetry)
        case .mixed:
            let sub = layerCount / 5 + 1
            drawSpirographLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                                 params: params, palette: palette, rng: &rng,
                                 layerCount: sub, symmetry: symmetry)
            drawRoseLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius * 0.9,
                           params: params, palette: palette, rng: &rng,
                           layerCount: sub, symmetry: symmetry)
            drawStringArtLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius * 0.8,
                                params: params, palette: palette, rng: &rng,
                                layerCount: sub, symmetry: symmetry)
            drawSunburstLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius * 0.7,
                               params: params, palette: palette, rng: &rng,
                               layerCount: sub, symmetry: symmetry)
            drawEpitrochoidLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius * 0.6,
                                  params: params, palette: palette, rng: &rng,
                                  layerCount: sub, symmetry: symmetry)
        }

        // 4. Convert buffer to CGImage and apply post-processing
        var cgImage = buffer.toCGImage()

        // 5. Apply glow
        cgImage = applyGlow(image: cgImage, intensity: params.glowIntensity)

        // 6. Painterly effects for abstractLevel > 0.3
        if params.abstractLevel > 0.3 {
            cgImage = applyPaintedEffect(image: cgImage, abstractLevel: params.abstractLevel)
        }

        // 7. Downscale 2x -> final size using Lanczos
        cgImage = downscaleLanczos(image: cgImage, targetSize: params.outputSize)

        let size = NSSize(width: params.outputSize, height: params.outputSize)
        return NSImage(cgImage: cgImage, size: size)
    }

    // MARK: - Background

    private static func drawBackground(buffer: PixelBuffer, palette: ColorPalette, seed: UInt64) {
        let w = buffer.width
        let h = buffer.height
        let cx = Double(w) * 0.5
        let cy = Double(h) * 0.5
        let maxR = sqrt(cx * cx + cy * cy)
        let s = Int(seed & 0x7FFFFFFF)

        for y in 0..<h {
            for x in 0..<w {
                let dx = Double(x) - cx
                let dy = Double(y) - cy
                let r = sqrt(dx * dx + dy * dy) / maxR
                // Radial gradient darkness
                let radial = 1.0 - r * 0.7
                // Noise texture
                let nx = Double(x) / Double(w) * 4.0
                let ny = Double(y) / Double(h) * 4.0
                let noise = NoiseUtils.fbm2D(nx, ny, octaves: 3, seed: s) * 0.08

                let t = r * 0.5 + noise
                let col = palette.color(at: t)
                let brightness = Float(radial * 0.06 + noise * 0.5)

                let base = (y * w + x) * 3
                buffer.data[base]     = Float(col.redComponent)   * brightness
                buffer.data[base + 1] = Float(col.greenComponent) * brightness
                buffer.data[base + 2] = Float(col.blueComponent)  * brightness
            }
        }
    }

    // MARK: - Grass Fibers

    private static func drawGrassFibers(buffer: PixelBuffer, params: MandalaParameters,
                                        palette: ColorPalette, rng: inout SeededRNG) {
        let w = Float(buffer.width)
        let h = Float(buffer.height)
        let cx = w * 0.5
        let cy = h * 0.5
        let maxRadius = w * 0.46

        let nLines = Int(Double(15000) * params.density + 500)
        let symmetry = max(1, min(8, params.symmetry))

        for _ in 0..<nLines {
            let angle = rng.nextDouble() * Double.pi * 2.0
            let radiusFrac = rng.nextDouble()
            let startRadius = Float(radiusFrac) * maxRadius * 0.95
            let length = Float(rng.nextDouble(in: 3...18)) * (1.0 + Float(params.complexity) * 1.5)
            let wobble = Float(rng.nextDouble(in: -0.15...0.15))
            let t = rng.nextDouble()
            let col = palette.color(at: t)
            let weight = Float(rng.nextDouble(in: 0.03...0.25)) * Float(params.density)

            for sym in 0..<symmetry {
                let symAngle = angle + Double(sym) * Double.pi * 2.0 / Double(symmetry)
                let x0 = cx + cos(Float(symAngle)) * startRadius
                let y0 = cy + sin(Float(symAngle)) * startRadius
                let endAngle = Float(symAngle) + wobble
                let x1 = x0 + cos(endAngle) * length
                let y1 = y0 + sin(endAngle) * length

                let c = (r: Float(col.redComponent), g: Float(col.greenComponent), b: Float(col.blueComponent))
                buffer.addLine(x0: x0, y0: y0, x1: x1, y1: y1, color: c, weight: weight)
            }
        }
    }

    // MARK: - Spirograph Layers

    private static func drawSpirographLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                             radius: Double, params: MandalaParameters,
                                             palette: ColorPalette, rng: inout SeededRNG,
                                             layerCount: Int, symmetry: Int) {
        let ratios: [(Double, Double)] = [(7, 3), (5, 2), (8, 5), (9, 4), (11, 6), (7, 4), (13, 5), (6, 5)]
        for i in 0..<layerCount {
            let idx = i % ratios.count
            let R = radius * (0.5 + rng.nextDouble() * 0.5)
            let r = R * ratios[idx].1 / ratios[idx].0
            let d = r * (0.4 + rng.nextDouble() * 0.7)
            let (xs, ys) = spiroPoints(R: R, r: r, d: d, steps: 3000)
            let tOffset = rng.nextDouble()
            let weight = Float(rng.nextDouble(in: 0.4...1.2)) * Float(params.complexity)
            let thickness = Int(rng.nextDouble(in: 1...3))

            for sym in 0..<symmetry {
                let angle = Double(sym) * Double.pi * 2.0 / Double(symmetry)
                let cosA = Float(cos(angle))
                let sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: xs.count)
                var rys = [Float](repeating: 0, count: ys.count)
                for j in 0..<xs.count {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                drawCurve(buffer: buffer, cx: cx, cy: cy, xs: rxs, ys: rys,
                          palette: palette, tOffset: tOffset + Double(sym) * 0.1,
                          drift: params.colorDrift, weight: weight, thickness: thickness)
            }
        }
    }

    // MARK: - Rose Curve Layers

    private static func drawRoseLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                       radius: Double, params: MandalaParameters,
                                       palette: ColorPalette, rng: inout SeededRNG,
                                       layerCount: Int, symmetry: Int) {
        let kValues = [3, 4, 5, 6, 7, 8, 9, 2]
        for i in 0..<layerCount {
            let k = kValues[i % kValues.count]
            let r = radius * (0.5 + rng.nextDouble() * 0.5)
            let (xs, ys) = rosePoints(k: k, radius: r, steps: 2000)
            let tOffset = rng.nextDouble()
            let weight = Float(rng.nextDouble(in: 0.5...1.4)) * Float(params.complexity)
            let thickness = Int(rng.nextDouble(in: 1...2))
            let rotOffset = rng.nextDouble() * Double.pi * 2.0

            for sym in 0..<symmetry {
                let angle = Double(sym) * Double.pi * 2.0 / Double(symmetry) + rotOffset
                let cosA = Float(cos(angle))
                let sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: xs.count)
                var rys = [Float](repeating: 0, count: ys.count)
                for j in 0..<xs.count {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                drawCurve(buffer: buffer, cx: cx, cy: cy, xs: rxs, ys: rys,
                          palette: palette, tOffset: tOffset + Double(sym) * 0.07,
                          drift: params.colorDrift, weight: weight, thickness: thickness)
            }
        }
    }

    // MARK: - String Art Layers

    private static func drawStringArtLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                            radius: Double, params: MandalaParameters,
                                            palette: ColorPalette, rng: inout SeededRNG,
                                            layerCount: Int, symmetry: Int) {
        for _ in 0..<layerCount {
            let r = Float(radius * (0.5 + rng.nextDouble() * 0.5))
            let n = Int(rng.nextDouble(in: 20...60))
            let step = Int(rng.nextDouble(in: 3...8))
            let tOffset = rng.nextDouble()
            let weight = Float(rng.nextDouble(in: 0.2...0.8)) * Float(params.complexity)

            for sym in 0..<symmetry {
                let symAngle = Double(sym) * Double.pi * 2.0 / Double(symmetry)
                let cosA = Float(cos(symAngle))
                let sinA = Float(sin(symAngle))

                for i in 0..<n {
                    let a0 = Float(i) / Float(n) * Float.pi * 2.0
                    let j = (i + step) % n
                    let a1 = Float(j) / Float(n) * Float.pi * 2.0

                    let lx0 = cos(a0) * r
                    let ly0 = sin(a0) * r
                    let lx1 = cos(a1) * r
                    let ly1 = sin(a1) * r

                    let rx0 = cx + lx0 * cosA - ly0 * sinA
                    let ry0 = cy + lx0 * sinA + ly0 * cosA
                    let rx1 = cx + lx1 * cosA - ly1 * sinA
                    let ry1 = cy + lx1 * sinA + ly1 * cosA

                    let t = (tOffset + Double(i) / Double(n) * params.colorDrift)
                               .truncatingRemainder(dividingBy: 1.0)
                    let col = palette.color(at: t)
                    let c = (r: Float(col.redComponent), g: Float(col.greenComponent),
                             b: Float(col.blueComponent))
                    buffer.addLine(x0: rx0, y0: ry0, x1: rx1, y1: ry1, color: c, weight: weight)
                }
            }
        }
    }

    // MARK: - Sunburst Layers

    private static func drawSunburstLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                           radius: Double, params: MandalaParameters,
                                           palette: ColorPalette, rng: inout SeededRNG,
                                           layerCount: Int, symmetry: Int) {
        for _ in 0..<layerCount {
            let r = Float(radius * (0.3 + rng.nextDouble() * 0.7))
            let nRays = Int(rng.nextDouble(in: 12...60))
            let twist = Float(rng.nextDouble(in: -1.0...1.0)) * Float(params.colorDrift)
            let tOffset = rng.nextDouble()
            let weight = Float(rng.nextDouble(in: 0.3...1.0)) * Float(params.complexity)

            for sym in 0..<symmetry {
                let symAngle = Double(sym) * Double.pi * 2.0 / Double(symmetry)

                for i in 0..<nRays {
                    let frac = Float(i) / Float(nRays)
                    let angle = frac * Float.pi * 2.0 + Float(symAngle)
                    let innerR = r * 0.05
                    let outerR = r * (0.5 + Float(rng.nextDouble()) * 0.5)
                    let twistAngle = angle + outerR / r * twist

                    let x0 = cx + cos(angle) * innerR
                    let y0 = cy + sin(angle) * innerR
                    let x1 = cx + cos(twistAngle) * outerR
                    let y1 = cy + sin(twistAngle) * outerR

                    let t = (tOffset + Double(frac) * params.colorDrift)
                                .truncatingRemainder(dividingBy: 1.0)
                    let col = palette.color(at: t)
                    let c = (r: Float(col.redComponent), g: Float(col.greenComponent),
                             b: Float(col.blueComponent))
                    buffer.addLine(x0: x0, y0: y0, x1: x1, y1: y1, color: c, weight: weight)
                }
            }
        }
    }

    // MARK: - Epitrochoid Layers

    private static func drawEpitrochoidLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                              radius: Double, params: MandalaParameters,
                                              palette: ColorPalette, rng: inout SeededRNG,
                                              layerCount: Int, symmetry: Int) {
        let denominators = [3, 4, 5, 6, 7, 8, 9, 10]
        for i in 0..<layerCount {
            let denom = Double(denominators[i % denominators.count])
            let R = radius * (0.4 + rng.nextDouble() * 0.45)
            let r = R / denom
            let d = r * (0.5 + rng.nextDouble() * 0.8)
            let (xs, ys) = epitrochoidPoints(R: R, r: r, d: d, steps: 2500)
            let tOffset = rng.nextDouble()
            let weight = Float(rng.nextDouble(in: 0.4...1.2)) * Float(params.complexity)
            let thickness = Int(rng.nextDouble(in: 1...2))

            for sym in 0..<symmetry {
                let angle = Double(sym) * Double.pi * 2.0 / Double(symmetry)
                let cosA = Float(cos(angle))
                let sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: xs.count)
                var rys = [Float](repeating: 0, count: ys.count)
                for j in 0..<xs.count {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                drawCurve(buffer: buffer, cx: cx, cy: cy, xs: rxs, ys: rys,
                          palette: palette, tOffset: tOffset + Double(sym) * 0.08,
                          drift: params.colorDrift, weight: weight, thickness: thickness)
            }
        }
    }

    // MARK: - Curve Drawing Helper

    private static func drawCurve(buffer: PixelBuffer, cx: Float, cy: Float,
                                  xs: [Float], ys: [Float], palette: ColorPalette,
                                  tOffset: Double, drift: Double, weight: Float, thickness: Int) {
        guard xs.count > 1 else { return }
        let count = xs.count
        for i in 0..<(count - 1) {
            let t = (tOffset + Double(i) / Double(count) * drift)
                        .truncatingRemainder(dividingBy: 1.0)
            let tClamped = t < 0 ? t + 1.0 : t
            let col = palette.color(at: tClamped)
            let c = (r: Float(col.redComponent), g: Float(col.greenComponent),
                     b: Float(col.blueComponent))
            if thickness > 1 {
                buffer.addThickLine(x0: xs[i], y0: ys[i], x1: xs[i + 1], y1: ys[i + 1],
                                    color: c, weight: weight, thickness: thickness)
            } else {
                buffer.addLine(x0: xs[i], y0: ys[i], x1: xs[i + 1], y1: ys[i + 1],
                               color: c, weight: weight)
            }
        }
    }

    // MARK: - Curve Generators

    private static func spiroPoints(R: Double, r: Double, d: Double, steps: Int) -> ([Float], [Float]) {
        var xs = [Float]()
        var ys = [Float]()
        xs.reserveCapacity(steps + 1)
        ys.reserveCapacity(steps + 1)
        let diff = R - r
        let ratio = r == 0 ? 0 : diff / r
        // Compute LCM-based period
        let period = lcmPeriod(R: R, r: r)
        let tMax = period * 2.0 * Double.pi
        let dt = tMax / Double(steps)
        for i in 0...steps {
            let t = Double(i) * dt
            let x = diff * cos(t) + d * cos(ratio * t)
            let y = diff * sin(t) - d * sin(ratio * t)
            xs.append(Float(x))
            ys.append(Float(y))
        }
        return (xs, ys)
    }

    private static func epitrochoidPoints(R: Double, r: Double, d: Double, steps: Int) -> ([Float], [Float]) {
        var xs = [Float]()
        var ys = [Float]()
        xs.reserveCapacity(steps + 1)
        ys.reserveCapacity(steps + 1)
        let sum = R + r
        let ratio = r == 0 ? 0 : sum / r
        let period = lcmPeriod(R: R, r: r)
        let tMax = period * 2.0 * Double.pi
        let dt = tMax / Double(steps)
        for i in 0...steps {
            let t = Double(i) * dt
            let x = sum * cos(t) - d * cos(ratio * t)
            let y = sum * sin(t) - d * sin(ratio * t)
            xs.append(Float(x))
            ys.append(Float(y))
        }
        return (xs, ys)
    }

    private static func rosePoints(k: Int, radius: Double, steps: Int) -> ([Float], [Float]) {
        var xs = [Float]()
        var ys = [Float]()
        xs.reserveCapacity(steps + 1)
        ys.reserveCapacity(steps + 1)
        let period = k % 2 == 0 ? 2.0 * Double.pi : Double.pi
        let dt = period / Double(steps)
        for i in 0...steps {
            let theta = Double(i) * dt
            let r = radius * abs(cos(Double(k) * theta))
            xs.append(Float(r * cos(theta)))
            ys.append(Float(r * sin(theta)))
        }
        return (xs, ys)
    }

    private static func lcmPeriod(R: Double, r: Double) -> Double {
        guard r > 0 else { return 1 }
        // Approximate as rational fraction
        let ratio = R / r
        // Find a good period: smallest p such that p*(R/r) is nearly integer
        for p in 1...20 {
            let val = Double(p) * ratio
            if abs(val - val.rounded()) < 0.01 {
                return Double(p)
            }
        }
        return 10.0
    }

    // MARK: - Post-processing

    static func applyGlow(image: CGImage, intensity: Double) -> CGImage {
        guard intensity > 0 else { return image }
        let ciImage = CIImage(cgImage: image)
        let context = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])

        // Multiple blur radii for bloom
        let blurRadii: [Double] = [2.0, 8.0, 20.0]
        let strengths: [Double] = [1.0, 0.4, 0.15]

        var result = ciImage
        for (radius, strength) in zip(blurRadii, strengths) {
            guard let blurFilter = CIFilter(name: "CIGaussianBlur") else { continue }
            blurFilter.setValue(ciImage, forKey: kCIInputImageKey)
            blurFilter.setValue(radius * intensity * 3.0, forKey: kCIInputRadiusKey)
            guard let blurred = blurFilter.outputImage else { continue }

            // Screen composite: result + blurred - result*blurred (approx)
            let scaledStrength = strength * intensity
            if let screenFilter = CIFilter(name: "CIScreenBlendMode") {
                // Tint the blur by strength
                guard let tintFilter = CIFilter(name: "CIColorMatrix") else {
                    result = blended(base: result, overlay: blurred, strength: scaledStrength) ?? result
                    continue
                }
                let sv = Float(scaledStrength)
                tintFilter.setValue(blurred, forKey: kCIInputImageKey)
                tintFilter.setValue(CIVector(x: CGFloat(sv), y: 0, z: 0, w: 0), forKey: "inputRVector")
                tintFilter.setValue(CIVector(x: 0, y: CGFloat(sv), z: 0, w: 0), forKey: "inputGVector")
                tintFilter.setValue(CIVector(x: 0, y: 0, z: CGFloat(sv), w: 0), forKey: "inputBVector")
                tintFilter.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
                guard let tinted = tintFilter.outputImage else { continue }
                screenFilter.setValue(result, forKey: kCIInputBackgroundImageKey)
                screenFilter.setValue(tinted, forKey: kCIInputImageKey)
                if let out = screenFilter.outputImage {
                    result = out.cropped(to: ciImage.extent)
                }
            } else {
                result = blended(base: result, overlay: blurred, strength: scaledStrength) ?? result
            }
        }

        let rect = CGRect(x: 0, y: 0, width: image.width, height: image.height)
        guard let output = context.createCGImage(result, from: rect) else { return image }
        return output
    }

    private static func blended(base: CIImage, overlay: CIImage, strength: Double) -> CIImage? {
        guard let colorMatrix = CIFilter(name: "CIColorMatrix") else { return nil }
        let sv = Float(strength)
        colorMatrix.setValue(overlay, forKey: kCIInputImageKey)
        colorMatrix.setValue(CIVector(x: CGFloat(sv), y: 0, z: 0, w: 0), forKey: "inputRVector")
        colorMatrix.setValue(CIVector(x: 0, y: CGFloat(sv), z: 0, w: 0), forKey: "inputGVector")
        colorMatrix.setValue(CIVector(x: 0, y: 0, z: CGFloat(sv), w: 0), forKey: "inputBVector")
        colorMatrix.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
        guard let scaled = colorMatrix.outputImage else { return nil }
        guard let addFilter = CIFilter(name: "CIAdditionCompositing") else { return nil }
        addFilter.setValue(base, forKey: kCIInputBackgroundImageKey)
        addFilter.setValue(scaled, forKey: kCIInputImageKey)
        return addFilter.outputImage?.cropped(to: base.extent)
    }

    static func applyPaintedEffect(image: CGImage, abstractLevel: Double) -> CGImage {
        let ciImage = CIImage(cgImage: image)
        let context = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])
        let extent = ciImage.extent

        var result = ciImage

        // Displacement distortion using turbulence
        let displacementAmount = abstractLevel * 30.0
        if let turbulenceFilter = CIFilter(name: "CITurbulence") {
            turbulenceFilter.setValue(CIVector(x: 0, y: 0), forKey: kCIInputCenterKey)
            turbulenceFilter.setValue(Double(image.width) * 0.5, forKey: "inputSize")
            turbulenceFilter.setValue(abstractLevel * 3.0, forKey: "inputAmount")
            turbulenceFilter.setValue(3, forKey: "inputOctaves")
            if let turbulence = turbulenceFilter.outputImage?.cropped(to: extent) {
                if let dispFilter = CIFilter(name: "CIDisplacementDistortion") {
                    dispFilter.setValue(result, forKey: kCIInputImageKey)
                    dispFilter.setValue(turbulence, forKey: "inputDisplacementImage")
                    dispFilter.setValue(displacementAmount, forKey: kCIInputScaleKey)
                    if let displaced = dispFilter.outputImage?.cropped(to: extent) {
                        result = displaced
                    }
                }
            }
        }

        // Additional blur pass
        let blurRadius = abstractLevel * 3.0
        if let blurFilter = CIFilter(name: "CIGaussianBlur") {
            blurFilter.setValue(result, forKey: kCIInputImageKey)
            blurFilter.setValue(blurRadius, forKey: kCIInputRadiusKey)
            if let blurred = blurFilter.outputImage?.cropped(to: extent) {
                result = blurred
            }
        }

        // Heavy paint path for high abstractLevel
        if abstractLevel > 0.6 {
            let heavyBlur = abstractLevel * 8.0
            if let blurFilter = CIFilter(name: "CIGaussianBlur") {
                blurFilter.setValue(result, forKey: kCIInputImageKey)
                blurFilter.setValue(heavyBlur, forKey: kCIInputRadiusKey)
                if let blurred = blurFilter.outputImage?.cropped(to: extent) {
                    result = blurred
                }
            }

            // Color bleeding: blur a high-saturation version
            if let satFilter = CIFilter(name: "CIColorControls") {
                satFilter.setValue(result, forKey: kCIInputImageKey)
                satFilter.setValue(2.0, forKey: kCIInputSaturationKey)
                satFilter.setValue(0.1, forKey: kCIInputBrightnessKey)
                satFilter.setValue(1.0, forKey: kCIInputContrastKey)
                if let saturated = satFilter.outputImage {
                    if let blurFilter2 = CIFilter(name: "CIGaussianBlur") {
                        blurFilter2.setValue(saturated, forKey: kCIInputImageKey)
                        blurFilter2.setValue(heavyBlur * 1.5, forKey: kCIInputRadiusKey)
                        if let blurredSat = blurFilter2.outputImage?.cropped(to: extent) {
                            if let screenFilter = CIFilter(name: "CIScreenBlendMode") {
                                screenFilter.setValue(result, forKey: kCIInputBackgroundImageKey)
                                screenFilter.setValue(blurredSat, forKey: kCIInputImageKey)
                                if let screened = screenFilter.outputImage?.cropped(to: extent) {
                                    result = screened
                                }
                            }
                        }
                    }
                }
            }

            // Grain overlay
            if let randomFilter = CIFilter(name: "CIRandomGenerator"),
               let randomImg = randomFilter.outputImage {
                let grainStrength = (abstractLevel - 0.6) * 0.15
                if let grainMatrix = CIFilter(name: "CIColorMatrix") {
                    let gs = Float(grainStrength)
                    grainMatrix.setValue(randomImg.cropped(to: extent), forKey: kCIInputImageKey)
                    grainMatrix.setValue(CIVector(x: CGFloat(gs), y: 0, z: 0, w: 0), forKey: "inputRVector")
                    grainMatrix.setValue(CIVector(x: 0, y: CGFloat(gs), z: 0, w: 0), forKey: "inputGVector")
                    grainMatrix.setValue(CIVector(x: 0, y: 0, z: CGFloat(gs), w: 0), forKey: "inputBVector")
                    grainMatrix.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
                    if let grain = grainMatrix.outputImage?.cropped(to: extent) {
                        if let addFilter = CIFilter(name: "CIAdditionCompositing") {
                            addFilter.setValue(result, forKey: kCIInputBackgroundImageKey)
                            addFilter.setValue(grain, forKey: kCIInputImageKey)
                            if let withGrain = addFilter.outputImage?.cropped(to: extent) {
                                result = withGrain
                            }
                        }
                    }
                }
            }
        }

        let rect = CGRect(x: 0, y: 0, width: image.width, height: image.height)
        guard let output = context.createCGImage(result, from: rect) else { return image }
        return output
    }

    private static func downscaleLanczos(image: CGImage, targetSize: Int) -> CGImage {
        let ciImage = CIImage(cgImage: image)
        let context = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])

        let scaleX = CGFloat(targetSize) / CGFloat(image.width)
        let scaleY = CGFloat(targetSize) / CGFloat(image.height)

        guard let lanczos = CIFilter(name: "CILanczosScaleTransform") else {
            // Fallback: Core Graphics downscale
            let colorSpace = CGColorSpace(name: CGColorSpace.displayP3) ?? CGColorSpaceCreateDeviceRGB()
            guard let ctx = CGContext(data: nil, width: targetSize, height: targetSize,
                                      bitsPerComponent: 8, bytesPerRow: targetSize * 4,
                                      space: colorSpace,
                                      bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue).rawValue) else {
                return image
            }
            ctx.interpolationQuality = .high
            ctx.draw(image, in: CGRect(x: 0, y: 0, width: targetSize, height: targetSize))
            return ctx.makeImage() ?? image
        }

        lanczos.setValue(ciImage, forKey: kCIInputImageKey)
        lanczos.setValue(scaleX, forKey: kCIInputScaleKey)
        lanczos.setValue(scaleY / scaleX, forKey: kCIInputAspectRatioKey)
        guard let scaled = lanczos.outputImage else { return image }
        let rect = CGRect(x: 0, y: 0, width: targetSize, height: targetSize)
        return context.createCGImage(scaled, from: rect) ?? image
    }
}
