package com.mathis.mandala.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourTest {
    /** A gate over a flag in memory, as the preferences would hold it. */
    private class Flag(var seen: Boolean = false) {
        val gate = TourGate({ seen }, { seen = true })
    }

    @Test
    fun aNewInstallGetsTheTourOnce() {
        val flag = Flag()
        assertTrue(flag.gate.startsByItself(hadSavedState = false))
        assertTrue(flag.seen)
        // The next start, even before anything was saved.
        assertFalse(flag.gate.startsByItself(hadSavedState = false))
    }

    @Test
    fun anExistingUserNeverGetsItByItself() {
        val flag = Flag()
        assertFalse(flag.gate.startsByItself(hadSavedState = true))
        // Marked as seen, so it does not come later when the saved state is gone.
        assertTrue(flag.seen)
        assertFalse(flag.gate.startsByItself(hadSavedState = false))
    }

    @Test
    fun aSeenTourStaysAway() {
        assertFalse(Flag(seen = true).gate.startsByItself(hadSavedState = false))
    }

    @Test
    fun sevenStepsInTheirOrder() {
        assertEquals(
            listOf(TourStep.PLAY, TourStep.FAVOURITES, TourStep.SAVE, TourStep.STYLES, TourStep.COLOURS, TourStep.EFFECTS, TourStep.DIE),
            TourStep.entries.toList(),
        )
        assertEquals(7, TourState.count)
    }

    @Test
    fun nextWalksThroughAndEndsAfterTheLast() {
        var state: TourState? = TourState.start(automatic = true)
        val seen = mutableListOf<Int>()
        while (state != null) {
            seen += state.number
            assertTrue(state.automatic)
            state = state.next()
        }
        assertEquals((1..7).toList(), seen)
    }

    @Test
    fun backStopsAtTheFirstStep() {
        val first = TourState.start(automatic = false)
        assertTrue(first.isFirst)
        assertEquals(first, first.back())
        val third = first.next()!!.next()!!
        assertEquals(TourStep.FAVOURITES, third.back().step)
        assertTrue(TourState(TourStep.DIE).isLast)
    }

    @Test
    fun stepsNeedTheirTabsAndRows() {
        // The first three point at the top bar and keep the tab that is open.
        for (step in listOf(TourStep.PLAY, TourStep.FAVOURITES, TourStep.SAVE)) assertNull(step.tab)
        assertEquals(TourTab.LAYERS, TourStep.STYLES.tab)
        assertEquals(TourTarget.STYLE_ROW, TourStep.STYLES.scrollTo)
        assertEquals(TourTarget.PALETTE_ROW, TourStep.COLOURS.scrollTo)
        assertEquals(TourTab.SCENE, TourStep.EFFECTS.tab)
        // The die step marks both dice in view; only the lower one gets a line.
        assertEquals(listOf(TourMark(TourTarget.TOP_DIE, line = false), TourMark(TourTarget.LAYER_DIE)), TourStep.DIE.marks)
        // Save and share marks both the save arrow and the menu.
        assertEquals(listOf(TourTarget.SAVE, TourTarget.MORE), TourStep.SAVE.marks.map { it.target })
    }

    @Test
    fun theCardDocksAtTheBottom() {
        val top = TourLayout.cardTop(listOf(TourLayout.Span(44f, 68f)), cardHeight = 150f, top = 36f, bottom = 756f, gap = 8f)
        assertEquals(606f, top)
    }

    @Test
    fun theCardMovesUpPastARingItWouldCover() {
        // A row at 600..650: the card goes above it, keeping the gap.
        val top = TourLayout.cardTop(listOf(TourLayout.Span(600f, 650f)), cardHeight = 150f, top = 36f, bottom = 756f, gap = 8f)
        assertEquals(442f, top)
    }

    @Test
    fun theCardNeverGoesAboveTheTop() {
        val top = TourLayout.cardTop(listOf(TourLayout.Span(100f, 700f)), cardHeight = 150f, top = 36f, bottom = 756f, gap = 8f)
        assertEquals(36f, top)
    }

    @Test
    fun aRowScrollsUpUnderTheTabs() {
        // The row is 300 px below the top of the scrolled area, which is already scrolled by 40.
        assertEquals(328, TourLayout.scrollFor(rowTop = 800f, viewportTop = 500f, scroll = 40, max = 2000, gap = 12f))
        // Never past either end.
        assertEquals(0, TourLayout.scrollFor(rowTop = 505f, viewportTop = 500f, scroll = 0, max = 2000, gap = 12f))
        assertEquals(100, TourLayout.scrollFor(rowTop = 900f, viewportTop = 500f, scroll = 0, max = 100, gap = 12f))
    }
}
