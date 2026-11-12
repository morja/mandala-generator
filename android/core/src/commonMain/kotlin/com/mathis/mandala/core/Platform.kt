package com.mathis.mandala.core

import kotlin.math.floor

/** One scratch value per thread, created on first use (java.lang.ThreadLocal on the JVM). */
internal expect class PerThread<T : Any>(create: () -> T) {
    fun get(): T

    /** Drops the calling thread's value, so a thread that outlives the owner does not keep it. */
    fun remove()
}

/** `System.arraycopy` for common code; source and target may overlap. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun arraycopy(src: ByteArray, srcPos: Int, dst: ByteArray, dstPos: Int, length: Int) {
    src.copyInto(dst, dstPos, srcPos, srcPos + length)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun arraycopy(src: FloatArray, srcPos: Int, dst: FloatArray, dstPos: Int, length: Int) {
    src.copyInto(dst, dstPos, srcPos, srcPos + length)
}

/** `roundHalfUp(Double)`: half rounds up, NaN gives 0. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun roundHalfUp(x: Double): Long = floor(x + 0.5).toLong()

/** Cores the platform offers, the default thread count for rendering. */
internal expect fun availableProcessors(): Int
