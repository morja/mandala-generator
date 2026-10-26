package com.mathis.mandala.core

/**
 * The colour conversion for video frames: an sRGB image to Y′CbCr 4:2:0 with the BT.709 matrix in
 * limited range, which is what phones and players assume for HD video. The app hands the planes to
 * the platform's encoder; the conversion stays here so it is tested like the rest of the rendering.
 */
object VideoFrames {
    private const val KR = 0.2126
    private const val KB = 0.0722
    private const val KG = 1 - KR - KB

    /**
     * Writes `image` (sRGB, even width and height) as three planes: `y` of width × height, `u` and `v`
     * of half width × half height, each chroma value the mean of its 2 × 2 pixels. Rows are shared
     * among the threads of `par`, if given.
     */
    fun toI420(image: Image8, y: ByteArray, u: ByteArray, v: ByteArray, par: Parallel? = null) {
        val srgb = if (image.encoding == Encoding.SRGB) image else WorkingSpace.convertToSrgb(image)
        val w = srgb.width
        val h = srgb.height
        require(w % 2 == 0 && h % 2 == 0) { "Video frames need an even width and height, not $w x $h." }
        if (par != null) par.forRange(h / 2) { from, until -> rows(srgb, y, u, v, from, until) } else rows(srgb, y, u, v, 0, h / 2)
    }

    /** Chroma rows `from` until `until` and the two luma rows each covers. */
    private fun rows(srgb: Image8, y: ByteArray, u: ByteArray, v: ByteArray, from: Int, until: Int) {
        val w = srgb.width
        val cw = w / 2
        for (cy in from until until) {
            for (cx in 0 until cw) {
                var sumR = 0
                var sumG = 0
                var sumB = 0
                for (dy in 0..1) {
                    for (dx in 0..1) {
                        val px = (cy * 2 + dy) * w + cx * 2 + dx
                        val r = srgb.get(px * 3)
                        val g = srgb.get(px * 3 + 1)
                        val b = srgb.get(px * 3 + 2)
                        y[px] = luma(r, g, b).toByte()
                        sumR += r
                        sumG += g
                        sumB += b
                    }
                }
                val r = sumR / 4.0
                val g = sumG / 4.0
                val b = sumB / 4.0
                val l = KR * r + KG * g + KB * b
                u[cy * cw + cx] = chroma((b - l) / (2 * (1 - KB))).toByte()
                v[cy * cw + cx] = chroma((r - l) / (2 * (1 - KR))).toByte()
            }
        }
    }

    /** Limited range: black at 16, white at 235. */
    private fun luma(r: Int, g: Int, b: Int): Int = clamp(16 + (KR * r + KG * g + KB * b) * 219 / 255)

    /** Limited range: grey at 128, ±112 at the extremes. */
    private fun chroma(c: Double): Int = clamp(128 + c * 224 / 255)

    private fun clamp(x: Double): Int = Math.round(x).toInt().coerceIn(0, 255)
}
