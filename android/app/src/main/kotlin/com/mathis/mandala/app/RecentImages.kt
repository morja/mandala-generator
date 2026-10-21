package com.mathis.mandala.app

/**
 * The last few rendered previews by what they were rendered from, so a state shown before (a step
 * back in the history, an undo, a layer switched off and on again) comes back without rendering.
 * The oldest fall out once `max` are kept.
 */
class RecentImages<K, V>(private val max: Int) {
    private val entries = object : LinkedHashMap<K, V>(max + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > max
    }

    operator fun get(key: K): V? = entries[key]

    operator fun set(key: K, value: V) {
        entries[key] = value
    }

    val size: Int get() = entries.size
}
