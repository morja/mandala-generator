package com.mathis.mandala.app

import com.mathis.mandala.app.Editing.addStroke
import com.mathis.mandala.app.Randomize.randomAll
import com.mathis.mandala.app.Randomize.randomLayer
import com.mathis.mandala.app.Randomize.randomLayers
import com.mathis.mandala.app.Randomize.randomVariant
import com.mathis.mandala.app.Randomize.randomized
import com.mathis.mandala.core.BaseLayerSettings
import com.mathis.mandala.core.EmblemLayerSettings
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaStyle
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
            // Strange Attractor has calmer ranges of its own (strangeAttractorGetsCalmerDice…).
            for ((i, l) in p.layers.withIndex().filter { it.value.style != MandalaStyle.STRANGE_ATTRACTOR }) {
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

    @Test
    fun strangeAttractorGetsCalmerDiceAndAShorterComplexitySlider() {
        val p = MandalaParameters()
        val throws = (0 until 2000).map { p.randomLayer(0, Random(it)).layers[0] }.filter { it.style == MandalaStyle.STRANGE_ATTRACTOR }
        assertTrue(throws.isNotEmpty())
        for (l in throws) {
            assertTrue(l.complexity in 0.15..0.45)
            assertTrue(l.density in 0.1..0.45)
            assertTrue(l.brightness in 0.35..0.5)
            assertTrue(l.abstractLevel in 0.1..0.3)
            assertTrue(l.glowIntensity in 0.2..0.6)
        }
        assertEquals(0.15..0.5, Editing.Ranges.layerComplexity(MandalaStyle.STRANGE_ATTRACTOR))
        assertEquals(Editing.Ranges.layerComplexity, Editing.Ranges.layerComplexity(MandalaStyle.FLORAL))
    }

    @Test
    fun aVariantKeepsTheStyle() {
        val p = MandalaParameters().randomLayers(Random(3))
        repeat(50) { n ->
            val v = p.randomVariant(0, Random(n))
            assertEquals(p.layers[0].style, v.layers[0].style)
            assertEquals(p.layers[0].symmetry, v.layers[0].symmetry)
        }
        assertTrue((0 until 50).any { p.randomVariant(0, Random(it)).layers[0].seed != p.layers[0].seed })
    }

    @Test
    fun throwsWithoutProStayWithinTwoLayers() {
        for (n in 0 until 50) {
            assertTrue(MandalaParameters().randomLayers(Random(n), maxLayers = 2).layers.size in 1..2)
            assertTrue(MandalaParameters().randomAll(Random(n), maxLayers = 2).layers.size in 1..2)
        }
        assertEquals(3, (0 until 50).maxOf { MandalaParameters().randomLayers(Random(it)).layers.size })
    }
}
