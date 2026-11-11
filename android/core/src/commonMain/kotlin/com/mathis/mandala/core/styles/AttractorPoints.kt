package com.mathis.mandala.core.styles

import com.mathis.mandala.core.PixelBuffer

/**
 * The points of one Strange Attractor call, kept so they can be drawn strip by strip. The macOS
 * renderer adds them straight into its full buffer; replaying them in the same order (runs, points,
 * symmetry copies) gives every pixel the same sums. One point costs 10 bytes, whatever the image size.
 */
class AttractorPoints internal constructor(
    private val cx: Float,
    private val cy: Float,
    private val lut: Array<FloatArray>,
    private val symCos: FloatArray,
    private val symSin: FloatArray,
) {
    private class Run(val weight: Float, capacity: Int) {
        val xy = FloatArray(capacity * 2)
        val colour = ShortArray(capacity)
        var count = 0
    }

    private val runs = ArrayList<Run>()
    private var current: Run? = null

    internal fun startRun(weight: Float, capacity: Int) {
        current = Run(weight, capacity).also { runs += it }
    }

    internal fun add(px: Float, py: Float, colourIndex: Int) {
        val run = current!!
        val i = run.count++
        run.xy[i * 2] = px
        run.xy[i * 2 + 1] = py
        run.colour[i] = colourIndex.toShort()
    }

    fun drawInto(buffer: PixelBuffer) {
        val symmetry = symCos.size
        for (run in runs) {
            val weight = run.weight
            for (i in 0 until run.count) {
                val px = run.xy[i * 2]
                val py = run.xy[i * 2 + 1]
                val col = lut[run.colour[i].toInt()]
                for (s in 0 until symmetry) {
                    buffer.addPoint(
                        (cx + px * symCos[s] - py * symSin[s]).toInt(),
                        (cy + px * symSin[s] + py * symCos[s]).toInt(),
                        col[0], col[1], col[2], weight,
                    )
                }
            }
        }
    }
}
