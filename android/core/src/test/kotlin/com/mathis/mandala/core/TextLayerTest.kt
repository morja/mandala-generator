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
        override fun drawLines(size: Int, lines: List<PlacedLine>): FloatArray {
            drawn += lines
            val mask = FloatArray(size * size)
            for (l in lines) {
                val top = (l.baseline - l.font.sizePx * 0.8f).toInt().coerceAtLeast(0)
                val bottom = l.baseline.toInt().coerceAtMost(size)
                val right = (l.x + width(l.text, l.font)).toInt().coerceAtMost(size)
                for (y in top until bottom) for (x in l.x.toInt().coerceAtLeast(0) until right) mask[y * size + x] = 1f
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
    fun warnsWithoutATextRenderer() {
        val result = MandalaRenderer.render(params, RenderOptions(outputSize = 128, threads = 1))
        assertTrue(result.warnings.any { "text" in it.lowercase() })
    }
}
