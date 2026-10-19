package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs in the `memoryTest` task with a 256 MB heap, the budget of a typical Android app.
 * The scene uses the most memory-hungry paths: a full-size layer, glow, wash and the heavy painted effect.
 */
class MemoryBudgetTest {
    private val twoLayers = """
        {"layers":[
          {"style":"waveInterference","scale":1.0,"complexity":0.8,"paletteIndex":11,"glowIntensity":0.6,
           "abstractLevel":0.3,"seed":0},
          {"style":"spirograph","scale":0.6,"paletteIndex":5,"glowIntensity":0.9,"wash":0.3,"abstractLevel":0.8,
           "opacity":0.7,"blendMode":"add","seed":99}
        ],"baseLayer":{"isEnabled":false},"effectsLayer":{"isEnabled":false},"seed":13730275572896771193}
    """

    @Test
    fun exports1024WithTwoLayersWithin256MbHeap() {
        val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        assertTrue(maxHeapMb <= 256, "run this test through the memoryTest task (heap limit is $maxHeapMb MB)")
        val result = MandalaRenderer.render(SettingsJson.decode(twoLayers), RenderOptions(outputSize = 1024, threads = 4))
        assertEquals(1024, result.image.width)
        assertTrue(result.warnings.isEmpty(), result.warnings.joinToString())
    }
}
