package com.mathis.mandala.app

import com.mathis.mandala.core.Quote
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The wide widget's words without a quote of the favourite's own (ADR-0020). */
class MandalaWidgetTest {
    private val quotes = listOf(Quote("One"), Quote("Two"), Quote("Three"))

    @Test
    fun theQuoteStaysForTheDayAndChangesTheNext() {
        val day = LocalDate.of(2026, 10, 26)
        assertEquals(MandalaWidget.quoteOfTheDay(quotes, day), MandalaWidget.quoteOfTheDay(quotes, day))
        assertNotEquals(MandalaWidget.quoteOfTheDay(quotes, day), MandalaWidget.quoteOfTheDay(quotes, day.plusDays(1)))
        assertEquals(Quote(""), MandalaWidget.quoteOfTheDay(emptyList(), day))
    }
}
