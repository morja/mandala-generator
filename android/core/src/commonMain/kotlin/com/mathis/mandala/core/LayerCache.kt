package com.mathis.mandala.core

/**
 * Keeps each style layer's finished image between renders, so a change to one layer redraws only
 * that layer; the others are blended in from here, and a layer only turned or faded is not drawn
 * again. The result is the same image as without it. Meant for the app's preview and for the frames
 * of an animation: it holds one image per layer of the last render (a few MB each at preview size).
 * Renders that share a cache must not run at the same time.
 */
class LayerCache {
    /** The layer's image before turning and fading, its place on the canvas and the centre it turns about. */
    internal class Entry(val image: Image8, val x0: Int, val y0: Int, val cx: Float, val cy: Float)

    /** Everything a layer's image depends on. */
    internal data class Key(
        /** The layer without rotation and opacity, which are applied to the kept image. */
        val layer: StyleLayer,
        /** Drawn as a rotated layer (a larger square), which an unrotated one is not. */
        val rotated: Boolean,
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

    /**
     * What stays the same in every frame of an animation (background, emblem, text, the drawing layer,
     * stars and glitter), kept by name while the mandala apart from its layers' rotation stays the same.
     */
    private val still = HashMap<String, Any>()
    private var stillKey: Any? = null

    /** The kept still parts for `key`; they are dropped when the key changes. */
    internal fun still(key: Any): MutableMap<String, Any> {
        if (key != stillKey) {
            still.clear()
            stillKey = key
        }
        return still
    }

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
        still.clear()
        stillKey = null
    }
}

/**
 * Float rows of `channels` values per pixel kept for the frames of an animation in 16-bit fixed point
 * (steps of 1/4096 up to 8), half the memory of floats and far finer than the 8-bit images they end in.
 */
internal class KeptRows(val width: Int, val height: Int, private val channels: Int = 3) {
    private val data = ShortArray(width * height * channels)

    fun write(y: Int, row: FloatArray) {
        val o = y * width * channels
        for (k in 0 until width * channels) data[o + k] = (row[k].coerceIn(0f, MAX) * SCALE + 0.5f).toInt().toShort()
    }

    fun read(y: Int, out: FloatArray) {
        val o = y * width * channels
        for (k in 0 until width * channels) out[k] = data[o + k] / SCALE
    }

    private companion object {
        const val SCALE = 4096f
        const val MAX = 32767f / SCALE
    }
}
