package com.mathis.mandala.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Approximate Gaussian blur (three box passes) of a single-channel size x size mask, in place. */
internal object MaskBlur {
    fun blur(mask: FloatArray, size: Int, sigma: Double, par: Parallel) {
        if (sigma < 0.5) return
        val radii = boxRadii(sigma)
        par.forRange(size) { from, until ->
            val a = FloatArray(size)
            val b = FloatArray(size)
            for (y in from until until) {
                System.arraycopy(mask, y * size, a, 0, size)
                for (r in radii) { box(a, b, size, r); System.arraycopy(b, 0, a, 0, size) }
                System.arraycopy(a, 0, mask, y * size, size)
            }
        }
        par.forRange(size) { from, until ->
            val a = FloatArray(size)
            val b = FloatArray(size)
            for (x in from until until) {
                for (y in 0 until size) a[y] = mask[y * size + x]
                for (r in radii) { box(a, b, size, r); System.arraycopy(b, 0, a, 0, size) }
                for (y in 0 until size) mask[y * size + x] = a[y]
            }
        }
    }

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
