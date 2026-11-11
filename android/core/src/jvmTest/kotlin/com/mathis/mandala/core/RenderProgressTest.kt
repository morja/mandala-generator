package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The share of the work done that a render reports, for the app's progress bar. */
class RenderProgressTest {
    private fun shares(json: String, options: RenderOptions = RenderOptions(outputSize = 256)): List<Double> {
        val shares = ArrayList<Double>()
        MandalaRenderer.render(SettingsJson.decode(json), options.copy(threads = 2, onProgress = { shares += it }))
        return shares
    }

    private fun assertRisesToOne(shares: List<Double>) {
        assertTrue(shares.size > 2, "only ${shares.size} reports")
        assertTrue(shares.zipWithNext().all { (a, b) -> b > a }, "not rising: $shares")
        assertTrue(shares.first() > 0.0)
        assertEquals(1.0, shares.last())
    }

    @Test
    fun risesToOneOverLayersEmblemAndEffects() {
        assertRisesToOne(
            shares(
                """{"layers":[{"style":"starBurst","wash":0.3,"abstractLevel":0.5,"seed":1},{"style":"hyperboloid","seed":2}],
                   "baseLayer":{"isEnabled":true,"type":"gradient","isRadial":true},
                   "emblemLayer":{"isEnabled":true,"motif":"om"},"effectsLayer":{"isEnabled":true,"bloom":0.3}}""",
            ),
        )
    }

    @Test
    fun risesToOneWithASingleLayerOnASolidBackgroundInAPortrait() {
        assertRisesToOne(
            shares(
                """{"layers":[{"style":"roseCurves","seed":3}],"baseLayer":{"isEnabled":true,"type":"color"}}""",
                RenderOptions(width = 256, height = 512, supersample = false),
            ),
        )
    }

    @Test
    fun layersTakeMostOfTheBar() {
        val shares = shares("""{"layers":[{"style":"roseCurves","seed":3},{"style":"spirograph","seed":4}]}""")
        // Without background or steps above the layers, all but the finishing step belongs to the layers.
        assertTrue(shares.count { it < 0.9 } >= 4, "too few reports during the layers: $shares")
    }
}
