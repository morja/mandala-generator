package com.mathis.mandala.cli

import com.mathis.mandala.core.Encoding
import com.mathis.mandala.core.Image8
import com.mathis.mandala.core.ImageLoader
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import javax.imageio.ImageIO

/**
 * Reads `file:` URLs (as the macOS app stores them) with ImageIO and stretches them to the square.
 * Plain paths, as the fixtures use, count from `base`.
 */
class FileImageLoader(private val base: File = File(".")) : ImageLoader {
    override fun load(url: String, size: Int): Image8? {
        val file = runCatching { if (url.startsWith("file:")) File(URI(url)) else File(base, url) }.getOrNull() ?: return null
        val src = runCatching { ImageIO.read(file) }.getOrNull() ?: return null
        val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        scaled.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            drawImage(src, 0, 0, size, size, null)
            dispose()
        }
        val out = Image8(size, size, Encoding.SRGB)
        for (y in 0 until size) for (x in 0 until size) {
            val rgb = scaled.getRGB(x, y)
            val o = (y * size + x) * 3
            out.data[o] = (rgb shr 16).toByte()
            out.data[o + 1] = (rgb shr 8).toByte()
            out.data[o + 2] = rgb.toByte()
        }
        return out
    }
}
