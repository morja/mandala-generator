package com.mathis.mandala.core

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Small fixed thread pool for splitting rows or tasks. Plain java.util.concurrent, so it runs on Android too. */
class Parallel(val threads: Int) : AutoCloseable {
    private val pool: ExecutorService? =
        if (threads > 1) Executors.newFixedThreadPool(threads) { r -> Thread(r, "mandala-render").apply { isDaemon = true } } else null

    /** Runs `body(index)` for each chunk index in 0 until chunks, in parallel. */
    fun forEachChunk(chunks: Int, body: (Int) -> Unit) {
        if (pool == null || chunks <= 1) {
            for (c in 0 until chunks) body(c)
            return
        }
        val futures = (0 until chunks).map { c -> pool.submit(Callable { body(c) }) }
        futures.forEach { it.get() }
    }

    /** Splits [0, count) into contiguous ranges, one per thread. */
    fun forRange(count: Int, body: (from: Int, until: Int) -> Unit) {
        val n = maxOf(1, minOf(threads, count))
        val step = (count + n - 1) / n
        forEachChunk(n) { c ->
            val from = c * step
            val until = minOf(count, from + step)
            if (from < until) body(from, until)
        }
    }

    override fun close() {
        pool?.shutdown()
    }
}
