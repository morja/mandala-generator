package com.mathis.mandala.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The emblem layer of the macOS app (EmblemLayer.swift): the emblem geometry drawn as an 8-bit
 * coverage mask, coloured from a palette by distance from the centre, then glow and opacity, then
 * blended over the image. The overlay is premultiplied (alpha = coverage) and never stored: its
 * rows are computed from the mask where the glow and the blend need them.
 */
internal class EmblemLayer(private val par: Parallel, private val fx: LayerEffects) {

    fun apply(base: Image8, s: EmblemLayerSettings, palettes: List<ColorPalette>, place: Placement): Image8 {
        // The emblem belongs to the mandala: it is sized by the mandala's square and drawn around
        // its centre, on the part of the canvas it and its glow can reach (all of it by default).
        val size = place.size
        val geometry = EmblemShapes.geometry(s)
        // Unit space → pixels: the frame ring (radius 0.92) gets a diameter of `size` × canvas.
        val unit = s.size.coerceIn(0.1, 1.0) * size * 0.5 / EmblemShapes.RING_RADIUS
        val glowReach = if (s.glow > 0) 3 * 60.0 * s.glow * size / 1600 else 0.0
        // Its own offset from the mandala centre (ADR-0011).
        val centreX = place.centreX + s.offsetX * size
        val centreY = place.centreY + s.offsetY * size
        val area = place.around(centreX, centreY, max(size.toDouble(), unit * 1.6 + glowReach + 4))
        val w = area[2] - area[0]
        val h = area[3] - area[1]
        if (w <= 0 || h <= 0) return base
        val ecx = centreX - area[0]
        val ecy = centreY - area[1]
        val mask = EmblemMask(w, h, ecx, ecy, unit, s.rotation).draw(geometry, s.rendering, s.weight, par)

        val palette = palettes[s.paletteIndex.coerceIn(0, palettes.size - 1)]
        val colour = ColourSource(mask, w, h, ecx, ecy, lut(palette), s.colorPosition, s.colorSpread, unit)
        val op = s.blendMode
        // Alpha only matters where the overlay covers the image: source-over and multiply.
        val needsAlpha = op == LayerBlendMode.NORMAL || op == LayerBlendMode.MULTIPLY
        val passes = glowPasses(s.glow)

        // Core Image's glow keeps the blurred alpha unscaled and sums it over the passes.
        val glowAlpha: ByteArray? = if (needsAlpha && passes != null) {
            val alphaPasses = passes.map { LayerEffects.BlurPass(it.radius, 1.0) }
            fx.referenceBlurOverlay(AlphaSource(mask, w, h), size, alphaPasses)?.let { ref ->
                ByteArray(w * h).also { out ->
                    Filters.resampleRows(ref.asRowSource(), w, h, par) { y, row ->
                        for (x in 0 until w) out[y * w + x] = WorkingSpace.quantize(row[x * 3])
                    }
                }
            }
        } else {
            null
        }
        val glow = passes?.let { fx.referenceBlurOverlay(colour, size, it) }

        val fade = if (s.opacity < 0.999) s.opacity.coerceIn(0.0, 1.0).toFloat() else 1f
        val canvasW = base.width
        val out = if (base.encoding == Encoding.SRGB) base else Image8(canvasW, base.height, Encoding.SRGB)
        val n = canvasW * 3
        fun blendRow(y: Int, over: FloatArray?) {
            val row = FloatArray(n)
            val c = FloatArray(w * 3)
            WorkingSpace.readRow(base, y + area[1], row)
            colour.readRow(y, c)
            for (x in 0 until w) {
                val p = y * w + x
                var a = (mask[p].toInt() and 0xFF) / 255f
                if (glowAlpha != null) {
                    val ga = (glowAlpha[p].toInt() and 0xFF) / 255f
                    a = a + ga - a * ga
                }
                a = a.coerceIn(0f, 1f) * fade
                for (ch in 0..2) {
                    val j = x * 3 + ch
                    val k = (x + area[0]) * 3 + ch
                    var sc = c[j]
                    if (over != null) sc = sc + over[j] - sc * over[j]
                    sc = sc.coerceIn(0f, 1f) * fade
                    val d = row[k]
                    // Blend formulas for a premultiplied overlay on an opaque base.
                    row[k] = when (op) {
                        LayerBlendMode.NORMAL -> sc + d * (1 - a)
                        LayerBlendMode.SCREEN -> d + sc - d * sc
                        LayerBlendMode.ADD -> d + sc
                        LayerBlendMode.MULTIPLY -> d * (1 - a + sc)
                    }
                }
            }
            WorkingSpace.writeRow(row, out, y + area[1])
        }
        if (glow != null) {
            Filters.resampleRows(glow.asRowSource(), w, h, par) { y, over -> blendRow(y, over) }
        } else {
            par.forRange(h) { from, until -> for (y in from until until) blendRow(y, null) }
        }
        if (out !== base) {
            // A new image: the rows outside the emblem's area still need converting to sRGB.
            par.forRange(base.height) { from, until ->
                val row = FloatArray(n)
                for (y in from until until) {
                    if (y >= area[1] && y < area[3]) continue
                    WorkingSpace.readRow(base, y, row)
                    WorkingSpace.writeRow(row, out, y)
                }
            }
        }
        return out
    }

    /** The three glow passes of the macOS applyGlow (radii in 1600 px reference pixels), or null for none. */
    private fun glowPasses(intensity: Double): List<LayerEffects.BlurPass>? {
        if (intensity <= 0) return null
        val i = intensity
        return listOf(
            LayerEffects.BlurPass(2.0 * i * 3.0, 1.0 * i),
            LayerEffects.BlurPass(8.0 * i * 3.0, 0.4 * i),
            LayerEffects.BlurPass(20.0 * i * 3.0, 0.15 * i),
        )
    }

    /**
     * 256 palette colours as P3 working values. The macOS app converts them to device RGB (which
     * clips them to sRGB) and hands them to Core Image as an sRGB image, which reads them back as P3.
     */
    private fun lut(palette: ColorPalette): FloatArray {
        val out = FloatArray(256 * 3)
        val p3 = FloatArray(3)
        val srgb = DoubleArray(3)
        val back = DoubleArray(3)
        for (i in 0 until 256) {
            palette.colorAt(i / 255.0, p3)
            ColorSpaces.p3ToSrgb(p3[0].toDouble(), p3[1].toDouble(), p3[2].toDouble(), srgb)
            ColorSpaces.srgbToP3(srgb[0].coerceIn(0.0, 1.0), srgb[1].coerceIn(0.0, 1.0), srgb[2].coerceIn(0.0, 1.0), back)
            for (ch in 0..2) out[i * 3 + ch] = back[ch].toFloat()
        }
        return out
    }

    /** Premultiplied overlay colour: palette colour by distance from the centre times coverage. */
    private class ColourSource(
        private val mask: ByteArray,
        override val width: Int,
        override val height: Int,
        private val centreX: Double,
        private val centreY: Double,
        private val lut: FloatArray,
        private val position: Double,
        private val spread: Double,
        unit: Double,
    ) : RowSource {
        // The colour reaches `position + spread` at the outer edge of the lotus or gate frame.
        private val outer = unit * 1.45

        override fun readRow(y: Int, out: FloatArray) {
            val dy = y + 0.5 - centreY
            for (x in 0 until width) {
                val a = mask[y * width + x].toInt() and 0xFF
                val o = x * 3
                if (a == 0) {
                    out[o] = 0f; out[o + 1] = 0f; out[o + 2] = 0f
                    continue
                }
                val dx = x + 0.5 - centreX
                // Run along the palette and back instead of jumping from its end to its start.
                var t = (position + spread * min(1.0, sqrt(dx * dx + dy * dy) / outer)) % 2.0
                if (t < 0) t += 2
                if (t > 1) t = 2 - t
                val i = (t * 255).toInt() * 3
                val af = a / 255f
                out[o] = lut[i] * af; out[o + 1] = lut[i + 1] * af; out[o + 2] = lut[i + 2] * af
            }
        }
    }

    /** The coverage as a grey image, for blurring the alpha channel like the colour. */
    private class AlphaSource(private val mask: ByteArray, override val width: Int, override val height: Int) : RowSource {
        override fun readRow(y: Int, out: FloatArray) {
            for (x in 0 until width) {
                val a = (mask[y * width + x].toInt() and 0xFF) / 255f
                out[x * 3] = a; out[x * 3 + 1] = a; out[x * 3 + 2] = a
            }
        }
    }
}

/**
 * 8-bit coverage of the emblem, drawn the way the macOS app draws into a grey Core Graphics
 * context: round-capped line segments, discs, rings and even-odd filled polygons, each painted
 * source-over in white (or black, to cut out) in order. Row 0 is the top of the image.
 */
internal class EmblemMask(
    private val width: Int,
    private val height: Int,
    private val centreX: Double,
    private val centreY: Double,
    private val unit: Double,
    rotationDegrees: Double,
) {

    // Positive degrees turn clockwise on screen (y points down in unit space).
    private val ca = cos(rotationDegrees * PI / 180)
    private val sa = sin(rotationDegrees * PI / 180)
    private fun px(x: Double, y: Double) = centreX + unit * (x * ca - y * sa)
    private fun py(x: Double, y: Double) = centreY + unit * (x * sa + y * ca)

    private sealed class Shape(val white: Boolean, val top: Double, val bottom: Double)

    /** Round-capped segment of half-width r (pixels). */
    private class Segment(val ax: Double, val ay: Double, val bx: Double, val by: Double, val r: Double, white: Boolean) :
        Shape(white, min(ay, by) - r - 1, max(ay, by) + r + 1)

    /** Disc of radius r, or with `ring` > 0 a ring of that half-width around radius r. */
    private class Disc(val cx: Double, val cy: Double, val r: Double, val ring: Double, white: Boolean) :
        Shape(white, cy - r - ring - 1, cy + r + ring + 1)

    /** Polygons filled together with the even-odd rule (pixel coordinates, x and y interleaved). */
    private class Polygons(val polys: List<DoubleArray>, top: Double, bottom: Double) : Shape(true, top, bottom)

    private val shapes = ArrayList<Shape>()

    private fun segment(a: EmblemPoint, b: EmblemPoint, width: Double, white: Boolean) {
        shapes += Segment(px(a.x, a.y), py(a.x, a.y), px(b.x, b.y), py(b.x, b.y), width * unit / 2, white)
    }

    private fun disc(x: Double, y: Double, r: Double, white: Boolean) {
        if (r > 0) shapes += Disc(px(x, y), py(x, y), r * unit, 0.0, white)
    }

    fun draw(g: EmblemGeometry, rendering: EmblemRendering, weight: Double, par: Parallel): ByteArray {
        val w = weight.coerceIn(0.0, 1.0)
        val thick = 0.4 + 1.6 * w // scales the motif's own widths
        val line = 0.006 + 0.03 * w // uniform line width, in units

        fun strokeSegments(pts: List<EmblemPoint>, extra: Double, white: Boolean) {
            for (i in 1 until pts.size) {
                val width = (pts[i - 1].w + pts[i].w) * 0.5 * thick + extra
                if (width <= 0.0005) continue
                segment(pts[i - 1], pts[i], width, white)
            }
        }

        when (rendering) {
            EmblemRendering.LINE -> {
                // Even lines, but fine parts (twigs, thin rings) get thinner lines.
                for (pts in g.strokes) {
                    if (pts.size <= 1) continue
                    for (i in 1 until pts.size) {
                        val sw = (pts[i - 1].w + pts[i].w) * 0.5
                        segment(pts[i - 1], pts[i], line * max(0.35, min(1.0, sw / 0.03)), true)
                    }
                }
                for (d in g.dots) shapes += Disc(px(d.x, d.y), py(d.x, d.y), d.r * unit, line * unit / 2, true)
            }
            EmblemRendering.FILLED -> {
                for (group in g.fills) {
                    val polys = group.filter { it.size > 2 }.map { poly ->
                        DoubleArray(poly.size * 2).also { a ->
                            for ((k, p) in poly.withIndex()) { a[2 * k] = px(p.x, p.y); a[2 * k + 1] = py(p.x, p.y) }
                        }
                    }
                    if (polys.isEmpty()) continue
                    var top = Double.MAX_VALUE; var bottom = -Double.MAX_VALUE
                    for (a in polys) for (k in 1 until a.size step 2) { top = min(top, a[k]); bottom = max(bottom, a[k]) }
                    shapes += Polygons(polys, top - 1, bottom + 1)
                }
                for (pts in g.strokes) if (pts.size > 1) strokeSegments(pts, 0.0, true)
                for (d in g.dots) disc(d.x, d.y, d.r * sqrt(thick), true)
            }
            EmblemRendering.DOUBLE_LINE -> {
                // Outline of the filled shape: draw it widened, then cut out the narrowed shape.
                for (pts in g.strokes) if (pts.size > 1) strokeSegments(pts, line, true)
                for (d in g.dots) disc(d.x, d.y, d.r * sqrt(thick) + line / 2, true)
                for (pts in g.strokes) if (pts.size > 1) strokeSegments(pts, -line, false)
                for (d in g.dots) disc(d.x, d.y, d.r * sqrt(thick) - line / 2, false)
            }
            EmblemRendering.BEADS -> {
                for (pts in g.strokes) {
                    if (pts.size <= 1) continue
                    for (b in beads(pts, thick, line * 0.8)) disc(b.x, b.y, b.r, true)
                }
                for (d in g.dots) disc(d.x, d.y, d.r * sqrt(thick), true)
            }
        }
        return rasterise(par)
    }

    /** Dots spaced along a stroke by arc length, sized by the stroke width there. */
    private fun beads(raw: List<EmblemPoint>, thick: Double, minRadius: Double): List<EmblemDot> {
        // Smooth the widths first so neighbouring beads grow and shrink gently.
        val pts = raw.indices.map { i ->
            val lo = max(0, i - 4); val hi = min(raw.size - 1, i + 4)
            var sum = 0.0
            for (k in lo..hi) sum += raw[k].w
            raw[i].copy(w = sum / (hi - lo + 1))
        }
        val out = ArrayList<EmblemDot>()
        fun radius(w: Double) = max(minRadius, w * thick * 0.42)
        var next = 0.0 // arc length where the next bead goes
        var walked = 0.0
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            val len = hypot(b.x - a.x, b.y - a.y)
            while (next <= walked + len) {
                val t = if (len > 0) (next - walked) / len else 0.0
                val r = radius(a.w + (b.w - a.w) * t)
                out += EmblemDot(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, r)
                next += max(r * 2.6, 0.004)
            }
            walked += len
        }
        return out
    }

    /**
     * Paints all shapes in order. Rows are split into bands; each band paints every shape clipped
     * to its rows, so the result does not depend on the thread count.
     */
    private fun rasterise(par: Parallel): ByteArray {
        val mask = ByteArray(width * height)
        val bands = max(1, min(height, par.threads * 4))
        val bandHeight = (height + bands - 1) / bands
        par.forEachChunk(bands) { band ->
            val y0 = band * bandHeight
            val y1 = min(height, y0 + bandHeight)
            if (y0 >= y1) return@forEachChunk
            val acc = FloatArray(width)
            for (shape in shapes) {
                val from = max(y0, floor(shape.top).toInt())
                val until = min(y1, ceil(shape.bottom).toInt() + 1)
                if (from >= until) continue
                when (shape) {
                    is Segment -> paintSegment(mask, shape, from, until)
                    is Disc -> paintDisc(mask, shape, from, until)
                    is Polygons -> paintPolygons(mask, shape, from, until, acc)
                }
            }
        }
        return mask
    }

    /** Source-over of white (or black) at coverage `cov` onto the 8-bit mask, rounded like an 8-bit context. */
    private fun put(mask: ByteArray, i: Int, cov: Double, white: Boolean) {
        if (cov <= 0) return
        val m = mask[i].toInt() and 0xFF
        val v = if (white) m + cov * (255 - m) else m * (1 - cov)
        mask[i] = (v + 0.5).toInt().coerceIn(0, 255).toByte()
    }

    /** Area of a 1 px wide box filter over a band of half-width r at distance d from its middle. */
    private fun bandCoverage(d: Double, r: Double): Double =
        (min(d + 0.5, r) - max(d - 0.5, -r)).coerceIn(0.0, 1.0)

    private fun paintSegment(mask: ByteArray, s: Segment, from: Int, until: Int) {
        val dx = s.bx - s.ax; val dy = s.by - s.ay
        val len2 = dx * dx + dy * dy
        val reach = s.r + 1
        val left = max(0, floor(min(s.ax, s.bx) - reach).toInt())
        val right = min(width, ceil(max(s.ax, s.bx) + reach).toInt() + 1)
        val len = sqrt(len2)
        for (y in from until until) {
            val cy = y + 0.5
            var x0 = left; var x1 = right
            // Pixels within reach of the segment lie within reach of its line: narrow the row to that band.
            if (abs(dy) > 1e-9 * len) {
                val xl = s.ax + (cy - s.ay) * dx / dy
                val hw = reach * len / abs(dy)
                x0 = max(x0, floor(xl - hw).toInt())
                x1 = min(x1, ceil(xl + hw).toInt() + 1)
            }
            for (x in x0 until x1) {
                val cx = x + 0.5
                val t = if (len2 > 0) (((cx - s.ax) * dx + (cy - s.ay) * dy) / len2).coerceIn(0.0, 1.0) else 0.0
                val ex = cx - (s.ax + t * dx); val ey = cy - (s.ay + t * dy)
                put(mask, y * width + x, bandCoverage(sqrt(ex * ex + ey * ey), s.r), s.white)
            }
        }
    }

    private fun paintDisc(mask: ByteArray, d: Disc, from: Int, until: Int) {
        val outer = d.r + d.ring + 1
        val x0 = max(0, floor(d.cx - outer).toInt())
        val x1 = min(width, ceil(d.cx + outer).toInt() + 1)
        for (y in from until until) {
            val ey = y + 0.5 - d.cy
            for (x in x0 until x1) {
                val ex = x + 0.5 - d.cx
                val dist = sqrt(ex * ex + ey * ey)
                val cov = if (d.ring > 0) bandCoverage(abs(dist - d.r), d.ring) else (d.r + 0.5 - dist).coerceIn(0.0, min(1.0, 2 * d.r))
                put(mask, y * width + x, cov, d.white)
            }
        }
    }

    /**
     * Even-odd fill with antialiasing: 16 sub-rows per pixel row, each with exact horizontal
     * coverage of its spans, summed into `acc`.
     */
    private fun paintPolygons(mask: ByteArray, s: Polygons, from: Int, until: Int, acc: FloatArray) {
        val sub = 16
        val crossings = DoubleArray(s.polys.sumOf { it.size / 2 })
        for (y in from until until) {
            acc.fill(0f)
            var minX = width; var maxX = -1
            for (k in 0 until sub) {
                val sy = y + (k + 0.5) / sub
                var count = 0
                for (poly in s.polys) {
                    val n = poly.size / 2
                    for (i in 0 until n) {
                        val j = if (i == n - 1) 0 else i + 1
                        val ay = poly[2 * i + 1]; val by = poly[2 * j + 1]
                        if ((ay <= sy) == (by <= sy)) continue
                        val ax = poly[2 * i]; val bx = poly[2 * j]
                        crossings[count++] = ax + (sy - ay) * (bx - ax) / (by - ay)
                    }
                }
                if (count < 2) continue
                crossings.sort(0, count)
                var i = 0
                while (i + 1 < count) {
                    val xa = crossings[i].coerceIn(0.0, width.toDouble())
                    val xb = crossings[i + 1].coerceIn(0.0, width.toDouble())
                    i += 2
                    if (xb <= xa) continue
                    val pa = floor(xa).toInt(); val pb = min(width - 1, floor(xb).toInt())
                    minX = min(minX, pa); maxX = max(maxX, pb)
                    val w = 1f / sub
                    if (pa == pb) {
                        acc[pa] += ((xb - xa) * w).toFloat()
                    } else {
                        acc[pa] += ((pa + 1 - xa) * w).toFloat()
                        for (x in pa + 1 until pb) acc[x] += w
                        acc[pb] += ((xb - pb) * w).toFloat()
                    }
                }
            }
            for (x in minX..maxX) put(mask, y * width + x, min(1f, acc[x]).toDouble(), true)
        }
    }
}
