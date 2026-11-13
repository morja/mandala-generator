package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextLayerTest {
    /** Draws every glyph as a filled box, so layout and compositing can be checked without fonts. */
    private class BoxRenderer : TextRenderer {
        val drawn = mutableListOf<PlacedLine>()
        override fun metrics(font: TextFont) = TextRenderer.Metrics(font.sizePx * 0.8f, font.sizePx * 0.2f, 0f)
        override fun width(text: String, font: TextFont) = text.length * font.sizePx * 0.5f
        override fun drawLines(width: Int, height: Int, lines: List<PlacedLine>): FloatArray {
            drawn += lines
            val mask = FloatArray(width * height)
            for (l in lines) {
                val top = (l.baseline - l.font.sizePx * 0.8f).toInt().coerceAtLeast(0)
                val bottom = l.baseline.toInt().coerceAtMost(height)
                val right = (l.x + width(l.text, l.font)).toInt().coerceAtMost(width)
                for (y in top until bottom) for (x in l.x.toInt().coerceAtLeast(0) until right) mask[y * width + x] = 1f
            }
            return mask
        }
    }

    private val params = MandalaParameters(
        textLayer = TextLayerSettings(isEnabled = true, text = "The state we call realization is simply being oneself.", customAuthor = "Ramana Maharshi"),
    )

    @Test
    fun wrapsTheQuoteAndAddsTheAuthorBelow() {
        val renderer = BoxRenderer()
        val result = MandalaRenderer.render(params, RenderOptions(outputSize = 256, threads = 2, textRenderer = renderer))
        assertTrue(result.warnings.isEmpty(), result.warnings.joinToString())
        val lines = renderer.drawn.distinctBy { it.text to it.baseline }
        val author = lines.last()
        assertEquals("— Ramana Maharshi", author.text)
        assertTrue(lines.size > 2, "expected the quote to wrap: ${lines.map { it.text }}")
        assertTrue(lines.dropLast(1).all { it.baseline < author.baseline })
    }

    @Test
    fun brightensTheImageWhereTheTextSits() {
        val plain = MandalaRenderer.render(params.copy(textLayer = params.textLayer.copy(isEnabled = false)), RenderOptions(outputSize = 256, threads = 2))
        val withText = MandalaRenderer.render(params, RenderOptions(outputSize = 256, threads = 2, textRenderer = BoxRenderer()))
        assertTrue(withText.image.data.sumOf { it.toInt() and 0xff } > plain.image.data.sumOf { it.toInt() and 0xff })
    }

    @Test
    fun sceneSmootherLeavesTextOnBlackExactlyAsSharpAsBefore() {
        val textOnly = MandalaParameters(
            layers = emptyList(),
            textLayer = TextLayerSettings(isEnabled = true, text = "Keep this clear.", showAuthor = false,
                fontSize = 0.15, hue = 0.6, saturation = 0.7, brightness = 0.6),
            effectsLayer = EffectsLayerSettings(isEnabled = false, vignette = 0.0, smoother = 1.0),
        )
        for (supersample in listOf(false, true)) {
            val options = RenderOptions(outputSize = 256, supersample = supersample, threads = 2, textRenderer = BoxRenderer())
            val sharp = MandalaRenderer.render(textOnly, options).image
            val smoothed = MandalaRenderer.render(textOnly.copy(effectsLayer = textOnly.effectsLayer.copy(isEnabled = true)), options).image
            assertTrue(sharp.data.contentEquals(smoothed.data), "Scene Smoother changed text, supersampling=$supersample")
        }
    }

    @Test
    fun textAndItsOwnEffectsAreCompositedAfterTheFinishedScene() {
        val scene = params.copy(
            textLayer = params.textLayer.copy(glow = 0.4, shadowOpacity = 0.5, cloudOpacity = 0.25,
                cloudBrightness = 0.1, blur = 0.3),
            effectsLayer = EffectsLayerSettings(isEnabled = true, smoother = 1.0, sepia = 0.7,
                grain = 0.2, relief = 0.3, chromatic = 0.4, bloom = 0.5, stars = 0.15),
        )
        val options = RenderOptions(outputSize = 256, supersample = false, threads = 2, textRenderer = BoxRenderer())
        val background = MandalaRenderer.render(scene.copy(textLayer = scene.textLayer.copy(isEnabled = false)), options).image
        val expected = Parallel(2).use { par ->
            TextLayer(par, BoxRenderer()).apply(background, scene.textLayer, TextFrame(0f, 0f, 256, 256, 256))
        }
        val shares = mutableListOf<Double>()
        val actual = MandalaRenderer.render(scene, options.copy(onProgress = { shares += it })).image
        assertTrue(expected.data.contentEquals(actual.data), "Scene effects were applied to the text or its own effects")
        assertEquals(1.0, shares.last())
        assertTrue(shares.zipWithNext().all { (a, b) -> b > a }, "Render progress went backwards: $shares")
    }

    /** Text on black with a strong glow (above 1, so the light passes run too) and a boost. */
    private fun glowing(text: TextLayerSettings) = MandalaParameters(
        layers = emptyList(),
        textLayer = text.copy(isEnabled = true, text = "Glow", showAuthor = false, fontSize = 0.2, glow = 2.0,
            hue = 0.6, saturation = 0.8, brightness = 1.3),
    )

    @Test
    fun theGlowColourFieldsChangeNothingWhileTheGlowIsTheSameAsTheText() {
        val options = RenderOptions(outputSize = 128, threads = 2, textRenderer = BoxRenderer())
        val plain = MandalaRenderer.render(glowing(TextLayerSettings()), options).image
        val stray = MandalaRenderer.render(glowing(TextLayerSettings(glowHue = 0.1, glowSaturation = 1.0, glowBrightness = 0.3)), options).image
        assertTrue(plain.data.contentEquals(stray.data))
        // Off, with the glow colour equal to the drawn text colour (its brightness capped at 1): the same pixels.
        val same = MandalaRenderer.render(glowing(TextLayerSettings(glowSameAsText = false, glowHue = 0.6, glowSaturation = 0.8, glowBrightness = 1.0)), options).image
        assertTrue(plain.data.contentEquals(same.data))
    }

    @Test
    fun anOwnGlowColourTintsOnlyWhereTheGlowReaches() {
        val options = RenderOptions(outputSize = 128, threads = 2, textRenderer = BoxRenderer())
        val plain = MandalaRenderer.render(glowing(TextLayerSettings()), options).image
        val red = MandalaRenderer.render(glowing(TextLayerSettings(glowSameAsText = false, glowHue = 0.0, glowSaturation = 1.0, glowBrightness = 1.0)), options).image
        assertTrue(!plain.data.contentEquals(red.data))
        // The corners lie beyond the glow: black in both.
        for (i in 0 until 3) assertEquals(plain.data[i], red.data[i])
        // Red rises where the blue glow was: more red in total.
        var redPlain = 0L
        var redOwn = 0L
        for (p in 0 until plain.width * plain.height) { redPlain += plain.get(p * 3); redOwn += red.get(p * 3) }
        assertTrue(redOwn > redPlain)
    }

    @Test
    fun warnsWithoutATextRenderer() {
        val result = MandalaRenderer.render(params, RenderOptions(outputSize = 128, threads = 1))
        assertTrue(result.warnings.any { "text" in it.lowercase() })
    }
}
