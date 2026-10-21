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
internal class LayerEffects(
    private val par: Parallel,
    /**
     * Width at which glow and wash are blurred. The macOS renderer's 1600 px by default; the app's
     * fast preview uses less, which is quicker and softens the glow slightly (ADR-0004).
     */
    private val referenceWidth: Int = REFERENCE_WIDTH,
) {

    data class BlurPass(val radius: Double, val strength: Double)

    companion object {
        /** The macOS renderer's reference width; blur radii are given in its pixels. */
        const val REFERENCE_WIDTH = 1600

        /** Core height of one strip of the painted effect; the overlap comes on top. */
        private const val STRIP_ROWS = 512

        /**
         * Reference images larger than this (a square up to 1700 px) are blurred in strips of rows,
         * so a tall canvas does not hold several full reference copies at once.
         */
        private const val REF_TILE_AREA = 1700 * 1700
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

    /** Radii in reference pixels become this many working pixels each (1 at the default width). */
    private val refScale = referenceWidth.toDouble() / REFERENCE_WIDTH

    fun referenceBlurOverlay(source: RowSource, effectWidth: Int, passes: List<BlurPass>, extra: ((FloatImage) -> FloatImage?)? = null): FloatImage? {
        val sf = referenceWidth.toDouble() / effectWidth
        val scaled = abs(sf - 1.0) > 0.005
        val refW = if (scaled) (source.width * sf).roundToInt() else source.width
        val refH = if (scaled) (source.height * sf).roundToInt() else source.height
        val active = passes.filter { it.radius >= 0.5 }.map { if (refScale == 1.0) it else it.copy(radius = it.radius * refScale) }
        if (extra == null && refW.toLong() * refH > REF_TILE_AREA) return tiledOverlay(source, refW, refH, active)
        val work = Filters.resampleToFloat(source, refW, refH, par)
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

    /**
     * [referenceBlurOverlay] for a large reference image, strip by strip: each strip is resampled
     * with an overlap as wide as the widest blur reads and only its core rows are kept.
     */
    private fun tiledOverlay(source: RowSource, refW: Int, refH: Int, active: List<BlurPass>): FloatImage? {
        if (active.isEmpty()) return null
        val reach = active.maxOf { Filters.blurReach(it.radius) }
        val core = max(STRIP_ROWS, 2 * reach)
        val n = refW * 3
        val overlay = FloatImage(refW, refH)
        for (t0 in 0 until refH step core) {
            val t1 = minOf(refH, t0 + core)
            val lo = max(0, t0 - reach)
            val hi = minOf(refH, t1 + reach)
            val work = FloatImage(refW, hi - lo)
            Filters.resampleRows(source, refW, refH, lo, hi, par) { y, row -> System.arraycopy(row, 0, work.data, (y - lo) * n, n) }
            var acc: FloatImage? = null
            for ((index, pass) in active.withIndex()) {
                val blurred = if (index == active.lastIndex) work else work.copy()
                Filters.blurInPlace(blurred, pass.radius, par)
                val st = pass.strength.toFloat()
                val a = acc
                if (a == null) {
                    for (i in blurred.data.indices) blurred.data[i] *= st
                    acc = blurred
                } else {
                    for (i in a.data.indices) a.data[i] += blurred.data[i] * st
                }
            }
            System.arraycopy(acc!!.data, (t0 - lo) * n, overlay.data, t0 * n, (t1 - t0) * n)
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
                Filters.blurInPlace(work, 45.0 * a * refScale, par)
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
     * whole image had been processed at once. The result replaces the input in place; the input
     * rows the next strip still reads above its core are kept aside before they are overwritten.
     */
    fun painted(image: Image8, abstractLevel: Double, effectWidth: Int, offsetX: Int, offsetY: Int): Image8 {
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
        val overlap = paintedReach(abstractLevel, effectWidth)

        val out = target(image)
        val n = w * 3
        // Original bytes of rows [keptFrom, y0), which earlier strips have already overwritten.
        var kept = Image8(w, 0, image.encoding, ByteArray(0))
        var keptFrom = 0
        for (y0 in 0 until h step STRIP_ROWS) {
            val y1 = minOf(h, y0 + STRIP_ROWS)
            val top = max(0, y0 - overlap)
            val bottom = minOf(h, y1 + overlap)
            val strip = FloatImage(w, bottom - top)
            val above = kept
            val aboveFrom = keptFrom
            par.forRange(bottom - top) { from, until ->
                val row = FloatArray(n)
                for (r in from until until) {
                    val y = top + r
                    if (y < y0) WorkingSpace.readRow(above, y - aboveFrom, row) else WorkingSpace.readRow(image, y, row)
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
                        val vy = top + r + offsetY
                        for (x in 0 until w) {
                            val p = (r * w + x) * 3
                            val vx = x + offsetX
                            d[p] += Filters.hash01(vx, vy, 1).toFloat() * grain
                            d[p + 1] += Filters.hash01(vx, vy, 2).toFloat() * grain
                            d[p + 2] += Filters.hash01(vx, vy, 3).toFloat() * grain
                        }
                    }
                }
            }

            // Keep the input rows the next strip reads above its core, then write this core.
            val nextFrom = max(0, y1 - overlap)
            val keep = ByteArray((y1 - nextFrom) * n)
            for (y in nextFrom until y1) {
                if (y < y0) System.arraycopy(above.data, (y - aboveFrom) * n, keep, (y - nextFrom) * n, n)
                else System.arraycopy(image.data, y * n, keep, (y - nextFrom) * n, n)
            }
            kept = Image8(w, y1 - nextFrom, image.encoding, keep)
            keptFrom = nextFrom
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

    /** How many pixels the painted effect reads around each pixel. */
    fun paintedReach(abstractLevel: Double, effectWidth: Int): Int {
        val scale = effectWidth / 1600.0
        val heavyFactor = max(0.0, (abstractLevel - 0.3) / 0.7)
        val heavyBlur = heavyFactor * abstractLevel * 8.0 * scale
        fun reach(sigma: Double) = if (sigma < 0.05) 0 else ceil(sigma * 3.0).toInt() + 3
        return reach(abstractLevel * 3.0 * scale) + (if (heavyBlur > 0.5) reach(heavyBlur) + reach(heavyBlur * 1.5) else 0)
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

    /** Rotates about (cx, cy) (counter-clockwise on screen for positive angles), bilinear, zero outside. */
    fun rotate(image: Image8, angle: Double, cx: Float = image.width * 0.5f, cy: Float = image.height * 0.5f): Image8 {
        val w = image.width
        val h = image.height
        val out = Image8(w, h, Encoding.SRGB)
        val ca = cos(angle).toFloat()
        val sa = sin(angle).toFloat()
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

    /**
     * Blends `overlay`, whose top left corner sits at (x0, y0) on `base`, into `base` in place. Outside
     * the overlay it counts as black, as if it covered the whole base: that changes nothing except
     * with multiply, which darkens to black there.
     */
    fun composite(base: Image8, overlay: Image8?, op: BlendOp, x0: Int = 0, y0: Int = 0): Image8 {
        val out = target(base)
        val w = base.width
        val n = w * 3
        val covers = overlay != null && x0 == 0 && y0 == 0 && overlay.width == w && overlay.height == base.height
        par.forRange(base.height) { from, until ->
            val a = FloatArray(n)
            val b = FloatArray(n)
            val src = if (overlay == null || covers) null else FloatArray(overlay.width * 3)
            for (y in from until until) {
                if (covers) {
                    WorkingSpace.readRow(overlay!!, y, b)
                } else {
                    val oy = y - y0
                    val inRows = overlay != null && oy >= 0 && oy < overlay.height
                    // Rows the overlay misses stay as they are, unless they still need converting to sRGB.
                    if (!inRows && op != BlendOp.MULTIPLY && base.encoding == Encoding.SRGB) continue
                    b.fill(0f)
                    if (inRows) {
                        WorkingSpace.readRow(overlay!!, oy, src!!)
                        val xs = max(0, x0)
                        val xe = minOf(w, x0 + overlay.width)
                        if (xe > xs) System.arraycopy(src, (xs - x0) * 3, b, xs * 3, (xe - xs) * 3)
                    }
                }
                WorkingSpace.readRow(base, y, a)
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

    fun downscale(image: Image8, width: Int, height: Int): Image8 {
        val out = Image8(width, height, Encoding.SRGB)
        Filters.resampleRows(image.asRowSource(), width, height, par) { y, row -> WorkingSpace.writeRow(row, out, y) }
        return out
    }
}
