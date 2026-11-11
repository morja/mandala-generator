package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/** The styles built on Android first (no macOS reference yet, ADR-0004): they draw, and the same settings draw the same. */
class RingStylesTest {
    private fun render(style: String, complexity: Double) = MandalaRenderer.render(
        SettingsJson.decode("""{"seed":3,"layers":[{"style":"$style","complexity":$complexity,"density":0.6,"ripple":0.4,"symmetry":6}]}"""),
        RenderOptions(outputSize = 96, threads = 2),
    ).image

    @Test
    fun petalAndLoopRingsDrawAndRepeat() {
        for (style in listOf("petalRings", "loopRings")) {
            for (c in listOf(0.0, 1.0)) {
                val a = render(style, c)
                val lit = (0 until a.width * a.height).count { a.get(it * 3) + a.get(it * 3 + 1) + a.get(it * 3 + 2) > 60 }
                assertTrue(lit > a.width * a.height / 50, "$style at complexity $c lights only $lit pixels")
                assertContentEquals(a.data, render(style, c).data, "$style is not repeatable")
            }
        }
    }

    @Test
    fun theyAreReadFromSettingsAndWrittenBack() {
        val p = SettingsJson.decode("""{"layers":[{"style":"petalRings"},{"style":"loopRings"}]}""")
        assertTrue(p.layers.map { it.style } == listOf(MandalaStyle.PETAL_RINGS, MandalaStyle.LOOP_RINGS))
        assertTrue(SettingsJson.encode(p).contains("\"petalRings\"") && SettingsJson.encode(p).contains("\"loopRings\""))
    }
}
