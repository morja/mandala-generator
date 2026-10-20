package com.mathis.mandala.app

import com.mathis.mandala.app.Editing.SaveFormat
import com.mathis.mandala.app.Editing.addLayer
import com.mathis.mandala.app.Editing.moved
import com.mathis.mandala.app.Editing.zoomedAround
import com.mathis.mandala.core.Framing
import com.mathis.mandala.app.Editing.addStroke
import com.mathis.mandala.app.Editing.clearStrokes
import com.mathis.mandala.app.Editing.removeLastStroke
import com.mathis.mandala.app.Editing.updateDrawing
import com.mathis.mandala.app.Editing.duplicateLayer
import com.mathis.mandala.app.Editing.moveLayer
import com.mathis.mandala.app.Editing.newVariation
import com.mathis.mandala.app.Editing.removeLayer
import com.mathis.mandala.app.Editing.updateLayer
import com.mathis.mandala.app.Editing.newShape
import com.mathis.mandala.app.Editing.reset
import com.mathis.mandala.app.Editing.updateEmblem
import com.mathis.mandala.app.Randomize.randomized
import com.mathis.mandala.core.EmblemLayerSettings
import com.mathis.mandala.core.EmblemMotif
import com.mathis.mandala.core.LayerBlendMode
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
        assertEquals(Editing.styles.toSet(), Editing.styleGroups.flatMap { it.second }.toSet())
        assertTrue(start.layers.single().style in Editing.styles)
        Editing.backgrounds.forEach { assertTrue(it.name, com.mathis.mandala.core.MandalaRenderer.supportsBackground(it)) }
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
    fun newPatternChangesOnlyTheEffectSeeds() {
        val fx = com.mathis.mandala.core.EffectsLayerSettings(isEnabled = true, stars = 0.3)
        var next = 500uL
        val renewed = with(Editing) { fx.newPattern { next++ } }
        assertEquals(listOf(500uL, 501uL, 502uL, 503uL, 504uL),
            listOf(renewed.dimmingSeed, renewed.erasureSeed, renewed.highlightsSeed, renewed.starsSeed, renewed.glitterSeed))
        assertEquals(fx.copy(dimmingSeed = 0uL, erasureSeed = 0uL, highlightsSeed = 0uL, starsSeed = 0uL, glitterSeed = 0uL),
            renewed.copy(dimmingSeed = 0uL, erasureSeed = 0uL, highlightsSeed = 0uL, starsSeed = 0uL, glitterSeed = 0uL))
    }

    @Test
    fun fileNameFollowsTheMacScheme() {
        val p = start.updateLayer(0) { it.copy(style = MandalaStyle.ROSE_CURVES, paletteIndex = 5, symmetry = 6) }
        assertTrue(Editing.fileName(p), Regex("mandala-rose-p5-x6-[0-9a-f]{8}\\.png").matches(Editing.fileName(p)))
        val two = p.addLayer(1uL).first
        assertTrue(Editing.fileName(two), Editing.fileName(two).startsWith("mandala-rose+rose-p5-x6-"))
        assertNotEquals(Editing.fileName(p), Editing.fileName(p.newVariation { 1uL }))
    }

    @Test
    fun strokesTakeTheToolColourAndSwitchTheLayerOn() {
        val p = MandalaParameters().updateDrawing { it.copy(isEnabled = false, currentHue = 0.25) }
        val drawn = p.addStroke(DrawTool.LINES, listOf(0.1, 0.2), listOf(0.3, 0.4))
        assertTrue(drawn.drawingLayer.isEnabled)
        assertEquals(0.25, drawn.drawingLayer.strokes.single().hue, 0.0)

        val sprayed = drawn.addStroke(DrawTool.SPRAY, listOf(0.5), listOf(0.5))
        assertTrue(sprayed.graffitiLayer.isEnabled)
        assertEquals(1, sprayed.graffitiLayer.strokes.size)
        assertEquals(sprayed.graffitiLayer.currentBrushSize, sprayed.graffitiLayer.strokes.single().brushSize, 0.0)
    }

    @Test
    fun singleTapsMakeNoLineButASprayDot() {
        val p = MandalaParameters()
        assertEquals(p, p.addStroke(DrawTool.LINES, listOf(0.5), listOf(0.5)))
        assertEquals(1, p.addStroke(DrawTool.SPRAY, listOf(0.5), listOf(0.5)).graffitiLayer.strokes.size)
    }

    @Test
    fun undoAndClearOnlyTouchTheirTool() {
        val p = MandalaParameters()
            .addStroke(DrawTool.LINES, listOf(0.1, 0.2), listOf(0.1, 0.2))
            .addStroke(DrawTool.LINES, listOf(0.3, 0.4), listOf(0.3, 0.4))
            .addStroke(DrawTool.SPRAY, listOf(0.5), listOf(0.5))
        val undone = p.removeLastStroke(DrawTool.LINES)
        assertEquals(listOf(0.1, 0.2), undone.drawingLayer.strokes.single().xs)
        assertEquals(1, undone.graffitiLayer.strokes.size)
        val cleared = p.clearStrokes(DrawTool.SPRAY)
        assertTrue(cleared.graffitiLayer.strokes.isEmpty())
        assertEquals(2, cleared.drawingLayer.strokes.size)
    }

    @Test
    fun emblemResetKeepsItOnAndTheDiceKeepSizeAndBlending() {
        val custom = start.updateEmblem {
            it.copy(isEnabled = true, motif = EmblemMotif.LOTUS, size = 0.9, rotation = 45.0, blendMode = LayerBlendMode.ADD, opacity = 0.5)
        }.emblemLayer
        assertEquals(EmblemLayerSettings(isEnabled = true), custom.reset())
        repeat(50) { i ->
            val r = custom.randomized(kotlin.random.Random(i))
            assertEquals(0.9, r.size, 0.0)
            assertEquals(45.0, r.rotation, 0.0)
            assertEquals(LayerBlendMode.ADD, r.blendMode)
            assertEquals(0.5, r.opacity, 0.0)
            assertTrue(r.petals in Editing.lotusPetals)
            assertTrue(r.seed in 1uL..999_999uL)
            assertTrue(r.colorSpread in 0.0..0.6 && r.glow in 0.2..0.8 && r.organic in 0.2..1.0)
            assertTrue(custom.newShape(kotlin.random.Random(i)).seed in 1uL..999_999uL)
        }
    }

    @Test
    fun saveSizesFollowTheFormatAndThePhoneScreen() {
        val phone = 2400.0 / 1080.0
        assertEquals(1024 to 1024, Editing.exportSize(SaveFormat.SQUARE, phone, large = false))
        assertEquals(2048 to 2560, Editing.exportSize(SaveFormat.FOUR_FIVE, phone, large = true))
        assertEquals(1024 to 2276, Editing.exportSize(SaveFormat.SCREEN, phone, large = false))
        // Taller than 21:9 is capped; below 1 (never passed by the app) gives a square.
        assertEquals(2048 to 4779, Editing.exportSize(SaveFormat.SCREEN, 3.0, large = true))
        assertEquals(1024 to 1024, Editing.exportSize(SaveFormat.SCREEN, 0.6, large = false))
        assertEquals("a-1024x2276.png", Editing.fileName(start, 1024, 2276).substringAfterLast('-').let { "a-$it" })
        assertTrue(Editing.fileName(start, 1024, 1024) == Editing.fileName(start))
    }

    @Test
    fun framingMovesAndZoomsAroundTheFingers() {
        val moved = Framing().moved(0.1, -0.2)
        assertEquals(0.6, moved.centreX, 1e-9)
        assertEquals(0.3, moved.centreY, 1e-9)
        // Zooming around the centre keeps it; around a point the centre moves away from that point.
        assertEquals(Framing(zoom = 2.0), Framing().zoomedAround(0.5, 0.5, 2.0))
        val z = Framing().zoomedAround(0.25, 0.5, 2.0)
        assertEquals(0.75, z.centreX, 1e-9)
        // Limits: zoom 0.25 to 4, centre inside the frame.
        assertEquals(Framing.MAX_ZOOM, Framing(zoom = 3.0).zoomedAround(0.5, 0.5, 10.0).zoom, 1e-9)
        assertEquals(1.0, Framing().moved(5.0, 0.0).centreX, 1e-9)
    }
}
