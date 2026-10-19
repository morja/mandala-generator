package com.mathis.mandala.core.styles

import com.mathis.mandala.core.ColorPalette
import com.mathis.mandala.core.SeededRng
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private const val PI_F = PI.toFloat()

/** Deep Nebula and Interference Bloom (DeepNebulaStyle.swift and InterferenceBloomStyle.swift in the macOS app). */
object CosmicStyles {

    private fun color(palette: ColorPalette, t: Double, colorOffset: Double): FloatArray =
        FloatArray(3).also { palette.colorAt((t + colorOffset) % 1.0, it) }

    private fun polyline(lines: LineList, xs: FloatArray, ys: FloatArray, r: Float, g: Float, b: Float, w: Float) {
        for (i in 0 until xs.size - 1) lines.add(xs[i], ys[i], xs[i + 1], ys[i + 1], r, g, b, w)
    }

    private fun ellipse(
        lines: LineList, cx: Float, cy: Float, rx: Float, ry: Float, rotation: Float,
        r: Float, g: Float, b: Float, w: Float, steps: Int, start: Float = 0f, end: Float = PI_F * 2,
    ) {
        if (rx <= 0.5f || ry <= 0.5f || steps <= 1) return
        val dt = (end - start) / steps
        val ca = cos(rotation)
        val sa = sin(rotation)
        val xs = FloatArray(steps + 1)
        val ys = FloatArray(steps + 1)
        for (i in 0..steps) {
            val t = start + i * dt
            val px = cos(t) * rx
            val py = sin(t) * ry
            xs[i] = cx + px * ca - py * sa
            ys[i] = cy + px * sa + py * ca
        }
        polyline(lines, xs, ys, r, g, b, w)
    }

    /** Cloud masses of nested ellipses, curling filaments and dark dust lanes. */
    fun deepNebula(lines: LineList, cx: Float, cy: Float, radius: Double, ctx: StyleContext, palette: ColorPalette, rng: SeededRng, colorOffset: Double, symmetry: Int) {
        val bigR = radius.toFloat()
        val sector = PI_F * 2 / max(1, symmetry)
        val glowFloor = (0.18 + ctx.glowIntensity * 0.16).toFloat()
        val densityBoost = (0.70 + ctx.density * 1.10).toFloat()
        val cloudMasses = max(10, (10 + ctx.density * 18 + ctx.complexity * 6).toInt())
        val cloudShells = max(7, (7 + ctx.abstractLevel * 10 + ctx.density * 5 + ctx.glowIntensity * 2).toInt())
        val dustLanes = max(8, (8 + ctx.complexity * 10 + ctx.density * 8).toInt())
        val filaments = max(10, (10 + ctx.complexity * 8 + ctx.density * 6).toInt())
        val symmetryCount = max(1, symmetry)

        for (mass in 0 until cloudMasses) {
            val massFrac = mass.toDouble() / max(1, cloudMasses - 1)
            val cloud = color(palette, 0.03 + massFrac * 0.44, colorOffset)
            val massA = (rng.nextDouble() * sector.toDouble()).toFloat()
            val massR = bigR * (rng.nextDouble().pow(0.86) * (0.14 + ctx.density * 0.54)).toFloat()
            val baseRX = bigR * (0.08 + rng.nextDouble() * 0.18 + ctx.density * 0.05).toFloat()
            val baseRY = baseRX * (0.42 + rng.nextDouble() * 0.88).toFloat()
            val rotation = (rng.nextDouble() * PI).toFloat()
            for (sym in 0 until symmetryCount) {
                val symA = massA + sym.toFloat() * sector
                val mx = cx + cos(symA) * massR
                val my = cy + sin(symA) * massR
                for (shell in 0 until cloudShells) {
                    val shellFrac = shell.toDouble() / max(1, cloudShells - 1)
                    val swell = (1.0 + shellFrac * (1.2 + ctx.abstractLevel * 0.7)).toFloat()
                    val jitterX = baseRX * rng.nextDouble(-0.18, 0.18).toFloat()
                    val jitterY = baseRY * rng.nextDouble(-0.18, 0.18).toFloat()
                    val shellColorScale = (0.28 + (1 - shellFrac) * 0.22).toFloat() * densityBoost + glowFloor * 0.10f
                    val shellRYScale = (0.90 + rng.nextDouble() * 0.22).toFloat()
                    val shellRotation = rotation + shell.toFloat() * 0.14f + symA * 0.25f
                    val shellWeight = (0.09 + (1 - shellFrac) * 0.10).toFloat() + glowFloor * 0.10f
                    ellipse(
                        lines, mx + jitterX, my + jitterY, baseRX * swell, baseRY * swell * shellRYScale, shellRotation,
                        cloud[0] * shellColorScale, cloud[1] * shellColorScale, cloud[2] * shellColorScale, shellWeight, 48,
                    )
                }
            }
        }

        for (filament in 0 until filaments) {
            val frac = filament.toDouble() / max(1, filaments - 1)
            val fc = color(palette, 0.18 + frac * 0.36, colorOffset)
            val startA = (rng.nextDouble() * sector.toDouble()).toFloat()
            val startR = bigR * (0.06 + rng.nextDouble() * 0.20).toFloat()
            val steps = (110 + ctx.complexity * 120).toInt()
            for (sym in 0 until symmetryCount) {
                val symA = startA + sym.toFloat() * sector
                val xs = FloatArray(steps + 1)
                val ys = FloatArray(steps + 1)
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val rr = startR + t * bigR * (0.44 + ctx.density * 0.28).toFloat()
                    val bend = sin(t * PI_F * (2.2 + ctx.complexity * 4.4).toFloat() + filament.toFloat() * 0.7f) * 0.28f
                    val curl = cos(t * PI_F * (1.1 + ctx.abstractLevel * 2.4).toFloat() + filament.toFloat() * 1.2f) * 0.14f
                    val a = symA + bend + curl + t * (0.8 + ctx.abstractLevel * 1.1).toFloat()
                    xs[i] = cx + cos(a) * rr
                    ys[i] = cy + sin(a) * rr
                }
                val k = 0.34f + glowFloor
                polyline(lines, xs, ys, fc[0] * k, fc[1] * k, fc[2] * k, (0.06 + ctx.density * 0.06).toFloat() + glowFloor * 0.03f)
            }
        }

        for (lane in 0 until dustLanes) {
            val frac = lane.toDouble() / max(1, dustLanes - 1)
            val tone = (0.10 + frac * 0.08).toFloat()
            val arcA = (rng.nextDouble() * sector.toDouble()).toFloat()
            val arcSpan = (0.55 + ctx.abstractLevel * 0.85 + rng.nextDouble() * 0.45).toFloat()
            val rx = bigR * (0.22 + frac * 0.54 + rng.nextDouble() * 0.12).toFloat()
            val ry = rx * (0.10 + rng.nextDouble() * 0.18).toFloat()
            val rot = arcA + rng.nextDouble(-0.9, 0.9).toFloat()
            for (sym in 0 until symmetryCount) {
                ellipse(
                    lines, cx, cy, rx, ry, rot + sym.toFloat() * sector, tone, tone, tone,
                    (0.10 + ctx.complexity * 0.09 + (1 - frac) * 0.03).toFloat(), 96, -arcSpan, arcSpan,
                )
            }
        }
    }

    /** Interfering contour rings, wavy spokes, bloom bands and caustic circles. */
    fun interferenceBloom(lines: LineList, cx: Float, cy: Float, radius: Double, ctx: StyleContext, palette: ColorPalette, rng: SeededRng, colorOffset: Double, symmetry: Int) {
        val bigR = radius.toFloat()
        val contourRings = max(12, (14 + ctx.complexity * 18 + ctx.density * 10).toInt())
        val petals = max(6, symmetry * 2 + (ctx.complexity * 6).toInt())
        val contourSteps = (240 + ctx.complexity * 220).toInt()
        val freqA = (2.5 + ctx.density * 8.5 + rng.nextDouble() * 3.5).toFloat()
        val freqB = (3.5 + ctx.complexity * 10.0 + rng.nextDouble() * 4.5).toFloat()
        val phaseA = (rng.nextDouble() * PI * 2).toFloat()
        val phaseB = (rng.nextDouble() * PI * 2).toFloat()
        val boost = (0.90 + ctx.glowIntensity * 0.85 + ctx.density * 0.45).toFloat()

        for (ring in 0 until contourRings) {
            val t = ring.toDouble() / max(1, contourRings - 1)
            val tf = t.toFloat()
            val baseR = bigR * (0.05 + t * 0.92).toFloat()
            val amp = bigR * (0.018 + ctx.abstractLevel * 0.060 + ctx.density * 0.020 + (1 - t) * 0.035).toFloat()
            val c = color(palette, 0.08 + t * 0.72, colorOffset)
            val xs = FloatArray(contourSteps + 1)
            val ys = FloatArray(contourSteps + 1)
            for (i in 0..contourSteps) {
                val a = i.toFloat() / contourSteps * PI_F * 2
                val interference = sin(a * freqA + phaseA + tf * 5.2f) + cos(a * freqB + phaseB - tf * 4.1f)
                val petalMod = sin(a * petals.toFloat() + tf * 6.4f + phaseB * 0.45f) * (0.55 + ctx.density * 0.48).toFloat()
                val innerPulse = cos(a * max(3, symmetry).toFloat() * 0.5f - tf * 7.0f) * (0.28 + ctx.complexity * 0.22).toFloat()
                val rr = baseR + (interference * 0.72f + petalMod + innerPulse) * amp
                xs[i] = cx + cos(a) * rr
                ys[i] = cy + sin(a) * rr
            }
            polyline(lines, xs, ys, c[0] * boost, c[1] * boost, c[2] * boost,
                (0.11 + (1 - t) * 0.22 + ctx.glowIntensity * 0.12 + ctx.density * 0.05).toFloat())
        }

        val waveFamilies = max(3, (3 + ctx.density * 6 + ctx.complexity * 2).toInt())
        for (family in 0 until waveFamilies) {
            val familyFrac = family.toDouble() / max(1, waveFamilies - 1)
            val wc = color(palette, 0.38 + familyFrac * 0.30, colorOffset)
            val fb = (0.80 + familyFrac * 0.25).toFloat()
            val spokes = max(8, petals + family * 4)
            for (spoke in 0 until spokes) {
                val baseA = spoke.toFloat() * PI_F * 2 / spokes + (rng.nextDouble() * 0.24).toFloat()
                val spokeSteps = 120
                val xs = FloatArray(spokeSteps + 1)
                val ys = FloatArray(spokeSteps + 1)
                for (i in 0..spokeSteps) {
                    val t = i.toFloat() / spokeSteps
                    val rr = bigR * (0.06f + t * (0.86f + ctx.density.toFloat() * 0.08f))
                    val sideA = sin(t * PI_F * (4.8f + family.toFloat() * 1.9f) + baseA * (freqA * 0.55f) + phaseB) * bigR * 0.055f
                    val sideB = cos(t * PI_F * (2.2f + family.toFloat() * 0.9f) + baseA * (freqB * 0.42f) - phaseA) * bigR * 0.030f
                    val a = baseA + (sideA + sideB) / max(rr, 1f)
                    xs[i] = cx + cos(a) * rr
                    ys[i] = cy + sin(a) * rr
                }
                polyline(lines, xs, ys, wc[0] * fb * boost, wc[1] * fb * boost, wc[2] * fb * boost,
                    (0.08 + ctx.complexity * 0.08 + ctx.density * 0.04).toFloat())
            }
        }

        val bloomBands = max(3, (3 + ctx.abstractLevel * 4 + ctx.glowIntensity * 2).toInt())
        for (band in 0 until bloomBands) {
            val bandFrac = band.toDouble() / max(1, bloomBands - 1)
            val bc = color(palette, 0.18 + bandFrac * 0.56, colorOffset)
            val loops = max(5, petals / 2 + band * 2)
            val bandSteps = 150
            val bandBase = bigR * (0.18 + bandFrac * 0.56).toFloat()
            val xs = FloatArray(bandSteps + 1)
            val ys = FloatArray(bandSteps + 1)
            for (i in 0..bandSteps) {
                val t = i.toFloat() / bandSteps
                val a = t * PI_F * 2
                val lobe = sin(a * loops.toFloat() + phaseA + band.toFloat() * 0.7f) * bigR * (0.05 + ctx.abstractLevel * 0.07).toFloat()
                val caustic = cos(a * freqA * 0.42f - phaseB + band.toFloat() * 0.5f) * bigR * 0.022f
                val rr = bandBase + lobe + caustic
                xs[i] = cx + cos(a) * rr
                ys[i] = cy + sin(a) * rr
            }
            polyline(lines, xs, ys, bc[0] * 0.72f * boost, bc[1] * 0.72f * boost, bc[2] * 0.72f * boost,
                (0.08 + ctx.glowIntensity * 0.08 + bandFrac * 0.04).toFloat())
        }

        val causticNodes = max(12, (14 + ctx.density * 18 + ctx.complexity * 8).toInt())
        for (i in 0 until causticNodes) {
            val frac = i.toDouble() / max(1, causticNodes - 1)
            val c = color(palette, 0.74 + frac * 0.20, colorOffset)
            val a = i.toFloat() * PI_F * 2 / causticNodes + phaseA * 0.4f + rng.nextDouble(-0.12, 0.12).toFloat()
            val rr = bigR * (0.12f + (sin(a * freqA + phaseB) * 0.5f + 0.5f) * 0.68f)
            val r = bigR * (0.020 + rng.nextDouble() * 0.040 + ctx.density * 0.008).toFloat()
            ellipse(lines, cx + cos(a) * rr, cy + sin(a) * rr, r, r, 0f,
                c[0] * 0.55f * boost, c[1] * 0.55f * boost, c[2] * 0.55f * boost,
                (0.10 + ctx.glowIntensity * 0.12 + ctx.complexity * 0.04).toFloat(), 28)
        }
    }
}
