package com.mathis.mandala.core

/** What a mandala may use without Pro, and what it uses that needs Pro (ADR-0016). Shared by the phone apps. */
enum class ProFeature(val label: String) {
    MORE_LAYERS("More than three layers"),
    EMBLEM("Emblem"),
    TEXT("Text"),
    MOVED_LAYERS("Moved layers"),
}

object ProFeatures {
    /** Visible style layers a mandala may have without Pro. */
    const val FREE_LAYERS = 3

    /**
     * The Pro features `params` uses, in the order of [ProFeature]. Only what shows in the image counts:
     * hidden layers, a hidden emblem or empty text do not.
     */
    fun of(params: MandalaParameters): Set<ProFeature> {
        val shown = params.layers.filter { it.isEnabled }
        val found = linkedSetOf<ProFeature>()
        if (shown.size > FREE_LAYERS) found += ProFeature.MORE_LAYERS
        if (params.emblemLayer.isEnabled) found += ProFeature.EMBLEM
        if (params.textLayer.isEnabled && params.textLayer.text.isNotBlank()) found += ProFeature.TEXT
        if (shown.any { it.offsetX != 0.0 || it.offsetY != 0.0 }) found += ProFeature.MOVED_LAYERS
        return found
    }

    /**
     * `params` without its Pro features: layers above the third visible one hidden (not deleted, so
     * they come back with Pro), the emblem and text hidden, moved layers centred.
     */
    fun removed(params: MandalaParameters): MandalaParameters {
        var visible = 0
        val layers = params.layers.map { layer ->
            if (!layer.isEnabled) return@map layer
            visible++
            val kept = if (visible > FREE_LAYERS) layer.copy(isEnabled = false) else layer
            kept.copy(offsetX = 0.0, offsetY = 0.0)
        }
        return params.copy(
            layers = layers,
            emblemLayer = params.emblemLayer.copy(isEnabled = false),
            textLayer = params.textLayer.copy(isEnabled = false),
        )
    }
}
