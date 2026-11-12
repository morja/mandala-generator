package com.mathis.mandala.core

/**
 * Row-sized work arrays of one thread, kept for the length of a render (one per thread of its
 * [Parallel], see [Parallel.scratch]). Filters ask for them per chunk instead of allocating new ones,
 * which spares both garbage collectors gigabytes of short-lived arrays per heavy render.
 *
 * Each array belongs to one slot, and a slot is used by one function at a time: only inside a chunk
 * body, never across a call that runs chunks itself. Arrays only grow, never shrink; callers pass
 * explicit lengths and must not rely on `size`, except for [exactFloats].
 */
internal class Scratch {
    private val floats = arrayOfNulls<FloatArray>(SLOTS)
    private val doubles = arrayOfNulls<DoubleArray>(SLOTS)
    private val ints = arrayOfNulls<IntArray>(SLOTS)
    private var ring: Array<FloatArray> = emptyArray()

    /** At least `size` floats; the content is whatever the slot last held. */
    fun floats(slot: Int, size: Int): FloatArray {
        val a = floats[slot]
        if (a != null && a.size >= size) return a
        return FloatArray(size).also { floats[slot] = it }
    }

    /** Exactly `size` floats, for rows handed to code outside the filters. */
    fun exactFloats(slot: Int, size: Int): FloatArray {
        val a = floats[slot]
        if (a != null && a.size == size) return a
        return FloatArray(size).also { floats[slot] = it }
    }

    fun doubles(slot: Int, size: Int): DoubleArray {
        val a = doubles[slot]
        if (a != null && a.size >= size) return a
        return DoubleArray(size).also { doubles[slot] = it }
    }

    fun ints(slot: Int, size: Int): IntArray {
        val a = ints[slot]
        if (a != null && a.size >= size) return a
        return IntArray(size).also { ints[slot] = it }
    }

    /** `count` rows of at least `size` floats each, for the resampler's ring of source rows. */
    fun ring(count: Int, size: Int): Array<FloatArray> {
        if (ring.size < count || ring[0].size < size) ring = Array(maxOf(count, ring.size)) { FloatArray(size) }
        return ring
    }

    companion object {
        const val RESAMPLE_VERTICAL = 0
        const val RESAMPLE_OUT = 1
        const val RESAMPLE_RING_ROWS = 2
        const val BLUR_SRC = 3
        const val BLUR_DST = 4
        const val BLUR_COLS = 5
        const val BLUR_PREFIX = 6
        const val ROW_A = 7
        const val ROW_B = 8
        const val ROW_C = 9
        private const val SLOTS = 10
    }
}
