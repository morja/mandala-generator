package com.mathis.mandala.app

import android.content.Context
import androidx.annotation.StringRes

/**
 * The first-start tour (DM-22, card a; UX-1 21): seven cards docked at the bottom, each pointing with
 * a ring and a line at a real control while the rest of the screen is dimmed, except the picture.
 * The composables report where their targets are; this file only holds the steps and the rules.
 */

/** What the tour points at. The composables of these controls report their place on screen. */
enum class TourTarget { PICTURE, PLAY, FAVOURITES, SAVE, MORE, TOP_DIE, SCENE_TAB, STYLE_ROW, PALETTE_ROW, EFFECTS_ROW, LAYER_DIE }

/** The control tabs a step can need. */
enum class TourTab { LAYERS, SCENE }

/** A ring around a target; `line` also draws the line from the ring to the card. */
data class TourMark(val target: TourTarget, val line: Boolean = true)

/** The steps in their order, with what each one marks and which tab and row it needs. */
enum class TourStep(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val marks: List<TourMark>,
    /** The tab the step opens; null keeps the tab that was open before the tour. */
    val tab: TourTab? = null,
    /** Scrolled up to just under the tabs. */
    val scrollTo: TourTarget? = null,
    /** Left undimmed, without a ring. */
    val open: List<TourTarget> = emptyList(),
) {
    PLAY(R.string.tour_play_title, R.string.tour_play_body, listOf(TourMark(TourTarget.PLAY))),
    FAVOURITES(R.string.tour_favourites_title, R.string.tour_favourites_body, listOf(TourMark(TourTarget.FAVOURITES))),
    SAVE(R.string.tour_save_title, R.string.tour_save_body, listOf(TourMark(TourTarget.SAVE), TourMark(TourTarget.MORE))),
    STYLES(R.string.tour_styles_title, R.string.tour_styles_body, listOf(TourMark(TourTarget.STYLE_ROW)),
        tab = TourTab.LAYERS, scrollTo = TourTarget.STYLE_ROW),
    COLOURS(R.string.tour_colours_title, R.string.tour_colours_body, listOf(TourMark(TourTarget.PALETTE_ROW)),
        tab = TourTab.LAYERS, scrollTo = TourTarget.PALETTE_ROW),
    EFFECTS(R.string.tour_effects_title, R.string.tour_effects_body, listOf(TourMark(TourTarget.EFFECTS_ROW)),
        tab = TourTab.SCENE, scrollTo = TourTarget.EFFECTS_ROW, open = listOf(TourTarget.SCENE_TAB)),
    // The top die is far up; only the one in the Layers tab gets a line, so the line does not cross the picture.
    DIE(R.string.tour_die_title, R.string.tour_die_body, listOf(TourMark(TourTarget.TOP_DIE, line = false), TourMark(TourTarget.LAYER_DIE)),
        tab = TourTab.LAYERS, scrollTo = TourTarget.LAYER_DIE),
}

/** Where a running tour is, and whether it started by itself on a new install (then it says where to find it again). */
data class TourState(val step: TourStep, val automatic: Boolean = false) {
    /** The step's number from 1, for "3 of 7". */
    val number: Int get() = step.ordinal + 1
    val isFirst: Boolean get() = step.ordinal == 0
    val isLast: Boolean get() = step.ordinal == TourStep.entries.lastIndex

    /** The next step, or null after the last one (the tour ends). */
    fun next(): TourState? = TourStep.entries.getOrNull(step.ordinal + 1)?.let { copy(step = it) }

    /** The step before; the first step stays. */
    fun back(): TourState = copy(step = TourStep.entries.getOrElse(step.ordinal - 1) { step })

    companion object {
        val count: Int get() = TourStep.entries.size

        fun start(automatic: Boolean) = TourState(TourStep.entries.first(), automatic)
    }
}

/**
 * Whether the tour starts by itself: once, on a new install. Someone who already has a saved mandala
 * (an update from an older version) never gets it by itself; both are marked as seen right away, so
 * the tour does not come back after a crash or on the next start. The menu can start it any time.
 */
class TourGate(private val isSeen: () -> Boolean, private val markSeen: () -> Unit) {
    fun startsByItself(hadSavedState: Boolean): Boolean {
        if (isSeen()) return false
        markSeen()
        return !hadSavedState
    }

    companion object {
        /** The flag in the app's preferences. */
        fun of(context: Context): TourGate {
            val prefs = context.getSharedPreferences("tour", Context.MODE_PRIVATE)
            return TourGate({ prefs.getBoolean(KEY_SEEN, false) }, { prefs.edit().putBoolean(KEY_SEEN, true).apply() })
        }

        private const val KEY_SEEN = "seen"
    }
}

/** Where the card goes, in pixels from the top of the overlay. */
object TourLayout {
    /** A ring's top and bottom edge. */
    data class Span(val top: Float, val bottom: Float)

    /**
     * The card's top edge: docked at `bottom`, so Next stays under the thumb, and moved up past every
     * ring it would cover (keeping `gap` to it), but never above `top`.
     */
    fun cardTop(rings: List<Span>, cardHeight: Float, top: Float, bottom: Float, gap: Float): Float {
        var y = bottom - cardHeight
        for (r in rings.sortedByDescending { it.bottom }) {
            val covers = y < r.bottom + gap && y + cardHeight > r.top - gap
            if (covers) y = r.top - gap - cardHeight
        }
        return maxOf(top, y)
    }

    /**
     * The scroll position that brings a row up to `gap` under the top of the scrolled area: `rowTop`
     * and `viewportTop` are on screen, `scroll` is the current position, `max` the largest one.
     */
    fun scrollFor(rowTop: Float, viewportTop: Float, scroll: Int, max: Int, gap: Float): Int =
        (rowTop - viewportTop + scroll - gap).toInt().coerceIn(0, max)
}
