package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertTrue

class SmootherTest {
    private fun settings(amount: Double) = SettingsJson.decode(
        """{"effectsLayer":{"isEnabled":true,"vignette":0,"smoother":$amount}}""",
    ).effectsLayer

    private fun apply(image: Image8, amount: Double): Image8 = Parallel(2).use {
        EffectsLayer(it, LayerEffects(it)).apply(image, settings(amount))
    }

    @Test
    fun savedStrengthSurvivesRoundTrip() {
        val params = SettingsJson.decode("""{"effectsLayer":{"smoother":0.75}}""")
        assertTrue(SettingsJson.encode(params).contains("\"smoother\":0.75"))
    }

    @Test
    fun softensFineDetailAcrossStripBoundaries() {
        val source = Image8(160, 1100, Encoding.SRGB)
        for (y in 0 until source.height) for (x in 0 until source.width) for (c in 0..2) {
            source.data[(y * source.width + x) * 3 + c] = (if ((x + y) % 2 == 0) 255 else 0).toByte()
        }
        assertTrue(source.data.contentEquals(apply(source, 0.0).data))
        val soft = apply(source, 1.0)
        for (y in listOf(100, 510, 511, 512, 513, 1023, 1024)) {
            val value = soft.get((y * source.width + 80) * 3)
            assertTrue(value in 80..190, "fine detail still sharp at row $y: $value")
        }
    }

    @Test
    fun preservesSolidColourAtTheEdges() {
        val source = Image8(160, 180, Encoding.SRGB)
        source.data.fill(140.toByte())
        val soft = apply(source, 1.0)
        for (i in soft.data.indices) assertTrue(kotlin.math.abs(soft.get(i) - 140) <= 1)
    }
}
