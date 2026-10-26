package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which layers above the mandala the scene effects reach (ADR-0018). */
class EffectsScopeTest {
    private val options = RenderOptions(outputSize = 192, supersample = false, threads = 2)
    private val effects = EffectsLayerSettings(isEnabled = true, sepia = 0.8, grain = 0.4, vignette = 1.0)

    private fun render(params: MandalaParameters) = MandalaRenderer.render(params, options).image.data

    @Test
    fun leavingTheEmblemOutChangesOnlyPicturesWithAnEmblem() {
        val emblem = MandalaParameters(emblemLayer = EmblemLayerSettings(isEnabled = true), effectsLayer = effects)
        val left = emblem.copy(effectsLayer = effects.copy(affectsEmblem = false))
        assertFalse(render(emblem).contentEquals(render(left)), "the emblem still went through the effects")
        val noEmblem = emblem.copy(emblemLayer = EmblemLayerSettings(isEnabled = false))
        assertTrue(render(noEmblem).contentEquals(render(noEmblem.copy(effectsLayer = left.effectsLayer))))
    }

    @Test
    fun defaultsMatchTheOrderOfAdr17() {
        val emblem = MandalaParameters(emblemLayer = EmblemLayerSettings(isEnabled = true), effectsLayer = effects)
        val explicit = emblem.copy(effectsLayer = effects.copy(affectsEmblem = true, affectsText = false))
        assertTrue(render(emblem).contentEquals(render(explicit)))
    }

    @Test
    fun oldSettingsWithoutTheFieldsKeepTheirLook() {
        val decoded = SettingsJson.decode("""{"effectsLayer":{"isEnabled":true,"sepia":0.5}}""").effectsLayer
        assertTrue(decoded.affectsEmblem)
        assertFalse(decoded.affectsText)
    }
}
