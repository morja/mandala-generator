package com.mathis.mandala.core

import kotlin.math.roundToInt

/**
 * A last adjustment of a finished image (ADR-0014): brightness, contrast and saturation, each 1 for
 * "unchanged". Belongs to one saved image or wallpaper, not to the mandala, so it is not in the
 * settings JSON. Works on the 8-bit sRGB result: saturation first (towards the pixel's luma), then
 * contrast around mid grey, then brightness as a factor.
 */
data class Finish(val brightness: Double = 1.0, val contrast: Double = 1.0, val saturation: Double = 1.0) {
    val isIdentity: Boolean get() = brightness == 1.0 && contrast == 1.0 && saturation == 1.0

    fun clamped() = Finish(
        brightness.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS),
        contrast.coerceIn(MIN_CONTRAST, MAX_CONTRAST),
        saturation.coerceIn(0.0, MAX_SATURATION),
    )

    /** The image with this finish, as a new sRGB image; the identity returns the image itself. */
    fun apply(image: Image8, threads: Int = 1): Image8 {
        if (isIdentity) return image
        val src = if (image.encoding == Encoding.SRGB) image else WorkingSpace.convertToSrgb(image)
        val f = clamped()
        val sat = f.saturation.toFloat()
        val con = f.contrast.toFloat()
        val bri = f.brightness.toFloat()
        val out = Image8(src.width, src.height, Encoding.SRGB)
        Parallel(maxOf(1, threads)).use { par ->
            par.forRange(src.height) { from, until ->
                for (i in from * src.width * 3 until until * src.width * 3 step 3) {
                    val r = src.get(i) / 255f
                    val g = src.get(i + 1) / 255f
                    val b = src.get(i + 2) / 255f
                    val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                    out.data[i] = channel(r, luma, sat, con, bri)
                    out.data[i + 1] = channel(g, luma, sat, con, bri)
                    out.data[i + 2] = channel(b, luma, sat, con, bri)
                }
            }
        }
        return out
    }

    private fun channel(v: Float, luma: Float, sat: Float, con: Float, bri: Float): Byte {
        val s = luma + (v - luma) * sat
        val c = 0.5f + (s - 0.5f) * con
        return (c * bri * 255f).roundToInt().coerceIn(0, 255).toByte()
    }

    companion object {
        const val MIN_BRIGHTNESS = 0.1
        const val MAX_BRIGHTNESS = 1.5
        const val MIN_CONTRAST = 0.5
        const val MAX_CONTRAST = 1.5
        const val MAX_SATURATION = 2.0
    }
}
