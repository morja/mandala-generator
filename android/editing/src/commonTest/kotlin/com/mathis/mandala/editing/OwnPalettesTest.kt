package com.mathis.mandala.editing

import com.mathis.mandala.editing.Editing.changeOwnPalettes
import com.mathis.mandala.editing.Editing.withOwnPalette
import com.mathis.mandala.editing.Randomize.randomLayer
import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.CustomPalette
import com.mathis.mandala.core.EmblemLayerSettings
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.OwnPalettes
import com.mathis.mandala.core.StyleLayer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Own palettes in the editor: choosing, saving again, deleting (ADR-0021). */
class OwnPalettesTest {
    private val gold = CustomPalette("G", "Gold copy", OwnPalettes.stopsOf(BuiltInPalettes.all[17]))
    private val other = CustomPalette("O", "Other", OwnPalettes.defaultStops())

    @Test
    fun choosingAnOwnPaletteKeepsTheLookAlikeAsFallback() {
        val layer = StyleLayer(paletteIndex = 0).withOwnPalette(gold)
        assertEquals(17, layer.paletteIndex)
        assertEquals(gold, layer.customPalette)
    }

    @Test
    fun savingAgainReachesEveryUseAndDeletingFallsBack() {
        val params = MandalaParameters(
            layers = listOf(StyleLayer().withOwnPalette(gold), StyleLayer().withOwnPalette(other), StyleLayer(paletteIndex = 4)),
            emblemLayer = EmblemLayerSettings().withOwnPalette(gold),
        )
        val renamed = gold.copy(name = "Mine")
        val saved = params.changeOwnPalettes { if (it.id == "G") renamed else it }
        assertEquals(renamed, saved.layers[0].customPalette)
        assertEquals(renamed, saved.emblemLayer.customPalette)
        assertEquals(params.layers[1], saved.layers[1])

        val deleted = saved.changeOwnPalettes { if (it.id == "G") null else it }
        assertNull(deleted.layers[0].customPalette)
        assertEquals(17, deleted.layers[0].paletteIndex)
        assertNull(deleted.emblemLayer.customPalette)
        assertEquals(other, deleted.layers[1].customPalette)
        assertEquals(4, deleted.layers[2].paletteIndex)
    }

    @Test
    fun theDiceGiveABuiltInPalette() {
        val params = MandalaParameters(layers = listOf(StyleLayer().withOwnPalette(gold)))
        assertNull(params.randomLayer(0, Random(3)).layers[0].customPalette)
    }
}
