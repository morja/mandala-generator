package com.mathis.mandala.editing

import com.mathis.mandala.core.Finish
import com.mathis.mandala.core.Framing
import com.mathis.mandala.core.LayerAnimation
import com.mathis.mandala.editing.Editing.SaveFormat

/** What the save sheet is set to (ADR-0010). Kept across starts as a convenience, not part of the mandala. */
data class SaveChoice(
    val format: SaveFormat = SaveFormat.SCREEN,
    val framing: Framing = Framing(),
    val textFollowsMandala: Boolean = false,
    /** How the mandala sits in the frame; [Fit.HAND] (placed by hand, `framing`) needs Pro. */
    val fit: Fit = Fit.WHOLE,
    /** Pro: brightness, contrast and saturation of the saved image or wallpaper (ADR-0014). */
    val finish: Finish = Finish(),
    /** Pro, the wallpaper pair: the home screen's brightness relative to the lock screen (ADR-0014). */
    val homeBrightness: Double = DEFAULT_HOME_BRIGHTNESS,
    /** How the mandala sat in the other formats, so each keeps its own; `fit` and `framing` are this format's. */
    val placements: Map<SaveFormat, Placement> = emptyMap(),
    /** Pro, a video of the layers turning (UX-1 12) instead of a picture. */
    val moving: Boolean = false,
    /** The video's length, one of [LayerAnimation.SECONDS]. */
    val videoSeconds: Int = LayerAnimation.DEFAULT_SECONDS,
    /** Placed by hand at least once; the sheet's hint on how to do it shows until then. */
    val framedByHand: Boolean = false,
) {
    /** The same choice in another format, with the placement that format had last ("Whole" the first time). */
    fun withFormat(next: SaveFormat): SaveChoice {
        if (next == format) return this
        val kept = placements + (format to Placement(fit, framing))
        val placement = kept[next] ?: Placement()
        return copy(format = next, fit = placement.fit, framing = placement.framing, placements = kept - next)
    }

    companion object {
        /** Darker than the lock screen, so the icons on the home screen stay readable. */
        const val DEFAULT_HOME_BRIGHTNESS = 0.6
        const val MIN_HOME_BRIGHTNESS = 0.2
    }
}

/** How the mandala sits in one format: a preset, or placed by hand. */
data class Placement(val fit: Fit = Fit.WHOLE, val framing: Framing = Framing())

/** How the mandala sits in the frame (ADR-0010): free presets, or placed by hand with Pro. */
enum class Fit {
    /** Centred, fitting the shorter side: the whole mandala shows. */
    WHOLE,
    /** Centred and zoomed until its large layers cover the corners ([com.mathis.mandala.core.MandalaRenderer.fillZoom]). */
    FILL,
    /** Moved and zoomed by hand (Pro): [SaveChoice.framing]. */
    HAND,
}
