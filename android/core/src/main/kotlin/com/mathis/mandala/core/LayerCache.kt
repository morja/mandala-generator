package com.mathis.mandala.core

/**
 * Keeps each style layer's finished image between renders, so a change to one layer redraws only
 * that layer; the others are blended in from here. The result is the same image as without it.
 * Meant for the app's preview: it holds one image per layer of the last render (a few MB each at
 * preview size). Renders that share a cache must not run at the same time.
 */
class LayerCache {
    internal class Entry(val image: Image8, val x0: Int, val y0: Int)

    /** Everything a layer's image depends on. */
    internal data class Key(
        val layer: StyleLayer,
        /** The layer's place in the list; only matters when its seed comes from the mandala's. */
        val index: Int,
        val seed: ULong,
        val palette: ColorPalette,
        val bufW: Int,
        val bufH: Int,
        val centreX: Double,
        val centreY: Double,
        val mandalaSize: Int,
        val glowReferenceWidth: Int,
    )

    private val entries = HashMap<Key, Entry>()
    private val used = HashSet<Key>()

    internal fun get(key: Key): Entry? = entries[key]?.also { used += key }

    internal fun put(key: Key, entry: Entry) {
        entries[key] = entry
        used += key
    }

    /** Called when a render starts. */
    internal fun begin() = used.clear()

    /** Called when a render finishes: drops the images it did not use. */
    internal fun finish() = entries.keys.retainAll(used)

    fun clear() {
        entries.clear()
        used.clear()
    }
}
