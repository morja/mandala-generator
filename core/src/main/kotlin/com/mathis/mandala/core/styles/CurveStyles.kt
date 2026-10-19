package com.mathis.mandala.core.styles

import com.mathis.mandala.core.SeededRng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** All data needed to draw one curve; collected first, then drawn in parallel. */
class CurveTask(
    val xs: FloatArray,
    val ys: FloatArray,
    val tOffset: Double,
    val drift: Double,
    val weight: Float,
    val thickness: Int,
)

/** Per-layer values the styles read. `seed` is the global seed, as in the macOS renderer. */
class StyleContext(
    val complexity: Double,
    val density: Double,
    val colorDrift: Double,
    val seed: ULong,
)

/** Ports of the curve-collecting styles from MandalaRenderer.swift. */
object CurveStyles {

    fun spirograph(
        tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext,
        rng: SeededRng, layerCount: Int, symmetry: Int, rippleAmount: Float, weightMul: Float,
    ) {
        val ratios = arrayOf(7.0 to 3.0, 5.0 to 2.0, 8.0 to 5.0, 9.0 to 4.0, 11.0 to 6.0, 7.0 to 4.0, 13.0 to 5.0, 6.0 to 5.0)
        for (i in 0 until layerCount) {
            val ratio = ratios[i % ratios.size]
            val bigR = radius * (0.5 + rng.nextDouble() * 0.5)
            val r = bigR * ratio.second / ratio.first
            val d = r * (0.4 + rng.nextDouble() * 0.7)
            val (xs, ys) = spiroPoints(bigR, r, d, 3000)
            val tOffset = rng.nextDouble()
            val weight = rng.nextDouble(0.4, 1.2).toFloat() * ctx.complexity.toFloat() * weightMul
            val thickness = rng.nextDouble(1.0, 3.0).toInt()
            val ripSeed = ctx.seed + (i * 31 + 1).toULong()
            for (sym in 0 until symmetry) {
                val angle = sym * PI * 2.0 / symmetry
                val (rxs, rys) = rotated(xs, ys, cx, cy, angle)
                if (rippleAmount > 0) applyRipple(rxs, rys, rippleAmount, ripSeed + sym.toULong())
                tasks += CurveTask(rxs, rys, tOffset + sym * 0.1, ctx.colorDrift, weight, thickness)
            }
        }
    }

    fun roseCurves(
        tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext,
        rng: SeededRng, layerCount: Int, symmetry: Int, rippleAmount: Float, weightMul: Float,
    ) {
        val kValues = intArrayOf(3, 4, 5, 6, 7, 8, 9, 2)
        for (i in 0 until layerCount) {
            val k = kValues[i % kValues.size]
            val r = radius * (0.5 + rng.nextDouble() * 0.5)
            val (xs, ys) = rosePoints(k, r, 2000)
            val tOffset = rng.nextDouble()
            val weight = rng.nextDouble(0.5, 1.4).toFloat() * ctx.complexity.toFloat() * weightMul
            val thickness = rng.nextDouble(1.0, 2.0).toInt()
            val rotOffset = rng.nextDouble() * PI * 2.0
            val ripSeed = ctx.seed + (i * 47 + 7).toULong()
            for (sym in 0 until symmetry) {
                val angle = sym * PI * 2.0 / symmetry + rotOffset
                val (rxs, rys) = rotated(xs, ys, cx, cy, angle)
                if (rippleAmount > 0) applyRipple(rxs, rys, rippleAmount, ripSeed + sym.toULong())
                tasks += CurveTask(rxs, rys, tOffset + sym * 0.07, ctx.colorDrift, weight, thickness)
            }
        }
    }

    fun phyllotaxis(
        tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext,
        rng: SeededRng, symmetry: Int, rippleAmount: Float, weightMul: Float,
    ) {
        val goldenAngle = 2.39996323f
        val n = (80 + ctx.density * 220).toInt()
        val bigR = radius.toFloat()
        val petalR = bigR * (0.025 + ctx.complexity * 0.045).toFloat()
        val tOffset = rng.nextDouble()
        val weight = rng.nextDouble(0.4, 1.1).toFloat() * weightMul
        val ripSeed = ctx.seed + 0xABCDEF01uL

        for (i in 0 until n) {
            val fi = i.toFloat()
            val r = sqrt(fi / n) * bigR * 0.95f
            val theta = fi * goldenAngle
            val px = cos(theta) * r
            val py = sin(theta) * r

            val petalSteps = 14
            val xs = FloatArray(petalSteps + 1)
            val ys = FloatArray(petalSteps + 1)
            val rotA = theta + PI.toFloat() * 0.5f
            val cosRot = cos(rotA)
            val sinRot = sin(rotA)
            val pr = petalR * (0.4f + 0.6f * sqrt(r / max(bigR, 1f)))
            for (k in 0..petalSteps) {
                val a = k.toFloat() / petalSteps * PI.toFloat() * 2
                val lx = cos(a) * pr
                val ly = sin(a) * pr * 1.5f
                xs[k] = lx * cosRot - ly * sinRot + px
                ys[k] = lx * sinRot + ly * cosRot + py
            }

            for (sym in 0 until symmetry) {
                val angle = sym * PI * 2 / symmetry
                val (rxs, rys) = rotated(xs, ys, cx, cy, angle)
                if (rippleAmount > 0) {
                    applyRipple(rxs, rys, rippleAmount, ripSeed + (i * 13).toULong() + sym.toULong())
                }
                tasks += CurveTask(
                    rxs, rys,
                    tOffset = tOffset + fi.toDouble() / n * 0.8,
                    drift = ctx.colorDrift,
                    weight = weight * (0.3f + 0.7f * sqrt(r / max(bigR, 1f))),
                    thickness = 1,
                )
            }
        }
    }

    fun hypocycloid(
        tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext,
        rng: SeededRng, layerCount: Int, symmetry: Int, rippleAmount: Float, weightMul: Float,
    ) {
        val configs = arrayOf(
            1.0 / 3.0 to 1.0 / 3.0, 1.0 / 4.0 to 1.0 / 4.0, 2.0 / 5.0 to 2.0 / 5.0,
            3.0 / 7.0 to 3.0 / 7.0, 1.0 / 3.0 to 2.0 / 3.0, 2.0 / 7.0 to 0.5,
            1.0 / 5.0 to 4.0 / 5.0, 3.0 / 8.0 to 3.0 / 8.0,
        )
        for (i in 0 until layerCount) {
            val cfg = configs[i % configs.size]
            val bigR = radius * (0.5 + rng.nextDouble() * 0.5)
            val r = bigR * cfg.first
            val d = bigR * cfg.second * (0.7 + rng.nextDouble() * 0.4)
            val (xs, ys) = spiroPoints(bigR, r, d, 3000)
            val tOffset = rng.nextDouble()
            val weight = rng.nextDouble(0.5, 1.3).toFloat() * ctx.complexity.toFloat() * weightMul
            val ripSeed = ctx.seed + (i * 41 + 7).toULong()
            for (sym in 0 until symmetry) {
                val angle = sym * PI * 2 / symmetry
                val (rxs, rys) = rotated(xs, ys, cx, cy, angle)
                if (rippleAmount > 0) applyRipple(rxs, rys, rippleAmount, ripSeed + sym.toULong())
                tasks += CurveTask(rxs, rys, tOffset + sym * 0.09, ctx.colorDrift, weight, 1)
            }
        }
    }

    fun waveInterference(
        tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext,
        rng: SeededRng, symmetry: Int, rippleAmount: Float, weightMul: Float,
    ) {
        val nSources = 2 + (ctx.density * 3).toInt()
        val nRings = (8 + ctx.complexity * 24).toInt()
        val bigR = radius.toFloat()

        for (s in 0 until nSources) {
            val srcAngle = s.toFloat() / nSources * PI.toFloat() * 2 + rng.nextDouble().toFloat() * 0.5f
            val srcR = bigR * (0.05 + rng.nextDouble() * 0.38).toFloat()
            val srcX = cos(srcAngle) * srcR
            val srcY = sin(srcAngle) * srcR
            val tOffset = rng.nextDouble()
            val weight = rng.nextDouble(0.3, 0.8).toFloat() * weightMul
            val ripSeed = ctx.seed + (s * 77 + 13).toULong()

            for (ring in 0 until nRings) {
                val ringR = bigR * (ring + 1) / nRings
                val steps = max(80, (ringR * 2).toInt())
                val xs = FloatArray(steps + 1)
                val ys = FloatArray(steps + 1)
                for (k in 0..steps) {
                    val a = k.toFloat() / steps * PI.toFloat() * 2
                    xs[k] = srcX + cos(a) * ringR
                    ys[k] = srcY + sin(a) * ringR
                }
                for (sym in 0 until symmetry) {
                    val angle = sym * PI * 2 / symmetry
                    val (rxs, rys) = rotated(xs, ys, cx, cy, angle)
                    if (rippleAmount > 0) {
                        applyRipple(rxs, rys, rippleAmount, ripSeed + (ring * 7).toULong() + sym.toULong())
                    }
                    tasks += CurveTask(
                        rxs, rys,
                        tOffset = tOffset + ring.toDouble() / nRings * ctx.colorDrift,
                        drift = ctx.colorDrift * 0.3,
                        weight = weight * (1f - ring.toFloat() / nRings * 0.5f),
                        thickness = 1,
                    )
                }
            }
        }
    }

    // ── Curve generators ─────────────────────────────────────────────────

    /** Hypotrochoid points; the spirograph and hypocycloid styles share this formula. */
    private fun spiroPoints(bigR: Double, r: Double, d: Double, steps: Int): Pair<FloatArray, FloatArray> {
        val xs = FloatArray(steps + 1)
        val ys = FloatArray(steps + 1)
        val diff = bigR - r
        val ratio = if (r == 0.0) 0.0 else diff / r
        val tMax = lcmPeriod(bigR, r) * 2.0 * PI
        val dt = tMax / steps
        for (i in 0..steps) {
            val t = i * dt
            xs[i] = (diff * cos(t) + d * cos(ratio * t)).toFloat()
            ys[i] = (diff * sin(t) - d * sin(ratio * t)).toFloat()
        }
        return xs to ys
    }

    private fun rosePoints(k: Int, radius: Double, steps: Int): Pair<FloatArray, FloatArray> {
        val xs = FloatArray(steps + 1)
        val ys = FloatArray(steps + 1)
        val period = if (k % 2 == 0) 2.0 * PI else PI
        val dt = period / steps
        for (i in 0..steps) {
            val theta = i * dt
            val r = radius * abs(cos(k * theta))
            xs[i] = (r * cos(theta)).toFloat()
            ys[i] = (r * sin(theta)).toFloat()
        }
        return xs to ys
    }

    /** Smallest p in 1..20 with p * R / r nearly integer, else 10. */
    private fun lcmPeriod(bigR: Double, r: Double): Double {
        if (r <= 0) return 1.0
        val ratio = bigR / r
        for (p in 1..20) {
            val v = p * ratio
            if (abs(v - roundHalfAwayFromZero(v)) < 0.01) return p.toDouble()
        }
        return 10.0
    }

    // Swift's Double.rounded() rounds half away from zero; Kotlin's round() rounds half to even.
    private fun roundHalfAwayFromZero(v: Double): Double =
        if (v >= 0) (v + 0.5).toLong().toDouble() else -((-v + 0.5).toLong().toDouble())

    private fun rotated(xs: FloatArray, ys: FloatArray, cx: Float, cy: Float, angle: Double): Pair<FloatArray, FloatArray> {
        val cosA = cos(angle).toFloat()
        val sinA = sin(angle).toFloat()
        val rxs = FloatArray(xs.size)
        val rys = FloatArray(ys.size)
        for (j in xs.indices) {
            rxs[j] = cx + xs[j] * cosA - ys[j] * sinA
            rys[j] = cy + xs[j] * sinA + ys[j] * cosA
        }
        return rxs to rys
    }

    /** Multi-frequency sine ripple on absolute coordinates. */
    private fun applyRipple(xs: FloatArray, ys: FloatArray, amount: Float, seed: ULong) {
        val n = xs.size
        if (n <= 1) return
        val f1 = 3.0f + (seed and 0x7uL).toFloat() * 0.5f
        val f2 = 7.0f + ((seed shr 4) and 0x7uL).toFloat() * 0.7f
        val f3 = 13.0f + ((seed shr 8) and 0x7uL).toFloat() * 0.9f
        val twoPi = PI.toFloat() * 2
        val p1 = (seed and 0xFFuL).toFloat() / 255.0f * twoPi
        val p2 = ((seed shr 8) and 0xFFuL).toFloat() / 255.0f * twoPi
        val p3 = ((seed shr 16) and 0xFFuL).toFloat() / 255.0f * twoPi
        val scale = amount * 18.0f
        for (i in 0 until n) {
            val t = i.toFloat() / n * twoPi
            val dx = (sin(t * f1 + p1) * 0.6f + sin(t * f2 + p2) * 0.3f + sin(t * f3 + p3) * 0.1f) * scale
            val dy = (cos(t * f1 + p1) * 0.6f + cos(t * f2 + p2) * 0.3f + cos(t * f3 + p3) * 0.1f) * scale
            xs[i] += dx
            ys[i] += dy
        }
    }
}
