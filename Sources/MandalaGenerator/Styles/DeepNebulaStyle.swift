import Foundation

enum DeepNebulaStyle {
    static func draw(buffer: PixelBuffer, cx: Float, cy: Float,
                     radius: Double, params: MandalaParameters,
                     palette: ColorPalette, rng: inout SeededRNG,
                     colorOffset: Double, symmetry: Int) {
        let R = Float(radius)
        let sector = Float.pi * 2 / Float(max(1, symmetry))
        let glowFloor = Float(0.18 + params.glowIntensity * 0.16)
        let densityBoost = Float(0.70 + params.density * 1.10)
        let cloudMasses = max(10, Int(10 + params.density * 18 + params.complexity * 6))
        let cloudShells = max(7, Int(7 + params.abstractLevel * 10 + params.density * 5 + params.glowIntensity * 2))
        let dustLanes = max(8, Int(8 + params.complexity * 10 + params.density * 8))
        let filaments = max(10, Int(10 + params.complexity * 8 + params.density * 6))
        let symmetryCount = max(1, symmetry)

        for mass in 0..<cloudMasses {
            let massFrac = Double(mass) / Double(max(1, cloudMasses - 1))
            let cloudColor = StyleDrawing.paletteColor(palette, at: 0.03 + massFrac * 0.44, colorOffset: colorOffset)
            let massA = Float(rng.nextDouble() * Double(sector))
            let massR = R * Float(pow(rng.nextDouble(), 0.86) * (0.14 + params.density * 0.54))
            let baseRX = R * Float(0.08 + rng.nextDouble() * 0.18 + params.density * 0.05)
            let baseRY = baseRX * Float(0.42 + rng.nextDouble() * 0.88)
            let rotation = Float(rng.nextDouble() * Double.pi)
            for sym in 0..<symmetryCount {
                let symA = massA + Float(sym) * sector
                let mx = cx + cos(symA) * massR
                let my = cy + sin(symA) * massR
                for shell in 0..<cloudShells {
                    let shellFrac = Double(shell) / Double(max(1, cloudShells - 1))
                    let swell = Float(1.0 + shellFrac * (1.2 + params.abstractLevel * 0.7))
                    let jitterX = baseRX * Float(rng.nextDouble(in: -0.18...0.18))
                    let jitterY = baseRY * Float(rng.nextDouble(in: -0.18...0.18))
                    let shellBaseScale = Float(0.28 + (1 - shellFrac) * 0.22)
                    let shellGlowScale = glowFloor * 0.10
                    let shellColorScale = shellBaseScale * densityBoost + shellGlowScale
                    let shellRYScale = Float(0.90 + rng.nextDouble() * 0.22)
                    let shellRotation = rotation + Float(shell) * 0.14 + symA * 0.25
                    let shellWeightBase = Float(0.09 + (1 - shellFrac) * 0.10)
                    let shellWeightGlow = glowFloor * 0.10
                    let shellWeight = shellWeightBase + shellWeightGlow
                    let ringColor = (
                        r: cloudColor.r * shellColorScale,
                        g: cloudColor.g * shellColorScale,
                        b: cloudColor.b * shellColorScale
                    )
                    StyleDrawing.addEllipse(buffer: buffer,
                                            cx: mx + jitterX, cy: my + jitterY,
                                            rx: baseRX * swell,
                                            ry: baseRY * swell * shellRYScale,
                                            rotation: shellRotation,
                                            color: ringColor,
                                            weight: shellWeight,
                                            steps: 48)
                }
            }
        }

        for filament in 0..<filaments {
            let frac = Double(filament) / Double(max(1, filaments - 1))
            let filamentColor = StyleDrawing.paletteColor(palette, at: 0.18 + frac * 0.36, colorOffset: colorOffset)
            let startA = Float(rng.nextDouble() * Double(sector))
            let startR = R * Float(0.06 + rng.nextDouble() * 0.20)
            let steps = Int(110 + params.complexity * 120)
            for sym in 0..<symmetryCount {
                let symA = startA + Float(sym) * sector
                var points: [(Float, Float)] = []
                points.reserveCapacity(steps + 1)
                for i in 0...steps {
                    let t = Float(i) / Float(steps)
                    let rr = startR + t * R * Float(0.44 + params.density * 0.28)
                    let bend = sin(t * .pi * Float(2.2 + params.complexity * 4.4) + Float(filament) * 0.7) * 0.28
                    let curl = cos(t * .pi * Float(1.1 + params.abstractLevel * 2.4) + Float(filament) * 1.2) * 0.14
                    let a = symA + bend + curl + t * Float(0.8 + params.abstractLevel * 1.1)
                    points.append((cx + cos(a) * rr, cy + sin(a) * rr))
                }
                let filamentColorScale = Float(0.34) + glowFloor
                let filamentWeight = Float(0.06 + params.density * 0.06) + glowFloor * 0.03
                let filamentDrawColor = (
                    r: filamentColor.r * filamentColorScale,
                    g: filamentColor.g * filamentColorScale,
                    b: filamentColor.b * filamentColorScale
                )
                StyleDrawing.addPolyline(buffer: buffer, points: points,
                                         color: filamentDrawColor,
                                         weight: filamentWeight)
            }
        }

        for lane in 0..<dustLanes {
            let frac = Double(lane) / Double(max(1, dustLanes - 1))
            let laneTone = Float(0.10 + frac * 0.08)
            let laneColor = (r: laneTone, g: laneTone, b: laneTone)
            let arcA = Float(rng.nextDouble() * Double(sector))
            let arcSpan = Float(0.55 + params.abstractLevel * 0.85 + rng.nextDouble() * 0.45)
            let rx = R * Float(0.22 + frac * 0.54 + rng.nextDouble() * 0.12)
            let ry = rx * Float(0.10 + rng.nextDouble() * 0.18)
            let rot = arcA + Float(rng.nextDouble(in: -0.9...0.9))
            for sym in 0..<symmetryCount {
                let symRot = Float(sym) * sector
                StyleDrawing.addEllipse(buffer: buffer, cx: cx, cy: cy, rx: rx, ry: ry,
                                        rotation: rot + symRot,
                                        color: laneColor,
                                        weight: Float(0.10 + params.complexity * 0.09 + (1 - frac) * 0.03),
                                        steps: 96, start: -arcSpan, end: arcSpan)
            }
        }

    }
}
