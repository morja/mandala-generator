package com.mathis.mandala.app

import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaStyle
import com.mathis.mandala.core.StyleLayer
import kotlin.random.Random

/**
 * The macOS app's dice: new layers ("Randomize Layers"), new layers and background ("Randomize All")
 * and one layer. Same ranges as AppState.swift; effects, text and drawing stay as they are.
 */
object Randomize {

    fun MandalaParameters.randomLayers(random: Random): MandalaParameters {
        val count = random.nextInt(1, 4)
        val symmetry = random.nextInt(1, 9)
        val usedStyles = mutableSetOf<MandalaStyle>()
        val usedPalettes = mutableSetOf<Int>()
        val layers = List(count) { index ->
            val style = pickAvoiding(usedStyles, random) { Editing.styles.random(random) }
            val palette = pickAvoiding(usedPalettes, random) { random.nextInt(BuiltInPalettes.all.size) }
            randomLayer(StyleLayer(), index, style, palette, random).copy(colorOffset = random.nextDouble(), symmetry = symmetry)
        }
        return copy(seed = random.nextLong().toULong(), layers = layers)
    }

    fun MandalaParameters.randomAll(random: Random): MandalaParameters {
        val next = randomLayers(random)
        val types = Editing.backgrounds
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
        list[index] = randomLayer(list[index], index, Editing.styles.random(random), random.nextInt(BuiltInPalettes.all.size), random)
        return copy(layers = list)
    }

    private fun randomLayer(base: StyleLayer, index: Int, style: MandalaStyle, palette: Int, random: Random) = base.copy(
        style = style,
        seed = random.nextLong().toULong().coerceAtLeast(1uL),
        paletteIndex = palette,
        scale = if (index == 0) random.nextDouble(0.75, 1.0) else random.nextDouble(0.3, 0.75),
        complexity = random.nextDouble(0.2, 1.0),
        density = random.nextDouble(0.2, 1.0),
        glowIntensity = random.nextDouble(0.2, 0.9),
        colorDrift = random.nextDouble(0.1, 0.9),
        ripple = random.nextDouble(0.0, 0.6),
        wash = random.nextDouble(0.0, 0.5),
        abstractLevel = random.nextDouble(0.1, 0.8),
        saturation = random.nextDouble(0.3, 1.0),
        brightness = random.nextDouble(0.3, 0.7),
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
