package com.mathis.mandala.core

import com.mathis.mandala.core.styles.LineList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Port of the macOS effects layer (applyEffectsLayer). Vignette to relief form one Core Image chain
 * there, evaluated once in the Display P3 working space without clamping in between; this port keeps
 * that chain as one float image and converts to 8-bit sRGB once at the end. Stars and glitter are
 * drawn afterwards as separate light layers, as on macOS.
 *
 * Measured Core Image behaviour used here: radial gradients blend in encoded values, CIVignette and
 * CISepiaTone follow [MeasuredCurves], blend modes use the W3C formulas, CIConvolution3X3 and blurs
 * see zero outside the image, translations sample bilinearly.
 */
internal class EffectsLayer(private val par: Parallel, private val fx: LayerEffects) {

    fun apply(image: Image8, s: EffectsLayerSettings): Image8 {
        var result = if (hasChain(s)) chain(image, s) else image
        val size = result.width
        if (s.stars > 0) result = fx.composite(result, fx.glow(lightLayer(size) { stars(it, s, size) }, 0.28, size), BlendOp.SCREEN)
        if (s.glitter > 0) result = fx.composite(result, fx.glow(lightLayer(size) { glitter(it, s, size) }, 0.18, size), BlendOp.SCREEN)
        return result
    }

    private fun hasChain(s: EffectsLayerSettings) =
        s.vignette > 0 || s.dimming > 0 || s.erasure > 0 || s.highlights > 0 || s.chromatic > 0 ||
            s.wash > 0.001 || s.sepia > 0.001 || s.fade > 0.001 || s.bloom > 0.001 || s.localContrast > 0.001 ||
            s.grain > 0.001 || s.brightness != 0.5 || s.contrast != 0.5 || s.relief > 0

    // ── The Core Image chain ─────────────────────────────────────────────

    private fun chain(image: Image8, s: EffectsLayerSettings): Image8 {
        val size = image.width
        var w = FloatImage(size, image.height)
        par.forRange(image.height) { from, until ->
            val row = FloatArray(size * 3)
            for (y in from until until) {
                WorkingSpace.readRow(image, y, row)
                System.arraycopy(row, 0, w.data, y * size * 3, size * 3)
            }
        }

        if (s.vignette > 0) vignette(w, s.vignette * 2.5, 0.4 + s.vignette * 0.6)
        if (s.dimming > 0) darkSpots(w, s.dimmingSeed, (s.dimming * 10).toInt() + 3, 0.3, 0.08) { rng -> s.dimming * (rng.nextDouble() * 0.5 + 0.3) }
        if (s.erasure > 0) darkSpots(w, s.erasureSeed, (s.erasure * 6).toInt() + 1, 0.15, 0.04) { rng -> min(1.0, s.erasure * (rng.nextDouble() * 0.4 + 0.6)) }
        if (s.highlights > 0) highlights(w, s)
        if (s.chromatic > 0) chromatic(w, size * s.chromatic * 0.012)
        perPixel(w) { c ->
            if (s.wash > 0.001) {
                val t = s.wash.toFloat()
                saturate(c, max(0f, 1f - t * 0.4f))
                val k = 1f - t * 0.3f
                for (i in 0..2) c[i] = c[i] * k + t * 0.3f
            }
            if (s.sepia > 0.001) {
                val l = 0.2125f * c[0] + 0.7154f * c[1] + 0.0721f * c[2]
                val i = s.sepia.toFloat()
                c[0] += (MeasuredCurves.sepia(MeasuredCurves.sepiaR, l) - c[0]) * i
                c[1] += (MeasuredCurves.sepia(MeasuredCurves.sepiaG, l) - c[1]) * i
                c[2] += (MeasuredCurves.sepia(MeasuredCurves.sepiaB, l) - c[2]) * i
            }
            if (s.fade > 0.001) {
                val lift = (s.fade * 0.22).toFloat()
                for (i in 0..2) c[i] = c[i] * (1f - lift) + lift
            }
        }
        if (s.bloom > 0.001) bloom(w, s.bloom)
        if (s.localContrast > 0.001) localContrast(w, s.localContrast.toFloat())
        if (s.grain > 0.001) grain(w, s.grain)
        if (s.brightness != 0.5 || s.contrast != 0.5) {
            val b = ((s.brightness - 0.5) * 0.6).toFloat()
            val k = (0.5 + s.contrast * 1.5).toFloat()
            perPixel(w) { c -> for (i in 0..2) c[i] = (c[i] - 0.5f) * k + 0.5f + b }
        }
        if (s.relief > 0) w = relief(w, s.reliefAngle, s.relief)

        val out = Image8(size, image.height, Encoding.SRGB)
        par.forRange(image.height) { from, until ->
            val row = FloatArray(size * 3)
            for (y in from until until) {
                System.arraycopy(w.data, y * size * 3, row, 0, size * 3)
                WorkingSpace.writeRow(row, out, y)
            }
        }
        return out
    }

    private inline fun perPixel(w: FloatImage, crossinline f: (FloatArray) -> Unit) {
        par.forRange(w.height) { from, until ->
            val c = FloatArray(3)
            for (p in from * w.width until until * w.width) {
                val o = p * 3
                c[0] = w.data[o]; c[1] = w.data[o + 1]; c[2] = w.data[o + 2]
                f(c)
                w.data[o] = c[0]; w.data[o + 1] = c[1]; w.data[o + 2] = c[2]
            }
        }
    }

    private fun saturate(c: FloatArray, s: Float) {
        val l = 0.2125f * c[0] + 0.7154f * c[1] + 0.0721f * c[2]
        for (i in 0..2) c[i] = l + (c[i] - l) * s
    }

    /** CIVignette: factor by distance from the centre over the shorter side, to the power of the intensity. */
    private fun vignette(w: FloatImage, intensity: Double, radius: Double) {
        val size = min(w.width, w.height).toFloat()
        val cx = w.width / 2f
        val cy = w.height / 2f
        val r = radius.toFloat()
        val i = intensity
        par.forRange(w.height) { from, until ->
            for (y in from until until) {
                val dy = y + 0.5f - cy
                for (x in 0 until w.width) {
                    val dx = x + 0.5f - cx
                    val f = MeasuredCurves.vignette(sqrt(dx * dx + dy * dy) / size, r).toDouble().pow(i).toFloat()
                    val o = (y * w.width + x) * 3
                    w.data[o] *= f; w.data[o + 1] *= f; w.data[o + 2] *= f
                }
            }
        }
    }

    /**
     * Dimming and erasure: grey radial gradients (1 - dark at the centre to white at r1) multiplied in.
     * Centres are in Core Image coordinates, whose y axis points up.
     */
    private fun darkSpots(w: FloatImage, seed: ULong, count: Int, rMax: Double, rMin: Double, dark: (SeededRng) -> Double) {
        val rng = SeededRng(seed)
        val size = w.width.toDouble()
        repeat(count) {
            val cx = rng.nextDouble() * size
            val cy = rng.nextDouble() * size
            val r1 = size * (rng.nextDouble() * rMax + rMin)
            val d = dark(rng).toFloat()
            radial(w, cx, w.height - cy, r1) { t, c ->
                val g = (1f - d) + d * t
                c[0] *= g; c[1] *= g; c[2] *= g
            }
        }
    }

    private fun highlights(w: FloatImage, s: EffectsLayerSettings) {
        val rng = SeededRng(s.highlightsSeed)
        val size = w.width.toDouble()
        repeat((s.highlights * 8).toInt() + 2) {
            val cx = rng.nextDouble() * size
            val cy = rng.nextDouble() * size
            val r1 = size * (rng.nextDouble() * 0.12 + 0.02)
            val br = s.highlights * (rng.nextDouble() * 0.4 + 0.2)
            val col = Rgb.fromSrgb(br, br * 0.9, br * 0.8)
            val c0 = floatArrayOf(col.r.toFloat(), col.g.toFloat(), col.b.toFloat())
            radial(w, cx, w.height - cy, r1) { t, c ->
                for (i in 0..2) {
                    val o = c0[i] * (1f - t)
                    c[i] = c[i] + o - c[i] * o
                }
            }
        }
    }

    /** Applies `f(t, colour)` inside the circle; t runs 0 at the centre to 1 at r1 (outside the gradient is constant). */
    private inline fun radial(w: FloatImage, cx: Double, cy: Double, r1: Double, crossinline f: (Float, FloatArray) -> Unit) {
        val y0 = max(0, floor(cy - r1).toInt())
        val y1 = min(w.height, (cy + r1).toInt() + 2)
        val x0 = max(0, floor(cx - r1).toInt())
        val x1 = min(w.width, (cx + r1).toInt() + 2)
        if (y0 >= y1 || x0 >= x1) return
        par.forRange(y1 - y0) { from, until ->
            val c = FloatArray(3)
            for (yy in from until until) {
                val y = y0 + yy
                val dy = y + 0.5 - cy
                for (x in x0 until x1) {
                    val dx = x + 0.5 - cx
                    val d = sqrt(dx * dx + dy * dy)
                    if (d >= r1) continue
                    val o = (y * w.width + x) * 3
                    c[0] = w.data[o]; c[1] = w.data[o + 1]; c[2] = w.data[o + 2]
                    f((d / r1).toFloat(), c)
                    w.data[o] = c[0]; w.data[o + 1] = c[1]; w.data[o + 2] = c[2]
                }
            }
        }
    }

    /** Red moves left, blue right by `shift` pixels (bilinear, zero outside). */
    private fun chromatic(w: FloatImage, shift: Double) {
        val width = w.width
        par.forRange(w.height) { from, until ->
            val r = FloatArray(width)
            val b = FloatArray(width)
            for (y in from until until) {
                val row = y * width * 3
                for (x in 0 until width) { r[x] = w.data[row + x * 3]; b[x] = w.data[row + x * 3 + 2] }
                for (x in 0 until width) {
                    w.data[row + x * 3] = sample(r, x + shift)
                    w.data[row + x * 3 + 2] = sample(b, x - shift)
                }
            }
        }
    }

    private fun sample(line: FloatArray, pos: Double): Float {
        val i = floor(pos).toInt()
        val f = (pos - i).toFloat()
        val a = if (i in line.indices) line[i] else 0f
        val b = if (i + 1 in line.indices) line[i + 1] else 0f
        return a + (b - a) * f
    }

    private fun bloom(w: FloatImage, bloom: Double) {
        val passes = listOf(LayerEffects.BlurPass(40.0, 0.55 * bloom), LayerEffects.BlurPass(144.0, 0.30 * bloom))
        val overlay = fx.referenceBlurOverlay(w.asRowSource(), w.width, passes) ?: return
        val n = w.width * 3
        Filters.resampleRows(overlay.asRowSource(), w.width, w.height, par) { y, over ->
            val o = y * n
            for (k in 0 until n) {
                val a = w.data[o + k]
                w.data[o + k] = a + over[k] - a * over[k]
            }
        }
    }

    /** Clarity: high pass against a 96 px blur, softened by `amount`, soft-light blended over the image. */
    private fun localContrast(w: FloatImage, amount: Float) {
        val blurred = w.copy()
        Filters.blurInPlace(blurred, 96.0, par)
        val bias = 0.5f * (1f - amount)
        val d = w.data
        val bl = blurred.data
        par.forRange(w.height) { from, until ->
            for (k in from * w.width * 3 until until * w.width * 3) {
                // The high pass is the sum of two opaque images, so its alpha is 2 (measured): the colour
                // matrix scales the unpremultiplied half, adds the bias, and soft light then blends
                // with alpha 2, i.e. 2 * softLight - base.
                val top = (d[k] - bl[k] + 0.5f) / 2f * amount + bias
                d[k] = 2f * softLight(d[k], top) - d[k]
            }
        }
    }

    /** W3C soft light (matches Core Image's CISoftLightBlendMode in measurements). */
    private fun softLight(a: Float, b: Float): Float {
        if (b <= 0.5f) return a - (1f - 2f * b) * a * (1f - a)
        val dA = if (a <= 0.25f) ((16f * a - 12f) * a + 4f) * a else sqrt(max(0f, a))
        return a + (2f * b - 1f) * (dA - a)
    }

    /** Film grain. Core Image's random pattern is Apple's own; amount and offset match. */
    private fun grain(w: FloatImage, grain: Double) {
        val amount = (grain * 0.28).toFloat()
        val bias = (-grain * 0.14).toFloat()
        par.forRange(w.height) { from, until ->
            for (y in from until until) {
                for (x in 0 until w.width) {
                    val o = (y * w.width + x) * 3
                    w.data[o] += Filters.hash01(x, y, 101).toFloat() * amount + bias
                    w.data[o + 1] += Filters.hash01(x, y, 102).toFloat() * amount + bias
                    w.data[o + 2] += Filters.hash01(x, y, 103).toFloat() * amount + bias
                }
            }
        }
    }

    /** Directional emboss (3 x 3 convolution plus 0.5, zero outside) applied with hard light. */
    private fun relief(w: FloatImage, angleFraction: Double, relief: Double): FloatImage {
        val angle = angleFraction * PI * 2
        val ca = cos(angle)
        val sa = sin(angle)
        val r = relief * 2.0
        // Rows of the kernel run top to bottom on screen.
        val k = floatArrayOf(
            ((-ca - sa) * r).toFloat(), (-sa * r).toFloat(), ((ca - sa) * r).toFloat(),
            (-ca * r).toFloat(), 0f, (ca * r).toFloat(),
            ((-ca + sa) * r).toFloat(), (sa * r).toFloat(), ((ca + sa) * r).toFloat(),
        )
        val out = FloatImage(w.width, w.height)
        val width = w.width
        val height = w.height
        par.forRange(height) { from, until ->
            for (y in from until until) {
                for (x in 0 until width) {
                    // Core Image convolves premultiplied RGBA and adds the bias to alpha as well, so the
                    // emboss has alpha 0.5 inside and other values (even above 1 or below 0) at the
                    // border. Measured: alpha is used unclamped, the unpremultiplied colour is clamped
                    // to [0, 1] and taken as 0 where alpha is not positive.
                    var a = 0.5f
                    var ki = 0
                    for (ky in -1..1) for (kx in -1..1) {
                        if (y + ky in 0 until height && x + kx in 0 until width) a += k[ki]
                        ki++
                    }
                    for (c in 0..2) {
                        var sum = 0.5f
                        ki = 0
                        for (ky in -1..1) {
                            val yy = y + ky
                            for (kx in -1..1) {
                                val xx = x + kx
                                if (yy in 0 until height && xx in 0 until width) sum += k[ki] * w.data[(yy * width + xx) * 3 + c]
                                ki++
                            }
                        }
                        val base = w.data[(y * width + x) * 3 + c]
                        val top = if (a > 0f) (sum / a).coerceIn(0f, 1f) else 0f
                        val hard = if (top <= 0.5f) 2f * base * top else 1f - 2f * (1f - base) * (1f - top)
                        out.data[(y * width + x) * 3 + c] = (1f - a) * base + a * hard
                    }
                }
            }
        }
        return out
    }

    // ── Light layers drawn after the chain ───────────────────────────────

    private fun lightLayer(size: Int, draw: (LineList) -> Unit): Image8 {
        val lines = LineList()
        draw(lines)
        return PixelBuffer.drawInStrips(size, size) { lines.drawInto(it, par) }
    }

    /** Tapered diffraction spikes: four long arms and four shorter, dimmer diagonals. */
    private fun stars(lines: LineList, s: EffectsLayerSettings, size: Int) {
        val rng = SeededRng(s.starsSeed)
        val n = (s.stars * 400).toInt() + 10
        val wf = size.toFloat()
        val nSeg = 10
        fun arm(x: Float, y: Float, dx: Float, dy: Float, len: Float, b: Float, dimMul: Float) {
            for (seg in 0 until nSeg) {
                val t0 = seg.toFloat() / nSeg
                val t1 = (seg + 1).toFloat() / nSeg
                val br = b * (1.0f - t0).pow(2.2f) * dimMul
                lines.add(x + dx * len * t0, y + dy * len * t0, x + dx * len * t1, y + dy * len * t1, br, br * 0.93f, br * 0.82f, br * 0.18f)
            }
        }
        repeat(n) {
            val x = rng.nextFloat() * wf
            val y = rng.nextFloat() * wf
            val b = s.stars.toFloat() * (rng.nextFloat() * 0.5f + 0.5f) * 10.0f
            val len = rng.nextFloat() * wf * 0.022f + wf * 0.006f
            lines.add(x, y, x + 0.5f, y, b, b * 0.96f, b * 0.9f, b * 0.6f)
            arm(x, y, 1f, 0f, len, b, 1.0f)
            arm(x, y, -1f, 0f, len, b, 1.0f)
            arm(x, y, 0f, 1f, len, b, 1.0f)
            arm(x, y, 0f, -1f, len, b, 1.0f)
            val inv = (1.0 / 1.4142).toFloat()
            arm(x, y, inv, inv, len * 0.5f, b, 0.35f)
            arm(x, y, -inv, inv, len * 0.5f, b, 0.35f)
            arm(x, y, inv, -inv, len * 0.5f, b, 0.35f)
            arm(x, y, -inv, -inv, len * 0.5f, b, 0.35f)
        }
    }

    /** Dense tiny rainbow sparkles: a cross plus fainter diagonal glints. */
    private fun glitter(lines: LineList, s: EffectsLayerSettings, size: Int) {
        val rng = SeededRng(s.glitterSeed)
        val n = (s.glitter * 2500).toInt() + 80
        val wf = size.toFloat()
        repeat(n) {
            val x = rng.nextFloat() * wf
            val y = rng.nextFloat() * wf
            val hue = rng.nextFloat()
            val bright = s.glitter.toFloat() * (rng.nextFloat() * 0.55f + 0.45f) * 9.0f
            val hi = (hue * 6.0f).toInt() % 6
            val f = hue * 6.0f - (hue * 6.0f).toInt().toFloat()
            val q = 1.0f - f
            val rgb = when (hi) {
                0 -> floatArrayOf(1f, f, 0f)
                1 -> floatArrayOf(q, 1f, 0f)
                2 -> floatArrayOf(0f, 1f, f)
                3 -> floatArrayOf(0f, q, 1f)
                4 -> floatArrayOf(f, 0f, 1f)
                else -> floatArrayOf(1f, 0f, q)
            }
            val cr = rgb[0] * bright; val cg = rgb[1] * bright; val cb = rgb[2] * bright
            val len = (rng.nextFloat() * 0.006f + 0.002f) * wf
            val wt = bright * 0.12f
            lines.add(x - len, y, x + len, y, cr, cg, cb, wt)
            lines.add(x, y - len * 0.65f, x, y + len * 0.65f, cr, cg, cb, wt)
            val d = len * 0.45f
            lines.add(x - d, y - d, x + d, y + d, cr * 0.5f, cg * 0.5f, cb * 0.5f, wt * 0.6f)
            lines.add(x + d, y - d, x - d, y + d, cr * 0.5f, cg * 0.5f, cb * 0.5f, wt * 0.6f)
        }
    }
}
