package com.mathis.mandala.core

import com.mathis.mandala.core.styles.LineList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
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

    private companion object {
        /** Blur of the local contrast (clarity) step, in output pixels. */
        const val LOCAL_CONTRAST_SIGMA = 96.0
    }

    fun apply(image: Image8, s: EffectsLayerSettings): Image8 {
        var result = if (hasChain(s)) chain(image, s) else image
        val w = result.width
        val h = result.height
        val side = min(w, h)
        if (s.stars > 0) result = fx.composite(result, fx.glow(lightLayer(w, h) { stars(it, s, w, h) }, 0.28, side), BlendOp.SCREEN)
        if (s.glitter > 0) result = fx.composite(result, fx.glow(lightLayer(w, h) { glitter(it, s, w, h) }, 0.18, side), BlendOp.SCREEN)
        return result
    }

    /** How many of `count` scattered items a canvas gets: as many as a square of its shorter side, scaled by its area. */
    private fun scaledCount(count: Int, w: Int, h: Int): Int {
        val side = min(w, h).toDouble()
        return if (w == h) count else (count * (w.toDouble() * h / (side * side))).roundToInt()
    }

    private fun hasChain(s: EffectsLayerSettings) =
        s.vignette > 0 || s.dimming > 0 || s.erasure > 0 || s.highlights > 0 || s.chromatic > 0 ||
            s.wash > 0.001 || s.sepia > 0.001 || s.fade > 0.001 || s.bloom > 0.001 || s.localContrast > 0.001 ||
            s.grain > 0.001 || s.brightness != 0.5 || s.contrast != 0.5 || s.relief > 0

    // ── The Core Image chain ─────────────────────────────────────────────
    // Computed in strips of rows, so only one strip is held as floats. Everything before bloom works
    // per pixel or per row ([Pre]), so those rows can be computed wherever they are needed: once for
    // bloom's reference copy and once per strip. Local contrast blurs each strip with an overlap as
    // wide as its blur reads; relief reads one row above and below. Every pixel goes through the
    // same operations in the same order as with one image for the whole chain.

    private fun chain(image: Image8, s: EffectsLayerSettings): Image8 {
        val width = image.width
        val height = image.height
        val n = width * 3
        val pre = Pre(s, width, height)
        val source = object : RowSource {
            override val width get() = width
            override val height get() = height
            override fun readRow(y: Int, out: FloatArray) {
                WorkingSpace.readRow(image, y, out)
                pre.apply(out, y)
            }
        }
        val bloom = if (s.bloom > 0.001) {
            val passes = listOf(LayerEffects.BlurPass(40.0, 1.10 * s.bloom), LayerEffects.BlurPass(144.0, 0.60 * s.bloom))
            fx.referenceBlurOverlay(source, min(width, height), passes)
        } else {
            null
        }
        val contrastReach = if (s.localContrast > 0.001) Filters.blurReach(LOCAL_CONTRAST_SIGMA) else 0
        val reliefReach = if (s.relief > 0) 1 else 0
        val core = max(PixelBuffer.STRIP_ROWS, 2 * contrastReach)
        val adjust = s.brightness != 0.5 || s.contrast != 0.5
        // Grain cells grow with the image above 1024 px, so large exports keep the preview's grain.
        val grainSize = max(1.0, min(width, height) / 1024.0)
        val bright = ((s.brightness - 0.5) * 0.6).toFloat()
        val contrast = (0.5 + s.contrast * 1.5).toFloat()

        // Strips read the input around them, so the result goes into a separate image.
        val out = Image8(width, height, Encoding.SRGB)
        for (y0 in 0 until height step core) {
            val y1 = min(height, y0 + core)
            // Rows relief reads, and around them the rows the local contrast blur reads.
            val top = max(0, y0 - reliefReach)
            val bottom = min(height, y1 + reliefReach)
            val lo = max(0, top - contrastReach)
            val hi = min(height, bottom + contrastReach)
            var w = FloatImage(width, hi - lo)
            par.forRange(hi - lo) { from, until ->
                val row = FloatArray(n)
                for (r in from until until) {
                    source.readRow(lo + r, row)
                    System.arraycopy(row, 0, w.data, r * n, n)
                }
            }
            if (bloom != null) {
                Filters.resampleRows(bloom.asRowSource(), width, height, lo, hi, par) { y, over ->
                    val o = (y - lo) * n
                    for (k in 0 until n) {
                        val a = w.data[o + k]
                        w.data[o + k] = a + over[k] - a * over[k]
                    }
                }
            }
            // From here on only rows [top, bottom) are needed.
            w = if (contrastReach > 0) localContrast(w, top - lo, bottom - lo, s.localContrast.toFloat()) else w.rows(top - lo, bottom - lo)
            par.forRange(bottom - top) { from, until ->
                for (r in from until until) {
                    if (s.grain > 0.001) grainRow(w, r, top + r, s.grain, grainSize)
                    if (adjust) for (i in r * n until (r + 1) * n) w.data[i] = (w.data[i] - 0.5f) * contrast + 0.5f + bright
                }
            }
            par.forRange(y1 - y0) { from, until ->
                val row = FloatArray(n)
                for (y in y0 + from until y0 + until) {
                    if (s.relief > 0) reliefRow(w, y - top, top, height, s.reliefAngle, s.relief, row) else System.arraycopy(w.data, (y - top) * n, row, 0, n)
                    WorkingSpace.writeRow(row, out, y)
                }
            }
        }
        return out
    }

    private fun FloatImage.rows(from: Int, until: Int): FloatImage =
        if (from == 0 && until == height) this else FloatImage(width, until - from, data.copyOfRange(from * width * 3, until * width * 3))

    private fun saturate(c: FloatArray, s: Float) {
        val l = 0.2125f * c[0] + 0.7154f * c[1] + 0.0721f * c[2]
        for (i in 0..2) c[i] = l + (c[i] - l) * s
    }

    /** One spot of dimming, erasure or highlights: a radial gradient around (cx, cy) with radius r1. */
    private class Spot(val cx: Double, val cy: Double, val r1: Double, val dark: Float, val colour: FloatArray?)

    /**
     * Vignette, dimming, erasure, highlights, chromatic shift and the per-pixel colour steps, applied
     * to one row at a time. Each of them reads only its own pixel or its own row.
     */
    private inner class Pre(private val s: EffectsLayerSettings, private val width: Int, private val height: Int) {
        private val spots: List<Spot> = buildList {
            if (s.dimming > 0) addAll(darkSpots(s.dimmingSeed, (s.dimming * 10).toInt() + 3, 0.3, 0.08) { rng -> s.dimming * (rng.nextDouble() * 0.5 + 0.3) })
            if (s.erasure > 0) addAll(darkSpots(s.erasureSeed, (s.erasure * 6).toInt() + 1, 0.15, 0.04) { rng -> min(1.0, s.erasure * (rng.nextDouble() * 0.4 + 0.6)) })
            if (s.highlights > 0) addAll(highlights())
        }
        private val vignetteSize = min(width, height).toFloat()
        private val shift = width * s.chromatic * 0.012
        private val colour = ThreadLocal.withInitial { FloatArray(3) }
        private val channels = ThreadLocal.withInitial { Array(2) { FloatArray(width) } }

        fun apply(row: FloatArray, y: Int) {
            if (s.vignette > 0) vignette(row, y, s.vignette * 3.75, 1.0)
            for (spot in spots) radial(row, y, spot)
            if (s.chromatic > 0) chromatic(row)
            if (s.wash > 0.001 || s.sepia > 0.001 || s.fade > 0.001) colourSteps(row)
        }

        /** CIVignette: factor by distance from the centre over the shorter side, to the power of the intensity. */
        private fun vignette(row: FloatArray, y: Int, intensity: Double, radius: Double) {
            val cx = width / 2f
            val cy = height / 2f
            val r = radius.toFloat()
            val dy = y + 0.5f - cy
            for (x in 0 until width) {
                val dx = x + 0.5f - cx
                val f = MeasuredCurves.vignette(sqrt(dx * dx + dy * dy) / vignetteSize, r).toDouble().pow(intensity).toFloat()
                val o = x * 3
                row[o] *= f; row[o + 1] *= f; row[o + 2] *= f
            }
        }

        /**
         * Dimming and erasure: grey radial gradients (1 - dark at the centre to white at r1) multiplied in.
         * Centres are in Core Image coordinates, whose y axis points up.
         */
        private fun darkSpots(seed: ULong, count: Int, rMax: Double, rMin: Double, dark: (SeededRng) -> Double): List<Spot> {
            val rng = SeededRng(seed)
            val side = min(width, height).toDouble()
            return List(scaledCount(count, width, height)) {
                val cx = rng.nextDouble() * width
                val cy = rng.nextDouble() * height
                val r1 = side * (rng.nextDouble() * rMax + rMin)
                Spot(cx, height - cy, r1, dark(rng).toFloat(), null)
            }
        }

        private fun highlights(): List<Spot> {
            val rng = SeededRng(s.highlightsSeed)
            val side = min(width, height).toDouble()
            return List(scaledCount((s.highlights * 8).toInt() + 2, width, height)) {
                val cx = rng.nextDouble() * width
                val cy = rng.nextDouble() * height
                val r1 = side * (rng.nextDouble() * 0.12 + 0.02)
                val br = s.highlights * (rng.nextDouble() * 0.4 + 0.2)
                val col = Rgb.fromSrgb(br, br * 0.9, br * 0.8)
                Spot(cx, height - cy, r1, 0f, floatArrayOf(col.r.toFloat(), col.g.toFloat(), col.b.toFloat()))
            }
        }

        /** The spot's gradient inside its circle; t runs 0 at the centre to 1 at r1 (outside it changes nothing). */
        private fun radial(row: FloatArray, y: Int, spot: Spot) {
            val (cx, cy, r1) = Triple(spot.cx, spot.cy, spot.r1)
            val y0 = max(0, floor(cy - r1).toInt())
            val y1 = min(height, (cy + r1).toInt() + 2)
            val x0 = max(0, floor(cx - r1).toInt())
            val x1 = min(width, (cx + r1).toInt() + 2)
            if (y < y0 || y >= y1 || x0 >= x1) return
            val c = colour.get()
            val dy = y + 0.5 - cy
            for (x in x0 until x1) {
                val dx = x + 0.5 - cx
                val d = sqrt(dx * dx + dy * dy)
                if (d >= r1) continue
                val o = x * 3
                c[0] = row[o]; c[1] = row[o + 1]; c[2] = row[o + 2]
                val t = (d / r1).toFloat()
                val c0 = spot.colour
                if (c0 == null) {
                    val g = (1f - spot.dark) + spot.dark * t
                    c[0] *= g; c[1] *= g; c[2] *= g
                } else {
                    for (i in 0..2) {
                        val v = c0[i] * (1f - t)
                        c[i] = c[i] + v - c[i] * v
                    }
                }
                row[o] = c[0]; row[o + 1] = c[1]; row[o + 2] = c[2]
            }
        }

        /** Red moves left, blue right by `shift` pixels (bilinear, edge pixels repeated outside). */
        private fun chromatic(row: FloatArray) {
            val (r, b) = channels.get()
            for (x in 0 until width) { r[x] = row[x * 3]; b[x] = row[x * 3 + 2] }
            for (x in 0 until width) {
                row[x * 3] = sample(r, x + shift)
                row[x * 3 + 2] = sample(b, x - shift)
            }
        }

        private fun colourSteps(row: FloatArray) {
            val c = colour.get()
            for (p in 0 until width) {
                val o = p * 3
                c[0] = row[o]; c[1] = row[o + 1]; c[2] = row[o + 2]
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
                row[o] = c[0]; row[o + 1] = c[1]; row[o + 2] = c[2]
            }
        }
    }

    private fun sample(line: FloatArray, pos: Double): Float {
        val i = floor(pos).toInt()
        val f = (pos - i).toFloat()
        val a = line[i.coerceIn(0, line.size - 1)]
        val b = line[(i + 1).coerceIn(0, line.size - 1)]
        return a + (b - a) * f
    }

    /**
     * Clarity: high pass against a 96 px blur, softened by `amount`, soft-light blended over the image.
     * Blurs the whole strip `w` and returns its rows [from, until) with the effect applied.
     */
    private fun localContrast(w: FloatImage, from: Int, until: Int, amount: Float): FloatImage {
        val result = w.rows(from, until).let { if (it === w) it.copy() else it }
        Filters.blurInPlace(w, LOCAL_CONTRAST_SIGMA, par)
        val bias = 0.5f * (1f - amount)
        val d = result.data
        val bl = w.data
        val offset = from * w.width * 3
        par.forRange(result.height) { lo, hi ->
            for (k in lo * w.width * 3 until hi * w.width * 3) {
                // The high pass is the sum of two opaque images, so its alpha is 2 (measured): the colour
                // matrix scales the unpremultiplied half, adds the bias, and soft light then blends
                // with alpha 2, i.e. 2 * softLight - base.
                val top = (d[k] - bl[offset + k] + 0.5f) / 2f * amount + bias
                d[k] = 2f * softLight(d[k], top) - d[k]
            }
        }
        return result
    }

    /** W3C soft light (matches Core Image's CISoftLightBlendMode in measurements). */
    private fun softLight(a: Float, b: Float): Float {
        if (b <= 0.5f) return a - (1f - 2f * b) * a * (1f - a)
        val dA = if (a <= 0.25f) ((16f * a - 12f) * a + 4f) * a else sqrt(max(0f, a))
        return a + (2f * b - 1f) * (dA - a)
    }

    /**
     * Film grain on row `r` of `w`, which is canvas row `y`, in cells of `size` pixels (bilinear between
     * cells above 1). Core Image's random pattern is Apple's own; amount, offset and cell size match.
     */
    private fun grainRow(w: FloatImage, r: Int, y: Int, grain: Double, size: Double) {
        val amount = (grain * 0.56).toFloat()
        val bias = (-grain * 0.28).toFloat()
        if (size <= 1.0) {
            for (x in 0 until w.width) {
                val o = (r * w.width + x) * 3
                w.data[o] += Filters.hash01(x, y, 101).toFloat() * amount + bias
                w.data[o + 1] += Filters.hash01(x, y, 102).toFloat() * amount + bias
                w.data[o + 2] += Filters.hash01(x, y, 103).toFloat() * amount + bias
            }
            return
        }
        val v = (y + 0.5) / size - 0.5
        val j = floor(v).toInt()
        val fy = (v - j).toFloat()
        for (x in 0 until w.width) {
            val u = (x + 0.5) / size - 0.5
            val i = floor(u).toInt()
            val fx = (u - i).toFloat()
            val o = (r * w.width + x) * 3
            for (c in 0..2) {
                val salt = 101 + c
                val top = Filters.hash01(i, j, salt).toFloat() * (1 - fx) + Filters.hash01(i + 1, j, salt).toFloat() * fx
                val bottom = Filters.hash01(i, j + 1, salt).toFloat() * (1 - fx) + Filters.hash01(i + 1, j + 1, salt).toFloat() * fx
                w.data[o + c] += (top + (bottom - top) * fy) * amount + bias
            }
        }
    }

    /**
     * Directional emboss (3 x 3 convolution of the colour plus 0.5, edge pixels repeated outside the
     * canvas, alpha 1) applied with hard light, for row `r` of `w` (canvas row `top + r` of a canvas
     * `height` rows tall) into `out`. Flat areas give 0.5, which hard light leaves unchanged.
     */
    private fun reliefRow(w: FloatImage, r: Int, top: Int, height: Int, angleFraction: Double, relief: Double, out: FloatArray) {
        val angle = angleFraction * PI * 2
        val ca = cos(angle)
        val sa = sin(angle)
        val rf = relief * 2.0
        // Rows of the kernel run top to bottom on screen.
        val k = floatArrayOf(
            ((-ca - sa) * rf).toFloat(), (-sa * rf).toFloat(), ((ca - sa) * rf).toFloat(),
            (-ca * rf).toFloat(), 0f, (ca * rf).toFloat(),
            ((-ca + sa) * rf).toFloat(), (sa * rf).toFloat(), ((ca + sa) * rf).toFloat(),
        )
        val width = w.width
        val y = top + r
        for (x in 0 until width) {
            for (c in 0..2) {
                var sum = 0.5f
                var ki = 0
                for (ky in -1..1) {
                    val yy = (y + ky).coerceIn(0, height - 1)
                    for (kx in -1..1) {
                        val xx = (x + kx).coerceIn(0, width - 1)
                        sum += k[ki] * w.data[((yy - top) * width + xx) * 3 + c]
                        ki++
                    }
                }
                val base = w.data[(r * width + x) * 3 + c]
                // Core Image's hard light clamps the emboss to [0, 1] (measured).
                val tp = sum.coerceIn(0f, 1f)
                out[x * 3 + c] = if (tp <= 0.5f) 2f * base * tp else 1f - 2f * (1f - base) * (1f - tp)
            }
        }
    }

    // ── Light layers drawn after the chain ───────────────────────────────

    private fun lightLayer(width: Int, height: Int, draw: (LineList) -> Unit): Image8 {
        val lines = LineList()
        draw(lines)
        return PixelBuffer.drawInStrips(width, height) { lines.drawInto(it, par) }
    }

    /** Tapered diffraction spikes: four long arms and four shorter, dimmer diagonals. */
    private fun stars(lines: LineList, s: EffectsLayerSettings, width: Int, height: Int) {
        val rng = SeededRng(s.starsSeed)
        val n = scaledCount((s.stars * 400).toInt() + 10, width, height)
        val wf = min(width, height).toFloat()
        val xf = width.toFloat()
        val yf = height.toFloat()
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
            val x = rng.nextFloat() * xf
            val y = rng.nextFloat() * yf
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
    private fun glitter(lines: LineList, s: EffectsLayerSettings, width: Int, height: Int) {
        val rng = SeededRng(s.glitterSeed)
        val n = scaledCount((s.glitter * 2500).toInt() + 80, width, height)
        val wf = min(width, height).toFloat()
        val xf = width.toFloat()
        val yf = height.toFloat()
        repeat(n) {
            val x = rng.nextFloat() * xf
            val y = rng.nextFloat() * yf
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
