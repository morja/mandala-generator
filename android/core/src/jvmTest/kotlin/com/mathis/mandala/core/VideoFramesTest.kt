package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** BT.709, limited range, 4:2:0 (the colours phones expect from an HD video). */
class VideoFramesTest {
    private fun planes(r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        val img = Image8(2, 2, Encoding.SRGB, ByteArray(12) { (if (it % 3 == 0) r else if (it % 3 == 1) g else b).toByte() })
        val y = ByteArray(4)
        val u = ByteArray(1)
        val v = ByteArray(1)
        VideoFrames.toI420(img, y, u, v)
        return Triple(y[0].toInt() and 0xFF, u[0].toInt() and 0xFF, v[0].toInt() and 0xFF)
    }

    @Test
    fun blackWhiteAndRedLandWhereBt709PutsThem() {
        assertEquals(Triple(16, 128, 128), planes(0, 0, 0))
        assertEquals(Triple(235, 128, 128), planes(255, 255, 255))
        assertEquals(Triple(63, 102, 240), planes(255, 0, 0))
    }

    @Test
    fun anOddSizeIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            VideoFrames.toI420(Image8(3, 2, Encoding.SRGB), ByteArray(6), ByteArray(1), ByteArray(1))
        }
    }
}
