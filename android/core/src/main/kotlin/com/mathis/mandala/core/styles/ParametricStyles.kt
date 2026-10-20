package com.mathis.mandala.core.styles

import com.mathis.mandala.core.SeededRng
import com.mathis.mandala.core.styles.CurveStyles.applyRipple
import com.mathis.mandala.core.styles.CurveStyles.epitrochoidPoints
import com.mathis.mandala.core.styles.CurveStyles.rosePoints
import com.mathis.mandala.core.styles.CurveStyles.rotated
import com.mathis.mandala.core.styles.CurveStyles.spiroPoints
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private const val PI_F = PI.toFloat()

/** More curve styles from MandalaRenderer.swift: Epitrochoid, Floral, Lissajous, Butterfly, Geometric, Superformula. */
object ParametricStyles {

    fun epitrochoid(
        tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext,
        rng: SeededRng, layerCount: Int, symmetry: Int, rippleAmount: Float, weightMul: Float,
    ) {
        val denominators = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10)
        for (i in 0 until layerCount) {
            val denom = denominators[i % denominators.size].toDouble()
            val bigR = radius * (0.4 + rng.nextDouble() * 0.45)
            val r = bigR / denom
            val d = r * (0.5 + rng.nextDouble() * 0.8)
            val (xs, ys) = epitrochoidPoints(bigR, r, d, 2500)
            val tOffset = rng.nextDouble()
            val weight = rng.nextDouble(0.4, 1.2).toFloat() * ctx.complexity.toFloat() * weightMul
            val thickness = rng.nextDouble(1.0, 2.0).toInt()
            val ripSeed = ctx.seed + (i * 61 + 13).toULong()
            for (sym in 0 until symmetry) {
                val (rxs, rys) = rotated(xs, ys, cx, cy, sym * PI * 2.0 / symmetry)
                if (rippleAmount > 0) applyRipple(rxs, rys, rippleAmount, ripSeed + sym.toULong())
                tasks += CurveTask(rxs, rys, tOffset + sym * 0.08, ctx.colorDrift, weight, thickness)
            }
        }
    }

    /** Outer looping ring, hatched teardrop petals, spiral connectors and a central rosette. */
    fun floral(
        tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext,
        rng: SeededRng, symmetry: Int, rippleAmount: Float, weightMul: Float,
    ) {
        val bigR = radius.toFloat()
        val nPetals = max(5, symmetry * 2 + 3)

        // 1. Outer looping ring
        val outerR = bigR * 0.47f
        val loopR = bigR * (0.04 + ctx.density * 0.05).toFloat()
        val nLoops = nPetals * 3
        val outerSteps = nLoops * 50
        val oxs = FloatArray(outerSteps + 1)
        val oys = FloatArray(outerSteps + 1)
        for (i in 0..outerSteps) {
            val t = i.toFloat() / outerSteps * PI_F * 2
            oxs[i] = cx + outerR * cos(t) + loopR * cos(nLoops.toFloat() * t)
            oys[i] = cy + outerR * sin(t) + loopR * sin(nLoops.toFloat() * t)
        }
        tasks += CurveTask(oxs, oys, 0.72, ctx.colorDrift * 0.4, 0.8f * weightMul, 1)

        // 2. Teardrop petals with hatch fill
        val petalDist = bigR * 0.57f
        val petalLen = bigR * 0.51f
        val petalW = bigR * 0.195f
        val pinwheel = (0.28 + ctx.complexity * 0.18).toFloat()
        val nHatch = max(25, (ctx.density * 70).toInt())
        for (i in 0 until nPetals) {
            val baseAngle = i.toFloat() * PI_F * 2 / nPetals
            val axisAngle = baseAngle + pinwheel
            val pcx = cx + cos(baseAngle) * petalDist
            val pcy = cy + sin(baseAngle) * petalDist
            val petalT = i.toDouble() / nPetals * 0.35
            for (h in 0 until nHatch) {
                val t = h.toFloat() / (nHatch - 1)
                val halfW = petalW * 0.5f * sin(PI_F * t)
                val along = (t - 0.5f) * petalLen
                val midX = pcx + cos(axisAngle) * along
                val midY = pcy + sin(axisAngle) * along
                val perpA = axisAngle + PI_F * 0.5f
                val x0 = midX + cos(perpA) * halfW
                val y0 = midY + sin(perpA) * halfW
                val x1 = midX - cos(perpA) * halfW
                val y1 = midY - sin(perpA) * halfW
                val jitter = rng.nextDouble(-0.5, 0.5).toFloat()
                tasks += CurveTask(
                    floatArrayOf(x0, x1), floatArrayOf(y0, y1),
                    tOffset = petalT + t.toDouble() * 0.08 + jitter.toDouble() * 0.01,
                    drift = 0.04,
                    weight = rng.nextDouble(0.25, 0.55).toFloat() * weightMul,
                    thickness = 1,
                )
            }
            val nOut = 80
            val pxs = FloatArray(nOut + 1)
            val pys = FloatArray(nOut + 1)
            for (j in 0..nOut) {
                val a = j.toFloat() / nOut * PI_F * 2
                val along = petalLen * 0.5f * (1 - cos(a)) * 0.5f - petalLen * 0.25f
                val perp = petalW * 0.5f * sin(a)
                pxs[j] = pcx + cos(axisAngle) * along + cos(axisAngle + PI_F / 2) * perp
                pys[j] = pcy + sin(axisAngle) * along + sin(axisAngle + PI_F / 2) * perp
            }
            tasks += CurveTask(pxs, pys, 0.50 + i.toDouble() / nPetals * 0.25, ctx.colorDrift * 0.15, 0.9f * weightMul, 1)
        }

        // 3. Spiral connectors
        val nSpirals = max(2, nPetals / 3)
        for (s in 0 until nSpirals) {
            val startA = s.toFloat() * PI_F * 2 / nSpirals
            val turns = nPetals.toFloat() / nSpirals
            val spSteps = 280
            val sxs = FloatArray(spSteps + 1)
            val sys = FloatArray(spSteps + 1)
            for (i in 0..spSteps) {
                val t = i.toFloat() / spSteps
                val angle = startA + t * PI_F * 2 * turns
                val r = bigR * 0.04f + t * petalDist * 1.05f
                val wave = bigR * 0.018f * sin(nPetals.toFloat() * angle)
                sxs[i] = cx + cos(angle) * (r + wave)
                sys[i] = cy + sin(angle) * (r + wave)
            }
            if (rippleAmount > 0) applyRipple(sxs, sys, rippleAmount, ctx.seed + s.toULong() * 997uL)
            tasks += CurveTask(sxs, sys, 0.48 + s.toDouble() / nSpirals * 0.12, ctx.colorDrift * 0.5, 0.55f * weightMul, 1)
        }

        // 4. Central rosette
        val flowerK = nPetals / 2 + 1
        val flowerR2 = bigR * (0.06 + ctx.complexity * 0.03).toFloat()
        val flSteps = max(360, flowerK * 80)
        val fxs = FloatArray(flSteps + 1)
        val fys = FloatArray(flSteps + 1)
        for (i in 0..flSteps) {
            val t = i.toFloat() / flSteps * PI_F * 2
            val r = flowerR2 * abs(cos(flowerK.toFloat() * t))
            fxs[i] = cx + r * cos(t)
            fys[i] = cy + r * sin(t)
        }
        tasks += CurveTask(fxs, fys, 0.52, 0.08, 1.3f * weightMul, 1)
    }

    fun lissajous(
        tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext,
        rng: SeededRng, layerCount: Int, symmetry: Int, rippleAmount: Float, weightMul: Float,
    ) {
        val ratios = arrayOf(1 to 2, 2 to 3, 3 to 4, 3 to 5, 4 to 5, 5 to 6, 1 to 3, 2 to 5, 3 to 7, 4 to 7, 5 to 7, 5 to 8, 6 to 7, 7 to 9)
        for (i in 0 until layerCount) {
            val ratio = ratios[i % ratios.size]
            val a = ratio.first.toFloat()
            val b = ratio.second.toFloat()
            val bigR = radius.toFloat() * (0.45 + rng.nextDouble() * 0.5).toFloat()
            val delta = (rng.nextDouble() * PI * 2).toFloat()
            val steps = ratio.second * 280
            val xs = FloatArray(steps + 1)
            val ys = FloatArray(steps + 1)
            for (j in 0..steps) {
                val t = j.toFloat() / steps * PI_F * 2
                xs[j] = bigR * sin(a * t + delta)
                ys[j] = bigR * cos(b * t)
            }
            val tOffset = rng.nextDouble()
            val weight = rng.nextDouble(0.4, 1.4).toFloat() * ctx.complexity.toFloat() * weightMul
            val thickness = rng.nextDouble(1.0, 2.0).toInt()
            val ripSeed = ctx.seed + (i * 53 + 7).toULong()
            for (sym in 0 until symmetry) {
                val (rxs, rys) = rotated(xs, ys, cx, cy, sym * PI * 2 / symmetry)
                if (rippleAmount > 0) applyRipple(rxs, rys, rippleAmount, ripSeed + sym.toULong())
                tasks += CurveTask(rxs, rys, tOffset + sym * 0.09, ctx.colorDrift, weight, thickness)
            }
        }
    }

    /** Temple H. Fay's butterfly curve. */
    fun butterfly(
        tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext,
        rng: SeededRng, layerCount: Int, symmetry: Int, rippleAmount: Float, weightMul: Float,
    ) {
        for (i in 0 until layerCount) {
            val scale = radius.toFloat() * (0.16 + rng.nextDouble() * 0.20).toFloat()
            val rotOffset = (rng.nextDouble() * PI * 2).toFloat()
            val steps = 2000
            val xs = FloatArray(steps + 1)
            val ys = FloatArray(steps + 1)
            for (j in 0..steps) {
                val theta = j.toFloat() / steps * PI_F * 12
                val s = sin((2 * theta - PI_F) / 24)
                val rho = exp(sin(theta)) - 2 * cos(4 * theta) + s * s * s * s * s
                xs[j] = scale * rho * cos(theta + rotOffset)
                ys[j] = scale * rho * sin(theta + rotOffset)
            }
            val tOffset = rng.nextDouble()
            val weight = rng.nextDouble(0.3, 0.9).toFloat() * ctx.complexity.toFloat() * weightMul
            val ripSeed = ctx.seed + (i * 71 + 11).toULong()
            for (sym in 0 until symmetry) {
                val (rxs, rys) = rotated(xs, ys, cx, cy, sym * PI * 2 / symmetry)
                if (rippleAmount > 0) applyRipple(rxs, rys, rippleAmount, ripSeed + sym.toULong())
                tasks += CurveTask(rxs, rys, tOffset + sym * 0.11, ctx.colorDrift * 0.5, weight, 1)
            }
        }
    }

    /** Concentric architectural mandala: epitrochoid border, rose ring, inner spirograph, central rosette. */
    fun geometric(
        tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext,
        rng: SeededRng, layerCount: Int, symmetry: Int, rippleAmount: Float, weightMul: Float,
    ) {
        // Outer border: epitrochoid
        val eDenom = (5 + (rng.nextDouble() * 5).toInt()).toDouble()
        val eR = radius * 0.9
        val er = eR / eDenom
        val ed = er * (0.6 + rng.nextDouble() * 0.5)
        val (exs, eys) = epitrochoidPoints(eR, er, ed, 3000)
        for (sym in 0 until symmetry) {
            val (rxs, rys) = rotated(exs, eys, cx, cy, sym * PI * 2 / symmetry)
            if (rippleAmount > 0) applyRipple(rxs, rys, rippleAmount, ctx.seed + sym.toULong())
            tasks += CurveTask(rxs, rys, rng.nextDouble(), ctx.colorDrift, 0.8f * weightMul, 1)
        }

        // Middle ring: rose curves at varied radii
        val kVals = intArrayOf(5, 6, 7, 8, 9, 10, 11, 12)
        val midCount = max(2, layerCount / 2)
        for (i in 0 until midCount) {
            val k = kVals[(i + (ctx.seed and 7uL).toInt()) % kVals.size]
            val mR = radius * (0.55 + rng.nextDouble() * 0.15)
            val (rxs0, rys0) = rosePoints(k, mR, 2000)
            val tOff = rng.nextDouble()
            val w = rng.nextDouble(0.5, 1.2).toFloat() * ctx.complexity.toFloat() * weightMul
            for (sym in 0 until symmetry) {
                val angle = sym * PI * 2 / symmetry + rng.nextDouble() * 0.2
                val (rx, ry) = rotated(rxs0, rys0, cx, cy, angle)
                if (rippleAmount > 0) applyRipple(rx, ry, rippleAmount, ctx.seed + (i * 37 + sym).toULong())
                tasks += CurveTask(rx, ry, tOff + sym * 0.07, ctx.colorDrift, w, 1)
            }
        }

        // Inner ring: spirograph
        val iR = radius * 0.32
        val ir = iR / (3 + (rng.nextDouble() * 4).toInt()).toDouble()
        val id2 = ir * (0.5 + rng.nextDouble() * 0.7)
        val (ixs, iys) = spiroPoints(iR, ir, id2, 2000)
        val iToff = rng.nextDouble()
        val iW = rng.nextDouble(0.6, 1.3).toFloat() * ctx.complexity.toFloat() * weightMul
        for (sym in 0 until symmetry) {
            val (rx, ry) = rotated(ixs, iys, cx, cy, sym * PI * 2 / symmetry)
            if (rippleAmount > 0) applyRipple(rx, ry, rippleAmount, ctx.seed + (100 + sym).toULong())
            tasks += CurveTask(rx, ry, iToff + sym * 0.05, ctx.colorDrift * 0.6, iW, 1)
        }

        // Centre: dense rose rosette
        val cK = 8 + (ctx.seed and 3uL).toInt()
        val (cxs, cys) = rosePoints(cK, radius * 0.10, 1200)
        tasks += CurveTask(FloatArray(cxs.size) { cx + cxs[it] }, FloatArray(cys.size) { cy + cys[it] }, 0.0, 0.2, 1.5f * weightMul, 1)
    }

    /** Gielis superformula presets, from sharp stars to rounded polygons. */
    fun superformula(
        tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext,
        rng: SeededRng, layerCount: Int, symmetry: Int, rippleAmount: Float, weightMul: Float,
    ) {
        val presets = arrayOf(
            doubleArrayOf(5.0, 0.25, 0.25, 0.25), doubleArrayOf(4.0, 0.25, 0.25, 0.25), doubleArrayOf(3.0, 0.25, 0.25, 0.25),
            doubleArrayOf(8.0, 0.25, 0.25, 0.25), doubleArrayOf(6.0, 0.3, 0.3, 0.3), doubleArrayOf(5.0, 1.0, 1.0, 1.0),
            doubleArrayOf(3.0, 1.0, 1.0, 1.0), doubleArrayOf(4.0, 1.0, 1.0, 1.0), doubleArrayOf(5.0, 2.0, 7.0, 7.0),
            doubleArrayOf(3.0, 2.0, 7.0, 7.0), doubleArrayOf(7.0, 2.0, 7.0, 7.0), doubleArrayOf(4.0, 8.0, 8.0, 8.0),
            doubleArrayOf(3.0, 8.0, 8.0, 8.0), doubleArrayOf(6.0, 8.0, 8.0, 8.0), doubleArrayOf(12.0, 15.0, 15.0, 15.0),
            doubleArrayOf(2.0, 1.0, 4.0, 8.0), doubleArrayOf(4.0, 1.0, 0.5, 8.0), doubleArrayOf(6.0, 1.0, 0.5, 0.3),
            doubleArrayOf(7.0, 3.0, 4.0, 17.0), doubleArrayOf(5.0, 0.5, 0.5, 4.0), doubleArrayOf(4.0, 2.0, 2.0, 2.0),
            doubleArrayOf(10.0, 0.3, 0.3, 0.3),
        )
        val steps = 2000
        for (li in 0 until layerCount) {
            val p = presets[(li + (ctx.seed % 19uL).toInt()) % presets.size]
            val m = p[0]; val n1 = p[1]; val n2 = p[2]; val n3 = p[3]
            val curveR = radius * (0.2 + rng.nextDouble() * 0.8)
            val xs = FloatArray(steps)
            val ys = FloatArray(steps)
            var valid = true
            for (j in 0 until steps) {
                val theta = j.toDouble() / steps * PI * 2.0
                val t1 = abs(cos(m * theta / 4.0)).pow(n2)
                val t2 = abs(sin(m * theta / 4.0)).pow(n3)
                val sum = t1 + t2
                if (sum <= 1e-10) { valid = false; break }
                val r = sum.pow(-1.0 / n1)
                if (!r.isFinite() || r >= 50.0) { valid = false; break }
                xs[j] = (curveR * r * cos(theta)).toFloat()
                ys[j] = (curveR * r * sin(theta)).toFloat()
            }
            if (!valid) continue
            var maxR = 0f
            for (j in 0 until steps) maxR = max(maxR, hypot(xs[j], ys[j]))
            if (maxR > radius.toFloat() * 1.02f) {
                val scale = radius.toFloat() * 0.92f / maxR
                for (j in 0 until steps) { xs[j] *= scale; ys[j] *= scale }
            }
            val tOffset = rng.nextDouble()
            val densityMul = (ctx.density * 1.6 + 0.3).toFloat()
            val weight = rng.nextDouble(0.5, 1.8).toFloat() * ctx.complexity.toFloat() * weightMul * densityMul
            val thickness = rng.nextDouble(1.0, 3.0).toInt()
            val ripSeed = ctx.seed + (li * 41 + 3).toULong()
            for (sym in 0 until symmetry) {
                val (rxs, rys) = rotated(xs, ys, cx, cy, sym * PI * 2.0 / symmetry)
                if (rippleAmount > 0) applyRipple(rxs, rys, rippleAmount, ripSeed + sym.toULong())
                tasks += CurveTask(rxs, rys, tOffset + sym * 0.08, ctx.colorDrift, weight, thickness)
            }
        }
    }
}
