package com.mathis.mandala.app

import com.mathis.mandala.app.PlayField.Mode
import com.mathis.mandala.app.PlayField.Touch
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaStyle
import com.mathis.mandala.core.StyleLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/** The play field (UX-1 19): the middle is the mandala as it was, away from it many settings change, all within range. */
class PlayFieldTest {
    private val base = MandalaParameters(layers = listOf(StyleLayer(style = MandalaStyle.FLORAL, paletteIndex = 4, symmetry = 6)))
    private val land = PlayField.Landscape(seed = 9, base = base, paletteCount = 30)

    @Test
    fun theMiddleIsTheMandalaAsItWas() {
        for (mode in Mode.entries) assertEquals(base, PlayField.apply(base, mode, land, Touch()))
    }

    @Test
    fun everyVisibleLayerMovesAndHiddenLayersStay() {
        val hidden = StyleLayer(style = MandalaStyle.SPIROGRAPH, isEnabled = false)
        val two = MandalaParameters(layers = listOf(hidden, base.layers[0], StyleLayer(style = MandalaStyle.HYPOCYCLOID, paletteIndex = 2)))
        val land = PlayField.Landscape(seed = 3, base = two, paletteCount = 30)
        for (mode in Mode.entries) {
            val moved = PlayField.apply(two, mode, land, Touch(0.95, 0.1))
            assertEquals(hidden, moved.layers[0])
            assertTrue("$mode leaves layer 2", moved.layers[1] != two.layers[1])
            assertTrue("$mode leaves layer 3", moved.layers[2] != two.layers[2])
        }
    }

    @Test
    fun aNewLandscapeHopsOutOfTheMiddleAndStaysElsewhere() {
        for (seed in 1..20) {
            val hop = PlayField.hopOutOfTheMiddle(Touch(), Random(seed))
            val distance = hypot(hop.x - 0.5, hop.y - 0.5)
            assertTrue("seed $seed lands at $distance", distance in 0.15..0.3)
        }
        val away = Touch(0.8, 0.2)
        assertEquals(away, PlayField.hopOutOfTheMiddle(away, Random(1)))
    }

    @Test
    fun awayFromTheMiddleManySettingsChangeWithinRange() {
        for (mode in Mode.entries) {
            val corners = listOf(Touch(0.0, 0.0), Touch(1.0, 0.0), Touch(0.0, 1.0), Touch(1.0, 1.0), Touch(0.5, 0.0), Touch(1.0, 0.5))
            val results = corners.map { PlayField.apply(base, mode, land, it) }
            for (p in results) {
                val l = p.layers[0]
                assertTrue(l.symmetry in 1..8 && l.complexity in 0.0..1.0 && l.density in 0.0..1.0 && l.ripple in 0.0..1.0)
                assertTrue(l.colorOffset in 0.0..1.0 && l.glowIntensity in 0.0..1.0 && l.scale in 0.5..1.15)
            }
            assertTrue("$mode changes nothing", results.any { it != base })
        }
    }

    @Test
    fun theLandscapeChangesStylesAndTheCompassKeepsThem() {
        val styles = (0..10).flatMap { i -> (0..10).map { j -> PlayField.apply(base, Mode.MAP, land, Touch(i / 10.0, j / 10.0)).layers[0].style } }.toSet()
        assertTrue("only $styles", styles.size >= 3)
        assertEquals(MandalaStyle.FLORAL, PlayField.apply(base, Mode.COMPASS, land, Touch(1.0, 0.0)).layers[0].style)
    }
}
