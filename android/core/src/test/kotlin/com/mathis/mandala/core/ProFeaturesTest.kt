package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The line between free and Pro (ADR-0016). */
class ProFeaturesTest {
    private fun layers(n: Int) = List(n) { StyleLayer(seed = it.toULong()) }

    @Test
    fun twoLayersWithEffectsAndBackgroundAreFree() {
        val params = MandalaParameters(
            layers = layers(2),
            effectsLayer = EffectsLayerSettings(isEnabled = true, bloom = 1.0, glitter = 0.5),
            baseLayer = BaseLayerSettings(isEnabled = true),
            drawingLayer = DrawingLayerSettings(isEnabled = true),
        )
        assertEquals(emptySet(), ProFeatures.of(params))
    }

    @Test
    fun onlyWhatShowsCounts() {
        val params = MandalaParameters(
            layers = layers(2) + StyleLayer(isEnabled = false, offsetX = 0.3),
            emblemLayer = EmblemLayerSettings(isEnabled = false),
            textLayer = TextLayerSettings(isEnabled = true, text = "  "),
        )
        assertEquals(emptySet(), ProFeatures.of(params))
    }

    @Test
    fun eachProFeatureIsFound() {
        val params = MandalaParameters(
            layers = layers(3).mapIndexed { i, l -> if (i == 1) l.copy(offsetY = -0.2) else l },
            emblemLayer = EmblemLayerSettings(isEnabled = true),
            textLayer = TextLayerSettings(isEnabled = true, text = "Be still"),
        )
        assertEquals(ProFeature.entries.toSet(), ProFeatures.of(params))
    }

    @Test
    fun removingKeepsTheBottomTwoAndHidesTheRest() {
        val params = MandalaParameters(
            layers = listOf(StyleLayer(isEnabled = false, seed = 9uL)) + layers(4).map { it.copy(offsetX = 0.1) },
            emblemLayer = EmblemLayerSettings(isEnabled = true),
            textLayer = TextLayerSettings(isEnabled = true, text = "Be still"),
        )
        val free = ProFeatures.removed(params)
        assertEquals(emptySet(), ProFeatures.of(free))
        assertEquals(params.layers.size, free.layers.size)
        assertEquals(listOf(false, true, true, false, false), free.layers.map { it.isEnabled })
        assertTrue(free.layers.all { it.offsetX == 0.0 })
        assertEquals("Be still", free.textLayer.text)
    }
}
