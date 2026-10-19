package com.mathis.mandala.core

import kotlin.math.max
import kotlin.math.min

/** Which primaries the encoded components refer to. Both use the sRGB transfer curve. */
enum class Encoding { SRGB, DISPLAY_P3 }

/**
 * Interleaved RGB image with encoded float components. Between filter steps values are clamped
 * to [0, 1] and quantised to 8 bit, as each Core Image step on macOS produced an 8-bit image.
 */
class FloatImage(
    val width: Int,
    val height: Int,
    val data: FloatArray = FloatArray(width * height * 3),
    var encoding: Encoding = Encoding.SRGB,
) {

    fun copy(): FloatImage = FloatImage(width, height, data.copyOf(), encoding)

    /** Returns this image with Display P3 components (a copy unless it already is P3). */
    fun toP3(): FloatImage {
        if (encoding == Encoding.DISPLAY_P3) return copy()
        val out = FloatImage(width, height, data.copyOf(), Encoding.DISPLAY_P3)
        ColorSpaces.convertInPlace(out.data, toP3 = true)
        return out
    }

    /** Converts in place to sRGB components. */
    fun toSrgbInPlace(): FloatImage {
        if (encoding == Encoding.SRGB) return this
        ColorSpaces.convertInPlace(data, toP3 = false)
        encoding = Encoding.SRGB
        return this
    }

    fun clampAndQuantize(): FloatImage {
        for (i in data.indices) {
            data[i] = min(255, max(0, (data[i] * 255f + 0.5f).toInt())) / 255f
        }
        return this
    }

    /** Converts to 0xRRGGBB pixels (row-major, top row first). */
    fun toArgbPixels(): IntArray {
        val out = IntArray(width * height)
        for (p in out.indices) {
            val i = p * 3
            val r = to8(data[i]); val g = to8(data[i + 1]); val b = to8(data[i + 2])
            out[p] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    private fun to8(v: Float): Int = min(255, max(0, (v * 255f + 0.5f).toInt()))

    companion object {
        fun filled(width: Int, height: Int, r: Float, g: Float, b: Float, encoding: Encoding): FloatImage {
            val img = FloatImage(width, height, encoding = encoding)
            val d = img.data
            var i = 0
            while (i < d.size) {
                d[i] = r; d[i + 1] = g; d[i + 2] = b
                i += 3
            }
            return img
        }
    }
}
