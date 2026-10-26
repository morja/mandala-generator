package com.mathis.mandala.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Frames of the turning layers (UX-1 12): turned from the kept layer images, and a seamless loop. */
class LayerAnimationTest {
    private val params = SettingsJson.decode(
        """{"seed":7,"layers":[
             {"style":"roseCurves","scale":0.9,"glowIntensity":0.6,"wash":0.2,"opacity":0.8},
             {"style":"spiderWeb","scale":0.7,"glowIntensity":0.4,"blendMode":"multiply","rotation":0.1},
             {"style":"floral","scale":0.5,"glowIntensity":0.8,"abstractLevel":0.3,"seed":5}],
           "baseLayer":{"isEnabled":true,"type":"gradient","isRadial":true},
           "emblemLayer":{"isEnabled":true,"motif":"lotus","size":0.4,"glow":0.5},
           "effectsLayer":{"isEnabled":true,"bloom":0.4,"stars":0.3,"glitter":0.3,"vignette":0.3},
           "drawingLayer":{"isEnabled":true,"symmetry":6,"glowIntensity":0.5,"strokes":[{"xs":[0.2,0.5],"ys":[0.3,0.6]}]},
           "textLayer":{"isEnabled":true,"text":"Be still.","glow":1.5,"shadowOpacity":0.5,"cloudOpacity":0.4}}""",
    )

    private class BoxRenderer : TextRenderer {
        override fun metrics(font: TextFont) = TextRenderer.Metrics(font.sizePx * 0.8f, font.sizePx * 0.2f, 0f)
        override fun width(text: String, font: TextFont) = text.length * font.sizePx * 0.5f
        override fun drawLines(width: Int, height: Int, lines: List<PlacedLine>): FloatArray {
            val mask = FloatArray(width * height)
            for (l in lines) {
                val top = (l.baseline - l.font.sizePx * 0.8f).toInt().coerceAtLeast(0)
                val bottom = l.baseline.toInt().coerceAtMost(height)
                val right = (l.x + width(l.text, l.font)).toInt().coerceAtMost(width)
                for (y in top until bottom) for (x in l.x.toInt().coerceAtLeast(0) until right) mask[y * width + x] = 0.7f
            }
            return mask
        }
    }

    private val base = RenderOptions(width = 120, height = 200, supersample = false, threads = 2, textRenderer = BoxRenderer())

    @Test
    fun aFrameFromTheKeptPartsIsTheFrameDrawnAfresh() {
        val cache = LayerCache()
        val options = LayerAnimation.options(base, cache)
        val frames = 12
        MandalaRenderer.render(LayerAnimation.frame(params, 0, frames), options)
        for (f in listOf(1, 5, 11)) {
            val p = LayerAnimation.frame(params, f, frames)
            val kept = MandalaRenderer.render(p, options).image.data
            val fresh = MandalaRenderer.render(p, base.copy(turningLayers = true)).image.data
            // The glow of emblem and text is kept in fixed point, which may move a value by one step.
            val worst = maxDifference(kept, fresh)
            assertTrue(worst <= 1, "frame $f differs by $worst")
        }
    }

    @Test
    fun aTurnedLayerLooksTheSameWhetherOrNotAllLayersTurn() {
        val turned = params.copy(layers = params.layers.map { it.copy(rotation = 0.3) })
        assertContentEquals(
            MandalaRenderer.render(turned, base).image.data,
            MandalaRenderer.render(turned, base.copy(turningLayers = true)).image.data,
        )
    }

    @Test
    fun theLoopEndsWhereItStarts() {
        val start = LayerAnimation.at(params, 0.0).layers.map { it.rotation }
        val end = LayerAnimation.at(params, 1.0).layers.map { it.rotation }
        for ((a, b) in start.zip(end)) assertEquals(a, b, 1e-9)
        // Neighbouring layers turn in opposite directions.
        val quarter = LayerAnimation.at(params, 0.25).layers.map { it.rotation }
        assertEquals(0.25, quarter[0], 1e-9)
        assertEquals(0.85, quarter[1], 1e-9)
    }

    @Test
    fun aChangedMandalaDropsTheKeptParts() {
        val cache = LayerCache()
        val options = LayerAnimation.options(base, cache)
        MandalaRenderer.render(params, options)
        val other = params.copy(baseLayer = params.baseLayer.copy(type = BaseLayerType.SUNBURST), emblemLayer = params.emblemLayer.copy(size = 0.7))
        val kept = MandalaRenderer.render(other, options).image.data
        val fresh = MandalaRenderer.render(other, base.copy(turningLayers = true)).image.data
        assertTrue(maxDifference(kept, fresh) <= 1)
    }

    private fun maxDifference(a: ByteArray, b: ByteArray) = a.indices.maxOf { abs((a[it].toInt() and 0xFF) - (b[it].toInt() and 0xFF)) }
}
