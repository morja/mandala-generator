package com.mathis.mandala.core.styles

import com.mathis.mandala.core.SeededRng
import com.mathis.mandala.core.styles.CurveStyles.applyRipple
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val PI_F = PI.toFloat()

/** Projected 3D and 4D curve styles from MandalaRenderer.swift: Torus Knot, Sphere Grid, Tesseract. */
object ThreeDStyles {

    /** Symmetry copies of absolute screen points around (cx, cy), with the Float angle the Swift code uses. */
    private fun emitSym(
        tasks: MutableList<CurveTask>, xs: FloatArray, ys: FloatArray, cx: Float, cy: Float, symmetry: Int,
        rippleAmount: Float, seed: (Int) -> ULong, tOffset: (Int) -> Double, drift: Double, weight: Float,
    ) {
        for (sym in 0 until symmetry) {
            val angle = sym.toFloat() * PI_F * 2 / symmetry
            val cosA = cos(angle)
            val sinA = sin(angle)
            val rxs = FloatArray(xs.size)
            val rys = FloatArray(xs.size)
            for (k in xs.indices) {
                val dx = xs[k] - cx
                val dy = ys[k] - cy
                rxs[k] = cx + dx * cosA - dy * sinA
                rys[k] = cy + dx * sinA + dy * cosA
            }
            if (rippleAmount > 0) applyRipple(rxs, rys, rippleAmount, seed(sym))
            tasks += CurveTask(rxs, rys, tOffset(sym), drift, weight, 1)
        }
    }

    /** (p, q) torus knot drawn as strands along its tube plus cross-section rings, in perspective. */
    fun torusKnot(tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext, rng: SeededRng, symmetry: Int, ripple: Float, weightMul: Float) {
        val bigR = radius.toFloat() * 0.50f
        val r = bigR * 0.32f
        val pq = arrayOf(2 to 3, 2 to 5, 3 to 4, 3 to 5, 3 to 7, 4 to 5, 4 to 7, 5 to 6, 5 to 7, 7 to 9, 5 to 11, 4 to 9)
        val (p, q) = pq[min((ctx.complexity * (pq.size - 1)).toInt(), pq.size - 1)]
        val nSpine = 1800 + (ctx.complexity * 1200).toInt()
        val nTube = max(3, 2 + (ctx.density * 10).toInt())
        val tubeR = r * (0.10 + ctx.density * 0.25).toFloat()
        val tiltX = rng.nextDouble(0.18, 0.72).toFloat()
        val spinZ = (rng.nextDouble() * PI * 2).toFloat()
        val fov = bigR * 3.4f

        val sx = FloatArray(nSpine + 1)
        val sy = FloatArray(nSpine + 1)
        val sz = FloatArray(nSpine + 1)
        for (i in 0..nSpine) {
            val t = i.toFloat() / nSpine * PI_F * 2
            var x = (bigR + r * cos(q.toFloat() * t)) * cos(p.toFloat() * t)
            var y = (bigR + r * cos(q.toFloat() * t)) * sin(p.toFloat() * t)
            var z = r * sin(q.toFloat() * t)
            val x1 = x * cos(spinZ) - y * sin(spinZ)
            val y1 = x * sin(spinZ) + y * cos(spinZ)
            x = x1; y = y1
            val y2 = y * cos(tiltX) - z * sin(tiltX)
            val z2 = y * sin(tiltX) + z * cos(tiltX)
            y = y2; z = z2
            sx[i] = x; sy[i] = y; sz[i] = z
        }
        val zMin = sz.min()
        val zRange = max(0.001f, sz.max() - zMin)

        val frame = FloatArray(9)
        fun frenet(i: Int) {
            val a = max(0, i - 1)
            val b = min(nSpine, i + 1)
            var tx = sx[b] - sx[a]; var ty = sy[b] - sy[a]; var tz = sz[b] - sz[a]
            val tLen = max(0.0001f, sqrt(tx * tx + ty * ty + tz * tz))
            tx /= tLen; ty /= tLen; tz /= tLen
            var nx = 0f; var ny = 1f; var nz = 0f
            if (abs(ty) > 0.9f) { nx = 0f; ny = 0f; nz = 1f }
            val dot = nx * tx + ny * ty + nz * tz
            nx -= dot * tx; ny -= dot * ty; nz -= dot * tz
            val nLen = max(0.0001f, sqrt(nx * nx + ny * ny + nz * nz))
            nx /= nLen; ny /= nLen; nz /= nLen
            frame[0] = nx; frame[1] = ny; frame[2] = nz
            frame[3] = ty * nz - tz * ny; frame[4] = tz * nx - tx * nz; frame[5] = tx * ny - ty * nx
        }

        val tOffBase = rng.nextDouble()
        val baseWeight = rng.nextDouble(0.5, 1.2).toFloat() * weightMul
        val ripSeed = ctx.seed + 0xAB7C3DuL

        for (ring in 0 until nTube) {
            val ringAngle = ring.toFloat() / nTube * PI_F * 2
            val tOff = tOffBase + ring.toDouble() / nTube * ctx.colorDrift
            val segSize = 16
            val segXs = ArrayList<Float>()
            val segYs = ArrayList<Float>()
            var depthSum = 0f
            for (i in 0 until nSpine) {
                frenet(i)
                val ca = cos(ringAngle)
                val sa = sin(ringAngle)
                val tx3 = sx[i] + tubeR * (ca * frame[0] + sa * frame[3])
                val ty3 = sy[i] + tubeR * (ca * frame[1] + sa * frame[4])
                val tz3 = sz[i] + tubeR * (ca * frame[2] + sa * frame[5])
                val w = fov / (fov - tz3)
                val depth = (tz3 - zMin) / zRange
                segXs += cx + tx3 * w; segYs += cy + ty3 * w; depthSum += depth
                if (segXs.size >= segSize + 1) {
                    val wt = baseWeight * (0.04f + depthSum / segXs.size * 1.8f)
                    emitSym(tasks, segXs.toFloatArray(), segYs.toFloatArray(), cx, cy, symmetry, ripple,
                        { sym -> ripSeed + (ring * 97 + sym).toULong() }, { sym -> tOff + sym * 0.1 }, ctx.colorDrift, wt)
                    val lx = segXs.last(); val ly = segYs.last()
                    segXs.clear(); segYs.clear(); segXs += lx; segYs += ly
                    depthSum = depth
                }
            }
        }

        val nRings = max(4, (ctx.density * 20).toInt())
        for (ri in 0 until nRings) {
            val idx = ri * (nSpine / nRings)
            frenet(idx)
            val depth = (sz[idx] - zMin) / zRange
            val wt = baseWeight * (0.04f + depth * 1.8f) * 0.6f
            val nPts = nTube + 1
            val rxs = FloatArray(nPts)
            val rys = FloatArray(nPts)
            for (k in 0 until nPts) {
                val a = (k % nTube).toFloat() / nTube * PI_F * 2
                val tx3 = sx[idx] + tubeR * (cos(a) * frame[0] + sin(a) * frame[3])
                val ty3 = sy[idx] + tubeR * (cos(a) * frame[1] + sin(a) * frame[4])
                val tz3 = sz[idx] + tubeR * (cos(a) * frame[2] + sin(a) * frame[5])
                val w = fov / (fov - tz3)
                rxs[k] = cx + tx3 * w
                rys[k] = cy + ty3 * w
            }
            emitSym(tasks, rxs, rys, cx, cy, symmetry, ripple, { sym -> ripSeed + (ri * 13 + sym + 500).toULong() },
                { sym -> tOffBase + ri * 0.05 + sym * 0.1 }, ctx.colorDrift, wt)
        }
    }

    /** Tilted great circles and pole-to-pole spirals on a sphere, in short depth-shaded segments. */
    fun sphereGrid(tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext, rng: SeededRng, symmetry: Int, ripple: Float, weightMul: Float) {
        val bigR = radius.toFloat() * 0.72f
        val tiltX = rng.nextDouble(0.08, 0.85).toFloat()
        val spinZ = (rng.nextDouble() * PI * 2).toFloat()
        val fov = bigR * 3.8f
        val zMin = -bigR
        val zRange = 2 * bigR
        val tOffBase = rng.nextDouble()
        val baseWeight = rng.nextDouble(0.5, 1.1).toFloat() * weightMul
        val ripSeed = ctx.seed + 0xBEEF42uL

        val segX = ArrayList<Float>()
        val segY = ArrayList<Float>()
        val segZ = ArrayList<Float>()
        fun add(x0: Float, y0: Float, z0: Float) {
            val x1 = x0 * cos(spinZ) - y0 * sin(spinZ)
            val y1 = x0 * sin(spinZ) + y0 * cos(spinZ)
            val y2 = y1 * cos(tiltX) - z0 * sin(tiltX)
            val z2 = y1 * sin(tiltX) + z0 * cos(tiltX)
            val w = fov / (fov - z2)
            segX += cx + x1 * w; segY += cy + y2 * w; segZ += z2
        }
        fun emit(tOff: Double) {
            if (segX.size <= 1) return
            var sum = 0f
            for (z in segZ) sum += z
            val depth = (sum / segZ.size - zMin) / zRange
            val wt = baseWeight * (0.04f + depth * 1.8f)
            emitSym(tasks, segX.toFloatArray(), segY.toFloatArray(), cx, cy, symmetry, ripple,
                { sym -> ripSeed + sym.toULong() }, { sym -> tOff + sym * 0.1 }, ctx.colorDrift, wt)
        }
        fun keepLast() {
            val x = segX.last(); val y = segY.last(); val z = segZ.last()
            segX.clear(); segY.clear(); segZ.clear()
            segX += x; segY += y; segZ += z
        }
        val steps = 180
        val segSz = 24

        val nGreat = 4 + (ctx.complexity * 18).toInt()
        for (gi in 0 until nGreat) {
            val axisTheta = (rng.nextDouble() * PI * 2).toFloat()
            val axisPhi = (rng.nextDouble() * PI).toFloat()
            val ax = sin(axisPhi) * cos(axisTheta)
            val ay = sin(axisPhi) * sin(axisTheta)
            val az = cos(axisPhi)
            var ux = -ay; var uy = ax; var uz = 0f
            val uLen = max(0.0001f, sqrt(ux * ux + uy * uy + uz * uz))
            ux /= uLen; uy /= uLen; uz /= uLen
            val vx = ay * uz - az * uy
            val vy = az * ux - ax * uz
            val vz = ax * uy - ay * ux
            val tOff = tOffBase + gi * (ctx.colorDrift / max(1, nGreat))
            segX.clear(); segY.clear(); segZ.clear()
            for (i in 0..steps) {
                val t = i.toFloat() / steps * PI_F * 2
                add(bigR * (cos(t) * ux + sin(t) * vx), bigR * (cos(t) * uy + sin(t) * vy), bigR * (cos(t) * uz + sin(t) * vz))
                if (segX.size >= segSz + 1) { emit(tOff); keepLast() }
            }
            if (segX.size > 1) emit(tOff)
        }

        val nSpirals = 1 + (ctx.density * 5).toInt()
        for (si in 0 until nSpirals) {
            val winds = 2.0 + ctx.complexity * 8.0
            val phaseOff = si.toFloat() / nSpirals * PI_F * 2
            val tOff = tOffBase + 0.5 + si * 0.15
            val nPts = 400 + (ctx.complexity * 600).toInt()
            segX.clear(); segY.clear(); segZ.clear()
            for (i in 0..nPts) {
                val t = i.toFloat() / nPts
                val phi = t * PI_F
                val theta = t * winds.toFloat() * PI_F * 2 + phaseOff
                add(bigR * sin(phi) * cos(theta), bigR * sin(phi) * sin(theta), bigR * cos(phi))
                if (segX.size >= segSz + 1) { emit(tOff); keepLast() }
            }
            if (segX.size > 1) emit(tOff)
        }
    }

    /** 4D hypercube, rotated in four planes and projected twice, as nested shells. */
    fun tesseract(tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double, ctx: StyleContext, rng: SeededRng, symmetry: Int, ripple: Float, weightMul: Float) {
        val baseScale = radius.toFloat() * 0.38f
        val nShells = 1 + (ctx.density * 4).toInt()
        val angle1 = (rng.nextDouble() * PI * 2).toFloat()
        val angle2 = (rng.nextDouble() * PI * 2).toFloat()
        val angle3 = rng.nextDouble(0.15, 1.4).toFloat()
        val angle4 = (rng.nextDouble() * PI * 2).toFloat()
        val tiltX = rng.nextDouble(0.15, 0.75).toFloat()
        val tOffBase = rng.nextDouble()
        val baseWeight = rng.nextDouble(0.8, 1.8).toFloat() * weightMul
        val ripSeed = ctx.seed + 0xDEAD99uL

        val verts = ArrayList<FloatArray>()
        for (xi in intArrayOf(-1, 1)) for (yi in intArrayOf(-1, 1)) for (zi in intArrayOf(-1, 1)) for (wi in intArrayOf(-1, 1)) {
            verts += floatArrayOf(xi.toFloat(), yi.toFloat(), zi.toFloat(), wi.toFloat())
        }
        val edges = ArrayList<IntArray>()
        for (i in 0 until 16) for (j in i + 1 until 16) {
            var diff = 0
            for (k in 0..3) if (verts[i][k] != verts[j][k]) diff++
            if (diff == 1) edges += intArrayOf(i, j)
        }

        for (shell in 0 until nShells) {
            val shellScale = baseScale * (1.0f - shell * 0.18f)
            val fov3 = shellScale * 3.2f
            val fov4 = shellScale * 2.8f
            val extraRot = shell.toFloat() * (ctx.complexity * 0.6).toFloat()
            val proj = verts.map { v ->
                var x = v[0]; var y = v[1]; var z = v[2]; var w = v[3]
                val x1 = x * cos(angle1 + extraRot) - y * sin(angle1 + extraRot)
                val y1 = x * sin(angle1 + extraRot) + y * cos(angle1 + extraRot)
                x = x1; y = y1
                val z1 = z * cos(angle2 + extraRot * 0.7f) - w * sin(angle2 + extraRot * 0.7f)
                val w1 = z * sin(angle2 + extraRot * 0.7f) + w * cos(angle2 + extraRot * 0.7f)
                z = z1; w = w1
                val x2 = x * cos(angle3) - w * sin(angle3)
                val w2 = x * sin(angle3) + w * cos(angle3)
                x = x2; w = w2
                val y3 = y * cos(angle4) - w * sin(angle4)
                val w3 = y * sin(angle4) + w * cos(angle4)
                y = y3; w = w3
                x *= shellScale; y *= shellScale; z *= shellScale; w *= shellScale
                val d4 = fov4 / (fov4 - w)
                val px = x * d4; val py = y * d4; val pz = z * d4
                val yy = py * cos(tiltX) - pz * sin(tiltX)
                val zz = py * sin(tiltX) + pz * cos(tiltX)
                val d3 = fov3 / (fov3 - zz)
                floatArrayOf(cx + px * d3, cy + yy * d3, zz)
            }
            val zMin2 = proj.minOf { it[2] }
            val zRange2 = max(0.001f, proj.maxOf { it[2] } - zMin2)
            val nSubdiv = 18 + (ctx.complexity * 30).toInt()
            val tOff = tOffBase + shell * 0.2
            for (edge in edges) {
                val ei = edge[0]
                val ej = edge[1]
                val (px0, py0, pz0) = proj[ei].let { Triple(it[0], it[1], it[2]) }
                val (px1, py1, pz1) = proj[ej].let { Triple(it[0], it[1], it[2]) }
                val segXs = ArrayList<Float>()
                val segYs = ArrayList<Float>()
                var zSum = 0f
                val subSegSz = 6
                fun flush(seedExtra: Int) {
                    val depth = (zSum / segXs.size - zMin2) / zRange2
                    val wt = baseWeight * (0.04f + depth * 1.8f) * (1.0f - shell * 0.15f)
                    emitSym(tasks, segXs.toFloatArray(), segYs.toFloatArray(), cx, cy, symmetry, ripple,
                        { sym -> ripSeed + (ei * 31 + ej + sym * 7 + shell * 100 + seedExtra).toULong() },
                        { sym -> tOff + (ei + ej) * 0.012 + sym * 0.1 }, ctx.colorDrift * 0.6, wt)
                }
                for (s in 0..nSubdiv) {
                    val t = s.toFloat() / nSubdiv
                    segXs += px0 + (px1 - px0) * t
                    segYs += py0 + (py1 - py0) * t
                    zSum += pz0 + (pz1 - pz0) * t
                    if (segXs.size >= subSegSz + 1) {
                        flush(0)
                        val lx = segXs.last(); val ly = segYs.last()
                        segXs.clear(); segYs.clear(); segXs += lx; segYs += ly
                        zSum = pz0 + (pz1 - pz0) * nSubdiv.toFloat() / nSubdiv.toFloat()
                    }
                }
                if (segXs.size > 1) flush(999)
            }
        }
    }
}
