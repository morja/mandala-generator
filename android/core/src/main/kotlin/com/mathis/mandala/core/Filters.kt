package com.mathis.mandala.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Anything that can hand out rows of P3 working values. */
internal interface RowSource {
    val width: Int
    val height: Int
    fun readRow(y: Int, out: FloatArray)
}

internal fun Image8.asRowSource(): RowSource = object : RowSource {
    override val width get() = this@asRowSource.width
    override val height get() = this@asRowSource.height
    override fun readRow(y: Int, out: FloatArray) = WorkingSpace.readRow(this@asRowSource, y, out)
}

internal fun FloatImage.asRowSource(): RowSource = object : RowSource {
    override val width get() = this@asRowSource.width
    override val height get() = this@asRowSource.height
    override fun readRow(y: Int, out: FloatArray) = System.arraycopy(data, y * width * 3, out, 0, width * 3)
}

/**
 * CPU replacements for the Core Image filters of the macOS renderer, written to keep memory low:
 * blurs work in place, resampling streams row by row, noise is sampled on demand.
 * Measured Core Image behaviour: CIGaussianBlur radius = sigma, transparent (zero) outside the image.
 */
internal object Filters {

    // ── Blur (in place) ──────────────────────────────────────────────────

    fun blurInPlace(img: FloatImage, sigma: Double, par: Parallel) {
        if (sigma < 0.05) return
        val w = img.width
        val h = img.height
        val d = img.data
        // Rows
        par.forRange(h) { from, until ->
            val line = Line(w, sigma)
            for (y in from until until) {
                System.arraycopy(d, y * w * 3, line.src, 0, w * 3)
                line.blur()
                System.arraycopy(line.dst, 0, d, y * w * 3, w * 3)
            }
        }
        // Columns, in blocks so reads stay cache friendly
        val block = 16
        val blocks = (w + block - 1) / block
        par.forRange(blocks) { from, until ->
            val line = Line(h, sigma)
            for (b in from until until) {
                val x0 = b * block
                val x1 = min(w, x0 + block)
                for (x in x0 until x1) {
                    for (y in 0 until h) {
                        val i = (y * w + x) * 3
                        line.src[y * 3] = d[i]; line.src[y * 3 + 1] = d[i + 1]; line.src[y * 3 + 2] = d[i + 2]
                    }
                    line.blur()
                    for (y in 0 until h) {
                        val i = (y * w + x) * 3
                        d[i] = line.dst[y * 3]; d[i + 1] = line.dst[y * 3 + 1]; d[i + 2] = line.dst[y * 3 + 2]
                    }
                }
            }
        }
    }

    /** How many pixels [blurInPlace] reads on each side for this sigma. */
    fun blurReach(sigma: Double): Int = when {
        sigma < 0.05 -> 0
        sigma <= 6.0 -> ceil(sigma * 3.0).toInt().coerceAtLeast(1)
        else -> boxRadiiForGauss(sigma).sum()
    }

    /** One line of interleaved RGB and its blur, Gaussian up to sigma 6, three box passes above. */
    private class Line(val n: Int, sigma: Double) {
        val src = FloatArray(n * 3)
        val dst = FloatArray(n * 3)
        private val kernel = if (sigma <= 6.0) gaussianKernel(sigma) else null
        private val radii = if (kernel == null) boxRadiiForGauss(sigma) else null
        private val prefix = if (kernel == null) DoubleArray(n + 1) else null

        fun blur() {
            if (kernel != null) {
                convolve(src, dst, n, kernel)
            } else {
                val r = radii!!
                boxPass(src, dst, n, r[0], prefix!!)
                boxPass(dst, src, n, r[1], prefix)
                boxPass(src, dst, n, r[2], prefix)
            }
        }
    }

    private fun gaussianKernel(sigma: Double): FloatArray {
        val r = ceil(sigma * 3.0).toInt().coerceAtLeast(1)
        val k = FloatArray(2 * r + 1)
        var sum = 0.0
        for (i in -r..r) {
            val w = exp(-(i * i) / (2 * sigma * sigma))
            k[i + r] = w.toFloat()
            sum += w
        }
        for (i in k.indices) k[i] = (k[i] / sum).toFloat()
        return k
    }

    private fun convolve(src: FloatArray, dst: FloatArray, n: Int, k: FloatArray) {
        val r = k.size / 2
        for (x in 0 until n) {
            var sr = 0f; var sg = 0f; var sb = 0f
            val lo = max(0, x - r)
            val hi = min(n - 1, x + r)
            for (xx in lo..hi) {
                val wgt = k[xx - x + r]
                val i = xx * 3
                sr += src[i] * wgt; sg += src[i + 1] * wgt; sb += src[i + 2] * wgt
            }
            val o = x * 3
            dst[o] = sr; dst[o + 1] = sg; dst[o + 2] = sb
        }
    }

    /** Three box widths approximating a Gaussian (Kovesi), as radii. */
    private fun boxRadiiForGauss(sigma: Double): IntArray {
        val n = 3
        val wIdeal = sqrt(12 * sigma * sigma / n + 1)
        var wl = floor(wIdeal).toInt()
        if (wl % 2 == 0) wl--
        val wu = wl + 2
        val mIdeal = (12 * sigma * sigma - n * wl * wl - 4 * n * wl - 3 * n) / (-4.0 * wl - 4)
        val m = mIdeal.roundToInt()
        return IntArray(n) { if (it < m) (wl - 1) / 2 else (wu - 1) / 2 }
    }

    /** Box blur of one interleaved RGB line, zero outside. */
    private fun boxPass(src: FloatArray, dst: FloatArray, n: Int, r: Int, prefix: DoubleArray) {
        val norm = 1.0 / (2 * r + 1)
        for (c in 0 until 3) {
            prefix[0] = 0.0
            for (x in 0 until n) prefix[x + 1] = prefix[x] + src[x * 3 + c]
            for (x in 0 until n) {
                val lo = max(0, x - r)
                val hi = min(n, x + r + 1)
                dst[x * 3 + c] = ((prefix[hi] - prefix[lo]) * norm).toFloat()
            }
        }
    }

    // ── Resampling (Lanczos-3, edge clamped, streaming) ──────────────────

    private class Weights(srcLen: Int, dstLen: Int) {
        val taps: Int
        val starts = IntArray(dstLen)
        val w: FloatArray

        init {
            val scale = dstLen.toDouble() / srcLen
            val filterScale = max(1.0, 1.0 / scale)
            val support = 3.0 * filterScale
            taps = ceil(support).toInt() * 2 + 1
            w = FloatArray(dstLen * taps)
            for (x in 0 until dstLen) {
                val center = (x + 0.5) / scale - 0.5
                val start = ceil(center - support).toInt()
                starts[x] = start
                var sum = 0.0
                for (t in 0 until taps) {
                    val v = lanczos3((start + t - center) / filterScale)
                    w[x * taps + t] = v.toFloat()
                    sum += v
                }
                if (sum != 0.0) for (t in 0 until taps) w[x * taps + t] = (w[x * taps + t] / sum).toFloat()
            }
        }
    }

    private fun lanczos3(x: Double): Double {
        val ax = abs(x)
        if (ax < 1e-9) return 1.0
        if (ax >= 3.0) return 0.0
        val px = PI * x
        return 3.0 * sin(px) * sin(px / 3.0) / (px * px)
    }

    /**
     * Resamples `src` to dstW x dstH and hands each output row to `sink(y, row)`, in parallel
     * over rows. Only a few source rows per thread are held at a time. `row` is reused after
     * the call returns.
     */
    fun resampleRows(src: RowSource, dstW: Int, dstH: Int, par: Parallel, sink: (Int, FloatArray) -> Unit) {
        val sw = src.width
        val sh = src.height
        val sameW = dstW == sw
        val sameH = dstH == sh
        val wx = if (sameW) null else Weights(sw, dstW)
        val wy = if (sameH) null else Weights(sh, dstH)
        par.forRange(dstH) { from, until ->
            val cache = HashMap<Int, FloatArray>()
            val vertical = FloatArray(sw * 3)
            val out = FloatArray(dstW * 3)
            fun row(y: Int): FloatArray = cache.getOrPut(y) { FloatArray(sw * 3).also { src.readRow(y, it) } }
            for (y in from until until) {
                if (wy == null) {
                    System.arraycopy(row(y), 0, vertical, 0, sw * 3)
                    cache.remove(y)
                } else {
                    vertical.fill(0f)
                    val start = wy.starts[y]
                    cache.keys.removeAll { it < start.coerceIn(0, sh - 1) }
                    for (t in 0 until wy.taps) {
                        val wgt = wy.w[y * wy.taps + t]
                        if (wgt == 0f) continue
                        val r = row((start + t).coerceIn(0, sh - 1))
                        for (k in 0 until sw * 3) vertical[k] += r[k] * wgt
                    }
                }
                if (wx == null) {
                    System.arraycopy(vertical, 0, out, 0, sw * 3)
                } else {
                    for (x in 0 until dstW) {
                        var sr = 0f; var sg = 0f; var sb = 0f
                        val start = wx.starts[x]
                        for (t in 0 until wx.taps) {
                            val wgt = wx.w[x * wx.taps + t]
                            if (wgt == 0f) continue
                            val i = (start + t).coerceIn(0, sw - 1) * 3
                            sr += vertical[i] * wgt; sg += vertical[i + 1] * wgt; sb += vertical[i + 2] * wgt
                        }
                        out[x * 3] = sr; out[x * 3 + 1] = sg; out[x * 3 + 2] = sb
                    }
                }
                sink(y, out)
            }
        }
    }

    fun resampleToFloat(src: RowSource, dstW: Int, dstH: Int, par: Parallel): FloatImage {
        val out = FloatImage(dstW, dstH)
        resampleRows(src, dstW, dstH, par) { y, row -> System.arraycopy(row, 0, out.data, y * dstW * 3, dstW * 3) }
        return out
    }

    // ── Per-pixel operations on rows ─────────────────────────────────────

    /** CIColorControls: saturation around Rec. 709 luma, then contrast around 0.5, then additive brightness (measured order). */
    fun colorControls(d: FloatArray, from: Int, until: Int, saturation: Double, brightness: Double, contrast: Double) {
        val s = saturation.toFloat()
        val br = brightness.toFloat()
        val c = contrast.toFloat()
        var i = from
        while (i < until) {
            val lum = 0.2125f * d[i] + 0.7154f * d[i + 1] + 0.0721f * d[i + 2]
            for (k in 0 until 3) {
                val v = lum + (d[i + k] - lum) * s
                d[i + k] = (v - 0.5f) * c + 0.5f + br
            }
            i += 3
        }
    }

    /** Blends `over` into `base` (same length) in place. */
    fun blend(base: FloatArray, over: FloatArray, n: Int, op: BlendOp) {
        when (op) {
            BlendOp.SCREEN -> for (i in 0 until n) base[i] = base[i] + over[i] - base[i] * over[i]
            BlendOp.ADD -> for (i in 0 until n) base[i] += over[i]
            BlendOp.LIGHTEN -> for (i in 0 until n) base[i] = max(base[i], over[i])
            BlendOp.MULTIPLY -> for (i in 0 until n) base[i] *= over[i]
        }
    }

    /** Bilinear sample of `src` in working values at (sx, sy), zero outside. */
    fun sampleBilinear(src: Image8, sx: Float, sy: Float, dst: FloatArray, o: Int, tmp: FloatArray) {
        val x0 = floor(sx).toInt()
        val y0 = floor(sy).toInt()
        val fx = sx - x0
        val fy = sy - y0
        var r = 0f; var g = 0f; var b = 0f
        for (j in 0..1) {
            val yy = y0 + j
            if (yy < 0 || yy >= src.height) continue
            val wy = if (j == 0) 1 - fy else fy
            for (i in 0..1) {
                val xx = x0 + i
                if (xx < 0 || xx >= src.width) continue
                val wgt = wy * (if (i == 0) 1 - fx else fx)
                if (wgt == 0f) continue
                WorkingSpace.readPixel(src, (yy * src.width + xx) * 3, tmp, 0)
                r += tmp[0] * wgt; g += tmp[1] * wgt; b += tmp[2] * wgt
            }
        }
        dst[o] = r; dst[o + 1] = g; dst[o + 2] = b
    }

    fun cropCenter(src: Image8, size: Int): Image8 {
        val ox = (src.width - size) / 2
        val oy = (src.height - size) / 2
        val out = Image8(size, size, src.encoding)
        for (y in 0 until size) {
            System.arraycopy(src.data, ((y + oy) * src.width + ox) * 3, out.data, y * size * 3, size * 3)
        }
        return out
    }

    fun hash01(x: Int, y: Int, salt: Int): Double {
        var h = x * 374761393 + y * 668265263 + salt * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) / 2147483647.0
    }
}

internal enum class BlendOp { SCREEN, ADD, LIGHTEN, MULTIPLY }
