package com.mathis.mandala.app

import com.mathis.mandala.app.Editing.addLayer
import com.mathis.mandala.app.Editing.duplicateLayer
import com.mathis.mandala.app.Editing.moveLayer
import com.mathis.mandala.app.Editing.newVariation
import com.mathis.mandala.app.Editing.removeLayer
import com.mathis.mandala.app.Editing.updateLayer
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.MandalaStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditingTest {
    private val start = Editing.initial(42uL)

    private fun threeLayers(): MandalaParameters {
        var p = start
        p = p.addLayer(1uL).first
        p = p.addLayer(2uL).first
        return p
    }

    @Test
    fun offeredStylesAreAllPorted() {
        assertTrue(MandalaRenderer.supportedStyles.containsAll(Editing.styles))
        assertTrue(start.layers.single().style in Editing.styles)
    }

    @Test
    fun updatingALayerKeepsTheOthers() {
        val p = threeLayers()
        val changed = p.updateLayer(1) { it.copy(symmetry = 3) }
        assertEquals(3, changed.layers[1].symmetry)
        assertEquals(p.layers[0], changed.layers[0])
        assertEquals(p.layers[2], changed.layers[2])
    }

    @Test
    fun addedLayersFollowTheMacDefaults() {
        val (p, index) = start.addLayer(7uL)
        assertEquals(1, index)
        val added = p.layers[1]
        assertEquals(Editing.styles[1], added.style)
        assertEquals(3, added.paletteIndex)
        assertEquals(0.65, added.scale, 0.0)
        assertEquals(7uL, added.seed)
    }

    @Test
    fun atMostFiveLayers() {
        var p = start
        repeat(10) { p = p.addLayer(it.toULong()).first }
        assertEquals(Editing.MAX_LAYERS, p.layers.size)
        assertEquals(p, p.duplicateLayer(0, 99uL).first)
    }

    @Test
    fun duplicateGoesRightAboveWithANewSeed() {
        val p = threeLayers()
        val (d, index) = p.duplicateLayer(0, 99uL)
        assertEquals(1, index)
        assertEquals(4, d.layers.size)
        assertEquals(p.layers[0].copy(seed = 99uL), d.layers[1])
    }

    @Test
    fun theLastLayerCannotBeRemoved() {
        assertEquals(start, start.removeLayer(0).first)
        val p = threeLayers()
        val (r, select) = p.removeLayer(2)
        assertEquals(2, r.layers.size)
        assertEquals(1, select)
    }

    @Test
    fun moveSwapsNeighboursAndStopsAtTheEnds() {
        val p = threeLayers()
        val (up, index) = p.moveLayer(0, +1)
        assertEquals(1, index)
        assertEquals(listOf(p.layers[1], p.layers[0], p.layers[2]), up.layers)
        assertEquals(p to 2, p.moveLayer(2, +1))
        assertEquals(p to 0, p.moveLayer(0, -1))
    }

    @Test
    fun newVariationChangesOnlySeeds() {
        val p = threeLayers()
        var next = 100uL
        val varied = p.newVariation { next++ }
        assertEquals(100uL, varied.seed)
        assertEquals(listOf(101uL, 102uL, 103uL), varied.layers.map { it.seed })
        assertEquals(p.layers.map { it.copy(seed = 0uL) }, varied.layers.map { it.copy(seed = 0uL) })
    }

    @Test
    fun fileNameFollowsTheMacScheme() {
        val p = start.updateLayer(0) { it.copy(style = MandalaStyle.ROSE_CURVES, paletteIndex = 5, symmetry = 6) }
        assertTrue(Editing.fileName(p), Regex("mandala-rose-p5-x6-[0-9a-f]{8}\\.png").matches(Editing.fileName(p)))
        val two = p.addLayer(1uL).first
        assertTrue(Editing.fileName(two), Editing.fileName(two).startsWith("mandala-rose+rose-p5-x6-"))
        assertNotEquals(Editing.fileName(p), Editing.fileName(p.newVariation { 1uL }))
    }
}
