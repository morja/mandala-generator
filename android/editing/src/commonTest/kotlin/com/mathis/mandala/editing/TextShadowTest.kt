package com.mathis.mandala.editing

import com.mathis.mandala.core.TextLayerSettings
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The text shadow's direction, distance and softness sliders on the Mac's offset fields (QA-13). */
class TextShadowTest {
    private fun near(expected: Double, actual: Double, what: String) =
        assertTrue(abs(expected - actual) < 1e-9, "$what: expected $expected, was $actual")

    @Test
    fun theMacDefaultIsDownAndToTheRightLikeTheReliefAngle() {
        val s = TextLayerSettings()
        near(0.125, assertNotNull(TextShadow.direction(s)), "direction")
        near(sqrt(0.18), TextShadow.distance(s), "distance")
        assertEquals(TextShadow.DEFAULT_DIRECTION, TextShadow.direction(s)!!, 1e-9)
    }

    @Test
    fun directionsGoClockwiseOnScreenWithTheFieldsYUpward() {
        val s = TextLayerSettings()
        // 0 right, 0.25 down (Y negative, the Mac draws with y up), 0.5 left, 0.75 up.
        val cases = listOf(0.0 to (1.0 to 0.0), 0.25 to (0.0 to -1.0), 0.5 to (-1.0 to 0.0), 0.75 to (0.0 to 1.0))
        for ((direction, xy) in cases) {
            val turned = TextShadow.withDistance(TextShadow.withDirection(s, direction), 1.0)
            near(xy.first, turned.shadowOffsetX, "x at $direction")
            near(xy.second, turned.shadowOffsetY, "y at $direction")
            near(direction, TextShadow.direction(turned)!!, "back at $direction")
        }
    }

    @Test
    fun turningKeepsTheDistanceAndMovingKeepsTheDirection() {
        val mac = TextLayerSettings(shadowOffsetX = -1.2, shadowOffsetY = 0.5)
        val distance = TextShadow.distance(mac)
        val direction = TextShadow.direction(mac)!!
        near(distance, TextShadow.distance(TextShadow.withDirection(mac, 0.6)), "distance after turning")
        near(direction, TextShadow.direction(TextShadow.withDistance(mac, 0.4))!!, "direction after moving")
        near(0.4, TextShadow.distance(TextShadow.withDistance(mac, 0.4)), "new distance")
    }

    @Test
    fun aShadowWithoutDistanceTakesTheSlidersDirection() {
        val under = TextLayerSettings(shadowOffsetX = 0.0, shadowOffsetY = 0.0)
        assertNull(TextShadow.direction(under))
        val out = TextShadow.withDistance(under, 0.5, fallbackDirection = 0.75)
        near(0.75, TextShadow.direction(out)!!, "fallback direction")
        near(0.5, out.shadowOffsetY, "upward")
        // Back to 0 and out again keeps the given direction, never a negative distance.
        near(0.0, TextShadow.distance(TextShadow.withDistance(out, -1.0)), "no negative distance")
    }

    @Test
    fun aDiagonalBeyondTheSliderIsKeptUntilTheSliderMoves() {
        val far = TextLayerSettings(shadowOffsetX = 2.0, shadowOffsetY = -2.0)
        assertTrue(TextShadow.distance(far) > TextShadow.distanceRange.endInclusive)
        // Only the direction slider moved: the distance stays as the file had it.
        near(TextShadow.distance(far), TextShadow.distance(TextShadow.withDirection(far, 0.3)), "kept")
    }

    @Test
    fun softnessIsTheMacBlurAndOnlyItChanges() {
        val s = TextLayerSettings(shadowOffsetX = 0.7, shadowOffsetY = 0.1)
        val soft = TextShadow.withSoftness(s, 0.9)
        assertEquals(0.9, soft.shadowBlur)
        assertEquals(s.copy(shadowBlur = 0.9), soft)
        assertEquals(TextShadow.softnessRange, 0.0..1.0)
    }
}
