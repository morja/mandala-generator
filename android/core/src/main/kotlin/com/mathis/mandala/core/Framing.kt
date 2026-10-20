package com.mathis.mandala.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Where the mandala sits on the canvas (ADR-0010): its centre as fractions of the canvas width and
 * height, and its size relative to "fits the width". The default is today's image: centred, as
 * wide as the canvas.
 */
data class Framing(val centreX: Double = 0.5, val centreY: Double = 0.5, val zoom: Double = 1.0) {
    /** Zoom within the allowed range and the centre inside the frame. */
    fun clamped(): Framing = Framing(centreX.coerceIn(0.0, 1.0), centreY.coerceIn(0.0, 1.0), zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))

    companion object {
        const val MIN_ZOOM = 0.25
        const val MAX_ZOOM = 4.0
    }
}

/**
 * The mandala's place on a canvas in buffer pixels: the centre and the side of the square that the
 * mandala layers, hand layers and emblem are drawn for (the canvas width times the zoom).
 */
internal class Placement(val centreX: Double, val centreY: Double, val size: Int, val canvasWidth: Int, val canvasHeight: Int) {

    /**
     * The canvas pixels within `reach` of the centre horizontally and vertically, as
     * [x0, y0, x1, y1]; empty (x1 <= x0 or y1 <= y0) when the square misses the canvas.
     */
    fun around(reach: Double): IntArray = intArrayOf(
        max(0, floor(centreX - reach).toInt()),
        max(0, floor(centreY - reach).toInt()),
        min(canvasWidth, ceil(centreX + reach).toInt()),
        min(canvasHeight, ceil(centreY + reach).toInt()),
    )

    companion object {
        fun of(framing: Framing, canvasWidth: Int, canvasHeight: Int): Placement {
            val f = framing.clamped()
            return Placement(f.centreX * canvasWidth, f.centreY * canvasHeight, Math.round(canvasWidth * f.zoom).toInt().coerceAtLeast(1), canvasWidth, canvasHeight)
        }
    }
}
