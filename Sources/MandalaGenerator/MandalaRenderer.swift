import AppKit
import CoreImage
import CoreImage.CIFilterBuiltins
import Foundation
import os.lock

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

// MARK: - CurveDrawTask

/// All data needed to draw one curve — collected first, then drawn in parallel.
private struct CurveDrawTask {
    let xs: [Float]
    let ys: [Float]
    let tOffset: Double
    let drift: Double
    let weight: Float
    let thickness: Int
}

// MARK: - MandalaRenderer

struct MandalaRenderer {

    static func render(params: MandalaParameters) -> NSImage {
        let bufferSize = params.outputSize * 2
        let palettes   = ColorPalettes.all

        let cx = Float(bufferSize) * 0.5
        let cy = Float(bufferSize) * 0.5
        let baseRadius = Double(bufferSize) * 0.72

        // ── BACKGROUND (uses first layer's palette) ──
        let bgBuffer = PixelBuffer(width: bufferSize, height: bufferSize)
        let bgPaletteIdx = params.layers.first.map { max(0, min(palettes.count-1, $0.paletteIndex)) } ?? 0
        let bgPalette = palettes[bgPaletteIdx]
        drawBackground(buffer: bgBuffer, palette: bgPalette, seed: params.seed)

        // Grass fibers from first layer
        if let firstLayer = params.layers.first {
            var lp = params
            lp.density = firstLayer.density
            lp.complexity = firstLayer.complexity
            lp.paletteIndex = firstLayer.paletteIndex
            lp.symmetry = max(1, min(8, firstLayer.symmetry))
            var rng = SeededRNG(seed: params.seed)
            drawGrassFibers(buffer: bgBuffer, params: lp, palette: palettes[bgPaletteIdx], rng: &rng)
        }

        guard var compositeImage = bgBuffer.toCGImage() else { return NSImage() }

        // ── PER-LAYER RENDER ──
        for (li, layer) in params.layers.enumerated() {
            let layerSymmetry = max(1, min(8, layer.symmetry))
            let layerRadius = baseRadius * max(0.1, min(1.0, layer.scale))
            let layerCount  = max(2, Int(layer.complexity * 8) + 1)
            let palette     = palettes[max(0, min(palettes.count-1, layer.paletteIndex))]

            // Build a params copy with this layer's values for renderer internals
            var lp = params
            lp.symmetry      = layerSymmetry
            lp.complexity    = layer.complexity
            lp.density       = layer.density
            lp.glowIntensity = layer.glowIntensity
            lp.colorDrift    = layer.colorDrift
            lp.ripple        = layer.ripple
            lp.wash          = layer.wash
            lp.paletteIndex  = layer.paletteIndex

            let buffer = PixelBuffer(width: bufferSize, height: bufferSize)
            var layerRng = SeededRNG(seed: layer.seed == 0 ? params.seed &+ UInt64(li + 1) &* 0x9e3779b97f4a7c15 : layer.seed)

            drawStructuralLayers(buffer: buffer, cx: cx, cy: cy, baseRadius: layerRadius,
                                 params: lp, style: layer.style, colorOffset: layer.colorOffset,
                                 palette: palette, rng: &layerRng,
                                 layerCount: layerCount, symmetry: layerSymmetry,
                                 rippleAmount: Float(layer.ripple), weightMul: 1.0)

            guard var layerImage = buffer.toCGImage() else { continue }
            layerImage = applyGlow(image: layerImage, intensity: layer.glowIntensity)
            if layer.wash > 0 {
                layerImage = applyWash(image: layerImage, amount: layer.wash)
            }
            if layer.abstractLevel > 0.01 {
                layerImage = applyPaintedEffect(image: layerImage, abstractLevel: layer.abstractLevel)
            }
            layerImage = applyColourGrade(image: layerImage, saturation: layer.saturation, brightness: layer.brightness)
            compositeImage = screenComposite(base: compositeImage, overlay: layerImage)
        }

        // ── GLOBAL POST-PROCESS ──
        var result = compositeImage
        result = downscaleLanczos(image: result, targetSize: params.outputSize)
        let size = NSSize(width: params.outputSize, height: params.outputSize)
        return NSImage(cgImage: result, size: size)
    }

    // MARK: - Structural dispatch — collect tasks, then draw in parallel

    private static func drawStructuralLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                             baseRadius: Double, params: MandalaParameters,
                                             style: MandalaStyle, colorOffset: Double,
                                             palette: ColorPalette, rng: inout SeededRNG,
                                             layerCount: Int, symmetry: Int,
                                             rippleAmount: Float, weightMul: Float) {
        var tasks: [CurveDrawTask] = []
        tasks.reserveCapacity(layerCount * symmetry * 2)

        switch style {
        case .spirograph:
            collectSpirographTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                   params: params, rng: &rng, layerCount: layerCount,
                                   symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .roseCurves:
            collectRoseTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                             params: params, rng: &rng, layerCount: layerCount,
                             symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .stringArt:
            drawStringArtLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                                params: params, palette: palette, rng: &rng,
                                layerCount: layerCount, symmetry: symmetry)
            return
        case .sunburst:
            drawSunburstLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                               params: params, palette: palette, rng: &rng,
                               layerCount: layerCount, symmetry: symmetry)
            return
        case .epitrochoid:
            collectEpitrochoidTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                    params: params, rng: &rng, layerCount: layerCount,
                                    symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .floral:
            collectFloralTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                               params: params, rng: &rng, symmetry: symmetry,
                               rippleAmount: rippleAmount, weightMul: weightMul)
        case .lissajous:
            collectLissajousTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                  params: params, rng: &rng, layerCount: layerCount,
                                  symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .butterfly:
            collectButterflyTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                  params: params, rng: &rng, layerCount: layerCount,
                                  symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .geometric:
            collectGeometricTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                  params: params, rng: &rng, layerCount: layerCount,
                                  symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .mixed:
            // Seed-driven random zone selection — different every render
            var zoneRng = SeededRNG(seed: params.seed &+ 0xbeef1234)
            // Pick 3 distinct zone radii and assign a randomly chosen style to each
            let zoneStyles: [MandalaStyle] = [.spirograph, .roseCurves, .epitrochoid, .lissajous,
                                              .butterfly, .floral, .stringArt, .sunburst, .geometric]
            let radii: [Double] = [1.0, 0.72, 0.45]
            let sub = max(2, layerCount / 3)
            for (zi, zRadius) in radii.enumerated() {
                let pick = zoneStyles[Int(zoneRng.nextDouble() * Double(zoneStyles.count)) % zoneStyles.count]
                let scaled = baseRadius * zRadius
                let wmul = weightMul * Float(0.7 + zoneRng.nextDouble() * 0.6)
                switch pick {
                case .spirograph:
                    collectSpirographTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                           params: params, rng: &rng, layerCount: sub,
                                           symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .roseCurves:
                    collectRoseTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                     params: params, rng: &rng, layerCount: sub,
                                     symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .epitrochoid:
                    collectEpitrochoidTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                            params: params, rng: &rng, layerCount: sub,
                                            symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .lissajous:
                    collectLissajousTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                          params: params, rng: &rng, layerCount: sub,
                                          symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .butterfly:
                    collectButterflyTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                          params: params, rng: &rng, layerCount: sub,
                                          symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .floral:
                    collectFloralTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                       params: params, rng: &rng, symmetry: symmetry,
                                       rippleAmount: rippleAmount, weightMul: wmul)
                case .geometric:
                    collectGeometricTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                          params: params, rng: &rng, layerCount: sub,
                                          symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .stringArt:
                    drawStringArtLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                        params: params, palette: palette, rng: &rng,
                                        layerCount: sub, symmetry: symmetry)
                case .sunburst:
                    drawSunburstLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                       params: params, palette: palette, rng: &rng,
                                       layerCount: sub, symmetry: symmetry)
                case .mixed:
                    collectSpirographTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                           params: params, rng: &rng, layerCount: sub,
                                           symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                }
                _ = zi
            }
        }

        executeTasksParallel(tasks, buffer: buffer, palette: palette,
                             colorDrift: params.colorDrift, colorOffset: colorOffset)
    }

    /// Draw all CurveDrawTasks in parallel using per-thread sub-buffers, then merge.
    private static func executeTasksParallel(_ tasks: [CurveDrawTask],
                                             buffer: PixelBuffer,
                                             palette: ColorPalette,
                                             colorDrift: Double,
                                             colorOffset: Double = 0) {
        guard !tasks.isEmpty else { return }
        let nThreads = max(1, min(tasks.count, ProcessInfo.processInfo.activeProcessorCount))
        let chunk = max(1, tasks.count / nThreads)
        let subBuffers = (0..<nThreads).map { _ in
            PixelBuffer(width: buffer.width, height: buffer.height)
        }
        DispatchQueue.concurrentPerform(iterations: nThreads) { tid in
            let start = tid * chunk
            let end   = min(start + chunk, tasks.count)
            guard start < end else { return }
            let sub = subBuffers[tid]
            for i in start..<end {
                let t = tasks[i]
                drawCurve(buffer: sub, cx: 0, cy: 0, xs: t.xs, ys: t.ys,
                          palette: palette, tOffset: t.tOffset + colorOffset,
                          drift: t.drift, weight: t.weight, thickness: t.thickness)
            }
        }
        for sub in subBuffers { buffer.mergeAdding(sub) }
    }

    // MARK: - Background (GPU via CIFilter — much faster than per-pixel Swift loop)

    private static func drawBackground(buffer: PixelBuffer, palette: ColorPalette, seed: UInt64) {
        let w = buffer.width
        let h = buffer.height
        let col0 = palette.color(at: 0.0)
        let col1 = palette.color(at: 0.5)

        // Radial gradient tinted with palette, plus subtle random noise grain
        let ctx = CIContext()
        let extent = CGRect(x: 0, y: 0, width: w, height: h)

        // Dark tinted radial gradient
        let center = CIVector(x: CGFloat(w)/2, y: CGFloat(h)/2)
        var bg: CIImage? = nil
        if let grad = CIFilter(name: "CIRadialGradient") {
            grad.setValue(center, forKey: "inputCenter")
            grad.setValue(CGFloat(w) * 0.55, forKey: "inputRadius0")
            grad.setValue(CGFloat(w) * 0.85, forKey: "inputRadius1")
            let inner = CIColor(red: CGFloat(col0.redComponent)   * 0.12,
                                green: CGFloat(col0.greenComponent) * 0.12,
                                blue:  CGFloat(col0.blueComponent)  * 0.12)
            let outer = CIColor(red: 0.0, green: 0.0, blue: 0.0)
            grad.setValue(inner, forKey: "inputColor0")
            grad.setValue(outer, forKey: "inputColor1")
            bg = grad.outputImage?.cropped(to: extent)
        }

        // Subtle colour wash overlay using second palette stop
        if let wash = CIFilter(name: "CIConstantColorGenerator") {
            let tint = CIColor(red: CGFloat(col1.redComponent)   * 0.06,
                               green: CGFloat(col1.greenComponent) * 0.06,
                               blue:  CGFloat(col1.blueComponent)  * 0.06)
            wash.setValue(tint, forKey: kCIInputColorKey)
            if let washImg = wash.outputImage?.cropped(to: extent),
               let add = CIFilter(name: "CIAdditionCompositing") {
                add.setValue(bg ?? washImg, forKey: kCIInputBackgroundImageKey)
                add.setValue(washImg,       forKey: kCIInputImageKey)
                bg = add.outputImage?.cropped(to: extent)
            }
        }

        guard let finalCI = bg,
              let cgImg = ctx.createCGImage(finalCI, from: extent) else { return }

        // Copy CGImage pixels into buffer
        let bytesPerRow = w * 4
        var bytes = [UInt8](repeating: 0, count: h * bytesPerRow)
        let space = CGColorSpaceCreateDeviceRGB()
        guard let drawCtx = CGContext(data: &bytes, width: w, height: h,
                                      bitsPerComponent: 8, bytesPerRow: bytesPerRow,
                                      space: space,
                                      bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return }
        drawCtx.draw(cgImg, in: extent)
        let inv = Float(1.0 / 255.0)
        for i in 0..<(w * h) {
            let src = i * 4
            let dst = i * 3
            buffer.data[dst]     = Float(bytes[src])     * inv
            buffer.data[dst + 1] = Float(bytes[src + 1]) * inv
            buffer.data[dst + 2] = Float(bytes[src + 2]) * inv
        }
    }

    // MARK: - Grass Fibers (parallel)

    private static func drawGrassFibers(buffer: PixelBuffer, params: MandalaParameters,
                                        palette: ColorPalette, rng: inout SeededRNG) {
        let w = Float(buffer.width)
        let cx = w * 0.5
        let cy = cx
        let maxRadius = w * 0.48
        let nLines = Int(Double(15000) * params.density + 500)
        let symmetry = max(1, min(8, params.symmetry))
        let nThreads = max(1, ProcessInfo.processInfo.activeProcessorCount)
        let chunk = max(1, nLines / nThreads)

        // One sub-buffer per thread to avoid data races
        let subBuffers = (0..<nThreads).map { _ in
            PixelBuffer(width: buffer.width, height: buffer.height)
        }

        DispatchQueue.concurrentPerform(iterations: nThreads) { tid in
            let start = tid * chunk
            let end = min(start + chunk, nLines)
            guard start < end else { return }
            var local = SeededRNG(seed: params.seed &+ UInt64(tid) &* 6364136223846793005 &+ 1)
            let sub = subBuffers[tid]
            for _ in start..<end {
                let angle      = local.nextDouble() * .pi * 2.0
                let startR     = Float(local.nextDouble()) * maxRadius * 0.95
                let length     = Float(local.nextDouble(in: 3...18)) * (1.0 + Float(params.complexity) * 1.5)
                let wobble     = Float(local.nextDouble(in: -0.15...0.15))
                let t          = local.nextDouble()
                let col        = palette.color(at: t)
                let weight     = Float(local.nextDouble(in: 0.03...0.25)) * Float(params.density)
                let c = (r: Float(col.redComponent), g: Float(col.greenComponent), b: Float(col.blueComponent))
                for sym in 0..<symmetry {
                    let sa = Float(angle + Double(sym) * .pi * 2.0 / Double(symmetry))
                    let x0 = cx + cos(sa) * startR
                    let y0 = cy + sin(sa) * startR
                    let ea = sa + wobble
                    sub.addLine(x0: x0, y0: y0,
                                x1: x0 + cos(ea) * length,
                                y1: y0 + sin(ea) * length,
                                color: c, weight: weight)
                }
            }
        }
        for sub in subBuffers { buffer.mergeAdding(sub) }
    }

    // MARK: - Spirograph Layers

    private static func collectSpirographTasks(into tasks: inout [CurveDrawTask],
                                               cx: Float, cy: Float, radius: Double,
                                               params: MandalaParameters, rng: inout SeededRNG,
                                               layerCount: Int, symmetry: Int,
                                               rippleAmount: Float, weightMul: Float) {
        let ratios: [(Double, Double)] = [(7,3),(5,2),(8,5),(9,4),(11,6),(7,4),(13,5),(6,5)]
        for i in 0..<layerCount {
            let idx = i % ratios.count
            let R = radius * (0.5 + rng.nextDouble() * 0.5)
            let r = R * ratios[idx].1 / ratios[idx].0
            let d = r * (0.4 + rng.nextDouble() * 0.7)
            let (xs, ys) = spiroPoints(R: R, r: r, d: d, steps: 3000)
            let tOffset   = rng.nextDouble()
            let weight    = Float(rng.nextDouble(in: 0.4...1.2)) * Float(params.complexity) * weightMul
            let thickness = Int(rng.nextDouble(in: 1...3))
            let ripSeed   = params.seed &+ UInt64(i * 31 + 1)
            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2.0 / Double(symmetry)
                let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: xs.count)
                var rys = [Float](repeating: 0, count: ys.count)
                for j in 0..<xs.count {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                if rippleAmount > 0 {
                    applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                        seed: ripSeed &+ UInt64(sym))
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                           tOffset: tOffset + Double(sym) * 0.1,
                                           drift: params.colorDrift,
                                           weight: weight, thickness: thickness))
            }
        }
    }

    // MARK: - Rose Curve Layers

    private static func collectRoseTasks(into tasks: inout [CurveDrawTask],
                                         cx: Float, cy: Float, radius: Double,
                                         params: MandalaParameters, rng: inout SeededRNG,
                                         layerCount: Int, symmetry: Int,
                                         rippleAmount: Float, weightMul: Float) {
        let kValues = [3,4,5,6,7,8,9,2]
        for i in 0..<layerCount {
            let k = kValues[i % kValues.count]
            let r = radius * (0.5 + rng.nextDouble() * 0.5)
            let (xs, ys) = rosePoints(k: k, radius: r, steps: 2000)
            let tOffset   = rng.nextDouble()
            let weight    = Float(rng.nextDouble(in: 0.5...1.4)) * Float(params.complexity) * weightMul
            let thickness = Int(rng.nextDouble(in: 1...2))
            let rotOffset = rng.nextDouble() * .pi * 2.0
            let ripSeed   = params.seed &+ UInt64(i * 47 + 7)
            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2.0 / Double(symmetry) + rotOffset
                let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: xs.count)
                var rys = [Float](repeating: 0, count: ys.count)
                for j in 0..<xs.count {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                if rippleAmount > 0 {
                    applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                        seed: ripSeed &+ UInt64(sym))
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                           tOffset: tOffset + Double(sym) * 0.07,
                                           drift: params.colorDrift,
                                           weight: weight, thickness: thickness))
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

    // MARK: - Floral Layers

    /// Generates a composition matching the reference image:
    /// outer looping ring + radial teardrop petals with hatch fill + swirl connectors + central rosette.
    private static func collectFloralTasks(into tasks: inout [CurveDrawTask],
                                           cx: Float, cy: Float, radius: Double,
                                           params: MandalaParameters, rng: inout SeededRNG,
                                           symmetry: Int, rippleAmount: Float, weightMul: Float) {
        let R = Float(radius)
        let nPetals = max(5, symmetry * 2 + 3)

        // ── 1. OUTER LOOPING RING ──────────────────────────────────────────────
        // Formula: x = outerR*cos(t) + loopR*cos(k*t),  y = outerR*sin(t) + loopR*sin(k*t)
        let outerR      = R * 0.47
        let loopR       = R * Float(0.04 + params.density * 0.05)
        let nLoops      = nPetals * 3
        let outerSteps  = nLoops * 50
        var oxs = [Float](); oxs.reserveCapacity(outerSteps + 1)
        var oys = [Float](); oys.reserveCapacity(outerSteps + 1)
        for i in 0...outerSteps {
            let t = Float(i) / Float(outerSteps) * .pi * 2
            oxs.append(cx + outerR * cos(t) + loopR * cos(Float(nLoops) * t))
            oys.append(cy + outerR * sin(t) + loopR * sin(Float(nLoops) * t))
        }
        tasks.append(CurveDrawTask(xs: oxs, ys: oys,
                                   tOffset: 0.72, drift: params.colorDrift * 0.4,
                                   weight: 0.8 * weightMul, thickness: 1))

        // ── 2. TEARDROP PETALS ────────────────────────────────────────────────
        let petalDist  = R * 0.57
        let petalLen   = R * 0.51
        let petalW     = R * 0.195
        let pinwheel   = Float(0.28 + params.complexity * 0.18)   // lean angle
        let nHatch     = max(25, Int(params.density * 70))

        for i in 0..<nPetals {
            let baseAngle = Float(i) * .pi * 2 / Float(nPetals)
            let axisAngle = baseAngle + pinwheel        // petal points in this direction
            let pcx = cx + cos(baseAngle) * petalDist
            let pcy = cy + sin(baseAngle) * petalDist
            let petalT = Double(i) / Double(nPetals) * 0.35   // colour position (blue band)

            // Hatch fill — dense parallel lines across the petal width
            for h in 0..<nHatch {
                let t      = Float(h) / Float(nHatch - 1)      // 0 = tip, 1 = base
                let halfW  = petalW * 0.5 * sin(.pi * t)
                let along  = (t - 0.5) * petalLen
                let midX   = pcx + cos(axisAngle) * along
                let midY   = pcy + sin(axisAngle) * along
                let perpA  = axisAngle + .pi * 0.5
                let x0 = midX + cos(perpA) * halfW
                let y0 = midY + sin(perpA) * halfW
                let x1 = midX - cos(perpA) * halfW
                let y1 = midY - sin(perpA) * halfW
                let jitter = Float(rng.nextDouble(in: -0.5...0.5))
                tasks.append(CurveDrawTask(
                    xs: [x0, x1], ys: [y0, y1],
                    tOffset: petalT + Double(t) * 0.08 + Double(jitter) * 0.01,
                    drift: 0.04,
                    weight: Float(rng.nextDouble(in: 0.25...0.55)) * weightMul,
                    thickness: 1))
            }

            // Teardrop outline
            let nOut = 80
            var pxs = [Float](); pxs.reserveCapacity(nOut + 1)
            var pys = [Float](); pys.reserveCapacity(nOut + 1)
            for j in 0...nOut {
                let a        = Float(j) / Float(nOut) * .pi * 2
                let along    = petalLen * 0.5 * (1 - cos(a)) * 0.5 - petalLen * 0.25
                let perp     = petalW * 0.5 * sin(a)
                pxs.append(pcx + cos(axisAngle) * along + cos(axisAngle + .pi/2) * perp)
                pys.append(pcy + sin(axisAngle) * along + sin(axisAngle + .pi/2) * perp)
            }
            tasks.append(CurveDrawTask(xs: pxs, ys: pys,
                                       tOffset: 0.50 + Double(i) / Double(nPetals) * 0.25,
                                       drift: params.colorDrift * 0.15,
                                       weight: 0.9 * weightMul, thickness: 1))
        }

        // ── 3. SPIRAL CONNECTORS ──────────────────────────────────────────────
        let nSpirals = max(2, nPetals / 3)
        for s in 0..<nSpirals {
            let startA  = Float(s) * .pi * 2 / Float(nSpirals)
            let turns   = Float(nPetals) / Float(nSpirals)
            let spSteps = 280
            var sxs = [Float](); sxs.reserveCapacity(spSteps + 1)
            var sys = [Float](); sys.reserveCapacity(spSteps + 1)
            for i in 0...spSteps {
                let t     = Float(i) / Float(spSteps)
                let angle = startA + t * .pi * 2 * turns
                let r     = R * 0.04 + t * petalDist * 1.05
                let wave  = R * 0.018 * sin(Float(nPetals) * angle)
                sxs.append(cx + cos(angle) * (r + wave))
                sys.append(cy + sin(angle) * (r + wave))
            }
            let ripSeed = params.seed &+ UInt64(s) &* 997
            if rippleAmount > 0 {
                applyRippleToPoints(xs: &sxs, ys: &sys, amount: rippleAmount, seed: ripSeed)
            }
            tasks.append(CurveDrawTask(xs: sxs, ys: sys,
                                       tOffset: 0.48 + Double(s) / Double(nSpirals) * 0.12,
                                       drift: params.colorDrift * 0.5,
                                       weight: 0.55 * weightMul, thickness: 1))
        }

        // ── 4. CENTRAL ROSETTE ────────────────────────────────────────────────
        let flowerK  = nPetals / 2 + 1
        let flowerR2 = R * Float(0.06 + params.complexity * 0.03)
        let flSteps  = max(360, flowerK * 80)
        var fxs = [Float](); fxs.reserveCapacity(flSteps + 1)
        var fys = [Float](); fys.reserveCapacity(flSteps + 1)
        for i in 0...flSteps {
            let t = Float(i) / Float(flSteps) * .pi * 2
            let r = flowerR2 * abs(cos(Float(flowerK) * t))
            fxs.append(cx + r * cos(t))
            fys.append(cy + r * sin(t))
        }
        tasks.append(CurveDrawTask(xs: fxs, ys: fys,
                                   tOffset: 0.52, drift: 0.08,
                                   weight: 1.3 * weightMul, thickness: 1))
    }

    // MARK: - Lissajous Layers

    /// Lissajous figures: x = R·sin(a·t + δ),  y = R·cos(b·t)
    private static func collectLissajousTasks(into tasks: inout [CurveDrawTask],
                                              cx: Float, cy: Float, radius: Double,
                                              params: MandalaParameters, rng: inout SeededRNG,
                                              layerCount: Int, symmetry: Int,
                                              rippleAmount: Float, weightMul: Float) {
        let ratios: [(a: Int, b: Int)] = [
            (1,2),(2,3),(3,4),(3,5),(4,5),(5,6),(1,3),(2,5),(3,7),(4,7),(5,7),(5,8),(6,7),(7,9)
        ]
        for i in 0..<layerCount {
            let ratio  = ratios[i % ratios.count]
            let a      = Float(ratio.a),  b = Float(ratio.b)
            let R      = Float(radius) * Float(0.45 + rng.nextDouble() * 0.5)
            let delta  = Float(rng.nextDouble() * .pi * 2)
            let steps  = ratio.b * 280
            var xs = [Float](); xs.reserveCapacity(steps + 1)
            var ys = [Float](); ys.reserveCapacity(steps + 1)
            for j in 0...steps {
                let t = Float(j) / Float(steps) * .pi * 2
                xs.append(R * sin(a * t + delta))
                ys.append(R * cos(b * t))
            }
            let tOffset   = rng.nextDouble()
            let weight    = Float(rng.nextDouble(in: 0.4...1.4)) * Float(params.complexity) * weightMul
            let thickness = Int(rng.nextDouble(in: 1...2))
            let ripSeed   = params.seed &+ UInt64(i * 53 + 7)
            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2 / Double(symmetry)
                let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: xs.count)
                var rys = [Float](repeating: 0, count: xs.count)
                for j in 0..<xs.count {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                if rippleAmount > 0 {
                    applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                        seed: ripSeed &+ UInt64(sym))
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                           tOffset: tOffset + Double(sym) * 0.09,
                                           drift: params.colorDrift,
                                           weight: weight, thickness: thickness))
            }
        }
    }

    // MARK: - Butterfly Layers

    /// Temple H. Fay butterfly curve (polar): ρ = e^sin(θ) − 2·cos(4θ) + sin⁵((2θ−π)/24)
    private static func collectButterflyTasks(into tasks: inout [CurveDrawTask],
                                              cx: Float, cy: Float, radius: Double,
                                              params: MandalaParameters, rng: inout SeededRNG,
                                              layerCount: Int, symmetry: Int,
                                              rippleAmount: Float, weightMul: Float) {
        for i in 0..<layerCount {
            let scale     = Float(radius) * Float(0.16 + rng.nextDouble() * 0.20)
            let rotOffset = Float(rng.nextDouble() * .pi * 2)
            let steps     = 2000   // 12π period
            var xs = [Float](); xs.reserveCapacity(steps + 1)
            var ys = [Float](); ys.reserveCapacity(steps + 1)
            for j in 0...steps {
                let theta = Float(j) / Float(steps) * .pi * 12
                let s     = sin((2 * theta - .pi) / 24)
                let rho   = exp(sin(theta)) - 2 * cos(4 * theta) + s * s * s * s * s
                xs.append(scale * rho * cos(theta + rotOffset))
                ys.append(scale * rho * sin(theta + rotOffset))
            }
            let tOffset   = rng.nextDouble()
            let weight    = Float(rng.nextDouble(in: 0.3...0.9)) * Float(params.complexity) * weightMul
            let ripSeed   = params.seed &+ UInt64(i * 71 + 11)
            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2 / Double(symmetry)
                let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: xs.count)
                var rys = [Float](repeating: 0, count: xs.count)
                for j in 0..<xs.count {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                if rippleAmount > 0 {
                    applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                        seed: ripSeed &+ UInt64(sym))
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                           tOffset: tOffset + Double(sym) * 0.11,
                                           drift: params.colorDrift * 0.5,
                                           weight: weight, thickness: 1))
            }
        }
    }

    // MARK: - Geometric Layers

    /// Concentric architectural mandala: outer epitrochoid ring → middle rose → inner spirograph → central rosette.
    private static func collectGeometricTasks(into tasks: inout [CurveDrawTask],
                                              cx: Float, cy: Float, radius: Double,
                                              params: MandalaParameters, rng: inout SeededRNG,
                                              layerCount: Int, symmetry: Int,
                                              rippleAmount: Float, weightMul: Float) {
        let R = Float(radius)
        // ── Outer border: epitrochoid ──
        let eDenom = Double(5 + Int(rng.nextDouble() * 5))
        let eR = radius * 0.9
        let er = eR / eDenom
        let ed = er * (0.6 + rng.nextDouble() * 0.5)
        let (exs, eys) = epitrochoidPoints(R: eR, r: er, d: ed, steps: 3000)
        for sym in 0..<symmetry {
            let angle = Double(sym) * .pi * 2 / Double(symmetry)
            let cosA = Float(cos(angle)), sinA = Float(sin(angle))
            var rxs = [Float](repeating: 0, count: exs.count)
            var rys = [Float](repeating: 0, count: exs.count)
            for j in 0..<exs.count {
                rxs[j] = cx + exs[j] * cosA - eys[j] * sinA
                rys[j] = cy + exs[j] * sinA + eys[j] * cosA
            }
            if rippleAmount > 0 { applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount, seed: params.seed &+ UInt64(sym)) }
            tasks.append(CurveDrawTask(xs: rxs, ys: rys, tOffset: rng.nextDouble(),
                                       drift: params.colorDrift, weight: 0.8 * weightMul, thickness: 1))
        }
        // ── Middle ring: layered rose curves at varied radii ──
        let kVals = [5,6,7,8,9,10,11,12]
        let midCount = max(2, layerCount / 2)
        for i in 0..<midCount {
            let k   = kVals[(i + Int(params.seed & 7)) % kVals.count]
            let mR  = radius * (0.55 + rng.nextDouble() * 0.15)
            let (rxs0, rys0) = rosePoints(k: k, radius: mR, steps: 2000)
            let tOff = rng.nextDouble()
            let w    = Float(rng.nextDouble(in: 0.5...1.2)) * Float(params.complexity) * weightMul
            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2 / Double(symmetry) + rng.nextDouble() * 0.2
                let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                var rx = [Float](repeating: 0, count: rxs0.count)
                var ry = [Float](repeating: 0, count: rxs0.count)
                for j in 0..<rxs0.count {
                    rx[j] = cx + rxs0[j] * cosA - rys0[j] * sinA
                    ry[j] = cy + rxs0[j] * sinA + rys0[j] * cosA
                }
                if rippleAmount > 0 { applyRippleToPoints(xs: &rx, ys: &ry, amount: rippleAmount, seed: params.seed &+ UInt64(i * 37 + sym)) }
                tasks.append(CurveDrawTask(xs: rx, ys: ry, tOffset: tOff + Double(sym) * 0.07,
                                           drift: params.colorDrift, weight: w, thickness: 1))
            }
        }
        // ── Inner ring: spirograph ──
        let iR = radius * 0.32, ir = iR / Double(3 + Int(rng.nextDouble() * 4))
        let id2 = ir * (0.5 + rng.nextDouble() * 0.7)
        let (ixs, iys) = spiroPoints(R: iR, r: ir, d: id2, steps: 2000)
        let iToff = rng.nextDouble()
        let iW = Float(rng.nextDouble(in: 0.6...1.3)) * Float(params.complexity) * weightMul
        for sym in 0..<symmetry {
            let angle = Double(sym) * .pi * 2 / Double(symmetry)
            let cosA = Float(cos(angle)), sinA = Float(sin(angle))
            var rx = [Float](repeating: 0, count: ixs.count)
            var ry = [Float](repeating: 0, count: ixs.count)
            for j in 0..<ixs.count {
                rx[j] = cx + ixs[j] * cosA - iys[j] * sinA
                ry[j] = cy + ixs[j] * sinA + iys[j] * cosA
            }
            if rippleAmount > 0 { applyRippleToPoints(xs: &rx, ys: &ry, amount: rippleAmount, seed: params.seed &+ UInt64(100 + sym)) }
            tasks.append(CurveDrawTask(xs: rx, ys: ry, tOffset: iToff + Double(sym) * 0.05,
                                       drift: params.colorDrift * 0.6, weight: iW, thickness: 1))
        }
        // ── Centre: dense rose rosette ──
        let cK = 8 + Int(params.seed & 3)
        let cR = radius * 0.10
        let (cxs, cys) = rosePoints(k: cK, radius: cR, steps: 1200)
        var rcx = [Float](repeating: 0, count: cxs.count)
        var rcy = [Float](repeating: 0, count: cxs.count)
        for j in 0..<cxs.count { rcx[j] = cx + cxs[j]; rcy[j] = cy + cys[j] }
        tasks.append(CurveDrawTask(xs: rcx, ys: rcy, tOffset: 0.0, drift: 0.2,
                                   weight: 1.5 * weightMul, thickness: 1))
        _ = R  // suppress warning
    }

    // MARK: - Epitrochoid Layers

    private static func collectEpitrochoidTasks(into tasks: inout [CurveDrawTask],
                                                cx: Float, cy: Float, radius: Double,
                                                params: MandalaParameters, rng: inout SeededRNG,
                                                layerCount: Int, symmetry: Int,
                                                rippleAmount: Float, weightMul: Float) {
        let denominators = [3,4,5,6,7,8,9,10]
        for i in 0..<layerCount {
            let denom = Double(denominators[i % denominators.count])
            let R = radius * (0.4 + rng.nextDouble() * 0.45)
            let r = R / denom
            let d = r * (0.5 + rng.nextDouble() * 0.8)
            let (xs, ys) = epitrochoidPoints(R: R, r: r, d: d, steps: 2500)
            let tOffset   = rng.nextDouble()
            let weight    = Float(rng.nextDouble(in: 0.4...1.2)) * Float(params.complexity) * weightMul
            let thickness = Int(rng.nextDouble(in: 1...2))
            let ripSeed   = params.seed &+ UInt64(i * 61 + 13)
            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2.0 / Double(symmetry)
                let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: xs.count)
                var rys = [Float](repeating: 0, count: ys.count)
                for j in 0..<xs.count {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                if rippleAmount > 0 {
                    applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                        seed: ripSeed &+ UInt64(sym))
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                           tOffset: tOffset + Double(sym) * 0.08,
                                           drift: params.colorDrift,
                                           weight: weight, thickness: thickness))
            }
        }
    }

    // MARK: - Curve Drawing Helper

    /// Draw a curve. When xs/ys are already in absolute buffer coordinates, pass cx=0, cy=0.
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
            let ax0 = cx + xs[i],   ay0 = cy + ys[i]
            let ax1 = cx + xs[i+1], ay1 = cy + ys[i+1]
            if thickness > 1 {
                buffer.addThickLine(x0: ax0, y0: ay0, x1: ax1, y1: ay1,
                                    color: c, weight: weight, thickness: thickness)
            } else {
                buffer.addLine(x0: ax0, y0: ay0, x1: ax1, y1: ay1,
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

    // MARK: - Ripple distortion

    /// Apply multi-frequency sine-wave ripple to absolute-coordinate point arrays.
    private static func applyRippleToPoints(xs: inout [Float], ys: inout [Float],
                                            amount: Float, seed: UInt64) {
        let n = xs.count
        guard n > 1 else { return }
        // Three frequency bands for richer ripple
        let f1 = 3.0 + Float(seed & 0x7) * 0.5
        let f2 = 7.0 + Float((seed >> 4) & 0x7) * 0.7
        let f3 = 13.0 + Float((seed >> 8) & 0x7) * 0.9
        let p1 = Float(seed & 0xFF) / 255.0 * .pi * 2
        let p2 = Float((seed >> 8) & 0xFF) / 255.0 * .pi * 2
        let p3 = Float((seed >> 16) & 0xFF) / 255.0 * .pi * 2
        // Scale factor so ripple is proportional to element spacing
        let scale = amount * 18.0
        for i in 0..<n {
            let t = Float(i) / Float(n) * .pi * 2
            let dx = (sin(t * f1 + p1) * 0.6 + sin(t * f2 + p2) * 0.3 + sin(t * f3 + p3) * 0.1) * scale
            let dy = (cos(t * f1 + p1) * 0.6 + cos(t * f2 + p2) * 0.3 + cos(t * f3 + p3) * 0.1) * scale
            xs[i] += dx
            ys[i] += dy
        }
    }

    // MARK: - Wash (watercolour bleed)

    static func applyWash(image: CGImage, amount: Double) -> CGImage {
        guard amount > 0 else { return image }
        let ci  = CIImage(cgImage: image)
        let ctx = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])
        let ext = ci.extent
        var result = ci

        // Multiple blur passes at different radii — soft, overlapping, no hard edges.
        // Each pass is dissolved back at low opacity so gradients stay smooth.
        let passes: [(radius: Double, opacity: Double)] = [
            (amount * 4,  0.25),
            (amount * 12, 0.20),
            (amount * 28, 0.15),
            (amount * 55, 0.10),
        ]
        for (radius, opacity) in passes {
            guard let blur = CIFilter(name: "CIGaussianBlur") else { continue }
            blur.setValue(ci, forKey: kCIInputImageKey)   // always blur the original
            blur.setValue(radius, forKey: kCIInputRadiusKey)
            guard let blurred = blur.outputImage?.cropped(to: ext) else { continue }
            // Scale blurred layer to `opacity` before screen-compositing
            guard let cm = CIFilter(name: "CIColorMatrix") else { continue }
            let s = Float(opacity)
            cm.setValue(blurred, forKey: kCIInputImageKey)
            cm.setValue(CIVector(x: CGFloat(s),y:0,z:0,w:0), forKey: "inputRVector")
            cm.setValue(CIVector(x: 0,y: CGFloat(s),z:0,w:0), forKey: "inputGVector")
            cm.setValue(CIVector(x: 0,y:0,z: CGFloat(s),w:0), forKey: "inputBVector")
            cm.setValue(CIVector(x: 0,y:0,z:0,w:1),           forKey: "inputAVector")
            guard let scaled = cm.outputImage?.cropped(to: ext) else { continue }
            // Addition compositing — purely additive, no clamping artefacts at edges
            guard let add = CIFilter(name: "CIAdditionCompositing") else { continue }
            add.setValue(result, forKey: kCIInputBackgroundImageKey)
            add.setValue(scaled,  forKey: kCIInputImageKey)
            if let out = add.outputImage?.cropped(to: ext) { result = out }
        }

        // Chromatic bleed: blur a saturated copy and add softly — avoids banding
        // by blurring the saturated image very heavily (no hard gradient boundaries)
        if amount > 0.15, let sat = CIFilter(name: "CIColorControls"),
           let blur2 = CIFilter(name: "CIGaussianBlur"),
           let cm2 = CIFilter(name: "CIColorMatrix"),
           let add2 = CIFilter(name: "CIAdditionCompositing") {
            sat.setValue(ci, forKey: kCIInputImageKey)
            sat.setValue(min(4.0, 1.0 + amount * 3.0), forKey: kCIInputSaturationKey)
            sat.setValue(0.0, forKey: kCIInputBrightnessKey)
            if let saturated = sat.outputImage {
                blur2.setValue(saturated, forKey: kCIInputImageKey)
                blur2.setValue(amount * 45.0, forKey: kCIInputRadiusKey)
                if let blurredSat = blur2.outputImage?.cropped(to: ext) {
                    let s2 = Float(amount * 0.25)
                    cm2.setValue(blurredSat, forKey: kCIInputImageKey)
                    cm2.setValue(CIVector(x: CGFloat(s2),y:0,z:0,w:0), forKey: "inputRVector")
                    cm2.setValue(CIVector(x: 0,y: CGFloat(s2),z:0,w:0), forKey: "inputGVector")
                    cm2.setValue(CIVector(x: 0,y:0,z: CGFloat(s2),w:0), forKey: "inputBVector")
                    cm2.setValue(CIVector(x: 0,y:0,z:0,w:1),            forKey: "inputAVector")
                    if let tinted = cm2.outputImage?.cropped(to: ext) {
                        add2.setValue(result, forKey: kCIInputBackgroundImageKey)
                        add2.setValue(tinted,  forKey: kCIInputImageKey)
                        if let out = add2.outputImage?.cropped(to: ext) { result = out }
                    }
                }
            }
        }

        return ctx.createCGImage(result, from: ext) ?? image
    }

    // MARK: - Screen composite (painted base + crisp overlay)

    private static func screenComposite(base: CGImage, overlay: CGImage) -> CGImage {
        let ciBase    = CIImage(cgImage: base)
        let ciOverlay = CIImage(cgImage: overlay)
        let ctx = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])
        let ext = ciBase.extent
        guard let screen = CIFilter(name: "CIScreenBlendMode") else { return base }
        screen.setValue(ciBase,    forKey: kCIInputBackgroundImageKey)
        screen.setValue(ciOverlay, forKey: kCIInputImageKey)
        guard let out = screen.outputImage?.cropped(to: ext) else { return base }
        return ctx.createCGImage(out, from: ext) ?? base
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

        // Displacement distortion using turbulence — all amounts scale linearly
        let displacementAmount = abstractLevel * 30.0
        if let turbulenceFilter = CIFilter(name: "CITurbulence") {
            turbulenceFilter.setValue(CIVector(x: 0, y: 0), forKey: kCIInputCenterKey)
            turbulenceFilter.setValue(Double(image.width) * (0.1 + abstractLevel * 0.45), forKey: "inputSize")
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

        // Heavy paint path — smooth ramp starting from abstractLevel=0.3, full at 1.0
        let heavyFactor = max(0.0, (abstractLevel - 0.3) / 0.7)
        let heavyBlur = heavyFactor * abstractLevel * 8.0
        if heavyBlur > 0.5 {
            if let blurFilter = CIFilter(name: "CIGaussianBlur") {
                blurFilter.setValue(result, forKey: kCIInputImageKey)
                blurFilter.setValue(heavyBlur, forKey: kCIInputRadiusKey)
                if let blurred = blurFilter.outputImage?.cropped(to: extent) {
                    result = blurred
                }
            }

            // Color bleeding: blur a high-saturation version, blended proportionally
            if let satFilter = CIFilter(name: "CIColorControls") {
                satFilter.setValue(result, forKey: kCIInputImageKey)
                satFilter.setValue(1.0 + heavyFactor, forKey: kCIInputSaturationKey)
                satFilter.setValue(0.1 * heavyFactor, forKey: kCIInputBrightnessKey)
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

            // Grain overlay — smoothly ramps with heavyFactor
            if let randomFilter = CIFilter(name: "CIRandomGenerator"),
               let randomImg = randomFilter.outputImage {
                let grainStrength = heavyFactor * abstractLevel * 0.12
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

    // MARK: - Colour grade (saturation + brightness)

    private static func applyColourGrade(image: CGImage,
                                         saturation: Double,
                                         brightness: Double) -> CGImage {
        let ci  = CIImage(cgImage: image)
        let ctx = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])
        let ext = ci.extent
        guard let f = CIFilter(name: "CIColorControls") else { return image }
        // saturation: slider 0→1 maps to CIColorControls 0→3 (0=grey, 1=normal, 3=vivid)
        f.setValue(ci, forKey: kCIInputImageKey)
        f.setValue(saturation * 3.0, forKey: kCIInputSaturationKey)
        // brightness: slider 0→1 maps to -0.4 → +0.4 (0.5 = neutral)
        f.setValue((brightness - 0.5) * 0.8, forKey: kCIInputBrightnessKey)
        // slight contrast lift when brightness is high to keep punch
        f.setValue(0.9 + brightness * 0.3, forKey: kCIInputContrastKey)
        guard let out = f.outputImage?.cropped(to: ext) else { return image }
        return ctx.createCGImage(out, from: ext) ?? image
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
