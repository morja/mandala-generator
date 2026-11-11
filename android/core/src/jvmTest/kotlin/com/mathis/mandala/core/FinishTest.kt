package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The last adjustment of a saved image or wallpaper (ADR-0014). */
class FinishTest {
    private fun pixel(r: Int, g: Int, b: Int) = Image8(1, 1, Encoding.SRGB, byteArrayOf(r.toByte(), g.toByte(), b.toByte()))

    private fun Image8.rgb() = Triple(get(0), get(1), get(2))

    @Test
    fun theIdentityLeavesTheImageAlone() {
        val img = pixel(10, 200, 90)
        assertSame(img, Finish().apply(img))
    }

    @Test
    fun brightnessContrastAndSaturationDoWhatTheySay() {
        assertEquals(Triple(51, 102, 25), Finish(brightness = 0.5).apply(pixel(102, 204, 50)).rgb())
        // Contrast pulls away from mid grey, saturation 0 gives the luma.
        val (r, _, b) = Finish(contrast = 1.5).apply(pixel(64, 128, 192)).rgb()
        assertTrue(r < 64 && b > 192, "contrast $r, $b")
        val (gr, gg, gb) = Finish(saturation = 0.0).apply(pixel(255, 0, 0)).rgb()
        assertTrue(gr == gg && gg == gb && gr in 50..56, "grey $gr")
    }

    @Test
    fun aRenderTakesTheFinishAsItsLastStep() {
        val params = SettingsJson.decode("""{"layers":[{"style":"roseCurves","seed":3}]}""")
        val plain = MandalaRenderer.render(params, RenderOptions(outputSize = 64, threads = 2)).image
        val dimmed = MandalaRenderer.render(params, RenderOptions(outputSize = 64, threads = 2, finish = Finish(brightness = 0.3))).image
        val expected = Finish(brightness = 0.3).apply(plain)
        assertTrue(dimmed.data.contentEquals(expected.data))
    }
}
