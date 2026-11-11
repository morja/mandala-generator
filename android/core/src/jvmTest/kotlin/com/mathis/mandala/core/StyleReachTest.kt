package com.mathis.mandala.core

import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every style stays within the reach the renderer gives its layer buffer on a canvas larger than
 * the mandala (`MandalaRenderer.styleReach`); a style drawing farther would show a hard edge there.
 */
class StyleReachTest {
    @Test
    fun everyStyleStaysWithinItsReach() {
        val side = 800
        val radius = side * 0.25 * 0.72
        val tooFar = ArrayList<String>()
        for (style in MandalaStyle.entries) {
            val allowed = MandalaRenderer.styleReach(style)
            if (allowed >= MandalaRenderer.STYLE_REACH_CAP) continue
            var reach = 0.0
            for (strong in listOf(false, true)) for (seed in 1..2) {
                val v = if (strong) 1.0 else 0.2
                val layer = StyleLayer(style = style, complexity = v, density = v, ripple = v, glowIntensity = 0.0, wash = 0.0, abstractLevel = 0.0, seed = seed.toULong())
                val params = MandalaParameters(layers = listOf(layer), baseLayer = BaseLayerSettings(isEnabled = false), effectsLayer = EffectsLayerSettings(isEnabled = false))
                val img = MandalaRenderer.render(params, RenderOptions(outputSize = side, supersample = false, framing = Framing(zoom = 0.25), threads = 4)).image
                for (y in 0 until side) for (x in 0 until side) {
                    val o = (y * side + x) * 3
                    if (img.get(o) > 1 || img.get(o + 1) > 1 || img.get(o + 2) > 1) {
                        val dx = x + 0.5 - side / 2.0
                        val dy = y + 0.5 - side / 2.0
                        reach = max(reach, sqrt(dx * dx + dy * dy) / radius)
                    }
                }
            }
            if (reach > allowed) tooFar += "${style.name} reaches %.2f, allowed %.2f".format(reach, allowed)
        }
        assertTrue(tooFar.isEmpty(), tooFar.joinToString("; "))
    }
}
