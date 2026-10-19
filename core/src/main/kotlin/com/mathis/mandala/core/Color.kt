package com.mathis.mandala.core

import kotlin.math.abs
import kotlin.math.pow

/** Colour components in [0, 1]. Which space they are in is stated where an Rgb is used. */
data class Rgb(val r: Double, val g: Double, val b: Double) {
    companion object {
        /** Converts sRGB components to Display P3, as NSColor.usingColorSpace(.displayP3) does. */
        fun fromSrgb(r: Double, g: Double, b: Double): Rgb {
            val out = DoubleArray(3)
            ColorSpaces.srgbToP3(r, g, b, out)
            return Rgb(out[0], out[1], out[2])
        }
    }
}

/**
 * sRGB and Display P3 share the same transfer curve and white point and differ only in primaries.
 * The macOS renderer draws palette colours as P3 values and Core Image hands every intermediate
 * image back as sRGB, so both directions are needed.
 */
object ColorSpaces {
    // Linear-light primaries conversion matrices (D65).
    private val SRGB_TO_P3 = doubleArrayOf(
        0.8224621, 0.1775380, 0.0000000,
        0.0331941, 0.9668058, 0.0000000,
        0.0170827, 0.0723974, 0.9105199,
    )
    private val P3_TO_SRGB = doubleArrayOf(
        1.2249401, -0.2249404, 0.0000000,
        -0.0420569, 1.0420571, 0.0000000,
        -0.0196376, -0.0786361, 1.0982735,
    )

    private const val LUT_SIZE = 16384
    private val decodeLut = FloatArray(LUT_SIZE + 1) { decode(it.toDouble() / LUT_SIZE).toFloat() }
    private val encodeLut = FloatArray(LUT_SIZE + 1) { encode(it.toDouble() / LUT_SIZE).toFloat() }

    // Above 1 the curve continues as a straight line with the slope at 1. Core Image's exact
    // handling of over-range values is unknown; measured additions fit this better than the pure curve.
    private const val SLOPE_AT_ONE = 2.4 / 1.055

    /** Shared sRGB / P3 transfer curve: encoded value to linear light (sign-preserving). */
    fun decode(v: Double): Double {
        if (v > 1.0) return 1.0 + (v - 1.0) * SLOPE_AT_ONE
        val a = abs(v)
        val lin = if (a <= 0.04045) a / 12.92 else ((a + 0.055) / 1.055).pow(2.4)
        return if (v < 0) -lin else lin
    }

    /** Linear light to encoded value (sign-preserving). */
    fun encode(v: Double): Double {
        if (v > 1.0) return 1.0 + (v - 1.0) / SLOPE_AT_ONE
        val a = abs(v)
        val enc = if (a <= 0.0031308) a * 12.92 else 1.055 * a.pow(1.0 / 2.4) - 0.055
        return if (v < 0) -enc else enc
    }

    fun decodeFast(v: Float): Float =
        if (v < 0f || v > 1f) decode(v.toDouble()).toFloat() else lookup(decodeLut, v)

    fun encodeFast(v: Float): Float =
        if (v < 0f || v > 1f) encode(v.toDouble()).toFloat() else lookup(encodeLut, v)

    private fun lookup(lut: FloatArray, v: Float): Float {
        val pos = v * LUT_SIZE
        val i = pos.toInt().coerceAtMost(LUT_SIZE - 1)
        val f = pos - i
        return lut[i] + (lut[i + 1] - lut[i]) * f
    }

    fun srgbToP3(r: Double, g: Double, b: Double, out: DoubleArray) = convert(SRGB_TO_P3, r, g, b, out)

    fun p3ToSrgb(r: Double, g: Double, b: Double, out: DoubleArray) = convert(P3_TO_SRGB, r, g, b, out)

    private fun convert(m: DoubleArray, r: Double, g: Double, b: Double, out: DoubleArray) {
        val lr = decode(r)
        val lg = decode(g)
        val lb = decode(b)
        out[0] = encode(m[0] * lr + m[1] * lg + m[2] * lb)
        out[1] = encode(m[3] * lr + m[4] * lg + m[5] * lb)
        out[2] = encode(m[6] * lr + m[7] * lg + m[8] * lb)
    }

    /** In-place conversion of interleaved RGB floats; `toP3` selects the direction. */
    fun convertInPlace(data: FloatArray, toP3: Boolean) {
        val m = if (toP3) SRGB_TO_P3 else P3_TO_SRGB
        val m0 = m[0].toFloat(); val m1 = m[1].toFloat(); val m2 = m[2].toFloat()
        val m3 = m[3].toFloat(); val m4 = m[4].toFloat(); val m5 = m[5].toFloat()
        val m6 = m[6].toFloat(); val m7 = m[7].toFloat(); val m8 = m[8].toFloat()
        var i = 0
        while (i < data.size) {
            val lr = decodeFast(data[i])
            val lg = decodeFast(data[i + 1])
            val lb = decodeFast(data[i + 2])
            data[i] = encodeFast(m0 * lr + m1 * lg + m2 * lb)
            data[i + 1] = encodeFast(m3 * lr + m4 * lg + m5 * lb)
            data[i + 2] = encodeFast(m6 * lr + m7 * lg + m8 * lb)
            i += 3
        }
    }
}

/** HSB to RGB, a direct port of the Swift renderer's hsbToRGB. */
fun hsbToRgb(h: Double, s: Double, b: Double): Rgb {
    if (s <= 0) return Rgb(b, b, b)
    val h6 = h * 6.0
    val i = h6.toInt() % 6
    val f = h6 - h6.toInt()
    val p = b * (1 - s)
    val q = b * (1 - s * f)
    val t = b * (1 - s * (1 - f))
    return when (i) {
        0 -> Rgb(b, t, p)
        1 -> Rgb(q, b, p)
        2 -> Rgb(p, b, t)
        3 -> Rgb(p, q, b)
        4 -> Rgb(t, p, b)
        else -> Rgb(b, p, q)
    }
}
