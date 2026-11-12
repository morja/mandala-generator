package com.mathis.mandala.editing

import com.mathis.mandala.editing.PlayField.Mode
import com.mathis.mandala.editing.PlayField.Touch
import com.mathis.mandala.core.EffectsLayerSettings
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaStyle
import com.mathis.mandala.core.StyleLayer
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
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
            assertTrue(moved.layers[1] != two.layers[1], "$mode leaves layer 2")
            assertTrue(moved.layers[2] != two.layers[2], "$mode leaves layer 3")
        }
    }

    @Test
    fun aNewLandscapeHopsOutOfTheMiddleAndStaysElsewhere() {
        for (seed in 1..20) {
            val hop = PlayField.hopOutOfTheMiddle(Touch(), Random(seed))
            val distance = hypot(hop.x - 0.5, hop.y - 0.5)
            assertTrue(distance in 0.15..0.3, "seed $seed lands at $distance")
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
            assertTrue(results.any { it != base }, "$mode changes nothing")
        }
    }

    @Test
    fun theCompassLightHasNoStarsAndTheLandscapeHasStarsOnlyInRegions() {
        val lit = PlayField.apply(base, Mode.COMPASS, land, Touch(0.5, 0.0)).effectsLayer
        assertEquals(0.0, lit.stars, 0.0)
        assertTrue(lit.bloom > base.effectsLayer.bloom, "light brings bloom")
        val deep = PlayField.apply(base, Mode.COMPASS, land, Touch(0.5, 1.0)).effectsLayer
        assertTrue(deep.grain > 0 && deep.vignette > base.effectsLayer.vignette, "depth brings grain")
        val points = (0..20).flatMap { i -> (0..20).map { j -> Touch(i / 20.0, j / 20.0) } }
        for (seed in 1..5) {
            val land = PlayField.Landscape(seed = seed.toLong(), base = base, paletteCount = 30)
            val starry = points.count { PlayField.apply(base, Mode.MAP, land, it).effectsLayer.stars > 0 }
            assertTrue(starry < points.size / 2, "seed $seed: stars on $starry of ${points.size}")
        }
    }

    @Test
    fun theLandscapeChangesStylesAndTheCompassKeepsThem() {
        val styles = (0..10).flatMap { i -> (0..10).map { j -> PlayField.apply(base, Mode.MAP, land, Touch(i / 10.0, j / 10.0)).layers[0].style } }.toSet()
        assertTrue(styles.size >= 3, "only $styles")
        assertEquals(MandalaStyle.FLORAL, PlayField.apply(base, Mode.COMPASS, land, Touch(1.0, 0.0)).layers[0].style)
    }

    /** A dark starting scene: its effects on, with values that the switch must keep. */
    private val dark = base.copy(effectsLayer = EffectsLayerSettings(isEnabled = true, dimming = 0.8, vignette = 0.7, fade = 0.5, bloom = 0.1))

    @Test
    fun withTheEffectsOffTheSceneIsSwitchedOffWithItsValuesKept() {
        val land = PlayField.Landscape(seed = 9, base = dark, paletteCount = 30)
        val off = dark.effectsLayer.copy(isEnabled = false)
        for (mode in Mode.entries) {
            for (touch in listOf(Touch(), Touch(0.5, 0.0), Touch(0.5, 1.0), Touch(0.1, 0.9), Touch(1.0, 1.0))) {
                val on = PlayField.apply(dark, mode, land, touch)
                val shown = PlayField.apply(dark, mode, land, touch, effects = false)
                // No raising of bloom, vignette, stars or grain, and the layers play as with the effects on.
                assertEquals(off, shown.effectsLayer, "$mode at $touch")
                assertEquals(on.layers, shown.layers, "$mode at $touch")
                assertEquals(on.copy(effectsLayer = off), shown)
            }
        }
    }

    @Test
    fun backgroundEmblemAndTextStayWithTheEffectsOff() {
        val land = PlayField.Landscape(seed = 4, base = dark, paletteCount = 30)
        val shown = PlayField.apply(dark, Mode.MAP, land, Touch(0.9, 0.2), effects = false)
        assertEquals(dark.baseLayer, shown.baseLayer)
        assertEquals(dark.emblemLayer, shown.emblemLayer)
        assertEquals(dark.textLayer, shown.textLayer)
    }

    @Test
    fun theMiddleWithTheEffectsOffIsTheMandalaWithoutThem() {
        assertEquals(dark, PlayField.middle(dark, effects = true))
        assertEquals(dark.copy(effectsLayer = dark.effectsLayer.copy(isEnabled = false)), PlayField.middle(dark, effects = false))
        val land = PlayField.Landscape(seed = 9, base = dark, paletteCount = 30)
        for (mode in Mode.entries) assertEquals(PlayField.middle(dark, false), PlayField.apply(dark, mode, land, Touch(), effects = false))
    }

    @Test
    fun startingFromHereWithTheEffectsOffKeepsTheOldScene() {
        val land = PlayField.Landscape(seed = 9, base = dark, paletteCount = 30)
        val here = Touch(0.5, 1.0)
        val next = PlayField.newMiddle(dark, Mode.COMPASS, land, here, effects = false)
        assertEquals(dark.effectsLayer, next.effectsLayer)
        assertEquals(PlayField.apply(dark, Mode.COMPASS, land, here).layers, next.layers)
        // Switched on again the new middle shows the old scene; with the effects on it takes the raised one, as before.
        assertEquals(PlayField.apply(dark, Mode.COMPASS, land, here), PlayField.newMiddle(dark, Mode.COMPASS, land, here, effects = true))
    }
}
