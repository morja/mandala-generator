package com.mathis.mandala.app

import com.mathis.mandala.core.BaseLayerSettings
import com.mathis.mandala.core.BaseLayerType
import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.DrawStroke
import com.mathis.mandala.core.DrawingLayerSettings
import com.mathis.mandala.core.EffectsLayerSettings
import com.mathis.mandala.core.EmblemLayerSettings
import com.mathis.mandala.core.GraffitiLayerSettings
import com.mathis.mandala.core.Framing
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.MandalaStyle
import com.mathis.mandala.core.SettingsJson
import com.mathis.mandala.core.SprayStroke
import com.mathis.mandala.core.Quote
import com.mathis.mandala.core.StyleLayer
import com.mathis.mandala.core.TextLayerSettings

/**
 * Editing rules of the editor, following the macOS app where it has rules. Pure functions, so they
 * are unit-tested on the JVM. Layers are drawn in list order: the first one lies at the bottom.
 */
object Editing {
    /** Style groups as in the macOS app; only styles the renderer has ported are offered. */
    val styleGroups: List<Pair<String, List<MandalaStyle>>> = listOf(
        "Parametric curves" to listOf(
            MandalaStyle.SPIROGRAPH, MandalaStyle.ROSE_CURVES, MandalaStyle.FLORAL, MandalaStyle.BUTTERFLY,
            MandalaStyle.EPITROCHOID, MandalaStyle.LISSAJOUS, MandalaStyle.HYPOCYCLOID, MandalaStyle.PHYLLOTAXIS,
            MandalaStyle.FRACTAL, MandalaStyle.SUPERFORMULA, MandalaStyle.WAVE_INTERFERENCE,
        ),
        "Geometric and pattern" to listOf(
            MandalaStyle.GEOMETRIC, MandalaStyle.SACRED_GEOMETRY, MandalaStyle.SUNBURST, MandalaStyle.STAR_BURST,
            MandalaStyle.STRING_ART, MandalaStyle.SPIDER_WEB, MandalaStyle.WEAVE, MandalaStyle.RADIAL_MESH, MandalaStyle.MOIRE,
        ),
        "Organic and flow" to listOf(
            MandalaStyle.FLOW_FIELD, MandalaStyle.TENDRIL, MandalaStyle.VORONOI, MandalaStyle.STRANGE_ATTRACTOR,
            MandalaStyle.CONSTELLATION_WEB, MandalaStyle.DEEP_NEBULA, MandalaStyle.INTERFERENCE_BLOOM,
        ),
        "3D" to listOf(
            MandalaStyle.HYPERBOLOID, MandalaStyle.TORUS, MandalaStyle.NAUTILUS, MandalaStyle.SPHERE_GRID,
            MandalaStyle.TORUS_KNOT, MandalaStyle.TESSERACT,
        ),
        "Special" to listOf(MandalaStyle.UNIVERSE, MandalaStyle.SYMBOLS, MandalaStyle.MIXED),
    ).map { (name, list) -> name to list.filter { it in MandalaRenderer.supportedStyles } }.filter { it.second.isNotEmpty() }

    /** All offered styles in macOS order. */
    val styles: List<MandalaStyle> = MandalaStyle.entries.filter { it in MandalaRenderer.supportedStyles }

    /** Background types the renderer can draw; "off" is a disabled base layer. */
    val backgrounds = listOf(
        BaseLayerType.AUTO, BaseLayerType.COLOR, BaseLayerType.GRADIENT, BaseLayerType.SUNBURST, BaseLayerType.GRAIN, BaseLayerType.IMAGE,
    )

    // ── Choices offered in the pickers (TUNE-1) ──

    /**
     * Built-in palettes offered in the pickers, the dice and "add layer", by index into
     * [BuiltInPalettes.all]: colourful first, then once round the colour wheel. The others (Prism,
     * Candy, Nebula, Ocean, Synthwave, Dragon, Cosmic) look too much like one of these; they keep
     * rendering in saved settings and are shown in the picker only while a layer uses them.
     */
    val offeredPalettes: List<Int> = listOf(0, 3, 13, 4, 16, 17, 7, 9, 14, 1, 11)

    /** The palettes a picker shows: the offered ones, plus `current` at the end if it is a hidden built-in one. */
    fun paletteChoices(current: Int): List<Int> =
        if (current in BuiltInPalettes.all.indices && current !in offeredPalettes) offeredPalettes + current else offeredPalettes

    /** Palette of a new layer above one with palette `last`: three steps on in the offered order, like the Mac's `+ 3`. */
    fun nextPalette(last: Int?): Int {
        if (last == null) return offeredPalettes.first()
        val at = offeredPalettes.indexOf(last).coerceAtLeast(0)
        return offeredPalettes[(at + 3) % offeredPalettes.size]
    }

    /** Background types offered: Grain only while it is the current type (it vanishes in large exports, TUNE-1). */
    fun backgroundChoices(current: BaseLayerSettings): List<BaseLayerType> =
        if (current.type == BaseLayerType.GRAIN) backgrounds else backgrounds - BaseLayerType.GRAIN

    /**
     * Styles whose drawing reads the density setting. Found in the renderer code (`StyleContext.density`)
     * and checked by `StyleControlsTest`, which renders every style with low and high density.
     */
    val densityStyles: Set<MandalaStyle> = MandalaStyle.entries.toSet() - setOf(
        MandalaStyle.SPIROGRAPH, MandalaStyle.ROSE_CURVES, MandalaStyle.BUTTERFLY, MandalaStyle.EPITROCHOID,
        MandalaStyle.LISSAJOUS, MandalaStyle.HYPOCYCLOID, MandalaStyle.GEOMETRIC, MandalaStyle.SUNBURST, MandalaStyle.STRING_ART,
    )

    /** Styles whose curves the ripple setting bends; the line and point styles drawn directly ignore it. */
    val rippleStyles: Set<MandalaStyle> = MandalaStyle.entries.toSet() - setOf(
        MandalaStyle.SACRED_GEOMETRY, MandalaStyle.SUNBURST, MandalaStyle.STAR_BURST, MandalaStyle.STRING_ART,
        MandalaStyle.STRANGE_ATTRACTOR, MandalaStyle.CONSTELLATION_WEB, MandalaStyle.DEEP_NEBULA, MandalaStyle.INTERFERENCE_BLOOM,
        MandalaStyle.HYPERBOLOID, MandalaStyle.TORUS, MandalaStyle.NAUTILUS, MandalaStyle.UNIVERSE, MandalaStyle.SYMBOLS,
    )

    /**
     * Slider ranges where the setting gives usable results (TUNE-1). The settings JSON keeps any value:
     * a saved value outside a range stays as it is until that slider is moved.
     */
    object Ranges {
        val layerScale = 0.2..1.2
        val layerComplexity = 0.15..1.0
        val layerPainted = 0.25..0.85
        val layerBrightness = 0.15..0.85
        val emblemSize = 0.2..0.9
        val emblemWeight = 0.1..1.0
        val textSize = 0.03..0.12
        val textWidth = 0.4..1.0
        val textBrightness = 0.0..2.0
        val textCloudSize = 0.0..0.7
        /** Brighter colour backgrounds drown the mandala, whose lines are added with Screen. */
        val backgroundBrightness = 0.0..0.6
    }

    /** Colours a background starts with when it is switched on, brighter than the decoder defaults (0.18 and 0.04). */
    const val BACKGROUND_START_BRIGHTNESS = 0.35
    const val BACKGROUND_START_BRIGHTNESS2 = 0.08

    /**
     * The background switched on with `type`. While its colours are still the decoder defaults, which
     * are so dark that Colour, Gradient and Sunburst look alike, they start brighter.
     */
    fun BaseLayerSettings.switchedTo(type: BaseLayerType): BaseLayerSettings {
        val next = copy(isEnabled = true, type = type)
        val defaults = BaseLayerSettings()
        val untouched = brightness == defaults.brightness && brightness2 == defaults.brightness2
        return if (untouched) next.copy(brightness = BACKGROUND_START_BRIGHTNESS, brightness2 = BACKGROUND_START_BRIGHTNESS2) else next
    }

    const val PREVIEW_SIZE = 512

    /**
     * Width at which the fast preview blurs glow and wash, instead of the macOS 1600 px. Measured on
     * the starter favourites at preview size: 40 % less time, at least 52 dB PSNR (ADR-0004).
     */
    const val FAST_GLOW_REFERENCE = 800

    /** Size of a new mandala shown next to the current one in the menu (DM-7). */
    const val CANDIDATE_SIZE = 320

    /**
     * A rough image first, when a change redraws at least this many layers and is expected to take
     * longer than [QUICK_AFTER_MS]: small and with a coarse glow, replaced by the full one when ready.
     */
    const val QUICK_MIN_LAYERS = 2
    const val QUICK_AFTER_MS = 1000L
    const val QUICK_SIZE = 160
    const val QUICK_GLOW_REFERENCE = 320

    /** Side of the small image above the controls, as a fraction of the full one. */
    const val COMPACT_CANVAS_FRACTION = 0.32f
    const val EXPORT_SIZE = 1024

    /** Pro export (ADR-0007). */
    const val LARGE_EXPORT_SIZE = 2048

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
        val layer = StyleLayer(
            style = styles[layers.size % styles.size],
            scale = 0.65,
            paletteIndex = nextPalette(layers.lastOrNull()?.paletteIndex),
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

    fun MandalaParameters.updateEffects(change: (EffectsLayerSettings) -> EffectsLayerSettings): MandalaParameters =
        copy(effectsLayer = change(effectsLayer))

    /** New random positions for spots, highlights, stars and glitter; strengths stay. */
    fun EffectsLayerSettings.newPattern(nextSeed: () -> ULong): EffectsLayerSettings = copy(
        dimmingSeed = nextSeed(), erasureSeed = nextSeed(), highlightsSeed = nextSeed(),
        starsSeed = nextSeed(), glitterSeed = nextSeed(),
    )

    fun MandalaParameters.updateText(change: (TextLayerSettings) -> TextLayerSettings): MandalaParameters =
        copy(textLayer = change(textLayer))

    /** Puts a quote in and shows the text layer, like "Random quote" on the Mac. */
    fun TextLayerSettings.withQuote(quote: Quote): TextLayerSettings =
        copy(text = quote.text, customAuthor = quote.author, isEnabled = true)

    fun MandalaParameters.updateEmblem(change: (EmblemLayerSettings) -> EmblemLayerSettings): MandalaParameters =
        copy(emblemLayer = change(emblemLayer))

    /** Petal counts the macOS Lotus picker offers. */
    val lotusPetals = listOf(6, 8, 12, 16)

    /** The macOS reset button of the emblem card: all defaults, but it stays on or off. */
    fun EmblemLayerSettings.reset(): EmblemLayerSettings = EmblemLayerSettings(isEnabled = isEnabled)

    /** A new tree shape, in the range the macOS dice use. */
    fun EmblemLayerSettings.newShape(random: kotlin.random.Random): EmblemLayerSettings =
        copy(seed = random.nextLong(1, 1_000_000).toULong())

    fun MandalaParameters.updateDrawing(change: (DrawingLayerSettings) -> DrawingLayerSettings): MandalaParameters =
        copy(drawingLayer = change(drawingLayer))

    fun MandalaParameters.updateGraffiti(change: (GraffitiLayerSettings) -> GraffitiLayerSettings): MandalaParameters =
        copy(graffitiLayer = change(graffitiLayer))

    /**
     * Adds a finished finger stroke (points normalised to the canvas) in the tool's current colour and
     * switches its layer on. Lines need two points, spray paints single dots too, as on the Mac.
     */
    fun MandalaParameters.addStroke(tool: DrawTool, xs: List<Double>, ys: List<Double>): MandalaParameters = when (tool) {
        DrawTool.LINES -> if (xs.size < 2) this else updateDrawing {
            it.copy(isEnabled = true, strokes = it.strokes + DrawStroke(xs, ys, it.currentHue, it.currentSaturation, it.currentBrightness))
        }
        DrawTool.SPRAY -> if (xs.isEmpty()) this else updateGraffiti {
            it.copy(
                isEnabled = true,
                strokes = it.strokes + SprayStroke(
                    xs, ys, it.currentBrushSize, it.currentHue, it.currentSaturation, it.currentBrightness, it.currentOpacity,
                ),
            )
        }
    }

    fun MandalaParameters.removeLastStroke(tool: DrawTool): MandalaParameters = when (tool) {
        DrawTool.LINES -> updateDrawing { it.copy(strokes = it.strokes.dropLast(1)) }
        DrawTool.SPRAY -> updateGraffiti { it.copy(strokes = it.strokes.dropLast(1)) }
    }

    fun MandalaParameters.clearStrokes(tool: DrawTool): MandalaParameters = when (tool) {
        DrawTool.LINES -> updateDrawing { it.copy(strokes = emptyList()) }
        DrawTool.SPRAY -> updateGraffiti { it.copy(strokes = emptyList()) }
    }

    fun MandalaParameters.strokeCount(tool: DrawTool): Int = when (tool) {
        DrawTool.LINES -> drawingLayer.strokes.size
        DrawTool.SPRAY -> graffitiLayer.strokes.size
    }

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

    // ── Positions of layers and the emblem (ADR-0011) ──

    /** How far a centre may move from the mandala's centre, in fractions of its square. */
    const val MAX_OFFSET = 1.0

    /**
     * The layer moved by (dx, dy) (fractions of the canvas), its size multiplied by `zoom` and turned
     * by `turnDegrees` clockwise, within the ranges its controls offer.
     */
    fun StyleLayer.movedBy(dx: Double, dy: Double, zoom: Double = 1.0, turnDegrees: Double = 0.0): StyleLayer = copy(
        offsetX = (offsetX + dx).coerceIn(-MAX_OFFSET, MAX_OFFSET),
        offsetY = (offsetY + dy).coerceIn(-MAX_OFFSET, MAX_OFFSET),
        scale = zoomedWithin(scale, zoom, Ranges.layerScale),
        // A layer's rotation turns counter-clockwise on screen, the fingers' turn is clockwise.
        rotation = ((rotation - turnDegrees / 360.0) % 1.0).let { if (it < 0) it + 1.0 else it },
    )

    /** The emblem moved, sized and turned like [movedBy] for a layer; its rotation is in degrees. */
    fun EmblemLayerSettings.movedBy(dx: Double, dy: Double, zoom: Double = 1.0, turnDegrees: Double = 0.0): EmblemLayerSettings = copy(
        offsetX = (offsetX + dx).coerceIn(-MAX_OFFSET, MAX_OFFSET),
        offsetY = (offsetY + dy).coerceIn(-MAX_OFFSET, MAX_OFFSET),
        size = zoomedWithin(size, zoom, Ranges.emblemSize),
        rotation = ((rotation + turnDegrees + 180.0) % 360.0).let { if (it < 0) it + 360.0 else it } - 180.0,
    )

    /**
     * `value` times `zoom`, kept within `range` like its slider; a saved value already outside the
     * range is never pushed further out nor snapped in by a move without zoom.
     */
    private fun zoomedWithin(value: Double, zoom: Double, range: ClosedFloatingPointRange<Double>): Double =
        if (zoom == 1.0) value else (value * zoom).coerceIn(minOf(range.start, value), maxOf(range.endInclusive, value))

    // ── Saving in a format (ADR-0010) ──

    /** Formats offered when saving. The phone screen follows the device's aspect ratio. */
    enum class SaveFormat(val label: String, val across: Int = 0, val down: Int = 0) {
        SQUARE("Square", 1, 1),
        SCREEN("This screen"),
        FOUR_FIVE("4:5", 4, 5),
        THREE_FOUR("3:4", 3, 4),
        TWO_THREE("2:3", 2, 3),
        NINE_SIXTEEN("9:16", 9, 16),
        FIVE_FOUR("5:4", 5, 4),
        FOUR_THREE("4:3", 4, 3),
        THREE_TWO("3:2", 3, 2),
        SIXTEEN_NINE("16:9", 16, 9),
        ;

        /** The fixed ratios offered in the list next to Square and This screen. */
        val isRatio: Boolean get() = across > 0 && this != SQUARE
        val isLandscape: Boolean get() = across > down
    }

    /** Tallest screen the phone screen format follows (21:9); `MemoryBudgetTest` checks this height at 2048 px. */
    const val MAX_SCREEN_ASPECT = 21.0 / 9.0

    /** Shorter side of the preview in the save sheet. */
    const val FRAME_PREVIEW_WIDTH = 360

    /** Height over width of `format`; `screenAspect` is the device's (taller side over shorter side). */
    fun aspect(format: SaveFormat, screenAspect: Double): Double = when (format) {
        SaveFormat.SCREEN -> screenAspect.coerceIn(1.0, MAX_SCREEN_ASPECT)
        else -> format.down.toDouble() / format.across
    }

    /** Width and height of an image of `format` whose shorter side is `side` pixels. */
    fun size(format: SaveFormat, screenAspect: Double, side: Int): Pair<Int, Int> {
        val aspect = aspect(format, screenAspect)
        return if (aspect >= 1.0) side to Math.round(side * aspect).toInt() else Math.round(side / aspect).toInt() to side
    }

    /** Output size when saving: the shorter side 1024 px, or 2048 px with Pro. */
    fun exportSize(format: SaveFormat, screenAspect: Double, large: Boolean): Pair<Int, Int> =
        size(format, screenAspect, if (large) LARGE_EXPORT_SIZE else EXPORT_SIZE)

    /** File name with the size added for anything but the normal square. */
    fun fileName(params: MandalaParameters, width: Int, height: Int): String {
        val name = fileName(params)
        return when {
            width == height && width == EXPORT_SIZE -> name
            width == height -> name.removeSuffix(".png") + "-$width.png"
            else -> name.removeSuffix(".png") + "-${width}x$height.png"
        }
    }

    /** The framing moved by (dx, dy), given as fractions of the frame's width and height. */
    fun Framing.moved(dx: Double, dy: Double): Framing = copy(centreX = centreX + dx, centreY = centreY + dy).clamped()

    /**
     * The framing zoomed by `factor` around the point (px, py), given as fractions of the frame: the
     * point under the fingers stays where it is.
     */
    fun Framing.zoomedAround(px: Double, py: Double, factor: Double): Framing {
        val newZoom = (zoom * factor).coerceIn(Framing.MIN_ZOOM, Framing.MAX_ZOOM)
        val f = newZoom / zoom
        return Framing(px + (centreX - px) * f, py + (centreY - py) * f, newZoom).clamped()
    }

    private fun settingsHash(params: MandalaParameters): String {
        var h = 0xcbf29ce484222325uL
        for (b in SettingsJson.encode(params).encodeToByteArray()) {
            h = (h xor (b.toULong() and 0xFFuL)) * 0x100000001b3uL
        }
        return h.toString(16).padStart(16, '0').take(8)
    }
}

/** What drags on the canvas move in the move mode (ADR-0011). */
enum class MoveTarget { LAYER, EMBLEM }

/** The two hand-drawing tools of the macOS app: glowing lines (drawing layer) and spray paint (graffiti layer). */
enum class DrawTool(val label: String) { LINES("Lines"), SPRAY("Spray") }
