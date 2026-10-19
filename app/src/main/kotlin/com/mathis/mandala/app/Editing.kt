package com.mathis.mandala.app

import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaStyle
import com.mathis.mandala.core.SettingsJson
import com.mathis.mandala.core.StyleLayer

/** Editing rules for the single-layer editor. Pure functions, so they are unit-tested on the JVM. */
object Editing {
    /** Styles the renderer has ported so far, in the order the macOS app lists them. */
    val styles = listOf(
        MandalaStyle.SPIROGRAPH,
        MandalaStyle.ROSE_CURVES,
        MandalaStyle.HYPOCYCLOID,
        MandalaStyle.PHYLLOTAXIS,
        MandalaStyle.WAVE_INTERFERENCE,
    )

    const val PREVIEW_SIZE = 512
    const val EXPORT_SIZE = 1024

    fun initial(seed: ULong): MandalaParameters = MandalaParameters(
        layers = listOf(StyleLayer(style = MandalaStyle.SPIROGRAPH, scale = 0.8, seed = seed)),
        seed = seed,
        outputSize = EXPORT_SIZE,
    )

    val MandalaParameters.layer: StyleLayer get() = layers.first()

    fun MandalaParameters.updateLayer(change: (StyleLayer) -> StyleLayer): MandalaParameters =
        copy(layers = listOf(change(layer)) + layers.drop(1))

    /** Same settings, new random arrangement. */
    fun MandalaParameters.newVariation(seed: ULong): MandalaParameters =
        copy(seed = seed).updateLayer { it.copy(seed = seed) }

    /**
     * File name in the scheme of the macOS app: first four letters of the style, palette, symmetry,
     * and a short hash of the settings so different settings never overwrite each other.
     */
    fun fileName(params: MandalaParameters): String {
        val l = params.layer
        val style = l.style.displayName.take(4).lowercase()
        val sym = if (l.symmetry > 1) "-x${l.symmetry}" else ""
        return "mandala-$style-p${l.paletteIndex}$sym-${settingsHash(params)}.png"
    }

    private fun settingsHash(params: MandalaParameters): String {
        var h = 0xcbf29ce484222325uL
        for (b in SettingsJson.encode(params).encodeToByteArray()) {
            h = (h xor (b.toULong() and 0xFFuL)) * 0x100000001b3uL
        }
        return h.toString(16).padStart(16, '0').take(8)
    }
}
