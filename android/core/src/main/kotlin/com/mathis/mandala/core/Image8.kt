package com.mathis.mandala.core

/**
 * 8-bit interleaved RGB image, the form every intermediate takes between two former Core Image
 * steps. Storing it as bytes is exact, because the macOS renderer quantised to 8 bit there too,
 * and uses a quarter of the memory of a float image.
 */
class Image8(
    val width: Int,
    val height: Int,
    val encoding: Encoding,
    val data: ByteArray = ByteArray(width * height * 3),
) {
    fun get(i: Int): Int = data[i].toInt() and 0xFF

    /** Converts to 0xAARRGGBB pixels in sRGB (row-major, top row first). */
    fun toArgbPixels(): IntArray {
        val srgb = if (encoding == Encoding.SRGB) this else WorkingSpace.convertToSrgb(this)
        val out = IntArray(width * height)
        for (p in out.indices) {
            val i = p * 3
            out[p] = (0xFF shl 24) or (srgb.get(i) shl 16) or (srgb.get(i + 1) shl 8) or srgb.get(i + 2)
        }
        return out
    }
}

/**
 * Row conversion between stored 8-bit images and the Display P3 float working space that
 * Core Image computes in. Reading decodes into floats; writing converts to sRGB, clamps and
 * quantises, as Core Image did when it handed back an 8-bit sRGB image.
 */
object WorkingSpace {
    private val byteToUnit = FloatArray(256) { it / 255f }

    // sRGB byte -> linear light, exact for every 8-bit value.
    private val srgbByteToLinear = FloatArray(256) { ColorSpaces.decode(it / 255.0).toFloat() }

    private val s2p = FloatArray(9) { ColorSpaces.SRGB_TO_P3[it].toFloat() }
    private val p2s = FloatArray(9) { ColorSpaces.P3_TO_SRGB[it].toFloat() }

    /** Reads row `y` of `img` as P3 working values into `out` (size width * 3). */
    fun readRow(img: Image8, y: Int, out: FloatArray) {
        val base = y * img.width * 3
        val n = img.width * 3
        if (img.encoding == Encoding.DISPLAY_P3) {
            for (k in 0 until n) out[k] = byteToUnit[img.get(base + k)]
            return
        }
        var k = 0
        while (k < n) {
            readSrgbPixel(img, base + k, out, k)
            k += 3
        }
    }

    /** Reads one pixel (byte index `i`) as P3 working values into out[o..o+2]. */
    fun readPixel(img: Image8, i: Int, out: FloatArray, o: Int) {
        if (img.encoding == Encoding.DISPLAY_P3) {
            out[o] = byteToUnit[img.get(i)]; out[o + 1] = byteToUnit[img.get(i + 1)]; out[o + 2] = byteToUnit[img.get(i + 2)]
        } else {
            readSrgbPixel(img, i, out, o)
        }
    }

    private fun readSrgbPixel(img: Image8, i: Int, out: FloatArray, o: Int) {
        val lr = srgbByteToLinear[img.get(i)]
        val lg = srgbByteToLinear[img.get(i + 1)]
        val lb = srgbByteToLinear[img.get(i + 2)]
        out[o] = ColorSpaces.encodeFast(s2p[0] * lr + s2p[1] * lg + s2p[2] * lb)
        out[o + 1] = ColorSpaces.encodeFast(s2p[3] * lr + s2p[4] * lg + s2p[5] * lb)
        out[o + 2] = ColorSpaces.encodeFast(s2p[6] * lr + s2p[7] * lg + s2p[8] * lb)
    }

    /** Writes P3 working values as clamped 8-bit sRGB into row `y` of `img` (an sRGB image). */
    fun writeRow(row: FloatArray, img: Image8, y: Int) {
        require(img.encoding == Encoding.SRGB)
        val base = y * img.width * 3
        var k = 0
        val n = img.width * 3
        while (k < n) {
            val lr = ColorSpaces.decodeFast(row[k])
            val lg = ColorSpaces.decodeFast(row[k + 1])
            val lb = ColorSpaces.decodeFast(row[k + 2])
            img.data[base + k] = quantize(ColorSpaces.encodeFast(p2s[0] * lr + p2s[1] * lg + p2s[2] * lb))
            img.data[base + k + 1] = quantize(ColorSpaces.encodeFast(p2s[3] * lr + p2s[4] * lg + p2s[5] * lb))
            img.data[base + k + 2] = quantize(ColorSpaces.encodeFast(p2s[6] * lr + p2s[7] * lg + p2s[8] * lb))
            k += 3
        }
    }

    fun quantize(v: Float): Byte {
        val q = (v * 255f + 0.5f).toInt()
        return (if (q < 0) 0 else if (q > 255) 255 else q).toByte()
    }

    fun convertToSrgb(img: Image8): Image8 {
        val out = Image8(img.width, img.height, Encoding.SRGB)
        val row = FloatArray(img.width * 3)
        for (y in 0 until img.height) {
            readRow(img, y, row)
            writeRow(row, out, y)
        }
        return out
    }
}
