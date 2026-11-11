package com.mathis.mandala.core

/** xorshift64 generator, bit-identical to the macOS SeededRNG so seeds give the same mandala. */
class SeededRng(seed: ULong) {
    private var state: ULong = if (seed == 0uL) 6364136223846793005uL else seed

    fun next(): ULong {
        state = state xor (state shl 13)
        state = state xor (state shr 7)
        state = state xor (state shl 17)
        return state
    }

    fun nextDouble(): Double =
        (next() and 0x7FFF_FFFF_FFFF_FFFFuL).toLong().toDouble() / Long.MAX_VALUE.toDouble()

    fun nextFloat(): Float = nextDouble().toFloat()

    fun nextDouble(from: Double, to: Double): Double = from + nextDouble() * (to - from)
}
