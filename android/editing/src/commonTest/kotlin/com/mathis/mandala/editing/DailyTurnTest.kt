package com.mathis.mandala.editing

import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.MandalaParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Whose turn it is for the daily wallpaper and the widget (ADR-0014, ADR-0020). */
class DailyTurnTest {
    private val favourites = listOf("a", "b", "c", "d").map { Favorite(it, MandalaParameters()) }

    @Test
    fun onlyTheChosenFavouritesTakeTurns() {
        assertEquals("a" to 1, DailyTurn.turn(favourites, null, 0)?.let { it.first.id to it.second })
        assertEquals("d" to 4, DailyTurn.turn(favourites, null, 3)?.let { it.first.id to it.second })
        val chosen = setOf("b", "d")
        assertEquals(listOf("b", "d", "b"), (0..2).map { DailyTurn.turn(favourites, chosen, it)!!.first.id })
        // Every chosen one deleted: all take turns again.
        assertEquals("a", DailyTurn.turn(favourites, setOf("x"), 0)!!.first.id)
        assertNull(DailyTurn.turn(emptyList(), null, 0))
    }

    @Test
    fun tickingStartsFromAllAndEndsThere() {
        val all = favourites.map { it.id }
        val withoutB = DailyTurn.toggled(null, all, "b")
        assertEquals(setOf("a", "c", "d"), withoutB)
        assertNull(DailyTurn.toggled(withoutB, all, "b"))
        assertNull(DailyTurn.toggled(setOf("c"), all, "c"))
        assertEquals(setOf("c"), DailyTurn.toggled(setOf("c", "a"), all, "a"))
    }
}
