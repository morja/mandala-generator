package com.mathis.mandala.core

import com.mathis.mandala.core.styles.CurveStyles
import com.mathis.mandala.core.styles.CurveTask
import com.mathis.mandala.core.styles.DirectStyles
import com.mathis.mandala.core.styles.LineList
import com.mathis.mandala.core.styles.StyleContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class RenderOptions(
    /** Overrides the size from the settings (the preview uses a smaller one). */
    val outputSize: Int? = null,
    /** Threads for drawing and filters. The image does not depend on it. */
    val threads: Int = Runtime.getRuntime().availableProcessors(),
    /** Polled between steps; when it returns true the render stops with [RenderCancelledException]. */
    val isCancelled: () -> Boolean = { false },
)

class RenderCancelledException : RuntimeException("Render cancelled")

data class RenderResult(val image: Image8, val warnings: List<String>, val millis: Long)

/** Port of the macOS MandalaRenderer.render pipeline (proof-of-concept subset, see warnings). */
object MandalaRenderer {

    val supportedStyles = setOf(
        MandalaStyle.SPIROGRAPH,
        MandalaStyle.ROSE_CURVES,
        MandalaStyle.HYPOCYCLOID,
        MandalaStyle.PHYLLOTAXIS,
        MandalaStyle.WAVE_INTERFERENCE,
        MandalaStyle.FRACTAL,
        MandalaStyle.HYPERBOLOID,
        MandalaStyle.STAR_BURST,
        MandalaStyle.UNIVERSE,
    )

    /** Whether the renderer draws this background type (a solid colour or one computed per pixel). */
    fun supportsBackground(type: BaseLayerType): Boolean = type == BaseLayerType.COLOR || Background.supports(type)

    fun render(
        params: MandalaParameters,
        options: RenderOptions = RenderOptions(),
        palettes: List<ColorPalette> = BuiltInPalettes.all,
    ): RenderResult {
        val start = System.nanoTime()
        val warnings = mutableListOf<String>()
        val resolvedSize = options.outputSize ?: params.resolvedOutputSize
        val bufferSize = resolvedSize * 2
        val baseRadius = bufferSize * 0.72
        // Same lines spread over a larger canvas give a lower mean; this linear factor cancels it.
        val brightnessScale = bufferSize / 1600f

        Parallel(max(1, options.threads)).use { par ->
            val fx = LayerEffects(par)

            // ── Background ──
            val base = params.baseLayer
            if (base.isEnabled && base.type != BaseLayerType.COLOR && !Background.supports(base.type)) {
                warnings += "Background \"${base.type.name.lowercase()}\" is not available yet; drawn black."
            }
            var composite = if (base.isEnabled && Background.supports(base.type)) {
                Background.render(base, bufferSize, par)
            } else {
                Image8(bufferSize, bufferSize, Encoding.SRGB)
            }
            val solidBackground = if (base.isEnabled && base.type == BaseLayerType.COLOR) solidColor(base) else null

            // ── Layers ──
            fun checkpoint() { if (options.isCancelled()) throw RenderCancelledException() }

            for ((li, layer) in params.layers.withIndex()) {
                checkpoint()
                if (!layer.isEnabled) continue
                if (layer.style !in supportedStyles) {
                    warnings += "Layer ${li + 1} (${layer.style.displayName}) is not available yet; skipped."
                    continue
                }
                val palette = palettes[layer.paletteIndex.coerceIn(0, palettes.size - 1)]
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
                val effectWidth = max(bufferSize, if (raw % 2 == 0) raw else raw + 1)
                val rotated = abs(layer.rotation) > 0.001
                val size = if (rotated) effectWidth else min(effectWidth, bufferSize + 2 * effectMargin(layer, effectWidth))
                val offset = (effectWidth - size) / 2
                val c = size * 0.5f

                val ctx = StyleContext(layer.complexity, layer.density, layer.colorDrift, params.seed)
                val tasks = ArrayList<CurveTask>()
                val lines = LineList()
                collectStyle(
                    layer.style, tasks, lines, c, c, layerRadius, ctx, palette, rng,
                    layerCount, symmetry, layer.ripple.toFloat(), layer.colorOffset,
                )

                var img = run {
                    val buffer = PixelBuffer(size, size)
                    drawTasks(tasks, buffer, palette, layer.colorOffset, par)
                    lines.drawInto(buffer, par)
                    tasks.clear()
                    buffer.scale(brightnessScale)
                    buffer.toImage()
                }
                checkpoint()
                img = fx.glow(img, layer.glowIntensity, effectWidth)
                checkpoint()
                if (layer.wash > 0) img = fx.wash(img, layer.wash, effectWidth)
                checkpoint()
                if (layer.abstractLevel > 0.01) img = fx.painted(img, layer.abstractLevel, effectWidth, offset)
                checkpoint()
                img = fx.colourGrade(img, layer.saturation, layer.brightness)
                if (rotated) img = fx.rotate(img, layer.rotation * Math.PI * 2)
                if (size > bufferSize) img = Filters.cropCenter(img, bufferSize)
                if (layer.opacity < 0.999) img = fx.opacity(img, layer.opacity)

                val op = when (layer.blendMode) {
                    LayerBlendMode.SCREEN -> BlendOp.SCREEN
                    LayerBlendMode.ADD -> BlendOp.ADD
                    LayerBlendMode.NORMAL -> BlendOp.LIGHTEN
                    LayerBlendMode.MULTIPLY -> BlendOp.MULTIPLY
                }
                composite = fx.composite(composite, img, op)
            }

            if (params.graffitiLayer.isEnabledLayer()) warnings += "Graffiti layer is not available yet; skipped."
            if (params.drawingLayer.isEnabledLayer()) warnings += "Drawing layer is not available yet; skipped."

            // Solid background goes in after all layers (lighten) so layer blend modes don't tint it.
            if (solidBackground != null) {
                composite = fx.compositeSolid(composite, solidBackground[0], solidBackground[1], solidBackground[2], BlendOp.LIGHTEN)
            }

            if (params.textLayer.isEnabledLayer()) warnings += "Text layer is not available yet; skipped."
            if (params.effectsLayer.isEnabled) warnings += "Effects layer is not available yet; skipped."

            checkpoint()
            val result = fx.downscale(composite, resolvedSize)
            return RenderResult(result, warnings, (System.nanoTime() - start) / 1_000_000)
        }
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

    /** Curve styles fill `tasks`; styles the macOS renderer draws directly fill `lines`. */
    private fun collectStyle(
        style: MandalaStyle, tasks: MutableList<CurveTask>, lines: LineList, cx: Float, cy: Float, radius: Double,
        ctx: StyleContext, palette: ColorPalette, rng: SeededRng, layerCount: Int, symmetry: Int, ripple: Float,
        colorOffset: Double,
    ) {
        when (style) {
            MandalaStyle.SPIROGRAPH -> CurveStyles.spirograph(tasks, cx, cy, radius, ctx, rng, layerCount, symmetry, ripple, 1f)
            MandalaStyle.ROSE_CURVES -> CurveStyles.roseCurves(tasks, cx, cy, radius, ctx, rng, layerCount, symmetry, ripple, 1f)
            MandalaStyle.HYPOCYCLOID -> CurveStyles.hypocycloid(tasks, cx, cy, radius, ctx, rng, layerCount, symmetry, ripple, 1f)
            MandalaStyle.PHYLLOTAXIS -> CurveStyles.phyllotaxis(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, 1f)
            MandalaStyle.WAVE_INTERFERENCE -> CurveStyles.waveInterference(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, 1f)
            MandalaStyle.FRACTAL -> CurveStyles.fractal(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, 1f)
            MandalaStyle.STAR_BURST -> DirectStyles.starBurst(lines, cx, cy, radius, ctx, palette, rng)
            MandalaStyle.HYPERBOLOID -> DirectStyles.hyperboloid(lines, cx, cy, radius, ctx, palette, rng, layerCount, symmetry, colorOffset)
            MandalaStyle.UNIVERSE -> DirectStyles.universe(lines, cx, cy, radius, ctx, palette, rng, colorOffset, symmetry)
            else -> Unit
        }
    }

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
        val bands = max(1, min(buffer.height, par.threads * 4))
        val bandHeight = (buffer.height + bands - 1) / bands
        par.forEachChunk(bands) { b ->
            val view = buffer.band(b * bandHeight, (b + 1) * bandHeight)
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
