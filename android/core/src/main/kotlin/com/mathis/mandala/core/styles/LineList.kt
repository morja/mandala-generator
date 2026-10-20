package com.mathis.mandala.core.styles

import com.mathis.mandala.core.Parallel
import com.mathis.mandala.core.PixelBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Lines collected in drawing order (x0, y0, x1, y1, r, g, b, weight per line). Styles that the
 * macOS renderer draws straight into its buffer emit into this list; it is then drawn in parallel
 * row bands, which gives every pixel its lines in the same order as drawing one by one.
 */
class LineList {
    private var data = FloatArray(8 * 1024)
    var size = 0
        private set

    fun add(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, g: Float, b: Float, weight: Float) {
        if ((size + 1) * 8 > data.size) data = data.copyOf(data.size * 2)
        val o = size * 8
        data[o] = x0; data[o + 1] = y0; data[o + 2] = x1; data[o + 3] = y1
        data[o + 4] = r; data[o + 5] = g; data[o + 6] = b; data[o + 7] = weight
        size++
    }

    fun add(x0: Float, y0: Float, x1: Float, y1: Float, c: FloatArray, weight: Float) =
        add(x0, y0, x1, y1, c[0], c[1], c[2], weight)

    fun drawInto(buffer: PixelBuffer, par: Parallel) {
        if (size == 0) return
        val bands = max(1, min(buffer.height, par.threads * 4))
        val bandHeight = (buffer.height + bands - 1) / bands
        par.forEachChunk(bands) { b ->
            val view = buffer.band(b * bandHeight, (b + 1) * bandHeight)
            for (i in 0 until size) {
                val o = i * 8
                view.addLine(data[o], data[o + 1], data[o + 2], data[o + 3], data[o + 4], data[o + 5], data[o + 6], data[o + 7])
            }
        }
    }
}
