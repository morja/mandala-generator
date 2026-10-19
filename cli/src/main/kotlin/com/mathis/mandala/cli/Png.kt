package com.mathis.mandala.cli

import com.mathis.mandala.core.FloatImage
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.log10

/** PNG input/output for the JVM command. Output is 8-bit sRGB. */
object Png {
    fun write(image: FloatImage, file: File) {
        val img = image.copy().toSrgbInPlace()
        val bi = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
        bi.setRGB(0, 0, img.width, img.height, img.toArgbPixels(), 0, img.width)
        file.absoluteFile.parentFile?.mkdirs()
        ImageIO.write(bi, "png", file)
    }

    /** Reads raw 8-bit pixel values, ignoring any embedded colour profile. */
    fun read(file: File): FloatImage {
        val bi = ImageIO.read(file) ?: error("Cannot read image: $file")
        val w = bi.width
        val h = bi.height
        val out = FloatImage(w, h)
        val raster = bi.raster
        val px = IntArray(raster.numBands)
        for (y in 0 until h) {
            for (x in 0 until w) {
                raster.getPixel(x, y, px)
                val o = (y * w + x) * 3
                out.data[o] = px[0] / 255f
                out.data[o + 1] = px[1] / 255f
                out.data[o + 2] = px[2] / 255f
            }
        }
        return out
    }
}

/** Difference between two images in 8-bit units. */
class ImageDiff(val meanAbs: Double, val meanSigned: Double, val max: Int, val psnr: Double, val heatmap: FloatImage) {
    override fun toString() =
        "mean abs error %.3f /255 (signed %+.3f), max %d /255, PSNR %.2f dB".format(meanAbs, meanSigned, max, psnr)

    companion object {
        fun of(a: FloatImage, b: FloatImage): ImageDiff {
            require(a.width == b.width && a.height == b.height) {
                "Sizes differ: ${a.width}x${a.height} vs ${b.width}x${b.height}"
            }
            var sumAbs = 0.0
            var sumSigned = 0.0
            var sumSq = 0.0
            var maxDiff = 0
            val heat = FloatImage(a.width, a.height)
            for (p in 0 until a.width * a.height) {
                var pixelMax = 0
                for (c in 0 until 3) {
                    val i = p * 3 + c
                    val signed = Math.round(b.data[i] * 255) - Math.round(a.data[i] * 255)
                    val d = Math.abs(signed)
                    sumSigned += signed
                    sumAbs += d
                    sumSq += d.toDouble() * d
                    if (d > pixelMax) pixelMax = d
                }
                if (pixelMax > maxDiff) maxDiff = pixelMax
                // Grey = equal, brighter = larger difference (x4 so small errors show).
                val v = minOf(1f, pixelMax * 4 / 255f)
                heat.data[p * 3] = v; heat.data[p * 3 + 1] = v; heat.data[p * 3 + 2] = v
            }
            val n = a.width * a.height * 3.0
            val mse = sumSq / n
            val psnr = if (mse == 0.0) Double.POSITIVE_INFINITY else 10 * log10(255.0 * 255.0 / mse)
            return ImageDiff(sumAbs / n, sumSigned / n, maxDiff, psnr, heat)
        }
    }
}
