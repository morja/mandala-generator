package com.mathis.mandala.app

import com.mathis.mandala.app.Editing.addLayer
import com.mathis.mandala.app.Editing.movedBy
import com.mathis.mandala.app.Editing.switchedTo
import com.mathis.mandala.core.BaseLayerSettings
import com.mathis.mandala.core.BaseLayerType
import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.EmblemLayerSettings
import com.mathis.mandala.core.StyleLayer
import com.mathis.mandala.core.TextLayerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Palettes, background types and slider ranges offered in the controls (TUNE-1). */
class PickersAndRangesTest {
    @Test
    fun elevenPalettesAreOfferedInTheReviewedOrder() {
        val names = Editing.offeredPalettes.map { BuiltInPalettes.all[it].name }
        assertEquals(
            listOf("Psychedelic", "Neon City", "Tropical", "Sunset", "Lava", "Gold", "Earth", "Toxic", "Bio Glow", "Ice", "Cosmic", "Aurora", "Violet", "Rose Quartz", "Silver"),
            names,
        )
        // The hidden ones stay where they are, so saved indices keep their look.
        assertEquals(21, BuiltInPalettes.all.size)
    }

    @Test
    fun aHiddenPaletteInUseIsStillShown() {
        assertEquals(Editing.offeredPalettes, Editing.paletteChoices(0))
        assertEquals(Editing.offeredPalettes + 5, Editing.paletteChoices(5)) // Prism
        // Mac custom palettes (after the built-ins) are not built in here; the picker cannot show them.
        assertEquals(Editing.offeredPalettes, Editing.paletteChoices(BuiltInPalettes.all.size))
    }

    @Test
    fun addedLayersTakeOnlyOfferedPalettes() {
        var p = Editing.initial(1uL)
        repeat(4) { p = p.addLayer(it.toULong()).first }
        assertTrue(p.layers.all { it.paletteIndex in Editing.offeredPalettes })
        assertEquals(p.layers.size, p.layers.map { it.paletteIndex }.distinct().size)
        // Above a layer with a hidden palette the offered order starts again.
        assertEquals(Editing.offeredPalettes[3], Editing.nextPalette(12))
        assertEquals(Editing.offeredPalettes[0], Editing.nextPalette(Editing.offeredPalettes[Editing.offeredPalettes.size - 3]))
    }

    @Test
    fun grainIsOfferedOnlyWhileItIsUsed() {
        assertFalse(BaseLayerType.GRAIN in Editing.backgroundChoices(BaseLayerSettings(type = BaseLayerType.GRADIENT)))
        assertTrue(BaseLayerType.GRAIN in Editing.backgroundChoices(BaseLayerSettings(isEnabled = true, type = BaseLayerType.GRAIN)))
    }

    @Test
    fun aSwitchedOnBackgroundStartsBrighterUnlessItsColoursWereChanged() {
        val on = BaseLayerSettings().switchedTo(BaseLayerType.COLOR)
        assertTrue(on.isEnabled)
        assertEquals(BaseLayerType.COLOR, on.type)
        assertEquals(Editing.BACKGROUND_START_BRIGHTNESS, on.brightness, 0.0)
        assertEquals(Editing.BACKGROUND_START_BRIGHTNESS2, on.brightness2, 0.0)
        // The decoder defaults stay as in the macOS app.
        assertEquals(0.18, BaseLayerSettings().brightness, 0.0)
        assertEquals(0.04, BaseLayerSettings().brightness2, 0.0)

        val own = BaseLayerSettings(brightness = 0.5).switchedTo(BaseLayerType.GRADIENT)
        assertEquals(0.5, own.brightness, 0.0)
        assertEquals(0.04, own.brightness2, 0.0)
    }

    @Test
    fun defaultsLieInsideTheSliderRanges() {
        val layer = StyleLayer()
        assertTrue(layer.scale in Editing.Ranges.layerScale)
        assertTrue(layer.complexity in Editing.Ranges.layerComplexity)
        assertTrue(layer.abstractLevel in Editing.Ranges.layerPainted)
        assertTrue(layer.brightness in Editing.Ranges.layerBrightness)
        val em = EmblemLayerSettings()
        assertTrue(em.size in Editing.Ranges.emblemSize)
        assertTrue(em.weight in Editing.Ranges.emblemWeight)
        val text = TextLayerSettings()
        assertTrue(text.fontSize in Editing.Ranges.textSize)
        assertTrue(text.fontWidth in Editing.Ranges.textWidth)
        assertTrue(text.brightness in Editing.Ranges.textBrightness)
        assertTrue(text.cloudScale in Editing.Ranges.textCloudSize)
        assertTrue(Editing.BACKGROUND_START_BRIGHTNESS in Editing.Ranges.backgroundBrightness)
    }

    @Test
    fun movingKeepsSavedValuesOutsideTheRangesUntilTheyAreZoomed() {
        val small = StyleLayer(scale = 0.1)
        assertEquals(0.1, small.movedBy(0.2, 0.0).scale, 0.0)
        assertEquals(0.12, small.movedBy(0.0, 0.0, zoom = 1.2).scale, 1e-9) // zooming in from outside is fine
        assertEquals(0.1, small.movedBy(0.0, 0.0, zoom = 0.5).scale, 1e-9) // but never further out
        val big = EmblemLayerSettings(size = 1.0)
        assertEquals(1.0, big.movedBy(0.1, 0.1).size, 0.0)
        assertEquals(0.9, EmblemLayerSettings(size = 0.8).movedBy(0.0, 0.0, zoom = 2.0).size, 1e-9)
    }
}
