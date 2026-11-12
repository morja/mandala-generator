package com.mathis.mandala.core

internal actual class PerThread<T : Any> actual constructor(create: () -> T) {
    private val local = ThreadLocal.withInitial(create)

    actual fun get(): T = local.get()

    actual fun remove() = local.remove()
}

internal actual fun availableProcessors(): Int = Runtime.getRuntime().availableProcessors()
