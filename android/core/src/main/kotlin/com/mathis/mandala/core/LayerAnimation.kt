package com.mathis.mandala.core

/**
 * A looping animation of the mandala layers turning against each other, as the macOS app exports it
 * (UX-1 12): the first, third and fifth layer turn one way, the others the other way. Each layer
 * makes one whole turn per loop, so the last frame leads into the first without a jump (the macOS
 * app turns every second layer by 0.6, which jumps unless the layer's symmetry fits). Background,
 * emblem and text stay still.
 */
object LayerAnimation {
    /** As the macOS app exports by default. */
    const val FPS = 24

    /** Lengths the app offers, in seconds; a longer loop turns more calmly. */
    val SECONDS = listOf(5, 10, 15, 30)
    const val DEFAULT_SECONDS = 10

    fun frameCount(seconds: Int): Int = seconds * FPS

    /** The mandala at `t` through the loop (0 at the start, 1 back at the start). */
    fun at(params: MandalaParameters, t: Double): MandalaParameters =
        params.copy(
            layers = params.layers.mapIndexed { i, layer ->
                val turn = if (i % 2 == 0) t else -t
                layer.copy(rotation = (layer.rotation + turn).mod(1.0))
            },
        )

    /** Frame `frame` of `frames`. */
    fun frame(params: MandalaParameters, frame: Int, frames: Int): MandalaParameters = at(params, frame.toDouble() / frames)

    /**
     * Render options for the frames: every layer drawn as a turning one and kept in `cache`, so only
     * the first frame draws the layers and each further one turns and blends them.
     */
    fun options(base: RenderOptions, cache: LayerCache): RenderOptions = base.copy(turningLayers = true, layerCache = cache)
}
