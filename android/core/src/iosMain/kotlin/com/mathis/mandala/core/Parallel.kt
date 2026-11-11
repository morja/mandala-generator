package com.mathis.mandala.core

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.Worker
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait

/**
 * iOS: chunks run on a fixed set of Kotlin/Native worker threads that live as long as the app,
 * plus the calling thread. Grand Central Dispatch was tried first: its pool threads kept the
 * Kotlin/Native garbage collector from freeing memory during a render (SPIKE-3).
 */
@OptIn(ExperimentalAtomicApi::class, ObsoleteWorkersApi::class)
actual class Parallel actual constructor(actual val threads: Int) : AutoCloseable {
    actual fun forEachChunk(chunks: Int, body: (Int) -> Unit) {
        val helpers = minOf(threads, chunks) - 1
        if (helpers <= 0) {
            for (c in 0 until chunks) body(c)
            return
        }
        val next = AtomicInt(0)
        val failure = AtomicReference<Throwable?>(null)
        val run = {
            while (true) {
                val c = next.fetchAndIncrement()
                if (c >= chunks) break
                try {
                    body(c)
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                }
            }
        }
        val done = dispatch_semaphore_create(0)
        val pool = Pool.workers
        for (h in 0 until minOf(helpers, pool.size)) {
            pool[h].executeAfter(0L) {
                run()
                dispatch_semaphore_signal(done)
            }
        }
        run()
        repeat(minOf(helpers, pool.size)) { dispatch_semaphore_wait(done, DISPATCH_TIME_FOREVER) }
        failure.load()?.let { throw it }
    }

    actual fun forRange(count: Int, body: (from: Int, until: Int) -> Unit) {
        val n = maxOf(1, minOf(if (threads <= 1) 1 else threads * CHUNKS_PER_THREAD, count))
        val step = (count + n - 1) / n
        forEachChunk(n) { c ->
            val from = c * step
            val until = minOf(count, from + step)
            if (from < until) body(from, until)
        }
    }

    actual override fun close() {}

    /** One worker per core but the caller's, started on first use. */
    private object Pool {
        val workers: List<Worker> = List(maxOf(0, availableProcessors() - 1)) { Worker.start(name = "mandala-render-$it") }
    }
}
