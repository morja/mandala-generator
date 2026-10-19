package com.mathis.mandala.core

import com.mathis.mandala.core.styles.CurveStyles
import com.mathis.mandala.core.styles.CurveTask
import com.mathis.mandala.core.styles.StyleContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class RenderOptions(
    /** Overrides the size from the settings (the preview uses a smaller one). */
    val outputSize: Int? = null,
    /** Threads for drawing and filters. Each drawing thread holds its own full-size buffer. */
    val threads: Int = Runtime.getRuntime().availableProcessors(),
    /**
     * Reproduces a quirk of the macOS renderer: it splits curves into `n` equal chunks and drops
     * the remainder, so its output depends on the Mac's core count. Set to that core count to
     * compare against macOS output; leave null to draw every curve.
     */
    val macCoreCountForChunking: Int? = null,
)

data class RenderResult(val image: FloatImage, val warnings: List<String>, val millis: Long)

/** Port of the macOS MandalaRenderer.render pipeline (proof-of-concept subset, see warnings). */
object MandalaRenderer {

    val supportedStyles = setOf(
        MandalaStyle.SPIROGRAPH,
        MandalaStyle.ROSE_CURVES,
        MandalaStyle.HYPOCYCLOID,
        MandalaStyle.PHYLLOTAXIS,
        MandalaStyle.WAVE_INTERFERENCE,
    )

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
            if (base.isEnabled && base.type != BaseLayerType.COLOR) {
                warnings += "Background type ${base.type.name.lowercase()} is not ported yet; drawn black."
            }
            var composite = FloatImage(bufferSize, bufferSize, encoding = Encoding.DISPLAY_P3)
            val solidBackground = if (base.isEnabled && base.type == BaseLayerType.COLOR) solidImage(base, bufferSize) else null

            // ── Layers ──
            for ((li, layer) in params.layers.withIndex()) {
                if (!layer.isEnabled) continue
                if (layer.style !in supportedStyles) {
                    warnings += "Layer ${li + 1}: style ${layer.style.name.lowercase()} is not ported yet; skipped."
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

                // Render into a buffer large enough for the full radius, so rotation shows no cut edges.
                val raw = ceil(layerRadius * 2.0).toInt() + 8
                val lBufSize = max(bufferSize, if (raw % 2 == 0) raw else raw + 1)
                val c = lBufSize * 0.5f

                val ctx = StyleContext(layer.complexity, layer.density, layer.colorDrift, params.seed)
                val tasks = ArrayList<CurveTask>()
                collectTasks(layer.style, tasks, c, c, layerRadius, ctx, rng, layerCount, symmetry, layer.ripple.toFloat())

                val buffer = PixelBuffer(lBufSize, lBufSize)
                drawTasks(tasks, buffer, palette, layer.colorOffset, par, options.macCoreCountForChunking)
                buffer.scale(brightnessScale)

                var img = buffer.toImage()
                img = fx.glow(img, layer.glowIntensity)
                if (layer.wash > 0) img = fx.wash(img, layer.wash)
                if (layer.abstractLevel > 0.01) img = fx.painted(img, layer.abstractLevel)
                img = fx.colourGrade(img, layer.saturation, layer.brightness)
                if (abs(layer.rotation) > 0.001) img = fx.rotate(img, layer.rotation * Math.PI * 2)
                if (lBufSize > bufferSize) img = Filters.cropCenter(img, bufferSize)
                if (layer.opacity < 0.999) img = fx.opacity(img, layer.opacity)

                val op = when (layer.blendMode) {
                    LayerBlendMode.SCREEN -> BlendOp.SCREEN
                    LayerBlendMode.ADD -> BlendOp.ADD
                    LayerBlendMode.NORMAL -> BlendOp.LIGHTEN
                    LayerBlendMode.MULTIPLY -> BlendOp.MULTIPLY
                }
                composite = fx.composite(composite, img, op)
            }

            if (params.graffitiLayer.isEnabledLayer()) warnings += "Graffiti layer is not ported yet; skipped."
            if (params.drawingLayer.isEnabledLayer()) warnings += "Drawing layer is not ported yet; skipped."

            // Solid background goes in after all layers (lighten) so layer blend modes don't tint it.
            if (solidBackground != null) composite = fx.composite(composite, solidBackground, BlendOp.LIGHTEN)

            if (params.textLayer.isEnabledLayer()) warnings += "Text layer is not ported yet; skipped."
            if (params.effectsLayer.isEnabled) warnings += "Effects layer is not ported yet; skipped."

            val result = fx.downscale(composite, resolvedSize)
            return RenderResult(result, warnings, (System.nanoTime() - start) / 1_000_000)
        }
    }

    private fun collectTasks(
        style: MandalaStyle, tasks: MutableList<CurveTask>, cx: Float, cy: Float, radius: Double,
        ctx: StyleContext, rng: SeededRng, layerCount: Int, symmetry: Int, ripple: Float,
    ) {
        when (style) {
            MandalaStyle.SPIROGRAPH -> CurveStyles.spirograph(tasks, cx, cy, radius, ctx, rng, layerCount, symmetry, ripple, 1f)
            MandalaStyle.ROSE_CURVES -> CurveStyles.roseCurves(tasks, cx, cy, radius, ctx, rng, layerCount, symmetry, ripple, 1f)
            MandalaStyle.HYPOCYCLOID -> CurveStyles.hypocycloid(tasks, cx, cy, radius, ctx, rng, layerCount, symmetry, ripple, 1f)
            MandalaStyle.PHYLLOTAXIS -> CurveStyles.phyllotaxis(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, 1f)
            MandalaStyle.WAVE_INTERFERENCE -> CurveStyles.waveInterference(tasks, cx, cy, radius, ctx, rng, symmetry, ripple, 1f)
            else -> Unit
        }
    }

    /** Draws tasks in parallel, one sub-buffer per thread, then merges them in order. */
    private fun drawTasks(
        allTasks: List<CurveTask>, buffer: PixelBuffer, palette: ColorPalette, colorOffset: Double,
        par: Parallel, macCores: Int?,
    ) {
        if (allTasks.isEmpty()) return
        val tasks = if (macCores != null) {
            val n = max(1, min(allTasks.size, macCores))
            val chunk = max(1, allTasks.size / n)
            allTasks.take(min(allTasks.size, n * chunk))
        } else {
            allTasks
        }
        val n = max(1, min(tasks.size, par.threads))
        val step = (tasks.size + n - 1) / n
        val subs = Array(n) { PixelBuffer(buffer.width, buffer.height) }
        par.forEachChunk(n) { tid ->
            val from = tid * step
            val until = min(tasks.size, from + step)
            val col = FloatArray(3)
            for (i in from until until) drawCurve(subs[tid], tasks[i], palette, colorOffset, col)
        }
        for (sub in subs) buffer.mergeAdding(sub)
    }

    private fun drawCurve(buffer: PixelBuffer, task: CurveTask, palette: ColorPalette, colorOffset: Double, col: FloatArray) {
        val xs = task.xs
        val ys = task.ys
        val count = xs.size
        if (count <= 1) return
        val tOffset = task.tOffset + colorOffset
        for (i in 0 until count - 1) {
            val t = (tOffset + i.toDouble() / count * task.drift) % 1.0
            palette.colorAt(if (t < 0) t + 1.0 else t, col)
            if (task.thickness > 1) {
                buffer.addThickLine(xs[i], ys[i], xs[i + 1], ys[i + 1], col[0], col[1], col[2], task.weight, task.thickness)
            } else {
                buffer.addLine(xs[i], ys[i], xs[i + 1], ys[i + 1], col[0], col[1], col[2], task.weight)
            }
        }
    }

    /** Exact solid colour (no tone map), 8-bit, tagged P3 like the macOS makeSolidCGImage. */
    private fun solidImage(settings: BaseLayerSettings, size: Int): FloatImage {
        val c = hsbToRgb(settings.hue, settings.saturation, settings.brightness)
        val o = settings.opacity
        fun q(v: Double) = min(255, max(0, (v.toFloat() * o.toFloat() * 255 + 0.5f).toInt())) / 255f
        return FloatImage.filled(size, size, q(c.r), q(c.g), q(c.b), Encoding.DISPLAY_P3)
    }
}
