package com.mathis.mandala.core.styles

import com.mathis.mandala.core.ColorPalette
import com.mathis.mandala.core.SeededRng
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val PI_F = PI.toFloat()

/** Ports of the styles the macOS renderer draws straight into its buffer (draw…Layers). */
object DirectStyles {

    /** Palette colour at `t` (clamped like ColorPalette.color(at:)). */
    private fun ColorPalette.col(t: Double): FloatArray = FloatArray(3).also { colorAt(t, it) }

    private fun scaled(c: FloatArray, a: Float) = floatArrayOf(c[0] * a, c[1] * a, c[2] * a)

    // ── Star Burst ───────────────────────────────────────────────────────

    /** Dense field of short radial dashes starting at random distances from the centre. */
    fun starBurst(lines: LineList, cx: Float, cy: Float, radius: Double, ctx: StyleContext, palette: ColorPalette, rng: SeededRng) {
        val bigR = radius.toFloat()
        val numDashes = (ctx.density * 5000 + 1200).toInt()
        val dashLen = bigR * (0.05 + ctx.complexity * 0.50).toFloat()
        val jitterAmt = 0.04f
        val weight = (0.26 + ctx.density * 0.14).toFloat()
        val tBase = rng.nextDouble()
        val col = FloatArray(3)

        for (i in 0 until numDashes) {
            val angle = rng.nextFloat() * PI_F * 2
            val innerR = rng.nextFloat() * bigR
            val outerR = min(innerR + dashLen, bigR * 1.05f)
            val jitter = (rng.nextFloat() - 0.5f) * jitterAmt
            val ca = cos(angle + jitter)
            val sa = sin(angle + jitter)
            val t = (tBase + (innerR / bigR).toDouble() * ctx.colorDrift +
                i.toDouble() / numDashes * ctx.colorDrift * 0.08) % 1.0
            palette.colorAt(t, col)
            lines.add(cx + ca * innerR, cy + sa * innerR, cx + ca * outerR, cy + sa * outerR, col, weight)
        }
    }

    // ── Hyperboloid ──────────────────────────────────────────────────────

    /** A hyperboloid of one sheet drawn by its two families of ruling lines, tilted, with depth shading. */
    fun hyperboloid(
        lines: LineList, cx: Float, cy: Float, radius: Double, ctx: StyleContext, palette: ColorPalette,
        rng: SeededRng, layerCount: Int, symmetry: Int, colorOffset: Double,
    ) {
        val bigR = radius.toFloat() * 1.162f
        val nColors = 1024
        val lut = Array(nColors) { i ->
            val t = (i.toDouble() / nColors + colorOffset) % 1.0
            palette.col(if (t < 0) t + 1 else t)
        }

        val tilt = PI_F / 5.0f + (rng.nextDouble() * 0.3).toFloat()
        val cosTilt = cos(tilt)
        val sinTilt = sin(tilt)
        val nLines = 30 + (ctx.density * 50).toInt()
        val steps = 24
        val symCos = FloatArray(symmetry) { cos(it.toFloat() * PI_F * 2.0f / symmetry) }
        val symSin = FloatArray(symmetry) { sin(it.toFloat() * PI_F * 2.0f / symmetry) }

        repeat(layerCount) {
            val rimR = bigR * (0.35 + rng.nextDouble() * 0.55).toFloat()
            val height = rimR * (0.5 + rng.nextDouble() * 0.8).toFloat()
            val twist = PI_F * (0.35 + rng.nextDouble() * 0.45).toFloat()
            val baseW = rng.nextDouble(0.6, 1.6).toFloat() * ctx.complexity.toFloat()
            val tShift = rng.nextDouble()

            for (family in 0 until 2) {
                val sign = if (family == 0) 1f else -1f
                for (line in 0 until nLines) {
                    val phi = line.toFloat() / nLines * PI_F * 2.0f
                    val botPhi = phi + sign * twist
                    val tx = rimR * cos(phi)
                    val tz = rimR * sin(phi)
                    val bx = rimR * cos(botPhi)
                    val bz = rimR * sin(botPhi)
                    val colorPhi = (phi / (PI_F * 2.0f)).toDouble()
                    val colorT = (colorPhi + tShift + ctx.colorDrift * line.toDouble() / nLines) % 1.0
                    val cIdx = (colorT * nColors).toInt() % nColors
                    val c = lut[cIdx.coerceIn(0, nColors - 1)]

                    var prevSX = 0f
                    var prevSY = 0f
                    for (j in 0..steps) {
                        val t = j.toFloat() / steps
                        val x3 = tx + t * (bx - tx)
                        val y3 = height * (1 - 2 * t)
                        val z3 = tz + t * (bz - tz)
                        val sx = x3
                        val sy = y3 * cosTilt - z3 * sinTilt
                        val depth = y3 * sinTilt + z3 * cosTilt
                        if (j > 0) {
                            val df = max(0.08f, min(1.0f, 0.5f + depth / rimR * 0.55f))
                            val w = baseW * df
                            for (s in 0 until symmetry) {
                                val ca = symCos[s]
                                val sa = symSin[s]
                                lines.add(
                                    cx + prevSX * ca - prevSY * sa, cy + prevSX * sa + prevSY * ca,
                                    cx + sx * ca - sy * sa, cy + sx * sa + sy * ca, c, w,
                                )
                            }
                        }
                        prevSX = sx
                        prevSY = sy
                    }
                }
            }
        }
    }

    // ── Universe ─────────────────────────────────────────────────────────

    /** Density walks through seven scenes: planet, two planets, Saturn, solar system, galaxy, galaxies, cosmic web. */
    fun universe(
        lines: LineList, cx: Float, cy: Float, radius: Double, ctx: StyleContext, palette: ColorPalette,
        rng: SeededRng, colorOffset: Double, symmetry: Int,
    ) {
        val bigR = radius.toFloat()
        val density = ctx.density
        val complexity = ctx.complexity
        val w = max(0.5f, (0.6 + complexity * 1.0).toFloat() * bigR / 900.0f)
        val sectorAngle = PI_F * 2.0f / max(1, symmetry)

        fun col(t: Double): FloatArray = palette.col((t + colorOffset) % 1.0)

        fun addCircle(bx: Float, by: Float, r: Float, c: FloatArray, weight: Float, steps: Int = 100) {
            if (r <= 0.5f) return
            val step = PI_F * 2.0f / steps
            for (i in 0 until steps) {
                val t0 = i * step
                val t1 = t0 + step
                lines.add(bx + cos(t0) * r, by + sin(t0) * r, bx + cos(t1) * r, by + sin(t1) * r, c, weight)
            }
        }

        fun addEllipse(bx: Float, by: Float, rx: Float, ry: Float, c: FloatArray, weight: Float, steps: Int = 100) {
            if (rx <= 0.5f || ry <= 0.5f) return
            val step = PI_F * 2.0f / steps
            for (i in 0 until steps) {
                val t0 = i * step
                val t1 = t0 + step
                lines.add(bx + cos(t0) * rx, by + sin(t0) * ry, bx + cos(t1) * rx, by + sin(t1) * ry, c, weight)
            }
        }

        // Runs `body` once per symmetry copy of the offset (rx, ry) around the centre.
        fun forSym(rx: Float, ry: Float, body: (Float, Float) -> Unit) {
            for (s in 0 until symmetry) {
                val a = s.toFloat() * PI_F * 2.0f / symmetry
                val ca = cos(a)
                val sa = sin(a)
                body(cx + rx * ca - ry * sa, cy + rx * sa + ry * ca)
            }
        }

        fun drawPlanet(bx: Float, by: Float, pr: Float, ct: Double, wMul: Float = 1.0f) {
            addCircle(bx, by, pr, col(ct), w * 3.0f * wMul, 140)
            for (i in 1..5) {
                val haloR = pr * (1.0f + i * 0.22f)
                val a = (1.0 - i * 0.17).toFloat()
                addCircle(bx, by, haloR, scaled(col(ct + i * 0.03), a), w * 0.7f * wMul, 100)
            }
        }

        fun drawMiniGalaxy(gx: Float, gy: Float, gR: Float, gt: Double, arms: Int = 2) {
            val gc = col(gt)
            for (i in 0..4) {
                val a = (1.0 - i * 0.18).toFloat()
                addCircle(gx, gy, gR * (0.2 + i * 0.18).toFloat(), scaled(gc, a), w * (2.5 - i * 0.4).toFloat(), 35)
            }
            val ac = col(gt + 0.1)
            for (arm in 0 until arms) {
                val aOff = arm.toFloat() * PI_F * 2.0f / arms + (rng.nextDouble() * 0.6).toFloat()
                for (s in 0 until 70) {
                    val t = s / 70.0f
                    val lr = gR * t.pow(0.48f)
                    val th = t * PI_F * 4.0f + aOff
                    val t2 = t + 1.0f / 70.0f
                    val lr2 = gR * t2.pow(0.48f)
                    val th2 = t2 * PI_F * 4.0f + aOff
                    lines.add(gx + cos(th) * lr, gy + sin(th) * lr, gx + cos(th2) * lr2, gy + sin(th2) * lr2, ac, w * (1.2f - t * 0.7f))
                }
            }
        }

        // Stars, only with symmetry > 1 (background dots look wrong at symmetry 1)
        val starCount = if (symmetry > 1) (80 + density * 700).toInt() else 0
        repeat(starCount) {
            val ang = rng.nextDouble().toFloat() * sectorAngle
            val dist = sqrt(rng.nextDouble()).toFloat() * bigR * 0.96f
            val rx = cos(ang) * dist
            val ry = sin(ang) * dist
            val bright = (rng.nextDouble() * 0.5 + 0.5).toFloat()
            val sc = col(rng.nextDouble() * 0.4 + 0.55)
            val sr = (rng.nextDouble() * 1.8 + 0.5).toFloat()
            forSym(rx, ry) { sx, sy -> addCircle(sx, sy, sr, scaled(sc, bright), 0.6f, 8) }
        }

        val level = density
        when {
            level < 0.14 -> {
                // Single planet with ring and a moon per symmetry copy
                val pr = bigR * 0.32f
                drawPlanet(cx, cy, pr, 0.28)
                addEllipse(cx, cy, pr * 1.05f, pr * 0.35f, col(0.35), w * 0.8f, 100)
                val moonAng = rng.nextDouble().toFloat() * sectorAngle
                val moonDist = pr * 1.9f
                forSym(cos(moonAng) * moonDist, sin(moonAng) * moonDist) { mx, my -> drawPlanet(mx, my, pr * 0.22f, 0.55, 0.7f) }
            }
            level < 0.27 -> {
                // Two planets
                val o1x = -bigR * 0.35f
                val o1y = bigR * 0.06f
                val o2x = bigR * 0.30f
                val o2y = -bigR * 0.08f
                forSym(o1x, o1y) { px, py -> drawPlanet(px, py, bigR * 0.22f, 0.18) }
                forSym(o2x, o2y) { px, py -> drawPlanet(px, py, bigR * 0.16f, 0.56) }
                addCircle(cx, cy, bigR * 0.32f, col(0.72), w * 0.25f, 80)
                val mAng = rng.nextDouble().toFloat() * sectorAngle
                val mbx = o1x + cos(mAng) * bigR * 0.22f * 2.0f
                val mby = o1y + sin(mAng) * bigR * 0.22f * 2.0f
                forSym(mbx, mby) { px, py -> drawPlanet(px, py, bigR * 0.05f, 0.6, 0.6f) }
            }
            level < 0.40 -> {
                // Saturn
                val pr = bigR * 0.26f
                addCircle(cx, cy, pr, col(0.42), w * 3.0f, 140)
                for (j in 1..4) {
                    val a = (1.0 - j * 0.18).toFloat()
                    addCircle(cx, cy, pr * (1.0f + j * 0.14f), scaled(col(0.40 + j * 0.025), a), w * 0.6f, 100)
                }
                for (b in 0 until 3) {
                    val bf = (0.55 + b * 0.15).toFloat()
                    addEllipse(cx, cy, pr * bf, pr * 0.18f, col(0.38 + b * 0.04), w * 0.7f, 100)
                }
                for (ri in 0..6) {
                    val rf = (1.5 + ri * 0.16).toFloat()
                    val a = (1.0 - ri * 0.10).toFloat()
                    addEllipse(cx, cy, pr * rf, pr * 0.38f, scaled(col(0.55 + ri * 0.04), a), w * 0.7f, 140)
                }
                val mAng = rng.nextDouble().toFloat() * sectorAngle
                forSym(cos(mAng) * pr * 3.0f, sin(mAng) * pr * 3.0f) { mx, my -> drawPlanet(mx, my, pr * 0.14f, 0.65, 0.7f) }
            }
            level < 0.54 -> {
                // Solar system
                val sunR = bigR * 0.12f
                val sunC = col(0.13)
                for (i in 0..7) {
                    val a = (1.0 - i * 0.11).toFloat()
                    addCircle(cx, cy, sunR * (1.0f + i * 0.4f), scaled(sunC, a), w * (3.5 - i * 0.35).toFloat(), 120)
                }
                val flareCount = max(2, 6 / max(1, symmetry))
                for (f in 0 until flareCount) {
                    val fa = f.toFloat() * sectorAngle / flareCount + (rng.nextDouble() * 0.3).toFloat()
                    val fr = sunR * (1.4 + rng.nextDouble() * 0.8).toFloat()
                    forSym(cos(fa) * fr, sin(fa) * fr) { x1, y1 ->
                        lines.add(cx + cos(fa) * sunR * 1.3f, cy + sin(fa) * sunR * 1.3f, x1, y1, col(0.1), w * 0.8f)
                    }
                }
                val planets = arrayOf(
                    doubleArrayOf(0.18, 0.028, 0.25), doubleArrayOf(0.27, 0.045, 0.45), doubleArrayOf(0.38, 0.060, 0.60),
                    doubleArrayOf(0.50, 0.055, 0.75), doubleArrayOf(0.63, 0.100, 0.12), doubleArrayOf(0.76, 0.080, 0.35),
                    doubleArrayOf(0.88, 0.065, 0.55), doubleArrayOf(0.97, 0.045, 0.70),
                )
                for ((orbitFrac, sizeFrac, ct) in planets.map { Triple(it[0], it[1], it[2]) }) {
                    val orbitR = bigR * orbitFrac.toFloat()
                    addCircle(cx, cy, orbitR, col(ct * 0.4 + 0.48), w * 0.22f, 90)
                    val pAng = rng.nextDouble().toFloat() * sectorAngle
                    val pr = bigR * sizeFrac.toFloat()
                    forSym(cos(pAng) * orbitR, sin(pAng) * orbitR) { px, py ->
                        drawPlanet(px, py, pr, ct, 0.9f)
                        if (orbitFrac > 0.60 && orbitFrac < 0.65) {
                            for (ri in 0..3) {
                                addEllipse(px, py, pr * (1.5 + ri * 0.15).toFloat(), pr * 0.35f, col(ct + 0.12), w * 0.5f, 80)
                            }
                        }
                    }
                }
            }
            level < 0.67 -> {
                // Spiral galaxy; arm count times symmetry keeps every sector equally dense
                val armCount = (2 + (complexity * 3).toInt()) * max(1, symmetry)
                val stepsPerArm = (280 + complexity * 420).toInt()
                for (i in 0..8) {
                    val a = (1.0 - i * 0.10).toFloat()
                    addCircle(cx, cy, bigR * (0.04 + i * 0.038).toFloat(), scaled(col(0.33 + i * 0.01), a), w * (3.5 - i * 0.3).toFloat(), 70)
                }
                addEllipse(cx, cy, bigR * 0.30f, bigR * 0.06f, col(0.28), w * 0.5f, 80)
                for (arm in 0 until armCount) {
                    val armOff = arm.toFloat() * PI_F * 2.0f / armCount
                    val ac = col(arm.toDouble() / armCount * 0.7 + 0.05)
                    for (s in 0 until stepsPerArm) {
                        val t = s.toFloat() / stepsPerArm
                        val logR = bigR * 0.94f * t.pow(0.52f)
                        val theta = t * PI_F * 5.5f + armOff
                        val scatter = (rng.nextDouble() * 0.08 - 0.04).toFloat() * logR
                        val t2 = t + 1.0f / stepsPerArm
                        val logR2 = bigR * 0.94f * t2.pow(0.52f)
                        val theta2 = t2 * PI_F * 5.5f + armOff
                        val alpha = t * 0.88f + 0.12f
                        lines.add(
                            cx + cos(theta) * (logR + scatter), cy + sin(theta) * (logR + scatter),
                            cx + cos(theta2) * logR2, cy + sin(theta2) * logR2,
                            ac[0] * alpha, ac[1] * alpha, ac[2] * alpha, w * (2.0f - t * 1.1f),
                        )
                    }
                    repeat((3 + complexity * 6).toInt()) {
                        val t = (0.1 + rng.nextDouble() * 0.85).toFloat()
                        val lr = bigR * 0.94f * t.pow(0.52f)
                        val th = t * PI_F * 5.5f + armOff
                        addCircle(
                            cx + cos(th) * lr, cy + sin(th) * lr, bigR * (0.008 + rng.nextDouble() * 0.018).toFloat(),
                            col(arm.toDouble() / armCount * 0.7 + 0.05), w * 2.0f, 20,
                        )
                    }
                }
            }
            level < 0.82 -> {
                // Several galaxies with tidal streams towards the centre
                val count = max(symmetry, 5 + (density * 10).toInt())
                val perSector = count / max(1, symmetry)
                for (g in 0 until perSector) {
                    val gt = g.toDouble() / perSector
                    val gAng = gt.toFloat() * sectorAngle + (rng.nextDouble() * 0.3).toFloat()
                    val gDist = bigR * (0.15 + rng.nextDouble() * 0.68).toFloat()
                    val gR = bigR * (0.06 + rng.nextDouble() * 0.16).toFloat()
                    val arms = 2 + (rng.nextDouble() * 3).toInt()
                    val gc = col(gt)
                    forSym(cos(gAng) * gDist, sin(gAng) * gDist) { gx, gy ->
                        drawMiniGalaxy(gx, gy, gR, gt, arms)
                        lines.add(gx, gy, cx, cy, gc[0] * 0.2f, gc[1] * 0.2f, gc[2] * 0.2f, w * 0.2f)
                    }
                }
                for (ri in 0 until 4) {
                    addCircle(cx, cy, bigR * (0.2 + ri * 0.22).toFloat(), scaled(col(ri * 0.15 + 0.4), 0.3f), w * 0.25f, 60)
                }
            }
            else -> {
                // Cosmic web: nodes in one sector, copied by symmetry, connected when close
                val nodeCount = (16 + density * 55).toInt()
                val rel = ArrayList<FloatArray>()
                repeat(nodeCount / max(1, symmetry) + 1) {
                    val ang = rng.nextDouble().toFloat() * sectorAngle
                    val dist = sqrt(rng.nextDouble()).toFloat() * bigR * 0.92f
                    rel += floatArrayOf(cos(ang) * dist, sin(ang) * dist)
                }
                val nodes = ArrayList<FloatArray>()
                for (p in rel) {
                    for (s in 0 until symmetry) {
                        val a = s.toFloat() * PI_F * 2.0f / symmetry
                        val ca = cos(a)
                        val sa = sin(a)
                        nodes += floatArrayOf(cx + p[0] * ca - p[1] * sa, cy + p[0] * sa + p[1] * ca)
                    }
                }
                val connectDist = bigR * 0.42f
                for (i in nodes.indices) {
                    for (j in i + 1 until nodes.size) {
                        val dx = nodes[i][0] - nodes[j][0]
                        val dy = nodes[i][1] - nodes[j][1]
                        val d = sqrt(dx * dx + dy * dy)
                        if (d < connectDist) {
                            val alpha = (1.0 - (d / connectDist).toDouble()).toFloat() * 0.75f
                            val fc = col((i + j).toDouble() / (nodes.size * 2))
                            lines.add(nodes[i][0], nodes[i][1], nodes[j][0], nodes[j][1],
                                fc[0] * alpha, fc[1] * alpha, fc[2] * alpha, w * alpha * 1.5f)
                        }
                    }
                    val nc = col(i.toDouble() / nodes.size * 0.65 + 0.12)
                    val cr = bigR * (0.025 + rng.nextDouble() * 0.06).toFloat()
                    for (ri in 0..3) {
                        val a = (1.0 - ri * 0.22).toFloat()
                        addCircle(nodes[i][0], nodes[i][1], cr * (1.0 + ri * 0.55).toFloat(), scaled(nc, a), w * (2.5 - ri * 0.5).toFloat(), 30)
                    }
                    if (cr > bigR * 0.04f) {
                        drawMiniGalaxy(nodes[i][0], nodes[i][1], cr * 0.8f, i.toDouble() / nodes.size * 0.65 + 0.12, 2)
                    }
                }
                val walls = 4 + (complexity * 5).toInt()
                for (ri in 0 until walls) {
                    val rr = bigR * (0.08 + ri * 0.84 / walls).toFloat()
                    addCircle(cx, cy, rr, scaled(col(ri.toDouble() / walls * 0.45 + 0.48), 0.4f), w * 0.3f, 70)
                }
                for (i in 0..5) {
                    val a = (1.0 - i * 0.15).toFloat()
                    addCircle(cx, cy, bigR * (0.04 + i * 0.03).toFloat(), scaled(col(0.2 + i * 0.03), a), w * (3.0 - i * 0.4).toFloat(), 50)
                }
            }
        }
    }
}
