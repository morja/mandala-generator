package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertContentEquals

/** Layer images kept between renders give the same image as drawing every layer again. */
class LayerCacheTest {
    private val threeLayers = SettingsJson.decode(
        """{"seed":11,"layers":[
             {"style":"roseCurves","scale":0.9,"glowIntensity":0.6,"wash":0.2},
             {"style":"spiderWeb","scale":0.7,"glowIntensity":0.4,"blendMode":"multiply","rotation":0.1},
             {"style":"floral","scale":0.5,"glowIntensity":0.8,"abstractLevel":0.3,"offsetX":0.1,"seed":5}],
           "baseLayer":{"isEnabled":true,"type":"gradient","isRadial":true}}""",
    )

    private fun render(params: MandalaParameters, cache: LayerCache?, framing: Framing = Framing()) =
        MandalaRenderer.render(params, RenderOptions(outputSize = 160, threads = 2, framing = framing, layerCache = cache)).image.data

    @Test
    fun changingOneLayerGivesTheSameImageAsAFreshRender() {
        val cache = LayerCache()
        render(threeLayers, cache)
        val changed = threeLayers.copy(layers = threeLayers.layers.toMutableList().also { it[1] = it[1].copy(symmetry = 5) })
        assertContentEquals(render(changed, null), render(changed, cache))
    }

    @Test
    fun reorderingAndFramingGiveTheSameImageAsAFreshRender() {
        val cache = LayerCache()
        render(threeLayers, cache)
        val reordered = threeLayers.copy(layers = threeLayers.layers.reversed())
        assertContentEquals(render(reordered, null), render(reordered, cache))
        val framing = Framing(centreX = 0.4, zoom = 1.3)
        assertContentEquals(render(reordered, null, framing), render(reordered, cache, framing))
        val reseeded = reordered.copy(seed = 12uL)
        assertContentEquals(render(reseeded, null), render(reseeded, cache))
    }
}
