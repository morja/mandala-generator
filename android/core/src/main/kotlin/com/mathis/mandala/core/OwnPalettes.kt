package com.mathis.mandala.core

import kotlinx.serialization.Serializable
import kotlin.math.floor

/**
 * An own palette in the macOS app's `CustomPalette` format, so `palettes.json` and the copies inside
 * settings read the same on both. A layer or emblem that uses one carries it whole (ADR-0020); its
 * `paletteIndex` keeps a built-in palette for apps that do not know the field.
 */
@Serializable
data class CustomPalette(
    val id: String = "",
    val name: String = "",
    val stops: List<CustomPaletteStop> = emptyList(),
) {
    /** Stops sorted by position, colours as P3 like the built-in palettes. */
    fun toColorPalette(): ColorPalette =
        ColorPalette(id, name, stops.sortedBy { it.position }.map { PaletteStop(it.position, it.toRgb()) })
}

/** One stop as the Mac stores it: hue, saturation and brightness (HSB) of an sRGB colour, all 0–1. */
@Serializable
data class CustomPaletteStop(
    val id: String = "",
    val position: Double = 0.0,
    val hue: Double = 0.0,
    val saturation: Double = 0.0,
    val brightness: Double = 1.0,
) {
    fun toRgb(): Rgb {
        val out = DoubleArray(3)
        OwnPalettes.hsbToSrgb(hue, saturation, brightness, out)
        return Rgb.fromSrgb(out[0], out[1], out[2])
    }
}

object OwnPalettes {
    const val MIN_STOPS = 2
    const val MAX_STOPS = 8

    /** The palette a layer or emblem draws with: its own one when it carries one, else the built-in at `index`. */
    fun resolve(index: Int, own: CustomPalette?, palettes: List<ColorPalette>): ColorPalette =
        if (own != null && own.stops.isNotEmpty()) own.toColorPalette() else palettes[index.coerceIn(0, palettes.size - 1)]

    /**
     * The built-in palette among `candidates` that looks most like `palette`, compared at evenly spaced
     * points: what an app without own palettes shows instead.
     */
    fun nearestBuiltIn(palette: ColorPalette, candidates: List<Int>, palettes: List<ColorPalette> = BuiltInPalettes.all): Int {
        val samples = 16
        val a = FloatArray(3)
        val b = FloatArray(3)
        return candidates.minByOrNull { index ->
            var d = 0.0
            for (i in 0 until samples) {
                val t = i / (samples - 1.0)
                palette.colorAt(t, a)
                palettes[index].colorAt(t, b)
                for (c in 0..2) d += (a[c] - b[c]).toDouble().let { it * it }
            }
            d
        } ?: 0
    }

    /**
     * About `count` colours from a picture (packed ARGB, sRGB), dark to bright, evenly spaced: the start
     * of a palette "from the picture". Near-black pixels are left out, as the mandala lies on black.
     */
    fun fromPixels(argb: IntArray, count: Int = 5): List<CustomPaletteStop> {
        val step = maxOf(1, argb.size / 6000)
        val px = ArrayList<DoubleArray>()
        var i = 0
        while (i < argb.size) {
            val c = argb[i]
            val r = (c shr 16 and 0xFF) / 255.0
            val g = (c shr 8 and 0xFF) / 255.0
            val b = (c and 0xFF) / 255.0
            if (maxOf(r, g, b) > 0.08) px += doubleArrayOf(r, g, b)
            i += step
        }
        if (px.isEmpty()) return defaultStops()
        px.sortBy { luma(it) }
        val k = count.coerceIn(MIN_STOPS, MAX_STOPS).coerceAtMost(px.size)
        // k-means from brightness quantiles: deterministic, and the clusters come out spread from dark to bright.
        val centres = Array(k) { px[((it + 0.5) * px.size / k).toInt().coerceAtMost(px.size - 1)].copyOf() }
        val assign = IntArray(px.size)
        repeat(12) {
            for ((pi, p) in px.withIndex()) {
                var best = 0
                var bestD = Double.MAX_VALUE
                for ((ci, c) in centres.withIndex()) {
                    val d = (p[0] - c[0]).let { it * it } + (p[1] - c[1]).let { it * it } + (p[2] - c[2]).let { it * it }
                    if (d < bestD) { bestD = d; best = ci }
                }
                assign[pi] = best
            }
            val sums = Array(k) { DoubleArray(4) }
            for ((pi, p) in px.withIndex()) {
                val s = sums[assign[pi]]
                s[0] += p[0]; s[1] += p[1]; s[2] += p[2]; s[3] += 1.0
            }
            for (ci in 0 until k) if (sums[ci][3] > 0) for (c in 0..2) centres[ci][c] = sums[ci][c] / sums[ci][3]
        }
        val sorted = centres.sortedBy { luma(it) }
        val hsb = DoubleArray(3)
        return sorted.mapIndexed { n, c ->
            srgbToHsb(c[0], c[1], c[2], hsb)
            CustomPaletteStop(position = if (k == 1) 0.0 else n / (k - 1.0), hue = hsb[0], saturation = hsb[1], brightness = hsb[2])
        }
    }

    /** The Mac editor's starting stops: dark blue, violet, pink. */
    fun defaultStops(): List<CustomPaletteStop> = listOf(
        CustomPaletteStop(position = 0.0, hue = 0.6, saturation = 1.0, brightness = 0.2),
        CustomPaletteStop(position = 0.5, hue = 0.75, saturation = 1.0, brightness = 0.8),
        CustomPaletteStop(position = 1.0, hue = 0.9, saturation = 0.8, brightness = 1.0),
    )

    /** HSB stops close to a built-in palette's stops, to start an own palette from it. */
    fun stopsOf(palette: ColorPalette): List<CustomPaletteStop> {
        val srgb = DoubleArray(3)
        val hsb = DoubleArray(3)
        return palette.stops.take(MAX_STOPS).map {
            ColorSpaces.p3ToSrgb(it.color.r, it.color.g, it.color.b, srgb)
            srgbToHsb(srgb[0].coerceIn(0.0, 1.0), srgb[1].coerceIn(0.0, 1.0), srgb[2].coerceIn(0.0, 1.0), hsb)
            CustomPaletteStop(position = it.position, hue = hsb[0], saturation = hsb[1], brightness = hsb[2])
        }
    }

    fun hsbToSrgb(h: Double, s: Double, v: Double, out: DoubleArray) {
        val hh = (h - floor(h)) * 6.0
        val sector = floor(hh).toInt() % 6
        val f = hh - floor(hh)
        val p = v * (1 - s)
        val q = v * (1 - s * f)
        val t = v * (1 - s * (1 - f))
        val (r, g, b) = when (sector) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        out[0] = r; out[1] = g; out[2] = b
    }

    fun srgbToHsb(r: Double, g: Double, b: Double, out: DoubleArray) {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0.0 -> 0.0
            max == r -> ((g - b) / d).mod(6.0)
            max == g -> (b - r) / d + 2.0
            else -> (r - g) / d + 4.0
        } / 6.0
        out[0] = h
        out[1] = if (max == 0.0) 0.0 else d / max
        out[2] = max
    }

    private fun luma(c: DoubleArray) = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
}
