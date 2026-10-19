package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs in the `memoryTest` task with a 256 MB heap, the budget of a typical Android app.
 * Worst case the app allows: five layers (its maximum), the bottom one full size, all with strong
 * glow, wash and the heavy painted effect.
 */
class MemoryBudgetTest {
    private val fiveHeavyLayers = run {
        val styles = listOf("waveInterference", "spirograph", "roseCurves", "hypocycloid", "phyllotaxis")
        val layers = styles.mapIndexed { i, style ->
            """{"style":"$style","scale":${if (i == 0) 1.0 else 0.7},"complexity":0.8,"glowIntensity":0.9,
               "wash":0.3,"abstractLevel":0.8,"blendMode":"${if (i % 2 == 1) "add" else "screen"}","seed":${i + 1}}"""
        }
        """{"layers":[${layers.joinToString(",")}],"baseLayer":{"isEnabled":false},"effectsLayer":{"isEnabled":false}}"""
    }

    @Test
    fun exports1024WithFiveHeavyLayersWithin256MbHeap() {
        val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        assertTrue(maxHeapMb <= 256, "run this test through the memoryTest task (heap limit is $maxHeapMb MB)")
        val params = SettingsJson.decode(fiveHeavyLayers)
        assertEquals(5, params.layers.size)
        val result = MandalaRenderer.render(params, RenderOptions(outputSize = 1024, threads = 4))
        assertEquals(1024, result.image.width)
        assertTrue(result.warnings.isEmpty(), result.warnings.joinToString())
    }
}
