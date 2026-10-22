package com.mathis.mandala.app

import com.mathis.mandala.core.BaseLayerType
import com.mathis.mandala.core.EmblemFlowerVariant
import com.mathis.mandala.core.EmblemFrame
import com.mathis.mandala.core.EmblemGlowMode
import com.mathis.mandala.core.EmblemLayerSettings
import com.mathis.mandala.core.EmblemMotif
import com.mathis.mandala.core.EmblemRendering
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaStyle
import com.mathis.mandala.core.StyleLayer
import kotlin.random.Random

/**
 * The macOS app's dice: new layers ("Randomize Layers"), new layers and background ("Randomize All")
 * and one layer. Same ranges as AppState.swift (tuned in TUNE-1 so throws are not blown out: no wash,
 * less painting and brightness, symmetry from 3, a little calmer from the second layer on); palettes
 * only from [Editing.offeredPalettes]. Effects, text and drawing stay as they are.
 */
object Randomize {

    /** Most layers a throw makes, as on the Mac. */
    const val MAX_LAYERS = 3

    /** Up to `maxLayers` new layers (at most [MAX_LAYERS]); fewer without Pro (ADR-0016). */
    fun MandalaParameters.randomLayers(random: Random, maxLayers: Int = MAX_LAYERS): MandalaParameters {
        val count = random.nextInt(1, maxLayers.coerceIn(1, MAX_LAYERS) + 1)
        val symmetry = random.nextInt(SYMMETRY.first, SYMMETRY.last + 1)
        val usedStyles = mutableSetOf<MandalaStyle>()
        val usedPalettes = mutableSetOf<Int>()
        val layers = List(count) { index ->
            val style = pickAvoiding(usedStyles, random) { Editing.styles.random(random) }
            val palette = pickAvoiding(usedPalettes, random) { Editing.offeredPalettes.random(random) }
            randomLayer(StyleLayer(), index, style, palette, random).copy(colorOffset = random.nextDouble(), symmetry = symmetry)
        }
        return copy(seed = random.nextLong().toULong(), layers = layers)
    }

    fun MandalaParameters.randomAll(random: Random, maxLayers: Int = MAX_LAYERS): MandalaParameters {
        val next = randomLayers(random, maxLayers)
        // The macOS dice leave out auto and image, and Grain since it is no longer offered (TUNE-1).
        val types = listOf(BaseLayerType.COLOR, BaseLayerType.GRADIENT, BaseLayerType.SUNBURST)
        return next.copy(
            baseLayer = baseLayer.copy(
                isEnabled = random.nextBoolean(),
                type = types.random(random),
                hue = random.nextDouble(),
                saturation = random.nextDouble(0.3, 1.0),
                brightness = random.nextDouble(0.05, 0.3),
                hue2 = random.nextDouble(),
                saturation2 = random.nextDouble(0.3, 1.0),
                brightness2 = random.nextDouble(0.0, 0.12),
                isRadial = random.nextBoolean(),
                gradientAngle = random.nextDouble(),
            ),
        )
    }

    /** New style, palette, seed and look for one layer; symmetry, blend mode and opacity stay. */
    fun MandalaParameters.randomLayer(index: Int, random: Random): MandalaParameters {
        if (index !in layers.indices) return this
        val list = layers.toMutableList()
        list[index] = randomLayer(list[index], index, Editing.styles.random(random), Editing.offeredPalettes.random(random), random)
        return copy(layers = list)
    }

    /** A new variant of one layer in its own style: seed, palette and look; symmetry, blend mode and opacity stay. */
    fun MandalaParameters.randomVariant(index: Int, random: Random): MandalaParameters {
        if (index !in layers.indices) return this
        val list = layers.toMutableList()
        list[index] = randomLayer(list[index], index, list[index].style, Editing.offeredPalettes.random(random), random)
        return copy(layers = list)
    }

    /** Shared symmetry of new layers; 1 and 2 look lopsided for most styles. */
    val SYMMETRY = 3..8

    /** Complexity and density of the second layer and above, relative to the first. */
    const val UPPER_LAYER_CALM = 0.8

    private fun randomLayer(base: StyleLayer, index: Int, style: MandalaStyle, palette: Int, random: Random): StyleLayer {
        val calm = if (index == 0) 1.0 else UPPER_LAYER_CALM
        // Strange Attractor piles up hundreds of thousands of points per run; with the general ranges
        // about one throw in four burns to a white disc, so it gets calmer ones of its own.
        if (style == MandalaStyle.STRANGE_ATTRACTOR) return base.copy(
            style = style,
            seed = random.nextLong().toULong().coerceAtLeast(1uL),
            paletteIndex = palette,
            scale = if (index == 0) random.nextDouble(0.75, 1.0) else random.nextDouble(0.3, 0.75),
            complexity = random.nextDouble(0.15, 0.45) * calm,
            density = random.nextDouble(0.1, 0.45) * calm,
            glowIntensity = random.nextDouble(0.2, 0.6),
            colorDrift = random.nextDouble(0.1, 0.9),
            ripple = 0.0,
            wash = 0.0,
            abstractLevel = random.nextDouble(0.1, 0.3),
            saturation = random.nextDouble(0.3, 1.0),
            brightness = random.nextDouble(0.35, 0.5),
        )
        return base.copy(
            style = style,
            seed = random.nextLong().toULong().coerceAtLeast(1uL),
            paletteIndex = palette,
            scale = if (index == 0) random.nextDouble(0.75, 1.0) else random.nextDouble(0.3, 0.75),
            complexity = random.nextDouble(0.2, 1.0) * calm,
            density = random.nextDouble(0.2, 1.0) * calm,
            glowIntensity = random.nextDouble(0.2, 0.9),
            colorDrift = random.nextDouble(0.1, 0.9),
            ripple = random.nextDouble(0.0, 0.6),
            // Wash works like a switch (all of it happens between 0 and 0.05) and whitens the layer.
            wash = 0.0,
            abstractLevel = random.nextDouble(0.1, 0.5),
            saturation = random.nextDouble(0.3, 1.0),
            brightness = random.nextDouble(0.3, 0.6),
        )
    }

    /** The dice of the macOS emblem card: same fields and ranges; size, rotation, opacity and blending stay. */
    fun EmblemLayerSettings.randomized(random: Random): EmblemLayerSettings = copy(
        motif = EmblemMotif.entries.random(random),
        rendering = EmblemRendering.entries.random(random),
        frame = EmblemFrame.entries.random(random),
        seed = random.nextLong(1, 1_000_000).toULong(),
        branching = random.nextDouble(0.2, 0.9),
        weight = random.nextDouble(0.3, 0.8),
        paletteIndex = Editing.offeredPalettes.random(random),
        colorPosition = random.nextDouble(),
        colorSpread = random.nextDouble(0.0, 0.6),
        glow = random.nextDouble(0.2, 0.8),
        glowMode = EmblemGlowMode.LIGHT,
        organic = random.nextDouble(0.2, 1.0),
        flowerVariant = EmblemFlowerVariant.entries.random(random),
        petals = Editing.lotusPetals.random(random),
    )

    /** Like the macOS app: up to 20 tries for a value not used yet. */
    private fun <T> pickAvoiding(used: MutableSet<T>, random: Random, next: () -> T): T {
        var value = next()
        var tries = 0
        while (value in used && tries < 20) {
            value = next()
            tries++
        }
        used += value
        return value
    }
}
