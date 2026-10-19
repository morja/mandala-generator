package com.mathis.mandala.core.styles

import com.mathis.mandala.core.SeededRng
import com.mathis.mandala.core.styles.CurveStyles.applyRipple
import com.mathis.mandala.core.styles.CurveStyles.rotated
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val PI_F = PI.toFloat()

/** Pattern styles from MandalaRenderer.swift: Spider Web, Weave, Sacred Geometry, Radial Mesh, Flow Field, Tendril, Moiré, Voronoi. */
object PatternStyles {

    /** Adds one symmetry copy per sector of the centred points (xs, ys). */
    private fun addSym(
        tasks: MutableList<CurveTask>, xs: FloatArray, ys: FloatArray, cx: Float, cy: Float, symmetry: Int,
        tOffset: Double, drift: Double, weight: Float, ripple: ((Int) -> ULong)? = null, rippleAmount: Float = 0f,
    ) {
        for (sym in 0 until symmetry) {
            val (rxs, rys) = rotated(xs, ys, cx, cy, sym * PI * 2 / symmetry)
            if (ripple != null && rippleAmount > 0) applyRipple(rxs, rys, rippleAmount, ripple(sym))
            tasks += CurveTask(rxs, rys, tOffset, drift, weight, 1)
        }
    }

    private fun circle(steps: Int, r: Float, ox: Float = 0f, oy: Float = 0f): Pair<FloatArray, FloatArray> {
        val xs = FloatArray(steps + 1)
        val ys = FloatArray(steps + 1)
        for (k in 0..steps) {
            val a = k.toFloat() / steps * PI_F * 2
            xs[k] = ox + cos(a) * r
            ys[k] = oy + sin(a) * r
        }
        return xs to ys
    }

    /** Radial spokes plus concentric polygon rings. */
    fun spiderWeb(tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext, rng: SeededRng, symmetry: Int, ripple: Float, weightMul: Float) {
        val bigR = radius.toFloat()
        val nSpokes = max(6, symmetry * (2 + (ctx.density * 4).toInt()))
        val nRings = (4 + ctx.complexity * 14).toInt()
        val tOffset = rng.nextDouble()
        val weight = rng.nextDouble(0.4, 1.0).toFloat() * weightMul
        val ripSeed = ctx.seed + 0xABCD1234uL
        for (spoke in 0 until nSpokes) {
            val a = spoke.toFloat() / nSpokes * PI_F * 2
            addSym(tasks, floatArrayOf(0f, cos(a) * bigR), floatArrayOf(0f, sin(a) * bigR), cx, cy, symmetry,
                tOffset + spoke.toDouble() / nSpokes, ctx.colorDrift, weight * 0.6f)
        }
        for (ring in 0 until nRings) {
            val ringR = bigR * (ring + 1) / (nRings + 1)
            val xs = FloatArray(nSpokes + 1)
            val ys = FloatArray(nSpokes + 1)
            for (spoke in 0..nSpokes) {
                val a = (spoke % nSpokes).toFloat() / nSpokes * PI_F * 2
                xs[spoke] = cos(a) * ringR
                ys[spoke] = sin(a) * ringR
            }
            addSym(tasks, xs, ys, cx, cy, symmetry, tOffset + ring.toDouble() / nRings, ctx.colorDrift, weight,
                { sym -> ripSeed + (ring * 11).toULong() + sym.toULong() }, ripple)
        }
    }

    /** Interlocking sinusoidal circular strands, each in both directions. */
    fun weave(tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext, rng: SeededRng, symmetry: Int, ripple: Float, weightMul: Float) {
        val bigR = radius.toFloat()
        val nStrands = 3 + (ctx.density * 8).toInt()
        val freq = nStrands.toFloat()
        val steps = 400
        val tOffset = rng.nextDouble()
        val weight = rng.nextDouble(0.5, 1.2).toFloat() * ctx.complexity.toFloat() * weightMul
        val ripSeed = ctx.seed + 0xDEAD1234uL
        for (i in 0 until nStrands) {
            val baseR = bigR * (0.2 + 0.7 * (i + 1).toDouble() / (nStrands + 1)).toFloat()
            val amplitude = baseR * 0.12f
            val phaseOffset = i.toFloat() / nStrands * PI_F * 2
            for (direction in 0 until 2) {
                val dir = if (direction == 0) 1f else -1f
                val xs = FloatArray(steps + 1)
                val ys = FloatArray(steps + 1)
                for (k in 0..steps) {
                    val a = k.toFloat() / steps * PI_F * 2 * dir
                    val r = baseR + amplitude * sin(freq * a + phaseOffset + dir * PI_F * 0.25f)
                    xs[k] = cos(a) * r
                    ys[k] = sin(a) * r
                }
                addSym(tasks, xs, ys, cx, cy, symmetry, tOffset + i.toDouble() / nStrands * 0.5 + direction * 0.3, ctx.colorDrift, weight,
                    { sym -> ripSeed + (i * 23 + direction * 100 + sym).toULong() }, ripple)
            }
        }
    }

    /** Flower of Life circles, nested polygons and Metatron lines. */
    fun sacredGeometry(tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext, rng: SeededRng, symmetry: Int, weightMul: Float) {
        val bigR = radius.toFloat()
        val circleR = bigR * (0.15 + ctx.density * 0.20).toFloat()
        val tOffset = rng.nextDouble()
        val weight = rng.nextDouble(0.5, 1.0).toFloat() * weightMul
        val circStep = 48

        circle(circStep, circleR).let { (xs, ys) -> addSym(tasks, xs, ys, cx, cy, symmetry, tOffset, ctx.colorDrift, weight) }

        val centres = arrayListOf(0f to 0f)
        for (i in 0 until 6) {
            val a = i.toFloat() / 6 * PI_F * 2
            val px = cos(a) * circleR
            val py = sin(a) * circleR
            centres += px to py
            val (xs, ys) = circle(circStep, circleR, px, py)
            addSym(tasks, xs, ys, cx, cy, symmetry, tOffset + i / 6.0 * 0.4, ctx.colorDrift, weight)
        }
        if (ctx.density > 0.35) {
            for (i in 0 until 6) {
                val a = i.toFloat() / 6 * PI_F * 2 + PI_F / 6
                val px = cos(a) * circleR * 2
                val py = sin(a) * circleR * 2
                centres += px to py
                val (xs, ys) = circle(circStep, circleR, px, py)
                addSym(tasks, xs, ys, cx, cy, symmetry, tOffset + 0.5 + i / 6.0 * 0.3, ctx.colorDrift, weight * 0.8f)
            }
        }
        val maxSides = 3 + (ctx.complexity * 6).toInt()
        for (sides in 3..maxSides) {
            val polyR = bigR * (0.08 + (sides - 3).toDouble() / (maxSides - 2) * 0.88).toFloat()
            val xs = FloatArray(sides + 1)
            val ys = FloatArray(sides + 1)
            for (k in 0..sides) {
                val a = (k % sides).toFloat() / sides * PI_F * 2
                xs[k] = cos(a) * polyR
                ys[k] = sin(a) * polyR
            }
            addSym(tasks, xs, ys, cx, cy, symmetry, tOffset + sides * 0.1, ctx.colorDrift, weight * 0.7f)
        }
        if (ctx.complexity > 0.45) {
            for (ai in centres.indices) {
                for (bi in ai + 1 until centres.size) {
                    addSym(tasks, floatArrayOf(centres[ai].first, centres[bi].first), floatArrayOf(centres[ai].second, centres[bi].second),
                        cx, cy, symmetry, tOffset + (ai * 7 + bi) * 0.02, ctx.colorDrift, weight * 0.45f)
                }
            }
        }
    }

    /** Polar grid: concentric rings (optionally waved by ripple) and spokes scaled by the spokes setting. */
    fun radialMesh(tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext, rng: SeededRng, symmetry: Int, ripple: Float, weightMul: Float) {
        val bigR = radius.toFloat()
        val nRings = (3 + ctx.complexity * 15).toInt()
        val nSpokes = max(8, symmetry * (2 + (ctx.density * 4).toInt()))
        val tOffset = rng.nextDouble()
        val weight = rng.nextDouble(0.3, 0.9).toFloat() * weightMul
        val ripSeed = ctx.seed + 0xB00B5678uL
        val distort = ripple * 0.5f
        for (ring in 0 until nRings) {
            val baseR = bigR * (ring + 1) / (nRings + 1)
            val steps = max(60, nSpokes * 3)
            val xs = FloatArray(steps + 1)
            val ys = FloatArray(steps + 1)
            for (k in 0..steps) {
                val a = k.toFloat() / steps * PI_F * 2
                val dist = if (distort > 0) sin((nSpokes / 2).toFloat() * a + ring.toFloat()) * distort * baseR * 0.2f else 0f
                val r = baseR + dist
                xs[k] = cos(a) * r
                ys[k] = sin(a) * r
            }
            addSym(tasks, xs, ys, cx, cy, symmetry, tOffset + ring.toDouble() / nRings, ctx.colorDrift, weight,
                { sym -> ripSeed + (ring * 13).toULong() + sym.toULong() }, ripple)
        }
        val spokeWeight = ctx.spokes.coerceIn(0.0, 1.0).toFloat()
        if (spokeWeight <= 0f) return
        for (spoke in 0 until nSpokes) {
            val a = spoke.toFloat() / nSpokes * PI_F * 2
            addSym(tasks, floatArrayOf(0f, cos(a) * bigR), floatArrayOf(0f, sin(a) * bigR), cx, cy, symmetry,
                tOffset + spoke.toDouble() / nSpokes * ctx.colorDrift, ctx.colorDrift, weight * 0.5f * spokeWeight)
        }
    }

    /** Particle traces through a sine/cosine vector field. */
    fun flowField(tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext, rng: SeededRng, symmetry: Int, ripple: Float, weightMul: Float) {
        val bigR = radius.toFloat()
        val nParticles = (20 + ctx.density * 80).toInt()
        val traceSteps = (100 + ctx.complexity * 300).toInt()
        val stepSize = bigR / traceSteps * 3.5f
        val scale1 = (2.0 + rng.nextDouble() * 2.0).toFloat() / bigR
        val scale2 = scale1 * (1.5 + rng.nextDouble()).toFloat()
        val phase1 = rng.nextDouble().toFloat() * PI_F * 2
        val phase2 = rng.nextDouble().toFloat() * PI_F * 2
        val tOffset = rng.nextDouble()
        val weight = rng.nextDouble(0.4, 1.1).toFloat() * ctx.complexity.toFloat() * weightMul
        val ripSeed = ctx.seed + 0xF10F1234uL
        for (p in 0 until nParticles) {
            val startAngle = rng.nextDouble().toFloat() * PI_F * 2
            val startR = bigR * (rng.nextDouble() * 0.8 + 0.1).toFloat()
            var x = cos(startAngle) * startR
            var y = sin(startAngle) * startR
            val xs = arrayListOf(x)
            val ys = arrayListOf(y)
            for (n in 0 until traceSteps) {
                val fieldAngle = sin(x * scale1 + phase1) * cos(y * scale1) * PI_F + sin(x * scale2 + phase2) * PI_F * 0.5f
                x += cos(fieldAngle) * stepSize
                y += sin(fieldAngle) * stepSize
                if (x * x + y * y > bigR * bigR) break
                xs += x; ys += y
            }
            if (xs.size < 2) continue
            addSym(tasks, xs.toFloatArray(), ys.toFloatArray(), cx, cy, symmetry, tOffset + p.toDouble() / nParticles, ctx.colorDrift, weight,
                { sym -> ripSeed + (p * 17).toULong() + sym.toULong() }, ripple)
        }
    }

    private class Branch(val x: Float, val y: Float, val angle: Float, val length: Float, val depth: Int)

    /** Recursive branching curves (depth-first, like the Swift stack). */
    fun tendril(tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext, rng: SeededRng, symmetry: Int, ripple: Float, weightMul: Float) {
        val bigR = radius.toFloat()
        val maxDepth = 2 + (ctx.complexity * 3).toInt()
        val nTrunks = 1 + (ctx.density * 5).toInt()
        val tOffset = rng.nextDouble()
        val weight = rng.nextDouble(0.5, 1.2).toFloat() * weightMul
        val ripSeed = ctx.seed + 0xBEEF4321uL
        val stack = ArrayList<Branch>()
        for (trunk in 0 until nTrunks) {
            val trunkAngle = trunk.toFloat() / nTrunks * PI_F * 2 + rng.nextDouble().toFloat() * 0.5f
            stack += Branch(0f, 0f, trunkAngle, bigR * (0.4 + rng.nextDouble() * 0.5).toFloat(), 0)
        }
        while (stack.isNotEmpty()) {
            val branch = stack.removeAt(stack.lastIndex)
            if (branch.depth > maxDepth) continue
            val curvature = (rng.nextDouble() - 0.5).toFloat() * 0.8f
            val segSteps = 20
            val xs = FloatArray(segSteps + 1)
            val ys = FloatArray(segSteps + 1)
            xs[0] = branch.x; ys[0] = branch.y
            var curAngle = branch.angle
            var curX = branch.x
            var curY = branch.y
            val seg = branch.length / segSteps
            for (k in 1..segSteps) {
                curAngle += curvature * 0.06f
                curX += cos(curAngle) * seg
                curY += sin(curAngle) * seg
                xs[k] = curX; ys[k] = curY
            }
            val w = weight * 0.65f.pow(branch.depth.toFloat())
            addSym(tasks, xs, ys, cx, cy, symmetry, tOffset + branch.depth * 0.2, ctx.colorDrift, w, { sym -> ripSeed + sym.toULong() }, ripple)
            if (branch.depth < maxDepth) {
                val spread = PI_F * (0.3 + rng.nextDouble() * 0.4).toFloat()
                val childL = branch.length * (0.5 + rng.nextDouble() * 0.2).toFloat()
                for (b in 0 until 2) {
                    val childAngle = curAngle + (if (b == 0) spread else -spread) * (0.5 + rng.nextDouble() * 0.5).toFloat()
                    stack += Branch(curX, curY, childAngle, childL, branch.depth + 1)
                }
            }
        }
    }

    /** Two offset concentric ring systems. */
    fun moire(tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext, rng: SeededRng, symmetry: Int, ripple: Float, weightMul: Float) {
        val bigR = radius.toFloat()
        val nRings = (10 + ctx.complexity * 30).toInt()
        val offset = bigR * (0.05 + ctx.density * 0.25).toFloat()
        val tOffset = rng.nextDouble()
        val weight = rng.nextDouble(0.3, 0.8).toFloat() * weightMul * 0.6f
        val ripSeed = ctx.seed + 0xC0C0C0C0uL
        val centres = arrayOf(offset to 0f, -offset to 0f)
        for ((cIdx, centre) in centres.withIndex()) {
            for (ring in 0 until nRings) {
                val ringR = bigR * ring / (nRings - 1)
                if (ringR <= 1) continue
                val (xs, ys) = circle(max(60, (ringR * 1.5f).toInt()), ringR, centre.first, centre.second)
                addSym(tasks, xs, ys, cx, cy, symmetry,
                    tOffset + cIdx * 0.5 + ring.toDouble() / nRings * ctx.colorDrift * 0.5, ctx.colorDrift * 0.2, weight,
                    { sym -> ripSeed + (ring * 19 + cIdx * 1000 + sym).toULong() }, ripple)
            }
        }
    }

    /** Perpendicular bisectors between random seed points, clipped to the circle. */
    fun voronoi(tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext, rng: SeededRng, symmetry: Int, ripple: Float, weightMul: Float) {
        val bigR = radius.toFloat()
        val nSeeds = 4 + (ctx.density * 12).toInt()
        val tOffset = rng.nextDouble()
        val weight = rng.nextDouble(0.4, 1.0).toFloat() * weightMul
        val ripSeed = ctx.seed + 0xF0F12345uL
        val sx = FloatArray(nSeeds)
        val sy = FloatArray(nSeeds)
        for (i in 0 until nSeeds) {
            val r = rng.nextDouble().toFloat() * bigR * 0.85f
            val a = rng.nextDouble().toFloat() * PI_F * 2
            sx[i] = cos(a) * r
            sy[i] = sin(a) * r
        }
        for (ai in 0 until nSeeds) {
            for (bi in ai + 1 until nSeeds) {
                val mx = (sx[ai] + sx[bi]) * 0.5f
                val my = (sy[ai] + sy[bi]) * 0.5f
                val dx = sx[bi] - sx[ai]
                val dy = sy[bi] - sy[ai]
                val len = sqrt(dx * dx + dy * dy)
                if (len <= 2) continue
                val nx = -dy / len
                val ny = dx / len
                val ext = bigR * 1.5f
                val p0x = mx - nx * ext
                val p0y = my - ny * ext
                val ldx = (mx + nx * ext) - p0x
                val ldy = (my + ny * ext) - p0y
                val la = ldx * ldx + ldy * ldy
                val lb = 2 * (p0x * ldx + p0y * ldy)
                val lc = p0x * p0x + p0y * p0y - bigR * bigR
                val disc = lb * lb - 4 * la * lc
                if (disc < 0) continue
                val sqrtD = sqrt(disc)
                val t0 = max(0f, min(1f, (-lb - sqrtD) / (2 * la)))
                val t1 = max(0f, min(1f, (-lb + sqrtD) / (2 * la)))
                if (t1 <= t0) continue
                val tOff = tOffset + (ai * nSeeds + bi).toDouble() / (nSeeds * nSeeds) * ctx.colorDrift
                addSym(tasks, floatArrayOf(p0x + t0 * ldx, p0x + t1 * ldx), floatArrayOf(p0y + t0 * ldy, p0y + t1 * ldy),
                    cx, cy, symmetry, tOff, ctx.colorDrift, weight, { sym -> ripSeed + (ai * 37 + bi * 7).toULong() + sym.toULong() }, ripple)
            }
        }
    }
}
