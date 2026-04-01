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
        let resolvedSize = params.outputSize == 0
            ? max(64, min(8192, params.outputSizeCustom))
            : params.outputSize
        let bufferSize = resolvedSize * 2
        let palettes   = params.resolvedPalettes.isEmpty ? ColorPalettes.all : params.resolvedPalettes

        let cx = Float(bufferSize) * 0.5
        let cy = Float(bufferSize) * 0.5
        let baseRadius = Double(bufferSize) * 0.72
        // Brightness normalisation: mean raw pixel value ∝ 1/outputSize because the same
        // lines are spread over a proportionally larger canvas. Linear scale exactly cancels
        // this so every resolution produces the same tone-mapped output.
        // Using a power > 1 would give unequal results when preview and export sizes differ.
        let brightnessScale = Float(bufferSize) / 1600.0

        // ── BACKGROUND ──
        let bgBuffer = PixelBuffer(width: bufferSize, height: bufferSize)
        if params.baseLayer.isEnabled {
            if params.baseLayer.type == .auto {
                // Auto: palette-derived dark gradient + grass fibers
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
                    drawGrassFibers(buffer: bgBuffer, params: lp, palette: bgPalette, rng: &rng)
                }
            } else {
                // .color type bypasses the PixelBuffer pipeline — applied after all layers
                if params.baseLayer.type != .color {
                    drawBaseLayer(buffer: bgBuffer, settings: params.baseLayer, bufferSize: bufferSize)
                }
            }
        }
        // isEnabled == false (or .color type) → buffer stays black (all zeros)

        // Solid-colour background is created outside the PixelBuffer/tone-map pipeline so
        // the displayed colour exactly matches the user's HSB picker. It is composited AFTER
        // all layers so blend modes (Add, Screen…) between layers don't pollute it.
        let solidBgImage: CGImage? = (params.baseLayer.isEnabled && params.baseLayer.type == .color)
            ? makeSolidCGImage(settings: params.baseLayer, size: bufferSize) : nil

        guard var compositeImage = bgBuffer.toCGImage() else { return NSImage() }

        // ── PER-LAYER RENDER ──
        for (li, layer) in params.layers.enumerated() {
            guard layer.isEnabled else { continue }
            let palette = palettes[max(0, min(palettes.count-1, layer.paletteIndex))]
            var layerRng = SeededRNG(seed: layer.seed == 0 ? params.seed &+ UInt64(li + 1) &* 0x9e3779b97f4a7c15 : layer.seed)

            // ── Dust Clouds: CGContext-based, bypasses PixelBuffer ──────────────
            if layer.style == .dust {
                guard var layerImage = renderDustAsStyleLayer(layer: layer, palette: palette,
                                                              size: bufferSize, rng: &layerRng)
                else { continue }
                layerImage = applyGlow(image: layerImage, intensity: layer.glowIntensity)
                layerImage = applyColourGrade(image: layerImage,
                                              saturation: layer.saturation,
                                              brightness: layer.brightness)
                if abs(layer.rotation) > 0.001 {
                    layerImage = rotateImage(layerImage, angle: layer.rotation * .pi * 2)
                }
                if layer.opacity < 0.999 {
                    layerImage = applyLayerOpacity(image: layerImage, opacity: layer.opacity)
                }
                switch layer.blendMode {
                case .screen:   compositeImage = blendComposite(base: compositeImage, overlay: layerImage, mode: "CIScreenBlendMode")
                case .add:      compositeImage = blendComposite(base: compositeImage, overlay: layerImage, mode: "CIAdditionCompositing")
                case .normal:   compositeImage = blendComposite(base: compositeImage, overlay: layerImage, mode: "CILightenBlendMode")
                case .multiply: compositeImage = blendComposite(base: compositeImage, overlay: layerImage, mode: "CIMultiplyBlendMode")
                }
                continue
            }

            let layerSymmetry = max(1, min(8, layer.symmetry))
            let layerRadius = baseRadius * max(0.1, min(1.0, layer.scale))
            let layerCount  = max(2, Int(layer.complexity * 8) + 1)

            // Build a params copy with this layer's values for renderer internals
            var lp = params
            lp.symmetry      = layerSymmetry
            lp.complexity    = layer.complexity
            lp.density       = layer.density
            lp.glowIntensity = layer.glowIntensity
            lp.colorDrift    = layer.colorDrift
            lp.ripple        = layer.ripple
            lp.wash          = layer.wash
            lp.abstractLevel = layer.abstractLevel
            lp.paletteIndex  = layer.paletteIndex

            // Always render into an expanded buffer large enough to hold the full mandala
            // radius without clipping at the buffer edges.  At rotation=0 the crop produces
            // the same visible content as a normal-size buffer; at any other angle, lines
            // that would have been cut by the smaller buffer's edges come into view.
            let hasRotation = abs(layer.rotation) > 0.001
            let lBufSize: Int = {
                let raw = Int(ceil(layerRadius * 2.0)) + 8
                let even = raw % 2 == 0 ? raw : raw + 1
                return max(bufferSize, even)
            }()
            let lcx = Float(lBufSize) * 0.5
            let lcy = Float(lBufSize) * 0.5

            let buffer = PixelBuffer(width: lBufSize, height: lBufSize)

            drawStructuralLayers(buffer: buffer, cx: lcx, cy: lcy, baseRadius: layerRadius,
                                 params: lp, style: layer.style, colorOffset: layer.colorOffset,
                                 palette: palette, rng: &layerRng,
                                 layerCount: layerCount, symmetry: layerSymmetry,
                                 rippleAmount: Float(layer.ripple), weightMul: 1.0)

            buffer.scale(brightnessScale)
            guard var layerImage = buffer.toCGImage() else { continue }
            layerImage = applyGlow(image: layerImage, intensity: layer.glowIntensity)
            if layer.wash > 0 {
                layerImage = applyWash(image: layerImage, amount: layer.wash)
            }
            if layer.abstractLevel > 0.01 {
                layerImage = applyPaintedEffect(image: layerImage, abstractLevel: layer.abstractLevel)
            }
            layerImage = applyColourGrade(image: layerImage, saturation: layer.saturation, brightness: layer.brightness)
            if hasRotation {
                layerImage = rotateImage(layerImage, angle: layer.rotation * .pi * 2)
            }
            // Crop back to bufferSize × bufferSize from the expanded buffer.
            if lBufSize > bufferSize {
                layerImage = cropCenter(layerImage, to: bufferSize)
            }
            if layer.opacity < 0.999 {
                layerImage = applyLayerOpacity(image: layerImage, opacity: layer.opacity)
            }
            switch layer.blendMode {
            case .screen:   compositeImage = blendComposite(base: compositeImage, overlay: layerImage, mode: "CIScreenBlendMode")
            case .add:      compositeImage = blendComposite(base: compositeImage, overlay: layerImage, mode: "CIAdditionCompositing")
            case .normal:   compositeImage = blendComposite(base: compositeImage, overlay: layerImage, mode: "CILightenBlendMode")
            case .multiply: compositeImage = blendComposite(base: compositeImage, overlay: layerImage, mode: "CIMultiplyBlendMode")
            }
        }

        // ── DRAWING LAYER ──
        let dl = params.drawingLayer
        if dl.isEnabled && !dl.strokes.isEmpty {
            let palette = palettes[max(0, min(palettes.count - 1, dl.paletteIndex))]
            let drawBuffer = PixelBuffer(width: bufferSize, height: bufferSize)
            let sym = max(1, dl.symmetry)
            let lineThickness = max(1, Int(Float(dl.strokeWeight) * Float(bufferSize) * 0.015 + 1.5))
            let drawScale = Float(max(0.1, dl.scale))

            for (si, stroke) in dl.strokes.enumerated() {
                guard stroke.xs.count >= 2, stroke.xs.count == stroke.ys.count else { continue }
                let t: Double
                if dl.strokes.count > 1 {
                    t = (Double(si) / Double(dl.strokes.count - 1) * dl.colorDrift)
                        .truncatingRemainder(dividingBy: 1.0)
                } else { t = 0.0 }
                let nsColor = palette.color(at: t)
                let col = (r: Float(nsColor.redComponent),
                           g: Float(nsColor.greenComponent),
                           b: Float(nsColor.blueComponent))
                let pts: [(Float, Float)] = zip(stroke.xs, stroke.ys).map { (nx, ny) in
                    let sx = (Float(nx) - 0.5) * drawScale + 0.5
                    let sy = (Float(ny) - 0.5) * drawScale + 0.5
                    return (sx * Float(bufferSize), sy * Float(bufferSize))
                }
                for s in 0..<sym {
                    let angle = Float(s) * .pi * 2.0 / Float(sym)
                    let ca = cos(angle), sa = sin(angle)
                    for i in 1..<pts.count {
                        let (x0, y0) = pts[i - 1]
                        let (x1, y1) = pts[i]
                        let dx0 = x0 - cx, dy0 = y0 - cy
                        let rx0 = cx + dx0 * ca - dy0 * sa
                        let ry0 = cy + dx0 * sa + dy0 * ca
                        let dx1 = x1 - cx, dy1 = y1 - cy
                        let rx1 = cx + dx1 * ca - dy1 * sa
                        let ry1 = cy + dx1 * sa + dy1 * ca
                        drawBuffer.addThickLine(x0: rx0, y0: ry0, x1: rx1, y1: ry1,
                                                color: col, weight: 1.5, thickness: lineThickness)
                    }
                }
            }

            if var drawImage = drawBuffer.toCGImage() {
                drawImage = applyGlow(image: drawImage, intensity: dl.glowIntensity)
                drawImage = applyColourGrade(image: drawImage,
                                              saturation: dl.saturation,
                                              brightness: dl.brightness)
                if dl.opacity < 0.999 {
                    drawImage = applyLayerOpacity(image: drawImage, opacity: dl.opacity)
                }
                let blendFilter: String
                switch dl.blendMode {
                case .screen:   blendFilter = "CIScreenBlendMode"
                case .add:      blendFilter = "CIAdditionCompositing"
                case .normal:   blendFilter = "CILightenBlendMode"
                case .multiply: blendFilter = "CIMultiplyBlendMode"
                }
                compositeImage = blendComposite(base: compositeImage, overlay: drawImage,
                                                mode: blendFilter)
            }
        }

        // ── SOLID BACKGROUND (applied after all layers so blend modes don't pollute it) ──
        // CILightenBlendMode = max(composite, solidBg) per channel: dark areas become the
        // solid colour while bright mandala content is completely unaffected.
        if let solidBg = solidBgImage {
            compositeImage = blendComposite(base: compositeImage, overlay: solidBg,
                                            mode: "CILightenBlendMode")
        }

        // ── TEXT LAYER (on top of everything, before effects) ──
        if params.textLayer.isEnabled && !params.textLayer.text.isEmpty {
            compositeImage = applyTextLayer(image: compositeImage, settings: params.textLayer, size: bufferSize)
        }

        // ── EFFECTS LAYER ──
        var result = compositeImage
        if params.effectsLayer.isEnabled {
            result = applyEffectsLayer(image: result, settings: params.effectsLayer, bufferSize: bufferSize)
        }

        // ── GLOBAL POST-PROCESS ──
        result = downscaleLanczos(image: result, targetSize: resolvedSize)
        let size = NSSize(width: resolvedSize, height: resolvedSize)
        return NSImage(cgImage: result, size: size)
    }

    // MARK: - Layer preview thumbnail

    static func renderLayerPreview(params: MandalaParameters, layerIndex: Int, size: Int = 128) -> NSImage {
        guard layerIndex < params.layers.count else { return NSImage() }
        let layer = params.layers[layerIndex]
        guard layer.isEnabled else { return NSImage() }
        let bufferSize = size * 2
        let palettes   = params.resolvedPalettes.isEmpty ? ColorPalettes.all : params.resolvedPalettes
        let cx         = Float(bufferSize) * 0.5
        let cy         = Float(bufferSize) * 0.5
        let baseRadius = Double(bufferSize) * 0.72

        let layerSymmetry = max(1, min(8, layer.symmetry))
        let layerRadius   = baseRadius * max(0.1, min(1.0, layer.scale))
        let layerCount    = max(2, Int(layer.complexity * 8) + 1)
        let palette       = palettes[max(0, min(palettes.count - 1, layer.paletteIndex))]

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
        var rng = SeededRNG(seed: layer.seed == 0
            ? params.seed &+ UInt64(layerIndex + 1) &* 0x9e3779b97f4a7c15
            : layer.seed)

        drawStructuralLayers(buffer: buffer, cx: cx, cy: cy, baseRadius: layerRadius,
                             params: lp, style: layer.style, colorOffset: layer.colorOffset,
                             palette: palette, rng: &rng,
                             layerCount: layerCount, symmetry: layerSymmetry,
                             rippleAmount: Float(layer.ripple), weightMul: 1.0)

        guard var img = buffer.toCGImage() else { return NSImage() }
        img = applyGlow(image: img, intensity: layer.glowIntensity)
        img = applyColourGrade(image: img, saturation: layer.saturation, brightness: layer.brightness)
        img = downscaleLanczos(image: img, targetSize: size)
        return NSImage(cgImage: img, size: NSSize(width: size, height: size))
    }

    // MARK: - Structural dispatch — collect tasks, then draw in parallel

    fileprivate static func drawStructuralLayers(buffer: PixelBuffer, cx: Float, cy: Float,
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
        case .torusKnot:
            collectTorusKnotTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                  params: params, rng: &rng, layerCount: layerCount,
                                  symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .sphereGrid:
            collectSphereGridTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                   params: params, rng: &rng, layerCount: layerCount,
                                   symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .tesseract:
            collectTesseractTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                  params: params, rng: &rng, layerCount: layerCount,
                                  symmetry: symmetry, rippleAmount: rippleAmount, weightMul: weightMul)
        case .starBurst:
            drawStarBurstLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                                params: params, palette: palette, rng: &rng)
            return
        case .universe:
            drawUniverseLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                               params: params, palette: palette, rng: &rng,
                               colorOffset: colorOffset, symmetry: symmetry)
            return
        case .symbols:
            drawSymbolsLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                              params: params, palette: palette, rng: &rng,
                              colorOffset: colorOffset, layerCount: layerCount, symmetry: symmetry)
            return
        case .strangeAttractor:
            drawStrangeAttractorLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                                       params: params, palette: palette, rng: &rng,
                                       layerCount: layerCount, symmetry: symmetry,
                                       colorOffset: colorOffset)
            return
        case .nebulaVeins:
            drawNebulaVeinsLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                                  params: params, palette: palette, rng: &rng,
                                  colorOffset: colorOffset, symmetry: symmetry)
            return
        case .constellationWeb:
            drawConstellationWebLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                                       params: params, palette: palette, rng: &rng,
                                       colorOffset: colorOffset, symmetry: symmetry)
            return
        case .auroraRibbons:
            drawAuroraRibbonsLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                                    params: params, palette: palette, rng: &rng,
                                    colorOffset: colorOffset, symmetry: symmetry)
            return
        case .crystalBloom:
            drawCrystalBloomLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                                   params: params, palette: palette, rng: &rng,
                                   colorOffset: colorOffset, symmetry: symmetry)
            return
        case .blackHoleLens:
            drawBlackHoleLensLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                                    params: params, palette: palette, rng: &rng,
                                    colorOffset: colorOffset, symmetry: symmetry)
            return
        case .plasmaPetals:
            drawPlasmaPetalsLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                                   params: params, palette: palette, rng: &rng,
                                   colorOffset: colorOffset, symmetry: symmetry)
            return
        case .recursiveHalo:
            drawRecursiveHaloLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                                    params: params, palette: palette, rng: &rng,
                                    colorOffset: colorOffset, symmetry: symmetry)
            return
        case .superformula:
            collectSuperformulaTasks(into: &tasks, cx: cx, cy: cy, radius: baseRadius,
                                     params: params, rng: &rng, layerCount: layerCount,
                                     symmetry: symmetry, rippleAmount: rippleAmount,
                                     weightMul: weightMul)
        case .hyperboloid:
            drawHyperboloidLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                                  params: params, palette: palette, rng: &rng,
                                  layerCount: layerCount, symmetry: symmetry,
                                  colorOffset: colorOffset)
            return
        case .torus:
            drawTorusLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                            params: params, palette: palette, rng: &rng,
                            layerCount: layerCount, symmetry: symmetry,
                            colorOffset: colorOffset)
            return
        case .nautilus:
            drawNautilusLayers(buffer: buffer, cx: cx, cy: cy, radius: baseRadius,
                               params: params, palette: palette, rng: &rng,
                               layerCount: layerCount, symmetry: symmetry,
                               colorOffset: colorOffset)
            return
        case .dust:
            return  // handled separately in the per-layer loop; nothing to draw here
        case .mixed:
            // Seed-driven random zone selection — different every render
            var zoneRng = SeededRNG(seed: params.seed &+ 0xbeef1234)
            // Pick 3 distinct zone radii and assign a randomly chosen style to each
            let zoneStyles: [MandalaStyle] = [.spirograph, .roseCurves, .epitrochoid, .lissajous,
                                              .butterfly, .floral, .stringArt, .sunburst, .geometric, .fractal,
                                              .phyllotaxis, .hypocycloid, .waveInterference, .spiderWeb,
                                              .weave, .sacredGeometry, .radialMesh, .flowField, .tendril,
                                              .moire, .voronoi, .torusKnot, .sphereGrid, .tesseract, .starBurst,
                                              .universe, .symbols, .strangeAttractor, .nebulaVeins,
                                              .constellationWeb, .auroraRibbons, .crystalBloom,
                                              .blackHoleLens, .plasmaPetals, .recursiveHalo, .superformula,
                                              .hyperboloid, .torus, .nautilus]
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
                case .torusKnot:
                    collectTorusKnotTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                          params: params, rng: &rng, layerCount: sub,
                                          symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .sphereGrid:
                    collectSphereGridTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                           params: params, rng: &rng, layerCount: sub,
                                           symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .tesseract:
                    collectTesseractTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                          params: params, rng: &rng, layerCount: sub,
                                          symmetry: symmetry, rippleAmount: rippleAmount, weightMul: wmul)
                case .starBurst:
                    drawStarBurstLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                       params: params, palette: palette, rng: &rng)
                case .universe:
                    drawUniverseLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                       params: params, palette: palette, rng: &rng,
                                       colorOffset: 0, symmetry: symmetry)
                case .symbols:
                    drawSymbolsLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                      params: params, palette: palette, rng: &rng,
                                      colorOffset: 0, layerCount: sub, symmetry: symmetry)
                case .strangeAttractor:
                    drawStrangeAttractorLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                               params: params, palette: palette, rng: &rng,
                                               layerCount: sub, symmetry: symmetry,
                                               colorOffset: 0)
                case .nebulaVeins:
                    drawNebulaVeinsLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                          params: params, palette: palette, rng: &rng,
                                          colorOffset: 0, symmetry: symmetry)
                case .constellationWeb:
                    drawConstellationWebLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                               params: params, palette: palette, rng: &rng,
                                               colorOffset: 0, symmetry: symmetry)
                case .auroraRibbons:
                    drawAuroraRibbonsLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                            params: params, palette: palette, rng: &rng,
                                            colorOffset: 0, symmetry: symmetry)
                case .crystalBloom:
                    drawCrystalBloomLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                           params: params, palette: palette, rng: &rng,
                                           colorOffset: 0, symmetry: symmetry)
                case .blackHoleLens:
                    drawBlackHoleLensLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                            params: params, palette: palette, rng: &rng,
                                            colorOffset: 0, symmetry: symmetry)
                case .plasmaPetals:
                    drawPlasmaPetalsLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                           params: params, palette: palette, rng: &rng,
                                           colorOffset: 0, symmetry: symmetry)
                case .recursiveHalo:
                    drawRecursiveHaloLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                            params: params, palette: palette, rng: &rng,
                                            colorOffset: 0, symmetry: symmetry)
                case .superformula:
                    collectSuperformulaTasks(into: &tasks, cx: cx, cy: cy, radius: scaled,
                                             params: params, rng: &rng, layerCount: sub,
                                             symmetry: symmetry, rippleAmount: rippleAmount,
                                             weightMul: wmul)
                case .hyperboloid:
                    drawHyperboloidLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                          params: params, palette: palette, rng: &rng,
                                          layerCount: sub, symmetry: symmetry,
                                          colorOffset: 0)
                case .torus:
                    drawTorusLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                    params: params, palette: palette, rng: &rng,
                                    layerCount: sub, symmetry: symmetry,
                                    colorOffset: 0)
                case .nautilus:
                    drawNautilusLayers(buffer: buffer, cx: cx, cy: cy, radius: scaled,
                                       params: params, palette: palette, rng: &rng,
                                       layerCount: sub, symmetry: symmetry,
                                       colorOffset: 0)
                case .mixed, .dust:
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

    /// Creates a solid-colour CGImage with exact RGB values — bypasses the filmic tone-map
    /// so the displayed colour matches the user's HSB picker exactly.
    private static func makeSolidCGImage(settings: BaseLayerSettings, size: Int) -> CGImage? {
        let c = hsbToRGB(h: settings.hue, s: settings.saturation, b: settings.brightness)
        let opacity = Float(settings.opacity)
        let r = UInt8(max(0, min(255, Int(c.r * opacity * 255 + 0.5))))
        let g = UInt8(max(0, min(255, Int(c.g * opacity * 255 + 0.5))))
        let b = UInt8(max(0, min(255, Int(c.b * opacity * 255 + 0.5))))
        let count = size * size * 4
        var bytes = [UInt8](repeating: 255, count: count)
        for i in 0..<size * size {
            let p = i * 4
            bytes[p] = r; bytes[p+1] = g; bytes[p+2] = b
        }
        guard let data     = CFDataCreate(nil, bytes, count),
              let provider = CGDataProvider(data: data) else { return nil }
        let space = CGColorSpace(name: CGColorSpace.displayP3) ?? CGColorSpaceCreateDeviceRGB()
        return CGImage(width: size, height: size,
                       bitsPerComponent: 8, bitsPerPixel: 32,
                       bytesPerRow: size * 4, space: space,
                       bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
                       provider: provider, decode: nil,
                       shouldInterpolate: false, intent: .defaultIntent)
    }

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

        case .sunburst:
            let cx = Float(w) / 2
            let cy = Float(h) / 2
            // 3–17 alternating ray-pairs controlled by patternScale
            let numRays = Float(settings.patternScale * 14.0 + 3.0)
            // Edge transition width: wide (soft) when sharpness=0, narrow (hard) when sharpness=1
            let edgeWidth = max(0.015, 0.48 * (1.0 - Float(settings.patternSharpness) * 0.93))
            let invHalfW = 1.0 / (Float(w) * 0.5)
            for y in 0..<h {
                for x in 0..<w {
                    let dx = Float(x) - cx
                    let dy = Float(y) - cy
                    let angle = atan2f(dy, dx)
                    let dist  = sqrtf(dx * dx + dy * dy) * invHalfW
                    // Sine-wave ray pattern → sharpened linear ramp into [0,1]
                    let raw = (sinf(angle * numRays) + 1.0) * 0.5
                    var t   = (raw - (0.5 - edgeWidth)) / (2.0 * edgeWidth)
                    t = max(0.0, min(1.0, t))
                    // Subtle radial falloff: rays fade toward a neutral midpoint at the edges
                    let radial = max(0.0, 1.0 - dist * dist * 0.45)
                    t = t * radial + 0.5 * (1.0 - radial)
                    let r  = c1.r * (1.0 - t) + c2.r * t
                    let g  = c1.g * (1.0 - t) + c2.g * t
                    let bv = c1.b * (1.0 - t) + c2.b * t
                    let dst = (y * w + x) * 3
                    buffer.data[dst]     = r  * opacity
                    buffer.data[dst + 1] = g  * opacity
                    buffer.data[dst + 2] = bv * opacity
                }
            }

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

        case .auto:
            break   // handled in the render() call-site, not here
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

        // ── Wash — subtle bleach / overexposed look ──
        if settings.wash > 0.001 {
            let t = CGFloat(settings.wash)
            // Gentle saturation reduction (max −40% at full)
            if let sat = CIFilter(name: "CIColorControls") {
                sat.setValue(ci, forKey: kCIInputImageKey)
                sat.setValue(max(0, 1.0 - t * 0.4), forKey: kCIInputSaturationKey)
                if let out = sat.outputImage?.cropped(to: ext) { ci = out }
            }
            // Light push toward white (max +30% at full)
            if let mat = CIFilter(name: "CIColorMatrix") {
                let s = 1.0 - t * 0.3
                let b = t * 0.3
                mat.setValue(ci, forKey: kCIInputImageKey)
                mat.setValue(CIVector(x: s, y: 0, z: 0, w: 0), forKey: "inputRVector")
                mat.setValue(CIVector(x: 0, y: s, z: 0, w: 0), forKey: "inputGVector")
                mat.setValue(CIVector(x: 0, y: 0, z: s, w: 0), forKey: "inputBVector")
                mat.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
                mat.setValue(CIVector(x: b, y: b, z: b, w: 0), forKey: "inputBiasVector")
                if let out = mat.outputImage?.cropped(to: ext) { ci = out }
            }
        }

        // ── Sepia — warm antique tone ──
        if settings.sepia > 0.001,
           let sep = CIFilter(name: "CISepiaTone") {
            sep.setValue(ci, forKey: kCIInputImageKey)
            sep.setValue(CGFloat(settings.sepia), forKey: kCIInputIntensityKey)
            if let out = sep.outputImage?.cropped(to: ext) { ci = out }
        }

        // ── Fade — gentle matte / lifted-shadows look ──
        if settings.fade > 0.001,
           let mat = CIFilter(name: "CIColorMatrix") {
            // Compress tonal range into [lift, 1] without touching global brightness much
            let lift = CGFloat(settings.fade * 0.22)   // max +22% floor at full
            let s    = 1.0 - lift                       // scale so white stays at 1
            mat.setValue(ci, forKey: kCIInputImageKey)
            mat.setValue(CIVector(x: s, y: 0, z: 0, w: 0), forKey: "inputRVector")
            mat.setValue(CIVector(x: 0, y: s, z: 0, w: 0), forKey: "inputGVector")
            mat.setValue(CIVector(x: 0, y: 0, z: s, w: 0), forKey: "inputBVector")
            mat.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
            mat.setValue(CIVector(x: lift, y: lift, z: lift, w: 0), forKey: "inputBiasVector")
            if let out = mat.outputImage?.cropped(to: ext) { ci = out }
        }

        // ── Bloom — multi-scale soft glow (two passes: medium + wide) ──
        if settings.bloom > 0.001 {
            // Radii in reference-1600px pixels (= 1600 * 0.025 and 1600 * 0.090)
            let bF = CGFloat(settings.bloom)
            let bloomPasses: [(radius: CGFloat, strength: CGFloat)] = [
                (40.0,  0.55 * bF),   // medium glow
                (144.0, 0.30 * bF),   // wide atmospheric haze
            ]
            if let bloomOverlay = referenceBlurOverlay(source: ci, passes: bloomPasses),
               let screen = CIFilter(name: "CIScreenBlendMode") {
                screen.setValue(ci,           forKey: kCIInputBackgroundImageKey)
                screen.setValue(bloomOverlay, forKey: kCIInputImageKey)
                if let out = screen.outputImage?.cropped(to: ext) { ci = out }
            }
        }

        // ── Local Contrast — Lightroom-style Clarity (high-pass hard light) ──
        // Extracts mid-frequency structure via large-radius blur, centres the
        // high-pass at 0.5 gray, then applies it as a Hard Light layer over the
        // original. Hard Light < 0.5 darkens, > 0.5 brightens → punchy HDR look.
        if settings.localContrast > 0.001 {
            localContrastBlock: do {
                let amount = CGFloat(settings.localContrast)
                // Radius fixed at 6% of the 1600px reference — resolution-independent
                let blurR: CGFloat = 96.0
                guard let blur = CIFilter(name: "CIGaussianBlur") else { break localContrastBlock }
                blur.setValue(ci, forKey: kCIInputImageKey)
                blur.setValue(blurR, forKey: kCIInputRadiusKey)
                guard let blurred = blur.outputImage?.cropped(to: ext) else { break localContrastBlock }

                // Invert & centre the blurred image: -1*blurred + 0.5
                guard let invertMat = CIFilter(name: "CIColorMatrix") else { break localContrastBlock }
                invertMat.setValue(blurred, forKey: kCIInputImageKey)
                invertMat.setValue(CIVector(x: -1, y:  0, z: 0, w: 0), forKey: "inputRVector")
                invertMat.setValue(CIVector(x:  0, y: -1, z: 0, w: 0), forKey: "inputGVector")
                invertMat.setValue(CIVector(x:  0, y:  0, z:-1, w: 0), forKey: "inputBVector")
                invertMat.setValue(CIVector(x:  0, y:  0, z: 0, w: 1), forKey: "inputAVector")
                invertMat.setValue(CIVector(x: 0.5, y: 0.5, z: 0.5, w: 0), forKey: "inputBiasVector")
                guard let inverted = invertMat.outputImage?.cropped(to: ext) else { break localContrastBlock }

                // highPass = original + (-blurred + 0.5) = (original - blurred) + 0.5
                guard let addHF = CIFilter(name: "CIAdditionCompositing") else { break localContrastBlock }
                addHF.setValue(ci,       forKey: kCIInputBackgroundImageKey)
                addHF.setValue(inverted, forKey: kCIInputImageKey)
                guard let highPass = addHF.outputImage?.cropped(to: ext) else { break localContrastBlock }

                // Blend the high-pass toward neutral (0.5) by (1 - amount):
                // scaled = highPass * amount + 0.5 * (1 - amount)
                guard let scaleMat = CIFilter(name: "CIColorMatrix") else { break localContrastBlock }
                let bias = 0.5 * (1.0 - amount)
                scaleMat.setValue(highPass, forKey: kCIInputImageKey)
                scaleMat.setValue(CIVector(x: amount, y: 0,      z: 0,      w: 0), forKey: "inputRVector")
                scaleMat.setValue(CIVector(x: 0,      y: amount, z: 0,      w: 0), forKey: "inputGVector")
                scaleMat.setValue(CIVector(x: 0,      y: 0,      z: amount, w: 0), forKey: "inputBVector")
                scaleMat.setValue(CIVector(x: 0,      y: 0,      z: 0,      w: 1), forKey: "inputAVector")
                scaleMat.setValue(CIVector(x: bias, y: bias, z: bias, w: 0),       forKey: "inputBiasVector")
                guard let scaledHP = scaleMat.outputImage?.cropped(to: ext) else { break localContrastBlock }

                // Soft Light of scaledHP over original → local contrast boost.
                // Soft Light is gentler than Hard Light: it brightens edges without
                // aggressively darkening the surrounding shadow areas.
                if let softLight = CIFilter(name: "CISoftLightBlendMode") {
                    softLight.setValue(ci,       forKey: kCIInputBackgroundImageKey)
                    softLight.setValue(scaledHP, forKey: kCIInputImageKey)
                    if let out = softLight.outputImage?.cropped(to: ext) { ci = out }
                }
            }
        }

        // ── Grain — film grain / analogue noise ──
        if settings.grain > 0.001,
           let random = CIFilter(name: "CIRandomGenerator"),
           let noiseCtrl = CIFilter(name: "CIColorMatrix") {
            let amount = CGFloat(settings.grain * 0.28)
            let bias   = CGFloat(-settings.grain * 0.14)
            noiseCtrl.setValue(random.outputImage?.cropped(to: ext), forKey: kCIInputImageKey)
            noiseCtrl.setValue(CIVector(x: amount, y: 0, z: 0, w: 0), forKey: "inputRVector")
            noiseCtrl.setValue(CIVector(x: 0, y: amount, z: 0, w: 0), forKey: "inputGVector")
            noiseCtrl.setValue(CIVector(x: 0, y: 0, z: amount, w: 0), forKey: "inputBVector")
            noiseCtrl.setValue(CIVector(x: 0, y: 0, z: 0, w: 0), forKey: "inputAVector")
            noiseCtrl.setValue(CIVector(x: bias, y: bias, z: bias, w: 0), forKey: "inputBiasVector")
            if let grainImg = noiseCtrl.outputImage?.cropped(to: ext),
               let add = CIFilter(name: "CIAdditionCompositing") {
                add.setValue(ci, forKey: kCIInputBackgroundImageKey)
                add.setValue(grainImg, forKey: kCIInputImageKey)
                if let out = add.outputImage?.cropped(to: ext) { ci = out }
            }
        }

        // ── Brightness & Contrast ──
        if settings.brightness != 0.5 || settings.contrast != 0.5 {
            let b = CGFloat((settings.brightness - 0.5) * 0.6)   // −0.3 … +0.3
            let c = CGFloat(0.5 + settings.contrast * 1.5)        //  0.5 … 2.0
            if let cc = CIFilter(name: "CIColorControls") {
                cc.setValue(ci, forKey: kCIInputImageKey)
                cc.setValue(b,  forKey: kCIInputBrightnessKey)
                cc.setValue(c,  forKey: kCIInputContrastKey)
                if let out = cc.outputImage?.cropped(to: ext) { ci = out }
            }
        }

        // ── 3D Relief — directional emboss via Hard Light blend ──
        // Hard Light: emboss < 0.5 → multiply (shadow/darken), emboss > 0.5 → screen (highlight/brighten)
        // Neutral gray (0.5) = identity → no global brightness shift
        if settings.relief > 0 {
            let angle = CGFloat(settings.reliefAngle * .pi * 2)
            let ca = cos(angle), sa = sin(angle)
            let r  = CGFloat(settings.relief * 2.0)   // amplify for visible ridging
            let w: [CGFloat] = [
                (-ca - sa) * r, -sa * r, (ca - sa) * r,
                -ca * r,         0,       ca * r,
                (-ca + sa) * r,  sa * r, (ca + sa) * r
            ]
            let emboss = ci.applyingFilter("CIConvolution3X3", parameters: [
                "inputWeights": CIVector(values: w, count: 9),
                "inputBias":    CGFloat(0.5)
            ]).cropped(to: ext)
            // emboss is the hard-light overlay; ci is the base
            ci = emboss.applyingFilter("CIHardLightBlendMode",
                                       parameters: [kCIInputBackgroundImageKey: ci])
                       .cropped(to: ext)
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

        // ── Glitter — dense tiny iridescent rainbow sparkles ──
        if settings.glitter > 0 {
            let glitterBuffer = PixelBuffer(width: bufferSize, height: bufferSize)
            var rng = SeededRNG(seed: settings.glitterSeed)
            let nGlitter = Int(settings.glitter * 2500) + 80
            let wf = Float(bufferSize)

            for _ in 0..<nGlitter {
                let x = rng.nextFloat() * wf
                let y = rng.nextFloat() * wf
                let hue = rng.nextFloat()
                let bright = Float(settings.glitter) * (rng.nextFloat() * 0.55 + 0.45) * 9.0
                // HSV → RGB inline
                let hi = Int(hue * 6.0) % 6
                let f  = hue * 6.0 - Float(Int(hue * 6.0))
                let q  = 1.0 - f
                let rgb: (Float, Float, Float)
                switch hi {
                case 0: rgb = (1, f, 0)
                case 1: rgb = (q, 1, 0)
                case 2: rgb = (0, 1, f)
                case 3: rgb = (0, q, 1)
                case 4: rgb = (f, 0, 1)
                default: rgb = (1, 0, q)
                }
                let col = (r: rgb.0*bright, g: rgb.1*bright, b: rgb.2*bright)
                let len = (rng.nextFloat() * 0.006 + 0.002) * wf
                let wt  = bright * 0.12
                // Tiny cross
                glitterBuffer.addLine(x0: x-len, y0: y, x1: x+len, y1: y, color: col, weight: wt)
                glitterBuffer.addLine(x0: x, y0: y-len*0.65, x1: x, y1: y+len*0.65, color: col, weight: wt)
                // Diagonal glints at reduced brightness
                let d = len * 0.45
                let dc = (r: col.r*0.5, g: col.g*0.5, b: col.b*0.5)
                glitterBuffer.addLine(x0: x-d, y0: y-d, x1: x+d, y1: y+d, color: dc, weight: wt*0.6)
                glitterBuffer.addLine(x0: x+d, y0: y-d, x1: x-d, y1: y+d, color: dc, weight: wt*0.6)
            }
            if let gCG = glitterBuffer.toCGImage() {
                let gGlowed = applyGlow(image: gCG, intensity: 0.18)
                result = screenComposite(base: result, overlay: gGlowed)
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

    // MARK: - Star Burst Layers

    /// Dense field of short radial dashes starting at random distances from the center.
    /// Complexity → dash length, Density → dash count, ColorDrift → palette spread.
    private static func drawStarBurstLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                             radius: Double, params: MandalaParameters,
                                             palette: ColorPalette, rng: inout SeededRNG) {
        let R         = Float(radius)
        let numDashes = Int(params.density * 5000 + 1200)            // 1200–6200
        let dashLen   = R * Float(0.05 + params.complexity * 0.50)   // 5–55 % of radius
        let jitterAmt = Float(0.04)                                   // slight organic scatter
        let weight    = Float(0.26 + params.density * 0.14)
        let tBase     = rng.nextDouble()

        for i in 0..<numDashes {
            let angle  = rng.nextFloat() * .pi * 2
            let innerR = rng.nextFloat() * R                          // start anywhere in radius
            let outerR = min(innerR + dashLen, R * 1.05)
            let jitter = (rng.nextFloat() - 0.5) * jitterAmt

            let x0 = cx + cosf(angle + jitter) * innerR
            let y0 = cy + sinf(angle + jitter) * innerR
            let x1 = cx + cosf(angle + jitter) * outerR
            let y1 = cy + sinf(angle + jitter) * outerR

            // Color: drifts across palette with radial position
            let t = (tBase + Double(innerR / R) * params.colorDrift
                           + Double(i) / Double(numDashes) * params.colorDrift * 0.08)
                        .truncatingRemainder(dividingBy: 1.0)
            let col = palette.color(at: t)
            let c   = (r: Float(col.redComponent),
                       g: Float(col.greenComponent),
                       b: Float(col.blueComponent))
            buffer.addLine(x0: x0, y0: y0, x1: x1, y1: y1, color: c, weight: weight)
        }
    }

    // MARK: - Cosmic Style Helpers

    private static func paletteColor(_ palette: ColorPalette, at t: Double, colorOffset: Double = 0) -> (r: Float, g: Float, b: Float) {
        let c = palette.color(at: (t + colorOffset).truncatingRemainder(dividingBy: 1.0))
        return (Float(c.redComponent), Float(c.greenComponent), Float(c.blueComponent))
    }

    private static func addPolyline(buffer: PixelBuffer, points: [(Float, Float)],
                                    color: (r: Float, g: Float, b: Float), weight: Float) {
        guard points.count >= 2 else { return }
        for i in 0..<(points.count - 1) {
            buffer.addLine(x0: points[i].0, y0: points[i].1,
                           x1: points[i + 1].0, y1: points[i + 1].1,
                           color: color, weight: weight)
        }
    }

    private static func addCircle(buffer: PixelBuffer, cx: Float, cy: Float, radius: Float,
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

    private static func addEllipse(buffer: PixelBuffer, cx: Float, cy: Float, rx: Float, ry: Float,
                                   rotation: Float = 0,
                                   color: (r: Float, g: Float, b: Float), weight: Float,
                                   steps: Int = 96, start: Float = 0, end: Float = .pi * 2) {
        guard rx > 0.5, ry > 0.5, steps > 1 else { return }
        let dt = (end - start) / Float(steps)
        let ca = cos(rotation), sa = sin(rotation)
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

    private static func drawNebulaVeinsLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                              radius: Double, params: MandalaParameters,
                                              palette: ColorPalette, rng: inout SeededRNG,
                                              colorOffset: Double, symmetry: Int) {
        let R = Float(radius)
        let ribbons = max(3, Int(4 + params.complexity * 8))
        let steps = Int(80 + params.density * 140)
        let sector = Float.pi * 2 / Float(max(1, symmetry))
        let baseWeight = Float(0.22 + params.density * 0.20)

        for ribbon in 0..<ribbons {
            let startA = Float(ribbon) / Float(ribbons) * sector + Float(rng.nextDouble() * 0.35)
            let sway = Float(0.35 + params.abstractLevel * 0.8 + rng.nextDouble() * 0.2)
            let color = paletteColor(palette, at: Double(ribbon) / Double(ribbons) * 0.8, colorOffset: colorOffset)
            for sym in 0..<symmetry {
                let rot = Float(sym) * sector
                var points: [(Float, Float)] = []
                points.reserveCapacity(steps + 1)
                for step in 0...steps {
                    let t = Float(step) / Float(steps)
                    let branch = sin(t * .pi * (2.4 + sway * 2.2) + Float(ribbon) * 0.9) * R * 0.10 * sway
                    let curl = sin(t * .pi * 7.0 + Float(ribbon) * 1.7) * R * 0.02 * Float(params.ripple + 0.15)
                    let rr = R * (0.12 + t * (0.78 + Float(params.complexity) * 0.12)) + branch
                    let ang = startA + rot + t * (0.9 + sway * 1.2) + curl / max(R, 1)
                    points.append((cx + cos(ang) * rr, cy + sin(ang) * rr))
                }
                addPolyline(buffer: buffer, points: points, color: color, weight: baseWeight)
            }
        }

        let haloCount = 3 + Int(params.glowIntensity * 4)
        for i in 0..<haloCount {
            let t = Double(i) / Double(max(1, haloCount))
            let c = paletteColor(palette, at: 0.18 + t * 0.45, colorOffset: colorOffset)
            addEllipse(buffer: buffer, cx: cx, cy: cy,
                       rx: R * Float(0.18 + t * 0.55),
                       ry: R * Float(0.10 + t * 0.24),
                       rotation: Float(t) * .pi + Float(rng.nextDouble() * 0.4),
                       color: (c.r * 0.45, c.g * 0.45, c.b * 0.45),
                       weight: baseWeight * 0.75, steps: 90)
        }
    }

    private static func drawConstellationWebLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                                   radius: Double, params: MandalaParameters,
                                                   palette: ColorPalette, rng: inout SeededRNG,
                                                   colorOffset: Double, symmetry: Int) {
        let R = Float(radius)
        let sector = Float.pi * 2 / Float(max(1, symmetry))
        let baseNodes = max(5, Int(8 + params.density * 16))
        var relNodes: [(Float, Float)] = []
        relNodes.reserveCapacity(baseNodes)

        for _ in 0..<baseNodes {
            let ang = Float(rng.nextDouble()) * sector
            let dist = Float(sqrt(rng.nextDouble())) * R * 0.92
            relNodes.append((cos(ang) * dist, sin(ang) * dist))
        }

        var nodes: [(Float, Float)] = []
        for (rx, ry) in relNodes {
            for sym in 0..<symmetry {
                let a = Float(sym) * sector
                let ca = cos(a), sa = sin(a)
                nodes.append((cx + rx * ca - ry * sa, cy + rx * sa + ry * ca))
            }
        }

        let connectDist = R * Float(0.26 + params.complexity * 0.22)
        for i in 0..<nodes.count {
            for j in (i + 1)..<nodes.count {
                let dx = nodes[i].0 - nodes[j].0
                let dy = nodes[i].1 - nodes[j].1
                let d = sqrt(dx * dx + dy * dy)
                if d < connectDist {
                    let alpha: Float = 1 - d / connectDist
                    let t = Double(i + j) / Double(max(1, nodes.count * 2))
                    let c = paletteColor(palette, at: t, colorOffset: colorOffset)
                    let lineColor: (r: Float, g: Float, b: Float) = (
                        c.r * alpha,
                        c.g * alpha,
                        c.b * alpha
                    )
                    let lineWeight = Float(0.10 + Double(alpha) * (0.22 + params.density * 0.15))
                    let n0 = nodes[i]
                    let n1 = nodes[j]
                    buffer.addLine(x0: n0.0, y0: n0.1, x1: n1.0, y1: n1.1,
                                   color: lineColor, weight: lineWeight)
                }
            }
        }

        for (idx, node) in nodes.enumerated() {
            let c = paletteColor(palette, at: Double(idx) / Double(max(1, nodes.count)), colorOffset: colorOffset)
            addCircle(buffer: buffer, cx: node.0, cy: node.1, radius: R * 0.010,
                      color: c, weight: 1.2, steps: 16)
            let flare = R * Float(0.015 + params.glowIntensity * 0.04)
            buffer.addLine(x0: node.0 - flare, y0: node.1, x1: node.0 + flare, y1: node.1,
                           color: c, weight: 0.6)
            buffer.addLine(x0: node.0, y0: node.1 - flare, x1: node.0, y1: node.1 + flare,
                           color: c, weight: 0.6)
        }
    }

    private static func drawAuroraRibbonsLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                                radius: Double, params: MandalaParameters,
                                                palette: ColorPalette, rng: inout SeededRNG,
                                                colorOffset: Double, symmetry: Int) {
        let R = Float(radius)
        let bands = max(3, Int(4 + params.density * 5))
        let steps = Int(120 + params.complexity * 160)
        let sector = Float.pi * 2 / Float(max(1, symmetry))

        for band in 0..<bands {
            let c = paletteColor(palette, at: Double(band) / Double(max(1, bands)) * 0.9, colorOffset: colorOffset)
            let baseR = R * Float(0.18 + Double(band) / Double(max(1, bands)) * 0.60)
            let amp = R * Float(0.025 + params.abstractLevel * 0.09 + rng.nextDouble() * 0.03)
            let waviness = Float(2.5 + params.complexity * 5.5 + rng.nextDouble() * 1.2)
            for sym in 0..<symmetry {
                let rot = Float(sym) * sector
                for lane in 0..<3 {
                    var points: [(Float, Float)] = []
                    points.reserveCapacity(steps + 1)
                    let laneShift = (Float(lane) - 1) * amp * 0.45
                    for i in 0...steps {
                        let t = Float(i) / Float(steps)
                        let a = rot + t * sector + Float(band) * 0.05
                        let rr = baseR + laneShift + sin(t * .pi * waviness + Float(band) * 0.8) * amp
                        points.append((cx + cos(a) * rr, cy + sin(a) * rr))
                    }
                    addPolyline(buffer: buffer, points: points,
                                color: (c.r * 0.75, c.g * 0.75, c.b * 0.75),
                                weight: Float(0.12 + params.density * 0.12))
                }
            }
        }
    }

    private static func drawCrystalBloomLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                               radius: Double, params: MandalaParameters,
                                               palette: ColorPalette, rng: inout SeededRNG,
                                               colorOffset: Double, symmetry: Int) {
        let R = Float(radius)
        let petals = max(6, symmetry * 2)
        let layers = max(3, Int(3 + params.complexity * 4))
        let tipBase = R * Float(0.40 + params.density * 0.36)

        for layer in 0..<layers {
            let inner = R * Float(0.08 + Double(layer) * 0.09)
            let tip = tipBase * Float(0.52 + Double(layer) * 0.14)
            let width = R * Float(0.04 + params.abstractLevel * 0.09 + Double(layer) * 0.012)
            let c = paletteColor(palette, at: Double(layer) / Double(max(1, layers)) * 0.85, colorOffset: colorOffset)
            for p in 0..<petals {
                let ang = Float(p) * .pi * 2 / Float(petals) + Float(layer) * 0.03
                let p0 = (cx + cos(ang) * inner, cy + sin(ang) * inner)
                let p1 = (cx + cos(ang - 0.11) * (tip - width), cy + sin(ang - 0.11) * (tip - width))
                let p2 = (cx + cos(ang) * tip, cy + sin(ang) * tip)
                let p3 = (cx + cos(ang + 0.11) * (tip - width), cy + sin(ang + 0.11) * (tip - width))
                addPolyline(buffer: buffer, points: [p0, p1, p2, p3, p0], color: c,
                            weight: Float(0.16 + params.density * 0.18))
                buffer.addLine(x0: p0.0, y0: p0.1, x1: p2.0, y1: p2.1, color: c, weight: 0.10)
            }
        }

        addCircle(buffer: buffer, cx: cx, cy: cy, radius: R * 0.14,
                  color: paletteColor(palette, at: 0.1, colorOffset: colorOffset),
                  weight: Float(0.45 + params.glowIntensity * 0.3), steps: 64)
    }

    private static func drawBlackHoleLensLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                                radius: Double, params: MandalaParameters,
                                                palette: ColorPalette, rng: inout SeededRNG,
                                                colorOffset: Double, symmetry: Int) {
        let R = Float(radius)
        let darkRing = R * Float(0.16 + params.density * 0.10)
        let ringCount = max(4, Int(5 + params.complexity * 5))

        for i in 0..<ringCount {
            let t = Double(i) / Double(max(1, ringCount - 1))
            let c = paletteColor(palette, at: 0.08 + t * 0.42, colorOffset: colorOffset)
            let rr = darkRing * Float(1.2 + t * 3.6)
            let squash = Float(0.24 + t * 0.18)
            addEllipse(buffer: buffer, cx: cx, cy: cy, rx: rr, ry: rr * squash,
                       rotation: Float(0.12 + rng.nextDouble() * 0.5),
                       color: (c.r, c.g, c.b), weight: Float(0.18 + (1 - t) * 0.22), steps: 100)
        }

        let arcCount = max(6, symmetry * 2)
        for i in 0..<arcCount {
            let a0 = Float(i) * .pi * 2 / Float(arcCount) + Float(rng.nextDouble() * 0.2)
            let span = Float(0.22 + params.abstractLevel * 0.55)
            let rr = R * Float(0.34 + rng.nextDouble() * 0.42)
            let c = paletteColor(palette, at: 0.45 + Double(i) / Double(max(1, arcCount)) * 0.35, colorOffset: colorOffset)
            addEllipse(buffer: buffer, cx: cx, cy: cy, rx: rr, ry: rr * 0.42,
                       rotation: a0, color: (c.r * 0.7, c.g * 0.7, c.b * 0.7),
                       weight: 0.12, steps: 44, start: -span, end: span)
        }

        let jet = R * Float(0.28 + params.glowIntensity * 0.30)
        let c = paletteColor(palette, at: 0.86, colorOffset: colorOffset)
        for dir in [-Float.pi / 2, Float.pi / 2] {
            buffer.addLine(x0: cx + cos(dir) * darkRing, y0: cy + sin(dir) * darkRing,
                           x1: cx + cos(dir) * (darkRing + jet), y1: cy + sin(dir) * (darkRing + jet),
                           color: c, weight: Float(0.26 + params.glowIntensity * 0.2))
        }
    }

    private static func drawPlasmaPetalsLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                               radius: Double, params: MandalaParameters,
                                               palette: ColorPalette, rng: inout SeededRNG,
                                               colorOffset: Double, symmetry: Int) {
        let R = Float(radius)
        let petals = max(8, symmetry * 2 + Int(params.complexity * 6))
        let steps = Int(90 + params.density * 120)
        let weight = Float(0.16 + params.density * 0.18)

        for petal in 0..<petals {
            let baseA = Float(petal) * .pi * 2 / Float(petals)
            let c = paletteColor(palette, at: Double(petal) / Double(max(1, petals)) * 0.9, colorOffset: colorOffset)
            for side: Float in [-1, 1] {
                var points: [(Float, Float)] = []
                points.reserveCapacity(steps + 1)
                for i in 0...steps {
                    let t = Float(i) / Float(steps)
                    let flare = sin(t * .pi) * R * Float(0.10 + params.abstractLevel * 0.16)
                    let curl = sin(t * .pi * (3.5 + Float(params.complexity) * 3.0) + Float(petal) * 0.7) * R * 0.03
                    let rr = R * (0.08 + t * (0.82 + Float(params.density) * 0.08))
                    let a = baseA + side * (flare + curl) / max(rr, 1)
                    points.append((cx + cos(a) * rr, cy + sin(a) * rr))
                }
                addPolyline(buffer: buffer, points: points, color: c, weight: weight)
            }
        }

        addCircle(buffer: buffer, cx: cx, cy: cy, radius: R * 0.12,
                  color: paletteColor(palette, at: 0.04, colorOffset: colorOffset),
                  weight: Float(0.55 + params.glowIntensity * 0.35), steps: 60)
    }

    private static func drawRecursiveHaloLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                                radius: Double, params: MandalaParameters,
                                                palette: ColorPalette, rng: inout SeededRNG,
                                                colorOffset: Double, symmetry: Int) {
        let R = Float(radius)
        let rings = max(4, Int(5 + params.complexity * 5))
        let burstCount = max(5, symmetry * 2)

        for ring in 0..<rings {
            let t = Double(ring) / Double(max(1, rings - 1))
            let rr = R * Float(0.10 + t * 0.74)
            let c = paletteColor(palette, at: 0.15 + t * 0.65, colorOffset: colorOffset)
            addCircle(buffer: buffer, cx: cx, cy: cy, radius: rr,
                      color: (c.r, c.g, c.b), weight: Float(0.12 + (1 - t) * 0.20), steps: 96)

            for b in 0..<burstCount {
                let a = Float(b) * .pi * 2 / Float(burstCount) + Float(ring) * 0.06
                let inner = rr * Float(0.88 + sin(a * 3 + Float(ring)) * 0.04)
                let outer = rr * Float(1.07 + params.abstractLevel * 0.18)
                buffer.addLine(x0: cx + cos(a) * inner, y0: cy + sin(a) * inner,
                               x1: cx + cos(a) * outer, y1: cy + sin(a) * outer,
                               color: (c.r * 0.9, c.g * 0.9, c.b * 0.9),
                               weight: Float(0.09 + params.density * 0.09))
                if ring < rings - 1 {
                    let nodeR = rr * Float(0.55 + params.complexity * 0.22)
                    let nx = cx + cos(a) * nodeR
                    let ny = cy + sin(a) * nodeR
                    addCircle(buffer: buffer, cx: nx, cy: ny, radius: R * Float(0.010 + t * 0.014),
                              color: (c.r * 0.65, c.g * 0.65, c.b * 0.65), weight: 0.22, steps: 18)
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

    // MARK: - Torus Knot (3D) — full tube surface with symmetry + ripple

    private static func collectTorusKnotTasks(into tasks: inout [CurveDrawTask],
                                              cx: Float, cy: Float, radius: Double,
                                              params: MandalaParameters, rng: inout SeededRNG,
                                              layerCount: Int, symmetry: Int,
                                              rippleAmount: Float, weightMul: Float) {
        let R = Float(radius) * 0.50
        let r = R * 0.32

        let pqPairs: [(Int, Int)] = [(2,3),(2,5),(3,4),(3,5),(3,7),(4,5),(4,7),(5,6),(5,7),(7,9),(5,11),(4,9)]
        let pairIdx = min(Int(params.complexity * Double(pqPairs.count - 1)), pqPairs.count - 1)
        let (p, q) = pqPairs[pairIdx]

        let nSpine  = 1800 + Int(params.complexity * 1200)   // spine sample count
        let nTube   = max(3, 2 + Int(params.density * 10))   // cross-section rings
        let tubeR   = r * Float(0.10 + params.density * 0.25) // tube render radius

        let tiltX = Float(rng.nextDouble(in: 0.18...0.72))
        let spinZ  = Float(rng.nextDouble() * .pi * 2)
        let fov    = R * 3.4

        // Build spine in 3D
        var spine = [(x: Float, y: Float, z: Float)]()
        spine.reserveCapacity(nSpine + 1)
        for i in 0...nSpine {
            let t  = Float(i) / Float(nSpine) * .pi * 2
            var x  = (R + r * cos(Float(q) * t)) * cos(Float(p) * t)
            var y  = (R + r * cos(Float(q) * t)) * sin(Float(p) * t)
            var z  = r * sin(Float(q) * t)
            let x1 = x * cos(spinZ) - y * sin(spinZ); let y1 = x * sin(spinZ) + y * cos(spinZ)
            x = x1; y = y1
            let y2 = y * cos(tiltX) - z * sin(tiltX); let z2 = y * sin(tiltX) + z * cos(tiltX)
            y = y2; z = z2
            spine.append((x, y, z))
        }

        let zMin = spine.map { $0.z }.min() ?? -R
        let zRange = max(0.001, (spine.map { $0.z }.max() ?? R) - zMin)

        // Project one 3D point to 2D, returning (px, py, depth 0-1)
        func proj(_ pt: (x: Float, y: Float, z: Float)) -> (Float, Float, Float) {
            let w = fov / (fov - pt.z)
            let depth = (pt.z - zMin) / zRange
            return (cx + pt.x * w, cy + pt.y * w, depth)
        }

        // Frenet frame at index i (tangent + arbitrary normal)
        func frenet(_ i: Int) -> (tx: Float, ty: Float, tz: Float,
                                   nx: Float, ny: Float, nz: Float,
                                   bx: Float, by: Float, bz: Float) {
            let prev = spine[max(0, i - 1)]; let next = spine[min(spine.count - 1, i + 1)]
            var tx = next.x - prev.x; var ty = next.y - prev.y; var tz = next.z - prev.z
            let tLen = max(0.0001, sqrt(tx*tx + ty*ty + tz*tz))
            tx /= tLen; ty /= tLen; tz /= tLen
            // Stable normal: pick least-parallel world axis
            var nx: Float = 0; var ny: Float = 1; var nz: Float = 0
            if abs(ty) > 0.9 { nx = 0; ny = 0; nz = 1 }
            // Gram-Schmidt
            let dot = nx*tx + ny*ty + nz*tz
            nx -= dot*tx; ny -= dot*ty; nz -= dot*tz
            let nLen = max(0.0001, sqrt(nx*nx + ny*ny + nz*nz))
            nx /= nLen; ny /= nLen; nz /= nLen
            // Binormal = T × N
            let bx = ty*nz - tz*ny; let by = tz*nx - tx*nz; let bz = tx*ny - ty*nx
            return (tx, ty, tz, nx, ny, nz, bx, by, bz)
        }

        let tOffBase = rng.nextDouble()
        let baseWeight = Float(rng.nextDouble(in: 0.5...1.2)) * weightMul
        let ripSeed = params.seed &+ 0xAB7C3D

        // Draw nTube longitudinal strands along the tube surface
        for ring in 0..<nTube {
            let ringAngle = Float(ring) / Float(nTube) * .pi * 2
            let tOff = tOffBase + Double(ring) / Double(nTube) * params.colorDrift
            let segSize = 16

            var segXs = [Float](); var segYs = [Float](); var segDepthSum: Float = 0

            for i in 0..<nSpine {
                let fr = frenet(i)
                let sp = spine[i]
                // Point on tube surface
                let tx3 = sp.x + tubeR * (cos(ringAngle) * fr.nx + sin(ringAngle) * fr.bx)
                let ty3 = sp.y + tubeR * (cos(ringAngle) * fr.ny + sin(ringAngle) * fr.by)
                let tz3 = sp.z + tubeR * (cos(ringAngle) * fr.nz + sin(ringAngle) * fr.bz)
                let (px, py, depth) = proj((tx3, ty3, tz3))
                segXs.append(px); segYs.append(py); segDepthSum += depth

                if segXs.count >= segSize + 1 {
                    let avgDepth = segDepthSum / Float(segXs.count)
                    let wt = baseWeight * (0.04 + avgDepth * 1.8)
                    // Apply symmetry
                    for sym in 0..<symmetry {
                        let angle = Float(sym) * .pi * 2 / Float(symmetry)
                        let cosA = cos(angle); let sinA = sin(angle)
                        var rxs = [Float](); var rys = [Float]()
                        for k in 0..<segXs.count {
                            let dx = segXs[k] - cx; let dy = segYs[k] - cy
                            rxs.append(cx + dx*cosA - dy*sinA)
                            rys.append(cy + dx*sinA + dy*cosA)
                        }
                        if rippleAmount > 0 {
                            applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                                seed: ripSeed &+ UInt64(ring * 97 + sym))
                        }
                        tasks.append(CurveDrawTask(xs: rxs, ys: rys, tOffset: tOff + Double(sym) * 0.1,
                                                   drift: params.colorDrift, weight: wt, thickness: 1))
                    }
                    // Keep last point as start of next segment
                    segXs = [segXs.last!]; segYs = [segYs.last!]; segDepthSum = depth
                }
            }
        }

        // Also draw nTube/2 cross-section rings for tube roundness
        let nRings = max(4, Int(params.density * 20))
        for ri in 0..<nRings {
            let spineIdx = ri * (nSpine / nRings)
            let fr = frenet(spineIdx)
            let sp = spine[spineIdx]
            let (_, _, depth) = proj(sp)
            let wt = baseWeight * (0.04 + depth * 1.8) * 0.6
            var rxs = [Float](); var rys = [Float]()
            let nPts = nTube + 1
            for k in 0..<nPts {
                let a = Float(k % nTube) / Float(nTube) * .pi * 2
                let tx3 = sp.x + tubeR * (cos(a) * fr.nx + sin(a) * fr.bx)
                let ty3 = sp.y + tubeR * (cos(a) * fr.ny + sin(a) * fr.by)
                let tz3 = sp.z + tubeR * (cos(a) * fr.nz + sin(a) * fr.bz)
                let w = fov / (fov - tz3)
                rxs.append(cx + tx3 * w); rys.append(cy + ty3 * w)
            }
            for sym in 0..<symmetry {
                let angle = Float(sym) * .pi * 2 / Float(symmetry)
                let cosA = cos(angle); let sinA = sin(angle)
                var sxs = [Float](); var sys = [Float]()
                for k in 0..<rxs.count {
                    let dx = rxs[k] - cx; let dy = rys[k] - cy
                    sxs.append(cx + dx*cosA - dy*sinA)
                    sys.append(cy + dx*sinA + dy*cosA)
                }
                if rippleAmount > 0 {
                    applyRippleToPoints(xs: &sxs, ys: &sys, amount: rippleAmount,
                                        seed: ripSeed &+ UInt64(ri * 13 + sym + 500))
                }
                tasks.append(CurveDrawTask(xs: sxs, ys: sys,
                                           tOffset: tOffBase + Double(ri) * 0.05 + Double(sym) * 0.1,
                                           drift: params.colorDrift, weight: wt, thickness: 1))
            }
        }
    }

    // MARK: - Sphere Grid (3D) — tilted great circles + parametric sphere curves

    private static func collectSphereGridTasks(into tasks: inout [CurveDrawTask],
                                               cx: Float, cy: Float, radius: Double,
                                               params: MandalaParameters, rng: inout SeededRNG,
                                               layerCount: Int, symmetry: Int,
                                               rippleAmount: Float, weightMul: Float) {
        let R     = Float(radius) * 0.72
        let tiltX = Float(rng.nextDouble(in: 0.08...0.85))
        let spinZ = Float(rng.nextDouble() * .pi * 2)
        let fov   = R * 3.8

        func projectPt(_ x0: Float, _ y0: Float, _ z0: Float) -> (Float, Float, Float) {
            let x1 = x0 * cos(spinZ) - y0 * sin(spinZ)
            let y1 = x0 * sin(spinZ) + y0 * cos(spinZ)
            let y2 = y1 * cos(tiltX) - z0 * sin(tiltX)
            let z2 = y1 * sin(tiltX) + z0 * cos(tiltX)
            let w  = fov / (fov - z2)
            return (cx + x1 * w, cy + y2 * w, z2)
        }

        let zMin: Float = -R; let zRange: Float = 2 * R
        let tOffBase   = rng.nextDouble()
        let baseWeight = Float(rng.nextDouble(in: 0.5...1.1)) * weightMul
        let ripSeed    = params.seed &+ 0xBEEF42

        func emitCurve(_ pts3: [(Float, Float, Float)], tOff: Double) {
            guard pts3.count > 1 else { return }
            let avgZ = pts3.map { $0.2 }.reduce(0, +) / Float(pts3.count)
            let depth = (avgZ - zMin) / zRange
            let wt = baseWeight * (0.04 + depth * 1.8)
            for sym in 0..<symmetry {
                let angle = Float(sym) * .pi * 2 / Float(symmetry)
                let cosA = cos(angle); let sinA = sin(angle)
                var rxs = [Float](); var rys = [Float]()
                for pt in pts3 {
                    let dx = pt.0 - cx; let dy = pt.1 - cy
                    rxs.append(cx + dx*cosA - dy*sinA)
                    rys.append(cy + dx*sinA + dy*cosA)
                }
                if rippleAmount > 0 {
                    applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                        seed: ripSeed &+ UInt64(sym))
                }
                tasks.append(CurveDrawTask(xs: rxs, ys: rys, tOffset: tOff + Double(sym) * 0.1,
                                           drift: params.colorDrift, weight: wt, thickness: 1))
            }
        }

        let steps = 180
        let segSz = 24

        // Tilted great circles — randomly oriented full circles on the sphere
        let nGreat = 4 + Int(params.complexity * 18)
        for gi in 0..<nGreat {
            // Random axis perpendicular to the circle's plane
            let axisTheta = Float(rng.nextDouble() * .pi * 2)
            let axisPhi   = Float(rng.nextDouble() * .pi)
            let ax = sin(axisPhi) * cos(axisTheta)
            let ay = sin(axisPhi) * sin(axisTheta)
            let az = cos(axisPhi)
            // Build two orthogonal vectors in the circle plane
            var ux: Float = -ay; var uy: Float = ax; var uz: Float = 0
            let uLen = max(0.0001, sqrt(ux*ux + uy*uy + uz*uz))
            ux /= uLen; uy /= uLen; uz /= uLen
            let vx = ay*uz - az*uy; let vy = az*ux - ax*uz; let vz = ax*uy - ay*ux
            let tOff = tOffBase + Double(gi) * (params.colorDrift / Double(max(1, nGreat)))

            var seg = [(Float, Float, Float)]()
            for i in 0...steps {
                let t = Float(i) / Float(steps) * .pi * 2
                let x3 = R * (cos(t) * ux + sin(t) * vx)
                let y3 = R * (cos(t) * uy + sin(t) * vy)
                let z3 = R * (cos(t) * uz + sin(t) * vz)
                seg.append(projectPt(x3, y3, z3))
                if seg.count >= segSz + 1 {
                    emitCurve(seg, tOff: tOff)
                    seg = [seg.last!]
                }
            }
            if seg.count > 1 { emitCurve(seg, tOff: tOff) }
        }

        // Parametric sphere spirals — curves that wind pole to pole with increasing longitude
        let nSpirals = 1 + Int(params.density * 5)
        for si in 0..<nSpirals {
            let winds = 2.0 + params.complexity * 8.0   // number of longitude wraps
            let phaseOff = Float(si) / Float(nSpirals) * .pi * 2
            let tOff = tOffBase + 0.5 + Double(si) * 0.15
            let nPts = 400 + Int(params.complexity * 600)
            var seg = [(Float, Float, Float)]()

            for i in 0...nPts {
                let t = Float(i) / Float(nPts)
                let phi = t * .pi                         // 0 → π (south to north)
                let theta = t * Float(winds) * .pi * 2 + phaseOff
                let x3 = R * sin(phi) * cos(theta)
                let y3 = R * sin(phi) * sin(theta)
                let z3 = R * cos(phi)
                seg.append(projectPt(x3, y3, z3))
                if seg.count >= segSz + 1 {
                    emitCurve(seg, tOff: tOff)
                    seg = [seg.last!]
                }
            }
            if seg.count > 1 { emitCurve(seg, tOff: tOff) }
        }
    }

    // MARK: - Tesseract (4D hypercube projected to 2D) — nested shells + symmetry + ripple

    private static func collectTesseractTasks(into tasks: inout [CurveDrawTask],
                                              cx: Float, cy: Float, radius: Double,
                                              params: MandalaParameters, rng: inout SeededRNG,
                                              layerCount: Int, symmetry: Int,
                                              rippleAmount: Float, weightMul: Float) {
        let baseScale = Float(radius) * 0.38
        // Number of nested tesseract shells driven by density
        let nShells = 1 + Int(params.density * 4)

        let angle1 = Float(rng.nextDouble() * .pi * 2)
        let angle2 = Float(rng.nextDouble() * .pi * 2)
        let angle3 = Float(rng.nextDouble(in: 0.15...1.4))
        let angle4 = Float(rng.nextDouble() * .pi * 2)  // extra YW rotation
        let tiltX  = Float(rng.nextDouble(in: 0.15...0.75))
        let tOffBase   = rng.nextDouble()
        let baseWeight = Float(rng.nextDouble(in: 0.8...1.8)) * weightMul
        let ripSeed    = params.seed &+ 0xDEAD99

        // 16 vertices of unit 4-cube
        var verts4D = [(Float, Float, Float, Float)]()
        for xi in [-1, 1] { for yi in [-1, 1] { for zi in [-1, 1] { for wi in [-1, 1] {
            verts4D.append((Float(xi), Float(yi), Float(zi), Float(wi)))
        }}}}

        // 32 edges
        var edges = [(Int, Int)]()
        for i in 0..<16 {
            for j in (i+1)..<16 {
                let a = verts4D[i]; let b = verts4D[j]
                let diff = (a.0 != b.0 ? 1 : 0) + (a.1 != b.1 ? 1 : 0) +
                           (a.2 != b.2 ? 1 : 0) + (a.3 != b.3 ? 1 : 0)
                if diff == 1 { edges.append((i, j)) }
            }
        }

        for shell in 0..<nShells {
            let shellScale = baseScale * (1.0 - Float(shell) * 0.18)
            let fov3 = shellScale * 3.2
            let fov4 = shellScale * 2.8
            // Vary 4D rotation slightly per shell for complexity feel
            let extraRot = Float(shell) * Float(params.complexity * 0.6)

            func rot4(_ v: (Float,Float,Float,Float)) -> (Float,Float,Float,Float) {
                var (x,y,z,w) = v
                let x1 = x*cos(angle1+extraRot) - y*sin(angle1+extraRot)
                let y1 = x*sin(angle1+extraRot) + y*cos(angle1+extraRot)
                x = x1; y = y1
                let z1 = z*cos(angle2+extraRot*0.7) - w*sin(angle2+extraRot*0.7)
                let w1 = z*sin(angle2+extraRot*0.7) + w*cos(angle2+extraRot*0.7)
                z = z1; w = w1
                let x2 = x*cos(angle3) - w*sin(angle3)
                let w2 = x*sin(angle3) + w*cos(angle3)
                x = x2; w = w2
                let y3 = y*cos(angle4) - w*sin(angle4)
                let w3 = y*sin(angle4) + w*cos(angle4)
                y = y3; w = w3
                return (x*shellScale, y*shellScale, z*shellScale, w*shellScale)
            }

            func proj4to3(_ v: (Float,Float,Float,Float)) -> (Float,Float,Float) {
                let d = fov4 / (fov4 - v.3)
                return (v.0*d, v.1*d, v.2*d)
            }

            func proj3to2(_ p: (Float,Float,Float)) -> (Float,Float,Float) {
                let y2 = p.1*cos(tiltX) - p.2*sin(tiltX)
                let z2 = p.1*sin(tiltX) + p.2*cos(tiltX)
                let d = fov3 / (fov3 - z2)
                return (cx + p.0*d, cy + y2*d, z2)
            }

            let proj2 = verts4D.map { proj3to2(proj4to3(rot4($0))) }
            let zMin2 = proj2.map { $0.2 }.min() ?? -shellScale
            let zRange2 = max(0.001, (proj2.map { $0.2 }.max() ?? shellScale) - zMin2)

            let nSubdiv = 18 + Int(params.complexity * 30)
            let tOff = tOffBase + Double(shell) * 0.2

            for (ei, ej) in edges {
                let (px0, py0, pz0) = proj2[ei]
                let (px1, py1, pz1) = proj2[ej]

                var segXs = [Float](); var segYs = [Float](); var segZSum: Float = 0
                let subSegSz = 6
                for s in 0...nSubdiv {
                    let t = Float(s) / Float(nSubdiv)
                    segXs.append(px0 + (px1-px0)*t)
                    segYs.append(py0 + (py1-py0)*t)
                    segZSum += pz0 + (pz1-pz0)*t

                    if segXs.count >= subSegSz + 1 {
                        let avgZ  = segZSum / Float(segXs.count)
                        let depth = (avgZ - zMin2) / zRange2
                        let wt    = baseWeight * (0.04 + depth * 1.8) * (1.0 - Float(shell) * 0.15)
                        for sym in 0..<symmetry {
                            let angle = Float(sym) * .pi * 2 / Float(symmetry)
                            let cosA = cos(angle); let sinA = sin(angle)
                            var rxs = [Float](); var rys = [Float]()
                            for k in 0..<segXs.count {
                                let dx = segXs[k] - cx; let dy = segYs[k] - cy
                                rxs.append(cx + dx*cosA - dy*sinA)
                                rys.append(cy + dx*sinA + dy*cosA)
                            }
                            if rippleAmount > 0 {
                                applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                                    seed: ripSeed &+ UInt64(ei*31+ej+sym*7+shell*100))
                            }
                            tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                                       tOffset: tOff + Double(ei+ej)*0.012 + Double(sym)*0.1,
                                                       drift: params.colorDrift * 0.6,
                                                       weight: wt, thickness: 1))
                        }
                        segXs = [segXs.last!]; segYs = [segYs.last!]; segZSum = pz0 + (pz1-pz0)*Float(nSubdiv)/Float(nSubdiv)
                    }
                }
                if segXs.count > 1 {
                    let avgZ  = segZSum / Float(segXs.count)
                    let depth = (avgZ - zMin2) / zRange2
                    let wt    = baseWeight * (0.04 + depth * 1.8) * (1.0 - Float(shell) * 0.15)
                    for sym in 0..<symmetry {
                        let angle = Float(sym) * .pi * 2 / Float(symmetry)
                        let cosA = cos(angle); let sinA = sin(angle)
                        var rxs = [Float](); var rys = [Float]()
                        for k in 0..<segXs.count {
                            let dx = segXs[k] - cx; let dy = segYs[k] - cy
                            rxs.append(cx + dx*cosA - dy*sinA)
                            rys.append(cy + dx*sinA + dy*cosA)
                        }
                        if rippleAmount > 0 {
                            applyRippleToPoints(xs: &rxs, ys: &rys, amount: rippleAmount,
                                                seed: ripSeed &+ UInt64(ei*31+ej+sym*7+shell*100+999))
                        }
                        tasks.append(CurveDrawTask(xs: rxs, ys: rys,
                                                   tOffset: tOff + Double(ei+ej)*0.012 + Double(sym)*0.1,
                                                   drift: params.colorDrift * 0.6,
                                                   weight: wt, thickness: 1))
                    }
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

        // Radii in reference-1600px pixels — resolution-independent via referenceBlurOverlay.
        let aF = CGFloat(amount)
        let mainPasses: [(radius: CGFloat, strength: CGFloat)] = [
            ( 4.0 * aF, 0.25),
            (12.0 * aF, 0.20),
            (28.0 * aF, 0.15),
            (55.0 * aF, 0.10),
        ]
        var result = ci
        if let overlay = referenceBlurOverlay(source: ci, passes: mainPasses),
           let add = CIFilter(name: "CIAdditionCompositing") {
            add.setValue(result,  forKey: kCIInputBackgroundImageKey)
            add.setValue(overlay, forKey: kCIInputImageKey)
            if let out = add.outputImage?.cropped(to: ext) { result = out }
        }

        // Chromatic bleed: saturate then blur — also normalised via helper
        if amount > 0.15,
           let sat = CIFilter(name: "CIColorControls") {
            sat.setValue(ci, forKey: kCIInputImageKey)
            sat.setValue(min(4.0, 1.0 + amount * 3.0), forKey: kCIInputSaturationKey)
            sat.setValue(0.0, forKey: kCIInputBrightnessKey)
            if let saturated = sat.outputImage?.cropped(to: ext) {
                let chromaticPasses: [(radius: CGFloat, strength: CGFloat)] = [
                    (45.0 * aF, CGFloat(amount * 0.25))
                ]
                if let chrOverlay = referenceBlurOverlay(source: saturated, passes: chromaticPasses),
                   let add2 = CIFilter(name: "CIAdditionCompositing") {
                    add2.setValue(result,     forKey: kCIInputBackgroundImageKey)
                    add2.setValue(chrOverlay, forKey: kCIInputImageKey)
                    if let out = add2.outputImage?.cropped(to: ext) { result = out }
                }
            }
        }

        return ctx.createCGImage(result, from: ext) ?? image
    }


    // MARK: - Layer rotation

    /// Crop the centre `size × size` square from `image` (which may be larger).
    private static func cropCenter(_ image: CGImage, to size: Int) -> CGImage {
        let ci  = CIImage(cgImage: image)
        let src = ci.extent
        let ox  = (src.width  - CGFloat(size)) * 0.5
        let oy  = (src.height - CGFloat(size)) * 0.5
        let cropRect = CGRect(x: src.origin.x + ox, y: src.origin.y + oy,
                              width: CGFloat(size), height: CGFloat(size))
        let cropped = ci.cropped(to: cropRect)
        let ctx = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])
        // `from:` must use cropRect (CIImage coordinates), not (0,0,size,size),
        // so that output pixel (0,0) maps to CIImage coordinate (ox, oy) — the centre of the expanded buffer.
        return ctx.createCGImage(cropped, from: cropRect) ?? image
    }

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

    // MARK: - Dust Layer

    /// Renders a Dust Clouds layer.
    /// Two-level structure: N cloud masses, each filled with many tiny flat-alpha
    /// ellipses with random rotation/aspect. Flat fills + blur = cloud texture,
    /// not glowing orbs.
    private static func renderDustAsStyleLayer(layer: StyleLayer, palette: ColorPalette,
                                               size: Int, rng: inout SeededRNG) -> CGImage? {
        let sym    = max(1, layer.symmetry)
        let canvas = CGFloat(size)
        let cx = canvas / 2, cy = canvas / 2

        // Cloud mass count scales with density; sub-blob count with complexity
        let massCount    = max(2, Int(layer.density * 12 + 3))
        let blobsPerMass = max(80, Int(layer.complexity * 300 + 80))

        // Spread of mass centres across canvas
        let spread = canvas * 0.44 * CGFloat(layer.scale)

        guard let dustRep = NSBitmapImageRep(
            bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size,
            bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
            colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0
        ) else { return nil }

        NSGraphicsContext.saveGraphicsState()
        if let dustCtx = NSGraphicsContext(bitmapImageRep: dustRep) {
            NSGraphicsContext.current = dustCtx
            let cgCtx = dustCtx.cgContext

            for _ in 0..<massCount {
                // Cloud mass centre (square-root distribution → fill canvas area evenly)
                let massDist  = CGFloat(sqrt(rng.nextDouble())) * spread
                let massAngle = CGFloat(rng.nextDouble()) * .pi * 2
                let massCX    = cos(massAngle) * massDist
                let massCY    = sin(massAngle) * massDist

                // Cloud mass has a dominant axis (elongated like a real cloud)
                let massW = canvas * CGFloat(0.08 + rng.nextDouble() * 0.18) * CGFloat(layer.scale)
                let massH = massW * CGFloat(0.3 + rng.nextDouble() * 0.55)
                let massRot = CGFloat(rng.nextDouble()) * .pi

                // Per-mass colour
                let massT = (layer.colorOffset + rng.nextDouble() * layer.colorDrift)
                    .truncatingRemainder(dividingBy: 1.0)
                let massCol = palette.color(at: massT)
                let massR = CGFloat(massCol.redComponent)
                let massG = CGFloat(massCol.greenComponent)
                let massB = CGFloat(massCol.blueComponent)

                for _ in 0..<blobsPerMass {
                    // Sub-blob position: tent distribution along mass axes
                    let u1 = CGFloat(rng.nextDouble()), u2 = CGFloat(rng.nextDouble())
                    let v1 = CGFloat(rng.nextDouble()), v2 = CGFloat(rng.nextDouble())
                    let localX = (u1 + u2 - 1.0) * massW
                    let localY = (v1 + v2 - 1.0) * massH
                    // Rotate local offset by mass dominant axis
                    let subX = massCX + localX * cos(massRot) - localY * sin(massRot)
                    let subY = massCY + localX * sin(massRot) + localY * cos(massRot)

                    // Small flat-fill ellipses — no radial gradient → no glow look
                    let baseR = canvas * CGFloat(0.004 + rng.nextDouble() * 0.014) * CGFloat(layer.scale)
                    // Elongated, randomly oriented — creates wispy cloud texture
                    let aspect   = CGFloat(0.25 + rng.nextDouble() * 1.5)
                    let blobRot  = CGFloat(rng.nextDouble()) * .pi
                    let bW = baseR * max(aspect, 1.0)
                    let bH = baseR * min(1.0 / aspect, 1.0) * max(aspect, 1.0)

                    // Very low alpha — cloud density comes from overlap accumulation
                    let alpha = CGFloat(rng.nextDouble(in: 0.015...0.07))

                    // Tiny colour drift within mass
                    let subT = (massT + rng.nextDouble() * 0.05).truncatingRemainder(dividingBy: 1.0)
                    let subCol = palette.color(at: subT)
                    let r = massR * 0.7 + CGFloat(subCol.redComponent) * 0.3
                    let g = massG * 0.7 + CGFloat(subCol.greenComponent) * 0.3
                    let b = massB * 0.7 + CGFloat(subCol.blueComponent) * 0.3

                    cgCtx.setFillColor(CGColor(red: r, green: g, blue: b, alpha: alpha))

                    for s in 0..<sym {
                        let symA = CGFloat(s) * .pi * 2 / CGFloat(sym)
                        let cosS = cos(symA), sinS = sin(symA)
                        let rx   = subX * cosS - subY * sinS + cx
                        let ry   = subX * sinS + subY * cosS + cy

                        cgCtx.saveGState()
                        cgCtx.translateBy(x: rx, y: ry)
                        cgCtx.rotate(by: blobRot)
                        cgCtx.addEllipse(in: CGRect(x: -bW, y: -bH, width: bW * 2, height: bH * 2))
                        cgCtx.fillPath()
                        cgCtx.restoreGState()
                    }
                }
            }
        }
        NSGraphicsContext.restoreGraphicsState()

        guard let dustCG = dustRep.cgImage else { return nil }

        // Blur blends sub-blobs into smooth cloud volumes.
        // Minimum blur softens sharp blob edges; wash adds extra diffusion.
        let minBlur = canvas * 0.014
        let blurR   = minBlur + canvas * CGFloat(layer.wash * layer.wash) * 0.12
        return simpleGaussianBlur(image: dustCG, radius: blurR) ?? dustCG
    }

    // MARK: - Text Layer

    private static func applyTextLayer(image: CGImage, settings: TextLayerSettings, size: Int) -> CGImage {
        guard let textImage = renderTextToCGImage(settings: settings, size: size) else { return image }

        var overlayImage: CGImage = textImage

        // Blur the whole text layer
        if settings.blur > 0.005 {
            let blurRadius = CGFloat(settings.blur * settings.blur) * CGFloat(size) * 0.025
            if let blurred = simpleGaussianBlur(image: overlayImage, radius: blurRadius) {
                overlayImage = blurred
            }
        }

        // Glow (screen-blended soft copy of the text) — must run before brightness boost
        // so the glow source has valid premultiplied alpha (RGB ≤ alpha)
        if settings.glow > 0.005 {
            overlayImage = applyGlow(image: overlayImage, intensity: settings.glow)
        }

        // Extra brightness boost when brightness > 1.0 (applied after glow to avoid corrupting premultiplied alpha)
        if settings.brightness > 1.005 {
            let boost = CGFloat(settings.brightness) - 1.0
            let s = 1.0 + boost
            let ci  = CIImage(cgImage: overlayImage)
            let ext = ci.extent
            let ctx = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])
            if let filter = CIFilter(name: "CIColorMatrix") {
                filter.setValue(ci, forKey: kCIInputImageKey)
                filter.setValue(CIVector(x: s, y: 0, z: 0, w: 0), forKey: "inputRVector")
                filter.setValue(CIVector(x: 0, y: s, z: 0, w: 0), forKey: "inputGVector")
                filter.setValue(CIVector(x: 0, y: 0, z: s, w: 0), forKey: "inputBVector")
                filter.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
                filter.setValue(CIVector(x: 0, y: 0, z: 0, w: 0), forKey: "inputBiasVector")
                if let out = filter.outputImage?.cropped(to: ext),
                   let boosted = ctx.createCGImage(out, from: ext) {
                    overlayImage = boosted
                }
            }
        }

        // Opacity
        if settings.opacity < 0.999 {
            overlayImage = applyLayerOpacity(image: overlayImage, opacity: settings.opacity)
        }

        // Composite onto the main image
        let blendFilter: String
        switch settings.blendMode {
        case .screen:   blendFilter = "CIScreenBlendMode"
        case .add:      blendFilter = "CIAdditionCompositing"
        case .normal:   blendFilter = "CISourceOverCompositing"
        case .multiply: blendFilter = "CIMultiplyBlendMode"
        }
        return blendComposite(base: image, overlay: overlayImage, mode: blendFilter)
    }

    private static func renderTextToCGImage(settings: TextLayerSettings, size: Int) -> CGImage? {
        // NSBitmapImageRep + NSGraphicsContext(bitmapImageRep:) is the most reliable path
        // for text rendering on macOS — avoids all CGBitmapContext CTM/glyph-handedness issues.
        // NSGraphicsContext(bitmapImageRep:) uses a FLIPPED coordinate system:
        //   (0,0) = top-left, y increases downward.
        guard let rep = NSBitmapImageRep(
            bitmapDataPlanes: nil,
            pixelsWide: size, pixelsHigh: size,
            bitsPerSample: 8, samplesPerPixel: 4,
            hasAlpha: true, isPlanar: false,
            colorSpaceName: .deviceRGB,
            bytesPerRow: 0, bitsPerPixel: 0
        ) else { return nil }

        NSGraphicsContext.saveGraphicsState()
        defer { NSGraphicsContext.restoreGraphicsState() }
        guard let nsCtx = NSGraphicsContext(bitmapImageRep: rep) else { return nil }
        NSGraphicsContext.current = nsCtx

        let resolvedFontSize = CGFloat(settings.fontSize * Double(size))
        let nsFont = NSFont(name: settings.fontName, size: resolvedFontSize)
            ?? NSFont(name: "Georgia", size: resolvedFontSize)
            ?? NSFont.systemFont(ofSize: resolvedFontSize)

        // Clamp brightness to 1.0 for NSColor — values >1 are applied later via CIColorMatrix boost
        let textColor = NSColor(hue: CGFloat(settings.hue),
                                saturation: CGFloat(settings.saturation),
                                brightness: min(1.0, CGFloat(settings.brightness)),
                                alpha: 1.0)

        let paraStyle = NSMutableParagraphStyle()
        paraStyle.alignment = .center
        paraStyle.lineSpacing = resolvedFontSize * 0.18

        let kern = CGFloat(settings.tracking) * resolvedFontSize * 0.12

        let attrs: [NSAttributedString.Key: Any] = [
            .font: nsFont,
            .foregroundColor: textColor,
            .paragraphStyle: paraStyle,
            .kern: kern,
        ]

        // Shadow is rendered manually below for stronger effect (see manual shadow pass)

        // ── Quote text ────────────────────────────────────────────────────────
        let quoteString = NSAttributedString(string: settings.text, attributes: attrs)
        let maxWidth = CGFloat(size) * 0.82
        let quoteBounds = quoteString.boundingRect(
            with: NSSize(width: maxWidth, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading]
        )

        // ── Author line (optional, drawn separately so spacing is font-independent) ──
        // Resolve author: prefer customAuthor field, then fall back to database lookup.
        let resolvedAuthor: String? = {
            guard settings.showAuthor else { return nil }
            if !settings.customAuthor.isEmpty { return settings.customAuthor }
            return QuoteDatabase.quotes.first(where: {
                $0.text == settings.text || settings.text.hasSuffix($0.text)
            })?.author
        }()

        let authorAttrString: NSAttributedString? = resolvedAuthor.map { match in
            let authorFontSize = resolvedFontSize * CGFloat(settings.authorScale)
            let baseAuthorFont = NSFont(name: settings.fontName, size: authorFontSize)
                ?? NSFont.systemFont(ofSize: authorFontSize)
            let authorFont: NSFont = settings.authorItalic
                ? NSFontManager.shared.convert(baseAuthorFont, toHaveTrait: .italicFontMask)
                : baseAuthorFont
            var authorAttrs = attrs
            authorAttrs[.font] = authorFont
            return NSAttributedString(string: "\u{2014} " + match, attributes: authorAttrs)
        }

        let authorBounds: CGRect = authorAttrString?.boundingRect(
            with: NSSize(width: maxWidth, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading]
        ) ?? .zero

        // Fixed gap between quote bottom and author top — always one main-font line height,
        // so the author's baseline sits at the same distance regardless of author scale.
        let lineGap = authorAttrString != nil ? resolvedFontSize * 0.75 : 0
        let totalHeight = quoteBounds.height + lineGap + authorBounds.height

        let drawX = (CGFloat(size) - maxWidth) / 2.0
        // Standard y-up coords: offsetY 0=bottom, 0.5=center, 1=top
        let centerY = CGFloat(settings.offsetY) * CGFloat(size)
        let blockBottom = centerY - totalHeight / 2.0

        // Author is at the bottom of the block; quote sits above it.
        // In y-up coords, higher Y = higher on screen.
        let quoteY = blockBottom + authorBounds.height + lineGap

        // ── Cloud / halo behind text ──────────────────────────────────────────
        // Always circular: gradient drawn in a SQUARE rect so the inscribed ellipse
        // is a circle regardless of text block aspect ratio.
        // cloudRadius 0 → tight halo around text; 1 → fills the whole canvas.
        if settings.cloudOpacity > 0.01 {
            let r = CGFloat(settings.cloudRadius)
            let textCX = drawX + maxWidth / 2
            let textCY = blockBottom + totalHeight / 2

            // Radius grows from "snug around text" to "covers full canvas corners"
            let textHalf   = max(maxWidth, totalHeight) * 0.6
            let fullRadius = CGFloat(size) * 0.78   // just beyond canvas corner at sqrt(2)/2
            let circRadius = textHalf + r * (fullRadius - textHalf)

            let squareRect = NSRect(x: textCX - circRadius, y: textCY - circRadius,
                                    width: circRadius * 2, height: circRadius * 2)

            if let cloudRep = NSBitmapImageRep(
                bitmapDataPlanes: nil,
                pixelsWide: size, pixelsHigh: size,
                bitsPerSample: 8, samplesPerPixel: 4,
                hasAlpha: true, isPlanar: false,
                colorSpaceName: .deviceRGB,
                bytesPerRow: 0, bitsPerPixel: 0
            ), let cloudCtx = NSGraphicsContext(bitmapImageRep: cloudRep) {
                NSGraphicsContext.current = cloudCtx

                let cloudColor = NSColor(hue: CGFloat(settings.cloudHue),
                                         saturation: CGFloat(settings.cloudSaturation),
                                         brightness: CGFloat(settings.cloudBrightness),
                                         alpha: CGFloat(settings.cloudOpacity))
                let clearColor = cloudColor.withAlphaComponent(0)

                // Square rect → inscribed circle gradient (always round)
                if let gradient = NSGradient(colors: [cloudColor, cloudColor, clearColor],
                                              atLocations: [0.0, 0.5, 1.0],
                                              colorSpace: .deviceRGB) {
                    gradient.draw(in: squareRect, relativeCenterPosition: NSPoint(x: 0.5, y: 0.5))
                }

                NSGraphicsContext.current = nsCtx

                // Blur scales with cloudRadius: small = crisp circle, large = full-screen wash
                let blurRadius = CGFloat(size) * r * r * 0.25 + resolvedFontSize * 0.3
                if let cloudCGImage = cloudRep.cgImage,
                   let blurred = simpleGaussianBlur(image: cloudCGImage, radius: blurRadius) {
                    NSImage(cgImage: blurred, size: NSSize(width: size, height: size))
                        .draw(in: NSRect(x: 0, y: 0, width: size, height: size))
                }
            }
        }

        // ── Shadow: NSShadow rendered to a temp bitmap, composited N times for strength ──
        // NSShadow composites the shadow under the text in a single pass, so anti-aliased
        // text edges blend correctly with no coloured fringe. Repeating the temp bitmap
        // builds up shadow opacity while leaving fully-opaque text pixels unchanged.
        if settings.shadowOpacity > 0.01 {
            let blurPx  = max(1.0, CGFloat(settings.shadowBlur) * resolvedFontSize * 1.2)
            // Offset is in units of blurPx, scaled by the user sliders.
            // In the bitmap context y increases upward, so positive offsetY = upward on screen.
            // Default: offsetX +0.3 (right), offsetY -0.3 → negative = downward on screen.
            let offsetX = CGFloat(settings.shadowOffsetX) * blurPx
            let offsetY = CGFloat(settings.shadowOffsetY) * blurPx

            let nsShadow = NSShadow()
            nsShadow.shadowOffset     = NSSize(width: offsetX, height: offsetY)
            nsShadow.shadowBlurRadius = blurPx
            nsShadow.shadowColor      = (NSColor(hue: CGFloat(settings.shadowHue),
                                                  saturation: CGFloat(settings.shadowSaturation),
                                                  brightness: CGFloat(settings.shadowBrightness),
                                                  alpha: CGFloat(settings.shadowOpacity))
                                         .usingColorSpace(.deviceRGB)) ?? NSColor.black

            var shadowAttrs = attrs
            shadowAttrs[.shadow] = nsShadow

            if let tempRep = NSBitmapImageRep(
                bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size,
                bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0
            ), let tempCtx = NSGraphicsContext(bitmapImageRep: tempRep) {
                NSGraphicsContext.current = tempCtx
                tempCtx.imageInterpolation = .high

                NSAttributedString(string: settings.text, attributes: shadowAttrs)
                    .draw(with: NSRect(x: drawX, y: quoteY,
                                       width: maxWidth, height: quoteBounds.height + resolvedFontSize * 0.3),
                          options: [.usesLineFragmentOrigin, .usesFontLeading])

                if let authorStr = authorAttrString,
                   let authorFont = authorStr.attribute(.font, at: 0, effectiveRange: nil) as? NSFont {
                    var aAttrs = shadowAttrs
                    aAttrs[.font] = authorFont
                    NSAttributedString(string: authorStr.string, attributes: aAttrs)
                        .draw(with: NSRect(x: drawX, y: blockBottom,
                                           width: maxWidth, height: authorBounds.height + resolvedFontSize * 0.2),
                              options: [.usesLineFragmentOrigin, .usesFontLeading])
                }

                NSGraphicsContext.current = nsCtx

                if let tempCG = tempRep.cgImage {
                    let tempImg = NSImage(cgImage: tempCG, size: NSSize(width: size, height: size))
                    // More passes = deeper shadow; text pixels are already fully opaque so they don't change
                    let passes = max(1, Int(settings.shadowOpacity * 5 + 0.5))
                    for _ in 0..<passes {
                        tempImg.draw(in: NSRect(x: 0, y: 0, width: size, height: size))
                    }
                }
            }
            NSGraphicsContext.current = nsCtx
        }

        // Draw quote (top of block, higher y)
        quoteString.draw(with: NSRect(x: drawX, y: quoteY,
                                      width: maxWidth, height: quoteBounds.height + resolvedFontSize * 0.3),
                         options: [.usesLineFragmentOrigin, .usesFontLeading])

        // Draw author below the quote (bottom of block, lower y)
        if let authorStr = authorAttrString {
            authorStr.draw(with: NSRect(x: drawX, y: blockBottom,
                                        width: maxWidth, height: authorBounds.height + resolvedFontSize * 0.2),
                           options: [.usesLineFragmentOrigin, .usesFontLeading])
        }

        // rep stores pixels top→bottom (row 0 = visual top), matching PixelBuffer convention
        return rep.cgImage
    }

    private static func simpleGaussianBlur(image: CGImage, radius: CGFloat) -> CGImage? {
        guard radius > 0.5 else { return image }
        let ci  = CIImage(cgImage: image)
        let ext = ci.extent
        let ctx = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])
        guard let blur = CIFilter(name: "CIGaussianBlur") else { return nil }
        blur.setValue(ci,     forKey: kCIInputImageKey)
        blur.setValue(radius, forKey: kCIInputRadiusKey)
        guard let out = blur.outputImage?.cropped(to: ext) else { return nil }
        return ctx.createCGImage(out, from: ext)
    }

    // MARK: - Layer opacity

    private static func applyLayerOpacity(image: CGImage, opacity: Double) -> CGImage {
        let ci = CIImage(cgImage: image)
        let ctx = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])
        let ext = ci.extent
        let s = CGFloat(opacity)
        guard let filter = CIFilter(name: "CIColorMatrix") else { return image }
        filter.setValue(ci, forKey: kCIInputImageKey)
        filter.setValue(CIVector(x: s, y: 0, z: 0, w: 0), forKey: "inputRVector")
        filter.setValue(CIVector(x: 0, y: s, z: 0, w: 0), forKey: "inputGVector")
        filter.setValue(CIVector(x: 0, y: 0, z: s, w: 0), forKey: "inputBVector")
        filter.setValue(CIVector(x: 0, y: 0, z: 0, w: 1), forKey: "inputAVector")
        filter.setValue(CIVector(x: 0, y: 0, z: 0, w: 0), forKey: "inputBiasVector")
        guard let out = filter.outputImage?.cropped(to: ext) else { return image }
        return ctx.createCGImage(out, from: ext) ?? image
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

    // MARK: - Resolution-independent blur overlay helper

    /// Computes additive blur passes at a fixed 1600 px reference resolution so that
    /// brightness and glow appearance are identical regardless of the actual buffer size.
    /// The source is scaled to 1600 px before blurring; the resulting overlay is scaled
    /// back to the original size before returning.
    ///
    /// - Parameters:
    ///   - source:   The CIImage to blur (must have `.extent` starting at origin).
    ///   - passes:   Array of (blurRadius, strength) pairs in **reference-1600px pixels**.
    /// - Returns: An additive overlay CIImage at the same size as `source`, or nil on failure.
    private static func referenceBlurOverlay(source: CIImage,
                                             passes: [(radius: CGFloat, strength: CGFloat)]) -> CIImage? {
        let refW: CGFloat = 1600.0
        let actualW = source.extent.width
        let sf = refW / actualW                              // scale factor to reference
        let refExt = CGRect(x: 0, y: 0,
                            width:  actualW * sf,
                            height: source.extent.height * sf)

        // Scale source to reference size (up or down)
        var workCI = source
        if abs(sf - 1.0) > 0.005 {
            guard let scaleF = CIFilter(name: "CILanczosScaleTransform") else { return nil }
            scaleF.setValue(source, forKey: kCIInputImageKey)
            scaleF.setValue(sf,     forKey: kCIInputScaleKey)
            scaleF.setValue(1.0,    forKey: "inputAspectRatio")
            workCI = scaleF.outputImage?.cropped(to: refExt) ?? source
        }

        // Accumulate blur passes additively at reference resolution
        var overlayCI: CIImage? = nil
        for (radius, strength) in passes {
            guard radius >= 0.5,
                  let blur = CIFilter(name: "CIGaussianBlur"),
                  let tint = CIFilter(name: "CIColorMatrix") else { continue }
            blur.setValue(workCI, forKey: kCIInputImageKey)
            blur.setValue(radius, forKey: kCIInputRadiusKey)
            guard let blurred = blur.outputImage?.cropped(to: refExt) else { continue }
            tint.setValue(blurred, forKey: kCIInputImageKey)
            tint.setValue(CIVector(x: strength, y: 0,        z: 0,        w: 0), forKey: "inputRVector")
            tint.setValue(CIVector(x: 0,        y: strength, z: 0,        w: 0), forKey: "inputGVector")
            tint.setValue(CIVector(x: 0,        y: 0,        z: strength, w: 0), forKey: "inputBVector")
            tint.setValue(CIVector(x: 0,        y: 0,        z: 0,        w: 1), forKey: "inputAVector")
            tint.setValue(CIVector(x: 0,        y: 0,        z: 0,        w: 0), forKey: "inputBiasVector")
            guard let tinted = tint.outputImage?.cropped(to: refExt) else { continue }
            if let prev = overlayCI,
               let addF = CIFilter(name: "CIAdditionCompositing") {
                addF.setValue(prev,   forKey: kCIInputBackgroundImageKey)
                addF.setValue(tinted, forKey: kCIInputImageKey)
                overlayCI = addF.outputImage?.cropped(to: refExt)
            } else {
                overlayCI = tinted
            }
        }

        guard var overlay = overlayCI else { return nil }

        // Scale overlay back to original size
        if abs(sf - 1.0) > 0.005 {
            guard let upF = CIFilter(name: "CILanczosScaleTransform") else { return overlay }
            upF.setValue(overlay,         forKey: kCIInputImageKey)
            upF.setValue(1.0 / sf,        forKey: kCIInputScaleKey)
            upF.setValue(1.0,             forKey: "inputAspectRatio")
            overlay = upF.outputImage?.cropped(to: source.extent) ?? overlay
        }
        return overlay
    }

    static func applyGlow(image: CGImage, intensity: Double) -> CGImage {
        guard intensity > 0 else { return image }
        let ciImage = CIImage(cgImage: image)
        let context = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.displayP3) as Any])
        let ext = ciImage.extent

        // Radii in reference-1600px pixels (= original code at scale=1, intensity×3 factor).
        let iF = CGFloat(intensity)
        let passes: [(radius: CGFloat, strength: CGFloat)] = [
            (2.0  * iF * 3.0, 1.0  * iF),
            (8.0  * iF * 3.0, 0.4  * iF),
            (20.0 * iF * 3.0, 0.15 * iF),
        ]
        guard let glow = referenceBlurOverlay(source: ciImage, passes: passes) else { return image }
        guard let screen = CIFilter(name: "CIScreenBlendMode") else { return image }
        screen.setValue(ciImage, forKey: kCIInputBackgroundImageKey)
        screen.setValue(glow,    forKey: kCIInputImageKey)
        guard let composited = screen.outputImage?.cropped(to: ext) else { return image }
        return context.createCGImage(composited, from: ext) ?? image
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
        let bMul = CGFloat(brightness * 3.0)
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

    // MARK: - Universe Layer

    private static func drawUniverseLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                           radius: Double, params: MandalaParameters,
                                           palette: ColorPalette, rng: inout SeededRNG,
                                           colorOffset: Double, symmetry: Int) {
        let R = Float(radius)
        let density = params.density
        let complexity = params.complexity
        let w = max(0.5, Float(0.6 + complexity * 1.0) * R / 900.0)

        func col(at t: Double) -> (r: Float, g: Float, b: Float) {
            let c = palette.color(at: (t + colorOffset).truncatingRemainder(dividingBy: 1.0))
            return (Float(c.redComponent), Float(c.greenComponent), Float(c.blueComponent))
        }
        func addCircle(_ bx: Float, _ by: Float, _ r: Float,
                       _ color: (r: Float, g: Float, b: Float), _ weight: Float, _ steps: Int = 100) {
            guard r > 0.5 else { return }
            let step = Float.pi * 2.0 / Float(steps)
            for i in 0..<steps {
                let t0 = Float(i) * step, t1 = t0 + step
                buffer.addLine(x0: bx + cos(t0)*r, y0: by + sin(t0)*r,
                               x1: bx + cos(t1)*r, y1: by + sin(t1)*r, color: color, weight: weight)
            }
        }
        func addEllipse(_ bx: Float, _ by: Float, _ rx: Float, _ ry: Float,
                        _ color: (r: Float, g: Float, b: Float), _ weight: Float, _ steps: Int = 100) {
            guard rx > 0.5, ry > 0.5 else { return }
            let step = Float.pi * 2.0 / Float(steps)
            for i in 0..<steps {
                let t0 = Float(i) * step, t1 = t0 + step
                buffer.addLine(x0: bx + cos(t0)*rx, y0: by + sin(t0)*ry,
                               x1: bx + cos(t1)*rx, y1: by + sin(t1)*ry, color: color, weight: weight)
            }
        }
        // Symmetry helper: run body once per symmetry copy, rotating (rx,ry) relative to center
        func forSym(rx: Float, ry: Float, _ body: (Float, Float) -> Void) {
            for s in 0..<symmetry {
                let a = Float(s) * .pi * 2.0 / Float(symmetry)
                let ca = cos(a), sa = sin(a)
                body(cx + rx*ca - ry*sa, cy + rx*sa + ry*ca)
            }
        }

        // Draw a planet with atmosphere halos
        func drawPlanet(_ bx: Float, _ by: Float, _ pr: Float, _ ct: Double, _ wMul: Float = 1.0) {
            let pc = col(at: ct)
            addCircle(bx, by, pr, pc, w * 3.0 * wMul, 140)
            for i in 1...5 {
                let haloR = pr * (1.0 + Float(i) * 0.22)
                let a = Float(1.0 - Double(i) * 0.17)
                let hc = col(at: ct + Double(i) * 0.03)
                addCircle(bx, by, haloR, (hc.r*a, hc.g*a, hc.b*a), w * 0.7 * wMul, 100)
            }
        }
        // Draw a mini spiral galaxy at position
        func drawMiniGalaxy(_ gx: Float, _ gy: Float, _ gR: Float, _ gt: Double, _ arms: Int = 2) {
            let gc = col(at: gt)
            for i in 0...4 {
                let a = Float(1.0 - Double(i)*0.18)
                addCircle(gx, gy, gR*Float(0.2+Double(i)*0.18), (gc.r*a, gc.g*a, gc.b*a), w*Float(2.5-Double(i)*0.4), 35)
            }
            let ac = col(at: gt + 0.1)
            for arm in 0..<arms {
                let aOff = Float(arm) * .pi * 2.0 / Float(arms) + Float(rng.nextDouble() * 0.6)
                for s in 0..<70 {
                    let t = Float(s) / 70.0
                    let lr = gR * pow(t, 0.48)
                    let th = t * .pi * 4.0 + aOff
                    let x0 = gx + cos(th)*lr, y0 = gy + sin(th)*lr
                    let t2 = t + 1.0/70.0
                    let lr2 = gR * pow(t2, 0.48)
                    let th2 = t2 * .pi * 4.0 + aOff
                    let x1 = gx + cos(th2)*lr2, y1 = gy + sin(th2)*lr2
                    buffer.addLine(x0: x0, y0: y0, x1: x1, y1: y1,
                                   color: ac, weight: w*(1.2-t*0.7))
                }
            }
        }

        // Stars — only shown when symmetry > 1 (background dots look wrong at symmetry 1)
        let starCount = symmetry > 1 ? Int(80 + density * 700) : 0
        let sectorAngle = Float.pi * 2.0 / Float(max(1, symmetry))
        for _ in 0..<starCount {
            // Place star in first sector, then replicate via forSym
            let ang = Float(rng.nextDouble()) * sectorAngle
            let dist = Float(sqrt(rng.nextDouble())) * R * 0.96
            let rx = cos(ang) * dist, ry = sin(ang) * dist
            let bright = Float(rng.nextDouble() * 0.5 + 0.5)
            let sc = col(at: rng.nextDouble() * 0.4 + 0.55)
            let sr = Float(rng.nextDouble() * 1.8 + 0.5)
            forSym(rx: rx, ry: ry) { sx, sy in
                addCircle(sx, sy, sr, (sc.r*bright, sc.g*bright, sc.b*bright), 0.6, 8)
            }
        }

        let level = density

        if level < 0.14 {
            // ── Level 1: Single abstract planet ──
            let pr = R * 0.32
            drawPlanet(cx, cy, pr, 0.28)
            addEllipse(cx, cy, pr * 1.05, pr * 0.35, col(at: 0.35), w * 0.8, 100)
            // Moon — replicated per symmetry at angular offset
            let moonAng = Float(rng.nextDouble()) * sectorAngle
            let moonDist = pr * 1.9
            forSym(rx: cos(moonAng)*moonDist, ry: sin(moonAng)*moonDist) { mx, my in
                drawPlanet(mx, my, pr*0.22, 0.55, 0.7)
            }

        } else if level < 0.27 {
            // ── Level 2: Two planets ──
            let off1: (Float, Float) = (-R*0.35, R*0.06)
            let off2: (Float, Float) = (R*0.30, -R*0.08)
            forSym(rx: off1.0, ry: off1.1) { px, py in drawPlanet(px, py, R*0.22, 0.18) }
            forSym(rx: off2.0, ry: off2.1) { px, py in drawPlanet(px, py, R*0.16, 0.56) }
            addCircle(cx, cy, R*0.32, col(at: 0.72), w*0.25, 80)
            // Moon
            let mAng = Float(rng.nextDouble()) * sectorAngle
            let mBase = (off1.0 + cos(mAng)*R*0.22*2.0, off1.1 + sin(mAng)*R*0.22*2.0)
            forSym(rx: mBase.0, ry: mBase.1) { px, py in drawPlanet(px, py, R*0.05, 0.6, 0.6) }

        } else if level < 0.40 {
            // ── Level 3: Saturn ──
            let pr = R * 0.26
            let pc = col(at: 0.42)
            addCircle(cx, cy, pr, pc, w * 3.0, 140)
            for j in 1...4 {
                let hc = col(at: 0.40 + Double(j)*0.025)
                let a = Float(1.0 - Double(j)*0.18)
                addCircle(cx, cy, pr*(1.0+Float(j)*0.14), (hc.r*a, hc.g*a, hc.b*a), w*0.6, 100)
            }
            for b in 0..<3 {
                let bf = Float(0.55 + Double(b)*0.15)
                addEllipse(cx, cy, pr*bf, pr*0.18, col(at: 0.38+Double(b)*0.04), w*0.7, 100)
            }
            for ri in 0...6 {
                let rf = Float(1.5 + Double(ri) * 0.16)
                let rc = col(at: 0.55 + Double(ri)*0.04)
                let a = Float(1.0 - Double(ri)*0.10)
                addEllipse(cx, cy, pr*rf, pr*0.38, (rc.r*a, rc.g*a, rc.b*a), w*0.7, 140)
            }
            // Companion moons — one per symmetry sector
            let mAng = Float(rng.nextDouble()) * sectorAngle
            forSym(rx: cos(mAng)*pr*3.0, ry: sin(mAng)*pr*3.0) { mx, my in
                drawPlanet(mx, my, pr*0.14, 0.65, 0.7)
            }

        } else if level < 0.54 {
            // ── Level 4: Solar system ──
            let sunR = R * 0.12
            let sunC = col(at: 0.13)
            for i in 0...7 {
                let a = Float(1.0 - Double(i)*0.11)
                addCircle(cx, cy, sunR*(1.0+Float(i)*0.4), (sunC.r*a, sunC.g*a, sunC.b*a), w*Float(3.5-Double(i)*0.35), 120)
            }
            // Solar flares replicated by symmetry
            let flareCount = max(2, 6 / max(1, symmetry))
            for f in 0..<flareCount {
                let fa = Float(f) * sectorAngle / Float(flareCount) + Float(rng.nextDouble()*0.3)
                let fr = sunR * Float(1.4 + rng.nextDouble() * 0.8)
                forSym(rx: cos(fa)*fr, ry: sin(fa)*fr) { x1, y1 in
                    buffer.addLine(x0: cx + cos(fa)*sunR*1.3, y0: cy + sin(fa)*sunR*1.3,
                                   x1: x1, y1: y1, color: col(at: 0.1), weight: w*0.8)
                }
            }
            let planetData: [(Double, Double, Double)] = [
                (0.18, 0.028, 0.25), (0.27, 0.045, 0.45), (0.38, 0.060, 0.60),
                (0.50, 0.055, 0.75), (0.63, 0.100, 0.12), (0.76, 0.080, 0.35),
                (0.88, 0.065, 0.55), (0.97, 0.045, 0.70)
            ]
            for (orbitFrac, sizeFrac, ct) in planetData {
                let orbitR = R * Float(orbitFrac)
                addCircle(cx, cy, orbitR, col(at: ct*0.4+0.48), w*0.22, 90)
                let pAng = Float(rng.nextDouble()) * sectorAngle
                let rx = cos(pAng)*orbitR, ry = sin(pAng)*orbitR
                let pr = R * Float(sizeFrac)
                forSym(rx: rx, ry: ry) { px, py in
                    drawPlanet(px, py, pr, ct, 0.9)
                    if orbitFrac > 0.60 && orbitFrac < 0.65 {
                        for ri in 0...3 {
                            addEllipse(px, py, pr*Float(1.5+Double(ri)*0.15), pr*0.35,
                                       col(at: ct+0.12), w*0.5, 80)
                        }
                    }
                }
            }

        } else if level < 0.67 {
            // ── Level 5: Milky Way spiral galaxy ──
            // Spiral arms naturally wrap the full circle — multiply arm count by symmetry
            // so each symmetry sector gets the same arm density
            let armCount = (2 + Int(complexity * 3)) * max(1, symmetry)
            let stepsPerArm = Int(280 + complexity * 420)
            for i in 0...8 {
                let br = R * Float(0.04 + Double(i)*0.038)
                let a = Float(1.0 - Double(i)*0.10)
                let bc = col(at: 0.33 + Double(i)*0.01)
                addCircle(cx, cy, br, (bc.r*a, bc.g*a, bc.b*a), w*Float(3.5-Double(i)*0.3), 70)
            }
            addEllipse(cx, cy, R*0.30, R*0.06, col(at: 0.28), w*0.5, 80)
            for arm in 0..<armCount {
                let armOff = Float(arm) * .pi * 2.0 / Float(armCount)
                let ac = col(at: Double(arm)/Double(armCount) * 0.7 + 0.05)
                for s in 0..<stepsPerArm {
                    let t = Float(s) / Float(stepsPerArm)
                    let logR = R * 0.94 * pow(t, 0.52)
                    let theta = t * .pi * 5.5 + armOff
                    let scatter = Float(rng.nextDouble()*0.08 - 0.04) * logR
                    let x0 = cx + cos(theta)*(logR+scatter), y0 = cy + sin(theta)*(logR+scatter)
                    let t2 = t + 1.0/Float(stepsPerArm)
                    let logR2 = R * 0.94 * pow(t2, 0.52)
                    let theta2 = t2 * .pi * 5.5 + armOff
                    let x1 = cx + cos(theta2)*logR2, y1 = cy + sin(theta2)*logR2
                    let alpha = t * 0.88 + 0.12
                    buffer.addLine(x0: x0, y0: y0, x1: x1, y1: y1,
                                   color: (ac.r*alpha, ac.g*alpha, ac.b*alpha),
                                   weight: w * (2.0 - t * 1.1))
                }
                for _ in 0..<Int(3 + complexity*6) {
                    let t = Float(0.1 + rng.nextDouble() * 0.85)
                    let lr = R * 0.94 * pow(t, 0.52)
                    let th = t * .pi * 5.5 + armOff
                    let kx = cx + cos(th)*lr, ky = cy + sin(th)*lr
                    addCircle(kx, ky, R*Float(0.008+rng.nextDouble()*0.018),
                              col(at: Double(arm)/Double(armCount)*0.7+0.05), w*2.0, 20)
                }
            }

        } else if level < 0.82 {
            // ── Level 6: Multiple galaxies ──
            let count = max(symmetry, 5 + Int(density * 10))
            for g in 0..<(count / max(1, symmetry)) {
                let gt = Double(g) / Double(count / max(1, symmetry))
                let gAng = Float(gt) * sectorAngle + Float(rng.nextDouble()*0.3)
                let gDist = R * Float(0.15 + rng.nextDouble() * 0.68)
                let rx = cos(gAng)*gDist, ry = sin(gAng)*gDist
                let gR = R * Float(0.06 + rng.nextDouble() * 0.16)
                let arms = 2 + Int(rng.nextDouble() * 3)
                let gc = col(at: gt)
                forSym(rx: rx, ry: ry) { gx, gy in
                    drawMiniGalaxy(gx, gy, gR, gt, arms)
                    // Tidal stream toward center
                    buffer.addLine(x0: gx, y0: gy, x1: cx, y1: cy,
                                   color: (gc.r*0.2, gc.g*0.2, gc.b*0.2), weight: w*0.2)
                }
            }
            for ri in 0..<4 {
                let rr = R * Float(0.2 + Double(ri) * 0.22)
                let rc = col(at: Double(ri)*0.15 + 0.4)
                addCircle(cx, cy, rr, (rc.r*0.3, rc.g*0.3, rc.b*0.3), w*0.25, 60)
            }

        } else {
            // ── Level 7: The Universe — cosmic web ──
            let nodeCount = Int(16 + density * 55)
            // Generate nodes in one sector, replicate via symmetry
            var relNodes: [(Float, Float)] = []
            for _ in 0..<(nodeCount / max(1, symmetry) + 1) {
                let ang = Float(rng.nextDouble()) * sectorAngle
                let dist = Float(sqrt(rng.nextDouble())) * R * 0.92
                relNodes.append((cos(ang)*dist, sin(ang)*dist))
            }
            // Expand to all symmetry copies
            var nodes: [(Float, Float)] = []
            for (rx, ry) in relNodes {
                for s in 0..<symmetry {
                    let a = Float(s) * .pi * 2.0 / Float(symmetry)
                    let ca = cos(a), sa = sin(a)
                    nodes.append((cx + rx*ca - ry*sa, cy + rx*sa + ry*ca))
                }
            }
            let connectDist = R * 0.42
            for i in 0..<nodes.count {
                for j in (i+1)..<nodes.count {
                    let dx = nodes[i].0 - nodes[j].0, dy = nodes[i].1 - nodes[j].1
                    let d = sqrt(dx*dx + dy*dy)
                    if d < connectDist {
                        let alpha = Float(1.0 - Double(d/connectDist)) * 0.75
                        let tc = Double(i + j) / Double(nodes.count * 2)
                        let fc = col(at: tc)
                        buffer.addLine(x0: nodes[i].0, y0: nodes[i].1,
                                       x1: nodes[j].0, y1: nodes[j].1,
                                       color: (fc.r*alpha, fc.g*alpha, fc.b*alpha), weight: w*alpha*1.5)
                    }
                }
                let nc = col(at: Double(i) / Double(nodes.count) * 0.65 + 0.12)
                let cr = R * Float(0.025 + rng.nextDouble() * 0.06)
                for ri in 0...3 {
                    let a = Float(1.0 - Double(ri)*0.22)
                    addCircle(nodes[i].0, nodes[i].1, cr*Float(1.0+Double(ri)*0.55),
                              (nc.r*a, nc.g*a, nc.b*a), w*Float(2.5-Double(ri)*0.5), 30)
                }
                if cr > R * 0.04 {
                    drawMiniGalaxy(nodes[i].0, nodes[i].1, cr*0.8,
                                   Double(i)/Double(nodes.count)*0.65+0.12, 2)
                }
            }
            let walls = 4 + Int(complexity * 5)
            for ri in 0..<walls {
                let rr = R * Float(0.08 + Double(ri) * 0.84 / Double(walls))
                let rc = col(at: Double(ri)/Double(walls)*0.45 + 0.48)
                addCircle(cx, cy, rr, (rc.r*0.4, rc.g*0.4, rc.b*0.4), w*0.3, 70)
            }
            for i in 0...5 {
                let bc = col(at: 0.2 + Double(i)*0.03)
                let a = Float(1.0 - Double(i)*0.15)
                addCircle(cx, cy, R*Float(0.04+Double(i)*0.03), (bc.r*a, bc.g*a, bc.b*a), w*Float(3.0-Double(i)*0.4), 50)
            }
        }
    }

    // MARK: - Symbols Layer

    private static func drawSymbolsLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                          radius: Double, params: MandalaParameters,
                                          palette: ColorPalette, rng: inout SeededRNG,
                                          colorOffset: Double, layerCount: Int, symmetry: Int) {
        let R = Float(radius)
        let density = params.density
        let complexity = params.complexity
        let baseW = max(0.5, Float(0.6 + complexity * 1.1) * R / 900.0)

        func col(at t: Double) -> (r: Float, g: Float, b: Float) {
            let c = palette.color(at: (t + colorOffset).truncatingRemainder(dividingBy: 1.0))
            return (Float(c.redComponent), Float(c.greenComponent), Float(c.blueComponent))
        }
        func drawLines(_ pts: [(Float, Float)], bx: Float, by: Float, scale: Float,
                       rotation: Float, color: (r: Float, g: Float, b: Float), weight: Float) {
            guard pts.count >= 2 else { return }
            let ca = cos(rotation), sa = sin(rotation)
            func tr(_ p: (Float, Float)) -> (Float, Float) {
                return (bx + (p.0*ca - p.1*sa)*scale, by + (p.0*sa + p.1*ca)*scale)
            }
            for i in 1..<pts.count {
                let p0 = tr(pts[i-1]), p1 = tr(pts[i])
                buffer.addLine(x0: p0.0, y0: p0.1, x1: p1.0, y1: p1.1, color: color, weight: weight)
            }
        }
        func drawClosed(_ pts: [(Float, Float)], bx: Float, by: Float, scale: Float,
                        rotation: Float, color: (r: Float, g: Float, b: Float), weight: Float) {
            guard pts.count >= 2 else { return }
            var all = pts; all.append(pts[0])
            drawLines(all, bx: bx, by: by, scale: scale, rotation: rotation, color: color, weight: weight)
        }
        func circlePts(_ steps: Int, _ r: Float = 1.0) -> [(Float, Float)] {
            let s = Float.pi * 2.0 / Float(steps)
            return (0..<steps).map { (cos(Float($0)*s)*r, sin(Float($0)*s)*r) }
        }
        func arcPts(_ start: Float, _ end: Float, _ steps: Int, _ r: Float = 1.0) -> [(Float, Float)] {
            let range = end - start
            return (0...steps).map { i in
                let t = start + Float(i)/Float(steps) * range
                return (cos(t)*r, sin(t)*r)
            }
        }

        // ── Symbol library (20 symbols, normalized to ~radius 1.0) ──

        func heart() -> [(Float, Float)] {
            return (0...100).map { i in
                let t = Float(i)/100.0 * .pi * 2
                return (pow(sin(t),3), -(0.8125*cos(t) - 0.3125*cos(2*t) - 0.125*cos(3*t) - 0.0625*cos(4*t)))
            }
        }
        func peace() -> [[(Float, Float)]] {
            return [circlePts(80),
                    [(0,-1),(0,0)],
                    [(0,0),(-0.866,0.5)],
                    [(0,0),(0.866,0.5)]]
        }
        func infinity() -> [(Float, Float)] {
            return (0...120).map { i in
                let t = Float(i)/120.0 * .pi * 2
                let d = 1.0 + sin(t)*sin(t)
                return (cos(t)/d, sin(t)*cos(t)/d)
            }
        }
        func eyeOfProvidence() -> [[(Float, Float)]] {
            let tri: [(Float, Float)] = [(-0.866,0.5),(0.866,0.5),(0,-1.0)]
            let eye = (0..<60).map { i -> (Float, Float) in
                let t = Float(i)/59.0 * .pi * 2
                return (cos(t)*0.32, sin(t)*0.16 - 0.08)
            }
            let pupil = circlePts(16, 0.11).map { ($0.0, $0.1 - 0.08) }
            return [tri, eye, pupil]
        }
        func yinYang() -> [[(Float, Float)]] {
            let outer = circlePts(100)
            let topS = (0..<40).map { i -> (Float, Float) in
                let t = Float(i)/39.0 * .pi; return (sin(t)*0.5, cos(t)*0.5)
            }
            let botS = (0..<40).map { i -> (Float, Float) in
                let t = Float(i)/39.0 * .pi; return (-sin(t)*0.5, -cos(t)*0.5)
            }
            let dotA = circlePts(20, 0.25).map { ($0.0, $0.1 + 0.5) }
            let dotB = circlePts(20, 0.25).map { ($0.0, $0.1 - 0.5) }
            return [outer, topS, botS, dotA, dotB]
        }
        func crescent() -> [(Float, Float)] {
            return circlePts(80) + arcPts(-Float.pi*0.62, Float.pi*0.62, 50, 0.84).map { ($0.0+0.32, $0.1) }
        }
        func starOfDavid() -> [[(Float, Float)]] {
            return [[(0,-1),(0.866,0.5),(-0.866,0.5)],
                    [(0,1),(0.866,-0.5),(-0.866,-0.5)]]
        }
        func pentagram() -> [(Float, Float)] {
            let v = (0..<5).map { i -> (Float, Float) in
                let t = Float(i) * .pi * 2.0/5.0 - .pi/2.0
                return (cos(t), sin(t))
            }
            return [v[0],v[2],v[4],v[1],v[3]]
        }
        func ankh() -> [[(Float, Float)]] {
            let oval = (0..<60).map { i -> (Float, Float) in
                let t = Float(i)/60.0 * .pi * 2; return (cos(t)*0.42, sin(t)*0.46 - 0.54)
            }
            return [[(0,-0.18),(0,1.0)],[(-0.62,0.16),(0.62,0.16)],oval]
        }
        func om() -> [[(Float, Float)]] {
            let body = (0..<55).map { i -> (Float, Float) in
                let t = Float.pi*0.1 + Float(i)/54.0 * Float.pi*1.3
                return (cos(t)*0.72, sin(t)*0.56)
            }
            let curl = (0..<32).map { i -> (Float, Float) in
                let t = -Float.pi*0.2 + Float(i)/31.0 * Float.pi*1.4
                return (cos(t)*0.36 + 0.2, sin(t)*0.30 + 0.36)
            }
            let tail = (0..<28).map { i -> (Float, Float) in
                let t = Float(i)/27.0 * Float.pi
                return (cos(t)*0.26 - 0.36, -sin(t)*0.32 - 0.54)
            }
            let dot = circlePts(14, 0.06).map { ($0.0, $0.1 - 0.80) }
            return [body, curl, tail, [(-0.16,-0.88),(0.16,-0.88)], dot]
        }
        func chakra(petals: Int) -> [[(Float, Float)]] {
            var p: [[(Float, Float)]] = [circlePts(50, 0.42), circlePts(50, 1.0), circlePts(50, 1.45)]
            for i in 0..<petals {
                let a = Float(i) * .pi * 2.0/Float(petals)
                let petal = (0..<22).map { j -> (Float, Float) in
                    let t = Float(j)/21.0 * .pi
                    let r = sin(t)*0.42
                    let pa = a + (t - .pi/2)*0.42
                    return (cos(pa)*(1.0+r), sin(pa)*(1.0+r))
                }
                p.append(petal)
            }
            return p
        }
        func flowerOfLife() -> [[(Float, Float)]] {
            var parts: [[(Float, Float)]] = []
            var centers: [(Float, Float)] = [(0,0)]
            for i in 0..<6 {
                let a = Float(i) * .pi/3.0
                centers.append((cos(a), sin(a)))
            }
            for center in centers {
                parts.append((0..<40).map { i in
                    let t = Float(i)/39.0 * .pi * 2
                    return (cos(t)*0.52 + center.0, sin(t)*0.52 + center.1)
                })
            }
            return parts
        }
        // Dharma wheel: hub + 8 spokes + rim
        func dharmaWheel() -> [[(Float, Float)]] {
            var p: [[(Float, Float)]] = [circlePts(70), circlePts(30, 0.28)]
            for i in 0..<8 {
                let a = Float(i) * .pi/4.0
                p.append([(cos(a)*0.28, sin(a)*0.28), (cos(a)*0.95, sin(a)*0.95)])
                // Blade decorations
                let b0 = a + 0.18, b1 = a + 0.32
                p.append(arcPts(b0, b1, 8, 0.7))
            }
            return p
        }
        // Merkaba (Star Tetrahedron): two overlapping triangles at different scales
        func merkaba() -> [[(Float, Float)]] {
            let up: [(Float, Float)] = [(0,-1.0),(0.866,0.5),(-0.866,0.5)]
            let dn: [(Float, Float)] = [(0,1.0),(0.866,-0.5),(-0.866,-0.5)]
            let upS: [(Float, Float)] = [(0,-0.55),(0.476,0.275),(-0.476,0.275)]
            let dnS: [(Float, Float)] = [(0,0.55),(0.476,-0.275),(-0.476,-0.275)]
            return [up, dn, upS, dnS, circlePts(60)]
        }
        // Triquetra (Celtic trinity knot — approximated with 3 interlocked arcs)
        func triquetra() -> [[(Float, Float)]] {
            var p: [[(Float, Float)]] = []
            for k in 0..<3 {
                let base = Float(k) * .pi * 2.0/3.0
                let arc = (0...60).map { i -> (Float, Float) in
                    let t = base + Float(i)/60.0 * .pi * 4.0/3.0
                    let r = Float(0.55)
                    let ox = cos(base + .pi * 2.0/3.0) * 0.3
                    let oy = sin(base + .pi * 2.0/3.0) * 0.3
                    return (ox + cos(t)*r, oy + sin(t)*r)
                }
                p.append(arc)
            }
            p.append(circlePts(50, 1.0))
            return p
        }
        // Ouroboros: snake eating its tail — circle with a slight gap and head
        func ouroboros() -> [[(Float, Float)]] {
            let body = (0...90).map { i -> (Float, Float) in
                let t = Float(i)/90.0 * .pi * 1.85
                return (cos(t), sin(t))
            }
            // Head
            let headPts: [(Float, Float)] = [(1.0, 0.0),(1.15,-0.1),(1.0,-0.18),(0.85,-0.1),(1.0,0.0)]
            return [body, headPts]
        }
        // Triskelion: three spiraling arms
        func triskelion() -> [[(Float, Float)]] {
            var p: [[(Float, Float)]] = []
            for k in 0..<3 {
                let offset = Float(k) * .pi * 2.0/3.0
                let arm = (0...50).map { i -> (Float, Float) in
                    let t = Float(i)/50.0
                    let r = t * 0.85
                    let theta = t * .pi * 2.5 + offset
                    return (cos(theta)*r, sin(theta)*r)
                }
                p.append(arm)
            }
            p.append(circlePts(30, 0.12))
            return p
        }
        // Vesica Piscis: two overlapping circles
        func vesicaPiscis() -> [[(Float, Float)]] {
            return [circlePts(70).map { ($0.0 - 0.5, $0.1) },
                    circlePts(70).map { ($0.0 + 0.5, $0.1) }]
        }
        // Hamsa: hand outline + eye in palm (stylized)
        func hamsa() -> [[(Float, Float)]] {
            // Palm circle + 5 finger lines
            let palm = circlePts(50, 0.55)
            var p: [[(Float, Float)]] = [palm]
            let fingers: [(Float, Double)] = [
                (0.0, -1.0), (-0.28, -0.96), (-0.52, -0.8), (0.28, -0.96), (0.52, -0.8)
            ]
            for (fx, fy) in fingers {
                p.append([(fx, Float(fy)*0.55), (fx, Float(fy))])
            }
            p.append(circlePts(20, 0.22))  // eye outer
            p.append(circlePts(12, 0.10))  // pupil
            return p
        }
        // Sri Yantra (simplified): nested triangles + circles
        func sriYantra() -> [[(Float, Float)]] {
            var p: [[(Float, Float)]] = []
            p.append(circlePts(70, 1.0))
            p.append(circlePts(60, 0.85))
            // 4 upward triangles
            for i in 0..<4 {
                let s = Float(1.0 - Double(i) * 0.18)
                p.append([(0,-s),(s*0.866,s*0.5),(-s*0.866,s*0.5)])
            }
            // 5 downward triangles
            for i in 0..<5 {
                let s = Float(0.9 - Double(i) * 0.15)
                p.append([(0,s),(s*0.866,-s*0.5),(-s*0.866,-s*0.5)])
            }
            p.append(circlePts(12, 0.08))  // bindu dot
            return p
        }
        // Eye of Horus (stylized)
        func eyeOfHorus() -> [[(Float, Float)]] {
            let eyeOuter = arcPts(Float.pi*1.1, Float.pi*2.0 - Float.pi*0.1, 50, 1.0)
            let eyeLid   = arcPts(Float.pi*0.1, Float.pi*0.9, 50, 1.0)
            let pupil    = circlePts(30, 0.35)
            let tail: [(Float, Float)] = [(1.0,0.0),(1.3,0.4),(1.0,0.7),(0.5,0.6)]
            let brow: [(Float, Float)] = [(-0.5,-0.5),(0.0,-0.8),(0.5,-0.5)]
            return [eyeOuter, eyeLid, pupil, tail, brow]
        }
        // 8-pointed star (Star of Ishtar)
        func starOfIshtar() -> [(Float, Float)] {
            var pts: [(Float, Float)] = []
            for i in 0..<16 {
                let t = Float(i) * .pi/8.0 - .pi/2.0
                let r: Float = (i % 2 == 0) ? 1.0 : 0.42
                pts.append((cos(t)*r, sin(t)*r))
            }
            return pts
        }
        // Caduceus: central staff + two spiraling curves
        func caduceus() -> [[(Float, Float)]] {
            let staff: [(Float, Float)] = [(0,-1.0),(0,1.0)]
            let snake1 = (0...60).map { i -> (Float, Float) in
                let t = Float(i)/60.0 * .pi * 3.0 - .pi*1.5
                return (sin(t)*0.4, Float(i)/60.0 * 2.0 - 1.0)
            }
            let snake2 = (0...60).map { i -> (Float, Float) in
                let t = Float(i)/60.0 * .pi * 3.0 - .pi*0.5
                return (sin(t)*0.4, Float(i)/60.0 * 2.0 - 1.0)
            }
            // Wings
            let wingL = arcPts(.pi*0.7, .pi*1.4, 20, 0.5).map { ($0.0 - 0.2, $0.1 - 0.65) }
            let wingR = arcPts(-.pi*0.4, .pi*0.3, 20, 0.5).map { ($0.0 + 0.2, $0.1 - 0.65) }
            return [staff, snake1, snake2, wingL, wingR]
        }

        // Dispatch helper
        func drawSymbol(_ type: Int, bx: Float, by: Float, scale: Float,
                        rot: Float, c: (r: Float, g: Float, b: Float), wt: Float) {
            let totalTypes = 20
            switch type % totalTypes {
            case 0:
                drawLines(heart(), bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt)
            case 1:
                for seg in peace() { drawLines(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            case 2:
                drawLines(infinity(), bx: bx, by: by, scale: scale*1.6, rotation: rot, color: c, weight: wt)
            case 3:
                for seg in eyeOfProvidence() { drawClosed(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            case 4:
                for seg in yinYang() { drawLines(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            case 5:
                drawLines(crescent(), bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt)
            case 6:
                for seg in starOfDavid() { drawClosed(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            case 7:
                drawClosed(pentagram(), bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt)
            case 8:
                for seg in ankh() { drawLines(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            case 9:
                for seg in om() { drawLines(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            case 10:
                let petals = [4,6,8,10,12,16][min(5, Int(complexity*6))]
                for seg in chakra(petals: petals) { drawLines(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            case 11:
                for seg in flowerOfLife() { drawLines(seg, bx: bx, by: by, scale: scale*0.52, rotation: rot, color: c, weight: wt) }
            case 12:
                for seg in dharmaWheel() { drawLines(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            case 13:
                for seg in merkaba() { drawClosed(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            case 14:
                for seg in triquetra() { drawLines(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            case 15:
                for seg in ouroboros() { drawLines(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            case 16:
                for seg in triskelion() { drawLines(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            case 17:
                for seg in vesicaPiscis() { drawLines(seg, bx: bx, by: by, scale: scale*0.75, rotation: rot, color: c, weight: wt) }
            case 18:
                for seg in hamsa() { drawLines(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            case 19:
                for seg in sriYantra() { drawClosed(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            case 20:
                for seg in eyeOfHorus() { drawLines(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            case 21:
                drawClosed(starOfIshtar(), bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt)
            default:
                for seg in caduceus() { drawLines(seg, bx: bx, by: by, scale: scale, rotation: rot, color: c, weight: wt) }
            }
        }

        // Seed → base symbol type + per-ring variation
        let baseType = Int(rng.next() % 20)
        let ringCount = max(1, Int(density * 5.0) + 1)
        let symbolScale = R * Float(0.07 + (1.0 - density) * 0.14)

        for ring in 0..<ringCount {
            let ringFrac = ringCount > 1 ? Double(ring) / Double(ringCount - 1) : 0.5
            let ringRadius = R * Float(0.14 + ringFrac * 0.78)
            let countInRing = max(symmetry, (Int(density * 9) + symmetry) / symmetry * symmetry)
            let scale = symbolScale * Float(1.0 - ringFrac * 0.32)
            let ringColorT = ringFrac * 0.72 + Double(ring) * 0.09
            // Each ring gets its own symbol type for more variety
            let ringType = (baseType + ring * 3) % 20

            for si in 0..<countInRing {
                let frac = Double(si) / Double(countInRing)
                let angle = Float(frac) * .pi * 2
                let sx = cx + cos(angle)*ringRadius, sy = cy + sin(angle)*ringRadius
                let rot = angle + .pi/2
                let ct = (ringColorT + frac * params.colorDrift).truncatingRemainder(dividingBy: 1.0)
                let c = col(at: ct)
                let wt = baseW * Float(0.85 + complexity * 0.75)
                drawSymbol(ringType, bx: sx, by: sy, scale: scale, rot: rot, c: c, wt: wt)

                // Glow rings around each symbol
                if complexity > 0.35 {
                    let rc = col(at: (ct + 0.14).truncatingRemainder(dividingBy: 1.0))
                    let circ = circlePts(44)
                    drawClosed(circ, bx: sx, by: sy, scale: scale*1.3, rotation: 0,
                               color: (rc.r*0.55, rc.g*0.55, rc.b*0.55), weight: wt*0.4)
                    if complexity > 0.65 {
                        drawClosed(circ, bx: sx, by: sy, scale: scale*1.65, rotation: 0,
                                   color: (rc.r*0.28, rc.g*0.28, rc.b*0.28), weight: wt*0.28)
                    }
                }
            }
        }

        // Center symbol — larger, different type from rings
        let centerType = (baseType + 7) % 20
        let centerScale = R * Float(0.21 + complexity * 0.13)
        let centerC = col(at: 0.5)
        drawSymbol(centerType, bx: cx, by: cy, scale: centerScale, rot: 0, c: centerC, wt: baseW * 2.2)
        // Second concentric symbol at smaller scale
        let innerC = col(at: 0.25)
        drawSymbol((centerType + 5) % 20, bx: cx, by: cy, scale: centerScale * 0.5, rot: Float.pi/6, c: innerC, wt: baseW * 1.4)
        // Decorative rings
        for ri in 0..<(2 + Int(complexity*3)) {
            let rr = centerScale * Float(1.3 + Double(ri)*0.4)
            let rc = col(at: (0.5 + Double(ri)*0.12).truncatingRemainder(dividingBy: 1.0))
            drawClosed(circlePts(60), bx: cx, by: cy, scale: rr, rotation: 0,
                       color: (rc.r*0.5, rc.g*0.5, rc.b*0.5), weight: baseW*0.5)
        }
    }

    // MARK: - Strange Attractors (Clifford / De Jong)

    private static func drawStrangeAttractorLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                                    radius: Double, params: MandalaParameters,
                                                    palette: ColorPalette, rng: inout SeededRNG,
                                                    layerCount: Int, symmetry: Int,
                                                    colorOffset: Double) {
        let R = Float(radius)

        // (type, a, b, c, d)
        // type 0 = Clifford:  x'=sin(a·y)+c·cos(a·x),  y'=sin(b·x)+d·cos(b·y)
        // type 1 = De Jong:   x'=sin(a·y)-cos(b·x),    y'=sin(c·x)-cos(d·y)
        // type 2 = Bedhead:   x'=sin(x·y/b)·y+cos(a·x-y), y'=x+sin(y)/b  (c,d unused)
        // type 3 = Hopalong:  x'=y-sign(x)·√|b·x-c|,   y'=a-x           (d unused)
        let presets: [(Int, Double, Double, Double, Double)] = [
            // Clifford — wide variety of flow patterns
            (0, -1.4,  1.6,  1.0,  0.7),
            (0, -1.7,  1.3, -0.1, -1.21),
            (0,  1.5, -1.8,  1.6,  0.9),
            (0, -1.8, -2.0, -0.5, -0.9),
            (0,  1.7,  1.7,  0.6,  1.2),
            (0, -1.3, -1.3, -1.8, -1.9),
            (0, -1.5, -1.8,  1.6,  0.9),
            (0,  1.6, -0.6, -1.0,  0.7),
            (0, -2.0,  0.4, -1.1, -0.9),
            (0,  0.8, -1.9,  1.0,  1.5),
            (0, -1.1,  2.0, -1.6, -0.7),
            (0,  1.4,  1.4,  1.1, -1.7),
            // De Jong — more intricate filament structures
            (1, -2.0, -2.0, -1.2,  2.0),
            (1,  1.4, -2.3,  2.4, -2.1),
            (1, -0.8, -2.4, -0.7,  2.0),
            (1,  2.0, -1.0, -0.5,  2.0),
            (1, -1.5,  2.0, -1.0,  1.5),
            (1, -2.4,  2.4, -1.6,  1.0),
            (1, -0.7,  0.5, -1.4,  2.1),
            (1,  0.3, -1.5,  1.2, -1.9),
            (1, -1.8,  1.8,  0.9, -1.5),
            (1,  2.3, -2.3,  2.0, -0.8),
            // Bedhead — tangled organic loops
            (2,  0.64, 0.76, 0,    0   ),
            (2,  0.85, 0.92, 0,    0   ),
            (2,  0.50, 0.80, 0,    0   ),
            (2,  1.20, 0.60, 0,    0   ),
            // Hopalong — spiky radial flower forms
            (3,  0.4,  1.0,  0.0,  0   ),
            (3,  1.1,  0.5,  1.0,  0   ),
            (3, -0.5,  1.5,  0.2,  0   ),
            (3,  0.7,  1.2, -0.3,  0   ),
        ]

        // Precompute colour LUT — avoids per-point NSColor allocation
        let nColors = 2048
        let colorLUT: [(Float, Float, Float)] = (0..<nColors).map { i in
            let t = (Double(i) / Double(nColors) + colorOffset).truncatingRemainder(dividingBy: 1.0)
            let c = palette.color(at: t < 0 ? t + 1 : t)
            return (Float(c.redComponent), Float(c.greenComponent), Float(c.blueComponent))
        }

        let iters  = Int(200_000 + params.density * 600_000)
        let bright = Float(params.density * 1.2 + 0.5) / Float(max(1, symmetry))

        for li in 0..<layerCount {
            let pIdx = (li + Int(params.seed % 13)) % presets.count
            let (type, a, b, c, d) = presets[pIdx]

            // Step function for the chosen attractor type
            func step(_ x: Double, _ y: Double) -> (Double, Double) {
                switch type {
                case 0: return (sin(a*y) + c*cos(a*x), sin(b*x) + d*cos(b*y))
                case 1: return (sin(a*y) - cos(b*x),   sin(c*x) - cos(d*y))
                case 2: // Bedhead
                    let nx = sin(x*y/b)*y + cos(a*x - y)
                    let ny = x + sin(y)/b
                    return (nx, ny)
                default: // Hopalong
                    let nx = y - (x >= 0 ? 1 : -1) * sqrt(abs(b*x - c))
                    let ny = a - x
                    return (nx, ny)
                }
            }

            // Warmup: find bounding box of the attractor
            var x = 0.1 + Double(li) * 0.07, y = 0.13 + Double(li) * 0.05
            var minX = x, maxX = x, minY = y, maxY = y
            for _ in 0..<3000 {
                let (nx, ny) = step(x, y)
                x = nx; y = ny
                // Bail if diverging
                guard x.isFinite && y.isFinite && abs(x) < 1e6 && abs(y) < 1e6 else { break }
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
            }
            let rangeX = maxX - minX, rangeY = maxY - minY
            guard rangeX > 0.001, rangeY > 0.001 else { continue }
            let scale  = min(Double(R) * 1.98 / rangeX, Double(R) * 1.98 / rangeY)
            let offX   = -(minX + maxX) * 0.5 * scale
            let offY   = -(minY + maxY) * 0.5 * scale

            let weight = Float(rng.nextDouble(in: 0.25...0.7)) * bright
            let tShift = rng.nextDouble()

            for i in 0..<iters {
                let (nx, ny) = step(x, y)
                x = nx; y = ny
                guard x.isFinite && y.isFinite else { break }

                let px = Float(x * scale + offX)
                let py = Float(y * scale + offY)

                // Colour by angle from origin
                let angle01 = Double(atan2(py, px)) / (.pi * 2.0) + 0.5
                let colorIdx = Int((angle01 + tShift + params.colorDrift * Double(i) / Double(iters))
                    .truncatingRemainder(dividingBy: 1.0) * Double(nColors)) % nColors
                let col = colorLUT[max(0, min(nColors - 1, colorIdx))]

                for s in 0..<symmetry {
                    let ang  = Float(s) * .pi * 2.0 / Float(symmetry)
                    let cosA = cos(ang), sinA = sin(ang)
                    buffer.addPixel(x: Int(cx + px * cosA - py * sinA),
                                    y: Int(cy + px * sinA + py * cosA),
                                    color: col, weight: weight)
                }
            }
        }
    }

    // MARK: - Superformula (Gielis)

    /// Gielis' generalisation of the circle — one formula produces triangles, stars,
    /// flowers, leaves, astroids, and organic blobs depending on (m, n1, n2, n3).
    private static func collectSuperformulaTasks(into tasks: inout [CurveDrawTask],
                                                 cx: Float, cy: Float, radius: Double,
                                                 params: MandalaParameters, rng: inout SeededRNG,
                                                 layerCount: Int, symmetry: Int,
                                                 rippleAmount: Float, weightMul: Float) {
        // (m, n1, n2, n3) — covers the full range from sharp stars to smooth blobs
        // n1 small → spiky/star, n1 large → polygon, n1~1 → astroid/organic
        // n2≠n3 → asymmetric shapes
        let presets: [(Double, Double, Double, Double)] = [
            (5,  0.25, 0.25, 0.25),  // sharp 5-pointed star
            (4,  0.25, 0.25, 0.25),  // sharp 4-pointed star
            (3,  0.25, 0.25, 0.25),  // sharp 3-pointed star
            (8,  0.25, 0.25, 0.25),  // sharp 8-pointed star
            (6,  0.3,  0.3,  0.3 ),  // sharp snowflake
            (5,  1,    1,    1   ),   // 5-pointed astroid
            (3,  1,    1,    1   ),   // 3-pointed astroid (tricorn)
            (4,  1,    1,    1   ),   // 4-pointed astroid
            (5,  2,    7,    7   ),   // smooth 5-petal flower
            (3,  2,    7,    7   ),   // smooth 3-petal flower
            (7,  2,    7,    7   ),   // smooth 7-petal flower
            (4,  8,    8,    8   ),   // rounded square (squircle)
            (3,  8,    8,    8   ),   // rounded triangle
            (6,  8,    8,    8   ),   // rounded hexagon
            (12, 15,   15,   15  ),   // 12-sided polygon
            (2,  1,    4,    8   ),   // lens / vesica (asymmetric)
            (4,  1,    0.5,  8   ),   // elongated cross (asymmetric)
            (6,  1,    0.5,  0.3 ),   // organic leaf
            (7,  3,    4,    17  ),   // 7-petal irregular
            (5,  0.5,  0.5,  4   ),   // 5-spike with wide base
            (4,  2,    2,    2   ),   // intermediate 4 shape
            (10, 0.3,  0.3,  0.3 ),   // 10-spike star
        ]
        let steps = 2000

        for li in 0..<layerCount {
            let preset  = presets[(li + Int(params.seed % 19)) % presets.count]
            let mG      = preset.0
            let n1      = preset.1   // no randomization — keep canonical shape
            let n2      = preset.2
            let n3      = preset.3
            let curveR  = radius * (0.2 + rng.nextDouble() * 0.8)

            var xs = [Float](repeating: 0, count: steps)
            var ys = [Float](repeating: 0, count: steps)
            var valid = true

            for j in 0..<steps {
                let theta = Double(j) / Double(steps) * .pi * 2.0
                let t1    = pow(abs(cos(mG * theta / 4.0)), n2)
                let t2    = pow(abs(sin(mG * theta / 4.0)), n3)
                let sum   = t1 + t2
                guard sum > 1e-10 else { valid = false; break }
                let r = pow(sum, -1.0 / n1)
                guard r.isFinite && r < 50.0 else { valid = false; break }
                xs[j] = Float(curveR * r * cos(theta))
                ys[j] = Float(curveR * r * sin(theta))
            }
            guard valid else { continue }

            // Normalize if curve extends beyond the allowed radius
            var maxR: Float = 0
            for j in 0..<steps { maxR = max(maxR, hypot(xs[j], ys[j])) }
            if maxR > Float(radius) * 1.02 {
                let scale = Float(radius) * 0.92 / maxR
                for j in 0..<steps { xs[j] *= scale; ys[j] *= scale }
            }

            let tOffset   = rng.nextDouble()
            let densityMul = Float(params.density * 1.6 + 0.3)
            let weight    = Float(rng.nextDouble(in: 0.5...1.8)) * Float(params.complexity) * weightMul * densityMul
            let thickness = Int(rng.nextDouble(in: 1...3))
            let ripSeed   = params.seed &+ UInt64(li * 41 + 3)

            for sym in 0..<symmetry {
                let angle = Double(sym) * .pi * 2.0 / Double(symmetry)
                let cosA  = Float(cos(angle)), sinA = Float(sin(angle))
                var rxs = [Float](repeating: 0, count: steps)
                var rys = [Float](repeating: 0, count: steps)
                for j in 0..<steps {
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

    // MARK: - Hyperboloid (Ruled Surface — 3D)

    /// A hyperboloid of one sheet rendered via its two families of ruling lines,
    /// projected at a tilt angle so the 3D waist shape is clearly visible.
    /// Depth shading (front = bright, back = dim) reinforces the 3D illusion.
    private static func drawHyperboloidLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                              radius: Double, params: MandalaParameters,
                                              palette: ColorPalette, rng: inout SeededRNG,
                                              layerCount: Int, symmetry: Int,
                                              colorOffset: Double) {
        let R = Float(radius) * 1.162

        // Precompute colour LUT
        let nColors = 1024
        let colorLUT: [(Float, Float, Float)] = (0..<nColors).map { i in
            let t = (Double(i) / Double(nColors) + colorOffset).truncatingRemainder(dividingBy: 1.0)
            let c = palette.color(at: t < 0 ? t + 1 : t)
            return (Float(c.redComponent), Float(c.greenComponent), Float(c.blueComponent))
        }

        // Tilt the hyperboloid so we see it at roughly 35–50° from above
        let tiltBase: Float = .pi / 5.0
        let tilt = tiltBase + Float(rng.nextDouble() * 0.3)
        let cosTilt = cos(tilt), sinTilt = sin(tilt)

        // Project 3D (x3, y3, z3) → screen (sx, sy) + depth for shading
        func proj(_ x3: Float, _ y3: Float, _ z3: Float) -> (Float, Float, Float) {
            let sy    = y3 * cosTilt - z3 * sinTilt
            let depth = y3 * sinTilt + z3 * cosTilt
            return (x3, sy, depth)
        }

        let nLines = 30 + Int(params.density * 50)   // 30–80 ruling lines per family
        let steps  = 24                               // segments per ruling line

        for _ in 0..<layerCount {
            let rimR   = R * Float(0.35 + rng.nextDouble() * 0.55)
            let height = rimR * Float(0.5 + rng.nextDouble() * 0.8)
            // twist angle controls waist tightness: π/3 = mild, 2π/3 = very tight waist
            let twist  = Float.pi * Float(0.35 + rng.nextDouble() * 0.45)
            let baseW  = Float(rng.nextDouble(in: 0.6...1.6)) * Float(params.complexity)
            let tShift = rng.nextDouble()

            // Draw both ruling families (+twist and −twist)
            for family in 0..<2 {
                let sign: Float = family == 0 ? 1 : -1

                for line in 0..<nLines {
                    let phi    = Float(line) / Float(nLines) * .pi * 2.0
                    let topPhi = phi
                    let botPhi = phi + sign * twist

                    let tx = rimR * cos(topPhi), tz = rimR * sin(topPhi)
                    let bx = rimR * cos(botPhi), bz = rimR * sin(botPhi)

                    var prevSX: Float = 0, prevSY: Float = 0, prevDepth: Float = 0

                    for j in 0...steps {
                        let t  = Float(j) / Float(steps)
                        let x3 = tx + t * (bx - tx)
                        let y3 = height * (1 - 2*t)      // top → bottom
                        let z3 = tz + t * (bz - tz)

                        let (sx, sy, depth) = proj(x3, y3, z3)

                        if j > 0 {
                            // Depth factor: front half bright, back half dim
                            let df = max(0.08, min(1.0, 0.5 + depth / rimR * 0.55))
                            let w  = baseW * df

                            // Colour by angle around the hyperboloid
                            let colorPhi = Double(phi / (.pi * 2.0))
                            let colorT   = (colorPhi + tShift + params.colorDrift * Double(line) / Double(nLines)).truncatingRemainder(dividingBy: 1.0)
                            let cIdx     = Int(colorT * Double(nColors)) % nColors
                            let col = colorLUT[max(0, min(nColors - 1, cIdx))]

                            // Paint with symmetry rotations
                            for s in 0..<symmetry {
                                let ang  = Float(s) * .pi * 2.0 / Float(symmetry)
                                let cosA = cos(ang), sinA = sin(ang)
                                let rx0  = cx + prevSX * cosA - prevSY * sinA
                                let ry0  = cy + prevSX * sinA + prevSY * cosA
                                let rx1  = cx + sx * cosA - sy * sinA
                                let ry1  = cy + sx * sinA + sy * cosA
                                buffer.addLine(x0: rx0, y0: ry0, x1: rx1, y1: ry1,
                                               color: col, weight: w)
                            }
                        }
                        prevSX = sx; prevSY = sy; prevDepth = depth
                    }
                    _ = prevDepth
                }
            }
        }
    }

    // MARK: - Torus (3D)

    /// A parametric torus rendered as wireframe meridian and parallel circles,
    /// tilted ~40° so the donut shape reads clearly in 2D. Depth-shaded.
    private static func drawTorusLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                        radius: Double, params: MandalaParameters,
                                        palette: ColorPalette, rng: inout SeededRNG,
                                        layerCount: Int, symmetry: Int,
                                        colorOffset: Double) {
        let R = Float(radius) * 1.2

        let nColors = 1024
        let colorLUT: [(Float, Float, Float)] = (0..<nColors).map { i in
            let t = (Double(i) / Double(nColors) + colorOffset).truncatingRemainder(dividingBy: 1.0)
            let c = palette.color(at: t < 0 ? t + 1 : t)
            return (Float(c.redComponent), Float(c.greenComponent), Float(c.blueComponent))
        }

        let tiltBase: Float = .pi * 0.222   // ~40°
        let tilt = tiltBase + Float(rng.nextDouble() * 0.25)
        let cosTilt = cos(tilt), sinTilt = sin(tilt)

        // Tilt around the X-axis: (x,y,z) → screen x = x3, screen y = y3*cos - z3*sin
        func proj(_ x3: Float, _ y3: Float, _ z3: Float) -> (Float, Float, Float) {
            let sy    = y3 * cosTilt - z3 * sinTilt
            let depth = y3 * sinTilt + z3 * cosTilt
            return (x3, sy, depth)
        }

        let nMeridians = 16 + Int(params.density * 24)   // 16–40
        let nParallels = 8  + Int(params.density * 16)   // 8–24
        let steps      = 32
        // Normalise accumulated brightness: N overlapping copies at centre → ×N overexposure.
        // Dividing by sqrt(N) keeps centre from blowing out while preserving visible lines.
        let symScale   = 1.0 / sqrt(Float(max(1, symmetry)))

        for _ in 0..<layerCount {
            // bigR = distance from torus centre to tube centre; smallR = tube radius
            let bigR   = R * Float(0.40 + rng.nextDouble() * 0.30)
            let smallR = bigR * Float(0.25 + rng.nextDouble() * 0.40)
            let baseW  = Float(rng.nextDouble(in: 0.5...1.4)) * Float(params.complexity) * symScale
            let tShift = rng.nextDouble()

            // Draw meridian circles (constant u, vary v 0…2π)
            for mi in 0..<nMeridians {
                let u    = Float(mi) / Float(nMeridians) * .pi * 2.0
                let cosU = cos(u), sinU = sin(u)
                var prevSX: Float = 0, prevSY: Float = 0, prevDepth: Float = 0
                for j in 0...steps {
                    let v    = Float(j) / Float(steps) * .pi * 2.0
                    let cosV = cos(v), sinV = sin(v)
                    let x3   = (bigR + smallR * cosV) * cosU
                    let y3   = (bigR + smallR * cosV) * sinU
                    let z3   = smallR * sinV
                    let (sx, sy, depth) = proj(x3, y3, z3)
                    if j > 0 {
                        let df   = max(0.08, min(1.0, 0.5 + depth / (bigR + smallR) * 0.55))
                        let w    = baseW * df
                        let cT   = (Double(mi) / Double(nMeridians) + tShift + params.colorDrift * Double(j) / Double(steps)).truncatingRemainder(dividingBy: 1.0)
                        let cIdx = Int(max(0.0, min(1.0, cT)) * Double(nColors - 1))
                        let col  = colorLUT[cIdx]
                        for s in 0..<symmetry {
                            let ang  = Float(s) * .pi * 2.0 / Float(symmetry)
                            let cosA = cos(ang), sinA = sin(ang)
                            let rx0  = cx + prevSX * cosA - prevSY * sinA
                            let ry0  = cy + prevSX * sinA + prevSY * cosA
                            let rx1  = cx + sx    * cosA - sy    * sinA
                            let ry1  = cy + sx    * sinA + sy    * cosA
                            buffer.addLine(x0: rx0, y0: ry0, x1: rx1, y1: ry1,
                                           color: col, weight: w)
                        }
                    }
                    prevSX = sx; prevSY = sy; prevDepth = depth
                }
                _ = prevDepth
            }

            // Draw parallel circles (constant v, vary u 0…2π)
            for pi2 in 0..<nParallels {
                let v    = Float(pi2) / Float(nParallels) * .pi * 2.0
                let cosV = cos(v), sinV = sin(v)
                var prevSX: Float = 0, prevSY: Float = 0, prevDepth: Float = 0
                for j in 0...steps {
                    let u    = Float(j) / Float(steps) * .pi * 2.0
                    let cosU = cos(u), sinU = sin(u)
                    let x3   = (bigR + smallR * cosV) * cosU
                    let y3   = (bigR + smallR * cosV) * sinU
                    let z3   = smallR * sinV
                    let (sx, sy, depth) = proj(x3, y3, z3)
                    if j > 0 {
                        let df   = max(0.08, min(1.0, 0.5 + depth / (bigR + smallR) * 0.55))
                        let w    = baseW * df * 0.6    // parallels slightly thinner
                        let cT   = (Double(pi2) / Double(nParallels) + tShift + 0.5 + params.colorDrift * Double(j) / Double(steps)).truncatingRemainder(dividingBy: 1.0)
                        let cIdx = Int(max(0.0, min(1.0, cT)) * Double(nColors - 1))
                        let col  = colorLUT[cIdx]
                        for s in 0..<symmetry {
                            let ang  = Float(s) * .pi * 2.0 / Float(symmetry)
                            let cosA = cos(ang), sinA = sin(ang)
                            let rx0  = cx + prevSX * cosA - prevSY * sinA
                            let ry0  = cy + prevSX * sinA + prevSY * cosA
                            let rx1  = cx + sx    * cosA - sy    * sinA
                            let ry1  = cy + sx    * sinA + sy    * cosA
                            buffer.addLine(x0: rx0, y0: ry0, x1: rx1, y1: ry1,
                                           color: col, weight: w)
                        }
                    }
                    prevSX = sx; prevSY = sy; prevDepth = depth
                }
                _ = prevDepth
            }
        }
    }

    // MARK: - Nautilus Shell (3D)

    /// A logarithmic spiral shell rendered as cross-section ribs (constant u) and
    /// surface spirals (constant v), projected at ~35° tilt. Depth-shaded.
    private static func drawNautilusLayers(buffer: PixelBuffer, cx: Float, cy: Float,
                                           radius: Double, params: MandalaParameters,
                                           palette: ColorPalette, rng: inout SeededRNG,
                                           layerCount: Int, symmetry: Int,
                                           colorOffset: Double) {
        let R = Float(radius) * 1.1

        let nColors = 1024
        let colorLUT: [(Float, Float, Float)] = (0..<nColors).map { i in
            let t = (Double(i) / Double(nColors) + colorOffset).truncatingRemainder(dividingBy: 1.0)
            let c = palette.color(at: t < 0 ? t + 1 : t)
            return (Float(c.redComponent), Float(c.greenComponent), Float(c.blueComponent))
        }

        let tiltBase: Float = .pi / 5.0   // ~36°
        let tilt = tiltBase + Float(rng.nextDouble() * 0.25)
        let cosTilt = cos(tilt), sinTilt = sin(tilt)

        func proj(_ x3: Float, _ y3: Float, _ z3: Float) -> (Float, Float, Float) {
            let sy    = y3 * cosTilt - z3 * sinTilt
            let depth = y3 * sinTilt + z3 * cosTilt
            return (x3, sy, depth)
        }

        // uMin…uMax spans 2 full turns; shell grows outward as u increases toward 0
        let uMin: Float = -.pi * 4.0
        let uMax: Float = 0.0

        let nRibs     = 18 + Int(params.density * 22)   // 18–40 cross-section ribs
        let nSpiralLines = 6 + Int(params.density * 10) // 6–16 surface spirals
        let ribSteps     = 24
        let spiralSteps  = 64
        let symScale     = 1.0 / sqrt(Float(max(1, symmetry)))

        for _ in 0..<layerCount {
            // b = growth rate; r = relative tube radius
            let b: Float  = Float(0.12 + rng.nextDouble() * 0.10)
            let r: Float  = Float(0.25 + rng.nextDouble() * 0.30)
            let baseW     = Float(rng.nextDouble(in: 0.5...1.4)) * Float(params.complexity) * symScale
            let tShift    = rng.nextDouble()

            // Normalise so the outermost edge fits within R
            let outerScale: Float = R / (exp(b * 0) * (1 + r))  // u=0 is outermost

            func shellPt(_ u: Float, _ v: Float) -> (Float, Float, Float) {
                let ebu  = exp(b * u) * outerScale
                let cosU = cos(u), sinU = sin(u)
                let cosV = cos(v), sinV = sin(v)
                let x3   = ebu * (1 + r * cosV) * cosU
                let y3   = ebu * (1 + r * cosV) * sinU
                let z3   = ebu * r * sinV
                return (x3, y3, z3)
            }

            // Cross-section ribs (constant u, vary v 0…2π)
            for ri in 0..<nRibs {
                let u = uMin + Float(ri) / Float(nRibs - 1) * (uMax - uMin)
                var prevSX: Float = 0, prevSY: Float = 0, prevDepth: Float = 0
                for j in 0...ribSteps {
                    let v = Float(j) / Float(ribSteps) * .pi * 2.0
                    let (x3, y3, z3) = shellPt(u, v)
                    let (sx, sy, depth) = proj(x3, y3, z3)
                    if j > 0 {
                        let normDepth = (depth + R) / (2 * R)
                        let df   = max(0.08, min(1.0, normDepth * 0.92 + 0.08))
                        let w    = baseW * df
                        let cT   = (Double(ri) / Double(nRibs) + tShift + params.colorDrift * Double(j) / Double(ribSteps)).truncatingRemainder(dividingBy: 1.0)
                        let cIdx = Int(max(0.0, min(1.0, cT)) * Double(nColors - 1))
                        let col  = colorLUT[cIdx]
                        for s in 0..<symmetry {
                            let ang  = Float(s) * .pi * 2.0 / Float(symmetry)
                            let cosA = cos(ang), sinA = sin(ang)
                            let rx0  = cx + prevSX * cosA - prevSY * sinA
                            let ry0  = cy + prevSX * sinA + prevSY * cosA
                            let rx1  = cx + sx    * cosA - sy    * sinA
                            let ry1  = cy + sx    * sinA + sy    * cosA
                            buffer.addLine(x0: rx0, y0: ry0, x1: rx1, y1: ry1,
                                           color: col, weight: w)
                        }
                    }
                    prevSX = sx; prevSY = sy; prevDepth = depth
                }
                _ = prevDepth
            }

            // Surface spirals (constant v, vary u uMin…uMax)
            for si in 0..<nSpiralLines {
                let v = Float(si) / Float(nSpiralLines) * .pi * 2.0
                var prevSX: Float = 0, prevSY: Float = 0, prevDepth: Float = 0
                for j in 0...spiralSteps {
                    let u = uMin + Float(j) / Float(spiralSteps) * (uMax - uMin)
                    let (x3, y3, z3) = shellPt(u, v)
                    let (sx, sy, depth) = proj(x3, y3, z3)
                    if j > 0 {
                        let normDepth = (depth + R) / (2 * R)
                        let df   = max(0.08, min(1.0, normDepth * 0.92 + 0.08))
                        let w    = baseW * df * 0.7
                        let cT   = (Double(si) / Double(nSpiralLines) + tShift + 0.5 + params.colorDrift * Double(j) / Double(spiralSteps)).truncatingRemainder(dividingBy: 1.0)
                        let cIdx = Int(max(0.0, min(1.0, cT)) * Double(nColors - 1))
                        let col  = colorLUT[cIdx]
                        for s in 0..<symmetry {
                            let ang  = Float(s) * .pi * 2.0 / Float(symmetry)
                            let cosA = cos(ang), sinA = sin(ang)
                            let rx0  = cx + prevSX * cosA - prevSY * sinA
                            let ry0  = cy + prevSX * sinA + prevSY * cosA
                            let rx1  = cx + sx    * cosA - sy    * sinA
                            let ry1  = cy + sx    * sinA + sy    * cosA
                            buffer.addLine(x0: rx0, y0: ry0, x1: rx1, y1: ry1,
                                           color: col, weight: w)
                        }
                    }
                    prevSX = sx; prevSY = sy; prevDepth = depth
                }
                _ = prevDepth
            }
        }
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
