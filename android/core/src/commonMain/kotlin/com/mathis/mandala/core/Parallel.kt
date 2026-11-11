package com.mathis.mandala.core

/**
 * Splits rows or tasks over threads. One implementation per platform (ADR-0015): a fixed thread pool
 * on the JVM and Android, Grand Central Dispatch on iOS. The image never depends on [threads].
 */
expect class Parallel(threads: Int) : AutoCloseable {
    val threads: Int

    /** Runs `body(index)` for each chunk index in 0 until chunks, in parallel. */
    fun forEachChunk(chunks: Int, body: (Int) -> Unit)

    /**
     * Splits [0, count) into contiguous ranges, several per thread: on phones with fast and slow
     * cores, one range per thread would leave the fast cores waiting for the slow ones.
     */
    fun forRange(count: Int, body: (from: Int, until: Int) -> Unit)

    override fun close()
}

/** Ranges per thread in [Parallel.forRange], shared by both platforms. */
internal const val CHUNKS_PER_THREAD = 4
