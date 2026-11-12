package com.mathis.mandala.editing

import com.mathis.mandala.core.Quote
import com.mathis.mandala.core.Quotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovingAndQuotesTest {
    @Test
    fun movingTheEmblemSnapsOntoTheCentreAndLeavesItAgain() {
        val gesture = MoveGesture()
        var p = Editing.initial(1uL)
        p = gesture.moved(p, 0, MoveTarget.EMBLEM, 0.1, 0.0, 1.0, 0.0)
        assertEquals(0.1, p.emblemLayer.offsetX, 1e-9)
        // Back within the snap distance of the centre line: on it.
        p = gesture.moved(p, 0, MoveTarget.EMBLEM, -0.09, 0.0, 1.0, 0.0)
        assertEquals(0.0, p.emblemLayer.offsetX)
        assertTrue(Moving.mark(p, 0, MoveTarget.EMBLEM)!!.onCentreX)
        // A slow drag leaves the line once the fingers are past the snap distance.
        p = gesture.moved(p, 0, MoveTarget.EMBLEM, 0.015, 0.0, 1.0, 0.0)
        assertEquals(0.025, p.emblemLayer.offsetX, 1e-9)
    }

    @Test
    fun theTextMovesOnlyUpAndDownCountingFromTheBottom() {
        val gesture = MoveGesture()
        val p0 = Editing.initial(1uL)
        val p = gesture.moved(p0, 0, MoveTarget.TEXT, 0.3, -0.05, 1.0, 45.0)
        assertEquals(p0.textLayer.offsetY + 0.05, p.textLayer.offsetY, 1e-9)
        val mark = Moving.mark(p, 0, MoveTarget.TEXT)!!
        assertEquals(0.5, mark.x)
        assertFalse(mark.onCentreX)
        assertNull(Moving.mark(p, 0, null))
        assertEquals(p, gesture.moved(p, 0, null, 0.1, 0.1, 2.0, 10.0))
    }

    @Test
    fun theLayerMarkNamesTheSelectedLayer() {
        val p = Editing.initial(1uL)
        val mark = Moving.mark(p, 0, MoveTarget.LAYER)!!
        assertEquals(1, mark.layer)
        assertTrue(mark.onCentreX && mark.onCentreY)
    }

    @Test
    fun keptWordsAreFoundAndLetGoAsTyped() {
        val list = MyQuotes.add(emptyList(), Quote(" Be here. ", " Me "))
        assertTrue(MyQuotes.contains(list, "Be here.  ", "Me"))
        assertFalse(MyQuotes.contains(list, "Be here.", "You"))
        assertEquals(emptyList(), MyQuotes.removeWords(list, " Be here.", "Me "))
    }

    @Test
    fun quoteLanguagesKeepOneOnAndFallBackToTheDevice() {
        assertEquals(listOf("de", "en"), QuoteChoice.languages(null, listOf("de-AT".take(2), "fr")))
        assertEquals(listOf("en"), QuoteChoice.languages(listOf("xx", ""), listOf("en")))
        assertEquals(listOf("de"), QuoteChoice.languages(listOf("de"), listOf("en")))
        assertNull(QuoteChoice.toggled(listOf("en"), "en"))
        assertEquals(listOf("en", "de"), QuoteChoice.toggled(listOf("en"), "de"))
        val mine = Quote("Mine", "")
        val pool = QuoteChoice.pool(listOf("de"), listOf(mine))
        assertEquals(Quotes.collectionsFor(listOf("de")).single().quotes.size + 1, pool.size)
        assertEquals(mine, pool.last())
    }
}
