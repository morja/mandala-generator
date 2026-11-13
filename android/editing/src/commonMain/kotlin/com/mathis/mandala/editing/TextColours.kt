package com.mathis.mandala.editing

import com.mathis.mandala.core.OwnPalettes
import com.mathis.mandala.core.TextLayerSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** One colour as hue, saturation and brightness, all 0–1 (text brightness may be above 1, see [TextColours]). */
data class Hsb(val hue: Double, val saturation: Double, val brightness: Double) {
    companion object {
        val WHITE = Hsb(0.0, 0.0, 1.0)
    }
}

/** Which of the two text colours the swatches, the colour sheet and "Fine tune" change (DM-24 A2). */
enum class TextColourTarget { TEXT, GLOW }

/**
 * The text layer's two colours (DM-24 A2, Mathis 13 November 2026): "Text" for the letters, "Glow" for
 * the light around them. The glow follows the text while "Same as text" is on, the default that keeps
 * every saved file looking as before; choosing a glow colour switches it off.
 */
object TextColours {
    /** Hues closer than this count as the same picture colour. */
    const val SWATCH_HUE_GAP = 0.055

    /** Picture colours offered beside white. */
    const val PICTURE_SWATCHES = 7

    /** "Stand out" takes a picture colour up to this far from the complement of the hue behind the text. */
    const val NEAR_COMPLEMENT = 0.15

    /** Below this hue clarity the picture behind the text has no clear hue (many colours mixed). */
    const val CLEAR_HUE = 0.2

    /** The glow's saturation at least, so it shows beside near-white letters. */
    const val MIN_GLOW_SATURATION = 0.6

    /** "Stand out" lowers a stronger glow to this, so the letters stay readable inside their own glow. */
    const val MAX_STAND_OUT_GLOW = 1.2

    /** Saturation of the complement when no picture colour is near it. */
    const val COMPLEMENT_SATURATION = 0.75

    /** Tints of the near-white letters toward the glow, tried in turn while the contrast stays below [MIN_CONTRAST]. */
    val TEXT_TINTS = listOf(0.12, 0.06, 0.0)

    /** Contrast the letters keep against the mean brightness behind them (WCAG AA for text). */
    const val MIN_CONTRAST = 4.5

    /** Saturation the hue strip gives a colour that had almost none, so a new hue shows. */
    const val HUE_STRIP_SATURATION = 0.8

    /** The hue strip's right end, short of 1 so the marker stays there instead of jumping to red at the left. */
    const val MAX_HUE = 0.999

    // ---------- Reading and writing the two colours ----------

    fun text(s: TextLayerSettings) = Hsb(s.hue, s.saturation, s.brightness)

    /**
     * The glow colour in effect: the text colour while "Same as text" is on, its brightness at most 1
     * (the renderer draws the colour so; a boost above 1 scales the whole text, see [withColour]).
     */
    fun glow(s: TextLayerSettings) =
        if (s.glowSameAsText) Hsb(s.hue, s.saturation, min(1.0, s.brightness)) else Hsb(s.glowHue, s.glowSaturation, s.glowBrightness)

    fun of(s: TextLayerSettings, target: TextColourTarget) = if (target == TextColourTarget.TEXT) text(s) else glow(s)

    /**
     * Sets one colour; setting the glow switches "Same as text" off. A text brightness above 1 (a
     * boost from the Mac or the slider, which also brightens the glow) is kept when the new colour
     * comes at full brightness (a swatch, "Stand out"); the field and the slider set it as given.
     */
    fun withPickedColour(s: TextLayerSettings, target: TextColourTarget, c: Hsb): TextLayerSettings =
        withColour(s, target, if (target == TextColourTarget.TEXT && c.brightness >= 1.0) c.copy(brightness = max(1.0, s.brightness)) else c)

    /** Sets one colour as given; setting the glow switches "Same as text" off. */
    fun withColour(s: TextLayerSettings, target: TextColourTarget, c: Hsb): TextLayerSettings = when (target) {
        TextColourTarget.TEXT -> s.copy(hue = c.hue, saturation = c.saturation, brightness = c.brightness)
        TextColourTarget.GLOW -> s.copy(glowSameAsText = false, glowHue = c.hue, glowSaturation = c.saturation, glowBrightness = c.brightness.coerceIn(0.0, 1.0))
    }

    /** Changes one part of a colour (a "Fine tune" slider or the field); the others stay. */
    fun withColour(s: TextLayerSettings, target: TextColourTarget, change: (Hsb) -> Hsb): TextLayerSettings = withColour(s, target, change(of(s, target)))

    /**
     * "Same as text": on, the glow follows the text again; off, it starts from the text colour, so the
     * picture does not change until a colour is chosen.
     */
    fun sameAsText(s: TextLayerSettings, on: Boolean): TextLayerSettings =
        if (on) s.copy(glowSameAsText = true)
        else if (!s.glowSameAsText) s
        else s.copy(glowSameAsText = false, glowHue = s.hue, glowSaturation = s.saturation, glowBrightness = s.brightness.coerceIn(0.0, 1.0))

    /** A hue from the hue strip; a colour with almost no saturation gets some, so the new hue shows. */
    fun withHueFromStrip(c: Hsb, hue: Double): Hsb =
        c.copy(hue = hue.coerceIn(0.0, MAX_HUE), saturation = if (c.saturation < 0.05) HUE_STRIP_SATURATION else c.saturation)

    /** Saturation and brightness from a point of the field (x to the right, y down, both 0–1). */
    fun withFieldPoint(c: Hsb, x: Double, y: Double): Hsb = c.copy(saturation = x.coerceIn(0.0, 1.0), brightness = (1 - y).coerceIn(0.0, 1.0))

    /** Whether a swatch shows the colour (to mark it as chosen); greys match at any hue. */
    fun matches(a: Hsb, b: Hsb): Boolean =
        abs(a.saturation - b.saturation) < 0.01 && abs(a.brightness - b.brightness) < 0.01 &&
            (a.saturation < 0.01 || hueDistance(a.hue, b.hue) < 0.004)

    fun hueDistance(a: Double, b: Double): Double {
        val d = abs(a - b) % 1.0
        return min(d, 1 - d)
    }

    // ---------- Picture colours ----------

    /**
     * Up to [PICTURE_SWATCHES] colours of a picture (packed ARGB, sRGB) at full brightness, plus white
     * last: dark pixels and greys are left out, the rest is grouped by hue and saturation and ranked by
     * area × saturation × brightness; kept hues lie at least [SWATCH_HUE_GAP] apart. Sorted by hue.
     */
    fun pictureSwatches(argb: IntArray): List<Hsb> {
        val hueBins = 48
        val satBins = 4
        // Per bin: count, saturation sum, brightness sum, hue as a vector (x, y).
        val bins = Array(hueBins * satBins) { DoubleArray(5) }
        val hsb = DoubleArray(3)
        val step = max(1, argb.size / 20000)
        var i = 0
        while (i < argb.size) {
            val c = argb[i]
            OwnPalettes.srgbToHsb((c shr 16 and 0xFF) / 255.0, (c shr 8 and 0xFF) / 255.0, (c and 0xFF) / 255.0, hsb)
            i += step
            if (hsb[2] < 0.25 || hsb[1] < 0.15) continue
            val bin = min(hueBins - 1, (hsb[0] * hueBins).toInt()) * satBins + min(satBins - 1, (hsb[1] * satBins).toInt())
            val b = bins[bin]
            b[0] += 1.0; b[1] += hsb[1]; b[2] += hsb[2]
            b[3] += cos(hsb[0] * 2 * PI); b[4] += sin(hsb[0] * 2 * PI)
        }
        val ranked = bins.filter { it[0] > 0 }.map { b ->
            val n = b[0]
            val hue = (atan2(b[4], b[3]) / (2 * PI)).let { it - floor(it) }
            val sat = b[1] / n
            val bright = b[2] / n
            Pair(Hsb(hue, sat, 1.0), n * sat * bright)
        }.sortedByDescending { it.second }
        val kept = ArrayList<Hsb>()
        for ((c, _) in ranked) {
            if (kept.size == PICTURE_SWATCHES) break
            if (kept.none { hueDistance(it.hue, c.hue) < SWATCH_HUE_GAP }) kept += c
        }
        return kept.sortedBy { it.hue } + Hsb.WHITE
    }

    // ---------- "Stand out" ----------

    /** What "Stand out" chose, and from what (for tests and the QA report). */
    data class StandOut(
        val text: Hsb,
        val glow: Hsb,
        /** Dominant hue behind the text and how clear it is (1 = one hue, 0 = all hues). */
        val dominantHue: Double,
        val clarity: Double,
        /** Contrast of the letters against the mean behind them. */
        val contrast: Double,
    )

    /**
     * The part of the picture the text with its glow covers, as fractions of the picture (x0, y0, x1,
     * y1, y down): the text box's width around its centre, about a quarter of the height around the
     * text's vertical centre (as in the DM-24 mock).
     */
    fun textArea(s: TextLayerSettings): DoubleArray {
        val half = s.fontWidth.coerceIn(0.05, 1.0) / 2
        val centreY = 1 - s.offsetY
        return doubleArrayOf(
            (s.offsetX - half).coerceIn(0.0, 1.0), (centreY - 0.13).coerceIn(0.0, 1.0),
            (s.offsetX + half).coerceIn(0.0, 1.0), (centreY + 0.13).coerceIn(0.0, 1.0),
        )
    }

    /**
     * The DM-24 rule. The picture behind the text block (packed ARGB, sRGB, `width` × `height`, without
     * the text) gives the dominant hue of its lines, lit and saturated pixels weighing most. Glow: the
     * picture colour nearest the complement if within [NEAR_COMPLEMENT], else the complement itself at
     * [COMPLEMENT_SATURATION]; with no clear hue behind, the picture colour farthest from the background
     * hue; at least [MIN_GLOW_SATURATION]. Text: near white tinted toward the glow, less tint while
     * the letters miss [MIN_CONTRAST] against the mean behind them. Null when the area is empty.
     */
    fun standOut(argb: IntArray, width: Int, height: Int, area: DoubleArray, swatches: List<Hsb>): StandOut? {
        val x0 = (area[0] * width).toInt().coerceIn(0, width)
        val x1 = (area[2] * width).toInt().coerceIn(0, width)
        val y0 = (area[1] * height).toInt().coerceIn(0, height)
        val y1 = (area[3] * height).toInt().coerceIn(0, height)
        if (x1 <= x0 || y1 <= y0) return null
        var sx = 0.0
        var sy = 0.0
        var sw = 0.0
        val mean = DoubleArray(3)
        var n = 0
        val hsb = DoubleArray(3)
        for (y in y0 until y1) for (x in x0 until x1) {
            val c = argb[y * width + x]
            val r = (c shr 16 and 0xFF) / 255.0
            val g = (c shr 8 and 0xFF) / 255.0
            val b = (c and 0xFF) / 255.0
            mean[0] += r; mean[1] += g; mean[2] += b; n++
            OwnPalettes.srgbToHsb(r, g, b, hsb)
            // Lit, saturated lines count; dark gaps hardly.
            val w = hsb[1] * hsb[2] * hsb[2]
            sx += cos(hsb[0] * 2 * PI) * w
            sy += sin(hsb[0] * 2 * PI) * w
            sw += w
        }
        for (k in 0..2) mean[k] /= n
        val dominant = (atan2(sy, sx) / (2 * PI)).let { it - floor(it) }
        val clarity = if (sw > 0) hypot(sx, sy) / sw else 0.0
        val opposite = (dominant + 0.5) % 1.0
        val colours = swatches.filter { it.saturation > 0 }

        var glow = when {
            colours.isEmpty() -> Hsb(opposite, COMPLEMENT_SATURATION, 1.0)
            clarity < CLEAR_HUE -> colours.maxBy { hueDistance(it.hue, dominant) }
            else -> {
                val near = colours.minBy { hueDistance(it.hue, opposite) }
                if (hueDistance(near.hue, opposite) <= NEAR_COMPLEMENT) near else Hsb(opposite, COMPLEMENT_SATURATION, 1.0)
            }
        }
        if (glow.saturation < MIN_GLOW_SATURATION) glow = Hsb(glow.hue, MIN_GLOW_SATURATION, 1.0)

        val behind = luminance(mean[0], mean[1], mean[2])
        fun contrast(c: Hsb): Double {
            val rgb = DoubleArray(3)
            OwnPalettes.hsbToSrgb(c.hue, c.saturation, c.brightness, rgb)
            val l = luminance(rgb[0], rgb[1], rgb[2])
            return (max(l, behind) + 0.05) / (min(l, behind) + 0.05)
        }
        var text = Hsb(glow.hue, TEXT_TINTS.first(), 1.0)
        for (tint in TEXT_TINTS.drop(1)) if (contrast(text) < MIN_CONTRAST) text = Hsb(glow.hue, tint, 1.0)
        return StandOut(text, glow, dominant, clarity, contrast(text))
    }

    /**
     * Applies a "Stand out" result: both colours, "Same as text" off; cloud, shadow and a text
     * brightness boost above 1 stay. A glow stronger than [MAX_STAND_OUT_GLOW] is lowered to it, because
     * a bright glow in a light colour swallows the near-white letters (QA-12, Mathis 13 November 2026).
     */
    fun applyStandOut(s: TextLayerSettings, result: StandOut): TextLayerSettings =
        withColour(withPickedColour(s, TextColourTarget.TEXT, result.text), TextColourTarget.GLOW, result.glow)
            .let { if (it.glow > MAX_STAND_OUT_GLOW) it.copy(glow = MAX_STAND_OUT_GLOW) else it }

    /** Relative luminance of an sRGB colour (0–1 channels). */
    fun luminance(r: Double, g: Double, b: Double): Double {
        fun lin(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }
}

/**
 * The colour sheet (DM-24 A2): both colours as they were when it opened, for Cancel. Dragging changes the settings at once; Cancel puts back [before]'s colours only, so a
 * change made elsewhere meanwhile (none, while the sheet is open) would stay.
 */
data class TextColourSheet(val before: TextLayerSettings) {
    /** The settings with the colours from when the sheet opened, everything else as now. */
    fun cancelled(now: TextLayerSettings): TextLayerSettings = now.copy(
        hue = before.hue, saturation = before.saturation, brightness = before.brightness,
        glowSameAsText = before.glowSameAsText, glowHue = before.glowHue,
        glowSaturation = before.glowSaturation, glowBrightness = before.glowBrightness,
    )

    /** Whether anything changed since it opened (the before/after preview shows both only then). */
    fun changed(now: TextLayerSettings): Boolean = cancelled(now) != now

    companion object {
        fun open(s: TextLayerSettings) = TextColourSheet(s)
    }
}
