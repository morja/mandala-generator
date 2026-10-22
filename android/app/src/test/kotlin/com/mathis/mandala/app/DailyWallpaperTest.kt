package com.mathis.mandala.app

import java.time.Duration
import java.time.LocalDateTime
import org.junit.Test
import org.junit.Assert.assertEquals

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
}
