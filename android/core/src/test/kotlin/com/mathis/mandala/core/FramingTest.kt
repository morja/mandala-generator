package com.mathis.mandala.core

import kotlin.math.abs
import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Canvases that are not square and the mandala placed on them (ADR-0010). */
class FramingTest {
    private val oneLayer = SettingsJson.decode(
        """{"layers":[{"style":"roseCurves","scale":0.9,"glowIntensity":0.6,"wash":0.2,"seed":3}],
           "baseLayer":{"isEnabled":true,"type":"gradient","isRadial":true}}""",
    )

    private fun render(params: MandalaParameters, options: RenderOptions) = MandalaRenderer.render(params, options.copy(threads = 2)).image

    private fun pixel(img: Image8, x: Int, y: Int, c: Int) = img.get((y * img.width + x) * 3 + c)

    /**
     * Not byte-identical: on the taller canvas the layer is drawn as far as its style reaches, and
     * its glow is blurred on that larger buffer, on a slightly different reference grid (68 dB).
     */
    @Test
    fun portraitWithTheDefaultFramingHasTheSquareImageInItsMiddle() {
        val square = render(oneLayer, RenderOptions(outputSize = 256, supersample = false))
        val portrait = render(oneLayer, RenderOptions(width = 256, height = 400, supersample = false))
        assertEquals(256, portrait.width)
        assertEquals(400, portrait.height)
        val top = (400 - 256) / 2
        var squared = 0.0
        for (y in 0 until 256) for (x in 0 until 256) for (c in 0..2) {
            val d = pixel(square, x, y, c) - pixel(portrait, x, y + top, c)
            squared += d * d
        }
        val psnr = 10 * log10(255.0 * 255.0 / (squared / (256 * 256 * 3)))
        assertTrue(psnr > 60, "middle of the portrait at $psnr dB")
    }

    /** The edge of a layer's buffer must not show on a canvas taller than the mandala (Star Burst reaches past its radius). */
    @Test
    fun aPortraitShowsNoBufferEdgeAboveTheMandala() {
        val rays = SettingsJson.decode("""{"layers":[{"style":"starBurst","complexity":1,"density":1,"glowIntensity":0,"wash":0,"abstractLevel":0,"seed":5}],"baseLayer":{"isEnabled":false}}""")
        val img = render(rays, RenderOptions(width = 200, height = 400, supersample = false))
        // Rows just above and below where the centred square (and its margin) ended: rays go on.
        fun lit(y: Int) = (0 until 200).count { x -> pixel(img, x, y, 0) + pixel(img, x, y, 1) + pixel(img, x, y, 2) > 6 }
        assertTrue(lit(92) > 0 && lit(308) > 0, "no rays beyond the square: ${lit(92)} above, ${lit(308)} below")
    }

    @Test
    fun zoomShowsTheMiddleOfALargerMandala() {
        // Without glow or background the lines alone can be compared; positions differ only by whole pixels.
        val lines = SettingsJson.decode("""{"layers":[{"style":"roseCurves","scale":0.9,"glowIntensity":0,"seed":3}]}""")
        val zoomed = render(lines, RenderOptions(outputSize = 200, supersample = false, framing = Framing(zoom = 2.0)))
        val large = render(lines, RenderOptions(outputSize = 400, supersample = false))
        var diff = 0L
        var lit = 0L
        for (y in 0 until 200) for (x in 0 until 200) for (c in 0..2) {
            val a = pixel(zoomed, x, y, c)
            diff += abs(a - pixel(large, x + 100, y + 100, c))
            lit += a
        }
        assertTrue(lit > 200 * 200, "expected visible lines")
        assertTrue(diff < lit / 50, "zoomed $lit, difference $diff")
    }

    @Test
    fun aRadialBackgroundSitsAtTheMandalaCentre() {
        val background = SettingsJson.decode("""{"layers":[],"baseLayer":{"isEnabled":true,"type":"gradient","isRadial":true}}""")
        val high = render(background, RenderOptions(width = 100, height = 400, supersample = false, framing = Framing(centreY = 0.3)))
        val low = render(background, RenderOptions(width = 100, height = 400, supersample = false, framing = Framing(centreY = 0.7)))
        val centred = render(background, RenderOptions(width = 100, height = 400, supersample = false))
        // Centres at 120 and 280 px mirror each other; neither is the centred background.
        for (y in 0 until 400) for (x in 0 until 100) assertEquals(pixel(high, x, y, 0), pixel(low, x, 399 - y, 0), "pixel $x,$y")
        assertTrue((0 until 400).any { pixel(high, 50, it, 0) != pixel(centred, 50, it, 0) })
    }

    @Test
    fun fillZoomCoversTheCornersWithTheLargeLayers() {
        val phone = 2340.0 / 1080
        fun zoom(json: String) = MandalaRenderer.fillZoom(SettingsJson.decode(json), phone)
        // Star Burst and Hyperboloid at full size: Star Burst reaches less far, so it sets the zoom (about 1.6).
        val starAndHyper = zoom("""{"layers":[{"style":"starBurst"},{"style":"hyperboloid"}]}""")
        assertTrue(starAndHyper in 1.55..1.65, "zoom $starAndHyper")
        // A small accent in the middle does not drive the zoom.
        assertEquals(starAndHyper, zoom("""{"layers":[{"style":"starBurst"},{"style":"hyperboloid"},{"style":"roseCurves","scale":0.3}]}"""))
        // Deep Nebula fills any canvas already; a small mandala zooms at most to the longer side.
        assertEquals(1.0, zoom("""{"layers":[{"style":"deepNebula"}]}"""))
        assertEquals(phone, zoom("""{"layers":[{"style":"spirograph","scale":0.2}]}"""), 1e-9)
        assertEquals(1.0, MandalaRenderer.fillZoom(SettingsJson.decode("""{"layers":[{"style":"starBurst"}]}"""), 1.0))
    }

    @Test
    fun aMandalaMostlyOffTheCanvasStillRenders() {
        val img = render(oneLayer, RenderOptions(width = 120, height = 200, framing = Framing(centreX = 1.0, centreY = 0.0, zoom = 0.25)))
        assertEquals(120, img.width)
        assertEquals(200, img.height)
        val clamped = Framing(centreX = 2.0, centreY = -1.0, zoom = 9.0).clamped()
        assertEquals(Framing(1.0, 0.0, Framing.MAX_ZOOM), clamped)
    }

    @Test
    fun theQuoteStaysOnTheCanvasOrFollowsTheMandala() {
        val params = oneLayer.copy(textLayer = TextLayerSettings(isEnabled = true, text = "Be still.", offsetY = 0.2))
        fun baseline(follow: Boolean, framing: Framing): Float {
            val renderer = BoxRenderer()
            render(params, RenderOptions(width = 200, height = 400, supersample = false, framing = framing, textFollowsMandala = follow, textRenderer = renderer))
            return renderer.drawn.first().baseline
        }
        val stays = baseline(false, Framing())
        val follows = baseline(true, Framing())
        // Staying in place, offsetY 0.2 is a fifth of the canvas height from the bottom; following,
        // a fifth of the mandala square (200 px, centred) from its bottom.
        assertTrue(stays > 400 * 0.7f, "stays at $stays")
        assertTrue(follows > 300 * 0.7f && follows < 300f, "follows at $follows")
        assertEquals(stays, baseline(false, Framing(centreY = 0.3)))
        assertEquals(follows - 40f, baseline(true, Framing(centreY = 0.4)), 0.01f)
    }

    /** Draws every glyph as a filled box. */
    private class BoxRenderer : TextRenderer {
        val drawn = mutableListOf<PlacedLine>()
        override fun metrics(font: TextFont) = TextRenderer.Metrics(font.sizePx * 0.8f, font.sizePx * 0.2f, 0f)
        override fun width(text: String, font: TextFont) = text.length * font.sizePx * 0.5f
        override fun drawLines(width: Int, height: Int, lines: List<PlacedLine>): FloatArray {
            drawn += lines
            return FloatArray(width * height)
        }
    }
}
