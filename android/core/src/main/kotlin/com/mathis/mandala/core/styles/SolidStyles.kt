package com.mathis.mandala.core.styles

import com.mathis.mandala.core.ColorPalette
import com.mathis.mandala.core.PixelBuffer
import com.mathis.mandala.core.SeededRng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val PI_F = PI.toFloat()

/**
 * Direct-draw styles from MandalaRenderer.swift: String Art, Sunburst, Torus, Nautilus,
 * Constellation Web (as lines) and Strange Attractor (as single pixels, drawn straight into the buffer).
 */
object SolidStyles {

    private fun ColorPalette.col(t: Double): FloatArray = FloatArray(3).also { colorAt(t, it) }

    private fun lut(palette: ColorPalette, n: Int, colorOffset: Double): Array<FloatArray> = Array(n) { i ->
        val t = (i.toDouble() / n + colorOffset) % 1.0
        palette.col(if (t < 0) t + 1 else t)
    }

    /** Chords between points on circles, a fixed step apart. */
    fun stringArt(lines: LineList, cx: Float, cy: Float, radius: Double, ctx: StyleContext, palette: ColorPalette, rng: SeededRng, layerCount: Int, symmetry: Int) {
        val col = FloatArray(3)
        repeat(layerCount) {
            val r = (radius * (0.5 + rng.nextDouble() * 0.5)).toFloat()
            val n = rng.nextDouble(20.0, 60.0).toInt()
            val step = rng.nextDouble(3.0, 8.0).toInt()
            val tOffset = rng.nextDouble()
            val weight = rng.nextDouble(0.2, 0.8).toFloat() * ctx.complexity.toFloat()
            for (sym in 0 until symmetry) {
                val symAngle = sym.toDouble() * PI * 2.0 / symmetry
                val cosA = cos(symAngle).toFloat()
                val sinA = sin(symAngle).toFloat()
                for (i in 0 until n) {
                    val a0 = i.toFloat() / n * PI_F * 2.0f
                    val a1 = ((i + step) % n).toFloat() / n * PI_F * 2.0f
                    val lx0 = cos(a0) * r; val ly0 = sin(a0) * r
                    val lx1 = cos(a1) * r; val ly1 = sin(a1) * r
                    palette.colorAt((tOffset + i.toDouble() / n * ctx.colorDrift) % 1.0, col)
                    lines.add(
                        cx + lx0 * cosA - ly0 * sinA, cy + lx0 * sinA + ly0 * cosA,
                        cx + lx1 * cosA - ly1 * sinA, cy + lx1 * sinA + ly1 * cosA, col, weight,
                    )
                }
            }
        }
    }

    /** Twisted rays from near the centre. */
    fun sunburst(lines: LineList, cx: Float, cy: Float, radius: Double, ctx: StyleContext, palette: ColorPalette, rng: SeededRng, layerCount: Int, symmetry: Int) {
        val col = FloatArray(3)
        repeat(layerCount) {
            val r = (radius * (0.3 + rng.nextDouble() * 0.7)).toFloat()
            val nRays = rng.nextDouble(12.0, 60.0).toInt()
            val twist = rng.nextDouble(-1.0, 1.0).toFloat() * ctx.colorDrift.toFloat()
            val tOffset = rng.nextDouble()
            val weight = rng.nextDouble(0.3, 1.0).toFloat() * ctx.complexity.toFloat()
            for (sym in 0 until symmetry) {
                val symAngle = sym.toDouble() * PI * 2.0 / symmetry
                for (i in 0 until nRays) {
                    val frac = i.toFloat() / nRays
                    val angle = frac * PI_F * 2.0f + symAngle.toFloat()
                    val innerR = r * 0.05f
                    val outerR = r * (0.5f + rng.nextDouble().toFloat() * 0.5f)
                    val twistAngle = angle + outerR / r * twist
                    palette.colorAt((tOffset + frac.toDouble() * ctx.colorDrift) % 1.0, col)
                    lines.add(cx + cos(angle) * innerR, cy + sin(angle) * innerR, cx + cos(twistAngle) * outerR, cy + sin(twistAngle) * outerR, col, weight)
                }
            }
        }
    }

    /** Emits a projected polyline segment with symmetry copies. */
    private fun symSegment(lines: LineList, cx: Float, cy: Float, symmetry: Int, x0: Float, y0: Float, x1: Float, y1: Float, c: FloatArray, w: Float) {
        for (s in 0 until symmetry) {
            val ang = s.toFloat() * PI_F * 2.0f / symmetry
            val cosA = cos(ang)
            val sinA = sin(ang)
            lines.add(cx + x0 * cosA - y0 * sinA, cy + x0 * sinA + y0 * cosA, cx + x1 * cosA - y1 * sinA, cy + x1 * sinA + y1 * cosA, c, w)
        }
    }

    /** Tilted wireframe torus (meridians and parallels) with depth shading. */
    fun torus(lines: LineList, cx: Float, cy: Float, radius: Double, ctx: StyleContext, palette: ColorPalette, rng: SeededRng, layerCount: Int, symmetry: Int, colorOffset: Double) {
        val bigRAll = radius.toFloat() * 1.2f
        val nColors = 1024
        val lut = lut(palette, nColors, colorOffset)
        val tilt = PI_F * 0.222f + (rng.nextDouble() * 0.25).toFloat()
        val cosTilt = cos(tilt)
        val sinTilt = sin(tilt)
        val nMeridians = 16 + (ctx.density * 24).toInt()
        val nParallels = 8 + (ctx.density * 16).toInt()
        val steps = 32
        val symScale = 1.0f / sqrt(max(1, symmetry).toFloat())
        repeat(layerCount) {
            val bigR = bigRAll * (0.40 + rng.nextDouble() * 0.30).toFloat()
            val smallR = bigR * (0.25 + rng.nextDouble() * 0.40).toFloat()
            val baseW = rng.nextDouble(0.5, 1.4).toFloat() * ctx.complexity.toFloat() * symScale
            val tShift = rng.nextDouble()
            for (pass in 0 until 2) {
                val count = if (pass == 0) nMeridians else nParallels
                for (mi in 0 until count) {
                    val fixed = mi.toFloat() / count * PI_F * 2.0f
                    var px = 0f; var py = 0f
                    for (j in 0..steps) {
                        val vary = j.toFloat() / steps * PI_F * 2.0f
                        val u = if (pass == 0) fixed else vary
                        val v = if (pass == 0) vary else fixed
                        val x3 = (bigR + smallR * cos(v)) * cos(u)
                        val y3 = (bigR + smallR * cos(v)) * sin(u)
                        val z3 = smallR * sin(v)
                        val sx = x3
                        val sy = y3 * cosTilt - z3 * sinTilt
                        val depth = y3 * sinTilt + z3 * cosTilt
                        if (j > 0) {
                            val df = max(0.08f, min(1.0f, 0.5f + depth / (bigR + smallR) * 0.55f))
                            val w = if (pass == 0) baseW * df else baseW * df * 0.6f
                            val cT = (mi.toDouble() / count + tShift + (if (pass == 0) 0.0 else 0.5) + ctx.colorDrift * j / steps) % 1.0
                            val c = lut[(cT.coerceIn(0.0, 1.0) * (nColors - 1)).toInt()]
                            symSegment(lines, cx, cy, symmetry, px, py, sx, sy, c, w)
                        }
                        px = sx; py = sy
                    }
                }
            }
        }
    }

    /** Logarithmic spiral shell: cross-section ribs and surface spirals, tilted, depth-shaded. */
    fun nautilus(lines: LineList, cx: Float, cy: Float, radius: Double, ctx: StyleContext, palette: ColorPalette, rng: SeededRng, layerCount: Int, symmetry: Int, colorOffset: Double) {
        val bigR = radius.toFloat() * 1.1f
        val nColors = 1024
        val lut = lut(palette, nColors, colorOffset)
        val tilt = PI_F / 5.0f + (rng.nextDouble() * 0.25).toFloat()
        val cosTilt = cos(tilt)
        val sinTilt = sin(tilt)
        val uMin = -PI_F * 4.0f
        val uMax = 0.0f
        val nRibs = 18 + (ctx.density * 22).toInt()
        val nSpiralLines = 6 + (ctx.density * 10).toInt()
        val ribSteps = 24
        val spiralSteps = 64
        val symScale = 1.0f / sqrt(max(1, symmetry).toFloat())
        repeat(layerCount) {
            val b = (0.12 + rng.nextDouble() * 0.10).toFloat()
            val r = (0.25 + rng.nextDouble() * 0.30).toFloat()
            val baseW = rng.nextDouble(0.5, 1.4).toFloat() * ctx.complexity.toFloat() * symScale
            val tShift = rng.nextDouble()
            val outerScale = bigR / (exp(b * 0f) * (1 + r))
            fun shell(u: Float, v: Float, out: FloatArray) {
                val ebu = exp(b * u) * outerScale
                val x3 = ebu * (1 + r * cos(v)) * cos(u)
                val y3 = ebu * (1 + r * cos(v)) * sin(u)
                val z3 = ebu * r * sin(v)
                out[0] = x3
                out[1] = y3 * cosTilt - z3 * sinTilt
                out[2] = y3 * sinTilt + z3 * cosTilt
            }
            val p = FloatArray(3)
            for (pass in 0 until 2) {
                val count = if (pass == 0) nRibs else nSpiralLines
                val steps = if (pass == 0) ribSteps else spiralSteps
                for (k in 0 until count) {
                    var px = 0f; var py = 0f
                    for (j in 0..steps) {
                        if (pass == 0) {
                            val u = uMin + k.toFloat() / (nRibs - 1) * (uMax - uMin)
                            shell(u, j.toFloat() / steps * PI_F * 2.0f, p)
                        } else {
                            val v = k.toFloat() / nSpiralLines * PI_F * 2.0f
                            shell(uMin + j.toFloat() / steps * (uMax - uMin), v, p)
                        }
                        if (j > 0) {
                            val normDepth = (p[2] + bigR) / (2 * bigR)
                            val df = max(0.08f, min(1.0f, normDepth * 0.92f + 0.08f))
                            val w = if (pass == 0) baseW * df else baseW * df * 0.7f
                            val cT = (k.toDouble() / count + tShift + (if (pass == 0) 0.0 else 0.5) + ctx.colorDrift * j / steps) % 1.0
                            val c = lut[(cT.coerceIn(0.0, 1.0) * (nColors - 1)).toInt()]
                            symSegment(lines, cx, cy, symmetry, px, py, p[0], p[1], c, w)
                        }
                        px = p[0]; py = p[1]
                    }
                }
            }
        }
    }

    /** Small four-pointed star with a short diagonal cross and a round core. */
    private fun tinyStar(lines: LineList, cx: Float, cy: Float, radius: Float, rotation: Float, c: FloatArray, coreWeight: Float, spikeWeight: Float) {
        val shortR = radius * 0.45f
        for (i in 0 until 4) {
            val a = rotation + i.toFloat() * PI_F * 0.5f
            lines.add(cx - cos(a) * radius, cy - sin(a) * radius, cx + cos(a) * radius, cy + sin(a) * radius, c, spikeWeight)
        }
        for (i in 0 until 4) {
            val a = rotation + PI_F * 0.25f + i.toFloat() * PI_F * 0.5f
            lines.add(cx - cos(a) * shortR, cy - sin(a) * shortR, cx + cos(a) * shortR, cy + sin(a) * shortR, c, spikeWeight * 0.55f)
        }
        val r = max(1.2f, radius * 0.16f)
        if (r <= 0.5f) return
        val steps = 10
        val dt = PI_F * 2 / steps
        var px = cx + cos(0f) * r
        var py = cy + sin(0f) * r
        for (i in 1..steps) {
            val t = i * dt
            val x = cx + cos(t) * r
            val y = cy + sin(t) * r
            lines.add(px, py, x, y, c, coreWeight)
            px = x; py = y
        }
    }

    /** Star nodes on rings, connected when close, with a bias towards the centre. */
    fun constellationWeb(lines: LineList, cx: Float, cy: Float, radius: Double, ctx: StyleContext, palette: ColorPalette, rng: SeededRng, colorOffset: Double, symmetry: Int) {
        val bigR = radius.toFloat()
        val sector = PI_F * 2 / max(1, symmetry)
        val ringCount = max(2, (2 + ctx.complexity * 4).toInt())
        val baseNodes = max(6, (7 + ctx.density * 12 + ctx.complexity * 8).toInt())
        val rel = ArrayList<FloatArray>()
        for (ring in 0 until ringCount) {
            val ringT = (ring + 1).toFloat() / (ringCount + 1)
            val onRing = max(2, baseNodes / ringCount + (rng.nextDouble() * 3).toInt())
            for (idx in 0 until onRing) {
                val baseA = idx.toFloat() / onRing * sector
                val jitterA = (rng.nextDouble() - 0.5).toFloat() * sector * (0.08f + ctx.abstractLevel.toFloat() * 0.20f)
                val distBase = bigR * (0.12f + ringT * 0.74f)
                val distJitter = bigR * ((rng.nextDouble() - 0.5) * (0.12 + ctx.density * 0.10)).toFloat()
                val ang = baseA + jitterA
                val dist = max(bigR * 0.06f, min(bigR * 0.94f, distBase + distJitter))
                rel += floatArrayOf(cos(ang) * dist, sin(ang) * dist)
            }
        }
        val nodes = ArrayList<FloatArray>()
        for (p in rel) {
            for (sym in 0 until symmetry) {
                val a = sym.toFloat() * sector
                val ca = cos(a)
                val sa = sin(a)
                nodes += floatArrayOf(cx + p[0] * ca - p[1] * sa, cy + p[0] * sa + p[1] * ca)
            }
        }
        fun col(t: Double): FloatArray = palette.col((t + colorOffset) % 1.0)
        val connectDist = bigR * (0.18 + ctx.complexity * 0.28 + ctx.density * 0.10).toFloat()
        val radialBias = (0.32 + ctx.complexity * 0.20).toFloat()
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val n0 = nodes[i]
                val n1 = nodes[j]
                val dx = n0[0] - n1[0]
                val dy = n0[1] - n1[1]
                val d = sqrt(dx * dx + dy * dy)
                if (d < connectDist) {
                    val alpha = 1 - d / connectDist
                    val midX = (n0[0] + n1[0]) * 0.5f - cx
                    val midY = (n0[1] + n1[1]) * 0.5f - cy
                    val midR = sqrt(midX * midX + midY * midY) / max(bigR, 1f)
                    val keepChance = min(0.97, (alpha * 0.9f + (1 - midR) * radialBias).toDouble())
                    if (rng.nextDouble() > keepChance) continue
                    val c = col((i + j).toDouble() / max(1, nodes.size * 2))
                    val w = (0.07 + alpha.toDouble() * (0.18 + ctx.density * 0.14)).toFloat()
                    lines.add(n0[0], n0[1], n1[0], n1[1], c[0] * alpha, c[1] * alpha, c[2] * alpha, w)
                }
            }
        }
        for ((idx, node) in nodes.withIndex()) {
            val c = col(idx.toDouble() / max(1, nodes.size))
            val dx = node[0] - cx
            val dy = node[1] - cy
            val nodeR = sqrt(dx * dx + dy * dy) / max(bigR, 1f)
            val starRadius = bigR * (0.006f + (ctx.density * 0.008).toFloat() + (1 - nodeR) * 0.010f)
            val rotation = (rng.nextDouble() * PI * 0.5).toFloat()
            tinyStar(lines, node[0], node[1], starRadius, rotation, c,
                (0.45 + ctx.glowIntensity * 0.30).toFloat(), (0.22 + ctx.glowIntensity * 0.12).toFloat())
        }
    }

    private val attractorPresets = arrayOf(
        doubleArrayOf(0.0, -1.4, 1.6, 1.0, 0.7), doubleArrayOf(0.0, -1.7, 1.3, -0.1, -1.21), doubleArrayOf(0.0, 1.5, -1.8, 1.6, 0.9),
        doubleArrayOf(0.0, -1.8, -2.0, -0.5, -0.9), doubleArrayOf(0.0, 1.7, 1.7, 0.6, 1.2), doubleArrayOf(0.0, -1.3, -1.3, -1.8, -1.9),
        doubleArrayOf(0.0, -1.5, -1.8, 1.6, 0.9), doubleArrayOf(0.0, 1.6, -0.6, -1.0, 0.7), doubleArrayOf(0.0, -2.0, 0.4, -1.1, -0.9),
        doubleArrayOf(0.0, 0.8, -1.9, 1.0, 1.5), doubleArrayOf(0.0, -1.1, 2.0, -1.6, -0.7), doubleArrayOf(0.0, 1.4, 1.4, 1.1, -1.7),
        doubleArrayOf(1.0, -2.0, -2.0, -1.2, 2.0), doubleArrayOf(1.0, 1.4, -2.3, 2.4, -2.1), doubleArrayOf(1.0, -0.8, -2.4, -0.7, 2.0),
        doubleArrayOf(1.0, 2.0, -1.0, -0.5, 2.0), doubleArrayOf(1.0, -1.5, 2.0, -1.0, 1.5), doubleArrayOf(1.0, -2.4, 2.4, -1.6, 1.0),
        doubleArrayOf(1.0, -0.7, 0.5, -1.4, 2.1), doubleArrayOf(1.0, 0.3, -1.5, 1.2, -1.9), doubleArrayOf(1.0, -1.8, 1.8, 0.9, -1.5),
        doubleArrayOf(1.0, 2.3, -2.3, 2.0, -0.8),
        doubleArrayOf(2.0, 0.64, 0.76, 0.0, 0.0), doubleArrayOf(2.0, 0.85, 0.92, 0.0, 0.0), doubleArrayOf(2.0, 0.50, 0.80, 0.0, 0.0),
        doubleArrayOf(2.0, 1.20, 0.60, 0.0, 0.0),
        doubleArrayOf(3.0, 0.4, 1.0, 0.0, 0.0), doubleArrayOf(3.0, 1.1, 0.5, 1.0, 0.0), doubleArrayOf(3.0, -0.5, 1.5, 0.2, 0.0),
        doubleArrayOf(3.0, 0.7, 1.2, -0.3, 0.0),
    )

    /** Clifford, De Jong, Bedhead and Hopalong attractors as hundreds of thousands of points. */
    fun strangeAttractor(buffer: PixelBuffer, cx: Float, cy: Float, radius: Double, ctx: StyleContext, palette: ColorPalette, rng: SeededRng, layerCount: Int, symmetry: Int, colorOffset: Double) {
        val bigR = radius.toFloat()
        val nColors = 2048
        val lut = lut(palette, nColors, colorOffset)
        val iters = (200_000 + ctx.density * 600_000).toInt()
        val bright = (ctx.density * 1.2 + 0.5).toFloat() / max(1, symmetry)
        val symCos = FloatArray(symmetry) { cos(it.toFloat() * PI_F * 2.0f / symmetry) }
        val symSin = FloatArray(symmetry) { sin(it.toFloat() * PI_F * 2.0f / symmetry) }
        for (li in 0 until layerCount) {
            val p = attractorPresets[(li + (ctx.seed % 13uL).toInt()) % attractorPresets.size]
            val type = p[0].toInt()
            val a = p[1]; val b = p[2]; val c = p[3]; val d = p[4]
            val next = DoubleArray(2)
            fun step(x: Double, y: Double) {
                when (type) {
                    0 -> { next[0] = sin(a * y) + c * cos(a * x); next[1] = sin(b * x) + d * cos(b * y) }
                    1 -> { next[0] = sin(a * y) - cos(b * x); next[1] = sin(c * x) - cos(d * y) }
                    2 -> { next[0] = sin(x * y / b) * y + cos(a * x - y); next[1] = x + sin(y) / b }
                    else -> { next[0] = y - (if (x >= 0) 1 else -1) * sqrt(abs(b * x - c)); next[1] = a - x }
                }
            }
            var x = 0.1 + li * 0.07
            var y = 0.13 + li * 0.05
            var minX = x; var maxX = x; var minY = y; var maxY = y
            for (n in 0 until 3000) {
                step(x, y)
                x = next[0]; y = next[1]
                if (!(x.isFinite() && y.isFinite() && abs(x) < 1e6 && abs(y) < 1e6)) break
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
            }
            val rangeX = maxX - minX
            val rangeY = maxY - minY
            if (rangeX <= 0.001 || rangeY <= 0.001) continue
            val scale = min(bigR.toDouble() * 1.98 / rangeX, bigR.toDouble() * 1.98 / rangeY)
            val offX = -(minX + maxX) * 0.5 * scale
            val offY = -(minY + maxY) * 0.5 * scale
            val weight = rng.nextDouble(0.25, 0.7).toFloat() * bright
            val tShift = rng.nextDouble()
            for (i in 0 until iters) {
                step(x, y)
                x = next[0]; y = next[1]
                if (!(x.isFinite() && y.isFinite())) break
                val px = (x * scale + offX).toFloat()
                val py = (y * scale + offY).toFloat()
                val angle01 = atan2(py, px).toDouble() / (PI * 2.0) + 0.5
                val colorIdx = (((angle01 + tShift + ctx.colorDrift * i / iters) % 1.0) * nColors).toInt() % nColors
                val col = lut[colorIdx.coerceIn(0, nColors - 1)]
                for (s in 0 until symmetry) {
                    buffer.addPixel(
                        (cx + px * symCos[s] - py * symSin[s]).toInt(),
                        (cy + px * symSin[s] + py * symCos[s]).toInt(),
                        col[0], col[1], col[2], weight,
                    )
                }
            }
        }
    }
}
