package com.mathis.mandala.core

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import platform.darwin.DISPATCH_QUEUE_PRIORITY_HIGH
import platform.darwin.dispatch_apply
import platform.darwin.dispatch_get_global_queue

/**
 * iOS: chunks run through Grand Central Dispatch (`dispatch_apply` on a global queue), which spreads
 * them over the performance and efficiency cores. [threads] only sets how finely ranges are split.
 */
@OptIn(ExperimentalAtomicApi::class)
actual class Parallel actual constructor(actual val threads: Int) : AutoCloseable {
    private val queue = if (threads > 1) dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH.toLong(), 0u) else null

    actual fun forEachChunk(chunks: Int, body: (Int) -> Unit) {
        if (queue == null || chunks <= 1) {
            for (c in 0 until chunks) body(c)
            return
        }
        // An exception inside a dispatch block would end the process, so the first one is carried out.
        val failure = AtomicReference<Throwable?>(null)
        dispatch_apply(chunks.toULong(), queue) { c ->
            try {
                body(c.toInt())
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            }
        }
        failure.load()?.let { throw it }
    }

    actual fun forRange(count: Int, body: (from: Int, until: Int) -> Unit) {
        val n = maxOf(1, minOf(if (queue == null) 1 else threads * CHUNKS_PER_THREAD, count))
        val step = (count + n - 1) / n
        forEachChunk(n) { c ->
            val from = c * step
            val until = minOf(count, from + step)
            if (from < until) body(from, until)
        }
    }

    actual override fun close() {}
}
