package com.mathis.mandala.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Expected values were measured on macOS with Core Image (working space Display P3, sRGB inputs),
 * the setup the macOS renderer uses. They pin down the colour handling of the blend steps.
 */
class CoreImageParityTest {
    private val a = listOf(intArrayOf(0, 0, 0), intArrayOf(50, 50, 50), intArrayOf(100, 100, 100), intArrayOf(200, 40, 10), intArrayOf(10, 120, 240))
    private val b = listOf(intArrayOf(128, 128, 128), intArrayOf(50, 50, 50), intArrayOf(100, 100, 100), intArrayOf(30, 30, 30), intArrayOf(60, 10, 5))

    private fun image(px: List<IntArray>): Image8 {
        val img = Image8(px.size, 1, Encoding.SRGB)
        px.forEachIndexed { i, p -> for (c in 0..2) img.data[i * 3 + c] = p[c].toByte() }
        return img
    }

    private fun check(op: BlendOp, expected: List<IntArray>) {
        val out = Parallel(1).use { LayerEffects(it).composite(image(a), image(b), op) }
        expected.forEachIndexed { i, e ->
            for (c in 0..2) {
                val got = out.get(i * 3 + c)
                assertTrue(abs(got - e[c]) <= 2, "$op pixel $i channel $c: expected ${e[c]}, got $got")
            }
        }
    }

    @Test
    fun addition() = check(
        BlendOp.ADD,
        listOf(intArrayOf(128, 128, 128), intArrayOf(100, 100, 100), intArrayOf(200, 200, 200), intArrayOf(232, 75, 51), intArrayOf(101, 133, 247)),
    )

    @Test
    fun screen() = check(
        BlendOp.SCREEN,
        listOf(intArrayOf(128, 128, 128), intArrayOf(90, 90, 90), intArrayOf(161, 161, 161), intArrayOf(208, 69, 48), intArrayOf(88, 127, 240)),
    )

    @Test
    fun lighten() = check(
        BlendOp.LIGHTEN,
        listOf(intArrayOf(128, 128, 128), intArrayOf(50, 50, 50), intArrayOf(100, 100, 100), intArrayOf(200, 40, 10), intArrayOf(16, 120, 240)),
    )
}
