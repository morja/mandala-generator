package com.mathis.mandala.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Background types of the macOS base layer that are computed per pixel. Like there, the colours
 * go through the same tone map as the mandala lines (x / (x + 0.55)), so they come out darker
 * than the picked colour, and the result is tagged Display P3.
 */
internal object Background {

    fun supports(type: BaseLayerType) = type == BaseLayerType.GRADIENT || type == BaseLayerType.SUNBURST

    fun render(settings: BaseLayerSettings, size: Int, par: Parallel): Image8 {
        val c1 = hsbToRgb(settings.hue, settings.saturation, settings.brightness)
        val c2 = hsbToRgb(settings.hue2, settings.saturation2, settings.brightness2)
        val out = Image8(size, size, Encoding.DISPLAY_P3)
        val opacity = settings.opacity.toFloat()
        val fill: (Int, Int, FloatArray) -> Unit = when (settings.type) {
            BaseLayerType.SUNBURST -> sunburst(settings, c1, c2, size)
            else -> if (settings.isRadial) radialGradient(c1, c2, size) else linearGradient(settings, c1, c2, size)
        }
        par.forRange(size) { from, until ->
            val c = FloatArray(3)
            for (y in from until until) {
                for (x in 0 until size) {
                    fill(x, y, c)
                    val o = (y * size + x) * 3
                    for (k in 0..2) {
                        val v = c[k] * opacity
                        out.data[o + k] = WorkingSpace.quantize(v / (v + 0.55f))
                    }
                }
            }
        }
        return out
    }

    // ── Gradients (CIRadialGradient / CISmoothLinearGradient in the macOS app) ──
    // Core Image blends the two colours in linear light and hands back 8-bit sRGB values,
    // which the macOS renderer then copies into its buffer as they are.

    private fun radialGradient(c1: Rgb, c2: Rgb, size: Int): (Int, Int, FloatArray) -> Unit {
        val centre = size / 2.0
        val r1 = size * 0.72
        return { x, y, out ->
            val dx = x + 0.5 - centre
            val dy = y + 0.5 - centre
            mixLinear(c1, c2, min(1.0, sqrt(dx * dx + dy * dy) / r1), out)
        }
    }

    private fun linearGradient(settings: BaseLayerSettings, c1: Rgb, c2: Rgb, size: Int): (Int, Int, FloatArray) -> Unit {
        val angle = settings.gradientAngle * Math.PI * 2
        // Core Image's y axis points up; the image rows run down.
        val dx = cos(angle) * size * 0.5
        val dy = sin(angle) * size * 0.5
        val len2 = 4 * (dx * dx + dy * dy)
        val centre = size / 2.0
        return { x, y, out ->
            val px = x + 0.5 - (centre - dx)
            val py = (size - (y + 0.5)) - (centre - dy)
            val t = ((px * 2 * dx + py * 2 * dy) / len2).coerceIn(0.0, 1.0)
            mixLinear(c1, c2, t * t * (3 - 2 * t), out)
        }
    }

    private fun mixLinear(a: Rgb, b: Rgb, t: Double, out: FloatArray) {
        out[0] = mixChannel(a.r, b.r, t)
        out[1] = mixChannel(a.g, b.g, t)
        out[2] = mixChannel(a.b, b.b, t)
    }

    private fun mixChannel(a: Double, b: Double, t: Double): Float {
        val lin = ColorSpaces.decode(a) + (ColorSpaces.decode(b) - ColorSpaces.decode(a)) * t
        val enc = ColorSpaces.encode(lin)
        return (min(255.0, max(0.0, enc * 255 + 0.5)).toInt() / 255.0).toFloat()
    }

    // ── Sunburst (computed per pixel in the macOS app too) ──

    private fun sunburst(settings: BaseLayerSettings, c1: Rgb, c2: Rgb, size: Int): (Int, Int, FloatArray) -> Unit {
        val cx = size / 2f
        val cy = size / 2f
        val numRays = (settings.patternScale * 14.0 + 3.0).toFloat()
        val edgeWidth = max(0.015f, 0.48f * (1.0f - settings.patternSharpness.toFloat() * 0.93f))
        val invHalfW = 1.0f / (size * 0.5f)
        val a1 = floatArrayOf(c1.r.toFloat(), c1.g.toFloat(), c1.b.toFloat())
        val a2 = floatArrayOf(c2.r.toFloat(), c2.g.toFloat(), c2.b.toFloat())
        return { x, y, out ->
            val dx = x - cx
            val dy = y - cy
            val angle = atan2(dy, dx)
            val dist = sqrt(dx * dx + dy * dy) * invHalfW
            val raw = (sin(angle * numRays) + 1.0f) * 0.5f
            var t = ((raw - (0.5f - edgeWidth)) / (2.0f * edgeWidth)).coerceIn(0f, 1f)
            val radial = max(0.0f, 1.0f - dist * dist * 0.45f)
            t = t * radial + 0.5f * (1.0f - radial)
            for (k in 0..2) out[k] = a1[k] * (1.0f - t) + a2[k] * t
        }
    }
}
