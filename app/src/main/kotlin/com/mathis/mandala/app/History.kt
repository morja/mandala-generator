package com.mathis.mandala.app

/**
 * Back and forward through earlier settings, like the arrows of the macOS app: every settled state
 * is recorded, going back and then changing something drops the states ahead, at most 50 are kept.
 */
class History<T>(private val limit: Int = 50) {
    private val entries = ArrayList<T>()
    private var index = -1

    val canGoBack: Boolean get() = index > 0
    val canGoForward: Boolean get() = index < entries.lastIndex

    /** Records a state unless it is the current one (as after going back or forward). */
    fun record(state: T) {
        if (index >= 0 && entries[index] == state) return
        while (entries.lastIndex > index) entries.removeAt(entries.lastIndex)
        entries += state
        if (entries.size > limit) entries.removeAt(0)
        index = entries.lastIndex
    }

    fun back(): T? = if (canGoBack) entries[--index] else null

    fun forward(): T? = if (canGoForward) entries[++index] else null
}
