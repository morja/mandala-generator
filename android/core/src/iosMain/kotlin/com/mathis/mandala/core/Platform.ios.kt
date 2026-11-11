package com.mathis.mandala.core

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toLong
import platform.posix.pthread_self

/**
 * iOS: a copy-on-write map from the thread to its value. It is read once per row, never per pixel,
 * and lives as long as its owner, so dispatch threads keep nothing after a render.
 */
@OptIn(ExperimentalAtomicApi::class, ExperimentalForeignApi::class)
internal actual class PerThread<T : Any> actual constructor(private val create: () -> T) {
    private val values = AtomicReference<Map<Long, T>>(emptyMap())

    actual fun get(): T {
        val thread = pthread_self().toLong()
        values.load()[thread]?.let { return it }
        val value = create()
        while (true) {
            val old = values.load()
            if (values.compareAndSet(old, old + (thread to value))) return value
        }
    }
}

internal actual fun availableProcessors(): Int = platform.Foundation.NSProcessInfo.processInfo.activeProcessorCount.toInt()
