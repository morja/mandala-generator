package com.mathis.mandala.cli

import com.mathis.mandala.core.PlacedLine
import com.mathis.mandala.core.TextFont
import com.mathis.mandala.core.TextRenderer
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.image.BufferedImage

/** Text for the JVM command via Java2D. On a Mac the macOS fonts are available by name. */
class AwtTextRenderer : TextRenderer {
    private val installed: Set<String> by lazy {
        val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
        (env.availableFontFamilyNames + env.allFonts.map { it.fontName } + env.allFonts.map { it.psName }).toSet()
    }
    private val scratch = BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY).createGraphics()

    private fun awtFont(f: TextFont): Font {
        val base = listOf(f.name, f.name.substringBefore('-'), f.name.replace(Regex("([a-z])([A-Z])"), "$1 $2").substringBefore('-'))
            .firstOrNull { it in installed }
            ?: when (f.family) {
                TextFont.Family.SANS -> Font.SANS_SERIF
                TextFont.Family.MONO -> Font.MONOSPACED
                else -> Font.SERIF
            }
        val attrs = HashMap<TextAttribute, Any>()
        attrs[TextAttribute.FAMILY] = Font(base, Font.PLAIN, 1).family
        attrs[TextAttribute.SIZE] = f.sizePx
        if (f.italic) attrs[TextAttribute.POSTURE] = TextAttribute.POSTURE_OBLIQUE
        if (f.trackingPx != 0f) attrs[TextAttribute.TRACKING] = f.trackingPx / f.sizePx
        return Font(base, Font.PLAIN, 1).deriveFont(attrs)
    }

    override fun metrics(font: TextFont): TextRenderer.Metrics {
        val m = scratch.getFontMetrics(awtFont(font))
        return TextRenderer.Metrics(m.ascent.toFloat(), m.descent.toFloat(), m.leading.toFloat())
    }

    override fun width(text: String, font: TextFont): Float =
        awtFont(font).getStringBounds(text, scratch.fontRenderContext).width.toFloat()

    override fun drawLines(width: Int, height: Int, lines: List<PlacedLine>): FloatArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.color = java.awt.Color.WHITE
        for (line in lines) {
            g.font = awtFont(line.font)
            g.drawString(line.text, line.x, line.baseline)
        }
        g.dispose()
        val raster = img.raster
        val out = FloatArray(width * height)
        val row = IntArray(width)
        for (y in 0 until height) {
            raster.getSamples(0, y, width, 1, 0, row)
            for (x in 0 until width) out[y * width + x] = row[x] / 255f
        }
        return out
    }
}
