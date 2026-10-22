package com.mathis.mandala.core

import com.mathis.mandala.core.styles.AttractorPoints
import com.mathis.mandala.core.styles.CurveTask
import com.mathis.mandala.core.styles.LineList
import com.mathis.mandala.core.styles.StyleContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * SPIKE-2: the mandala layers' geometry as SVG. Only the curves and lines the styles collect are
 * written; glow, wash, painting, colour grading, background types other than a solid colour, the
 * emblem, text, hand layers and the effects layer are pixel steps and are left out.
 */
object VectorExport {

    enum class Look {
        /** Coloured lines on black, added like light (CSS plus-lighter), as close to the PNG as SVG gets. */
        GLOW,

        /** Dark lines on white, one colour, for plotters, cutters and colouring pages. */
        LINES,
    }

    class Stats(val paths: Int, val segments: Int, val skipped: List<String>)

    /**
     * Writes an SVG of `size` × `size` user units. `gain` scales the line opacity; `blur` adds a
     * Gaussian blur copy under each layer as a rough stand-in for glow.
     */
    fun svg(
        params: MandalaParameters, size: Int, look: Look, out: Appendable,
        gain: Double = 1.0, blur: Boolean = false, palettes: List<ColorPalette> = BuiltInPalettes.all,
    ): Stats {
        val skipped = mutableListOf<String>()
        var paths = 0
        var segments = 0
        val baseRadius = size * 0.72
        // The pixel renderer scales line light by this; opacity follows it so density reads the same.
        val brightnessScale = min(size, 1024) / 1600.0

        out.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $size $size" width="$size" height="$size">""").append('\n')
        if (blur && look == Look.GLOW) {
            out.append("""<defs><filter id="glow" x="-10%" y="-10%" width="120%" height="120%">""")
            out.append("""<feGaussianBlur stdDeviation="${fmt(size / 200.0)}" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>""").append('\n')
        }
        val base = params.baseLayer
        val background = when {
            look == Look.LINES -> "#ffffff"
            base.isEnabled && base.type == BaseLayerType.COLOR -> {
                val c = hsbToRgb(base.hue, base.saturation, base.brightness)
                hex(c.r.toFloat(), c.g.toFloat(), c.b.toFloat())
            }
            else -> "#000000"
        }
        if (base.isEnabled && base.type != BaseLayerType.COLOR) skipped += "background ${base.type.name.lowercase()} (drawn ${if (look == Look.LINES) "white" else "black"})"
        out.append("""<rect width="$size" height="$size" fill="$background"/>""").append('\n')

        val blend = if (look == Look.GLOW) "plus-lighter" else "multiply"
        for ((li, layer) in params.layers.withIndex()) {
            if (!layer.isEnabled) continue
            val palette = palettes[layer.paletteIndex.coerceIn(0, palettes.size - 1)]
            val layerSeed = if (layer.seed == 0uL) params.seed + (li + 1).toULong() * 0x9e3779b97f4a7c15uL else layer.seed
            val rng = SeededRng(layerSeed)
            val symmetry = layer.symmetry.coerceIn(1, 8)
            val layerRadius = baseRadius * layer.scale.coerceIn(0.1, 1.2)
            val layerCount = max(2, (layer.complexity * 8).toInt() + 1)
            val cx = (size / 2.0 + layer.offsetX * size).toFloat()
            val cy = (size / 2.0 + layer.offsetY * size).toFloat()
            val ctx = StyleContext(
                layer.complexity, layer.density, layer.colorDrift, params.seed, layer.spokes,
                layer.abstractLevel, layer.glowIntensity,
            )
            val tasks = ArrayList<CurveTask>()
            val lines = LineList()
            val points = ArrayList<AttractorPoints>()
            MandalaRenderer.collectStyle(
                layer.style, tasks, lines, points, cx, cy, layerRadius, ctx, palette, rng,
                layerCount, symmetry, layer.ripple.toFloat(), layer.colorOffset,
            )
            if (points.isNotEmpty()) skipped += "layer ${li + 1}: attractor points"
            if (layer.glowIntensity > 0.01 && !blur) skipped += "layer ${li + 1}: glow"
            if (layer.wash > 0) skipped += "layer ${li + 1}: wash"
            if (layer.abstractLevel > 0.01) skipped += "layer ${li + 1}: painted"

            val transform = if (kotlin.math.abs(layer.rotation) > 0.001) {
                """ transform="rotate(${fmt(layer.rotation * 360)} ${fmt(cx.toDouble())} ${fmt(cy.toDouble())})""""
            } else ""
            val filter = if (blur && look == Look.GLOW) """ filter="url(#glow)"""" else ""
            val opacity = if (layer.opacity < 0.999) """ opacity="${fmt(layer.opacity)}"""" else ""
            out.append("""<g style="mix-blend-mode:$blend;isolation:isolate" fill="none" stroke-linecap="round" stroke-linejoin="round"$transform$filter$opacity>""").append('\n')

            val writer = PathWriter(out, look, gain * brightnessScale)
            val col = FloatArray(3)
            for (task in tasks) {
                val n = task.xs.size
                if (n <= 1) continue
                val tOffset = task.tOffset + layer.colorOffset
                for (i in 0 until n - 1) {
                    val t = (tOffset + i.toDouble() / n * task.drift) % 1.0
                    palette.colorAt(if (t < 0) t + 1.0 else t, col)
                    writer.segment(task.xs[i], task.ys[i], task.xs[i + 1], task.ys[i + 1], col[0], col[1], col[2], task.weight, task.thickness.toFloat())
                }
                writer.flush()
            }
            lines.forEachLine { d, o -> writer.segment(d[o], d[o + 1], d[o + 2], d[o + 3], d[o + 4], d[o + 5], d[o + 6], d[o + 7], 1f) }
            writer.flush()
            paths += writer.paths
            segments += writer.segments
            out.append("</g>\n")
        }
        if (params.emblemLayer.isEnabled) skipped += "emblem"
        if (params.textLayer.isEnabled && params.textLayer.text.isNotEmpty()) skipped += "text"
        if (params.effectsLayer.isEnabled) skipped += "effects layer"
        if ((params.graffitiLayer.isEnabled && params.graffitiLayer.strokes.isNotEmpty()) ||
            (params.drawingLayer.isEnabled && params.drawingLayer.strokes.isNotEmpty())
        ) skipped += "hand layers"
        out.append("</svg>\n")
        return Stats(paths, segments, skipped)
    }

    /** Joins consecutive segments of one colour, opacity and width into a single path. */
    private class PathWriter(val out: Appendable, val look: Look, val opacityScale: Double) {
        var paths = 0
        var segments = 0
        private val d = StringBuilder()
        private var key = ""
        private var lastX = Float.NaN
        private var lastY = Float.NaN

        fun segment(x0: Float, y0: Float, x1: Float, y1: Float, r: Float, g: Float, b: Float, weight: Float, width: Float) {
            if (!x0.isFinite() || !y0.isFinite() || !x1.isFinite() || !y1.isFinite()) return
            val alpha = (weight * opacityScale).coerceIn(0.0, 1.0)
            // Colour in 32 steps per channel and opacity in 64 keep runs long without visible banding.
            val stroke = if (look == Look.LINES) "#1f1f1f" else hex(q(r, 32), q(g, 32), q(b, 32))
            val k = "$stroke|${fmt(q(alpha.toFloat(), 64).toDouble())}|${fmt(width.toDouble())}"
            if (k != key) {
                flush()
                key = k
            }
            if (d.isEmpty() || x0 != lastX || y0 != lastY) d.append('M').append(fmt(x0.toDouble())).append(' ').append(fmt(y0.toDouble()))
            d.append('L').append(fmt(x1.toDouble())).append(' ').append(fmt(y1.toDouble()))
            lastX = x1
            lastY = y1
            segments++
        }

        fun flush() {
            if (d.isEmpty()) return
            val (stroke, alpha, width) = key.split('|')
            out.append("""<path d="$d" stroke="$stroke" stroke-opacity="$alpha" stroke-width="$width"/>""").append('\n')
            paths++
            d.setLength(0)
            lastX = Float.NaN
            lastY = Float.NaN
        }

        private fun q(v: Float, steps: Int) = (v.coerceIn(0f, 1f) * (steps - 1)).roundToInt() / (steps - 1).toFloat()
    }

    private fun hex(r: Float, g: Float, b: Float): String {
        fun c(v: Float) = (v.coerceIn(0f, 1f) * 255 + 0.5f).toInt().toString(16).padStart(2, '0')
        return "#" + c(r) + c(g) + c(b)
    }

    /** At most one decimal, no trailing zero: keeps files small. */
    private fun fmt(v: Double): String {
        val r = (v * 10).roundToInt()
        return if (r % 10 == 0) (r / 10).toString() else (r / 10.0).toString()
    }
}
