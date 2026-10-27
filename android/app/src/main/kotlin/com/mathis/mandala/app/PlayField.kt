package com.mathis.mandala.app

import com.mathis.mandala.core.EffectsLayerSettings
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaStyle
import com.mathis.mandala.core.StyleLayer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The play field (UX-1 19, DM-20): one finger on a square moves many settings of the selected layer
 * and the scene at once. In the middle of the field is the mandala as it was; away from it,
 * [Mode.MAP] lets every setting follow a wave of its own (a landscape of mandalas), [Mode.COMPASS]
 * changes light, shape, depth or colour by direction, more the farther out.
 */
object PlayField {
    enum class Mode { MAP, COMPASS }

    /** Where the finger is (0..1 across and down). */
    data class Touch(val x: Double = 0.5, val y: Double = 0.5)

    /** One landscape: a wave per setting, and the styles and palettes its zones offer. */
    class Landscape(seed: Long, baseStyle: MandalaStyle, basePalette: Int, paletteCount: Int) {
        private val rng = Random(seed)
        internal val waves: Map<String, Wave> = SETTINGS.associateWith { Wave(rng) }
        internal val styles: List<MandalaStyle> = listOf(baseStyle) + (PLAY_STYLES - baseStyle).shuffled(rng).take(ZONE_STYLES - 1)
        internal val palettes: List<Int> = listOf(basePalette) + (0 until paletteCount).filter { it != basePalette }.shuffled(rng).take(ZONE_PALETTES - 1)
    }

    /** A long wave for the landscape and a short one for detail, in its own direction. */
    internal class Wave(rng: Random) {
        private val angle = rng.nextDouble() * 2 * PI
        private val f = 0.5 + rng.nextDouble() * 1.4
        private val phase = rng.nextDouble() * 2 * PI
        private val f2 = 1.5 + rng.nextDouble() * 2
        private val phase2 = rng.nextDouble() * 2 * PI

        /** −1..1 or a little beyond, 0 in the middle of the field. */
        fun at(x: Double, y: Double): Double = raw(x, y) - raw(0.5, 0.5)

        private fun raw(x: Double, y: Double) =
            0.8 * sin(2 * PI * f * (cos(angle) * x + sin(angle) * y) + phase) + 0.25 * sin(2 * PI * f2 * (x - y) + phase2)
    }

    fun apply(base: MandalaParameters, layer: Int, mode: Mode, land: Landscape, touch: Touch): MandalaParameters {
        val l = base.layers.getOrNull(layer) ?: return base
        val (moved, effects) = when (mode) {
            Mode.MAP -> map(l, base.effectsLayer, land, touch)
            Mode.COMPASS -> compass(l, base.effectsLayer, touch)
        }
        return base.copy(layers = base.layers.toMutableList().also { it[layer] = moved }, effectsLayer = effects)
    }

    private fun map(l: StyleLayer, e: EffectsLayerSettings, land: Landscape, t: Touch): Pair<StyleLayer, EffectsLayerSettings> {
        fun w(name: String) = land.waves.getValue(name).at(t.x, t.y)
        // Zones: the index moves away from the middle's (0) in steps as the wave grows.
        fun zone(name: String, count: Int) = ((w(name) * ZONE_STEPS).roundToInt()).mod(count)
        val layer = l.copy(
            style = land.styles[zone("style", land.styles.size)],
            paletteIndex = if (l.customPalette != null) l.paletteIndex else land.palettes[zone("palette", land.palettes.size)],
            symmetry = (l.symmetry + (w("symmetry") * 4).roundToInt()).coerceIn(1, 8),
            complexity = (l.complexity + 0.5 * w("complexity")).coerceIn(0.05, 1.0),
            density = (l.density + 0.5 * w("density")).coerceIn(0.05, 1.0),
            ripple = (l.ripple + 0.6 * w("ripple")).coerceIn(0.0, 1.0),
            scale = (l.scale + 0.3 * w("scale")).coerceIn(0.5, 1.15),
            glowIntensity = (l.glowIntensity + 0.6 * w("glow")).coerceIn(0.0, 1.0),
            colorDrift = (l.colorDrift + 0.6 * w("drift")).coerceIn(0.0, 1.0),
            colorOffset = (l.colorOffset + 0.5 * w("offset")).mod(1.0),
            saturation = (l.saturation + 0.4 * w("saturation")).coerceIn(0.2, 1.0),
            brightness = (l.brightness + 0.25 * w("brightness")).coerceIn(0.25, 0.85),
            wash = (l.wash + 0.4 * w("wash")).coerceIn(0.0, 0.7),
        )
        return layer to scene(e, light = w("light"), depth = w("depth"), stars = w("stars"))
    }

    private fun compass(l: StyleLayer, e: EffectsLayerSettings, t: Touch): Pair<StyleLayer, EffectsLayerSettings> {
        val dx = t.x - 0.5
        val dy = 0.5 - t.y
        val r = min(1.0, hypot(dx, dy) * 2)
        val a = atan2(dy, dx)
        fun toward(direction: Double) = max(0.0, cos(a - direction)) * r
        val light = toward(PI / 2)
        val shape = toward(0.0)
        val depth = toward(-PI / 2)
        val colour = toward(PI)
        val layer = l.copy(
            symmetry = (l.symmetry + (shape * 3).roundToInt()).coerceIn(1, 8),
            complexity = (l.complexity + 0.45 * shape).coerceIn(0.0, 1.0),
            ripple = (l.ripple + 0.7 * shape).coerceIn(0.0, 1.0),
            density = (l.density + 0.3 * shape).coerceIn(0.0, 1.0),
            glowIntensity = (l.glowIntensity + 0.5 * light - 0.3 * depth).coerceIn(0.0, 1.0),
            brightness = (l.brightness + 0.2 * light - 0.15 * depth).coerceIn(0.25, 0.85),
            wash = (l.wash + 0.45 * depth).coerceIn(0.0, 0.7),
            colorOffset = (l.colorOffset + 0.5 * colour).mod(1.0),
            colorDrift = (l.colorDrift + 0.5 * colour).coerceIn(0.0, 1.0),
            saturation = (l.saturation + 0.3 * colour).coerceIn(0.2, 1.0),
        )
        return layer to scene(e, light = light, depth = depth, stars = light * 0.8)
    }

    /** The scene's light: bloom and stars come up with light, the vignette with depth. */
    private fun scene(e: EffectsLayerSettings, light: Double, depth: Double, stars: Double): EffectsLayerSettings {
        val bloom = (e.bloom + 0.6 * light).coerceIn(0.0, 0.8)
        val vignette = (e.vignette + 0.6 * depth).coerceIn(0.0, 0.9)
        val star = (e.stars + 0.6 * stars).coerceIn(0.0, 0.8)
        // Switched on only where the field changes it, so the middle stays the mandala as it was.
        val changed = bloom != e.bloom || star != e.stars || vignette != e.vignette
        return e.copy(isEnabled = e.isEnabled || changed, bloom = bloom, vignette = vignette, stars = star)
    }

    private val SETTINGS = listOf(
        "style", "palette", "symmetry", "complexity", "density", "ripple", "scale", "glow", "drift", "offset",
        "saturation", "brightness", "wash", "light", "depth", "stars",
    )

    /** Styles the landscape's zones choose from: varied, quick to draw. */
    private val PLAY_STYLES = listOf(
        MandalaStyle.SPIROGRAPH, MandalaStyle.ROSE_CURVES, MandalaStyle.FLORAL, MandalaStyle.EPITROCHOID, MandalaStyle.HYPOCYCLOID,
        MandalaStyle.GEOMETRIC, MandalaStyle.SACRED_GEOMETRY, MandalaStyle.STRING_ART, MandalaStyle.SPIDER_WEB,
        MandalaStyle.SUPERFORMULA, MandalaStyle.PETAL_RINGS, MandalaStyle.LOOP_RINGS,
    )
    private const val ZONE_STYLES = 5
    private const val ZONE_PALETTES = 5
    /** How many zones a wave crosses from the middle to its extremes. */
    private const val ZONE_STEPS = 2.4
}
