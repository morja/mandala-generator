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
 * (glowing lines repeated around the centre) and the graffiti layer (soft spray paint).
 */
internal class HandLayers(private val par: Parallel, private val fx: LayerEffects) {

    // ── Drawing layer: the same additive lines as the styles, then glow and blending ──

    fun drawing(base: Image8, s: DrawingLayerSettings): Image8 {
        val size = base.width
        val buffer = PixelBuffer(size, size)
        val c = size * 0.5f
        val sym = max(1, s.symmetry)
        val thickness = max(1, (s.strokeWeight.toFloat() * size * 0.015f + 1.5f).toInt())
        for (stroke in s.strokes) {
            if (stroke.xs.size < 2 || stroke.xs.size != stroke.ys.size) continue
            val col = hsbToRgb(stroke.hue, stroke.saturation, stroke.brightness)
            val r = col.r.toFloat(); val g = col.g.toFloat(); val b = col.b.toFloat()
            for (k in 0 until sym) {
                val angle = k * PI.toFloat() * 2f / sym
                val ca = cos(angle); val sa = sin(angle)
                for (i in 1 until stroke.xs.size) {
                    val dx0 = stroke.xs[i - 1].toFloat() * size - c; val dy0 = stroke.ys[i - 1].toFloat() * size - c
                    val dx1 = stroke.xs[i].toFloat() * size - c; val dy1 = stroke.ys[i].toFloat() * size - c
                    buffer.addThickLine(
                        c + dx0 * ca - dy0 * sa, c + dx0 * sa + dy0 * ca,
                        c + dx1 * ca - dy1 * sa, c + dx1 * sa + dy1 * ca,
                        r, g, b, 1.5f, thickness,
                    )
                }
            }
        }
        var img = buffer.toImage()
        img = fx.glow(img, s.glowIntensity, size)
        if (s.opacity < 0.999) img = fx.opacity(img, s.opacity)
        return fx.composite(base, img, blendOp(s.blendMode, normal = BlendOp.LIGHTEN)!!)
    }

    // ── Graffiti layer: round-capped strokes painted over each other, blurred, then blended ──
    // The macOS app strokes paths with Core Graphics into a transparent canvas; each path is one
    // coverage shape, so a stroke does not darken where it crosses itself.

    fun graffiti(base: Image8, s: GraffitiLayerSettings): Image8 {
        val size = base.width
        val colour = FloatImage(size, size) // premultiplied P3 working values
        val alpha = FloatArray(size * size)
        val sym = max(1, s.symmetry)
        val c = size * 0.5
        val mask = Coverage()
        for (stroke in s.strokes) {
            if (stroke.xs.isEmpty() || stroke.xs.size != stroke.ys.size) continue
            val halfWidth = max(0.005, stroke.brushSize) * size * 0.5
            val a = (stroke.opacity * s.opacity).toFloat().coerceIn(0f, 1f)
            val p3 = toWorking(hsbToRgb(stroke.hue, stroke.saturation, stroke.brightness))
            for (k in 0 until sym) {
                if (stroke.xs.size == 1) {
                    // The macOS app fills single dots at the unrotated spot for every copy.
                    mask.dot(stroke.xs[0] * size, stroke.ys[0] * size, halfWidth, size)
                } else {
                    val angle = k * PI * 2 / sym
                    val ca = cos(angle); val sa = sin(angle)
                    val xs = DoubleArray(stroke.xs.size) { val dx = stroke.xs[it] * size - c; val dy = stroke.ys[it] * size - c; c + dx * ca - dy * sa }
                    val ys = DoubleArray(stroke.xs.size) { val dx = stroke.xs[it] * size - c; val dy = stroke.ys[it] * size - c; c + dx * sa + dy * ca }
                    mask.path(xs, ys, halfWidth, size)
                }
                mask.paint(colour, alpha, p3, a, size)
            }
        }
        val sigma = s.softness * size * 0.012
        if (sigma > 0.5) {
            Filters.blurInPlace(colour, sigma, par)
            MaskBlur.blur(alpha, size, sigma, par)
        }
        val op = blendOp(s.blendMode, normal = null)
        val out = Image8(size, size, Encoding.SRGB)
        val n = size * 3
        par.forRange(size) { from, until ->
            val row = FloatArray(n)
            for (y in from until until) {
                WorkingSpace.readRow(base, y, row)
                for (x in 0 until size) {
                    val p = y * size + x
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

    /** Antialiased coverage of one path, kept in its bounding box. */
    private class Coverage {
        private var data = FloatArray(0)
        private var x0 = 0; private var y0 = 0; private var w = 0; private var h = 0

        private fun reset(minX: Double, minY: Double, maxX: Double, maxY: Double, size: Int): Boolean {
            x0 = max(0, floor(minX).toInt()); y0 = max(0, floor(minY).toInt())
            val x1 = min(size, ceil(maxX).toInt() + 1); val y1 = min(size, ceil(maxY).toInt() + 1)
            w = max(0, x1 - x0); h = max(0, y1 - y0)
            if (w * h > data.size) data = FloatArray(w * h) else data.fill(0f, 0, w * h)
            return w > 0 && h > 0
        }

        fun dot(cx: Double, cy: Double, r: Double, size: Int) {
            path(doubleArrayOf(cx, cx), doubleArrayOf(cy, cy), r, size)
        }

        fun path(xs: DoubleArray, ys: DoubleArray, r: Double, size: Int) {
            if (!reset(xs.min() - r - 1, ys.min() - r - 1, xs.max() + r + 1, ys.max() + r + 1, size)) return
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

        /** Source-over of colour `p3` at opacity `a` through the coverage. */
        fun paint(colour: FloatImage, alpha: FloatArray, p3: FloatArray, a: Float, size: Int) {
            for (y in 0 until h) for (x in 0 until w) {
                val cov = data[y * w + x] * a
                if (cov <= 0f) continue
                val p = (y + y0) * size + (x + x0)
                val keep = 1f - cov
                alpha[p] = cov + alpha[p] * keep
                for (ch in 0..2) colour.data[p * 3 + ch] = p3[ch] * cov + colour.data[p * 3 + ch] * keep
            }
        }
    }
}
