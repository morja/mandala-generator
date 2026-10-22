package com.mathis.mandala.app

import com.mathis.mandala.app.Editing.addStroke
import com.mathis.mandala.app.Randomize.randomAll
import com.mathis.mandala.app.Randomize.randomLayer
import com.mathis.mandala.app.Randomize.randomLayers
import com.mathis.mandala.app.Randomize.randomized
import com.mathis.mandala.core.BaseLayerSettings
import com.mathis.mandala.core.EmblemLayerSettings
import com.mathis.mandala.core.MandalaParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HistoryAndRandomizeTest {
    @Test
    fun goesBackAndForwardAndDropsTheStatesAheadOnChange() {
        val h = History<Int>()
        h.record(1); h.record(2); h.record(3)
        assertEquals(2, h.back())
        h.record(2) // the state just restored is not recorded again
        assertTrue(h.canGoForward)
        assertEquals(3, h.forward())
        assertEquals(2, h.back())
        h.record(4)
        assertFalse(h.canGoForward)
        assertEquals(2, h.back())
        assertEquals(1, h.back())
        assertNull(h.back())
    }

    @Test
    fun restoresSavedStatesAtTheNewest() {
        val h = History<Int>()
        h.restore(listOf(1, 2, 3))
        assertFalse(h.canGoForward)
        assertEquals(2, h.back())
        assertEquals(listOf(1, 2, 3), h.entries)
    }

    @Test
    fun keepsAtMostTheLimit() {
        val h = History<Int>(limit = 3)
        for (i in 1..5) h.record(i)
        assertEquals(4, h.back())
        assertEquals(3, h.back())
        assertNull(h.back())
    }

    @Test
    fun randomLayersFollowTheMacRanges() {
        repeat(50) { n ->
            val p = MandalaParameters().randomLayers(Random(n))
            assertTrue(p.layers.size in 1..3)
            assertEquals(1, p.layers.map { it.symmetry }.distinct().size)
            assertEquals(p.layers.size, p.layers.map { it.style }.distinct().size)
            assertTrue(p.layers.first().scale >= 0.75)
            assertTrue(p.layers.drop(1).all { it.scale < 0.75 })
            assertTrue(p.layers.all { it.style in Editing.styles })
        }
    }

    @Test
    fun diceStayInTheTunedRanges() {
        repeat(200) { n ->
            val p = MandalaParameters().randomAll(Random(n))
            assertTrue(p.layers.first().symmetry in Randomize.SYMMETRY)
            for ((i, l) in p.layers.withIndex()) {
                assertEquals(0.0, l.wash, 0.0)
                assertTrue(l.abstractLevel in 0.1..0.5)
                assertTrue(l.brightness in 0.3..0.6)
                assertTrue(l.paletteIndex in Editing.offeredPalettes)
                val top = if (i == 0) 1.0 else Randomize.UPPER_LAYER_CALM
                assertTrue(l.complexity in 0.2 * top..top)
                assertTrue(l.density in 0.2 * top..top)
            }
            assertTrue(p.baseLayer.type in Editing.backgroundChoices(BaseLayerSettings()))
            assertTrue(p.randomLayer(0, Random(n)).layers[0].paletteIndex in Editing.offeredPalettes)
            assertTrue(EmblemLayerSettings().randomized(Random(n)).paletteIndex in Editing.offeredPalettes)
        }
    }

    @Test
    fun randomAllKeepsDrawingAndChangesOnlyTheChosenLayer() {
        val drawn = MandalaParameters().addStroke(DrawTool.LINES, listOf(0.1, 0.2), listOf(0.1, 0.2))
        val all = drawn.randomAll(Random(7))
        assertEquals(drawn.drawingLayer, all.drawingLayer)
        assertTrue(all.baseLayer.type in Editing.backgrounds)

        val two = all.copy(layers = all.layers + all.layers.first())
        val one = two.randomLayer(1, Random(3))
        assertEquals(two.layers[0], one.layers[0])
        assertEquals(two.layers[1].symmetry, one.layers[1].symmetry)
    }
}
