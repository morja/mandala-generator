package com.mathis.mandala.app

import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.MandalaStyle
import com.mathis.mandala.core.RenderOptions
import com.mathis.mandala.core.StyleLayer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Density and Ripple are shown only for styles where they change the image (TUNE-1). Renders every
 * offered style with a low and a high value and checks that the tables in [Editing] match the renderer.
 */
class StyleControlsTest {
    private fun render(style: MandalaStyle, density: Double, ripple: Double): ByteArray {
        val layer = StyleLayer(style = style, scale = 0.9, density = density, ripple = ripple, abstractLevel = 0.0, seed = 1234567uL)
        val params = MandalaParameters(layers = listOf(layer), seed = 13730275572896771193uL, outputSize = 128)
        return MandalaRenderer.render(params, RenderOptions(outputSize = 128, supersample = false, threads = 2)).image.data
    }

    @Test
    fun densityIsShownExactlyWhereItChangesTheImage() {
        val changes = Editing.styles.filter { !render(it, 0.15, 0.0).contentEquals(render(it, 0.95, 0.0)) }.toSet()
        assertEquals(changes, Editing.densityStyles.intersect(Editing.styles.toSet()))
    }

    @Test
    fun rippleIsShownExactlyWhereItChangesTheImage() {
        val changes = Editing.styles.filter { !render(it, 0.5, 0.0).contentEquals(render(it, 0.5, 0.8)) }.toSet()
        assertEquals(changes, Editing.rippleStyles.intersect(Editing.styles.toSet()))
    }
}
