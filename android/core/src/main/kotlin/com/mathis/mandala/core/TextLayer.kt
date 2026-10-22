package com.mathis.mandala.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A font request. `name` is a macOS font name; platforms map it to what they have. */
data class TextFont(val name: String, val sizePx: Float, val italic: Boolean, val trackingPx: Float) {
    /** Rough family of a macOS font name, for platforms that lack the font. */
    val family: Family
        get() = when {
            name.startsWith("Zapfino") -> Family.SCRIPT
            name.startsWith("Futura") || name.startsWith("Gill") || name.startsWith("Optima") ||
                name.startsWith("Helvetica") || name.startsWith("Avenir") -> Family.SANS
            name.startsWith("American") -> Family.MONO
            name.startsWith("Papyrus") || name.startsWith("Copperplate") -> Family.DISPLAY
            else -> Family.SERIF
        }

    enum class Family { SERIF, SANS, SCRIPT, MONO, DISPLAY }
}

/** One laid-out line of text: left edge and baseline in pixels (y down). */
data class PlacedLine(val text: String, val font: TextFont, val x: Float, val baseline: Float)

/** Platform text drawing. Core keeps line breaking and placement; the platform supplies fonts. */
interface TextRenderer {
    class Metrics(val ascent: Float, val descent: Float, val leading: Float)

    fun metrics(font: TextFont): Metrics

    fun width(text: String, font: TextFont): Float

    /** Draws the lines into a coverage mask (0..1, width x height, row 0 at the top). */
    fun drawLines(width: Int, height: Int, lines: List<PlacedLine>): FloatArray
}

/**
 * Where the text is laid out on the canvas: the rectangle (x, y, width, height) whose vertical
 * position setting it follows, and `scale`, the side that font size and line width are measured
 * on. The whole canvas with its shorter side, or the mandala's square (ADR-0010).
 */
internal class TextFrame(val x: Float, val y: Float, val width: Int, val height: Int, val scale: Int)

/**
 * Port of applyTextLayer: cloud, shadow passes, then the text with blur, glow, brightness boost and
 * opacity, blended with the chosen mode. The text is one colour, so everything is kept as single-
 * channel coverage masks and coloured only when compositing. Fonts differ by platform, so the result
 * matches the macOS look, not its exact pixels.
 */
internal class TextLayer(private val par: Parallel, private val renderer: TextRenderer) {

    private class Block(val quote: List<PlacedLine>, val author: List<PlacedLine>, val centreX: Float, val centreYDown: Float, val fontSize: Float)

    fun apply(image: Image8, s: TextLayerSettings, frame: TextFrame): Image8 {
        val w = image.width
        val h = image.height
        val size = frame.scale
        val block = layout(s, frame, 0f, 0f)
        var out = image

        if (s.cloudOpacity > 0.01) {
            val textHalf = block.fontSize * 0.8f
            val fullRadius = size * 0.78f
            val circR = textHalf + s.cloudScale.toFloat() * (fullRadius - textHalf)
            val mask = FloatArray(w * h)
            // Solid to half the radius, then fading to nothing at the edge (NSGradient stops 0, 0.5, 1).
            for (y in 0 until h) for (x in 0 until w) {
                val dx = x + 0.5f - block.centreX
                val dy = y + 0.5f - block.centreYDown
                val t = sqrt(dx * dx + dy * dy) / circR
                mask[y * w + x] = when {
                    t <= 0.5f -> 1f
                    t >= 1f -> 0f
                    else -> 1f - (t - 0.5f) / 0.5f
                } * s.cloudOpacity.toFloat()
            }
            MaskBlur.blur(mask, w, h, (circR * s.cloudRadius.toFloat() * 0.35f + block.fontSize * 0.3f).toDouble(), par)
            out = sourceOver(out, mask, hsbColour(s.cloudHue, s.cloudSaturation, s.cloudBrightness))
        }

        if (s.shadowOpacity > 0.01) {
            val offX = (s.shadowOffsetX * block.fontSize * 0.75).toFloat()
            val offY = (s.shadowOffsetY * block.fontSize * 0.75).toFloat()
            val shadow = layout(s, frame, offX, -offY)
            val mask = renderer.drawLines(w, h, shadow.quote + shadow.author)
            MaskBlur.blur(mask, w, h, max(0.5, s.shadowBlur * block.fontSize * 0.4), par)
            val colour = hsbColour(s.shadowHue, s.shadowSaturation, s.shadowBrightness)
            repeat(max(1, (s.shadowOpacity * 4 + 0.5).toInt())) { out = sourceOver(out, mask, colour) }
        }

        val text = renderer.drawLines(w, h, block.quote + block.author)
        if (s.blur > 0.005) MaskBlur.blur(text, w, h, s.blur * s.blur * size * 0.025, par)
        var glow: FloatArray? = null
        if (s.glow > 0.005) {
            // Glow radii are given at a 1600 px reference width.
            val g = FloatArray(w * h)
            val k = size / 1600.0
            // Up to 1 as always; above 1 (slider up to 3) this part stays at its value for 1 (except with
            // Multiply) and the wide passes add light on top at the end, as on macOS.
            val gl = if (s.blendMode == LayerBlendMode.MULTIPLY) s.glow else min(1.0, s.glow)
            for ((radius, strength) in listOf(30.0 * gl to 0.9 * gl, 105.0 * gl to 0.5 * gl, 210.0 * gl to 0.2 * gl)) {
                val pass = text.copyOf()
                MaskBlur.blur(pass, w, h, radius * k, par)
                val st = strength.toFloat()
                for (i in g.indices) g[i] += pass[i] * st
            }
            glow = g
        }
        val colour = hsbColour(s.hue, s.saturation, min(1.0, s.brightness))
        val boost = if (s.brightness > 1.005) s.brightness.toFloat() else 1f
        val opacity = if (s.opacity < 0.999) s.opacity.toFloat() else 1f
        val blended = blendText(out, text, glow, colour, boost * opacity, s.blendMode)
        if (s.glow <= 1.0 || s.blendMode == LayerBlendMode.MULTIPLY) return blended
        // Glow above 1: the two wide passes of the glow at 1 as light, growing from nothing at 1
        // (macOS textGlowLightPasses). The macOS light source is the boosted text with its glow at 1
        // as an 8-bit image, so the boost saturates at white.
        val source = FloatArray(w * h) { val ta = text[it].coerceIn(0f, 1f); val ga = glow?.get(it) ?: 0f; (ta + ga - ta * ga).coerceIn(0f, 1f) }
        val light = FloatArray(w * h)
        val k = size / 1600.0
        val gain = (s.glow - 1) * opacity
        for ((radius, strength) in listOf(105.0 to 0.75 * gain, 210.0 to 0.3 * gain)) {
            val pass = source.copyOf()
            MaskBlur.blur(pass, w, h, radius * k, par)
            val st = strength.toFloat()
            for (i in light.indices) light[i] += pass[i] * st
        }
        val lightColour = FloatArray(3) { min(1f, colour[it] * boost) }
        return mapPixels(blended) { p, c ->
            for (ch in 0..2) {
                val g = lightColour[ch] * light[p]
                c[ch] = c[ch] + g - c[ch] * g
            }
        }
    }

    /** Lays out quote and author like renderTextToCGImage (block centred at offsetY of the frame, y up). */
    private fun layout(s: TextLayerSettings, frame: TextFrame, shiftX: Float, shiftYDown: Float): Block {
        val size = frame.scale
        val fs = (s.fontSize * size).toFloat()
        val font = TextFont(s.fontName, fs, false, (s.tracking * fs * 0.12).toFloat())
        val maxWidth = size * max(0.05f, s.fontWidth.toFloat())
        val author = if (s.showAuthor) s.customAuthor.ifEmpty { Quotes.authorOf(s.text) ?: "" }.takeIf { it.isNotEmpty() } else null
        val authorFont = font.copy(sizePx = fs * s.authorScale.toFloat(), italic = s.authorItalic)

        val quoteLines = wrap(s.text, font, maxWidth)
        val authorLines = author?.let { wrap("— $it", authorFont, maxWidth) } ?: emptyList()
        val quoteH = blockHeight(quoteLines.size, font)
        val authorH = if (author != null) blockHeight(authorLines.size, authorFont) else 0f
        val lineGap = if (author != null) fs * 0.75f else 0f
        val totalHeight = quoteH + lineGap + authorH
        val drawX = frame.x + (frame.width - maxWidth) / 2f
        val centreY = s.offsetY.toFloat() * frame.height
        val blockBottom = centreY - totalHeight / 2f
        val quoteY = blockBottom + authorH + lineGap
        // AppKit draws from the top of the rectangle (height plus a little padding), y up.
        val quoteTop = frame.y + (frame.height - (quoteY + quoteH + fs * 0.3f))
        val authorTop = frame.y + (frame.height - (blockBottom + authorH + fs * 0.2f))
        return Block(
            place(quoteLines, font, drawX + shiftX, quoteTop + shiftYDown, maxWidth),
            place(authorLines, authorFont, drawX + shiftX, authorTop + shiftYDown, maxWidth),
            drawX + maxWidth / 2, frame.y + (frame.height - (blockBottom + totalHeight / 2)), fs,
        )
    }

    private fun lineSpacing(font: TextFont) = font.sizePx * 0.18f

    private fun lineHeight(font: TextFont): Float = renderer.metrics(font).let { it.ascent + it.descent + it.leading }

    private fun blockHeight(lines: Int, font: TextFont): Float =
        if (lines == 0) 0f else lines * lineHeight(font) + (lines - 1) * lineSpacing(font)

    private fun place(lines: List<String>, font: TextFont, x: Float, top: Float, maxWidth: Float): List<PlacedLine> {
        val m = renderer.metrics(font)
        return lines.mapIndexed { i, line ->
            val w = renderer.width(line, font)
            PlacedLine(line, font, x + (maxWidth - w) / 2f, top + m.ascent + i * (lineHeight(font) + lineSpacing(font)))
        }
    }

    /** Greedy word wrap; words wider than the box are split. */
    private fun wrap(text: String, font: TextFont, maxWidth: Float): List<String> {
        val out = ArrayList<String>()
        for (paragraph in text.split('\n')) {
            var line = ""
            for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (renderer.width(candidate, font) <= maxWidth || line.isEmpty()) {
                    line = candidate
                } else {
                    out += line
                    line = word
                }
            }
            out += line
        }
        return out.filter { it.isNotEmpty() }
    }

    private fun hsbColour(h: Double, s: Double, b: Double): FloatArray =
        hsbToRgb(h, s, b).let { floatArrayOf(it.r.toFloat(), it.g.toFloat(), it.b.toFloat()) }

    /** Premultiplied source-over of a single-colour mask onto an 8-bit image (sRGB values). */
    private fun sourceOver(image: Image8, mask: FloatArray, colour: FloatArray): Image8 =
        mapPixels(image) { p, c -> val a = mask[p].coerceIn(0f, 1f); for (k in 0..2) c[k] = colour[k] * a + c[k] * (1 - a) }

    /** Text plus glow (screened together), scaled by brightness boost and opacity, then blended. */
    private fun blendText(image: Image8, text: FloatArray, glow: FloatArray?, colour: FloatArray, scale: Float, mode: LayerBlendMode): Image8 =
        mapPixels(image) { p, c ->
            val ta = text[p].coerceIn(0f, 1f)
            val ga = glow?.get(p) ?: 0f
            val alpha = (ta + ga - ta * ga).coerceIn(0f, 1f)
            for (k in 0..2) {
                val t = colour[k] * ta
                val g = colour[k] * ga
                val premult = (t + g - t * g) * scale
                c[k] = when (mode) {
                    LayerBlendMode.SCREEN -> c[k] + premult - c[k] * premult
                    LayerBlendMode.ADD -> c[k] + premult
                    LayerBlendMode.NORMAL -> premult + c[k] * (1 - alpha)
                    LayerBlendMode.MULTIPLY -> premult * c[k] + c[k] * (1 - alpha)
                }
            }
        }

    private inline fun mapPixels(image: Image8, crossinline f: (Int, FloatArray) -> Unit): Image8 {
        val out = if (image.encoding == Encoding.SRGB) image else WorkingSpace.convertToSrgb(image)
        val w = out.width
        par.forRange(out.height) { from, until ->
            val c = FloatArray(3)
            for (y in from until until) for (x in 0 until w) {
                val p = y * w + x
                val o = p * 3
                c[0] = out.get(o) / 255f; c[1] = out.get(o + 1) / 255f; c[2] = out.get(o + 2) / 255f
                f(p, c)
                out.data[o] = WorkingSpace.quantize(c[0]); out.data[o + 1] = WorkingSpace.quantize(c[1]); out.data[o + 2] = WorkingSpace.quantize(c[2])
            }
        }
        return out
    }

}
