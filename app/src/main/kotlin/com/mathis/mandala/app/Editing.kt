package com.mathis.mandala.app

import com.mathis.mandala.core.BaseLayerSettings
import com.mathis.mandala.core.BaseLayerType
import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaStyle
import com.mathis.mandala.core.SettingsJson
import com.mathis.mandala.core.StyleLayer

/**
 * Editing rules of the editor, following the macOS app where it has rules. Pure functions, so they
 * are unit-tested on the JVM. Layers are drawn in list order: the first one lies at the bottom.
 */
object Editing {
    /** Styles the renderer has ported so far, in the order the macOS app lists them. */
    val styles = listOf(
        MandalaStyle.SPIROGRAPH,
        MandalaStyle.ROSE_CURVES,
        MandalaStyle.HYPOCYCLOID,
        MandalaStyle.PHYLLOTAXIS,
        MandalaStyle.FRACTAL,
        MandalaStyle.WAVE_INTERFERENCE,
        MandalaStyle.STAR_BURST,
        MandalaStyle.HYPERBOLOID,
        MandalaStyle.UNIVERSE,
    )

    /** Background types the renderer can draw; "off" is a disabled base layer. */
    val backgrounds = listOf(BaseLayerType.COLOR, BaseLayerType.GRADIENT, BaseLayerType.SUNBURST)

    const val PREVIEW_SIZE = 512
    const val EXPORT_SIZE = 1024

    /** Same limit as the macOS app. */
    const val MAX_LAYERS = 5

    fun initial(seed: ULong): MandalaParameters = MandalaParameters(
        layers = listOf(StyleLayer(style = MandalaStyle.SPIROGRAPH, scale = 0.8, seed = seed)),
        seed = seed,
        outputSize = EXPORT_SIZE,
    )

    fun MandalaParameters.updateLayer(index: Int, change: (StyleLayer) -> StyleLayer): MandalaParameters {
        if (index !in layers.indices) return this
        return copy(layers = layers.toMutableList().also { it[index] = change(it[index]) })
    }

    /** Adds a layer on top, set up like a new layer in the macOS app. Returns the settings and its index. */
    fun MandalaParameters.addLayer(seed: ULong): Pair<MandalaParameters, Int> {
        if (layers.size >= MAX_LAYERS) return this to layers.lastIndex
        val palettes = BuiltInPalettes.all.size
        val layer = StyleLayer(
            style = styles[layers.size % styles.size],
            scale = 0.65,
            paletteIndex = layers.lastOrNull()?.let { (it.paletteIndex + 3) % palettes } ?: 0,
            colorOffset = layers.size * 0.25,
            glowIntensity = 0.5,
            seed = seed,
        )
        return copy(layers = layers + layer) to layers.size
    }

    /** Inserts a copy with a new seed right above `index`. Returns the settings and the copy's index. */
    fun MandalaParameters.duplicateLayer(index: Int, seed: ULong): Pair<MandalaParameters, Int> {
        if (index !in layers.indices || layers.size >= MAX_LAYERS) return this to index
        val copy = layers[index].copy(seed = seed)
        return copy(layers = layers.toMutableList().also { it.add(index + 1, copy) }) to index + 1
    }

    /** Removes a layer; the last remaining layer cannot be removed. Returns the settings and the layer to select. */
    fun MandalaParameters.removeLayer(index: Int): Pair<MandalaParameters, Int> {
        if (index !in layers.indices || layers.size <= 1) return this to index.coerceIn(0, layers.lastIndex)
        val rest = layers.toMutableList().also { it.removeAt(index) }
        return copy(layers = rest) to index.coerceAtMost(rest.lastIndex)
    }

    /** Moves a layer by `delta` places (positive = further up). Returns the settings and its new index. */
    fun MandalaParameters.moveLayer(index: Int, delta: Int): Pair<MandalaParameters, Int> {
        val target = index + delta
        if (index !in layers.indices || target !in layers.indices) return this to index
        val list = layers.toMutableList()
        val moved = list.removeAt(index)
        list.add(target, moved)
        return copy(layers = list) to target
    }

    fun MandalaParameters.updateBackground(change: (BaseLayerSettings) -> BaseLayerSettings): MandalaParameters =
        copy(baseLayer = change(baseLayer))

    /** Same settings, new random arrangement for every layer (like "Randomize Seed" on the Mac). */
    fun MandalaParameters.newVariation(nextSeed: () -> ULong): MandalaParameters =
        copy(seed = nextSeed(), layers = layers.map { it.copy(seed = nextSeed()) })

    /**
     * File name in the scheme of the macOS app: first four letters of the first two styles, palette and
     * symmetry of the first layer, and a short hash of the settings so different settings never collide.
     */
    fun fileName(params: MandalaParameters): String {
        val styles = params.layers.take(2).joinToString("+") { it.style.displayName.take(4).lowercase() }
        val first = params.layers.first()
        val sym = if (first.symmetry > 1) "-x${first.symmetry}" else ""
        return "mandala-$styles-p${first.paletteIndex}$sym-${settingsHash(params)}.png"
    }

    private fun settingsHash(params: MandalaParameters): String {
        var h = 0xcbf29ce484222325uL
        for (b in SettingsJson.encode(params).encodeToByteArray()) {
            h = (h xor (b.toULong() and 0xFFuL)) * 0x100000001b3uL
        }
        return h.toString(16).padStart(16, '0').take(8)
    }
}
