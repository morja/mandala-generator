package com.mathis.mandala.editing

import com.mathis.mandala.core.BaseLayerSettings
import com.mathis.mandala.core.DrawingLayerSettings
import com.mathis.mandala.core.EffectsLayerSettings
import com.mathis.mandala.core.EmblemGlowMode
import com.mathis.mandala.core.EmblemLayerSettings
import com.mathis.mandala.core.GraffitiLayerSettings
import com.mathis.mandala.core.MandalaStyle
import com.mathis.mandala.core.ProFeatures
import com.mathis.mandala.core.StyleLayer
import com.mathis.mandala.core.TextLayerSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Start fresh": the plain mandala both phones set from the menu. */
class FreshTest {

    @Test
    fun oneCalmLayerAndEverythingElseAtItsDefaults() {
        val p = Editing.fresh()

        // One Spirograph layer at the defaults, in an offered palette, centred.
        assertEquals(
            listOf(StyleLayer(style = MandalaStyle.SPIROGRAPH, scale = 0.8, paletteIndex = Editing.FRESH_PALETTE, seed = Editing.FRESH_SEED)),
            p.layers,
        )
        assertTrue(Editing.FRESH_PALETTE in Editing.offeredPalettes)
        assertTrue(MandalaStyle.SPIROGRAPH in Editing.styles)

        // Text, emblem, scene, light, drawing and spray off and at their defaults.
        assertEquals(TextLayerSettings(), p.textLayer)
        assertEquals(EmblemLayerSettings(glowMode = EmblemGlowMode.LIGHT), p.emblemLayer)
        assertEquals(BaseLayerSettings(), p.baseLayer)
        assertEquals(EffectsLayerSettings(), p.effectsLayer)
        assertEquals(DrawingLayerSettings(), p.drawingLayer)
        assertEquals(GraffitiLayerSettings(), p.graffitiLayer)
        assertEquals(Editing.EXPORT_SIZE, p.outputSize)
    }

    @Test
    fun isFreeAndTheSameEveryTime() {
        assertEquals(emptySet(), ProFeatures.of(Editing.fresh()))
        assertEquals(Editing.fresh(), Editing.fresh())
    }
}
