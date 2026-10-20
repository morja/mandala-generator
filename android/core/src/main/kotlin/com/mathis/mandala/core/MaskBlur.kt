package com.mathis.mandala.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Approximate Gaussian blur (three box passes) of a single-channel mask, in place, zero outside. */
internal object MaskBlur {
    fun blur(mask: FloatArray, size: Int, sigma: Double, par: Parallel) = blur(mask, size, size, sigma, par)

    fun blur(mask: FloatArray, width: Int, height: Int, sigma: Double, par: Parallel) {
        if (sigma < 0.5) return
        val radii = boxRadii(sigma)
        par.forRange(height) { from, until ->
            val a = FloatArray(width)
            val b = FloatArray(width)
            for (y in from until until) {
                System.arraycopy(mask, y * width, a, 0, width)
                for (r in radii) { box(a, b, width, r); System.arraycopy(b, 0, a, 0, width) }
                System.arraycopy(a, 0, mask, y * width, width)
            }
        }
        par.forRange(width) { from, until ->
            val a = FloatArray(height)
            val b = FloatArray(height)
            for (x in from until until) {
                for (y in 0 until height) a[y] = mask[y * width + x]
                for (r in radii) { box(a, b, height, r); System.arraycopy(b, 0, a, 0, height) }
                for (y in 0 until height) mask[y * width + x] = a[y]
            }
        }
    }

    /** How many pixels a blur of this sigma reads on each side. */
    fun reach(sigma: Double): Int = if (sigma < 0.5) 0 else boxRadii(sigma).sum()

    private fun boxRadii(sigma: Double): IntArray {
        val wIdeal = sqrt(12 * sigma * sigma / 3 + 1)
        var wl = floor(wIdeal).toInt()
        if (wl % 2 == 0) wl--
        val m = ((12 * sigma * sigma - 3 * wl * wl - 12 * wl - 9) / (-4.0 * wl - 4)).let { kotlin.math.round(it).toInt() }
        return IntArray(3) { if (it < m) max(0, (wl - 1) / 2) else (wl + 1) / 2 }
    }

    private fun box(src: FloatArray, dst: FloatArray, n: Int, r: Int) {
        if (r <= 0) { System.arraycopy(src, 0, dst, 0, n); return }
        val norm = 1f / (2 * r + 1)
        var sum = 0f
        for (i in 0..min(r, n - 1)) sum += src[i]
        for (x in 0 until n) {
            dst[x] = sum * norm
            val add = x + r + 1
            val remove = x - r
            if (add < n) sum += src[add]
            if (remove >= 0) sum -= src[remove]
        }
    }
}
