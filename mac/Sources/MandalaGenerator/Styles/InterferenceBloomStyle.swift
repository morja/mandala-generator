import Foundation

enum InterferenceBloomStyle {
    static func draw(buffer: PixelBuffer, cx: Float, cy: Float,
                     radius: Double, params: MandalaParameters,
                     palette: ColorPalette, rng: inout SeededRNG,
                     colorOffset: Double, symmetry: Int) {
        let R = Float(radius)
        let contourRings = max(12, Int(14 + params.complexity * 18 + params.density * 10))
        let petals = max(6, symmetry * 2 + Int(params.complexity * 6))
        let contourSteps = Int(240 + params.complexity * 220)
        let freqA = Float(2.5 + params.density * 8.5 + rng.nextDouble() * 3.5)
        let freqB = Float(3.5 + params.complexity * 10.0 + rng.nextDouble() * 4.5)
        let phaseA = Float(rng.nextDouble() * Double.pi * 2)
        let phaseB = Float(rng.nextDouble() * Double.pi * 2)
        let brightnessBoost = Float(0.90 + params.glowIntensity * 0.85 + params.density * 0.45)

        for ring in 0..<contourRings {
            let t = Double(ring) / Double(max(1, contourRings - 1))
            let tf = Float(t)
            let baseR = R * Float(0.05 + t * 0.92)
            let amp = R * Float(0.018 + params.abstractLevel * 0.060 + params.density * 0.020 + (1 - t) * 0.035)
            let c = StyleDrawing.paletteColor(palette, at: 0.08 + t * 0.72, colorOffset: colorOffset)
            var points: [(Float, Float)] = []
            points.reserveCapacity(contourSteps + 1)
            for i in 0...contourSteps {
                let a = Float(i) / Float(contourSteps) * Float.pi * 2
                let interference = sin(a * freqA + phaseA + tf * 5.2) + cos(a * freqB + phaseB - tf * 4.1)
                let petalMod = sin(a * Float(petals) + tf * 6.4 + phaseB * 0.45) * Float(0.55 + params.density * 0.48)
                let innerPulse = cos(a * Float(max(3, symmetry)) * 0.5 - tf * 7.0) * Float(0.28 + params.complexity * 0.22)
                let rr = baseR + (interference * 0.72 + petalMod + innerPulse) * amp
                points.append((cx + cos(a) * rr, cy + sin(a) * rr))
            }
            StyleDrawing.addPolyline(buffer: buffer, points: points,
                                     color: (c.r * brightnessBoost, c.g * brightnessBoost, c.b * brightnessBoost),
                                     weight: Float(0.11 + (1 - t) * 0.22 + params.glowIntensity * 0.12 + params.density * 0.05))
        }

        let waveFamilies = max(3, Int(3 + params.density * 6 + params.complexity * 2))
        for family in 0..<waveFamilies {
            let familyFrac = Double(family) / Double(max(1, waveFamilies - 1))
            let waveColor = StyleDrawing.paletteColor(palette, at: 0.38 + familyFrac * 0.30, colorOffset: colorOffset)
            let familyBrightness = Float(0.80 + familyFrac * 0.25)
            let spokes = max(8, petals + family * 4)
            for spoke in 0..<spokes {
                let baseA = Float(spoke) * .pi * 2 / Float(spokes) + Float(rng.nextDouble() * 0.24)
                let spokeSteps = 120
                var points: [(Float, Float)] = []
                points.reserveCapacity(spokeSteps + 1)
                for i in 0...spokeSteps {
                    let t = Float(i) / Float(spokeSteps)
                    let rr = R * (0.06 + t * (0.86 + Float(params.density) * 0.08))
                    let sideA = sin(t * .pi * (4.8 + Float(family) * 1.9) + baseA * (freqA * 0.55) + phaseB) * R * 0.055
                    let sideB = cos(t * .pi * (2.2 + Float(family) * 0.9) + baseA * (freqB * 0.42) - phaseA) * R * 0.030
                    let a = baseA + (sideA + sideB) / max(rr, 1)
                    points.append((cx + cos(a) * rr, cy + sin(a) * rr))
                }
                StyleDrawing.addPolyline(buffer: buffer, points: points,
                                         color: (waveColor.r * familyBrightness * brightnessBoost,
                                                 waveColor.g * familyBrightness * brightnessBoost,
                                                 waveColor.b * familyBrightness * brightnessBoost),
                                         weight: Float(0.08 + params.complexity * 0.08 + params.density * 0.04))
            }
        }

        let bloomBands = max(3, Int(3 + params.abstractLevel * 4 + params.glowIntensity * 2))
        for band in 0..<bloomBands {
            let bandFrac = Double(band) / Double(max(1, bloomBands - 1))
            let bandColor = StyleDrawing.paletteColor(palette, at: 0.18 + bandFrac * 0.56, colorOffset: colorOffset)
            let loops = max(5, petals / 2 + band * 2)
            let bandSteps = 150
            var points: [(Float, Float)] = []
            points.reserveCapacity(bandSteps + 1)
            let bandBase = R * Float(0.18 + bandFrac * 0.56)
            for i in 0...bandSteps {
                let t = Float(i) / Float(bandSteps)
                let a = t * Float.pi * 2
                let lobe = sin(a * Float(loops) + phaseA + Float(band) * 0.7) * R * Float(0.05 + params.abstractLevel * 0.07)
                let caustic = cos(a * freqA * 0.42 - phaseB + Float(band) * 0.5) * R * 0.022
                let rr = bandBase + lobe + caustic
                points.append((cx + cos(a) * rr, cy + sin(a) * rr))
            }
            StyleDrawing.addPolyline(buffer: buffer, points: points,
                                     color: (bandColor.r * 0.72 * brightnessBoost,
                                             bandColor.g * 0.72 * brightnessBoost,
                                             bandColor.b * 0.72 * brightnessBoost),
                                     weight: Float(0.08 + params.glowIntensity * 0.08 + bandFrac * 0.04))
        }

        let causticNodes = max(12, Int(14 + params.density * 18 + params.complexity * 8))
        for i in 0..<causticNodes {
            let frac = Double(i) / Double(max(1, causticNodes - 1))
            let c = StyleDrawing.paletteColor(palette, at: 0.74 + frac * 0.20, colorOffset: colorOffset)
            let a = Float(i) * .pi * 2 / Float(causticNodes) + phaseA * 0.4 + Float(rng.nextDouble(in: -0.12...0.12))
            let rr = R * Float(0.12 + (sin(a * freqA + phaseB) * 0.5 + 0.5) * 0.68)
            let x = cx + cos(a) * rr
            let y = cy + sin(a) * rr
            StyleDrawing.addCircle(buffer: buffer, cx: x, cy: y,
                                   radius: R * Float(0.020 + rng.nextDouble() * 0.040 + params.density * 0.008),
                                   color: (c.r * 0.55 * brightnessBoost, c.g * 0.55 * brightnessBoost, c.b * 0.55 * brightnessBoost),
                                   weight: Float(0.10 + params.glowIntensity * 0.12 + params.complexity * 0.04),
                                   steps: 28)
        }
    }
}
