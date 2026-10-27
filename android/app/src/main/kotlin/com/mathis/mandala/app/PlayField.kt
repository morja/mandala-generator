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
 * The play field (UX-1 19, DM-20): one finger on a square moves many settings of every visible
 * layer and the scene at once; hidden layers stay as they are. In the middle of the field is the
 * mandala as it was; away from it, [Mode.MAP] lets every setting follow a wave of its own (a
 * landscape of mandalas, each layer from its own starting point), [Mode.COMPASS] changes light,
 * shape, depth or colour by direction, more the farther out.
 */
object PlayField {
    enum class Mode { MAP, COMPASS }

    /** Where the finger is (0..1 across and down). */
    data class Touch(val x: Double = 0.5, val y: Double = 0.5)

    /** One landscape: a wave per setting, shared by all layers, and per layer the styles and palettes its zones offer. */
    class Landscape(seed: Long, base: MandalaParameters, paletteCount: Int) {
        private val rng = Random(seed)
        internal val waves: Map<String, Wave> = SETTINGS.associateWith { Wave(rng) }
        internal val zones: List<Zones> = base.layers.map { Zones(it, paletteCount, rng) }
    }

    /** What a layer's zones offer: its own style and palette first, so the middle stays as it was. */
    internal class Zones(layer: StyleLayer, paletteCount: Int, rng: Random) {
        val styles: List<MandalaStyle> = listOf(layer.style) + (PLAY_STYLES - layer.style).shuffled(rng).take(ZONE_STYLES - 1)
        val palettes: List<Int> = listOf(layer.paletteIndex) + (0 until paletteCount).filter { it != layer.paletteIndex }.shuffled(rng).take(ZONE_PALETTES - 1)
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

        /** −1..1 or a little beyond, as the wave stands, without the middle taken out. */
        fun raw(x: Double, y: Double) =
            0.8 * sin(2 * PI * f * (cos(angle) * x + sin(angle) * y) + phase) + 0.25 * sin(2 * PI * f2 * (x - y) + phase2)
    }

    fun apply(base: MandalaParameters, mode: Mode, land: Landscape, touch: Touch): MandalaParameters {
        val layers = base.layers.mapIndexed { i, l ->
            if (!l.isEnabled) l else when (mode) {
                Mode.MAP -> map(l, land.zones.getOrNull(i), land, touch)
                Mode.COMPASS -> compass(l, touch)
            }
        }
        val effects = when (mode) {
            Mode.MAP -> mapScene(base.effectsLayer, land, touch)
            Mode.COMPASS -> compassScene(base.effectsLayer, touch)
        }
        return base.copy(layers = layers, effectsLayer = effects)
    }

    private fun wave(land: Landscape, t: Touch, name: String) = land.waves.getValue(name).at(t.x, t.y)

    private fun map(l: StyleLayer, zones: Zones?, land: Landscape, t: Touch): StyleLayer {
        fun w(name: String) = wave(land, t, name)
        // Zones: the index moves away from the middle's (0) in steps as the wave grows.
        fun zone(name: String, count: Int) = ((w(name) * ZONE_STEPS).roundToInt()).mod(count)
        return l.copy(
            style = zones?.let { it.styles[zone("style", it.styles.size)] } ?: l.style,
            paletteIndex = if (l.customPalette != null || zones == null) l.paletteIndex else zones.palettes[zone("palette", zones.palettes.size)],
            // The layers carry the play (Mathis, 27 October 2026: the scene effects were too much of it).
            symmetry = (l.symmetry + (w("symmetry") * 5).roundToInt()).coerceIn(1, 8),
            complexity = (l.complexity + 0.7 * w("complexity")).coerceIn(0.05, 1.0),
            density = (l.density + 0.7 * w("density")).coerceIn(0.05, 1.0),
            ripple = (l.ripple + 0.8 * w("ripple")).coerceIn(0.0, 1.0),
            scale = (l.scale + 0.4 * w("scale")).coerceIn(0.5, 1.15),
            glowIntensity = (l.glowIntensity + 0.7 * w("glow")).coerceIn(0.0, 1.0),
            colorDrift = (l.colorDrift + 0.7 * w("drift")).coerceIn(0.0, 1.0),
            colorOffset = (l.colorOffset + 0.5 * w("offset")).mod(1.0),
            saturation = (l.saturation + 0.5 * w("saturation")).coerceIn(0.2, 1.0),
            brightness = (l.brightness + 0.3 * w("brightness")).coerceIn(0.25, 0.85),
            wash = (l.wash + 0.4 * w("wash")).coerceIn(0.0, 0.7),
            abstractLevel = (l.abstractLevel + 0.3 * w("abstract")).coerceIn(0.0, 0.6),
        )
    }

    /**
     * The landscape's weather, sparingly (Mathis, 27 October 2026: the scene effects were too much of
     * the play, which the layers should carry): a little bloom and vignette follow their waves, and
     * stars and grain each show only in a small region of their own, so most of a landscape is plain.
     */
    private fun mapScene(e: EffectsLayerSettings, land: Landscape, t: Touch) = scene(
        e,
        bloom = wave(land, t, "light"), vignette = wave(land, t, "depth"),
        stars = region(land, t, "stars"), grain = region(land, t, "grain"),
    )

    /**
     * A region of the field: where the wave itself (not the middle taken out) is well above zero,
     * 0..1 from there; fading in from the middle, which stays the mandala as it was.
     */
    private fun region(land: Landscape, t: Touch, name: String): Double {
        val w = land.waves.getValue(name).raw(t.x, t.y)
        val out = min(1.0, hypot(t.x - 0.5, t.y - 0.5) * 4)
        return ((w - REGION_FROM) / (1 - REGION_FROM)).coerceIn(0.0, 1.0) * out
    }

    /** The compass directions: light up, shape right, depth down, colour left, each 0..1. */
    private class Directions(t: Touch) {
        private val dx = t.x - 0.5
        private val dy = 0.5 - t.y
        private val r = min(1.0, hypot(dx, dy) * 2)
        private val a = atan2(dy, dx)
        private fun toward(direction: Double) = max(0.0, cos(a - direction)) * r
        val light = toward(PI / 2)
        val shape = toward(0.0)
        val depth = toward(-PI / 2)
        val colour = toward(PI)
    }

    private fun compass(l: StyleLayer, t: Touch): StyleLayer {
        val d = Directions(t)
        val light = d.light
        val shape = d.shape
        val depth = d.depth
        val colour = d.colour
        return l.copy(
            symmetry = (l.symmetry + (shape * 4).roundToInt()).coerceIn(1, 8),
            complexity = (l.complexity + 0.6 * shape).coerceIn(0.0, 1.0),
            ripple = (l.ripple + 0.8 * shape).coerceIn(0.0, 1.0),
            density = (l.density + 0.4 * shape).coerceIn(0.0, 1.0),
            glowIntensity = (l.glowIntensity + 0.6 * light - 0.3 * depth).coerceIn(0.0, 1.0),
            brightness = (l.brightness + 0.25 * light - 0.15 * depth).coerceIn(0.25, 0.85),
            wash = (l.wash + 0.45 * depth).coerceIn(0.0, 0.7),
            colorOffset = (l.colorOffset + 0.5 * colour).mod(1.0),
            colorDrift = (l.colorDrift + 0.6 * colour).coerceIn(0.0, 1.0),
            saturation = (l.saturation + 0.4 * colour).coerceIn(0.2, 1.0),
        )
    }

    /** Light: a little bloom (the layers' glow carries it); depth: vignette and a little grain. No stars, no glitter. */
    private fun compassScene(e: EffectsLayerSettings, t: Touch): EffectsLayerSettings {
        val d = Directions(t)
        return scene(e, bloom = d.light, vignette = d.depth, grain = d.depth * 0.5)
    }

    /** The scene effects, each raised by 0..1 of its play range; switched on only where the field changes something. */
    private fun scene(
        e: EffectsLayerSettings,
        bloom: Double = 0.0, vignette: Double = 0.0, stars: Double = 0.0, grain: Double = 0.0,
    ): EffectsLayerSettings {
        val next = e.copy(
            bloom = (e.bloom + 0.4 * bloom).coerceIn(0.0, 0.6),
            vignette = (e.vignette + 0.5 * vignette).coerceIn(0.0, 0.8),
            stars = (e.stars + 0.4 * stars).coerceIn(0.0, 0.5),
            grain = (e.grain + 0.5 * grain).coerceIn(0.0, 0.6),
        )
        // Switched on only where the field changes it, so the middle stays the mandala as it was.
        return next.copy(isEnabled = e.isEnabled || next != e)
    }

    /** Where a scene wave starts to count (−1..1): above it in about a tenth of the field. */
    private const val REGION_FROM = 0.65

    private val SETTINGS = listOf(
        "style", "palette", "symmetry", "complexity", "density", "ripple", "scale", "glow", "drift", "offset",
        "saturation", "brightness", "wash", "abstract", "light", "depth", "stars", "grain",
    )

    /** Styles the landscape's zones choose from: varied, quick to draw. */
    private val PLAY_STYLES = listOf(
        MandalaStyle.SPIROGRAPH, MandalaStyle.ROSE_CURVES, MandalaStyle.FLORAL, MandalaStyle.EPITROCHOID, MandalaStyle.HYPOCYCLOID,
        MandalaStyle.GEOMETRIC, MandalaStyle.SACRED_GEOMETRY, MandalaStyle.STRING_ART, MandalaStyle.SPIDER_WEB,
        MandalaStyle.SUPERFORMULA, MandalaStyle.PETAL_RINGS, MandalaStyle.LOOP_RINGS,
    )
    /**
     * The die with the point in the middle: there a new landscape shows nothing, so the point hops a
     * step out, into the inner third, and the new landscape shows at once (Mathis, 27 October 2026).
     * Elsewhere the point stays where it is.
     */
    fun hopOutOfTheMiddle(touch: Touch, random: Random): Touch {
        if (hypot(touch.x - 0.5, touch.y - 0.5) > MIDDLE) return touch
        val angle = random.nextDouble() * 2 * PI
        val distance = random.nextDouble(HOP_MIN, HOP_MAX)
        return Touch(0.5 + cos(angle) * distance, 0.5 + sin(angle) * distance)
    }

    /** This close to the middle the point counts as in the middle. */
    private const val MIDDLE = 0.08

    /** How far the hop goes, as a fraction of the field: into the inner third, enough to see a change. */
    private const val HOP_MIN = 0.15
    private const val HOP_MAX = 0.3

    private const val ZONE_STYLES = 5
    private const val ZONE_PALETTES = 5
    /** How many zones a wave crosses from the middle to its extremes. */
    private const val ZONE_STEPS = 2.4
}
