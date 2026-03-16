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

        // ── BACKGROUND ──
        let bgBuffer = PixelBuffer(width: bufferSize, height: bufferSize)
        if params.baseLayer.isEnabled {
            drawBaseLayer(buffer: bgBuffer, settings: params.baseLayer, bufferSize: bufferSize)
        } else {
            let bgPaletteIdx = params.layers.first.map { max(0, min(palettes.count-1, $0.paletteIndex)) } ?? 0
            let bgPalette = palettes[bgPaletteIdx]
            drawBackground(buffer: bgBuffer, palette: bgPalette, seed: params.seed)
            if let firstLayer = params.layers.first {
                var lp = params
                lp.density = firstLayer.density
                lp.complexity = firstLayer.complexity
                lp.paletteIndex = firstLayer.paletteIndex
                lp.symmetry = max(1, min(8, firstLayer.symmetry))
                var rng = SeededRNG(seed: params.seed)
                drawGrassFibers(buffer: bgBuffer, params: lp, palette: palettes[bgPaletteIdx], rng: &rng)
            }
        }

        guard var compositeImage = bgBuffer.toCGImage() else { return NSImage() }

        // ── PER-LAYER RENDER ──
        for (li, layer) in params.layers.enumerated() {
            guard layer.isEnabled else { continue }
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
            if layer.rotation > 0.001 || layer.rotation < -0.001 {
                layerImage = rotateImage(layerImage, angle: layer.rotation * .pi * 2)
            }
            switch layer.blendMode {
            case .screen:   compositeImage = blendComposite(base: compositeImage, overlay: layerImage, mode: "CIScreenBlendMode")
            case .add:      compositeImage = blendComposite(base: compositeImage, overlay: layerImage, mode: "CIAdditionCompositing")
            case .normal:   compositeImage = blendComposite(base: compositeImage, overlay: layerImage, mode: "CILightenBlendMode")
            case .multiply: compositeImage = blendComposite(base: compositeImage, overlay: layerImage, mode: "CIMultiplyBlendMode")
            }
        }

        // ── EFFECTS LAYER ──
        var result = compositeImage
        if params.effectsLayer.isEnabled {
            result = applyEffectsLayer(image: result, settings: params.effectsLayer, bufferSize: bufferSize)
        }

        // ── GLOBAL POST-PROCESS ──
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
        case .fractal:
            collectFractalTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                params: params, rng: &rng, layerCount: layerCount,
                                symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .phyllotaxis:
            collectPhyllotaxisTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                    params: params, rng: &rng, layerCount: layerCount,
                                    symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .hypocycloid:
            collectHypocycloidTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                    params: params, rng: &rng, layerCount: layerCount,
                                    symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .waveInterference:
            collectWaveInterferenceTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                         params: params, rng: &rng, layerCount: layerCount,
                                         symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .spiderWeb:
            collectSpiderWebTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                  params: params, rng: &rng, layerCount: layerCount,
                                  symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .weave:
            collectWeaveTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                              params: params, rng: &rng, layerCount: layerCount,
                              symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .sacredGeometry:
            collectSacredGeometryTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                       params: params, rng: &rng, layerCount: layerCount,
                                       symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .radialMesh:
            collectRadialMeshTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                   params: params, rng: &rng, layerCount: layerCount,
                                   symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .flowField:
            collectFlowFieldTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                  params: params, rng: &rng, layerCount: layerCount,
                                  symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .tendril:
            collectTendrilTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                params: params, rng: &rng, layerCount: layerCount,
                                symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .moire:
            collectMoireTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                              params: params, rng: &rng, layerCount: layerCount,
                              symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .voronoi:
            collectVoronoiTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                params: params, rng: &rng, layerCount: layerCount,
                                symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .mixed:
            // Seed-driven random zone selection — different every render
            var zoneRng = SeededRNG(seed: params.seed &+ 0xbeef1234)
            // Pick 3 distinct zone radii and assign a randomly chosen style to each
            let zoneStyles: [MandalaStyle] = [.spirograph, .roseCurves, .epitrochoid, .lissajous,
                                              .butterfly, .floral, .stringArt, .sunburst, .geometric, .fractal,
                                              .phyllotaxis, .hypocycloid, .waveInterference, .spiderWeb,
                                              .weave, .sacredGeometry, .radialMesh, .flowField, .tendril,
                                              .moire, .voronoi]
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
                case .fractal:
                    collectFractalTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                        params: params, rng: &rng, layerCount: sub,
                                        symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .phyllotaxis:
                    collectPhyllotaxisTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                            params: params, rng: &rng, layerCount: sub,
                                            symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .hypocycloid:
                    collectHypocycloidTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                            params: params, rng: &rng, layerCount: sub,
                                            symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .waveInterference:
                    collectWaveInterferenceTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                                 params: params, rng: &rng, layerCount: sub,
                                                 symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .spiderWeb:
                    collectSpiderWebTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                          params: params, rng: &rng, layerCount: sub,
                                          symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .weave:
                    collectWeaveTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                      params: params, rng: &rng, layerCount: sub,
                                      symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .sacredGeometry:
                    collectSacredGeometryTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                               params: params, rng: &rng, layerCount: sub,
                                               symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .radialMesh:
                    collectRadialMeshTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                           params: params, rng: &rng, layerCount: sub,
                                           symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .flowField:
                    collectFlowFieldTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                          params: params, rng: &rng, layerCount: sub,
                                          symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .tendril:
                    collectTendrilTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                        params: params, rng: &rng, layerCount: sub,
                                        symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .moire:
                    collectMoireTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                      params: params, rng: &rng, layerCount: sub,
                                      symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .voronoi:
                    collectVoronoiTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                        params: params, rng: &rng, layerCount: sub,
                                        symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
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

    // MARK: - HSB → RGB (pure Swift, thread-safe)

    private static func hsbToRGB(h: Double, s: Double, b: Double) -> (r: Float, g: Float, b: Float) {
        guard s > 0 else { let v = Float(b); return (v, v, v) }
        let h6 = h * 6.0
        let i  = Int(h6) % 6
        let f  = h6 - Double(Int(h6))
        let p  = b * (1 - s)
        let q  = b * (1 - s * f)
        let t  = b * (1 - s * (1 - f))
        switch i {
        case 0:  return (Float(b), Float(t), Float(p))
        case 1:  return (Float(q), Float(b), Float(p))
        case 2:  return (Float(p), Float(b), Float(t))
        case 3:  return (Float(p), Float(q), Float(b))
        case 4:  return (Float(t), Float(p), Float(b))
        default: return (Float(b), Float(p), Float(q))
        }
    }

    // MARK: - Copy CIImage pixels into PixelBuffer

    private static func copyCI(_ ci: CIImage, to buffer: PixelBuffer, extent: CGRect, opacity: Float) {
        let w = buffer.width
        let h = buffer.height
        let ciCtx = CIContext()
        guard let cgImg = ciCtx.createCGImage(ci, from: extent) else { return }
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
            buffer.data[dst]     = Float(bytes[src])     * inv * opacity
            buffer.data[dst + 1] = Float(bytes[src + 1]) * inv * opacity
            buffer.data[dst + 2] = Float(bytes[src + 2]) * inv * opacity
        }
    }

    // MARK: - Base Layer

    private static func drawBaseLayer(buffer: PixelBuffer, settings: BaseLayerSettings, bufferSize: Int) {
        let w   = bufferSize
        let h   = bufferSize
        let ext = CGRect(x: 0, y: 0, width: w, height: h)
        let opacity = Float(settings.opacity)

        let c1 = hsbToRGB(h: settings.hue,  s: settings.saturation,  b: settings.brightness)
        let c2 = hsbToRGB(h: settings.hue2, s: settings.saturation2, b: settings.brightness2)
        let ci1 = CIColor(red: CGFloat(c1.r), green: CGFloat(c1.g), blue: CGFloat(c1.b))
        let ci2 = CIColor(red: CGFloat(c2.r), green: CGFloat(c2.g), blue: CGFloat(c2.b))

        switch settings.type {

        case .color:
            for i in 0..<(w * h) {
                let dst = i * 3
                buffer.data[dst]     = c1.r * opacity
                buffer.data[dst + 1] = c1.g * opacity
                buffer.data[dst + 2] = c1.b * opacity
            }

        case .gradient:
            var gradImg: CIImage?
            if settings.isRadial {
                if let grad = CIFilter(name: "CIRadialGradient") {
                    grad.setValue(CIVector(x: CGFloat(w)/2, y: CGFloat(h)/2), forKey: "inputCenter")
                    grad.setValue(0 as CGFloat,                               forKey: "inputRadius0")
                    grad.setValue(CGFloat(w) * 0.72,                          forKey: "inputRadius1")
                    grad.setValue(ci1, forKey: "inputColor0")
                    grad.setValue(ci2, forKey: "inputColor1")
                    gradImg = grad.outputImage?.cropped(to: ext)
                }
            } else {
                if let grad = CIFilter(name: "CISmoothLinearGradient") {
                    let angle = settings.gradientAngle * .pi * 2
                    let dx = CGFloat(cos(angle)) * CGFloat(w) * 0.5
                    let dy = CGFloat(sin(angle)) * CGFloat(h) * 0.5
                    let cx = CGFloat(w) / 2, cy = CGFloat(h) / 2
                    grad.setValue(CIVector(x: cx - dx, y: cy - dy), forKey: "inputPoint0")
                    grad.setValue(CIVector(x: cx + dx, y: cy + dy), forKey: "inputPoint1")
                    grad.setValue(ci1, forKey: "inputColor0")
                    grad.setValue(ci2, forKey: "inputColor1")
                    gradImg = grad.outputImage?.cropped(to: ext)
                }
            }
            if let img = gradImg { copyCI(img, to: buffer, extent: ext, opacity: opacity) }

        case .pattern:
            let tileSize = CGFloat(w) * CGFloat(0.02 + settings.patternScale * 0.18)
            let sharp    = Float(settings.patternSharpness)
            var patternImg: CIImage?
            switch settings.patternType {
            case 0: // Checkerboard
                if let f = CIFilter(name: "CICheckerboardGenerator") {
                    f.setValue(CIVector(x: CGFloat(w)/2, y: CGFloat(h)/2), forKey: "inputCenter")
                    f.setValue(ci1,   forKey: "inputColor0")
                    f.setValue(ci2,   forKey: "inputColor1")
                    f.setValue(tileSize, forKey: "inputWidth")
                    f.setValue(sharp, forKey: "inputSharpness")
                    patternImg = f.outputImage?.cropped(to: ext)
                }
            case 1: // Horizontal stripes
                if let f = CIFilter(name: "CIStripesGenerator") {
                    f.setValue(CIVector(x: CGFloat(w)/2, y: CGFloat(h)/2), forKey: "inputCenter")
                    f.setValue(ci1,      forKey: "inputColor0")
                    f.setValue(ci2,      forKey: "inputColor1")
                    f.setValue(tileSize, forKey: "inputWidth")
                    f.setValue(sharp,    forKey: "inputSharpness")
                    patternImg = f.outputImage?.cropped(to: ext)
                }
            case 2: // Diagonal stripes
                if let f = CIFilter(name: "CIStripesGenerator") {
                    f.setValue(CIVector(x: 0, y: 0), forKey: "inputCenter")
                    f.setValue(ci1,      forKey: "inputColor0")
                    f.setValue(ci2,      forKey: "inputColor1")
                    f.setValue(tileSize, forKey: "inputWidth")
                    f.setValue(sharp,    forKey: "inputSharpness")
                    if let stripes = f.outputImage {
                        patternImg = stripes
                            .transformed(by: CGAffineTransform(rotationAngle: .pi / 4))
                            .cropped(to: ext)
                    }
                }
            default: // Crosshatch
                if let f1 = CIFilter(name: "CIStripesGenerator"),
                   let f2 = CIFilter(name: "CIStripesGenerator") {
                    let black = CIColor(red: 0, green: 0, blue: 0)
                    for (f, col) in [(f1, ci1), (f2, ci2)] {
                        f.setValue(CIVector(x: 0, y: 0), forKey: "inputCenter")
                        f.setValue(black,          forKey: "inputColor0")
                        f.setValue(col,            forKey: "inputColor1")
                        f.setValue(tileSize * 0.6, forKey: "inputWidth")
                        f.setValue(sharp,          forKey: "inputSharpness")
                    }
                    if let s1 = f1.outputImage, let s2 = f2.outputImage {
                        let r1 = s1.transformed(by: CGAffineTransform(rotationAngle:  .pi/4)).cropped(to: ext)
                        let r2 = s2.transformed(by: CGAffineTransform(rotationAngle: -.pi/4)).cropped(to: ext)
                        if let add = CIFilter(name: "CIAdditionCompositing") {
                            add.setValue(r1, forKey: kCIInputBackgroundImageKey)
                            add.setValue(r2, forKey: kCIInputImageKey)
                            patternImg = add.outputImage?.cropped(to: ext)
                        }
                    }
                }
            }
            if let img = patternImg { copyCI(img, to: buffer, extent: ext, opacity: opacity) }

        case .grain:
            if let rngFilter = CIFilter(name: "CIRandomGenerator"),
               let noiseImg  = rngFilter.outputImage,
               let cm        = CIFilter(name: "CIColorMatrix") {
                let ga = Float(settings.grainAmount)
                let bd = Float(settings.brightness * 0.6)
                let (r, g, b) = settings.grainColored ? (c1.r, c1.g, c1.b) : (1.0 as Float, 1.0 as Float, 1.0 as Float)
                cm.setValue(noiseImg.cropped(to: ext), forKey: kCIInputImageKey)
                cm.setValue(CIVector(x: CGFloat(r * ga), y: 0,            z: 0,            w: 0), forKey: "inputRVector")
                cm.setValue(CIVector(x: 0,            y: CGFloat(g * ga), z: 0,            w: 0), forKey: "inputGVector")
                cm.setValue(CIVector(x: 0,            y: 0,            z: CGFloat(b * ga), w: 0), forKey: "inputBVector")
                cm.setValue(CIVector(x: 0,            y: 0,            z: 0,            w: 1),    forKey: "inputAVector")
                cm.setValue(CIVector(x: CGFloat(c1.r * bd), y: CGFloat(c1.g * bd), z: CGFloat(c1.b * bd), w: 0),
                            forKey: "inputBiasVector")
                if let tinted = cm.outputImage?.cropped(to: ext) {
                    copyCI(tinted, to: buffer, extent: ext, opacity: opacity)
                }
            }

        case .image:
            guard let url   = settings.imageURL,
                  let src   = CGImageSourceCreateWithURL(url as CFURL, nil),
                  let cgImg = CGImageSourceCreateImageAtIndex(src, 0, nil) else {
                // Fallback: solid color
                let imgOpacity = Float(settings.imageBlend) * opacity
                for i in 0..<(w * h) {
                    let dst = i * 3
                    buffer.data[dst]     = c1.r * imgOpacity
                    buffer.data[dst + 1] = c1.g * imgOpacity
                    buffer.data[dst + 2] = c1.b * imgOpacity
                }
                return
            }
            let bytesPerRow = w * 4
            var bytes = [UInt8](repeating: 0, count: h * bytesPerRow)
            let space = CGColorSpaceCreateDeviceRGB()
            guard let drawCtx = CGContext(data: &bytes, width: w, height: h,
                                          bitsPerComponent: 8, bytesPerRow: bytesPerRow,
                                          space: space,
                                          bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return }
            drawCtx.interpolationQuality = .high
            drawCtx.draw(cgImg, in: ext)
            let inv = Float(1.0 / 255.0)
            let imgOpacity = Float(settings.imageBlend) * opacity
            for i in 0..<(w * h) {
                let src4 = i * 4
                let dst  = i * 3
                buffer.data[dst]     = Float(bytes[src4])     * inv * imgOpacity
                buffer.data[dst + 1] = Float(bytes[src4 + 1]) * inv * imgOpacity
                buffer.data[dst + 2] = Float(bytes[src4 + 2]) * inv * imgOpacity
            }
        }
    }

    // MARK: - Effects Layer

    private static func applyEffectsLayer(image: CGImage, settings: EffectsLayerSettings, bufferSize: Int) -> CGImage {
        let ciCtx = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])
        let ext = CGRect(x: 0, y: 0, width: bufferSize, height: bufferSize)
        var ci  = CIImage(cgImage: image)

        // ── Vignette ──
        if settings.vignette > 0,
           let vig = CIFilter(name: "CIVignette") {
            vig.setValue(ci, forKey: kCIInputImageKey)
            vig.setValue(settings.vignette * 2.5, forKey: kCIInputIntensityKey)
            vig.setValue(0.4 + settings.vignette * 0.6, forKey: kCIInputRadiusKey)
            if let out = vig.outputImage?.cropped(to: ext) { ci = out }
        }

        // ── Dimming — random soft dark radial blotches (multiply blend) ──
        if settings.dimming > 0 {
            var rng = SeededRNG(seed: settings.dimmingSeed)
            let nSpots = Int(settings.dimming * 10) + 3
            for _ in 0..<nSpots {
                let cx = CGFloat(rng.nextDouble()) * CGFloat(bufferSize)
                let cy = CGFloat(rng.nextDouble()) * CGFloat(bufferSize)
                let r1 = CGFloat(bufferSize) * CGFloat(rng.nextDouble() * 0.3 + 0.08)
                let dark = CGFloat(settings.dimming) * CGFloat(rng.nextDouble() * 0.5 + 0.3)
                if let grad = CIFilter(name: "CIRadialGradient") {
                    grad.setValue(CIVector(x: cx, y: cy), forKey: "inputCenter")
                    grad.setValue(0 as CGFloat, forKey: "inputRadius0")
                    grad.setValue(r1,           forKey: "inputRadius1")
                    grad.setValue(CIColor(red: 1-dark, green: 1-dark, blue: 1-dark), forKey: "inputColor0")
                    grad.setValue(CIColor(red: 1,      green: 1,      blue: 1),      forKey: "inputColor1")
                    if let gradImg = grad.outputImage?.cropped(to: ext),
                       let mult   = CIFilter(name: "CIMultiplyCompositing") {
                        mult.setValue(ci,      forKey: kCIInputBackgroundImageKey)
                        mult.setValue(gradImg, forKey: kCIInputImageKey)
                        if let out = mult.outputImage?.cropped(to: ext) { ci = out }
                    }
                }
            }
        }

        // ── Erasure — deep burn-through holes ──
        if settings.erasure > 0 {
            var rng = SeededRNG(seed: settings.erasureSeed)
            let nHoles = Int(settings.erasure * 6) + 1
            for _ in 0..<nHoles {
                let cx = CGFloat(rng.nextDouble()) * CGFloat(bufferSize)
                let cy = CGFloat(rng.nextDouble()) * CGFloat(bufferSize)
                let r1 = CGFloat(bufferSize) * CGFloat(rng.nextDouble() * 0.15 + 0.04)
                let dark = min(1.0, CGFloat(settings.erasure) * CGFloat(rng.nextDouble() * 0.4 + 0.6))
                if let grad = CIFilter(name: "CIRadialGradient") {
                    grad.setValue(CIVector(x: cx, y: cy), forKey: "inputCenter")
                    grad.setValue(0 as CGFloat, forKey: "inputRadius0")
                    grad.setValue(r1,           forKey: "inputRadius1")
                    grad.setValue(CIColor(red: 1-dark, green: 1-dark, blue: 1-dark), forKey: "inputColor0")
                    grad.setValue(CIColor(red: 1, green: 1, blue: 1),                forKey: "inputColor1")
                    if let gradImg = grad.outputImage?.cropped(to: ext),
                       let mult   = CIFilter(name: "CIMultiplyCompositing") {
                        mult.setValue(ci,      forKey: kCIInputBackgroundImageKey)
                        mult.setValue(gradImg, forKey: kCIInputImageKey)
                        if let out = mult.outputImage?.cropped(to: ext) { ci = out }
                    }
                }
            }
        }

        // ── Highlights — additive glowing bright spots ──
        if settings.highlights > 0 {
            var rng = SeededRNG(seed: settings.highlightsSeed)
            let nSpots = Int(settings.highlights * 8) + 2
            for _ in 0..<nSpots {
                let cx = CGFloat(rng.nextDouble()) * CGFloat(bufferSize)
                let cy = CGFloat(rng.nextDouble()) * CGFloat(bufferSize)
                let r1 = CGFloat(bufferSize) * CGFloat(rng.nextDouble() * 0.12 + 0.02)
                let br = CGFloat(settings.highlights) * CGFloat(rng.nextDouble() * 0.4 + 0.2)
                if let grad  = CIFilter(name: "CIRadialGradient"),
                   let screen = CIFilter(name: "CIScreenBlendMode") {
                    grad.setValue(CIVector(x: cx, y: cy), forKey: "inputCenter")
                    grad.setValue(0 as CGFloat, forKey: "inputRadius0")
                    grad.setValue(r1,           forKey: "inputRadius1")
                    grad.setValue(CIColor(red: br, green: br * 0.9, blue: br * 0.8), forKey: "inputColor0")
                    grad.setValue(CIColor(red: 0, green: 0, blue: 0),                forKey: "inputColor1")
                    if let gradImg = grad.outputImage?.cropped(to: ext) {
                        screen.setValue(ci,      forKey: kCIInputBackgroundImageKey)
                        screen.setValue(gradImg, forKey: kCIInputImageKey)
                        if let out = screen.outputImage?.cropped(to: ext) { ci = out }
                    }
                }
            }
        }

        // ── Chromatic aberration — shift R left, B right ──
        if settings.chromatic > 0 {
            let shift = CGFloat(bufferSize) * CGFloat(settings.chromatic) * 0.012
            if let rMask = CIFilter(name: "CIColorMatrix"),
               let gMask = CIFilter(name: "CIColorMatrix"),
               let bMask = CIFilter(name: "CIColorMatrix") {
                let zero = CIVector(x: 0, y: 0, z: 0, w: 0)
                rMask.setValue(ci, forKey: kCIInputImageKey)
                rMask.setValue(CIVector(x: 1, y: 0, z: 0, w: 0), forKey: "inputRVector")
                rMask.setValue(zero, forKey: "inputGVector"); rMask.setValue(zero, forKey: "inputBVector")
                rMask.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
                gMask.setValue(ci, forKey: kCIInputImageKey)
                gMask.setValue(zero, forKey: "inputRVector")
                gMask.setValue(CIVector(x: 0, y: 1, z: 0, w: 0), forKey: "inputGVector")
                gMask.setValue(zero, forKey: "inputBVector")
                gMask.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
                bMask.setValue(ci, forKey: kCIInputImageKey)
                bMask.setValue(zero, forKey: "inputRVector"); bMask.setValue(zero, forKey: "inputGVector")
                bMask.setValue(CIVector(x: 0, y: 0, z: 1, w: 0), forKey: "inputBVector")
                bMask.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
                if let rImg = rMask.outputImage, let gImg = gMask.outputImage, let bImg = bMask.outputImage,
                   let add1 = CIFilter(name: "CIAdditionCompositing"),
                   let add2 = CIFilter(name: "CIAdditionCompositing") {
                    let rShifted = rImg.transformed(by: CGAffineTransform(translationX: -shift, y: 0)).cropped(to: ext)
                    let bShifted = bImg.transformed(by: CGAffineTransform(translationX:  shift, y: 0)).cropped(to: ext)
                    add1.setValue(rShifted, forKey: kCIInputBackgroundImageKey)
                    add1.setValue(gImg,     forKey: kCIInputImageKey)
                    if let rg = add1.outputImage?.cropped(to: ext) {
                        add2.setValue(rg,       forKey: kCIInputBackgroundImageKey)
                        add2.setValue(bShifted, forKey: kCIInputImageKey)
                        if let rgb = add2.outputImage?.cropped(to: ext) { ci = rgb }
                    }
                }
            }
        }

        // Flush CIFilter pipeline → CGImage
        guard let flushed = ciCtx.createCGImage(ci, from: ext) else { return image }
        var result = flushed

        // ── Stars — tapered diffraction spikes ──
        if settings.stars > 0 {
            let starBuffer = PixelBuffer(width: bufferSize, height: bufferSize)
            var rng = SeededRNG(seed: settings.starsSeed)
            let nStars = Int(settings.stars * 400) + 10
            let wf = Float(bufferSize)
            let nSeg = 10  // segments per arm for brightness taper

            // Draw one tapered arm from (x,y) in direction (dx,dy) for length len
            func drawArm(_ x: Float, _ y: Float, _ dx: Float, _ dy: Float,
                         _ len: Float, _ b: Float, _ dimMul: Float) {
                for seg in 0..<nSeg {
                    let t0  = Float(seg)     / Float(nSeg)
                    let t1  = Float(seg + 1) / Float(nSeg)
                    let fade = pow(1.0 - t0, 2.2) * dimMul  // steep falloff
                    let br  = b * fade
                    let col: (r: Float, g: Float, b: Float) = (br, br * 0.93, br * 0.82)
                    starBuffer.addLine(x0: x + dx * len * t0, y0: y + dy * len * t0,
                                       x1: x + dx * len * t1, y1: y + dy * len * t1,
                                       color: col, weight: br * 0.18)
                }
            }

            for _ in 0..<nStars {
                let x   = rng.nextFloat() * wf
                let y   = rng.nextFloat() * wf
                let b   = Float(settings.stars) * (rng.nextFloat() * 0.5 + 0.5) * 10.0
                let len = rng.nextFloat() * wf * 0.022 + wf * 0.006

                // Hot pinpoint core
                let core: (r: Float, g: Float, b: Float) = (b, b * 0.96, b * 0.9)
                starBuffer.addLine(x0: x, y0: y, x1: x + 0.5, y1: y, color: core, weight: b * 0.6)

                // 4 cardinal arms
                drawArm(x, y,  1,  0, len, b, 1.0)
                drawArm(x, y, -1,  0, len, b, 1.0)
                drawArm(x, y,  0,  1, len, b, 1.0)
                drawArm(x, y,  0, -1, len, b, 1.0)
                // 4 diagonal arms (shorter, dimmer)
                let inv = Float(1.0 / 1.4142)
                drawArm(x, y,  inv,  inv, len * 0.5, b, 0.35)
                drawArm(x, y, -inv,  inv, len * 0.5, b, 0.35)
                drawArm(x, y,  inv, -inv, len * 0.5, b, 0.35)
                drawArm(x, y, -inv, -inv, len * 0.5, b, 0.35)
            }
            if let starCG = starBuffer.toCGImage() {
                let starGlowed = applyGlow(image: starCG, intensity: 0.28)
                result = screenComposite(base: result, overlay: starGlowed)
            }
        }

        return result
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

    // MARK: - Fractal (L-system)

    private static func collectFractalTasks(into tasks: inout [CurveDrawTask],
                                            cx: Float, cy: Float, radius: Double,
                                            params: MandalaParameters, rng: inout SeededRNG,
                                            layerCount: Int, symmetry: Int,
                                            rippleAmount: Float, weightMul: Float) {

        struct LSystem {
            let axiom: String
            let rules: [Character: String]
            let angleDeg: Double
            let maxIter: Int
        }

        // Six L-systems — density slider 0→1 scrolls through them
        let systems: [LSystem] = [
            // Koch snowflake — dense 6-pointed star
            LSystem(axiom: "F--F--F",
                    rules: ["F": "F+F--F+F"],
                    angleDeg: 60, maxIter: 5),
            // Gosper curve — hexagonal space-filling ribbon
            LSystem(axiom: "F",
                    rules: ["F": "F+G++G-F--FF-G+",
                            "G": "-F+GG++G+F--F-G"],
                    angleDeg: 60, maxIter: 4),
            // Dragon curve — folded ribbon spiral
            LSystem(axiom: "FX",
                    rules: ["X": "X+YF+",
                            "Y": "-FX-Y"],
                    angleDeg: 90, maxIter: 14),
            // Sierpinski arrowhead — triangular space-fill
            LSystem(axiom: "F",
                    rules: ["F": "G-F-G",
                            "G": "F+G+F"],
                    angleDeg: 60, maxIter: 8),
            // Hilbert curve — square space-filling
            LSystem(axiom: "A",
                    rules: ["A": "+BF-AFA-FB+",
                            "B": "-AF+BFB+FA-"],
                    angleDeg: 90, maxIter: 6),
            // Quadratic Koch island — spiky square snowflake
            LSystem(axiom: "F+F+F+F",
                    rules: ["F": "F+F-F-FF+F+F-F"],
                    angleDeg: 90, maxIter: 3),
        ]

        let sysIdx  = min(systems.count - 1, Int(params.density * Double(systems.count)))
        let ls      = systems[sysIdx]
        // complexity 0→1 maps to 1→maxIter iterations
        let iters   = max(1, Int(params.complexity * Double(ls.maxIter) + 0.5))
        let turnRad = ls.angleDeg * .pi / 180.0

        // ── Expand L-system string ────────────────────────────────────────
        var str = ls.axiom
        for _ in 0..<iters {
            var buf = ""; buf.reserveCapacity(min(str.count * 8, 1_000_000))
            for ch in str { buf += ls.rules[ch] ?? String(ch) }
            str = buf
            if str.count > 800_000 { break }
        }

        // ── Turtle-graphics walk → polyline paths ─────────────────────────
        var tx = 0.0, ty = 0.0, tdir = 0.0
        var stk: [(x: Double, y: Double, dir: Double)] = []
        var curXs: [Float] = [0], curYs: [Float] = [0]
        var paths: [([Float], [Float])] = []
        var allX: [Double] = [0], allY: [Double] = [0]

        for ch in str {
            switch ch {
            case "F", "G":
                tx += cos(tdir); ty += sin(tdir)
                curXs.append(Float(tx)); curYs.append(Float(ty))
                allX.append(tx); allY.append(ty)
            case "+": tdir += turnRad
            case "-": tdir -= turnRad
            case "[":
                if curXs.count >= 2 { paths.append((curXs, curYs)) }
                stk.append((tx, ty, tdir))
                curXs = [Float(tx)]; curYs = [Float(ty)]
            case "]":
                if curXs.count >= 2 { paths.append((curXs, curYs)) }
                if let s = stk.popLast() { tx = s.x; ty = s.y; tdir = s.dir }
                curXs = [Float(tx)]; curYs = [Float(ty)]
            default: break  // structural symbols (X, Y, A, B) — no draw or turn
            }
        }
        if curXs.count >= 2 { paths.append((curXs, curYs)) }

        guard !paths.isEmpty,
              let mnX = allX.min(), let mxX = allX.max(),
              let mnY = allY.min(), let mxY = allY.max() else { return }

        // ── Scale to fit within radius ────────────────────────────────────
        let range   = max(mxX - mnX, mxY - mnY, 1e-6)
        let fscale  = Float(radius * 1.8 / range)
        let offX    = Float(-(mnX + mxX) * 0.5) * fscale
        let offY    = Float(-(mnY + mxY) * 0.5) * fscale
        let nPaths  = max(1, paths.count)
        let weight  = max(0.2, Float(0.8 - params.density * 0.3)) * weightMul

        // ── Build tasks with symmetry copies ─────────────────────────────
        for (pi, (xs, ys)) in paths.enumerated() {
            let tOffset = params.colorDrift * Double(pi) / Double(nPaths)

            // Centre-relative scaled coordinates
            var sxs = xs.map { $0 * fscale + offX }
            var sysArr = ys.map { $0 * fscale + offY }

            if rippleAmount > 0 {
                applyRippleToPoints(xs: &sxs, ys: &sysArr, amount: rippleAmount, seed: rng.next())
            }

            for s in 0..<symmetry {
                let ang  = Float(s) * .pi * 2.0 / Float(symmetry)
                let cosA = cos(ang), sinA = sin(ang)
                var rxs = [Float](); rxs.reserveCapacity(sxs.count)
                var rys = [Float](); rys.reserveCapacity(sxs.count)
                for i in 0..<sxs.count {
                    rxs.append(cosA * sxs[i] - sinA * sysArr[i] + cx)
                    rys.append(sinA * sxs[i] + cosA * sysArr[i] + cy)
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                           tOffset: tOffset, drift: params.colorDrift,
                                           weight: weight, thickness: 1))
            }
        }
    }

    // MARK: - Phyllotaxis (golden angle spiral of petals)

    private static func collectPhyllotaxisTasks(into tasks: inout [CurveDrawTask],
                                                cx: Float, cy: Float, radius: Double,
                                                params: MandalaParameters, rng: inout SeededRNG,
                                                layerCount: Int, symmetry: Int,
                                                rippleAmount: Float, weightMul: Float) {
        let goldenAngle = Float(2.39996323)
        let N = Int(80 + params.density * 220)
        let R = Float(radius)
        let petalR = R * Float(0.025 + params.complexity * 0.045)
        let tOffset = rng.nextDouble()
        let weight = Float(rng.nextDouble(in: 0.4...1.1)) * weightMul
        let ripSeed = params.seed &+ 0xABCDEF01

        for i in 0..<N {
            let fi = Float(i)
            let r = sqrt(fi / Float(N)) * R * 0.95
            let theta = fi * goldenAngle
            let px = cos(theta) * r
            let py = sin(theta) * r

            let petalSteps = 14
            var xs = [Float]()
            var ys = [Float]()
            xs.reserveCapacity(petalSteps + 1)
            ys.reserveCapacity(petalSteps + 1)
            let rotA = theta + Float.pi * 0.5
            let cosRot = cos(rotA), sinRot = sin(rotA)
            let pr = petalR * (0.4 + 0.6 * sqrt(r / max(R, 1)))
            for k in 0...petalSteps {
                let a = Float(k) / Float(petalSteps) * Float.pi * 2
                let lx = cos(a) * pr
                let ly = sin(a) * pr * 1.5
                xs.append(lx * cosRot - ly * sinRot + px)
                ys.append(lx * sinRot + ly * cosRot + py)
            }

            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2 / Double(symmetry)
                let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: xs.count)
                var rys = [Float](repeating: 0, count: ys.count)
                for j in 0..<xs.count {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                if rippleAmount > 0 {
                    applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                        seed: ripSeed &+ UInt64(i * 13) &+ UInt64(sym))
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                           tOffset: tOffset + Double(fi) / Double(N) * 0.8,
                                           drift: params.colorDrift,
                                           weight: weight * (0.3 + 0.7 * sqrt(r / max(R, 1))),
                                           thickness: 1))
            }
        }
    }

    // MARK: - Hypocycloid (inner rolling circles — star/astroid shapes)

    private static func collectHypocycloidTasks(into tasks: inout [CurveDrawTask],
                                                cx: Float, cy: Float, radius: Double,
                                                params: MandalaParameters, rng: inout SeededRNG,
                                                layerCount: Int, symmetry: Int,
                                                rippleAmount: Float, weightMul: Float) {
        let configs: [(Double, Double)] = [
            (1.0/3.0, 1.0/3.0), (1.0/4.0, 1.0/4.0), (2.0/5.0, 2.0/5.0),
            (3.0/7.0, 3.0/7.0), (1.0/3.0, 2.0/3.0), (2.0/7.0, 0.5),
            (1.0/5.0, 4.0/5.0), (3.0/8.0, 3.0/8.0),
        ]
        for i in 0..<layerCount {
            let cfg = configs[i % configs.count]
            let R = radius * (0.5 + rng.nextDouble() * 0.5)
            let r = R * cfg.0
            let d = R * cfg.1 * (0.7 + rng.nextDouble() * 0.4)
            let (xs, ys) = hypoPoints(R: R, r: r, d: d, steps: 3000)
            let tOffset = rng.nextDouble()
            let weight = Float(rng.nextDouble(in: 0.5...1.3)) * Float(params.complexity) * weightMul
            let ripSeed = params.seed &+ UInt64(i * 41 + 7)
            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2 / Double(symmetry)
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
                                           tOffset: tOffset + Double(sym) * 0.09,
                                           drift: params.colorDrift, weight: weight, thickness: 1))
            }
        }
    }

    private static func hypoPoints(R: Double, r: Double, d: Double, steps: Int) -> ([Float], [Float]) {
        var xs = [Float](); var ys = [Float]()
        xs.reserveCapacity(steps + 1); ys.reserveCapacity(steps + 1)
        let diff = R - r
        let ratio = r == 0 ? 0.0 : diff / r
        let period = lcmPeriod(R: R, r: r)
        let tMax = period * 2.0 * Double.pi
        let dt = tMax / Double(steps)
        for i in 0...steps {
            let t = Double(i) * dt
            xs.append(Float(diff * cos(t) + d * cos(ratio * t)))
            ys.append(Float(diff * sin(t) - d * sin(ratio * t)))
        }
        return (xs, ys)
    }

    // MARK: - Wave Interference (overlapping concentric ring systems)

    private static func collectWaveInterferenceTasks(into tasks: inout [CurveDrawTask],
                                                     cx: Float, cy: Float, radius: Double,
                                                     params: MandalaParameters, rng: inout SeededRNG,
                                                     layerCount: Int, symmetry: Int,
                                                     rippleAmount: Float, weightMul: Float) {
        let nSources = 2 + Int(params.density * 3)
        let nRings   = Int(8 + params.complexity * 24)
        let R = Float(radius)

        for s in 0..<nSources {
            let srcAngle = Float(s) / Float(nSources) * Float.pi * 2 + Float(rng.nextDouble()) * 0.5
            let srcR     = R * Float(0.05 + rng.nextDouble() * 0.38)
            let srcX     = cos(srcAngle) * srcR
            let srcY     = sin(srcAngle) * srcR
            let tOffset  = rng.nextDouble()
            let weight   = Float(rng.nextDouble(in: 0.3...0.8)) * weightMul
            let ripSeed  = params.seed &+ UInt64(s * 77 + 13)

            for ring in 0..<nRings {
                let ringR = R * Float(ring + 1) / Float(nRings)
                let steps = max(80, Int(ringR * 2))
                var xs = [Float](); var ys = [Float]()
                xs.reserveCapacity(steps + 1); ys.reserveCapacity(steps + 1)
                for k in 0...steps {
                    let a = Float(k) / Float(steps) * Float.pi * 2
                    xs.append(srcX + cos(a) * ringR)
                    ys.append(srcY + sin(a) * ringR)
                }
                for sym in 0..<symmetry {
                    let angle = Double(sym) * .pi * 2 / Double(symmetry)
                    let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                    var rxs = [Float](repeating: 0, count: xs.count)
                    var rys = [Float](repeating: 0, count: ys.count)
                    for j in 0..<xs.count {
                        rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                        rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                    }
                    if rippleAmount > 0 {
                        applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                            seed: ripSeed &+ UInt64(ring * 7) &+ UInt64(sym))
                    }
                    tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                               tOffset: tOffset + Double(ring) / Double(nRings) * params.colorDrift,
                                               drift: params.colorDrift * 0.3,
                                               weight: weight * (1.0 - Float(ring) / Float(nRings) * 0.5),
                                               thickness: 1))
                }
            }
        }
    }

    // MARK: - Spider Web (radial spokes + concentric polygon rings)

    private static func collectSpiderWebTasks(into tasks: inout [CurveDrawTask],
                                              cx: Float, cy: Float, radius: Double,
                                              params: MandalaParameters, rng: inout SeededRNG,
                                              layerCount: Int, symmetry: Int,
                                              rippleAmount: Float, weightMul: Float) {
        let R       = Float(radius)
        let nSpokes = max(6, symmetry * (2 + Int(params.density * 4)))
        let nRings  = Int(4 + params.complexity * 14)
        let tOffset = rng.nextDouble()
        let weight  = Float(rng.nextDouble(in: 0.4...1.0)) * weightMul
        let ripSeed = params.seed &+ 0xABCD1234

        // Radial spokes
        for spoke in 0..<nSpokes {
            let a    = Float(spoke) / Float(nSpokes) * Float.pi * 2
            let xs: [Float] = [0, cos(a) * R]
            let ys: [Float] = [0, sin(a) * R]
            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2 / Double(symmetry)
                let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: 2)
                var rys = [Float](repeating: 0, count: 2)
                for j in 0..<2 {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                           tOffset: tOffset + Double(spoke) / Double(nSpokes),
                                           drift: params.colorDrift, weight: weight * 0.6, thickness: 1))
            }
        }

        // Concentric polygon rings
        for ring in 0..<nRings {
            let ringR = R * Float(ring + 1) / Float(nRings + 1)
            var xs = [Float](); var ys = [Float]()
            xs.reserveCapacity(nSpokes + 1); ys.reserveCapacity(nSpokes + 1)
            for spoke in 0...nSpokes {
                let a = Float(spoke % nSpokes) / Float(nSpokes) * Float.pi * 2
                xs.append(cos(a) * ringR)
                ys.append(sin(a) * ringR)
            }
            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2 / Double(symmetry)
                let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: xs.count)
                var rys = [Float](repeating: 0, count: ys.count)
                for j in 0..<xs.count {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                if rippleAmount > 0 {
                    applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                        seed: ripSeed &+ UInt64(ring * 11) &+ UInt64(sym))
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                           tOffset: tOffset + Double(ring) / Double(nRings),
                                           drift: params.colorDrift, weight: weight, thickness: 1))
            }
        }
    }

    // MARK: - Weave (interlocking sinusoidal circular strands)

    private static func collectWeaveTasks(into tasks: inout [CurveDrawTask],
                                          cx: Float, cy: Float, radius: Double,
                                          params: MandalaParameters, rng: inout SeededRNG,
                                          layerCount: Int, symmetry: Int,
                                          rippleAmount: Float, weightMul: Float) {
        let R        = Float(radius)
        let nStrands = 3 + Int(params.density * 8)
        let freq     = Float(nStrands)
        let steps    = 400
        let tOffset  = rng.nextDouble()
        let weight   = Float(rng.nextDouble(in: 0.5...1.2)) * Float(params.complexity) * weightMul
        let ripSeed  = params.seed &+ 0xDEAD1234

        for i in 0..<nStrands {
            let baseR       = R * Float(0.2 + 0.7 * Double(i + 1) / Double(nStrands + 1))
            let amplitude   = baseR * 0.12
            let phaseOffset = Float(i) / Float(nStrands) * Float.pi * 2

            for direction in 0..<2 {
                let dir: Float = direction == 0 ? 1 : -1
                var xs = [Float](); var ys = [Float]()
                xs.reserveCapacity(steps + 1); ys.reserveCapacity(steps + 1)
                for k in 0...steps {
                    let a = Float(k) / Float(steps) * Float.pi * 2 * dir
                    let r = baseR + amplitude * sin(freq * a + phaseOffset + dir * Float.pi * 0.25)
                    xs.append(cos(a) * r)
                    ys.append(sin(a) * r)
                }
                for sym in 0..<symmetry {
                    let angle = Double(sym) * .pi * 2 / Double(symmetry)
                    let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                    var rxs = [Float](repeating: 0, count: xs.count)
                    var rys = [Float](repeating: 0, count: ys.count)
                    for j in 0..<xs.count {
                        rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                        rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                    }
                    if rippleAmount > 0 {
                        applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                            seed: ripSeed &+ UInt64(i * 23 + direction * 100 + sym))
                    }
                    tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                               tOffset: tOffset + Double(i) / Double(nStrands) * 0.5 + Double(direction) * 0.3,
                                               drift: params.colorDrift, weight: weight, thickness: 1))
                }
            }
        }
    }

    // MARK: - Sacred Geometry (Flower of Life + nested polygons + Metatron lines)

    private static func collectSacredGeometryTasks(into tasks: inout [CurveDrawTask],
                                                   cx: Float, cy: Float, radius: Double,
                                                   params: MandalaParameters, rng: inout SeededRNG,
                                                   layerCount: Int, symmetry: Int,
                                                   rippleAmount: Float, weightMul: Float) {
        let R        = Float(radius)
        let circleR  = R * Float(0.15 + params.density * 0.20)
        let tOffset  = rng.nextDouble()
        let weight   = Float(rng.nextDouble(in: 0.5...1.0)) * weightMul
        let circStep = 48

        // Central circle
        do {
            var xs = [Float](); var ys = [Float]()
            for k in 0...circStep {
                let a = Float(k) / Float(circStep) * Float.pi * 2
                xs.append(cos(a) * circleR); ys.append(sin(a) * circleR)
            }
            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2 / Double(symmetry)
                let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: xs.count)
                var rys = [Float](repeating: 0, count: ys.count)
                for j in 0..<xs.count {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys, tOffset: tOffset,
                                           drift: params.colorDrift, weight: weight, thickness: 1))
            }
        }

        // 6 petal circles
        var centers: [(Float, Float)] = [(0, 0)]
        for i in 0..<6 {
            let a = Float(i) / 6 * Float.pi * 2
            let px = cos(a) * circleR, py = sin(a) * circleR
            centers.append((px, py))
            var xs = [Float](); var ys = [Float]()
            for k in 0...circStep {
                let ca = Float(k) / Float(circStep) * Float.pi * 2
                xs.append(px + cos(ca) * circleR); ys.append(py + sin(ca) * circleR)
            }
            let petalTOffset = tOffset + Double(i) / 6.0 * 0.4
            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2 / Double(symmetry)
                let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: xs.count)
                var rys = [Float](repeating: 0, count: ys.count)
                for j in 0..<xs.count {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys, tOffset: petalTOffset,
                                           drift: params.colorDrift, weight: weight, thickness: 1))
            }
        }

        // Second ring of 6 circles
        if params.density > 0.35 {
            for i in 0..<6 {
                let a = Float(i) / 6 * Float.pi * 2 + Float.pi / 6
                let px = cos(a) * circleR * 2, py = sin(a) * circleR * 2
                centers.append((px, py))
                var xs = [Float](); var ys = [Float]()
                for k in 0...circStep {
                    let ca = Float(k) / Float(circStep) * Float.pi * 2
                    xs.append(px + cos(ca) * circleR); ys.append(py + sin(ca) * circleR)
                }
                let ring2TOffset = tOffset + 0.5 + Double(i) / 6.0 * 0.3
                let ring2Weight = weight * 0.8
                for sym in 0..<symmetry {
                    let angle = Double(sym) * .pi * 2 / Double(symmetry)
                    let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                    var rxs = [Float](repeating: 0, count: xs.count)
                    var rys = [Float](repeating: 0, count: ys.count)
                    for j in 0..<xs.count {
                        rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                        rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                    }
                    tasks.append(CurveDrawTask(xs: rxs, ys: rys, tOffset: ring2TOffset,
                                               drift: params.colorDrift, weight: ring2Weight, thickness: 1))
                }
            }
        }

        // Nested polygons at increasing radii
        let maxSides = 3 + Int(params.complexity * 6)
        for sides in 3...maxSides {
            let polyR = R * Float(0.08 + Double(sides - 3) / Double(maxSides - 2) * 0.88)
            var xs = [Float](); var ys = [Float]()
            for k in 0...sides {
                let a = Float(k % sides) / Float(sides) * Float.pi * 2
                xs.append(cos(a) * polyR); ys.append(sin(a) * polyR)
            }
            let polyTOffset = tOffset + Double(sides) * 0.1
            let polyWeight = weight * 0.7
            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2 / Double(symmetry)
                let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: xs.count)
                var rys = [Float](repeating: 0, count: ys.count)
                for j in 0..<xs.count {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys, tOffset: polyTOffset,
                                           drift: params.colorDrift, weight: polyWeight, thickness: 1))
            }
        }

        // Metatron lines: connect all FOL centers
        if params.complexity > 0.45 {
            for ai in 0..<centers.count {
                for bi in (ai + 1)..<centers.count {
                    let lxs: [Float] = [centers[ai].0, centers[bi].0]
                    let lys: [Float] = [centers[ai].1, centers[bi].1]
                    let metaTOffset = tOffset + Double(ai * 7 + bi) * 0.02
                    let metaWeight = weight * 0.45
                    for sym in 0..<symmetry {
                        let angle = Double(sym) * .pi * 2 / Double(symmetry)
                        let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                        var rxs = [Float](repeating: 0, count: lxs.count)
                        var rys = [Float](repeating: 0, count: lys.count)
                        for j in 0..<lxs.count {
                            rxs[j] = cx + lxs[j] * cosA - lys[j] * sinA
                            rys[j] = cy + lxs[j] * sinA + lys[j] * cosA
                        }
                        tasks.append(CurveDrawTask(xs: rxs, ys: rys, tOffset: metaTOffset,
                                                   drift: params.colorDrift, weight: metaWeight, thickness: 1))
                    }
                }
            }
        }
    }

    // MARK: - Radial Mesh (polar grid, optionally distorted)

    private static func collectRadialMeshTasks(into tasks: inout [CurveDrawTask],
                                               cx: Float, cy: Float, radius: Double,
                                               params: MandalaParameters, rng: inout SeededRNG,
                                               layerCount: Int, symmetry: Int,
                                               rippleAmount: Float, weightMul: Float) {
        let R        = Float(radius)
        let nRings   = Int(3 + params.complexity * 15)
        let nSpokes  = max(8, symmetry * (2 + Int(params.density * 4)))
        let tOffset  = rng.nextDouble()
        let weight   = Float(rng.nextDouble(in: 0.3...0.9)) * weightMul
        let ripSeed  = params.seed &+ 0xB00B5678
        let distort  = Float(params.ripple * 0.5)

        // Concentric circles (with optional sine distortion)
        for ring in 0..<nRings {
            let baseR = R * Float(ring + 1) / Float(nRings + 1)
            let steps = max(60, nSpokes * 3)
            var xs = [Float](); var ys = [Float]()
            xs.reserveCapacity(steps + 1); ys.reserveCapacity(steps + 1)
            for k in 0...steps {
                let a = Float(k) / Float(steps) * Float.pi * 2
                let dist = distort > 0 ? sin(Float(nSpokes / 2) * a + Float(ring)) * distort * baseR * 0.2 : 0
                let r = baseR + dist
                xs.append(cos(a) * r); ys.append(sin(a) * r)
            }
            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2 / Double(symmetry)
                let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: xs.count)
                var rys = [Float](repeating: 0, count: ys.count)
                for j in 0..<xs.count {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                if rippleAmount > 0 {
                    applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                        seed: ripSeed &+ UInt64(ring * 13) &+ UInt64(sym))
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                           tOffset: tOffset + Double(ring) / Double(nRings),
                                           drift: params.colorDrift, weight: weight, thickness: 1))
            }
        }

        // Radial spokes
        for spoke in 0..<nSpokes {
            let a = Float(spoke) / Float(nSpokes) * Float.pi * 2
            let xs: [Float] = [0, cos(a) * R]; let ys: [Float] = [0, sin(a) * R]
            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2 / Double(symmetry)
                let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: 2)
                var rys = [Float](repeating: 0, count: 2)
                for j in 0..<2 {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                           tOffset: tOffset + Double(spoke) / Double(nSpokes) * params.colorDrift,
                                           drift: params.colorDrift, weight: weight * 0.5, thickness: 1))
            }
        }
    }

    // MARK: - Flow Field (particle traces through sine/cosine vector field)

    private static func collectFlowFieldTasks(into tasks: inout [CurveDrawTask],
                                              cx: Float, cy: Float, radius: Double,
                                              params: MandalaParameters, rng: inout SeededRNG,
                                              layerCount: Int, symmetry: Int,
                                              rippleAmount: Float, weightMul: Float) {
        let R          = Float(radius)
        let nParticles = Int(20 + params.density * 80)
        let traceSteps = Int(100 + params.complexity * 300)
        let stepSize   = R / Float(traceSteps) * 3.5
        let scale1     = Float(2.0 + rng.nextDouble() * 2.0) / R
        let scale2     = scale1 * Float(1.5 + rng.nextDouble())
        let phase1     = Float(rng.nextDouble()) * Float.pi * 2
        let phase2     = Float(rng.nextDouble()) * Float.pi * 2
        let tOffset    = rng.nextDouble()
        let weight     = Float(rng.nextDouble(in: 0.4...1.1)) * Float(params.complexity) * weightMul
        let ripSeed    = params.seed &+ 0xF10F1234

        for p in 0..<nParticles {
            let startAngle = Float(rng.nextDouble()) * Float.pi * 2
            let startR     = R * Float(rng.nextDouble() * 0.8 + 0.1)
            var x = cos(startAngle) * startR
            var y = sin(startAngle) * startR
            var xs = [x], ys = [y]

            for _ in 0..<traceSteps {
                let fieldAngle = sin(x * scale1 + phase1) * cos(y * scale1) * Float.pi
                              + sin(x * scale2 + phase2) * Float.pi * 0.5
                x += cos(fieldAngle) * stepSize
                y += sin(fieldAngle) * stepSize
                if x * x + y * y > R * R { break }
                xs.append(x); ys.append(y)
            }
            guard xs.count >= 2 else { continue }

            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2 / Double(symmetry)
                let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: xs.count)
                var rys = [Float](repeating: 0, count: ys.count)
                for j in 0..<xs.count {
                    rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                    rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                }
                if rippleAmount > 0 {
                    applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                        seed: ripSeed &+ UInt64(p * 17) &+ UInt64(sym))
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                           tOffset: tOffset + Double(p) / Double(nParticles),
                                           drift: params.colorDrift, weight: weight, thickness: 1))
            }
        }
    }

    // MARK: - Tendril (recursive branching curves)

    private static func collectTendrilTasks(into tasks: inout [CurveDrawTask],
                                            cx: Float, cy: Float, radius: Double,
                                            params: MandalaParameters, rng: inout SeededRNG,
                                            layerCount: Int, symmetry: Int,
                                            rippleAmount: Float, weightMul: Float) {
        struct Branch { var x, y, angle, length: Float; var depth: Int }
        let R        = Float(radius)
        let maxDepth = 2 + Int(params.complexity * 3)
        let nTrunks  = 1 + Int(params.density * 5)
        let tOffset  = rng.nextDouble()
        let weight   = Float(rng.nextDouble(in: 0.5...1.2)) * weightMul
        let ripSeed  = params.seed &+ 0xBEEF4321
        var stack    = [Branch]()

        for trunk in 0..<nTrunks {
            let trunkAngle = Float(trunk) / Float(nTrunks) * Float.pi * 2 + Float(rng.nextDouble()) * 0.5
            stack.append(Branch(x: 0, y: 0, angle: trunkAngle,
                                length: R * Float(0.4 + rng.nextDouble() * 0.5), depth: 0))
        }

        while let branch = stack.popLast() {
            guard branch.depth <= maxDepth else { continue }
            let curvature = Float(rng.nextDouble() - 0.5) * 0.8
            let segSteps  = 20
            var xs = [branch.x]; var ys = [branch.y]
            var curAngle = branch.angle
            var curX = branch.x; var curY = branch.y
            let seg = branch.length / Float(segSteps)
            for _ in 0..<segSteps {
                curAngle += curvature * 0.06
                curX += cos(curAngle) * seg; curY += sin(curAngle) * seg
                xs.append(curX); ys.append(curY)
            }

            let branchTOffset = tOffset + Double(branch.depth) * 0.2
            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2 / Double(symmetry)
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
                let w = weight * pow(0.65, Float(branch.depth))
                tasks.append(CurveDrawTask(xs: rxs, ys: rys, tOffset: branchTOffset,
                                           drift: params.colorDrift, weight: w, thickness: 1))
            }

            if branch.depth < maxDepth {
                let spread = Float.pi * Float(0.3 + rng.nextDouble() * 0.4)
                let childL = branch.length * Float(0.5 + rng.nextDouble() * 0.2)
                for b in 0..<2 {
                    let childAngle = curAngle + (b == 0 ? spread : -spread) * Float(0.5 + rng.nextDouble() * 0.5)
                    stack.append(Branch(x: curX, y: curY, angle: childAngle, length: childL, depth: branch.depth + 1))
                }
            }
        }
    }

    // MARK: - Moiré (two offset concentric ring systems)

    private static func collectMoireTasks(into tasks: inout [CurveDrawTask],
                                          cx: Float, cy: Float, radius: Double,
                                          params: MandalaParameters, rng: inout SeededRNG,
                                          layerCount: Int, symmetry: Int,
                                          rippleAmount: Float, weightMul: Float) {
        let R       = Float(radius)
        let nRings  = Int(10 + params.complexity * 30)
        let offset  = R * Float(0.05 + params.density * 0.25)
        let tOffset = rng.nextDouble()
        let weight  = Float(rng.nextDouble(in: 0.3...0.8)) * weightMul * 0.6
        let ripSeed = params.seed &+ 0xC0C0C0C0
        let centers: [(Float, Float)] = [(offset, 0), (-offset, 0)]

        for (cIdx, (ocx, ocy)) in centers.enumerated() {
            for ring in 0..<nRings {
                let ringR = R * Float(ring) / Float(nRings - 1)
                guard ringR > 1 else { continue }
                let steps = max(60, Int(ringR * 1.5))
                var xs = [Float](); var ys = [Float]()
                xs.reserveCapacity(steps + 1); ys.reserveCapacity(steps + 1)
                for k in 0...steps {
                    let a = Float(k) / Float(steps) * Float.pi * 2
                    xs.append(ocx + cos(a) * ringR); ys.append(ocy + sin(a) * ringR)
                }
                for sym in 0..<symmetry {
                    let angle = Double(sym) * .pi * 2 / Double(symmetry)
                    let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                    var rxs = [Float](repeating: 0, count: xs.count)
                    var rys = [Float](repeating: 0, count: ys.count)
                    for j in 0..<xs.count {
                        rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                        rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                    }
                    if rippleAmount > 0 {
                        applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                            seed: ripSeed &+ UInt64(ring * 19 + cIdx * 1000 + sym))
                    }
                    tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                               tOffset: tOffset + Double(cIdx) * 0.5 + Double(ring) / Double(nRings) * params.colorDrift * 0.5,
                                               drift: params.colorDrift * 0.2, weight: weight, thickness: 1))
                }
            }
        }
    }

    // MARK: - Voronoi (perpendicular bisectors between symmetric seed points)

    private static func collectVoronoiTasks(into tasks: inout [CurveDrawTask],
                                            cx: Float, cy: Float, radius: Double,
                                            params: MandalaParameters, rng: inout SeededRNG,
                                            layerCount: Int, symmetry: Int,
                                            rippleAmount: Float, weightMul: Float) {
        let R       = Float(radius)
        let nSeeds  = 4 + Int(params.density * 12)
        let tOffset = rng.nextDouble()
        let weight  = Float(rng.nextDouble(in: 0.4...1.0)) * weightMul
        let ripSeed = params.seed &+ 0xF0F12345
        var seedX   = [Float](); var seedY = [Float]()

        for _ in 0..<nSeeds {
            let r = Float(rng.nextDouble()) * R * 0.85
            let a = Float(rng.nextDouble()) * Float.pi * 2
            seedX.append(cos(a) * r); seedY.append(sin(a) * r)
        }

        for ai in 0..<nSeeds {
            for bi in (ai + 1)..<nSeeds {
                let ax = seedX[ai], ay = seedY[ai]
                let bx = seedX[bi], by = seedY[bi]
                let mx = (ax + bx) * 0.5, my = (ay + by) * 0.5
                let dx = bx - ax, dy = by - ay
                let len = sqrt(dx * dx + dy * dy)
                guard len > 2 else { continue }
                let nx = -dy / len, ny = dx / len  // bisector direction
                let ext = R * 1.5
                let p0x = mx - nx * ext, p0y = my - ny * ext
                let p1x = mx + nx * ext, p1y = my + ny * ext
                // Clip line segment to circle of radius R
                let ldx = p1x - p0x, ldy = p1y - p0y
                let la = ldx * ldx + ldy * ldy
                let lb = 2 * (p0x * ldx + p0y * ldy)
                let lc = p0x * p0x + p0y * p0y - R * R
                let disc = lb * lb - 4 * la * lc
                guard disc >= 0 else { continue }
                let sqrtD = sqrt(disc)
                let t0 = max(0, min(1, (-lb - sqrtD) / (2 * la)))
                let t1 = max(0, min(1, (-lb + sqrtD) / (2 * la)))
                guard t1 > t0 else { continue }
                let xs: [Float] = [p0x + t0 * ldx, p0x + t1 * ldx]
                let ys: [Float] = [p0y + t0 * ldy, p0y + t1 * ldy]
                let tOff = tOffset + Double(ai * nSeeds + bi) / Double(nSeeds * nSeeds) * params.colorDrift
                for sym in 0..<symmetry {
                    let angle = Double(sym) * .pi * 2 / Double(symmetry)
                    let cosA = Float(cos(angle)), sinA = Float(sin(angle))
                    var rxs = [Float](repeating: 0, count: 2)
                    var rys = [Float](repeating: 0, count: 2)
                    for j in 0..<2 {
                        rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
                        rys[j] = cy + xs[j] * sinA + ys[j] * cosA
                    }
                    if rippleAmount > 0 {
                        applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                            seed: ripSeed &+ UInt64(ai * 37 + bi * 7) &+ UInt64(sym))
                    }
                    tasks.append(CurveDrawTask(xs: rxs, ys: rys, tOffset: tOff,
                                               drift: params.colorDrift, weight: weight, thickness: 1))
                }
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
        let scale = Double(image.width) / 1600.0

        // Multiple blur passes — radii scaled to buffer size so effect is resolution-independent
        let passes: [(radius: Double, opacity: Double)] = [
            (amount *  4 * scale, 0.25),
            (amount * 12 * scale, 0.20),
            (amount * 28 * scale, 0.15),
            (amount * 55 * scale, 0.10),
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
                blur2.setValue(amount * 45.0 * scale, forKey: kCIInputRadiusKey)
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


    // MARK: - Layer rotation

    private static func rotateImage(_ image: CGImage, angle: Double) -> CGImage {
        let ci  = CIImage(cgImage: image)
        let ctx = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])
        let ext = ci.extent
        let cx  = ext.midX, cy = ext.midY
        let t   = CGAffineTransform(translationX: cx, y: cy)
                    .rotated(by: CGFloat(angle))
                    .translatedBy(x: -cx, y: -cy)
        let rotated = ci.transformed(by: t).cropped(to: ext)
        return ctx.createCGImage(rotated, from: ext) ?? image
    }

    // MARK: - Blend composite

    private static func blendComposite(base: CGImage, overlay: CGImage, mode: String) -> CGImage {
        let ciBase    = CIImage(cgImage: base)
        let ciOverlay = CIImage(cgImage: overlay)
        let ctx = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])
        let ext = ciBase.extent
        guard let filter = CIFilter(name: mode) else { return base }
        filter.setValue(ciBase,    forKey: kCIInputBackgroundImageKey)
        filter.setValue(ciOverlay, forKey: kCIInputImageKey)
        guard let out = filter.outputImage?.cropped(to: ext) else { return base }
        return ctx.createCGImage(out, from: ext) ?? base
    }

    // Keep screenComposite for internal use (effects pipeline)
    private static func screenComposite(base: CGImage, overlay: CGImage) -> CGImage {
        blendComposite(base: base, overlay: overlay, mode: "CIScreenBlendMode")
    }

    // MARK: - Post-processing

    static func applyGlow(image: CGImage, intensity: Double) -> CGImage {
        guard intensity > 0 else { return image }
        let ciImage = CIImage(cgImage: image)
        let context = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])
        let scale = Double(image.width) / 1600.0

        // Multiple blur radii for bloom — scaled to buffer size
        let blurRadii: [Double] = [2.0 * scale, 8.0 * scale, 20.0 * scale]
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
        let scale = Double(image.width) / 1600.0

        // Displacement distortion using turbulence — all amounts scaled to buffer size
        let displacementAmount = abstractLevel * 30.0 * scale
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
        let blurRadius = abstractLevel * 3.0 * scale
        if let blurFilter = CIFilter(name: "CIGaussianBlur") {
            blurFilter.setValue(result, forKey: kCIInputImageKey)
            blurFilter.setValue(blurRadius, forKey: kCIInputRadiusKey)
            if let blurred = blurFilter.outputImage?.cropped(to: extent) {
                result = blurred
            }
        }

        // Heavy paint path — smooth ramp starting from abstractLevel=0.3, full at 1.0
        let heavyFactor = max(0.0, (abstractLevel - 0.3) / 0.7)
        let heavyBlur = heavyFactor * abstractLevel * 8.0 * scale
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

        // Saturation + contrast via CIColorControls — NO additive brightness offset,
        // so black pixels (background areas) stay exactly at zero and don't bleed
        // into the base layer when screen-composited.
        guard let sat = CIFilter(name: "CIColorControls") else { return image }
        sat.setValue(ci, forKey: kCIInputImageKey)
        sat.setValue(saturation * 3.0, forKey: kCIInputSaturationKey)   // 0=grey, 1=normal, 3=vivid
        sat.setValue(0.0, forKey: kCIInputBrightnessKey)                 // no additive lift
        sat.setValue(0.9 + brightness * 0.3, forKey: kCIInputContrastKey)
        guard let satImg = sat.outputImage?.cropped(to: ext) else { return image }

        // Brightness as a pure multiplier: 0→0×, 0.5→1×, 1→2×.
        // Multiplying keeps black at black — it never creates a gray wash.
        let bMul = CGFloat(brightness * 2.0)
        guard let bm = CIFilter(name: "CIColorMatrix") else {
            return ctx.createCGImage(satImg, from: ext) ?? image
        }
        bm.setValue(satImg, forKey: kCIInputImageKey)
        bm.setValue(CIVector(x: bMul, y: 0,    z: 0,    w: 0), forKey: "inputRVector")
        bm.setValue(CIVector(x: 0,    y: bMul, z: 0,    w: 0), forKey: "inputGVector")
        bm.setValue(CIVector(x: 0,    y: 0,    z: bMul, w: 0), forKey: "inputBVector")
        bm.setValue(CIVector(x: 0,    y: 0,    z: 0,    w: 1), forKey: "inputAVector")
        guard let out = bm.outputImage?.cropped(to: ext) else {
            return ctx.createCGImage(satImg, from: ext) ?? image
        }
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
