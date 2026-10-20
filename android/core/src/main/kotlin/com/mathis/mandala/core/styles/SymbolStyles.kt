package com.mathis.mandala.core.styles

import com.mathis.mandala.core.ColorPalette
import com.mathis.mandala.core.SeededRng
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val PI_F = PI.toFloat()

/**
 * The Symbols style: rings of sacred and universal symbols around a central one. A polyline is a
 * FloatArray of interleaved x, y in a unit space. The macOS app picks the type with `% 20`, so only
 * the first 20 of its symbols ever appear; those are ported.
 */
object SymbolStyles {

    private fun poly(vararg p: Float) = p

    private fun circle(steps: Int, r: Float = 1f, ox: Float = 0f, oy: Float = 0f): FloatArray {
        val s = PI_F * 2.0f / steps
        return FloatArray(steps * 2) { k -> val i = k / 2; if (k % 2 == 0) ox + cos(i * s) * r else oy + sin(i * s) * r }
    }

    private fun arc(start: Float, end: Float, steps: Int, r: Float = 1f): FloatArray {
        val range = end - start
        return FloatArray((steps + 1) * 2) { k ->
            val t = start + (k / 2).toFloat() / steps * range
            if (k % 2 == 0) cos(t) * r else sin(t) * r
        }
    }

    private inline fun curve(n: Int, f: (Int) -> Pair<Float, Float>): FloatArray {
        val out = FloatArray(n * 2)
        for (i in 0 until n) { val (x, y) = f(i); out[i * 2] = x; out[i * 2 + 1] = y }
        return out
    }

    private fun shifted(p: FloatArray, dx: Float, dy: Float) = FloatArray(p.size) { if (it % 2 == 0) p[it] + dx else p[it] + dy }

    private fun closed(p: FloatArray) = p + floatArrayOf(p[0], p[1])

    /** Parts of symbol `type`, each with whether it is drawn closed, and the scale factor the macOS app applies. */
    private fun symbol(type: Int, complexity: Double): Pair<List<FloatArray>, Float> = when (type) {
        0 -> listOf(curve(101) { i ->
            val t = i / 100.0f * PI_F * 2
            sin(t).pow(3) to -(0.8125f * cos(t) - 0.3125f * cos(2 * t) - 0.125f * cos(3 * t) - 0.0625f * cos(4 * t))
        }) to 1f
        1 -> listOf(circle(80), poly(0f, -1f, 0f, 0f), poly(0f, 0f, -0.866f, 0.5f), poly(0f, 0f, 0.866f, 0.5f)) to 1f
        2 -> listOf(curve(121) { i ->
            val t = i / 120.0f * PI_F * 2
            val d = 1.0f + sin(t) * sin(t)
            cos(t) / d to sin(t) * cos(t) / d
        }) to 1.6f
        3 -> listOf(
            closed(poly(-0.866f, 0.5f, 0.866f, 0.5f, 0f, -1.0f)),
            closed(curve(60) { i -> val t = i / 59.0f * PI_F * 2; cos(t) * 0.32f to sin(t) * 0.16f - 0.08f }),
            closed(shifted(circle(16, 0.11f), 0f, -0.08f)),
        ) to 1f
        4 -> listOf(
            circle(100),
            curve(40) { i -> val t = i / 39.0f * PI_F; sin(t) * 0.5f to cos(t) * 0.5f },
            curve(40) { i -> val t = i / 39.0f * PI_F; -sin(t) * 0.5f to -cos(t) * 0.5f },
            shifted(circle(20, 0.25f), 0f, 0.5f),
            shifted(circle(20, 0.25f), 0f, -0.5f),
        ) to 1f
        5 -> listOf(circle(80) + shifted(arc(-PI_F * 0.62f, PI_F * 0.62f, 50, 0.84f), 0.32f, 0f)) to 1f
        6 -> listOf(closed(poly(0f, -1f, 0.866f, 0.5f, -0.866f, 0.5f)), closed(poly(0f, 1f, 0.866f, -0.5f, -0.866f, -0.5f))) to 1f
        7 -> {
            val v = FloatArray(10) { k -> val t = (k / 2).toFloat() * PI_F * 2.0f / 5.0f - PI_F / 2.0f; if (k % 2 == 0) cos(t) else sin(t) }
            listOf(closed(floatArrayOf(v[0], v[1], v[4], v[5], v[8], v[9], v[2], v[3], v[6], v[7]))) to 1f
        }
        8 -> listOf(
            poly(0f, -0.18f, 0f, 1.0f), poly(-0.62f, 0.16f, 0.62f, 0.16f),
            curve(60) { i -> val t = i / 60.0f * PI_F * 2; cos(t) * 0.42f to sin(t) * 0.46f - 0.54f },
        ) to 1f
        9 -> listOf(
            curve(55) { i -> val t = PI_F * 0.1f + i / 54.0f * PI_F * 1.3f; cos(t) * 0.72f to sin(t) * 0.56f },
            curve(32) { i -> val t = -PI_F * 0.2f + i / 31.0f * PI_F * 1.4f; cos(t) * 0.36f + 0.2f to sin(t) * 0.30f + 0.36f },
            curve(28) { i -> val t = i / 27.0f * PI_F; cos(t) * 0.26f - 0.36f to -sin(t) * 0.32f - 0.54f },
            poly(-0.16f, -0.88f, 0.16f, -0.88f),
            shifted(circle(14, 0.06f), 0f, -0.80f),
        ) to 1f
        10 -> {
            val petals = intArrayOf(4, 6, 8, 10, 12, 16)[min(5, (complexity * 6).toInt())]
            val parts = arrayListOf(circle(50, 0.42f), circle(50, 1.0f), circle(50, 1.45f))
            for (i in 0 until petals) {
                val a = i.toFloat() * PI_F * 2.0f / petals
                parts += curve(22) { j ->
                    val t = j / 21.0f * PI_F
                    val r = sin(t) * 0.42f
                    val pa = a + (t - PI_F / 2) * 0.42f
                    cos(pa) * (1.0f + r) to sin(pa) * (1.0f + r)
                }
            }
            parts to 1f
        }
        11 -> {
            val centres = arrayListOf(0f to 0f)
            for (i in 0 until 6) { val a = i.toFloat() * PI_F / 3.0f; centres += cos(a) to sin(a) }
            centres.map { (ox, oy) -> curve(40) { i -> val t = i / 39.0f * PI_F * 2; cos(t) * 0.52f + ox to sin(t) * 0.52f + oy } } to 0.52f
        }
        12 -> {
            val parts = arrayListOf(circle(70), circle(30, 0.28f))
            for (i in 0 until 8) {
                val a = i.toFloat() * PI_F / 4.0f
                parts += poly(cos(a) * 0.28f, sin(a) * 0.28f, cos(a) * 0.95f, sin(a) * 0.95f)
                parts += arc(a + 0.18f, a + 0.32f, 8, 0.7f)
            }
            parts to 1f
        }
        13 -> listOf(
            poly(0f, -1.0f, 0.866f, 0.5f, -0.866f, 0.5f), poly(0f, 1.0f, 0.866f, -0.5f, -0.866f, -0.5f),
            poly(0f, -0.55f, 0.476f, 0.275f, -0.476f, 0.275f), poly(0f, 0.55f, 0.476f, -0.275f, -0.476f, -0.275f),
            circle(60),
        ).map { closed(it) } to 1f
        14 -> {
            val parts = ArrayList<FloatArray>()
            for (k in 0 until 3) {
                val base = k.toFloat() * PI_F * 2.0f / 3.0f
                val ox = cos(base + PI_F * 2.0f / 3.0f) * 0.3f
                val oy = sin(base + PI_F * 2.0f / 3.0f) * 0.3f
                parts += curve(61) { i -> val t = base + i / 60.0f * PI_F * 4.0f / 3.0f; ox + cos(t) * 0.55f to oy + sin(t) * 0.55f }
            }
            parts += circle(50, 1.0f)
            parts to 1f
        }
        15 -> listOf(
            curve(91) { i -> val t = i / 90.0f * PI_F * 1.85f; cos(t) to sin(t) },
            poly(1.0f, 0.0f, 1.15f, -0.1f, 1.0f, -0.18f, 0.85f, -0.1f, 1.0f, 0.0f),
        ) to 1f
        16 -> {
            val parts = ArrayList<FloatArray>()
            for (k in 0 until 3) {
                val offset = k.toFloat() * PI_F * 2.0f / 3.0f
                parts += curve(51) { i -> val t = i / 50.0f; val r = t * 0.85f; val th = t * PI_F * 2.5f + offset; cos(th) * r to sin(th) * r }
            }
            parts += circle(30, 0.12f)
            parts to 1f
        }
        17 -> listOf(shifted(circle(70), -0.5f, 0f), shifted(circle(70), 0.5f, 0f)) to 0.75f
        18 -> {
            val parts = arrayListOf(circle(50, 0.55f))
            for ((fx, fy) in listOf(0.0f to -1.0, -0.28f to -0.96, -0.52f to -0.8, 0.28f to -0.96, 0.52f to -0.8)) {
                parts += poly(fx, fy.toFloat() * 0.55f, fx, fy.toFloat())
            }
            parts += circle(20, 0.22f)
            parts += circle(12, 0.10f)
            parts to 1f
        }
        else -> {
            val parts = arrayListOf(circle(70, 1.0f), circle(60, 0.85f))
            for (i in 0 until 4) {
                val s = (1.0 - i * 0.18).toFloat()
                parts += poly(0f, -s, s * 0.866f, s * 0.5f, -s * 0.866f, s * 0.5f)
            }
            for (i in 0 until 5) {
                val s = (0.9 - i * 0.15).toFloat()
                parts += poly(0f, s, s * 0.866f, -s * 0.5f, -s * 0.866f, -s * 0.5f)
            }
            parts += circle(12, 0.08f)
            parts.map { closed(it) } to 1f
        }
    }

    private fun drawLines(lines: LineList, pts: FloatArray, bx: Float, by: Float, scale: Float, rotation: Float, c: FloatArray, weight: Float) {
        if (pts.size < 4) return
        val ca = cos(rotation)
        val sa = sin(rotation)
        var px = bx + (pts[0] * ca - pts[1] * sa) * scale
        var py = by + (pts[0] * sa + pts[1] * ca) * scale
        var i = 2
        while (i < pts.size) {
            val x = bx + (pts[i] * ca - pts[i + 1] * sa) * scale
            val y = by + (pts[i] * sa + pts[i + 1] * ca) * scale
            lines.add(px, py, x, y, c, weight)
            px = x; py = y
            i += 2
        }
    }

    private fun drawSymbol(lines: LineList, type: Int, complexity: Double, bx: Float, by: Float, scale: Float, rot: Float, c: FloatArray, wt: Float) {
        val (parts, factor) = symbol(type % 20, complexity)
        for (p in parts) drawLines(lines, p, bx, by, scale * factor, rot, c, wt)
    }

    fun symbols(lines: LineList, cx: Float, cy: Float, radius: Double, ctx: StyleContext, palette: ColorPalette, rng: SeededRng, colorOffset: Double, symmetry: Int) {
        val bigR = radius.toFloat()
        val density = ctx.density
        val complexity = ctx.complexity
        val baseW = max(0.5f, (0.6 + complexity * 1.1).toFloat() * bigR / 900.0f)
        fun col(t: Double) = FloatArray(3).also { palette.colorAt((t + colorOffset) % 1.0, it) }
        fun scaled(c: FloatArray, k: Float) = floatArrayOf(c[0] * k, c[1] * k, c[2] * k)
        val unitCircle44 = closed(circle(44))

        val baseType = (rng.next() % 20uL).toInt()
        val ringCount = max(1, (density * 5.0).toInt() + 1)
        val symbolScale = bigR * (0.07 + (1.0 - density) * 0.14).toFloat()
        for (ring in 0 until ringCount) {
            val ringFrac = if (ringCount > 1) ring.toDouble() / (ringCount - 1) else 0.5
            val ringRadius = bigR * (0.14 + ringFrac * 0.78).toFloat()
            val countInRing = max(symmetry, ((density * 9).toInt() + symmetry) / symmetry * symmetry)
            val scale = symbolScale * (1.0 - ringFrac * 0.32).toFloat()
            val ringColorT = ringFrac * 0.72 + ring * 0.09
            val ringType = (baseType + ring * 3) % 20
            for (si in 0 until countInRing) {
                val frac = si.toDouble() / countInRing
                val angle = frac.toFloat() * PI_F * 2
                val sx = cx + cos(angle) * ringRadius
                val sy = cy + sin(angle) * ringRadius
                val ct = (ringColorT + frac * ctx.colorDrift) % 1.0
                val c = col(ct)
                val wt = baseW * (0.85 + complexity * 0.75).toFloat()
                drawSymbol(lines, ringType, complexity, sx, sy, scale, angle + PI_F / 2, c, wt)
                if (complexity > 0.35) {
                    val rc = col((ct + 0.14) % 1.0)
                    drawLines(lines, unitCircle44, sx, sy, scale * 1.3f, 0f, scaled(rc, 0.55f), wt * 0.4f)
                    if (complexity > 0.65) drawLines(lines, unitCircle44, sx, sy, scale * 1.65f, 0f, scaled(rc, 0.28f), wt * 0.28f)
                }
            }
        }
        val centerType = (baseType + 7) % 20
        val centerScale = bigR * (0.21 + complexity * 0.13).toFloat()
        drawSymbol(lines, centerType, complexity, cx, cy, centerScale, 0f, col(0.5), baseW * 2.2f)
        drawSymbol(lines, (centerType + 5) % 20, complexity, cx, cy, centerScale * 0.5f, PI_F / 6, col(0.25), baseW * 1.4f)
        val unitCircle60 = closed(circle(60))
        for (ri in 0 until 2 + (complexity * 3).toInt()) {
            val rr = centerScale * (1.3 + ri * 0.4).toFloat()
            val rc = col((0.5 + ri * 0.12) % 1.0)
            drawLines(lines, unitCircle60, cx, cy, rr, 0f, scaled(rc, 0.5f), baseW * 0.5f)
        }
    }
}
