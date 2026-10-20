package com.mathis.mandala.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Emblem geometry, a straight port of the macOS EmblemGeometry.swift.
//
// Motifs and frames are centre lines with a width at every point, in a unit space with y pointing
// down and the frame ring at radius 0.92. The renderer turns them into lines, outlines, filled
// shapes or beads, so every motif works in every rendering.

internal data class EmblemPoint(val x: Double, val y: Double, val w: Double)

internal data class EmblemDot(val x: Double, val y: Double, val r: Double)

internal data class Pt(val x: Double, val y: Double)

internal class EmblemGeometry {
    val strokes = mutableListOf<List<EmblemPoint>>()
    val dots = mutableListOf<EmblemDot>()

    /**
     * Areas filled in the Filled rendering; each entry is a set of polygons filled even-odd, so an
     * inner polygon cuts a hole. Their outlines come from `strokes`.
     */
    val fills = mutableListOf<List<List<Pt>>>()

    fun append(other: EmblemGeometry) {
        strokes += other.strokes
        dots += other.dots
        fills += other.fills
    }
}

internal object EmblemShapes {

    /** Radius of the frame ring; tree twigs end on it. */
    const val RING_RADIUS = 0.92

    fun geometry(s: EmblemLayerSettings): EmblemGeometry {
        val organic = s.organic.coerceIn(0.0, 1.0)
        val g = when (s.motif) {
            EmblemMotif.OM -> om(organic)
            EmblemMotif.TREE_OF_LIFE -> treeOfLife(s.seed, s.branching, s.roots, organic)
            EmblemMotif.FLOWER_OF_LIFE -> flowerOfLife(s.flowerVariant)
            EmblemMotif.LOTUS -> lotus(s.petals, organic)
            EmblemMotif.YIN_YANG -> yinYang()
            EmblemMotif.DHARMA_WHEEL -> dharmaWheel()
            EmblemMotif.UNALOME -> unalome(organic)
            EmblemMotif.TRIPLE_MOON -> tripleMoon()
            EmblemMotif.SRI_YANTRA -> sriYantra()
        }
        g.append(frame(s.frame))
        return g
    }

    // ── Curves ──

    /**
     * Samples a chain of cubic Béziers. Anchors are [x, y, width], control points [x, y]:
     * P0, C1, C2, P1, C1, C2, P2 … Widths ease between anchors.
     */
    fun cubicChain(c: List<DoubleArray>, steps: Int = 24): List<EmblemPoint> {
        val out = ArrayList<EmblemPoint>()
        var i = 0
        while (i + 3 < c.size) {
            val p0 = c[i]; val c1 = c[i + 1]; val c2 = c[i + 2]; val p1 = c[i + 3]
            val last = i + 3 == c.size - 1
            for (k in 0 until steps + (if (last) 1 else 0)) {
                val t = k.toDouble() / steps; val u = 1 - t
                val x = u * u * u * p0[0] + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t * t * t * p1[0]
                val y = u * u * u * p0[1] + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t * t * t * p1[1]
                val e = t * t * (3 - 2 * t)
                out += EmblemPoint(x, y, p0[2] + (p1[2] - p0[2]) * e)
            }
            i += 3
        }
        return out
    }

    /** One cubic Bézier from p to q. */
    private fun cubic(p: Pt, c1: Pt, c2: Pt, q: Pt, w0: Double, w1: Double, steps: Int = 16): List<EmblemPoint> =
        (0..steps).map { k ->
            val t = k.toDouble() / steps; val u = 1 - t
            EmblemPoint(
                u * u * u * p.x + 3 * u * u * t * c1.x + 3 * u * t * t * c2.x + t * t * t * q.x,
                u * u * u * p.y + 3 * u * u * t * c1.y + 3 * u * t * t * c2.y + t * t * t * q.y,
                w0 + (w1 - w0) * t,
            )
        }

    private fun circle(r: Double, w: Double, cx: Double = 0.0, cy: Double = 0.0, steps: Int = 180): List<EmblemPoint> =
        (0..steps).map { i ->
            val a = i.toDouble() / steps * PI * 2
            EmblemPoint(cx + cos(a) * r, cy + sin(a) * r, w)
        }

    private fun unit(x: Double, y: Double): Pt {
        val l = max(1e-9, hypot(x, y))
        return Pt(x / l, y / l)
    }

    /** Calligraphic pen: thick where the stroke runs across the nib, thin along it. */
    private fun penWidths(pts: List<EmblemPoint>, organic: Double, nib: Double = PI * 0.2): List<EmblemPoint> {
        if (organic <= 0 || pts.size <= 2) return pts
        return pts.indices.map { i ->
            val a = pts[max(0, i - 1)]; val b = pts[min(pts.size - 1, i + 1)]
            val dir = atan2(b.y - a.y, b.x - a.x)
            val pen = 0.35 + 1.1 * abs(sin(dir - nib))
            pts[i].copy(w = pts[i].w * ((1 - organic) + organic * pen))
        }
    }

    private fun d(vararg v: Double) = v

    // ── Om ──

    /**
     * Own drawing of the Om sign: the "3" body, the curl to the right, the crescent and the dot.
     * `organic` turns the even strokes into those of a broad calligraphy pen.
     */
    fun om(organic: Double = 0.0): EmblemGeometry {
        val body = listOf(
            d(-0.62, -0.36, 0.05), d(-0.56, -0.60), d(-0.24, -0.64), d(-0.15, -0.42, 0.14),
            d(-0.07, -0.23), d(-0.22, -0.09), d(-0.40, -0.07, 0.08),
            d(-0.10, -0.10), d(0.03, 0.08), d(0.01, 0.25, 0.17),
            d(-0.01, 0.48), d(-0.30, 0.57), d(-0.48, 0.45, 0.12),
            d(-0.62, 0.37), d(-0.68, 0.22), d(-0.60, 0.14, 0.05),
        )
        val curl = listOf(
            d(0.0, 0.14, 0.07), d(0.12, 0.02), d(0.24, -0.10), d(0.40, -0.10, 0.12),
            d(0.58, -0.10), d(0.72, 0.04), d(0.70, 0.20, 0.15),
            d(0.68, 0.38), d(0.50, 0.46), d(0.36, 0.42, 0.12),
            d(0.22, 0.38), d(0.18, 0.26), d(0.26, 0.18, 0.07),
            d(0.32, 0.13), d(0.40, 0.16), d(0.42, 0.22, 0.03),
        )
        val crescent = listOf(
            d(0.02, -0.56, 0.03), d(0.05, -0.40), d(0.20, -0.33), d(0.34, -0.33, 0.10),
            d(0.48, -0.33), d(0.63, -0.40), d(0.66, -0.56, 0.03),
        )
        // Drawn in a box around (0.02, -0.10); centre it and leave room inside the ring.
        val scale = 0.8; val dx = -0.02; val dy = 0.10
        fun place(p: EmblemPoint) = EmblemPoint((p.x + dx) * scale, (p.y + dy) * scale, p.w * scale)
        val g = EmblemGeometry()
        for (chain in listOf(body, curl, crescent)) g.strokes += penWidths(cubicChain(chain).map(::place), organic)
        g.dots += EmblemDot((0.34 + dx) * scale, (-0.70 + dy) * scale, 0.075 * scale)
        return g
    }

    // ── Tree of Life ──

    private class Branch(val pos: Pt, val count: Int, val kids: List<Branch>)

    /**
     * A tree whose twigs and roots end evenly spread on the ring. The twig tips are placed on the
     * ring first; neighbouring tips are then joined pairwise (sometimes in threes) towards the
     * trunk, which gives the even fan of the classic Tree of Life. `organic` lets the branches
     * flow out of each other in gnarled curves, adds side twigs and rootlets, sways the trunk and
     * lets the crown reach further down. More branching gives more and finer twigs.
     */
    fun treeOfLife(seed: ULong, branching: Double, roots: Boolean, organic: Double = 0.0): EmblemGeometry {
        val rng = SeededRng(seed)
        val g = EmblemGeometry()
        val top = Pt(0.0, -0.10); val bottom = Pt(0.0, 0.32)
        val sway = organic * rng.nextDouble(-0.06, 0.06)
        // The trunk ends exactly where the roots start and leaves both ends vertically, so roots
        // and crown join without a kink; only its middle sways.
        val midY = (bottom.y + top.y) / 2
        g.strokes += cubicChain(
            listOf(
                d(bottom.x, bottom.y, 0.13), d(0.0, bottom.y - 0.07), d(sway, midY + 0.07), d(sway, midY, 0.085),
                d(sway, midY - 0.07), d(0.0, top.y + 0.07), d(top.x, top.y, 0.095),
            ),
            steps = 14,
        )
        val b = branching.coerceIn(0.0, 1.0)
        val twigs = 8 + (b * 56).roundToInt()
        val reach = 0.10 * organic
        branches(g, rng, top, up = true, lo = -PI * (0.92 + reach), hi = -PI * (0.08 - reach),
            n = twigs, maxWidth = 0.085, pull = 0.85, organic = organic, fineness = b)
        if (roots) {
            branches(g, rng, bottom, up = false, lo = PI * (0.14 - reach * 0.4), hi = PI * (0.86 + reach * 0.4),
                n = max(4, twigs / 2), maxWidth = 0.08, pull = 0.7, organic = organic, fineness = b)
        }
        return g
    }

    private fun branches(
        g: EmblemGeometry, rng: SeededRng, origin: Pt, up: Boolean, lo: Double, hi: Double,
        n: Int, maxWidth: Double, pull: Double, organic: Double, fineness: Double,
    ) {
        val step = (hi - lo) / n
        val tipPoints = ArrayList<Pt>()
        for (i in 0 until n) {
            val a = lo + step * (i + 0.5) + rng.nextDouble(-0.3, 0.3) * step
            tipPoints += Pt(cos(a) * RING_RADIUS, sin(a) * RING_RADIUS)
        }
        // Twigs get finer the more of them there are.
        val twigWidth = 0.012 - 0.006 * fineness
        val tipWidth = 0.008 - 0.004 * fineness
        fun width(count: Int) = twigWidth + maxWidth * sqrt(count.toDouble() / n)

        // Build the branching from the tips towards the trunk.
        fun node(a: Int, b: Int, depth: Int): Branch {
            if (b - a == 1) return Branch(tipPoints[a], 1, emptyList())
            val bounds: List<Int> = if (b - a >= 4 && rng.nextDouble() < 0.3) {
                val third = (b - a) / 3.0
                listOf(a, a + third.roundToInt(), a + (third * 2).roundToInt(), b)
            } else {
                val mid = (a + b) / 2.0 + rng.nextDouble(-0.2, 0.2) * (b - a)
                listOf(a, min(b - 1, max(a + 1, mid.roundToInt())), b)
            }
            val kids = (0 until bounds.size - 1).map { node(bounds[it], bounds[it + 1], depth + 1) }
            val count = kids.sumOf { it.count }
            val mx = kids.fold(0.0) { s, k -> s + k.pos.x * k.count } / count
            val my = kids.fold(0.0) { s, k -> s + k.pos.y * k.count } / count
            val f = if (depth > 0) pull * (1 - count.toDouble() / n).pow(0.6) else 0.0
            return Branch(Pt(origin.x + (mx - origin.x) * f, origin.y + (my - origin.y) * f), count, kids)
        }
        val root = node(0, n, 0)

        // Direction in which a branch leaves its junction: towards the weighted middle of its twigs.
        fun heading(b: Branch): Pt {
            if (b.kids.isEmpty()) return unit(b.pos.x, b.pos.y) // twigs meet the ring radially
            val mx = b.kids.fold(0.0) { s, k -> s + k.pos.x * k.count } / b.count
            val my = b.kids.fold(0.0) { s, k -> s + k.pos.y * k.count } / b.count
            return unit(mx - b.pos.x, my - b.pos.y)
        }

        // Bends a branch gently back and forth, keeping both ends in place.
        fun gnarl(pts: List<EmblemPoint>, length: Double): List<EmblemPoint> {
            if (organic <= 0 || pts.size <= 2) return pts
            val amp = min(0.018, organic * 0.06 * length)
            val freq = rng.nextDouble(0.7, 1.5)
            val phase = rng.nextDouble(0.0, 2 * PI)
            return pts.indices.map { i ->
                val a = pts[max(0, i - 1)]; val b = pts[min(pts.size - 1, i + 1)]
                val nrm = unit(-(b.y - a.y), b.x - a.x)
                val t = i.toDouble() / (pts.size - 1)
                val off = amp * sin(PI * t) * sin(2 * PI * freq * t + phase)
                pts[i].copy(x = pts[i].x + nrm.x * off, y = pts[i].y + nrm.y * off)
            }
        }

        // Short side twigs (or rootlets) that grow off a branch and stop before the ring.
        fun sprigs(pts: List<EmblemPoint>, count: Int) {
            if (organic <= 0.1 || pts.size <= 4) return
            val chance = organic * (0.25 + 0.6 * fineness) * (if (count <= 2) 1.0 else 0.6)
            for (k in 0 until 2) {
                if (rng.nextDouble() >= chance) continue
                val i = ((pts.size - 1) * rng.nextDouble(0.3, 0.8)).toInt()
                val a = pts[max(0, i - 1)]; val b = pts[min(pts.size - 1, i + 1)]
                val base = atan2(b.y - a.y, b.x - a.x)
                val side = if (rng.nextDouble() < 0.5) -1.0 else 1.0
                val dir = base + side * rng.nextDouble(0.45, 0.8)
                val len = rng.nextDouble(0.04, 0.09) * (1 - 0.3 * fineness)
                val p = Pt(pts[i].x, pts[i].y)
                val q = Pt(p.x + cos(dir) * len, p.y + sin(dir) * len)
                if (hypot(q.x, q.y) >= RING_RADIUS - 0.04) continue
                val bend = rng.nextDouble(-0.25, 0.25) * len
                val c = Pt((p.x + q.x) / 2 - sin(dir) * bend, (p.y + q.y) / 2 + cos(dir) * bend)
                val w0 = max(tipWidth, pts[i].w * 0.45)
                g.strokes += cubic(p, c, c, q, w0, tipWidth * 0.6, steps = 10)
            }
        }

        // Draw from the trunk outwards so every branch continues the direction it came from.
        fun draw(b: Branch, incoming: Pt) {
            for (kid in b.kids) {
                val p = b.pos; val q = kid.pos
                val len = hypot(q.x - p.x, q.y - p.y)
                val chord = unit(q.x - p.x, q.y - p.y)
                // Start along the parent's direction (organic) or straight at the target.
                val d0 = unit(chord.x + (incoming.x - chord.x) * organic, chord.y + (incoming.y - chord.y) * organic)
                val h = heading(kid)
                val d1 = unit(chord.x + (h.x - chord.x) * organic * 0.8, chord.y + (h.y - chord.y) * organic * 0.8)
                val bend = rng.nextDouble(-0.18, 0.18) * (1 - organic * 0.4)
                val k0 = 0.3 + 0.15 * organic; val k1 = 0.3
                val c1 = Pt(p.x + d0.x * len * k0 - chord.y * bend * len, p.y + d0.y * len * k0 + chord.x * bend * len)
                val c2 = Pt(q.x - d1.x * len * k1 - chord.y * bend * len, q.y - d1.y * len * k1 + chord.x * bend * len)
                val w0 = width(kid.count) * (1 + 0.2 * organic)
                val w1 = if (kid.count > 1) width(kid.count) * 0.8 else tipWidth
                val pts = gnarl(cubic(p, c1, c2, q, w0, w1, steps = 24), len)
                g.strokes += pts
                if (b !== root) sprigs(pts, kid.count) // none right at the trunk
                draw(kid, d1)
            }
        }
        draw(root, if (up) Pt(0.0, -1.0) else Pt(0.0, 1.0))
    }

    // ── Flower of Life ──

    /** The circles of the sacred-geometry family on a hexagonal grid, a corner pointing up. */
    fun flowerOfLife(variant: EmblemFlowerVariant): EmblemGeometry {
        val g = EmblemGeometry()
        val line = 0.022
        val angles = (0 until 6).map { -PI / 2 + it * PI / 3 }
        when (variant) {
            EmblemFlowerVariant.SEED -> {
                val r = 0.42
                g.strokes += circle(r, line)
                for (a in angles) g.strokes += circle(r, line, cos(a) * r, sin(a) * r)
            }
            EmblemFlowerVariant.FLOWER -> {
                val r = 0.84 / 3
                val outer = 3 * r
                // Lattice points along two grid axes; full circles up to two steps out.
                for (i in -3..3) {
                    for (j in -3..3) {
                        val x = r * (i * cos(angles[0]) + j * cos(angles[1]))
                        val y = r * (i * sin(angles[0]) + j * sin(angles[1]))
                        val dist = hypot(x, y)
                        if (dist <= 2 * r + 1e-6) {
                            g.strokes += circle(r, line, x, y)
                        } else if (dist <= 3 * r + 1e-6) {
                            // The next ring shows only as arcs inside the enclosing circle.
                            var run = ArrayList<EmblemPoint>()
                            for (p in circle(r, line * 0.8, x, y, steps = 240)) {
                                if (hypot(p.x, p.y) <= outer + 1e-6) {
                                    run += p
                                } else if (run.isNotEmpty()) {
                                    if (run.size > 1) g.strokes += run
                                    run = ArrayList()
                                }
                            }
                            if (run.size > 1) g.strokes += run
                        }
                    }
                }
                g.strokes += circle(outer, line * 1.4)
            }
            EmblemFlowerVariant.FRUIT, EmblemFlowerVariant.METATRON -> {
                val r = 0.84 / 5
                val centres = mutableListOf(Pt(0.0, 0.0))
                for (a in angles) centres += Pt(cos(a) * 2 * r, sin(a) * 2 * r)
                for (a in angles) centres += Pt(cos(a) * 4 * r, sin(a) * 4 * r)
                for (c in centres) g.strokes += circle(r, line, c.x, c.y)
                if (variant == EmblemFlowerVariant.METATRON) {
                    for (i in centres.indices) {
                        for (j in i + 1 until centres.size) {
                            g.strokes += listOf(
                                EmblemPoint(centres[i].x, centres[i].y, line * 0.6),
                                EmblemPoint(centres[j].x, centres[j].y, line * 0.6),
                            )
                        }
                    }
                }
            }
        }
        return g
    }

    // ── Lotus ──

    private class PetalShape(
        val angle: Double, val r0: Double, val r1: Double, val halfWidth: Double, val curl: Double,
        /** Yantra petal: full width where it sits on the circle, curving in to a point like an arch. */
        val arch: Boolean = false,
    ) {
        /** Lotus: full, rounded body and a short point. Arch: widest at the base. */
        fun halfWidthAt(t0: Double): Double {
            val t = t0.coerceIn(0.0, 1.0)
            if (arch) return halfWidth * sin(PI / 2 * (1 - t)).pow(0.55) * (1 + 0.18 * sin(PI * t))
            return halfWidth * sin(PI * t.pow(0.85)).pow(0.6)
        }

        /** Whether a point lies inside the petal (with a small margin so cut lines meet cleanly). */
        fun contains(x: Double, y: Double): Boolean {
            val ca = cos(-angle); val sa = sin(-angle)
            val u = x * ca - y * sa; val v = x * sa + y * ca
            val t = (u - r0) / (r1 - r0)
            if (!(t > 0.01 && t < 0.99)) return false
            return abs(v - curl * t * t) < halfWidthAt(t) - 0.004
        }

        fun outline(w: Double): List<EmblemPoint> {
            val steps = 40
            val right = ArrayList<Pt>(); val left = ArrayList<Pt>()
            for (i in 0..steps) {
                val t = i.toDouble() / steps
                val u = r0 + (r1 - r0) * t; val shift = curl * t * t; val h = halfWidthAt(t)
                right += Pt(u, shift + h); left += Pt(u, shift - h)
            }
            val pts = right + left.reversed()
            val ca = cos(angle); val sa = sin(angle)
            return pts.map { EmblemPoint(it.x * ca - it.y * sa, it.x * sa + it.y * ca, w) }
        }

        fun vein(w: Double): List<EmblemPoint> {
            val ca = cos(angle); val sa = sin(angle)
            return (0..20).map { i ->
                val t = 0.08 + i / 20.0 * 0.62
                val u = r0 + (r1 - r0) * t; val v = curl * t * t
                EmblemPoint(u * ca - v * sa, u * sa + v * ca, w * (1 - 0.6 * i / 20.0))
            }
        }
    }

    /** Keeps the parts of a line that no occluder covers. */
    private fun visibleRuns(pts: List<EmblemPoint>, hides: (Double, Double) -> Boolean): List<List<EmblemPoint>> {
        val runs = ArrayList<List<EmblemPoint>>()
        var run = ArrayList<EmblemPoint>()
        for (p in pts) {
            if (hides(p.x, p.y)) {
                if (run.size > 1) runs += run
                run = ArrayList()
            } else {
                run += p
            }
        }
        if (run.size > 1) runs += run
        return runs
    }

    /**
     * A lotus seen from above: three rings of pointed petals around the seed pod. Inner petals
     * lie in front of outer ones. `organic` gives the petals uneven sizes, a curved tip and a vein.
     */
    fun lotus(petals: Int, organic: Double = 0.0): EmblemGeometry {
        val g = EmblemGeometry()
        val rng = SeededRng(0x10705uL)
        val n = max(4, petals)
        val podRadius = 0.14
        class Ring(val count: Int, val r0: Double, val r1: Double, val offset: Double, val width: Double)
        val rings = listOf(
            Ring(n, 0.10, 0.52, 0.5, 0.026), // inner, in front
            Ring(n, 0.18, 0.74, 0.0, 0.024),
            Ring(n * 2, 0.30, 0.88, 0.25, 0.020), // outer, behind
        )
        val front = ArrayList<PetalShape>()
        for (ring in rings) {
            // Petals of one ring touch at their widest point.
            val half = PI * (ring.r0 + ring.r1) / 2 / ring.count
            val shapes = ArrayList<PetalShape>()
            for (i in 0 until ring.count) {
                val a = (i + ring.offset) / ring.count * PI * 2 - PI / 2
                val grow = 1 + organic * rng.nextDouble(-0.08, 0.06)
                val curl = organic * rng.nextDouble(-0.06, 0.06)
                shapes += PetalShape(a, ring.r0, ring.r0 + (ring.r1 - ring.r0) * grow, half, curl)
            }
            val covered = { x: Double, y: Double -> hypot(x, y) < podRadius || front.any { it.contains(x, y) } }
            for (shape in shapes) {
                g.strokes += visibleRuns(shape.outline(ring.width), covered)
                if (organic > 0.2) g.strokes += visibleRuns(shape.vein(ring.width * 0.6), covered)
            }
            front += shapes
        }
        // Seed pod with its seeds.
        g.strokes += circle(podRadius, 0.026)
        g.dots += EmblemDot(0.0, 0.0, 0.026)
        for (i in 0 until 6) {
            val a = i / 6.0 * PI * 2 - PI / 2
            g.dots += EmblemDot(cos(a) * 0.08, sin(a) * 0.08, 0.02)
        }
        return g
    }

    // ── Yin Yang ──

    /** Yin and yang, exact and closed. For an open brush circle around it, use the Ensō frame. */
    fun yinYang(): EmblemGeometry {
        val g = EmblemGeometry()
        val r = 0.70; val h = r / 2; val w = 0.03
        fun arc(cx: Double, cy: Double, rad: Double, a0: Double, a1: Double, steps: Int = 90): List<Pt> =
            (0..steps).map { i ->
                val a = a0 + (a1 - a0) * i / steps
                Pt(cx + cos(a) * rad, cy + sin(a) * rad)
            }
        // The S: upper half bulges left, lower half bulges right.
        val s = arc(0.0, -h, h, -PI / 2, -1.5 * PI) + arc(0.0, h, h, -PI / 2, PI / 2).drop(1)
        g.strokes += circle(r, w)
        g.strokes += s.map { EmblemPoint(it.x, it.y, w * 0.8) }
        // Filled half: the right side of the circle and back along the S, with the other eye cut out.
        val eye = r * 0.13
        val half = arc(0.0, 0.0, r, -PI / 2, PI / 2) + s.reversed()
        g.fills += listOf(half, arc(0.0, -h, eye, 0.0, 2 * PI, steps = 48))
        g.strokes += circle(eye, w * 0.7, 0.0, -h, steps = 60)
        g.dots += EmblemDot(0.0, h, eye)
        return g
    }

    // ── Dharma wheel ──

    /** The eight-spoked wheel: double rim, hub and spindle-shaped spokes with knobs outside. */
    fun dharmaWheel(): EmblemGeometry {
        val g = EmblemGeometry()
        val outer = 0.72; val inner = 0.60; val hub = 0.15
        g.strokes += circle(outer, 0.035)
        g.strokes += circle(inner, 0.026)
        g.strokes += circle(hub, 0.03)
        g.dots += EmblemDot(0.0, 0.0, 0.055)
        for (k in 0 until 8) {
            val a = -PI / 2 + k * PI / 4
            g.strokes += (0..24).map { i ->
                val t = i / 24.0
                val rr = hub + (inner - hub) * t
                // Spindle: thin at both ends, widest a third of the way out.
                val bulge = sin(PI * t.pow(0.75))
                EmblemPoint(cos(a) * rr, sin(a) * rr, 0.018 + 0.05 * bulge)
            }
            g.strokes += listOf(EmblemPoint(cos(a) * outer, sin(a) * outer, 0.03), EmblemPoint(cos(a) * 0.79, sin(a) * 0.79, 0.03))
            g.dots += EmblemDot(cos(a) * 0.82, sin(a) * 0.82, 0.045)
        }
        return g
    }

    // ── Unalome ──

    /**
     * The unalome: a spiral at the bottom, loops that grow smaller on the way up, a straight
     * line and three dots. `organic` gives it the strokes of a broad pen.
     */
    fun unalome(organic: Double = 0.0): EmblemGeometry {
        val g = EmblemGeometry()
        val w = 0.032
        val pts = ArrayList<EmblemPoint>()
        // Spiral from the centre outwards, ending at its top.
        val cx = 0.0; val cy = 0.50; val spiral = 0.17; val turns = 2.5
        for (i in 0..200) {
            val t = i / 200.0
            val a = PI / 2 + t * turns * 2 * PI
            pts += EmblemPoint(cx + cos(a) * spiral * t, cy + sin(a) * spiral * t, w * (0.4 + 0.6 * t))
        }
        // Loops climbing upwards, each smaller than the one before.
        val loops = 3.0; val yStart = cy - spiral; val yTop = -0.25
        for (i in 1..400) {
            val t = i / 400.0
            val th = t * loops * 2 * PI
            val amp = 0.20 * (1 - 0.65 * t)
            val back = 0.10 * (1 - 0.6 * t)
            pts += EmblemPoint(amp * sin(th), yStart + (yTop - yStart) * t + back * cos(th) - back, w)
        }
        // Straight line to the top.
        val last = pts.last()
        for (i in 1..20) {
            val t = i / 20.0
            pts += EmblemPoint(last.x * (1 - t), last.y + (-0.58 - last.y) * t, w * (1 - 0.3 * t))
        }
        g.strokes += penWidths(pts, organic)
        for (y in doubleArrayOf(-0.67, -0.75, -0.83)) g.dots += EmblemDot(0.0, y, 0.026)
        return g
    }

    // ── Triple moon ──

    /** Waxing crescent, full moon, waning crescent. */
    fun tripleMoon(): EmblemGeometry {
        val g = EmblemGeometry()
        val r = 0.24; val w = 0.026
        g.strokes += circle(r, w, steps = 120)
        g.fills += listOf((0..120).map { i ->
            val a = i / 120.0 * 2 * PI
            Pt(cos(a) * r, sin(a) * r)
        })
        for (side in doubleArrayOf(-1.0, 1.0)) {
            // A crescent with its back to the full moon: the disc minus one shifted outwards.
            val cx = side * 0.58; val dd = 0.13
            val phi = atan2(sqrt(r * r - dd * dd / 4), dd / 2)
            val poly = ArrayList<Pt>()
            for (i in 0..80) {
                val a = phi + (2 * PI - 2 * phi) * i / 80
                poly += Pt(cx + side * cos(a) * r, sin(a) * r)
            }
            for (i in 0..60) {
                val a = (PI + phi) - 2 * phi * i / 60
                poly += Pt(cx + side * (dd + cos(a) * r), sin(a) * r)
            }
            g.fills += listOf(poly)
            g.strokes += (poly + poly[0]).map { EmblemPoint(it.x, it.y, w) }
        }
        return g
    }

    // ── Sri Yantra ──

    /**
     * The nine interlocking triangles (four pointing up, five down) with the bindu, inside two
     * lotus rings of 8 and 16 petals. Triangle coordinates from Bhaskar Tripathi, "Sri Yantra",
     * TeXample.net (CC BY-SA 4.0): left x, base y, apex y, right x in a circle of radius 100
     * around (150, 150), y down.
     */
    fun sriYantra(): EmblemGeometry {
        val g = EmblemGeometry()
        val triangles = listOf(
            d(53.65669559977147, 123.20508075688774, 250.0, 246.34330440022853), // down
            d(52.984011026495736, 174.24660560764943, 50.0, 247.01598897350425), // up
            d(98.71823312733801, 220.03828947357886, 123.20508075688774, 201.281766872662), // up
            d(78.26467997914015, 197.92315674002487, 78.10499177949904, 221.73532002085986), // up
            d(90.4856922951427, 78.10499177949904, 160.66014976539617, 209.51430770485734), // down
            d(80.98384838952128, 103.12199145016105, 220.03828947357886, 219.0161516104787), // down
            d(114.9488500600036, 160.66014976539617, 103.12199145016105, 185.0511499399964), // up
            d(116.35142605010424, 134.30757626706648, 197.92315674002487, 183.64857394989576), // down
            d(124.61190803072795, 144.79777263138968, 174.24660560764943, 175.38809196927207), // down
        )
        val inner = 0.46
        fun map(x: Double, y: Double) = Pt((x - 150) / 100 * inner, (y - 150) / 100 * inner)
        for (t in triangles) {
            val pts = listOf(map(t[0], t[1]), map(150.0, t[2]), map(t[3], t[1]), map(t[0], t[1]))
            g.strokes += pts.map { EmblemPoint(it.x, it.y, 0.012) }
        }
        g.strokes += circle(inner, 0.016)
        g.dots += EmblemDot(0.0, 0.0, 0.022)
        // Lotus rings: 8 petals, then 16, each closed by a circle.
        for ((count, r0, r1, offset) in listOf(Ring4(8, inner, 0.65, 0.5), Ring4(16, 0.665, 0.83, 0.25))) {
            // Neighbouring petals touch where they sit on the circle.
            val half = PI * r0 / count
            for (i in 0 until count) {
                val a = (i + offset) / count * PI * 2 - PI / 2
                g.strokes += PetalShape(a, r0, r1, half, 0.0, arch = true).outline(0.014)
            }
            g.strokes += circle(r1 + 0.012, 0.014)
        }
        return g
    }

    private data class Ring4(val count: Int, val r0: Double, val r1: Double, val offset: Double)

    // ── Frames ──

    fun frame(f: EmblemFrame): EmblemGeometry {
        val g = EmblemGeometry()
        val r = RING_RADIUS
        when (f) {
            EmblemFrame.NONE -> Unit
            EmblemFrame.RING -> g.strokes += circle(r, 0.035)
            EmblemFrame.DOUBLE_RING -> {
                g.strokes += circle(r, 0.035)
                g.strokes += circle(r + 0.08, 0.018)
            }
            EmblemFrame.LOTUS -> {
                g.strokes += circle(r, 0.035)
                val petals = 16
                for (i in 0 until petals) {
                    g.strokes += petal(i.toDouble() / petals * PI * 2, r + 0.02, r + 0.30, 0.16, 0.018)
                }
                g.strokes += circle(r + 0.34, 0.012)
            }
            EmblemFrame.GATES -> {
                g.strokes += circle(r, 0.035)
                for ((i, s) in doubleArrayOf(1.06, 1.14, 1.22).withIndex()) {
                    g.strokes += gateSquare(s, 0.26, 0.09, if (i == 1) 0.022 else 0.014)
                }
            }
            EmblemFrame.ENSO -> g.append(enso(r))
        }
        return g
    }

    /**
     * Ensō: an open circle in one brush stroke. It starts heavy, thins out along the way and
     * ends in a few dry bristle traces; the gap stays open.
     */
    private fun enso(r: Double): EmblemGeometry {
        val g = EmblemGeometry()
        val start = -PI * 0.58 // upper left, where the brush is set down
        val sweep = 2 * PI * 0.9 // leaves a tenth of the circle open
        val steps = 260
        fun point(t: Double, dr: Double): Pt {
            // The brush drifts slightly inwards towards the end, as a hand does.
            val a = start + sweep * t
            val rr = r - 0.03 * t * t + dr
            return Pt(cos(a) * rr, sin(a) * rr)
        }
        // Main stroke: a heavy, round start, a full body, then thinning out.
        val main = ArrayList<EmblemPoint>()
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            // Swells quickly where the brush is pressed down, then loses ink towards the end.
            val w = if (t < 0.06) 0.06 + 0.04 * t / 0.06 else 0.10 * (1 - 0.85 * t.pow(1.3))
            val p = point(t, 0.0)
            main += EmblemPoint(p.x, p.y, max(0.006, w))
        }
        g.strokes += main
        // Dry brush: bristle traces that split off over the last third and stop one after another.
        val traces = listOf(d(-0.028, 0.97, 0.010), d(-0.012, 1.0, 0.012), d(0.010, 0.93, 0.009), d(0.026, 0.88, 0.007))
        for ((offset, endT, w) in traces.map { Triple(it[0], it[1], it[2]) }) {
            val trace = ArrayList<EmblemPoint>()
            for (i in 0..80) {
                val t = 0.62 + (endT - 0.62) * i / 80
                val spread = offset * min(1.0, (t - 0.62) / 0.12)
                val p = point(t, spread)
                trace += EmblemPoint(p.x, p.y, w * (1 - 0.7 * i / 80))
            }
            g.strokes += trace
        }
        return g
    }

    /** Pointed lotus petal as a closed outline, pointing outwards at `angle`. */
    private fun petal(angle: Double, r0: Double, r1: Double, halfWidth: Double, w: Double): List<EmblemPoint> {
        val mid = ArrayList<Pt>(); val side = ArrayList<Pt>()
        for (i in 0..20) {
            val t = i / 20.0
            mid += Pt(r0 + (r1 - r0) * t, 0.0)
            side += Pt(r0 + (r1 - r0) * t, halfWidth * sin(PI * t.pow(0.7)))
        }
        val other = mid.indices.reversed().map { Pt(mid[it].x, 2 * mid[it].y - side[it].y) }
        val outline = side + other
        val ca = cos(angle); val sa = sin(angle)
        return outline.map { EmblemPoint(it.x * ca - it.y * sa, it.x * sa + it.y * ca, w) }
    }

    /** Square with a T-shaped gate in the middle of each side (the bhupura of yantras). */
    private fun gateSquare(s: Double, gate: Double, t: Double, w: Double): List<EmblemPoint> {
        val g = gate
        val side = listOf(
            Pt(s, -s), Pt(s, -g - t), Pt(s + t, -g - t), Pt(s + t, -g), Pt(s + 2 * t, -g),
            Pt(s + 2 * t, g), Pt(s + t, g), Pt(s + t, g + t), Pt(s, g + t),
        )
        val pts = ArrayList<EmblemPoint>()
        for (k in 0 until 4) {
            val a = k * PI / 2; val ca = cos(a); val sa = sin(a)
            pts += side.map { EmblemPoint(it.x * ca - it.y * sa, it.x * sa + it.y * ca, w) }
        }
        pts += pts[0]
        return pts
    }
}
