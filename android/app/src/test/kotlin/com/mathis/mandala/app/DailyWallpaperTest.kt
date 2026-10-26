package com.mathis.mandala.app

import java.time.Duration
import java.time.LocalDateTime
import org.junit.Test
import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.MandalaParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/** When the daily wallpaper (ADR-0014) runs: the next morning between 4 and 8 o'clock. */
class DailyWallpaperTest {
    @Test
    fun theWindowIsTheNextMorning() {
        val evening = LocalDateTime.of(2026, 10, 22, 22, 0)
        assertEquals(Duration.ofHours(6) to Duration.ofHours(10), DailyWallpaper.nextWindow(evening))
        // Switched on in the night before 4: this morning; at 4 or later: tomorrow.
        assertEquals(Duration.ofHours(2) to Duration.ofHours(6), DailyWallpaper.nextWindow(LocalDateTime.of(2026, 10, 23, 2, 0)))
        assertEquals(Duration.ofHours(24) to Duration.ofHours(28), DailyWallpaper.nextWindow(LocalDateTime.of(2026, 10, 23, 4, 0)))
    }

    private val favourites = listOf("a", "b", "c", "d").map { Favorite(it, MandalaParameters()) }

    @Test
    fun onlyTheChosenFavouritesTakeTurns() {
        assertEquals("a" to 1, DailyWallpaper.turn(favourites, null, 0)?.let { it.first.id to it.second })
        assertEquals("d" to 4, DailyWallpaper.turn(favourites, null, 3)?.let { it.first.id to it.second })
        val chosen = setOf("b", "d")
        assertEquals(listOf("b", "d", "b"), (0..2).map { DailyWallpaper.turn(favourites, chosen, it)!!.first.id })
        // Every chosen one deleted: all take turns again.
        assertEquals("a", DailyWallpaper.turn(favourites, setOf("x"), 0)!!.first.id)
        assertNull(DailyWallpaper.turn(emptyList(), null, 0))
    }

    @Test
    fun tickingStartsFromAllAndEndsThere() {
        val all = favourites.map { it.id }
        val withoutB = DailyWallpaper.toggled(null, all, "b")
        assertEquals(setOf("a", "c", "d"), withoutB)
        assertNull(DailyWallpaper.toggled(withoutB, all, "b"))
        assertNull(DailyWallpaper.toggled(setOf("c"), all, "c"))
        assertEquals(setOf("c"), DailyWallpaper.toggled(setOf("c", "a"), all, "a"))
    }
}
