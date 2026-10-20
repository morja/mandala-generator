package com.mathis.mandala.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.mathis.mandala.core.PlacedLine
import com.mathis.mandala.core.TextFont
import com.mathis.mandala.core.TextRenderer

/**
 * Text for the text layer via Android's canvas. Android has none of the macOS fonts, so each is
 * mapped to the closest system family (serif, sans, cursive, small caps, monospace).
 */
class AndroidTextRenderer : TextRenderer {

    private fun paint(font: TextFont): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val family = when {
            font.name.startsWith("Copperplate") -> Typeface.create("serif", Typeface.NORMAL)
            font.name.startsWith("Papyrus") -> Typeface.create("casual", Typeface.NORMAL)
            font.family == TextFont.Family.SCRIPT -> Typeface.create("cursive", Typeface.NORMAL)
            font.family == TextFont.Family.SANS -> Typeface.SANS_SERIF
            font.family == TextFont.Family.MONO -> Typeface.MONOSPACE
            else -> Typeface.SERIF
        }
        typeface = if (font.italic) Typeface.create(family, Typeface.ITALIC) else family
        textSize = font.sizePx
        letterSpacing = if (font.sizePx > 0f) font.trackingPx / font.sizePx else 0f
        if (font.name.startsWith("Copperplate")) {
            // Copperplate is all capitals; small caps get closest where the feature is missing.
            fontFeatureSettings = "smcp"
        }
        color = android.graphics.Color.WHITE
    }

    override fun metrics(font: TextFont): TextRenderer.Metrics {
        val m = paint(font).fontMetrics
        return TextRenderer.Metrics(-m.ascent, m.descent, m.leading)
    }

    override fun width(text: String, font: TextFont): Float = paint(font).measureText(text)

    override fun drawLines(size: Int, lines: List<PlacedLine>): FloatArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        for (line in lines) canvas.drawText(line.text, line.x, line.baseline, paint(line.font))
        val bytes = java.nio.ByteBuffer.allocate(size * size)
        bitmap.copyPixelsToBuffer(bytes)
        bitmap.recycle()
        val raw = bytes.array()
        return FloatArray(size * size) { (raw[it].toInt() and 0xFF) / 255f }
    }
}
