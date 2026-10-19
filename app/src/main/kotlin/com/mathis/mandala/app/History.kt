package com.mathis.mandala.app

/**
 * Back and forward through earlier settings, like the arrows of the macOS app: every settled state
 * is recorded, going back and then changing something drops the states ahead, at most 50 are kept.
 */
class History<T>(private val limit: Int = 50) {
    private val list = ArrayList<T>()
    private var index = -1

    val canGoBack: Boolean get() = index > 0
    val canGoForward: Boolean get() = index < list.lastIndex

    /** Records a state unless it is the current one (as after going back or forward). */
    fun record(state: T) {
        if (index >= 0 && list[index] == state) return
        while (list.lastIndex > index) list.removeAt(list.lastIndex)
        list += state
        if (list.size > limit) list.removeAt(0)
        index = list.lastIndex
    }

    /** All recorded states, oldest first. */
    val entries: List<T> get() = list.toList()

    /** Starts from saved states, positioned at the newest, as the macOS app does on launch. */
    fun restore(saved: List<T>) {
        list.clear()
        list += saved.takeLast(limit)
        index = list.lastIndex
    }

    fun back(): T? = if (canGoBack) list[--index] else null

    fun forward(): T? = if (canGoForward) list[++index] else null
}
