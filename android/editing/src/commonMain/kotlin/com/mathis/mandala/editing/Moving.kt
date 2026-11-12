package com.mathis.mandala.editing

import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.editing.Editing.movedBy
import com.mathis.mandala.editing.Editing.updateEmblem
import com.mathis.mandala.editing.Editing.updateLayer
import com.mathis.mandala.editing.Editing.updateText

/**
 * Where the part being moved by hand sits, for the marks on the picture (ADR-0011, UX-1 20): its
 * centre as fractions of the canvas, a ring radius as a fraction of its width (for the text, half
 * its box width), and whether it lies on a centre line, where moving snaps it.
 */
class MoveMark(
    val x: Double,
    val y: Double,
    val radius: Double,
    val target: MoveTarget,
    /** The layer's number from 1, or null for the emblem and the text. */
    val layer: Int?,
    val onCentreX: Boolean,
    val onCentreY: Boolean,
)

object Moving {
    /** The mark for `target` (the selected layer, the emblem or the text), or null when there is nothing to move. */
    fun mark(params: MandalaParameters, selected: Int, target: MoveTarget?): MoveMark? = when (target) {
        null -> null
        MoveTarget.LAYER -> params.layers.getOrNull(selected)?.let { l ->
            MoveMark(0.5 + l.offsetX, 0.5 + l.offsetY, 0.36 * l.scale, target, selected + 1, l.offsetX == 0.0, l.offsetY == 0.0)
        }
        MoveTarget.EMBLEM -> params.emblemLayer.let { e ->
            MoveMark(0.5 + e.offsetX, 0.5 + e.offsetY, 0.5 * e.size, target, null, e.offsetX == 0.0, e.offsetY == 0.0)
        }
        // The text stays centred across, so only its height has a line to snap to.
        MoveTarget.TEXT -> params.textLayer.let { t ->
            MoveMark(0.5, 1.0 - t.offsetY, t.fontWidth / 2, target, null, false, t.offsetY == 0.5)
        }
    }
}

/**
 * One way of moving by hand: drags, pinches and turns on the canvas applied to the move target,
 * snapping onto the centre lines (UX-1 20). It keeps a [CentreSnap] per axis, so one instance lives
 * as long as the editor.
 */
class MoveGesture {
    private val snapX = CentreSnap()
    private val snapY = CentreSnap()

    /**
     * `params` with the target moved by (dx, dy) (fractions of the canvas, y down), its size times
     * `zoom` and turned by `turnDegrees` clockwise. The text only moves up and down and does not turn.
     */
    fun moved(params: MandalaParameters, selected: Int, target: MoveTarget?, dx: Double, dy: Double, zoom: Double, turnDegrees: Double): MandalaParameters {
        val offsets = -Editing.MAX_OFFSET..Editing.MAX_OFFSET
        return when (target) {
            MoveTarget.LAYER -> params.updateLayer(selected) {
                it.movedBy(0.0, 0.0, zoom, turnDegrees)
                    .copy(offsetX = snapX.next(it.offsetX, dx, 0.0, offsets), offsetY = snapY.next(it.offsetY, dy, 0.0, offsets))
            }
            MoveTarget.EMBLEM -> params.updateEmblem {
                it.movedBy(0.0, 0.0, zoom, turnDegrees)
                    .copy(offsetX = snapX.next(it.offsetX, dx, 0.0, offsets), offsetY = snapY.next(it.offsetY, dy, 0.0, offsets))
            }
            // The text's height counts up from the bottom.
            MoveTarget.TEXT -> params.updateText { it.movedBy(0.0, zoom).copy(offsetY = snapY.next(it.offsetY, -dy, 0.5, 0.0..1.0)) }
            null -> params
        }
    }
}
