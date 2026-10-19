package com.mathis.mandala.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Float RGB buffer with additive blending (the light-painting look). Values grow beyond 1
 * and are tone-mapped when converted to an image. Port of the macOS PixelBuffer.
 */
class PixelBuffer(val width: Int, val height: Int) {
    val data = FloatArray(width * height * 3)

    fun scale(factor: Float) {
        if (factor == 1f) return
        for (i in data.indices) data[i] *= factor
    }

    fun mergeAdding(other: PixelBuffer) {
        if (other.data.size != data.size) return
        val src = other.data
        for (i in data.indices) data[i] += src[i]
    }

    fun addPixel(x: Int, y: Int, r: Float, g: Float, b: Float, weight: Float) {
        if (x < 0 || y < 0 || x >= width || y >= height) return
        val base = (y * width + x) * 3
        data[base] += r * weight
        data[base + 1] += g * weight
        data[base + 2] += b * weight
    }

    /** Wu anti-aliased line with additive blending. Rejects non-finite and far out-of-range input. */
    fun addLine(x0In: Float, y0In: Float, x1In: Float, y1In: Float, r: Float, g: Float, b: Float, weight: Float) {
        if (!x0In.isFinite() || !y0In.isFinite() || !x1In.isFinite() || !y1In.isFinite()) return
        val limit = (max(width, height) * 4).toFloat()
        if (x0In <= -limit || x0In >= limit || y0In <= -limit || y0In >= limit ||
            x1In <= -limit || x1In >= limit || y1In <= -limit || y1In >= limit
        ) return

        var x0 = x0In; var y0 = y0In; var x1 = x1In; var y1 = y1In
        val steep = abs(y1 - y0) > abs(x1 - x0)
        if (steep) {
            var t = x0; x0 = y0; y0 = t
            t = x1; x1 = y1; y1 = t
        }
        if (x0 > x1) {
            var t = x0; x0 = x1; x1 = t
            t = y0; y0 = y1; y1 = t
        }

        val dx = x1 - x0
        val dy = y1 - y0
        val gradient = if (dx == 0f) 1f else dy / dx

        // First endpoint
        val xend0 = toInt(x0 + 0.5f).toFloat()
        val yend0 = y0 + gradient * (xend0 - x0)
        val xgap0 = 1f - frac(x0 + 0.5f)
        val xpxl1 = toInt(xend0)
        val ypxl1 = toInt(yend0)
        val yf1 = frac(yend0)
        if (steep) {
            addPixel(ypxl1, xpxl1, r, g, b, weight * (1 - yf1) * xgap0)
            addPixel(ypxl1 + 1, xpxl1, r, g, b, weight * yf1 * xgap0)
        } else {
            addPixel(xpxl1, ypxl1, r, g, b, weight * (1 - yf1) * xgap0)
            addPixel(xpxl1, ypxl1 + 1, r, g, b, weight * yf1 * xgap0)
        }
        var intery = yend0 + gradient

        // Second endpoint
        val xend1 = toInt(x1 + 0.5f).toFloat()
        val yend1 = y1 + gradient * (xend1 - x1)
        val xgap1 = frac(x1 + 0.5f)
        val xpxl2 = toInt(xend1)
        val ypxl2 = toInt(yend1)
        val yf2 = frac(yend1)
        if (steep) {
            addPixel(ypxl2, xpxl2, r, g, b, weight * (1 - yf2) * xgap1)
            addPixel(ypxl2 + 1, xpxl2, r, g, b, weight * yf2 * xgap1)
        } else {
            addPixel(xpxl2, ypxl2, r, g, b, weight * (1 - yf2) * xgap1)
            addPixel(xpxl2, ypxl2 + 1, r, g, b, weight * yf2 * xgap1)
        }

        // Middle pixels
        val loX = xpxl1 + 1
        val hiX = xpxl2
        if (loX >= hiX) return
        if (steep) {
            for (x in loX until hiX) {
                val iy = toInt(intery)
                val f = frac(intery)
                addPixel(iy, x, r, g, b, weight * (1 - f))
                addPixel(iy + 1, x, r, g, b, weight * f)
                intery += gradient
            }
        } else {
            for (x in loX until hiX) {
                val iy = toInt(intery)
                val f = frac(intery)
                addPixel(x, iy, r, g, b, weight * (1 - f))
                addPixel(x, iy + 1, r, g, b, weight * f)
                intery += gradient
            }
        }
    }

    fun addThickLine(
        x0: Float, y0: Float, x1: Float, y1: Float,
        r: Float, g: Float, b: Float, weight: Float, thickness: Int,
    ) {
        addLine(x0, y0, x1, y1, r, g, b, weight)
        if (thickness <= 1) return
        val dx = x1 - x0
        val dy = y1 - y0
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 0f) return
        val nx = -dy / len
        val ny = dx / len
        val half = thickness * 0.5f
        for (t in 1 until thickness) {
            val off = (t - half) * 0.6f
            val w = weight * (1f - t.toFloat() / thickness * 0.5f)
            addLine(x0 + nx * off, y0 + ny * off, x1 + nx * off, y1 + ny * off, r, g, b, w)
        }
    }

    /** Filmic tone map x / (x + 0.55) to 8-bit values. The macOS renderer tags them as Display P3. */
    fun toImage(): FloatImage {
        val out = FloatImage(width, height, encoding = Encoding.DISPLAY_P3)
        val dst = out.data
        for (i in data.indices) {
            val v = data[i]
            dst[i] = quantize(v / (v + 0.55f))
        }
        return out
    }

    private fun quantize(v: Float): Float = min(255, max(0, (v * 255f + 0.5f).toInt())) / 255f

    // Float to Int clamped so it never overflows; truncates toward zero like Swift's Int(Float).
    private fun toInt(x: Float): Int = x.coerceIn(Int.MIN_VALUE / 2f, Int.MAX_VALUE / 2f).toInt()

    private fun frac(x: Float): Float = x - floor(x)
}
