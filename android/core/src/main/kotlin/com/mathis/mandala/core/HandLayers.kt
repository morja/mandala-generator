package com.mathis.mandala.core

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The two hand-made layers of the macOS app, drawn over the generated layers: the drawing layer
 * (glowing lines repeated around the centre) and the graffiti layer (soft spray paint). Strokes are
 * stored in the mandala's square (0..1), so they move and scale with the framing (ADR-0010).
 */
internal class HandLayers(private val par: Parallel, private val fx: LayerEffects) {

    // ── Drawing layer: the same additive lines as the styles, then glow and blending ──

    /** `still`: kept parts of an animation's frames; the drawn lines are kept there. */
    fun drawing(base: Image8, s: DrawingLayerSettings, place: Placement, still: MutableMap<String, Any>? = null): Image8 {
        val drawn = if (still != null) still.getOrPut("drawing") { drawn(s, place) ?: Unit } as? Drawn else drawn(s, place)
        drawn ?: return base
        return fx.composite(base, drawn.image, blendOp(s.blendMode, normal = BlendOp.LIGHTEN)!!, drawn.x0, drawn.y0)
    }

    private class Drawn(val image: Image8, val x0: Int, val y0: Int)

    /** The drawing layer's lines with glow and opacity, on the part of the canvas they reach. */
    private fun drawn(s: DrawingLayerSettings, place: Placement): Drawn? {
        val size = place.size
        val half = size * 0.5f
        val sym = max(1, s.symmetry)
        val thickness = max(1, (s.strokeWeight.toFloat() * size * 0.015f + 1.5f).toInt())
        // Only the canvas part the strokes (the square turned about its centre) and their glow reach.
        val area = place.around(size * 0.75 + thickness + 3 * 60.0 * s.glowIntensity * size / 1600 + 4)
        val w = area[2] - area[0]
        val h = area[3] - area[1]
        if (w <= 0 || h <= 0) return null
        val cx = (place.centreX - area[0]).toFloat()
        val cy = (place.centreY - area[1]).toFloat()
        var img = PixelBuffer.drawInStrips(w, h) { buffer ->
            for (stroke in s.strokes) {
                if (stroke.xs.size < 2 || stroke.xs.size != stroke.ys.size) continue
                val col = hsbToRgb(stroke.hue, stroke.saturation, stroke.brightness)
                val r = col.r.toFloat(); val g = col.g.toFloat(); val b = col.b.toFloat()
                for (k in 0 until sym) {
                    val angle = k * PI.toFloat() * 2f / sym
                    val ca = cos(angle); val sa = sin(angle)
                    for (i in 1 until stroke.xs.size) {
                        val dx0 = stroke.xs[i - 1].toFloat() * size - half; val dy0 = stroke.ys[i - 1].toFloat() * size - half
                        val dx1 = stroke.xs[i].toFloat() * size - half; val dy1 = stroke.ys[i].toFloat() * size - half
                        buffer.addThickLine(
                            cx + dx0 * ca - dy0 * sa, cy + dx0 * sa + dy0 * ca,
                            cx + dx1 * ca - dy1 * sa, cy + dx1 * sa + dy1 * ca,
                            r, g, b, 1.5f, thickness,
                        )
                    }
                }
            }
        }
        img = fx.glow(img, s.glowIntensity, size)
        if (s.opacity < 0.999) img = fx.opacity(img, s.opacity)
        return Drawn(img, area[0], area[1])
    }

    // ── Graffiti layer: round-capped strokes painted over each other, blurred, then blended ──
    // The macOS app strokes paths with Core Graphics into a transparent canvas; each path is one
    // coverage shape, so a stroke does not darken where it crosses itself.

    fun graffiti(base: Image8, s: GraffitiLayerSettings, place: Placement): Image8 {
        val width = base.width
        val height = base.height
        val size = place.size
        val sym = max(1, s.symmetry)
        val half = size * 0.5
        val cx = place.centreX
        val cy = place.centreY
        val sigma = s.softness * size * 0.012
        val blurred = sigma > 0.5
        // Strips of rows; each carries an overlap as wide as the blur reads, so its core rows
        // get the same paint and nearly the same blur as the whole canvas at once.
        val overlap = if (blurred) max(Filters.blurReach(sigma), MaskBlur.reach(sigma)) else 0
        val core = max(PixelBuffer.STRIP_ROWS, 2 * overlap)
        val op = blendOp(s.blendMode, normal = null)
        val out = if (base.encoding == Encoding.SRGB) base else Image8(width, height, Encoding.SRGB, base.data)
        val n = width * 3
        val mask = Coverage()
        for (y0 in 0 until height step core) {
            val y1 = min(height, y0 + core)
            val top = max(0, y0 - overlap)
            val bottom = min(height, y1 + overlap)
            val rows = bottom - top
            val colour = FloatImage(width, rows) // premultiplied P3 working values
            val alpha = FloatArray(width * rows)
            for (stroke in s.strokes) {
                if (stroke.xs.isEmpty() || stroke.xs.size != stroke.ys.size) continue
                val halfWidth = max(0.005, stroke.brushSize) * size * 0.5
                val a = (stroke.opacity * s.opacity).toFloat().coerceIn(0f, 1f)
                val p3 = toWorking(hsbToRgb(stroke.hue, stroke.saturation, stroke.brightness))
                for (k in 0 until sym) {
                    if (stroke.xs.size == 1) {
                        // The macOS app fills single dots at the unrotated spot for every copy.
                        mask.dot(stroke.xs[0] * size + (cx - half), stroke.ys[0] * size + (cy - half), halfWidth, width, top, bottom)
                    } else {
                        val angle = k * PI * 2 / sym
                        val ca = cos(angle); val sa = sin(angle)
                        val xs = DoubleArray(stroke.xs.size) { val dx = stroke.xs[it] * size - half; val dy = stroke.ys[it] * size - half; cx + dx * ca - dy * sa }
                        val ys = DoubleArray(stroke.xs.size) { val dx = stroke.xs[it] * size - half; val dy = stroke.ys[it] * size - half; cy + dx * sa + dy * ca }
                        mask.path(xs, ys, halfWidth, width, top, bottom)
                    }
                    mask.paint(colour, alpha, p3, a, width, top)
                }
            }
            if (blurred) {
                Filters.blurInPlace(colour, sigma, par)
                MaskBlur.blur(alpha, width, rows, sigma, par)
            }
            par.forRange(y1 - y0) { from, until ->
                val row = FloatArray(n)
                for (y in y0 + from until y0 + until) {
                    WorkingSpace.readRow(base, y, row)
                    val r = y - top
                    for (x in 0 until width) {
                        val p = r * width + x
                        val sa = alpha[p]
                        if (sa <= 0f) continue
                        for (ch in 0..2) {
                            val d = row[x * 3 + ch]
                            val sc = colour.data[p * 3 + ch]
                            // Blend formulas for a premultiplied overlay on an opaque base.
                            row[x * 3 + ch] = when (op) {
                                null -> sc + d * (1 - sa)
                                BlendOp.SCREEN -> d + sc - d * sc
                                BlendOp.ADD -> d + sc
                                BlendOp.MULTIPLY -> d * (1 - sa + sc)
                                BlendOp.LIGHTEN -> max(d, sc + d * (1 - sa))
                            }
                        }
                    }
                    WorkingSpace.writeRow(row, out, y)
                }
            }
        }
        return out
    }

    private fun toWorking(c: Rgb): FloatArray {
        val probe = Image8(1, 1, Encoding.SRGB)
        probe.data[0] = WorkingSpace.quantize(c.r.toFloat())
        probe.data[1] = WorkingSpace.quantize(c.g.toFloat())
        probe.data[2] = WorkingSpace.quantize(c.b.toFloat())
        return FloatArray(3).also { WorkingSpace.readPixel(probe, 0, it, 0) }
    }

    private fun blendOp(mode: LayerBlendMode, normal: BlendOp?): BlendOp? = when (mode) {
        LayerBlendMode.SCREEN -> BlendOp.SCREEN
        LayerBlendMode.ADD -> BlendOp.ADD
        LayerBlendMode.NORMAL -> normal
        LayerBlendMode.MULTIPLY -> BlendOp.MULTIPLY
    }

    /** Antialiased coverage of one path, kept in its bounding box and clipped to rows [rowFrom, rowUntil). */
    private class Coverage {
        private var data = FloatArray(0)
        private var x0 = 0; private var y0 = 0; private var w = 0; private var h = 0

        private fun reset(minX: Double, minY: Double, maxX: Double, maxY: Double, size: Int, rowFrom: Int, rowUntil: Int): Boolean {
            x0 = max(0, floor(minX).toInt()); y0 = max(rowFrom, floor(minY).toInt())
            val x1 = min(size, ceil(maxX).toInt() + 1); val y1 = min(rowUntil, ceil(maxY).toInt() + 1)
            w = max(0, x1 - x0); h = max(0, y1 - y0)
            if (w * h > data.size) data = FloatArray(w * h) else data.fill(0f, 0, w * h)
            return w > 0 && h > 0
        }

        fun dot(cx: Double, cy: Double, r: Double, size: Int, rowFrom: Int, rowUntil: Int) {
            path(doubleArrayOf(cx, cx), doubleArrayOf(cy, cy), r, size, rowFrom, rowUntil)
        }

        fun path(xs: DoubleArray, ys: DoubleArray, r: Double, size: Int, rowFrom: Int, rowUntil: Int) {
            if (!reset(xs.min() - r - 1, ys.min() - r - 1, xs.max() + r + 1, ys.max() + r + 1, size, rowFrom, rowUntil)) return
            for (i in 1 until xs.size) segment(xs[i - 1], ys[i - 1], xs[i], ys[i], r)
        }

        private fun segment(ax: Double, ay: Double, bx: Double, by: Double, r: Double) {
            val sx0 = max(x0, floor(min(ax, bx) - r - 1).toInt()); val sx1 = min(x0 + w, ceil(max(ax, bx) + r + 1).toInt())
            val sy0 = max(y0, floor(min(ay, by) - r - 1).toInt()); val sy1 = min(y0 + h, ceil(max(ay, by) + r + 1).toInt())
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            for (y in sy0 until sy1) {
                val py = y + 0.5
                for (x in sx0 until sx1) {
                    val px = x + 0.5
                    val t = if (len2 > 0) (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0) else 0.0
                    val ex = px - (ax + t * dx); val ey = py - (ay + t * dy)
                    val cov = (r + 0.5 - sqrt(ex * ex + ey * ey)).coerceIn(0.0, 1.0).toFloat()
                    val i = (y - y0) * w + (x - x0)
                    if (cov > data[i]) data[i] = cov
                }
            }
        }

        /** Source-over of colour `p3` at opacity `a` through the coverage, into a strip starting at row `top`. */
        fun paint(colour: FloatImage, alpha: FloatArray, p3: FloatArray, a: Float, size: Int, top: Int) {
            for (y in 0 until h) for (x in 0 until w) {
                val cov = data[y * w + x] * a
                if (cov <= 0f) continue
                val p = (y + y0 - top) * size + (x + x0)
                val keep = 1f - cov
                alpha[p] = cov + alpha[p] * keep
                for (ch in 0..2) colour.data[p * 3 + ch] = p3[ch] * cov + colour.data[p * 3 + ch] * keep
            }
        }
    }
}
