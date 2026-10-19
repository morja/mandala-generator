package com.mathis.mandala.core

/** Which primaries encoded components refer to. Both use the sRGB transfer curve. */
enum class Encoding { SRGB, DISPLAY_P3 }

/**
 * Interleaved RGB float image for work inside one filter step (P3 working values, unclamped).
 * Used at the 1600 px reference size of the blur effects and for the painted effect.
 */
internal class FloatImage(val width: Int, val height: Int, val data: FloatArray = FloatArray(width * height * 3)) {
    fun copy(): FloatImage = FloatImage(width, height, data.copyOf())
}
