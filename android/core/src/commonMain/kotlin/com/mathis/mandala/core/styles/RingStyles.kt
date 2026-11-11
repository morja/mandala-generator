package com.mathis.mandala.core.styles

import com.mathis.mandala.core.SeededRng
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val PI_F = PI.toFloat()

/**
 * Styles built on Android first (27 October 2026, AGENTS.md): rings of shapes around the centre, as
 * in the play field mock (DM-19, DM-20). Complexity sets the number of rings, density the size of
 * the shapes, ripple bends them; symmetry sets how many sit on a ring. No macOS reference yet.
 */
object RingStyles {

    /** Rings of teardrop petals, each ring turned half a step against the one inside it. */
    fun petalRings(
        tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext,
        rng: SeededRng, symmetry: Int, rippleAmount: Float, weightMul: Float,
    ) {
        val bigR = radius.toFloat()
        val rings = rings(ctx)
        for (r in 0 until rings) {
            val t = if (rings == 1) 1f else r.toFloat() / (rings - 1)
            val ringR = bigR * (0.18f + 0.52f * t)
            val len = bigR * (0.13f + 0.13f * t) * (0.6f + 0.8f * ctx.density.toFloat())
            val width = len * 0.45f
            val count = max(3, symmetry) * (2 + r % 2)
            val turn = if (r % 2 == 1) PI_F / count else 0f
            // Bent like a pinwheel by ripple, alternately left and right.
            val bend = rippleAmount * len * 0.5f * (if (r % 2 == 0) 1f else -1f)
            val weight = rng.nextDouble(1.2, 2.0).toFloat() * weightMul
            for (k in 0 until count) {
                val a = k * 2 * PI_F / count + turn
                // The outline, and a smaller one inside it, as in a drawn petal.
                for ((scale, part) in listOf(1f to 1f, 0.55f to 0.5f)) {
                    val xs = FloatArray(PETAL_STEPS + 1)
                    val ys = FloatArray(PETAL_STEPS + 1)
                    petal(xs, ys, cx, cy, a, ringR - len * 0.35f, len * scale, width * scale, bend * scale)
                    tasks += CurveTask(xs, ys, t * 0.6 + k.toDouble() / count * 0.1 + (1 - scale) * 0.15, ctx.colorDrift, weight * part, 2)
                }
            }
        }
    }

    /** A teardrop from radius `from` outwards along angle `a`: pointed at its base, round at its tip. */
    private fun petal(xs: FloatArray, ys: FloatArray, cx: Float, cy: Float, a: Float, from: Float, len: Float, width: Float, bend: Float) {
        val ca = cos(a)
        val sa = sin(a)
        val steps = xs.size - 1
        for (i in 0..steps) {
            val s = i * 2 * PI_F / steps
            val along = len * (0.5f - 0.5f * cos(s))
            val across = width * sin(s) * (1 - cos(s)) * 0.5f + bend * (along / len) * (along / len)
            val u = from + along
            xs[i] = cx + u * ca - across * sa
            ys[i] = cy + u * sa + across * ca
        }
    }

    /** Rings of small circles, each ring with more of them; ripple makes the circles wave. */
    fun loopRings(
        tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext,
        rng: SeededRng, symmetry: Int, rippleAmount: Float, weightMul: Float,
    ) {
        val bigR = radius.toFloat()
        val rings = rings(ctx)
        for (r in 0 until rings) {
            val t = if (rings == 1) 1f else r.toFloat() / (rings - 1)
            val ringR = bigR * (0.16f + 0.56f * t)
            val loopR = bigR * (0.05f + 0.07f * ctx.density.toFloat()) * (0.7f + 0.6f * t)
            val count = max(3, symmetry) * (2 + r)
            val waves = 3 + r
            val weight = rng.nextDouble(1.1, 1.8).toFloat() * weightMul
            val turn = rng.nextDouble().toFloat() * 2 * PI_F / count
            for (k in 0 until count) {
                val a = k * 2 * PI_F / count + turn
                val ox = cx + cos(a) * ringR
                val oy = cy + sin(a) * ringR
                val xs = FloatArray(LOOP_STEPS + 1)
                val ys = FloatArray(LOOP_STEPS + 1)
                for (i in 0..LOOP_STEPS) {
                    val s = i * 2 * PI_F / LOOP_STEPS
                    val rr = loopR * (1 + rippleAmount * 0.35f * sin(waves * s))
                    xs[i] = ox + rr * cos(s)
                    ys[i] = oy + rr * sin(s)
                }
                tasks += CurveTask(xs, ys, t * 0.7 + k.toDouble() / count * 0.15, ctx.colorDrift, weight, 2)
            }
        }
    }

    /** Two rings at the lowest complexity, six at the highest. */
    private fun rings(ctx: StyleContext): Int = 2 + (ctx.complexity * 4).toInt().coerceIn(0, 4)

    private const val PETAL_STEPS = 64
    private const val LOOP_STEPS = 48
}
