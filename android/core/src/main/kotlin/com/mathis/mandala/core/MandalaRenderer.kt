package com.mathis.mandala.core

import com.mathis.mandala.core.styles.AttractorPoints
import com.mathis.mandala.core.styles.CurveStyles
import com.mathis.mandala.core.styles.CurveTask
import com.mathis.mandala.core.styles.DirectStyles
import com.mathis.mandala.core.styles.LineList
import com.mathis.mandala.core.styles.ParametricStyles
import com.mathis.mandala.core.styles.PatternStyles
import com.mathis.mandala.core.styles.SolidStyles
import com.mathis.mandala.core.styles.SymbolStyles
import com.mathis.mandala.core.styles.ThreeDStyles
import com.mathis.mandala.core.styles.CosmicStyles
import com.mathis.mandala.core.styles.StyleContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class RenderOptions(
    /** Overrides the size from the settings (the preview uses a smaller one). The image is square. */
    val outputSize: Int? = null,
    /** Output width and height of a canvas that need not be square (ADR-0010); they override `outputSize`. */
    val width: Int? = null,
    val height: Int? = null,
    /** Where the mandala sits on the canvas; the default is today's centred image. */
    val framing: Framing = Framing(),
    /** The text layer moves and scales with the mandala instead of staying in place on the canvas. */
    val textFollowsMandala: Boolean = false,
    /**
     * Like the macOS app, everything is drawn at twice the output size and scaled down at the end.
     * False draws at the output size itself: the image then looks like the supersampled one at half
     * its size, with twice the detail, for the memory of that half size (the app's large export).
     */
    val supersample: Boolean = true,
    /** Threads for drawing and filters. The image does not depend on it. */
    val threads: Int = Runtime.getRuntime().availableProcessors(),
    /** Draws text for the text layer; supplied by the platform (fonts are not part of the core). */
    val textRenderer: TextRenderer? = null,
    /** Loads the image of an image background, stretched to a square of the given size (sRGB). */
    val imageLoader: ImageLoader? = null,
    /** Polled between steps; when it returns true the render stops with [RenderCancelledException]. */
    val isCancelled: () -> Boolean = { false },
    /** Called on the rendering thread with the share of the work done, rising to 1 at the end. */
    val onProgress: (Double) -> Unit = {},
    /** Reuses the images of layers that did not change since the last render with this cache. */
    val layerCache: LayerCache? = null,
    /**
     * Width in pixels at which glow, wash and other soft light are blurred. The default is the macOS
     * renderer's; a smaller one is faster and softens the light slightly (the app's fast preview).
     */
    val glowReferenceWidth: Int = 1600,
    /** A last adjustment of the finished image (ADR-0014); the default leaves it untouched. */
    val finish: Finish = Finish(),
)

fun interface ImageLoader {
    /** Returns the image at `url` as a `size` x `size` sRGB image, or null if it cannot be read. */
    fun load(url: String, size: Int): Image8?
}

class RenderCancelledException : RuntimeException("Render cancelled")

data class RenderResult(val image: Image8, val warnings: List<String>, val millis: Long)

/** Port of the macOS MandalaRenderer.render pipeline (proof-of-concept subset, see warnings). */
object MandalaRenderer {

    /** Every style of the macOS app is ported. */
    val supportedStyles: Set<MandalaStyle> = MandalaStyle.entries.toSet()

    /** Whether the renderer draws this background type (a solid colour or one computed per pixel). */
    fun supportsBackground(type: BaseLayerType): Boolean = type == BaseLayerType.COLOR || Background.supports(type)

    fun render(
        params: MandalaParameters,
        options: RenderOptions = RenderOptions(),
        palettes: List<ColorPalette> = BuiltInPalettes.all,
    ): RenderResult {
        val start = System.nanoTime()
        val warnings = mutableListOf<String>()
        val outW = options.width ?: options.outputSize ?: params.resolvedOutputSize
        val outH = options.height ?: outW
        val factor = if (options.supersample) 2 else 1
        val bufW = outW * factor
        val bufH = outH * factor
        // The mandala layers, hand layers and emblem are drawn for a square of side `mandalaSize`
        // around the framing's centre; background and effects layer belong to the canvas.
        val place = Placement.of(options.framing, bufW, bufH)
        val mandalaSize = place.size
        val baseRadius = mandalaSize * BASE_RADIUS
        // Same lines spread over a larger canvas give a lower mean; this linear factor cancels it.
        val brightnessScale = mandalaSize / 1600f
        // Offset of the mandala centre from the canvas centre (zero with the default framing).
        val dX = place.centreX - bufW / 2.0
        val dY = place.centreY - bufH / 2.0

        Parallel(max(1, options.threads)).use { par ->
            val fx = LayerEffects(par, options.glowReferenceWidth)

            // ── Background ──
            val base = params.baseLayer
            if (base.isEnabled && base.type != BaseLayerType.COLOR && !Background.supports(base.type)) {
                warnings += "Background \"${base.type.name.lowercase()}\" is not available yet; drawn black."
            }
            var composite = if (base.isEnabled && base.type == BaseLayerType.AUTO) {
                Background.renderAuto(params, palettes, bufW, bufH, place.centreX, place.centreY, mandalaSize, par)
            } else if (base.isEnabled && base.type == BaseLayerType.IMAGE) {
                val picture = base.imageURL?.let { url -> options.imageLoader?.load(url, max(bufW, bufH)) }
                if (picture == null) warnings += "Background image not found; drawn in colour 1."
                Background.renderImage(base, picture, bufW, bufH, par)
            } else if (base.isEnabled && Background.supports(base.type)) {
                Background.render(base, bufW, bufH, place.centreX, place.centreY, par)
            } else {
                Image8(bufW, bufH, Encoding.SRGB)
            }
            val solidBackground = if (base.isEnabled && base.type == BaseLayerType.COLOR) solidColor(base) else null
            val graffiti = params.graffitiLayer.isEnabled && params.graffitiLayer.strokes.isNotEmpty()
            val drawing = params.drawingLayer.isEnabled && params.drawingLayer.strokes.isNotEmpty()
            val text = params.textLayer.isEnabled && params.textLayer.text.isNotEmpty()
            val progress = RenderProgress(
                options.onProgress,
                background = if (base.isEnabled && base.type != BaseLayerType.COLOR) RenderProgress.BACKGROUND else 0.0,
                layers = params.layers.count { it.isEnabled && it.style in supportedStyles },
                above = (if (graffiti) RenderProgress.HAND_LAYER else 0.0) + (if (drawing) RenderProgress.HAND_LAYER else 0.0) +
                    (if (params.emblemLayer.isEnabled) RenderProgress.EMBLEM else 0.0) +
                    (if (text && options.textRenderer != null) RenderProgress.TEXT else 0.0) +
                    (if (params.effectsLayer.isEnabled) RenderProgress.EFFECTS else 0.0),
            )
            progress.background()

            // ── Layers ──
            fun checkpoint() { if (options.isCancelled()) throw RenderCancelledException() }
            val cache = options.layerCache
            cache?.begin()

            var layerNo = 0
            for ((li, layer) in params.layers.withIndex()) {
                checkpoint()
                if (!layer.isEnabled) continue
                if (layer.style !in supportedStyles) {
                    warnings += "Layer ${li + 1} (${layer.style.displayName}) is not available yet; skipped."
                    continue
                }
                // Reaching a layer also completes the one before, whichever way that one ended.
                val n = layerNo++
                progress.layer(n, 0.0)
                val palette = palettes[layer.paletteIndex.coerceIn(0, palettes.size - 1)]
                val op = when (layer.blendMode) {
                    LayerBlendMode.SCREEN -> BlendOp.SCREEN
                    LayerBlendMode.ADD -> BlendOp.ADD
                    LayerBlendMode.NORMAL -> BlendOp.LIGHTEN
                    LayerBlendMode.MULTIPLY -> BlendOp.MULTIPLY
                }
                val cacheKey = cache?.let {
                    LayerCache.Key(
                        layer, if (layer.seed == 0uL) li else -1, params.seed, palette,
                        bufW, bufH, place.centreX, place.centreY, mandalaSize, options.glowReferenceWidth,
                    )
                }
                val cached = cacheKey?.let { cache.get(it) }
                if (cached != null) {
                    composite = fx.composite(composite, cached.image, op, cached.x0, cached.y0)
                    continue
                }
                val layerSeed = if (layer.seed == 0uL) {
                    params.seed + (li + 1).toULong() * 0x9e3779b97f4a7c15uL
                } else {
                    layer.seed
                }
                val rng = SeededRng(layerSeed)
                val symmetry = layer.symmetry.coerceIn(1, 8)
                val layerRadius = baseRadius * layer.scale.coerceIn(0.1, 1.2)
                val layerCount = max(2, (layer.complexity * 8).toInt() + 1)

                // The macOS renderer always draws into a buffer large enough for the full radius and
                // crops afterwards. Only a rotated layer can show that outer area, so only it gets the
                // full size; others get a margin as wide as glow, wash and painting reach, so light
                // from just outside the frame still bleeds in. Size-dependent effects use effectWidth.
                val raw = ceil(layerRadius * 2.0).toInt() + 8
                val effectWidth = max(mandalaSize, if (raw % 2 == 0) raw else raw + 1)
                val rotated = abs(layer.rotation) > 0.001
                val margin = effectMargin(layer, effectWidth)
                // A moved layer shows parts beyond the centred square, so it gets the full one (ADR-0011).
                val moved = layer.offsetX != 0.0 || layer.offsetY != 0.0
                // The layer's own offset from the mandala centre (ADR-0011): whole pixels move its square,
                // the rest goes into the centre it is drawn around (zero for a centred layer).
                val lX = dX + layer.offsetX * mandalaSize
                val lY = dY + layer.offsetY * mandalaSize
                val lXi = Math.round(lX).toInt()
                val lYi = Math.round(lY).toInt()
                // A canvas reaching past the centred square and its margin (portrait, landscape, zoomed
                // out, ADR-0010) or a moved layer (ADR-0011) shows the layer's outer area, and many
                // styles draw beyond the macOS buffer (see styleReach). Such a layer gets a square as
                // large as its style reaches plus the effect margin; a smaller one shows its edge as a
                // hard line. The centred square canvas keeps the old buffers, so its images stay the same.
                val small = min(effectWidth, mandalaSize + 2 * margin)
                val smallX0 = lXi - (small - bufW) / 2
                val smallY0 = lYi - (small - bufH) / 2
                val canvasBeyond = smallX0 > -margin || smallY0 > -margin || smallX0 + small < bufW + margin || smallY0 + small < bufH + margin
                val size = when {
                    moved || canvasBeyond -> {
                        val reached = ceil(2 * styleReach(layer.style) * layerRadius).toInt() + 2 * margin + 8
                        max(effectWidth, reached + (reached and 1))
                    }
                    rotated -> effectWidth
                    else -> small
                }

                // That square of side `size` sits around the mandala centre at (sx0, sy0) on the
                // canvas. Only the part that can reach the canvas is drawn: for an unrotated layer the
                // canvas plus the effect margin, for a rotated one what can turn into view. With the
                // default framing this is the whole square, as before.
                val layerCentreX = bufW / 2.0 + lX
                val layerCentreY = bufH / 2.0 + lY
                val sx0 = lXi - (size - bufW) / 2
                val sy0 = lYi - (size - bufH) / 2
                var view = when {
                    !rotated -> intArrayOf(max(sx0, -margin), max(sy0, -margin), min(sx0 + size, bufW + margin), min(sy0 + size, bufH + margin))
                    size <= 2 * max(bufW, bufH) -> intArrayOf(sx0, sy0, sx0 + size, sy0 + size)
                    else -> {
                        val reach = farthestCorner(layerCentreX, layerCentreY, bufW, bufH, margin)
                        intArrayOf(
                            max(sx0, floor(layerCentreX - reach).toInt()), max(sy0, floor(layerCentreY - reach).toInt()),
                            min(sx0 + size, ceil(layerCentreX + reach).toInt()), min(sy0 + size, ceil(layerCentreY + reach).toInt()),
                        )
                    }
                }
                val vw = view[2] - view[0]
                val vh = view[3] - view[1]
                if (vw <= 0 || vh <= 0) {
                    composite = fx.composite(composite, null, op)
                    continue
                }
                val cx = size * 0.5f + (lX - lXi).toFloat() - (view[0] - sx0)
                val cy = size * 0.5f + (lY - lYi).toFloat() - (view[1] - sy0)
                // Noise of the painted effect is sampled where this part sits in the effect square.
                var offsetX = (effectWidth - size) / 2 + (view[0] - sx0)
                var offsetY = (effectWidth - size) / 2 + (view[1] - sy0)
                val reject = PixelBuffer.Reject((view[0] - sx0).toFloat(), (view[1] - sy0).toFloat(), (size * 4).toFloat())

                val ctx = StyleContext(
                    layer.complexity, layer.density, layer.colorDrift, params.seed, layer.spokes,
                    layer.abstractLevel, layer.glowIntensity,
                )
                var img = run {
                    val tasks = ArrayList<CurveTask>()
                    val lines = LineList()
                    val points = ArrayList<AttractorPoints>()
                    collectStyle(
                        layer.style, tasks, lines, points, cx, cy, layerRadius, ctx, palette, rng,
                        layerCount, symmetry, layer.ripple.toFloat(), layer.colorOffset,
                    )
                    // Same order per pixel as one full buffer: attractor points, curves, lines.
                    // Lines are hairlines up to the preview's buffer (1024 px); a mandala drawn larger (the
                    // export, or zoomed in) gets proportionally wider lines and keeps the reference's
                    // brightness, so it looks like the preview enlarged instead of thinner and darker.
                    val lineWidth = lineWidth(mandalaSize, layer.style)
                    val layerBrightness = if (lineWidth > 1f) LINE_REFERENCE_SIZE / 1600f else brightnessScale
                    PixelBuffer.drawInStrips(vw, vh, reject, lineWidth, onStrip = { progress.layer(n, RenderProgress.DRAWN * it) }) { strip ->
                        for (p in points) p.drawInto(strip)
                        drawTasks(tasks, strip, palette, layer.colorOffset, par)
                        lines.drawInto(strip, par)
                        strip.scale(layerBrightness)
                    }
                }
                checkpoint()
                img = fx.glow(img, layer.glowIntensity, effectWidth)
                progress.layer(n, RenderProgress.GLOWN)
                checkpoint()
                if (layer.wash > 0) img = fx.wash(img, layer.wash, effectWidth)
                progress.layer(n, RenderProgress.WASHED)
                checkpoint()
                if (layer.abstractLevel > 0.01) {
                    if (!rotated && place.size > bufW) {
                        // Zoomed in, the margin for glow and wash is wide; painting needs less of it.
                        val reach = fx.paintedReach(layer.abstractLevel, effectWidth)
                        val keep = intArrayOf(max(view[0], -reach), max(view[1], -reach), min(view[2], bufW + reach), min(view[3], bufH + reach))
                        if (!keep.contentEquals(view)) {
                            img = Filters.crop(img, keep[0] - view[0], keep[1] - view[1], keep[2] - keep[0], keep[3] - keep[1])
                            offsetX += keep[0] - view[0]
                            offsetY += keep[1] - view[1]
                            view = keep
                        }
                    }
                    img = fx.painted(img, layer.abstractLevel, effectWidth, offsetX, offsetY)
                }
                progress.layer(n, RenderProgress.PAINTED)
                checkpoint()
                img = fx.colourGrade(img, layer.saturation, layer.brightness)
                if (rotated) img = fx.rotate(img, layer.rotation * Math.PI * 2, cx, cy)
                if (layer.opacity < 0.999) img = fx.opacity(img, layer.opacity)
                // Blending reads the layer image without changing it, so it can be kept.
                if (cacheKey != null) cache.put(cacheKey, LayerCache.Entry(img, view[0], view[1]))
                composite = fx.composite(composite, img, op, view[0], view[1])
            }
            progress.layer(layerNo, 0.0)
            cache?.finish()

            // ── Hand-made layers ──
            val hand = HandLayers(par, fx)
            if (graffiti) {
                checkpoint()
                composite = hand.graffiti(composite, params.graffitiLayer, place)
                progress.above(RenderProgress.HAND_LAYER)
            }
            if (drawing) {
                checkpoint()
                composite = hand.drawing(composite, params.drawingLayer, place)
                progress.above(RenderProgress.HAND_LAYER)
            }

            // Solid background goes in after all layers (lighten) so layer blend modes don't tint it.
            if (solidBackground != null) {
                composite = fx.compositeSolid(composite, solidBackground[0], solidBackground[1], solidBackground[2], BlendOp.LIGHTEN)
            }

            // Emblem above the mandala and background, below the text.
            if (params.emblemLayer.isEnabled) {
                checkpoint()
                composite = EmblemLayer(par, fx).apply(composite, params.emblemLayer, palettes, place)
                progress.above(RenderProgress.EMBLEM)
            }

            // Finish the scene before text, so its letters and own effects remain independent.
            if (params.effectsLayer.isEnabled) {
                checkpoint()
                composite = EffectsLayer(par, fx).apply(composite, params.effectsLayer)
                progress.above(RenderProgress.EFFECTS)
            }

            if (text) {
                val textRenderer = options.textRenderer
                if (textRenderer == null) {
                    warnings += "Text needs a text renderer; skipped."
                } else {
                    checkpoint()
                    val frame = if (options.textFollowsMandala) {
                        val half = mandalaSize / 2.0
                        TextFrame((place.centreX - half).toFloat(), (place.centreY - half).toFloat(), mandalaSize, mandalaSize, mandalaSize)
                    } else {
                        TextFrame(0f, 0f, bufW, bufH, min(bufW, bufH))
                    }
                    composite = TextLayer(par, textRenderer).apply(composite, params.textLayer, frame)
                    progress.above(RenderProgress.TEXT)
                }
            }

            checkpoint()
            val result = when {
                factor != 1 -> fx.downscale(composite, outW, outH)
                composite.encoding == Encoding.SRGB -> composite
                else -> WorkingSpace.convertToSrgb(composite)
            }
            progress.done()
            return RenderResult(options.finish.apply(result, options.threads), warnings, (System.nanoTime() - start) / 1_000_000)
        }
    }

    /**
     * Turns the steps of a render into a share of the work done. Each step has a weight, counted in
     * mandala layers, measured roughly on exports of a light and of the worst case; the share only rises.
     */
    private class RenderProgress(
        private val report: (Double) -> Unit,
        private val background: Double,
        private val layers: Int,
        above: Double,
    ) {
        private val total = background + layers + above + FINISH
        private var aboveDone = 0.0
        private var done = 0.0

        fun background() = at(background)

        /** Layer `n` (counting only drawn ones) is `part` done, from 0 to 1. */
        fun layer(n: Int, part: Double) = at(background + n + part)

        /** One of the steps above the layers, of this weight, is done. */
        fun above(weight: Double) {
            aboveDone += weight
            at(background + layers + aboveDone)
        }

        fun done() = to(1.0)

        private fun at(weight: Double) = to(weight / total)

        private fun to(share: Double) {
            if (share <= done) return
            done = min(share, 1.0)
            report(done)
        }

        companion object {
            const val BACKGROUND = 1.0
            const val HAND_LAYER = 0.5
            const val EMBLEM = 1.0
            const val TEXT = 0.3
            const val EFFECTS = 2.0
            const val FINISH = 0.2

            // Share of a layer done after each of its steps.
            const val DRAWN = 0.4
            const val GLOWN = 0.6
            const val WASHED = 0.8
            const val PAINTED = 0.95
        }
    }

    /**
     * How far a style draws from the layer centre, in layer radii: measured with every style at
     * low and high complexity, density and ripple (`StyleReachTest`), plus about 5 %. Deep Nebula
     * fills whatever it gets and Mixed draws zones of other styles; both get the cap, which keeps
     * the layer buffer and its glow within the memory budget.
     */
    internal fun styleReach(style: MandalaStyle): Double = when (style) {
        MandalaStyle.SPIROGRAPH -> 1.0
        MandalaStyle.ROSE_CURVES -> 1.1
        MandalaStyle.FLORAL -> 0.9
        MandalaStyle.BUTTERFLY -> 1.95
        MandalaStyle.EPITROCHOID -> 1.3
        MandalaStyle.LISSAJOUS -> 1.45
        MandalaStyle.HYPOCYCLOID -> 1.4
        MandalaStyle.PHYLLOTAXIS -> 1.2
        MandalaStyle.FRACTAL -> 1.2
        MandalaStyle.SUPERFORMULA -> 1.1
        MandalaStyle.WAVE_INTERFERENCE -> 1.55
        MandalaStyle.GEOMETRIC -> 1.35
        MandalaStyle.SACRED_GEOMETRY -> 1.15
        MandalaStyle.SUNBURST -> 1.05
        MandalaStyle.STAR_BURST -> 1.15
        MandalaStyle.STRING_ART -> 1.05
        MandalaStyle.SPIDER_WEB -> 1.1
        MandalaStyle.WEAVE -> 1.1
        MandalaStyle.RADIAL_MESH -> 1.2
        MandalaStyle.MOIRE -> 1.45
        MandalaStyle.FLOW_FIELD -> 1.15
        MandalaStyle.TENDRIL -> 2.2
        MandalaStyle.VORONOI -> 1.15
        MandalaStyle.STRANGE_ATTRACTOR -> 1.35
        MandalaStyle.CONSTELLATION_WEB -> 0.95
        MandalaStyle.INTERFERENCE_BLOOM -> 1.15
        MandalaStyle.HYPERBOLOID -> 1.45
        MandalaStyle.TORUS -> 1.3
        MandalaStyle.NAUTILUS -> 1.2
        MandalaStyle.SPHERE_GRID -> 0.9
        MandalaStyle.TORUS_KNOT -> 0.9
        MandalaStyle.TESSERACT -> 2.2
        MandalaStyle.UNIVERSE -> 1.2
        MandalaStyle.SYMBOLS -> 1.2
        MandalaStyle.DEEP_NEBULA, MandalaStyle.MIXED -> STYLE_REACH_CAP
    }

    /** The largest style reach drawn, in layer radii: beyond this even Deep Nebula ends. */
    internal const val STYLE_REACH_CAP = 2.5

    /**
     * The zoom (ADR-0010) at which the mandala fills a canvas of `aspect` (height over width) with its
     * own lines, between 1 (it fills the canvas already, as Deep Nebula does) and fitting the longer side.
     * Measured, since how full a style looks differs from how far it reaches: the mandala's layers alone
     * are drawn small on black, and the radius up to which at least 60 % of every circle is lit sets
     * the zoom that brings that radius to the canvas corners. Takes about a tenth of a second.
     */
    fun fillZoom(params: MandalaParameters, aspect: Double, threads: Int = Runtime.getRuntime().availableProcessors()): Double {
        val longer = max(aspect, 1 / aspect)
        val corner = 0.5 * sqrt(1 + longer * longer)
        val layersOnly = params.copy(
            baseLayer = params.baseLayer.copy(isEnabled = false),
            effectsLayer = params.effectsLayer.copy(isEnabled = false),
            emblemLayer = params.emblemLayer.copy(isEnabled = false),
            textLayer = params.textLayer.copy(isEnabled = false),
        )
        val side = FILL_PROBE_SIZE
        val img = render(
            layersOnly,
            RenderOptions(outputSize = side, supersample = false, framing = Framing(zoom = FILL_PROBE_ZOOM), threads = threads, glowReferenceWidth = FILL_PROBE_GLOW),
        ).image
        var filled = 0
        for (r in 1 until side / 2) {
            var lit = 0
            for (a in 0 until FILL_ANGLES) {
                val angle = a * 2 * Math.PI / FILL_ANGLES
                val x = (side / 2 + r * kotlin.math.cos(angle)).toInt()
                val y = (side / 2 + r * kotlin.math.sin(angle)).toInt()
                val o = (y * side + x) * 3
                if (0.3 * img.get(o) + 0.59 * img.get(o + 1) + 0.11 * img.get(o + 2) > FILL_LIT) lit++
            }
            if (lit >= FILL_ANGLES * FILL_SHARE) filled = r
        }
        if (filled == 0) return longer
        return (corner / (filled / (side * FILL_PROBE_ZOOM))).coerceIn(1.0, longer)
    }

    // The fill probe: 256 px with the mandala at 0.35 of it, so a reach of 1.4 mandala sizes still
    // shows; glow blurred at a small reference width, as the fast preview does.
    private const val FILL_PROBE_SIZE = 256
    private const val FILL_PROBE_ZOOM = 0.35
    private const val FILL_PROBE_GLOW = 400
    private const val FILL_ANGLES = 360
    /** Brightness (0 to 255) from which a pixel counts as lit, and the share of a circle that must be. */
    private const val FILL_LIT = 20.0
    private const val FILL_SHARE = 0.6

    /** Radius of a layer at scale 1, in mandala sizes (as on the Mac). */
    private const val BASE_RADIUS = 0.72

    /** Buffer size up to which lines are hairlines: the preview's (512 px drawn at twice the size), as on the Mac. */
    private const val LINE_REFERENCE_SIZE = 1024

    /**
     * Line width for a layer drawn for a mandala square of `size` pixels: proportionally wider above
     * the reference, the same rule as the Mac's `lineWidth(forSize:style:)`. Universe and Symbols
     * already grow their elements with the size and keep hairlines.
     */
    internal fun lineWidth(size: Int, style: MandalaStyle): Float =
        if (style == MandalaStyle.UNIVERSE || style == MandalaStyle.SYMBOLS) 1f else max(1f, size.toFloat() / LINE_REFERENCE_SIZE)

    /** Distance from (cx, cy) to the farthest corner of the canvas grown by `margin` on every side. */
    private fun farthestCorner(cx: Double, cy: Double, width: Int, height: Int, margin: Int): Double {
        val dx = max(abs(cx + margin), abs(width + margin - cx))
        val dy = max(abs(cy + margin), abs(height + margin - cy))
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * How far (in buffer pixels) the layer effects carry light: three sigma of the two stronger glow
     * passes and of the third wash pass, plus the painted displacement and blur. Reference pixels
     * are converted with the same 1600 px reference width the effects use. Always even.
     */
    private fun effectMargin(layer: StyleLayer, effectWidth: Int): Int {
        val refToPx = effectWidth / 1600.0
        val reachRef = 3 * 8.0 * 3.0 * layer.glowIntensity +
            3 * 28.0 * layer.wash +
            30.0 * layer.abstractLevel + 3 * 8.0 * layer.abstractLevel * 1.5 + 4
        val m = ceil(reachRef * refToPx).toInt()
        return m + (m and 1)
    }

    /**
     * Curve styles fill `tasks`; styles the macOS renderer draws directly fill `lines`; point styles
     * (Strange Attractor) fill `points`. Mixed calls this again for three zones.
     */
    internal fun collectStyle(
        style: MandalaStyle, tasks: MutableList<CurveTask>, lines: LineList, points: MutableList<AttractorPoints>, cx: Float, cy: Float, radius: Double,
        ctx: StyleContext, palette: ColorPalette, rng: SeededRng, layerCount: Int, symmetry: Int, ripple: Float,
        colorOffset: Double, weightMul: Float = 1f,
    ) {
        val w = weightMul
        when (style) {
            MandalaStyle.SPIROGRAPH -> CurveStyles.spirograph(tasks, cx, cy, radius, ctx, rng, layerCount, symmetry, ripple, w)
            MandalaStyle.ROSE_CURVES -> CurveStyles.roseCurves(tasks, cx, cy, radius, ctx, rng, layerCount, symmetry, ripple, w)
            MandalaStyle.HYPOCYCLOID -> CurveStyles.hypocycloid(tasks, cx, cy, radius, ctx, rng, layerCount, symmetry, ripple, w)
            MandalaStyle.PHYLLOTAXIS -> CurveStyles.phyllotaxis(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, w)
            MandalaStyle.WAVE_INTERFERENCE -> CurveStyles.waveInterference(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, w)
            MandalaStyle.FRACTAL -> CurveStyles.fractal(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, w)
            MandalaStyle.EPITROCHOID -> ParametricStyles.epitrochoid(tasks, cx, cy, radius, ctx, rng, layerCount, symmetry, ripple, w)
            MandalaStyle.FLORAL -> ParametricStyles.floral(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, w)
            MandalaStyle.LISSAJOUS -> ParametricStyles.lissajous(tasks, cx, cy, radius, ctx, rng, layerCount, symmetry, ripple, w)
            MandalaStyle.BUTTERFLY -> ParametricStyles.butterfly(tasks, cx, cy, radius, ctx, rng, layerCount, symmetry, ripple, w)
            MandalaStyle.GEOMETRIC -> ParametricStyles.geometric(tasks, cx, cy, radius, ctx, rng, layerCount, symmetry, ripple, w)
            MandalaStyle.SUPERFORMULA -> ParametricStyles.superformula(tasks, cx, cy, radius, ctx, rng, layerCount, symmetry, ripple, w)
            MandalaStyle.SPIDER_WEB -> PatternStyles.spiderWeb(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, w)
            MandalaStyle.WEAVE -> PatternStyles.weave(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, w)
            MandalaStyle.SACRED_GEOMETRY -> PatternStyles.sacredGeometry(tasks, cx, cy, radius, ctx, rng, symmetry, w)
            MandalaStyle.RADIAL_MESH -> PatternStyles.radialMesh(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, w)
            MandalaStyle.FLOW_FIELD -> PatternStyles.flowField(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, w)
            MandalaStyle.TENDRIL -> PatternStyles.tendril(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, w)
            MandalaStyle.MOIRE -> PatternStyles.moire(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, w)
            MandalaStyle.VORONOI -> PatternStyles.voronoi(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, w)
            MandalaStyle.TORUS_KNOT -> ThreeDStyles.torusKnot(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, w)
            MandalaStyle.SPHERE_GRID -> ThreeDStyles.sphereGrid(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, w)
            MandalaStyle.TESSERACT -> ThreeDStyles.tesseract(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, w)
            MandalaStyle.STRING_ART -> SolidStyles.stringArt(lines, cx, cy, radius, ctx, palette, rng, layerCount, symmetry)
            MandalaStyle.SUNBURST -> SolidStyles.sunburst(lines, cx, cy, radius, ctx, palette, rng, layerCount, symmetry)
            MandalaStyle.TORUS -> SolidStyles.torus(lines, cx, cy, radius, ctx, palette, rng, layerCount, symmetry, colorOffset)
            MandalaStyle.NAUTILUS -> SolidStyles.nautilus(lines, cx, cy, radius, ctx, palette, rng, layerCount, symmetry, colorOffset)
            MandalaStyle.CONSTELLATION_WEB -> SolidStyles.constellationWeb(lines, cx, cy, radius, ctx, palette, rng, colorOffset, symmetry)
            MandalaStyle.STRANGE_ATTRACTOR -> points += SolidStyles.strangeAttractor(cx, cy, radius, ctx, palette, rng, layerCount, symmetry, colorOffset)
            MandalaStyle.STAR_BURST -> DirectStyles.starBurst(lines, cx, cy, radius, ctx, palette, rng)
            MandalaStyle.HYPERBOLOID -> DirectStyles.hyperboloid(lines, cx, cy, radius, ctx, palette, rng, layerCount, symmetry, colorOffset)
            MandalaStyle.UNIVERSE -> DirectStyles.universe(lines, cx, cy, radius, ctx, palette, rng, colorOffset, symmetry)
            MandalaStyle.SYMBOLS -> SymbolStyles.symbols(lines, cx, cy, radius, ctx, palette, rng, colorOffset, symmetry)
            MandalaStyle.DEEP_NEBULA -> CosmicStyles.deepNebula(lines, cx, cy, radius * 3.0, ctx, palette, rng, colorOffset, symmetry)
            MandalaStyle.INTERFERENCE_BLOOM -> CosmicStyles.interferenceBloom(lines, cx, cy, radius, ctx, palette, rng, colorOffset, symmetry)
            MandalaStyle.MIXED -> {
                // Three zones, each with a style picked by its own generator from the global seed.
                val zoneRng = SeededRng(ctx.seed + 0xbeef1234uL)
                val sub = max(2, layerCount / 3)
                for (zRadius in doubleArrayOf(1.0, 0.72, 0.45)) {
                    val pick = mixedZoneStyles[(zoneRng.nextDouble() * mixedZoneStyles.size).toInt() % mixedZoneStyles.size]
                    val zoneWeight = weightMul * (0.7 + zoneRng.nextDouble() * 0.6).toFloat()
                    collectStyle(
                        if (pick == MandalaStyle.MIXED) MandalaStyle.SPIROGRAPH else pick,
                        tasks, lines, points, cx, cy, radius * zRadius, ctx, palette, rng, sub, symmetry, ripple,
                        colorOffset = 0.0, weightMul = zoneWeight,
                    )
                }
            }
        }
    }

    /** The styles Mixed chooses from, in the macOS order (the index comes from the random generator). */
    private val mixedZoneStyles = listOf(
        MandalaStyle.SPIROGRAPH, MandalaStyle.ROSE_CURVES, MandalaStyle.EPITROCHOID, MandalaStyle.LISSAJOUS,
        MandalaStyle.BUTTERFLY, MandalaStyle.FLORAL, MandalaStyle.STRING_ART, MandalaStyle.SUNBURST, MandalaStyle.GEOMETRIC,
        MandalaStyle.FRACTAL, MandalaStyle.PHYLLOTAXIS, MandalaStyle.HYPOCYCLOID, MandalaStyle.WAVE_INTERFERENCE,
        MandalaStyle.SPIDER_WEB, MandalaStyle.WEAVE, MandalaStyle.SACRED_GEOMETRY, MandalaStyle.RADIAL_MESH,
        MandalaStyle.FLOW_FIELD, MandalaStyle.TENDRIL, MandalaStyle.MOIRE, MandalaStyle.VORONOI, MandalaStyle.TORUS_KNOT,
        MandalaStyle.SPHERE_GRID, MandalaStyle.TESSERACT, MandalaStyle.STAR_BURST, MandalaStyle.UNIVERSE, MandalaStyle.SYMBOLS,
        MandalaStyle.STRANGE_ATTRACTOR, MandalaStyle.CONSTELLATION_WEB, MandalaStyle.DEEP_NEBULA,
        MandalaStyle.INTERFERENCE_BLOOM, MandalaStyle.SUPERFORMULA, MandalaStyle.HYPERBOLOID, MandalaStyle.TORUS,
        MandalaStyle.NAUTILUS,
    )

    /**
     * Draws tasks in parallel. The buffer is split into horizontal bands; each thread draws every
     * task clipped to its band, so no extra buffers are needed and the result is thread-count independent.
     */
    private fun drawTasks(
        tasks: List<CurveTask>, buffer: PixelBuffer, palette: ColorPalette, colorOffset: Double, par: Parallel,
    ) {
        if (tasks.isEmpty()) return
        val yMin = FloatArray(tasks.size)
        val yMax = FloatArray(tasks.size)
        for ((i, t) in tasks.withIndex()) {
            var lo = Float.POSITIVE_INFINITY
            var hi = Float.NEGATIVE_INFINITY
            for (y in t.ys) { if (y < lo) lo = y; if (y > hi) hi = y }
            yMin[i] = lo - t.thickness
            yMax[i] = hi + t.thickness
        }
        // More bands than threads keeps threads busy when the centre is denser than the edges.
        val rows = buffer.rowsUntil - buffer.rowsFrom
        if (rows <= 0) return
        val bands = max(1, min(rows, par.threads * 4))
        val bandHeight = (rows + bands - 1) / bands
        par.forEachChunk(bands) { b ->
            val view = buffer.band(buffer.rowsFrom + b * bandHeight, buffer.rowsFrom + (b + 1) * bandHeight)
            val col = FloatArray(3)
            for (i in tasks.indices) {
                if (view.touchesRows(yMin[i], yMax[i])) drawCurve(view, tasks[i], palette, colorOffset, col)
            }
        }
    }

    private fun drawCurve(buffer: PixelBuffer, task: CurveTask, palette: ColorPalette, colorOffset: Double, col: FloatArray) {
        val xs = task.xs
        val ys = task.ys
        val count = xs.size
        if (count <= 1) return
        val tOffset = task.tOffset + colorOffset
        for (i in 0 until count - 1) {
            val ya = ys[i]
            val yb = ys[i + 1]
            if (!buffer.touchesRows(min(ya, yb) - task.thickness, max(ya, yb) + task.thickness)) continue
            val t = (tOffset + i.toDouble() / count * task.drift) % 1.0
            palette.colorAt(if (t < 0) t + 1.0 else t, col)
            if (task.thickness > 1) {
                buffer.addThickLine(xs[i], ys[i], xs[i + 1], ys[i + 1], col[0], col[1], col[2], task.weight, task.thickness)
            } else {
                buffer.addLine(xs[i], ys[i], xs[i + 1], ys[i + 1], col[0], col[1], col[2], task.weight)
            }
        }
    }

    /** Exact solid colour (no tone map) as 8-bit P3 values, like the macOS makeSolidCGImage. */
    private fun solidColor(settings: BaseLayerSettings): FloatArray {
        val c = hsbToRgb(settings.hue, settings.saturation, settings.brightness)
        val o = settings.opacity.toFloat()
        fun q(v: Double) = min(255, max(0, (v.toFloat() * o * 255 + 0.5f).toInt())) / 255f
        return floatArrayOf(q(c.r), q(c.g), q(c.b))
    }
}
