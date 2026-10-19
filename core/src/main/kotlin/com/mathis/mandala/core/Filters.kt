package com.mathis.mandala.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CPU replacements for the Core Image filters the macOS renderer uses. They work on unclamped
 * values; the caller clamps and quantises once per former Core Image step.
 * Measured Core Image behaviour: CIGaussianBlur radius = sigma, transparent (zero) outside the image.
 */
internal object Filters {

    // ── Blur ─────────────────────────────────────────────────────────────

    fun gaussianBlur(src: FloatImage, sigma: Double, par: Parallel): FloatImage {
        if (sigma < 0.05) return src.copy()
        val h = blurRows(src, sigma, par)
        val t = transpose(h)
        val v = blurRows(t, sigma, par)
        return transpose(v)
    }

    private fun blurRows(src: FloatImage, sigma: Double, par: Parallel): FloatImage {
        val out = FloatImage(src.width, src.height, encoding = src.encoding)
        if (sigma <= 6.0) {
            val kernel = gaussianKernel(sigma)
            par.forRange(src.height) { from, until ->
                for (y in from until until) convolveRow(src.data, out.data, y, src.width, kernel)
            }
        } else {
            val radii = boxRadiiForGauss(sigma)
            par.forRange(src.height) { from, until ->
                val a = FloatArray(src.width * 3)
                val b = FloatArray(src.width * 3)
                val prefix = DoubleArray(src.width + 1)
                for (y in from until until) {
                    System.arraycopy(src.data, y * src.width * 3, a, 0, a.size)
                    boxRow(a, b, src.width, radii[0], prefix)
                    boxRow(b, a, src.width, radii[1], prefix)
                    boxRow(a, b, src.width, radii[2], prefix)
                    System.arraycopy(b, 0, out.data, y * src.width * 3, b.size)
                }
            }
        }
        return out
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

    private fun convolveRow(src: FloatArray, dst: FloatArray, y: Int, w: Int, k: FloatArray) {
        val r = k.size / 2
        val row = y * w * 3
        for (x in 0 until w) {
            var sr = 0f; var sg = 0f; var sb = 0f
            val lo = max(0, x - r)
            val hi = min(w - 1, x + r)
            for (xx in lo..hi) {
                val wgt = k[xx - x + r]
                val i = row + xx * 3
                sr += src[i] * wgt; sg += src[i + 1] * wgt; sb += src[i + 2] * wgt
            }
            val o = row + x * 3
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

    /** Box blur of one interleaved RGB row, zero outside the row. */
    private fun boxRow(src: FloatArray, dst: FloatArray, w: Int, r: Int, prefix: DoubleArray) {
        val norm = 1.0 / (2 * r + 1)
        for (c in 0 until 3) {
            prefix[0] = 0.0
            for (x in 0 until w) prefix[x + 1] = prefix[x] + src[x * 3 + c]
            for (x in 0 until w) {
                val lo = max(0, x - r)
                val hi = min(w, x + r + 1)
                dst[x * 3 + c] = ((prefix[hi] - prefix[lo]) * norm).toFloat()
            }
        }
    }

    private fun transpose(src: FloatImage): FloatImage {
        val w = src.width
        val h = src.height
        val out = FloatImage(h, w, encoding = src.encoding)
        val s = src.data
        val d = out.data
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 3
                val o = (x * h + y) * 3
                d[o] = s[i]; d[o + 1] = s[i + 1]; d[o + 2] = s[i + 2]
            }
        }
        return out
    }

    // ── Resampling (Lanczos-3, edge clamped) ────────────────────────────

    fun resample(src: FloatImage, newW: Int, newH: Int, par: Parallel): FloatImage {
        if (newW == src.width && newH == src.height) return src.copy()
        val h = resampleRows(src, newW, par)
        val t = transpose(h)
        val v = resampleRows(t, newH, par)
        return transpose(v)
    }

    private fun resampleRows(src: FloatImage, newW: Int, par: Parallel): FloatImage {
        val w = src.width
        val out = FloatImage(newW, src.height, encoding = src.encoding)
        val scale = newW.toDouble() / w
        val filterScale = max(1.0, 1.0 / scale)
        val support = 3.0 * filterScale
        val taps = ceil(support).toInt() * 2 + 1
        val starts = IntArray(newW)
        val weights = FloatArray(newW * taps)
        for (x in 0 until newW) {
            val center = (x + 0.5) / scale - 0.5
            val start = ceil(center - support).toInt()
            starts[x] = start
            var sum = 0.0
            for (t in 0 until taps) {
                val wgt = lanczos3((start + t - center) / filterScale)
                weights[x * taps + t] = wgt.toFloat()
                sum += wgt
            }
            if (sum != 0.0) for (t in 0 until taps) weights[x * taps + t] = (weights[x * taps + t] / sum).toFloat()
        }
        par.forRange(src.height) { from, until ->
            for (y in from until until) {
                val row = y * w * 3
                val orow = y * newW * 3
                for (x in 0 until newW) {
                    var sr = 0f; var sg = 0f; var sb = 0f
                    val start = starts[x]
                    for (t in 0 until taps) {
                        val wgt = weights[x * taps + t]
                        if (wgt == 0f) continue
                        val sx = (start + t).coerceIn(0, w - 1)
                        val i = row + sx * 3
                        sr += src.data[i] * wgt; sg += src.data[i + 1] * wgt; sb += src.data[i + 2] * wgt
                    }
                    val o = orow + x * 3
                    out.data[o] = sr; out.data[o + 1] = sg; out.data[o + 2] = sb
                }
            }
        }
        return out
    }

    private fun lanczos3(x: Double): Double {
        val ax = abs(x)
        if (ax < 1e-9) return 1.0
        if (ax >= 3.0) return 0.0
        val px = PI * x
        return 3.0 * sin(px) * sin(px / 3.0) / (px * px)
    }

    // ── Per-pixel operations ─────────────────────────────────────────────

    /** In-place multiply of RGB (CIColorMatrix with a uniform diagonal). */
    fun scaleInPlace(img: FloatImage, s: Float): FloatImage {
        for (i in img.data.indices) img.data[i] *= s
        return img
    }

    /** CIColorControls: saturation around Rec. 709 luma, then additive brightness, then contrast around 0.5. */
    fun colorControlsInPlace(img: FloatImage, saturation: Double, brightness: Double, contrast: Double): FloatImage {
        val s = saturation.toFloat()
        val br = brightness.toFloat()
        val c = contrast.toFloat()
        val d = img.data
        var i = 0
        while (i < d.size) {
            val lum = 0.2125f * d[i] + 0.7154f * d[i + 1] + 0.0721f * d[i + 2]
            for (k in 0 until 3) {
                var v = lum + (d[i + k] - lum) * s
                v += br
                d[i + k] = (v - 0.5f) * c + 0.5f
            }
            i += 3
        }
        return img
    }

    fun blend(base: FloatImage, overlay: FloatImage, mode: BlendOp): FloatImage {
        val out = FloatImage(base.width, base.height, encoding = base.encoding)
        val a = base.data
        val b = overlay.data
        val d = out.data
        when (mode) {
            BlendOp.SCREEN -> for (i in d.indices) d[i] = a[i] + b[i] - a[i] * b[i]
            BlendOp.ADD -> for (i in d.indices) d[i] = a[i] + b[i]
            BlendOp.LIGHTEN -> for (i in d.indices) d[i] = max(a[i], b[i])
            BlendOp.MULTIPLY -> for (i in d.indices) d[i] = a[i] * b[i]
        }
        return out
    }

    fun addInPlace(base: FloatImage, overlay: FloatImage): FloatImage {
        for (i in base.data.indices) base.data[i] += overlay.data[i]
        return base
    }

    /** Rotates about the image centre (counter-clockwise on screen for positive angles), bilinear, zero outside. */
    fun rotate(src: FloatImage, angle: Double, par: Parallel): FloatImage {
        val w = src.width
        val h = src.height
        val out = FloatImage(w, h, encoding = src.encoding)
        val ca = cos(angle).toFloat()
        val sa = sin(angle).toFloat()
        val cx = w * 0.5f
        val cy = h * 0.5f
        par.forRange(h) { from, until ->
            for (y in from until until) {
                for (x in 0 until w) {
                    val dx = x + 0.5f - cx
                    val dy = y + 0.5f - cy
                    val sx = cx + dx * ca - dy * sa - 0.5f
                    val sy = cy + dx * sa + dy * ca - 0.5f
                    sampleBilinear(src, sx, sy, out.data, (y * w + x) * 3)
                }
            }
        }
        return out
    }

    fun cropCenter(src: FloatImage, size: Int): FloatImage {
        val ox = (src.width - size) / 2
        val oy = (src.height - size) / 2
        val out = FloatImage(size, size, encoding = src.encoding)
        for (y in 0 until size) {
            System.arraycopy(src.data, ((y + oy) * src.width + ox) * 3, out.data, y * size * 3, size * 3)
        }
        return out
    }

    /** Displaces pixels by the two noise fields (values around 0.5), like CIDisplacementDistortion. */
    fun displace(src: FloatImage, noiseX: FloatArray, noiseY: FloatArray, amount: Float, par: Parallel): FloatImage {
        val w = src.width
        val h = src.height
        val out = FloatImage(w, h, encoding = src.encoding)
        par.forRange(h) { from, until ->
            for (y in from until until) {
                for (x in 0 until w) {
                    val p = y * w + x
                    val sx = x + (noiseX[p] - 0.5f) * amount
                    val sy = y + (noiseY[p] - 0.5f) * amount
                    sampleBilinear(src, sx, sy, out.data, p * 3)
                }
            }
        }
        return out
    }

    private fun sampleBilinear(src: FloatImage, sx: Float, sy: Float, dst: FloatArray, o: Int) {
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
                val s = (yy * src.width + xx) * 3
                r += src.data[s] * wgt; g += src.data[s + 1] * wgt; b += src.data[s + 2] * wgt
            }
        }
        dst[o] = r; dst[o + 1] = g; dst[o + 2] = b
    }

    // ── Noise ────────────────────────────────────────────────────────────

    /**
     * Smooth fractal value noise in [0, 1] around 0.5. Stands in for CITurbulence, whose exact
     * pattern is Apple's own; only the character (size, strength, octaves) is reproduced.
     */
    fun turbulence(w: Int, h: Int, featureSize: Double, amount: Double, octaves: Int, salt: Int): FloatArray {
        // Features are large, so evaluate on a coarse grid and interpolate.
        val step = max(1, (featureSize / 24).toInt())
        val gw = w / step + 2
        val gh = h / step + 2
        val grid = FloatArray(gw * gh)
        for (gy in 0 until gh) {
            for (gx in 0 until gw) {
                var sum = 0.0
                var amp = 1.0
                var norm = 0.0
                var freq = 1.0 / featureSize
                for (o in 0 until octaves) {
                    sum += amp * (valueNoise(gx * step * freq, gy * step * freq, salt + o * 101) * 2 - 1)
                    norm += amp
                    amp *= 0.5
                    freq *= 2.0
                }
                grid[gy * gw + gx] = (0.5 + 0.5 * amount * sum / norm).coerceIn(0.0, 1.0).toFloat()
            }
        }
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val gyf = y.toFloat() / step
            val gy = gyf.toInt()
            val fy = gyf - gy
            for (x in 0 until w) {
                val gxf = x.toFloat() / step
                val gx = gxf.toInt()
                val fx = gxf - gx
                val a = grid[gy * gw + gx]
                val b = grid[gy * gw + gx + 1]
                val c = grid[(gy + 1) * gw + gx]
                val d = grid[(gy + 1) * gw + gx + 1]
                out[y * w + x] = (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy
            }
        }
        return out
    }

    private fun valueNoise(x: Double, y: Double, salt: Int): Double {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val fx = smooth(x - xi)
        val fy = smooth(y - yi)
        val a = hash01(xi, yi, salt)
        val b = hash01(xi + 1, yi, salt)
        val c = hash01(xi, yi + 1, salt)
        val d = hash01(xi + 1, yi + 1, salt)
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy
    }

    private fun smooth(t: Double) = t * t * (3 - 2 * t)

    fun hash01(x: Int, y: Int, salt: Int): Double {
        var h = x * 374761393 + y * 668265263 + salt * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) / 2147483647.0
    }
}

internal enum class BlendOp { SCREEN, ADD, LIGHTEN, MULTIPLY }
