package com.mathis.mandala.editing

import com.mathis.mandala.core.TextLayerSettings
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The text shadow's direction and distance as two sliders, written to the Mac's `shadowOffsetX` and
 * `shadowOffsetY` (QA-13). The fields keep the Mac's meaning: fractions of 0.75 of the font size, X to
 * the right, Y upward (AppKit draws with y up; the Mac's field comment "-1 up … 1 down" is the wrong way
 * round, its renderer and core move the shadow up for a positive Y). A file keeps its exact offsets until
 * one of the two sliders moves.
 *
 * The direction is a fraction of a full turn on screen like the Scene tab's relief angle: 0 to the right,
 * 0.25 down, 0.5 left, 0.75 up (clockwise). The Mac's default offset (0.3, -0.3) is 0.125, down and to the
 * right, the relief angle's default too.
 */
object TextShadow {
    /** A full turn. */
    val directionRange = 0.0..1.0

    /** The Mac's X and Y sliders reach ±2 each; a longer diagonal from a file stays until the slider moves. */
    val distanceRange = 0.0..2.0

    /** The Mac's softness slider (`shadowBlur`). */
    val softnessRange = 0.0..1.0

    /** The direction of the default offset, also used while the distance is 0 and no direction was chosen. */
    const val DEFAULT_DIRECTION = 0.125

    /** Below this distance the shadow sits under the letters and has no direction. */
    private const val NO_DISTANCE = 1e-9

    fun distance(s: TextLayerSettings): Double = hypot(s.shadowOffsetX, s.shadowOffsetY)

    /** The direction as a fraction of a turn in [0, 1), or null while the shadow has no distance. */
    fun direction(s: TextLayerSettings): Double? {
        if (distance(s) < NO_DISTANCE) return null
        // Screen y runs down, the field's Y up.
        val turn = atan2(-s.shadowOffsetY, s.shadowOffsetX) / (2 * PI)
        return if (turn < 0) turn + 1.0 else if (turn >= 1.0) turn - 1.0 else turn
    }

    /** The shadow turned to `direction`, its distance kept. */
    fun withDirection(s: TextLayerSettings, direction: Double): TextLayerSettings = withOffset(s, direction, distance(s))

    /**
     * The shadow moved to `distance`, its direction kept; `fallbackDirection` is used while there is none
     * yet (the distance was 0), so the shadow comes out where the direction slider stands.
     */
    fun withDistance(s: TextLayerSettings, distance: Double, fallbackDirection: Double = DEFAULT_DIRECTION): TextLayerSettings =
        withOffset(s, direction(s) ?: fallbackDirection, distance.coerceAtLeast(0.0))

    fun withSoftness(s: TextLayerSettings, softness: Double): TextLayerSettings = s.copy(shadowBlur = softness)

    private fun withOffset(s: TextLayerSettings, direction: Double, distance: Double): TextLayerSettings {
        val angle = direction * 2 * PI
        return s.copy(shadowOffsetX = distance * cos(angle), shadowOffsetY = -distance * sin(angle))
    }
}
