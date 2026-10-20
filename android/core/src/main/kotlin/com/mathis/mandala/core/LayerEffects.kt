package com.mathis.mandala.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The per-layer post-processing of the macOS renderer. Each public function stands for one former
 * Core Image step: it reads 8-bit images as Display P3 working values, computes without clamping
 * and returns clamped 8-bit sRGB, writing into the input where that is safe.
 *
 * `effectWidth` is the width the macOS renderer would have used for this step. The layer buffer is
 * only enlarged for rotated layers; size-dependent effects still scale by the enlarged width, so
 * the result matches the enlarged-and-cropped macOS output. `offset` is where this image sits
 * inside that larger area (noise and grain are sampled there).
 */
internal class LayerEffects(private val par: Parallel) {

    data class BlurPass(val radius: Double, val strength: Double)

    private companion object {
        /** Core height of one strip of the painted effect; the overlap comes on top. */
        const val STRIP_ROWS = 512
    }

    private val rowScratch = ThreadLocal<FloatArray>()

    private fun scratch(size: Int): FloatArray {
        val s = rowScratch.get()
        if (s != null && s.size >= size) return s
        return FloatArray(size).also { rowScratch.set(it) }
    }

    /**
     * Output image over the input's own bytes: every caller reads a row and then writes the same row,
     * so the result can replace the input in place. A Display P3 input is relabelled sRGB; callers
     * must not use the input image afterwards.
     */
    private fun target(img: Image8): Image8 =
        if (img.encoding == Encoding.SRGB) img else Image8(img.width, img.height, Encoding.SRGB, img.data)

    /**
     * Sum of blur passes computed at a 1600 px reference width (radii in reference pixels), so glow
     * looks the same at every resolution. Returned at reference size; `prepare` may modify the
     * reference copy of the source before blurring (it is linear, so this equals doing it first).
     */
    private fun referenceBlurOverlay(source: Image8, effectWidth: Int, passes: List<BlurPass>, extra: ((FloatImage) -> FloatImage?)? = null): FloatImage? =
        referenceBlurOverlay(source.asRowSource(), effectWidth, passes, extra)

    fun referenceBlurOverlay(source: RowSource, effectWidth: Int, passes: List<BlurPass>, extra: ((FloatImage) -> FloatImage?)? = null): FloatImage? {
        val sf = 1600.0 / effectWidth
        val scaled = abs(sf - 1.0) > 0.005
        val refW = if (scaled) (source.width * sf).roundToInt() else source.width
        val refH = if (scaled) (source.height * sf).roundToInt() else source.height
        val work = Filters.resampleToFloat(source, refW, refH, par)
        val active = passes.filter { it.radius >= 0.5 }
        var overlay: FloatImage? = null
        for ((index, pass) in active.withIndex()) {
            val last = index == active.lastIndex && extra == null
            val blurred = if (last) work else work.copy()
            Filters.blurInPlace(blurred, pass.radius, par)
            val s = pass.strength.toFloat()
            val acc = overlay
            if (acc == null) {
                for (i in blurred.data.indices) blurred.data[i] *= s
                overlay = blurred
            } else {
                for (i in acc.data.indices) acc.data[i] += blurred.data[i] * s
            }
        }
        extra?.invoke(work)?.let { more ->
            val acc = overlay
            if (acc == null) overlay = more else for (i in acc.data.indices) acc.data[i] += more.data[i]
        }
        return overlay
    }

    /** Streams `overlay` back to the size of `src` and combines it row by row into a result image. */
    private fun combineWithOverlay(src: Image8, overlay: FloatImage, op: BlendOp): Image8 {
        val out = target(src)
        val n = src.width * 3
        Filters.resampleRows(overlay.asRowSource(), src.width, src.height, par) { y, over ->
            val row = scratch(n)
            WorkingSpace.readRow(src, y, row)
            Filters.blend(row, over, n, op)
            WorkingSpace.writeRow(row, out, y)
        }
        return out
    }

    fun glow(image: Image8, intensity: Double, effectWidth: Int): Image8 {
        if (intensity <= 0) return image
        val i = intensity
        val passes = listOf(
            BlurPass(2.0 * i * 3.0, 1.0 * i),
            BlurPass(8.0 * i * 3.0, 0.4 * i),
            BlurPass(20.0 * i * 3.0, 0.15 * i),
        )
        val overlay = referenceBlurOverlay(image, effectWidth, passes) ?: return image
        return combineWithOverlay(image, overlay, BlendOp.SCREEN)
    }

    fun wash(image: Image8, amount: Double, effectWidth: Int): Image8 {
        if (amount <= 0) return image
        val a = amount
        val main = listOf(
            BlurPass(4.0 * a, 0.25),
            BlurPass(12.0 * a, 0.20),
            BlurPass(28.0 * a, 0.15),
            BlurPass(55.0 * a, 0.10),
        )
        // Chromatic bleed: saturate, then blur. Saturation is linear, so it can run on the reference copy.
        val chromatic: ((FloatImage) -> FloatImage?)? = if (amount > 0.15) { work ->
            Filters.colorControls(work.data, 0, work.data.size, minOf(4.0, 1.0 + amount * 3.0), 0.0, 1.0)
            if (45.0 * a >= 0.5) {
                Filters.blurInPlace(work, 45.0 * a, par)
                val s = (amount * 0.25).toFloat()
                for (k in work.data.indices) work.data[k] *= s
                work
            } else {
                null
            }
        } else {
            null
        }
        val overlay = referenceBlurOverlay(image, effectWidth, main, chromatic) ?: return image
        return combineWithOverlay(image, overlay, BlendOp.ADD)
    }

    /**
     * Blur; above 0.3 also colour bleed and grain. The macOS code first asks for a CITurbulence
     * displacement, but Core Image has no filter of that name, so `CIFilter(name:)` returns nil and
     * the step is skipped there. The port skips it as well.
     *
     * Computed in horizontal strips so only one strip is held as floats. Each strip carries an
     * overlap as tall as displacement and all blurs reach, so its core rows come out as if the
     * whole image had been processed at once.
     */
    fun painted(image: Image8, abstractLevel: Double, effectWidth: Int, offset: Int): Image8 {
        val w = image.width
        val h = image.height
        val scale = effectWidth / 1600.0
        val level = abstractLevel

        val softBlur = level * 3.0 * scale
        val heavyFactor = max(0.0, (level - 0.3) / 0.7)
        val heavyBlur = heavyFactor * level * 8.0 * scale
        val heavy = heavyBlur > 0.5
        val bleedBlur = heavyBlur * 1.5
        val grain = (heavyFactor * level * 0.12).toFloat()

        fun reach(sigma: Double) = if (sigma < 0.05) 0 else ceil(sigma * 3.0).toInt() + 3
        val overlap = reach(softBlur) +
            (if (heavy) reach(heavyBlur) + reach(bleedBlur) else 0)

        // Strips read the input around them, so the result goes into a separate image.
        val out = Image8(w, h, Encoding.SRGB)
        val n = w * 3
        for (y0 in 0 until h step STRIP_ROWS) {
            val y1 = minOf(h, y0 + STRIP_ROWS)
            val top = max(0, y0 - overlap)
            val bottom = minOf(h, y1 + overlap)
            val strip = FloatImage(w, bottom - top)
            par.forRange(bottom - top) { from, until ->
                val row = FloatArray(n)
                for (r in from until until) {
                    WorkingSpace.readRow(image, top + r, row)
                    System.arraycopy(row, 0, strip.data, r * n, n)
                }
            }

            Filters.blurInPlace(strip, softBlur, par)

            if (heavy) {
                Filters.blurInPlace(strip, heavyBlur, par)
                run {
                    val bleed = strip.copy()
                    Filters.colorControls(bleed.data, 0, bleed.data.size, 1.0 + heavyFactor, 0.1 * heavyFactor, 1.0)
                    Filters.blurInPlace(bleed, bleedBlur, par)
                    Filters.blend(strip.data, bleed.data, strip.data.size, BlendOp.SCREEN)
                }
                val d = strip.data
                par.forRange(bottom - top) { from, until ->
                    for (r in from until until) {
                        val vy = top + r + offset
                        for (x in 0 until w) {
                            val p = (r * w + x) * 3
                            val vx = x + offset
                            d[p] += Filters.hash01(vx, vy, 1).toFloat() * grain
                            d[p + 1] += Filters.hash01(vx, vy, 2).toFloat() * grain
                            d[p + 2] += Filters.hash01(vx, vy, 3).toFloat() * grain
                        }
                    }
                }
            }

            par.forRange(y1 - y0) { from, until ->
                val row = FloatArray(n)
                for (k in from until until) {
                    val y = y0 + k
                    System.arraycopy(strip.data, (y - top) * n, row, 0, n)
                    WorkingSpace.writeRow(row, out, y)
                }
            }
        }
        return out
    }

    /** Saturation and contrast, then brightness as a pure multiplier (black stays black). */
    fun colourGrade(image: Image8, saturation: Double, brightness: Double): Image8 =
        mapRows(image) { row, n ->
            Filters.colorControls(row, 0, n, saturation * 3.0, 0.0, 0.9 + brightness * 0.3)
            val m = (brightness * 3.0).toFloat()
            for (k in 0 until n) row[k] *= m
        }

    fun opacity(image: Image8, opacity: Double): Image8 =
        mapRows(image) { row, n -> val s = opacity.toFloat(); for (k in 0 until n) row[k] *= s }

    private inline fun mapRows(image: Image8, crossinline f: (FloatArray, Int) -> Unit): Image8 {
        val out = target(image)
        val n = image.width * 3
        par.forRange(image.height) { from, until ->
            val row = FloatArray(n)
            for (y in from until until) {
                WorkingSpace.readRow(image, y, row)
                f(row, n)
                WorkingSpace.writeRow(row, out, y)
            }
        }
        return out
    }

    /** Rotates about the centre (counter-clockwise on screen for positive angles), bilinear, zero outside. */
    fun rotate(image: Image8, angle: Double): Image8 {
        val w = image.width
        val h = image.height
        val out = Image8(w, h, Encoding.SRGB)
        val ca = cos(angle).toFloat()
        val sa = sin(angle).toFloat()
        val cx = w * 0.5f
        val cy = h * 0.5f
        par.forRange(h) { from, until ->
            val row = FloatArray(w * 3)
            val tmp = FloatArray(3)
            for (y in from until until) {
                for (x in 0 until w) {
                    val dx = x + 0.5f - cx
                    val dy = y + 0.5f - cy
                    Filters.sampleBilinear(image, cx + dx * ca - dy * sa - 0.5f, cy + dx * sa + dy * ca - 0.5f, row, x * 3, tmp)
                }
                WorkingSpace.writeRow(row, out, y)
            }
        }
        return out
    }

    /** Blends `overlay` into `base` (an sRGB image) in place. */
    fun composite(base: Image8, overlay: Image8, op: BlendOp): Image8 {
        val out = target(base)
        val n = base.width * 3
        par.forRange(base.height) { from, until ->
            val a = FloatArray(n)
            val b = FloatArray(n)
            for (y in from until until) {
                WorkingSpace.readRow(base, y, a)
                WorkingSpace.readRow(overlay, y, b)
                Filters.blend(a, b, n, op)
                WorkingSpace.writeRow(a, out, y)
            }
        }
        return out
    }

    /** Blends a constant P3 colour into `base` in place. */
    fun compositeSolid(base: Image8, r: Float, g: Float, b: Float, op: BlendOp): Image8 {
        val n = base.width * 3
        val solid = FloatArray(n) { if (it % 3 == 0) r else if (it % 3 == 1) g else b }
        return mapRows(base) { row, len -> Filters.blend(row, solid, len, op) }
    }

    fun downscale(image: Image8, size: Int): Image8 {
        val out = Image8(size, size, Encoding.SRGB)
        Filters.resampleRows(image.asRowSource(), size, size, par) { y, row -> WorkingSpace.writeRow(row, out, y) }
        return out
    }
}
