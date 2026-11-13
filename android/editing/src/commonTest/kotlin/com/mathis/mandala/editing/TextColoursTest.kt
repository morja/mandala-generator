package com.mathis.mandala.editing

import com.mathis.mandala.core.OwnPalettes
import com.mathis.mandala.core.TextLayerSettings
import com.mathis.mandala.editing.Randomize.randomized
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The text's two colours, the picture swatches, "Stand out" and the colour sheet (DM-24 A2). */
class TextColoursTest {
    private val kabirText = TextLayerSettings(isEnabled = true, text = "Dance", hue = 0.611, saturation = 1.0, brightness = 1.0)

    @Test
    fun theGlowFollowsTheTextByDefault() {
        val s = TextLayerSettings(hue = 0.3, saturation = 0.5, brightness = 0.8)
        assertTrue(s.glowSameAsText)
        assertEquals(TextColours.text(s), TextColours.glow(s))
    }

    @Test
    fun choosingAGlowColourSwitchesSameAsTextOffButATextColourDoesNot() {
        val glow = TextColours.withColour(kabirText, TextColourTarget.GLOW, Hsb(0.1, 0.8, 1.0))
        assertFalse(glow.glowSameAsText)
        assertEquals(Hsb(0.1, 0.8, 1.0), TextColours.glow(glow))
        assertEquals(TextColours.text(kabirText), TextColours.text(glow))

        val text = TextColours.withColour(kabirText, TextColourTarget.TEXT, Hsb(0.2, 0.3, 0.9))
        assertTrue(text.glowSameAsText)
        assertEquals(Hsb(0.2, 0.3, 0.9), TextColours.glow(text))
    }

    @Test
    fun sameAsTextOffStartsFromTheTextColourAndOnFollowsItAgain() {
        val off = TextColours.sameAsText(kabirText, false)
        assertFalse(off.glowSameAsText)
        assertEquals(TextColours.text(kabirText), TextColours.glow(off))
        val changed = TextColours.withColour(off, TextColourTarget.GLOW, Hsb(0.0, 1.0, 1.0))
        assertEquals(TextColours.text(changed), TextColours.glow(TextColours.sameAsText(changed, true)))
        // Switching off again keeps the chosen glow colour.
        assertEquals(changed, TextColours.sameAsText(changed, false))
    }

    @Test
    fun aBoostedTextBrightnessStaysButTheGlowIsCappedAtOne() {
        val bright = kabirText.copy(brightness = 1.6)
        assertEquals(1.6, TextColours.text(bright).brightness)
        assertEquals(1.0, TextColours.sameAsText(bright, false).glowBrightness)
        assertEquals(1.0, TextColours.withColour(bright, TextColourTarget.GLOW, Hsb(0.0, 1.0, 1.4)).glowBrightness)
    }

    @Test
    fun fieldAndHueStripSetTheirParts() {
        assertEquals(Hsb(0.5, 0.25, 0.75), TextColours.withFieldPoint(Hsb(0.5, 1.0, 1.0), 0.25, 0.25))
        assertEquals(Hsb(0.7, 0.8, 1.0), TextColours.withHueFromStrip(Hsb.WHITE, 0.7))
        assertEquals(Hsb(0.7, 0.5, 1.0), TextColours.withHueFromStrip(Hsb(0.1, 0.5, 1.0), 0.7))
        assertEquals(0.0, TextColours.withHueFromStrip(Hsb(0.1, 0.5, 1.0), 1.0).hue)
    }

    @Test
    fun cancelPutsBackBothColoursOnly() {
        val sheet = TextColourSheet.open(kabirText, TextColourTarget.GLOW)
        val dragged = TextColours.withColour(TextColours.withColour(kabirText, TextColourTarget.GLOW, Hsb(0.1, 1.0, 1.0)), TextColourTarget.TEXT, Hsb.WHITE)
            .copy(glow = 2.0)
        assertTrue(sheet.changed(dragged))
        val cancelled = sheet.cancelled(dragged)
        assertEquals(kabirText.copy(glow = 2.0), cancelled)
        assertFalse(sheet.changed(cancelled))
    }

    @Test
    fun swatchesMatchGreysAtAnyHue() {
        assertTrue(TextColours.matches(Hsb.WHITE, Hsb(0.4, 0.0, 1.0)))
        assertTrue(TextColours.matches(Hsb(0.999, 0.5, 1.0), Hsb(0.001, 0.5, 1.0)))
        assertFalse(TextColours.matches(Hsb(0.2, 0.5, 1.0), Hsb(0.3, 0.5, 1.0)))
    }

    @Test
    fun theDieLetsTheGlowFollowTheTextAgain() {
        val custom = TextColours.withColour(kabirText, TextColourTarget.GLOW, Hsb(0.1, 1.0, 1.0))
        assertTrue(custom.randomized(Random(3)).glowSameAsText)
    }

    // ---------- Picture colours ----------

    @Test
    fun pictureSwatchesLeaveOutDarkAndGreyAndEndWithWhite() {
        // Half dark, a quarter red, a fifth blue, a little grey.
        val px = IntArray(1000) { i ->
            when {
                i < 500 -> argb(0.05, 0.05, 0.08)
                i < 750 -> argb(0.9, 0.1, 0.1)
                i < 950 -> argb(0.1, 0.2, 0.9)
                else -> argb(0.6, 0.6, 0.6)
            }
        }
        val swatches = TextColours.pictureSwatches(px)
        assertEquals(Hsb.WHITE, swatches.last())
        assertEquals(3, swatches.size)
        assertTrue(swatches.all { it.brightness == 1.0 })
        assertTrue(TextColours.hueDistance(swatches[0].hue, 0.0) < 0.02)
        assertTrue(TextColours.hueDistance(swatches[1].hue, hueOf(0.1, 0.2, 0.9)) < 0.02)
    }

    @Test
    fun pictureSwatchesAreAtMostSevenHuesApart() {
        val px = IntArray(4800) { i -> hsbArgb((i / 100) / 48.0, 0.9, 0.9) }
        val swatches = TextColours.pictureSwatches(px)
        assertEquals(TextColours.PICTURE_SWATCHES + 1, swatches.size)
        val colours = swatches.dropLast(1)
        for (a in colours) for (b in colours) if (a !== b) assertTrue(TextColours.hueDistance(a.hue, b.hue) >= TextColours.SWATCH_HUE_GAP)
    }

    // ---------- "Stand out" ----------

    @Test
    fun standOutTakesThePictureColourNearTheComplementAndANearWhiteText() {
        // Blue lines on black behind the text, as in Mathis' Kabir file; an orange in the picture.
        val (px, w, h) = picture { x, _ -> if (x % 4 == 0) hsbArgb(0.6, 0.9, 0.9) else argb(0.02, 0.02, 0.03) }
        val orange = Hsb(0.125, 0.75, 1.0)
        val r = assertNotNull(TextColours.standOut(px, w, h, TextColours.textArea(kabirText), listOf(Hsb(0.55, 0.9, 1.0), orange, Hsb.WHITE)))
        assertTrue(TextColours.hueDistance(r.dominantHue, 0.6) < 0.01)
        assertTrue(r.clarity > 0.9)
        assertEquals(orange, r.glow)
        assertEquals(Hsb(orange.hue, 0.12, 1.0), r.text)
        assertTrue(r.contrast >= TextColours.MIN_CONTRAST)

        val s = TextColours.applyStandOut(kabirText.copy(glow = 0.41, cloudOpacity = 0.2, shadowOpacity = 0.3), r)
        assertFalse(s.glowSameAsText)
        assertEquals(0.41, s.glow)
        assertEquals(0.2, s.cloudOpacity)
        assertEquals(0.3, s.shadowOpacity)
        assertEquals(r.text, TextColours.text(s))
        assertEquals(r.glow, TextColours.glow(s))
    }

    @Test
    fun standOutUsesTheComplementWhenNoPictureColourIsNearAndRaisesAPaleGlow() {
        val (px, w, h) = picture { _, _ -> hsbArgb(0.6, 0.9, 0.5) }
        val far = TextColours.standOut(px, w, h, TextColours.textArea(kabirText), listOf(Hsb(0.55, 0.9, 1.0), Hsb.WHITE))!!
        assertTrue(TextColours.hueDistance(far.glow.hue, 0.1) < 0.01)
        assertEquals(TextColours.COMPLEMENT_SATURATION, far.glow.saturation)

        val pale = TextColours.standOut(px, w, h, TextColours.textArea(kabirText), listOf(Hsb(0.08, 0.3, 1.0), Hsb.WHITE))!!
        assertEquals(Hsb(0.08, TextColours.MIN_GLOW_SATURATION, 1.0), pale.glow)
    }

    @Test
    fun standOutTakesTheFarthestPictureColourWhenTheBackgroundHasNoClearHue() {
        // Red, green and blue in equal parts: no hue wins.
        val (px, w, h) = picture { x, _ -> listOf(argb(0.9, 0.0, 0.0), argb(0.0, 0.9, 0.0), argb(0.0, 0.0, 0.9))[x % 3] }
        val r = TextColours.standOut(px, w, h, doubleArrayOf(0.0, 0.0, 0.99, 1.0), listOf(Hsb(0.1, 0.9, 1.0), Hsb(0.4, 0.9, 1.0), Hsb.WHITE))!!
        assertTrue(r.clarity < TextColours.CLEAR_HUE)
        val expected = listOf(Hsb(0.1, 0.9, 1.0), Hsb(0.4, 0.9, 1.0)).maxBy { TextColours.hueDistance(it.hue, r.dominantHue) }
        assertEquals(expected, r.glow)
    }

    @Test
    fun standOutFallsBackToWhiteLettersOnABrightBackground() {
        val (px, w, h) = picture { _, _ -> argb(0.85, 0.85, 0.6) }
        val r = TextColours.standOut(px, w, h, TextColours.textArea(kabirText), listOf(Hsb(0.6, 0.9, 1.0), Hsb.WHITE))!!
        assertEquals(0.0, r.text.saturation)
        assertEquals(1.0, r.text.brightness)
    }

    @Test
    fun standOutGivesNothingForAnEmptyArea() {
        val (px, w, h) = picture { _, _ -> argb(0.5, 0.5, 0.5) }
        assertEquals(null, TextColours.standOut(px, w, h, doubleArrayOf(0.5, 0.5, 0.5, 0.9), listOf(Hsb.WHITE)))
    }

    @Test
    fun theTextAreaFollowsTheBoxAndTheHeight() {
        val a = TextColours.textArea(TextLayerSettings(fontWidth = 0.8, offsetX = 0.5, offsetY = 0.6))
        assertEquals(0.1, a[0], 1e-9)
        assertEquals(0.9, a[2], 1e-9)
        assertEquals(0.27, a[1], 1e-9)
        assertEquals(0.53, a[3], 1e-9)
    }

    // ---------- Helpers ----------

    private fun argb(r: Double, g: Double, b: Double): Int =
        (0xFF shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()

    private fun hsbArgb(h: Double, s: Double, v: Double): Int {
        val rgb = DoubleArray(3)
        OwnPalettes.hsbToSrgb(h, s, v, rgb)
        return argb(rgb[0], rgb[1], rgb[2])
    }

    private fun hueOf(r: Double, g: Double, b: Double): Double {
        val out = DoubleArray(3)
        OwnPalettes.srgbToHsb(r, g, b, out)
        return out[0]
    }

    private fun picture(pixel: (Int, Int) -> Int): Triple<IntArray, Int, Int> {
        val w = 60
        val h = 60
        return Triple(IntArray(w * h) { pixel(it % w, it / w) }, w, h)
    }
}
