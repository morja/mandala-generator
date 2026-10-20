package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs in the `memoryTest` task with a 256 MB heap, the budget of a typical Android app.
 * Worst case the app allows: five layers (its maximum), the bottom one full size, all with strong
 * glow, wash and the heavy painted effect, the line-heaviest styles, a gradient background and the
 * most memory-hungry effects (bloom, clarity, relief, stars, glitter), plus hand-drawn lines and
 * spray paint (the graffiti layer keeps a float colour and alpha plane at buffer size), and an emblem
 * with glow and normal blending (which blurs its alpha as well as its colour).
 */
class MemoryBudgetTest {
    private val fiveHeavyLayers = run {
        val styles = listOf("waveInterference", "hyperboloid", "universe", "fractal", "starBurst")
        val layers = styles.mapIndexed { i, style ->
            """{"style":"$style","scale":${if (i == 0) 1.0 else 0.7},"complexity":0.8,"glowIntensity":0.9,
               "wash":0.3,"abstractLevel":0.8,"blendMode":"${if (i % 2 == 1) "add" else "screen"}","seed":${i + 1}}"""
        }
        val stroke = """"xs":[0.1,0.5,0.9,0.5],"ys":[0.2,0.8,0.3,0.5]"""
        val hand = """"drawingLayer":{"isEnabled":true,"symmetry":16,"glowIntensity":1,"strokeWeight":1,"strokes":[{$stroke}]},""" +
            """"graffitiLayer":{"isEnabled":true,"symmetry":16,"softness":1,"strokes":[{$stroke,"brushSize":0.2}]},""" +
            """"emblemLayer":{"isEnabled":true,"motif":"treeOfLife","branching":1,"organic":1,"frame":"lotus","size":1,"glow":1,"blendMode":"normal"}"""
        """{"layers":[${layers.joinToString(",")}],"baseLayer":{"isEnabled":true,"type":"gradient"},"effectsLayer":{"isEnabled":true,"vignette":0.3,"bloom":0.5,"localContrast":0.3,"relief":0.4,"grain":0.3,"stars":0.3,"glitter":0.3,"dimming":0.3,"highlights":0.3},$hand}"""
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

    @Test
    fun exports2048WithoutSupersamplingWithin256MbHeap() {
        val params = SettingsJson.decode(fiveHeavyLayers)
        val result = MandalaRenderer.render(params, RenderOptions(outputSize = 2048, supersample = false, threads = 4))
        assertEquals(2048, result.image.width)
        assertEquals(Encoding.SRGB, result.image.encoding)
    }

    /**
     * The tallest Pro portrait the app saves (a 21:9 phone screen, PLAN-2, ADR-0010), zoomed in as
     * far as the app allows: every layer then covers the whole canvas plus its margin. Fits only
     * because every full-size step works in strips.
     */
    @Test
    fun exportsTheProPortraitZoomedInWithin256MbHeap() {
        val params = SettingsJson.decode(fiveHeavyLayers)
        val options = RenderOptions(width = 2048, height = 4779, supersample = false, framing = Framing(zoom = Framing.MAX_ZOOM), threads = 4)
        val result = MandalaRenderer.render(params, options)
        assertEquals(4779, result.image.height)
    }

    /** The free portrait: 1024 wide, drawn at twice the size like the free square. */
    @Test
    fun exportsTheFreePortraitWithin256MbHeap() {
        val params = SettingsJson.decode(fiveHeavyLayers)
        val result = MandalaRenderer.render(params, RenderOptions(width = 1024, height = 2389, framing = Framing(centreY = 0.6), threads = 4))
        assertEquals(2389, result.image.height)
    }

    @Test
    fun largeExportMatchesTheNormalExportScaledDown() {
        val params = SettingsJson.decode("""{"layers":[{"style":"roseCurves","scale":0.8,"seed":3}]}""")
        val normal = MandalaRenderer.render(params, RenderOptions(outputSize = 128, threads = 2)).image
        val large = MandalaRenderer.render(params, RenderOptions(outputSize = 256, supersample = false, threads = 2)).image
        // Same picture: the large one is the normal export's internal image before scaling down.
        val mean = (0 until 128 * 128 * 3).sumOf { i ->
            val y = i / (128 * 3); val x = (i / 3) % 128; val c = i % 3
            val l = (0..1).sumOf { dy -> (0..1).sumOf { dx -> large.get(((2 * y + dy) * 256 + 2 * x + dx) * 3 + c) } } / 4
            kotlin.math.abs(l - normal.get(i))
        } / (128.0 * 128 * 3)
        assertTrue(mean < 6.0, "mean difference $mean")
    }
}
