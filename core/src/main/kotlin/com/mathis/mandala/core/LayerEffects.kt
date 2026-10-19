package com.mathis.mandala.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The per-layer post-processing of the macOS renderer. Each public function stands for one former
 * Core Image step: inputs are converted to the Display P3 working space, the step computes without
 * clamping, and the result comes back as clamped, 8-bit-quantised sRGB.
 */
internal class LayerEffects(private val par: Parallel) {

    private fun finish(working: FloatImage): FloatImage = working.toSrgbInPlace().clampAndQuantize()

    private data class BlurPass(val radius: Double, val strength: Double)

    /**
     * Additive blur passes computed at a fixed 1600 px reference width, so glow looks the same at
     * every resolution. Radii are in reference pixels. Returns an overlay at the source size.
     */
    private fun referenceBlurOverlay(source: FloatImage, passes: List<BlurPass>): FloatImage? {
        val sf = 1600.0 / source.width
        val scaled = abs(sf - 1.0) > 0.005
        val work = if (scaled) {
            Filters.resample(source, 1600, (source.height * sf).roundToInt(), par)
        } else {
            source
        }
        var overlay: FloatImage? = null
        for (pass in passes) {
            if (pass.radius < 0.5) continue
            val blurred = Filters.scaleInPlace(Filters.gaussianBlur(work, pass.radius, par), pass.strength.toFloat())
            overlay = overlay?.let { Filters.addInPlace(it, blurred) } ?: blurred
        }
        val result = overlay ?: return null
        return if (scaled) Filters.resample(result, source.width, source.height, par) else result
    }

    fun glow(image: FloatImage, intensity: Double): FloatImage {
        if (intensity <= 0) return image
        val src = image.toP3()
        val i = intensity
        val passes = listOf(
            BlurPass(2.0 * i * 3.0, 1.0 * i),
            BlurPass(8.0 * i * 3.0, 0.4 * i),
            BlurPass(20.0 * i * 3.0, 0.15 * i),
        )
        val overlay = referenceBlurOverlay(src, passes) ?: return image
        return finish(Filters.blend(src, overlay, BlendOp.SCREEN))
    }

    fun wash(image: FloatImage, amount: Double): FloatImage {
        if (amount <= 0) return image
        val src = image.toP3()
        val a = amount
        val main = listOf(
            BlurPass(4.0 * a, 0.25),
            BlurPass(12.0 * a, 0.20),
            BlurPass(28.0 * a, 0.15),
            BlurPass(55.0 * a, 0.10),
        )
        var result = src.copy()
        referenceBlurOverlay(src, main)?.let { Filters.addInPlace(result, it) }
        if (amount > 0.15) {
            val saturated = Filters.colorControlsInPlace(src.copy(), minOf(4.0, 1.0 + amount * 3.0), 0.0, 1.0)
            referenceBlurOverlay(saturated, listOf(BlurPass(45.0 * a, amount * 0.25)))?.let {
                result = Filters.addInPlace(result, it)
            }
        }
        return finish(result)
    }

    /** Turbulent displacement plus blur; above 0.3 also colour bleed and grain. */
    fun painted(image: FloatImage, abstractLevel: Double): FloatImage {
        var result = image.toP3()
        val w = result.width
        val h = result.height
        val scale = w / 1600.0
        val level = abstractLevel

        val displacement = (level * 30.0 * scale).toFloat()
        val size = w * (0.1 + level * 0.45)
        val nx = Filters.turbulence(w, h, size, level * 3.0, 3, salt = 17)
        val ny = Filters.turbulence(w, h, size, level * 3.0, 3, salt = 9001)
        result = Filters.displace(result, nx, ny, displacement, par)

        result = Filters.gaussianBlur(result, level * 3.0 * scale, par)

        val heavyFactor = max(0.0, (level - 0.3) / 0.7)
        val heavyBlur = heavyFactor * level * 8.0 * scale
        if (heavyBlur > 0.5) {
            result = Filters.gaussianBlur(result, heavyBlur, par)
            val saturated = Filters.colorControlsInPlace(result.copy(), 1.0 + heavyFactor, 0.1 * heavyFactor, 1.0)
            val bleed = Filters.gaussianBlur(saturated, heavyBlur * 1.5, par)
            result = Filters.blend(result, bleed, BlendOp.SCREEN)

            val grain = (heavyFactor * level * 0.12).toFloat()
            val d = result.data
            for (p in 0 until w * h) {
                val x = p % w
                val y = p / w
                d[p * 3] += Filters.hash01(x, y, 1).toFloat() * grain
                d[p * 3 + 1] += Filters.hash01(x, y, 2).toFloat() * grain
                d[p * 3 + 2] += Filters.hash01(x, y, 3).toFloat() * grain
            }
        }
        return finish(result)
    }

    /** Saturation and contrast, then brightness as a pure multiplier (black stays black). */
    fun colourGrade(image: FloatImage, saturation: Double, brightness: Double): FloatImage {
        val img = Filters.colorControlsInPlace(image.toP3(), saturation * 3.0, 0.0, 0.9 + brightness * 0.3)
        return finish(Filters.scaleInPlace(img, (brightness * 3.0).toFloat()))
    }

    fun rotate(image: FloatImage, angle: Double): FloatImage =
        finish(Filters.rotate(image.toP3(), angle, par))

    fun opacity(image: FloatImage, opacity: Double): FloatImage =
        finish(Filters.scaleInPlace(image.toP3(), opacity.toFloat()))

    fun composite(base: FloatImage, overlay: FloatImage, op: BlendOp): FloatImage =
        finish(Filters.blend(base.toP3(), overlay.toP3(), op))

    fun downscale(image: FloatImage, size: Int): FloatImage =
        finish(Filters.resample(image.toP3(), size, size, par))
}
