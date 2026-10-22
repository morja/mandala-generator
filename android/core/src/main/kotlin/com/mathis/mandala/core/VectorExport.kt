package com.mathis.mandala.core

import com.mathis.mandala.core.styles.AttractorPoints
import com.mathis.mandala.core.styles.CurveTask
import com.mathis.mandala.core.styles.LineList
import com.mathis.mandala.core.styles.StyleContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The mandala as an SVG line drawing (ADR-0013, SPIKE-2): the curves and lines the styles collect,
 * from the same style code as the pixel renderer, and the emblem as shapes. Glow, wash, painting,
 * colour grading, background types other than a solid colour, text, hand layers and the effects
 * layer are pixel steps and are left out; so are Strange Attractor points.
 */
object VectorExport {

    enum class Look {
        /** Palette colours on black, layers added like light (CSS plus-lighter), a soft blur for glow. */
        COLOUR,

        /** One dark colour on white, layers multiplied: for plotters, cutters and colouring pages. */
        LINES,
    }

    /** What was written, and what the drawing leaves out, in words for the user. */
    class Stats(val paths: Int, val segments: Int, val leftOut: List<String>)

    /** Line light relative to a hairline in the pixel renderer; SVG has no tone map or glow to add it. */
    private const val COLOUR_GAIN = 4.0
    private const val LINES_GAIN = 3.0
    private const val LINES_COLOUR = "#1f1f1f"

    /** True when a layer of this mandala draws only points, which the line drawing leaves out. */
    fun hasPointLayers(params: MandalaParameters): Boolean =
        params.layers.any { it.isEnabled && it.style == MandalaStyle.STRANGE_ATTRACTOR }

    /**
     * Writes an SVG of `size` × `size` user units with the mandala centred and filling the square,
     * as in a square image without framing.
     */
    fun svg(
        params: MandalaParameters, size: Int, look: Look, out: Appendable,
        palettes: List<ColorPalette> = BuiltInPalettes.all,
    ): Stats {
        val leftOut = linkedSetOf<String>()
        var paths = 0
        var segments = 0
        val colour = look == Look.COLOUR
        val baseRadius = size * 0.72
        // The pixel renderer's brightness scale for one hairline (ADR-0010: lines are hairlines up to 1024 px).
        val lineLight = min(size, 1024) / 1600.0 * (if (colour) COLOUR_GAIN else LINES_GAIN)

        out.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $size $size" width="$size" height="$size">""").append('\n')
        out.append("<defs>\n")
        if (colour) {
            out.append("""<filter id="glow" x="-10%" y="-10%" width="120%" height="120%">""")
            out.append("""<feGaussianBlur stdDeviation="${svgNumber(size / 200.0)}" result="b"/>""")
            out.append("""<feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>""").append('\n')
        }
        val emblem = params.emblemLayer
        val emblemPlace = if (emblem.isEnabled) EmblemPlace(emblem, size) else null
        if (emblemPlace != null) writeEmblemDefs(emblem, emblemPlace, look, palettes, out)
        out.append("</defs>\n")

        val base = params.baseLayer
        val background = when {
            !colour -> "#ffffff"
            base.isEnabled && base.type == BaseLayerType.COLOR -> {
                val c = hsbToRgb(base.hue, base.saturation, base.brightness)
                val o = base.opacity.toFloat()
                hex(c.r.toFloat() * o, c.g.toFloat() * o, c.b.toFloat() * o)
            }
            else -> "#000000"
        }
        if (base.isEnabled && base.type != BaseLayerType.COLOR) leftOut += "the background"
        out.append("""<rect width="$size" height="$size" fill="$background"/>""").append('\n')

        val blend = if (colour) "plus-lighter" else "multiply"
        for ((li, layer) in params.layers.withIndex()) {
            if (!layer.isEnabled) continue
            val palette = palettes[layer.paletteIndex.coerceIn(0, palettes.size - 1)]
            // Seed, symmetry, radius and count exactly as MandalaRenderer.render, so the geometry matches the image.
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
            if (points.isNotEmpty()) leftOut += "Strange Attractor points"
            if (layer.glowIntensity > 0.01) leftOut += "glow"
            if (layer.wash > 0) leftOut += "wash"
            if (layer.abstractLevel > 0.01) leftOut += "the painted look"

            val rotate = if (abs(layer.rotation) > 0.001) {
                """ transform="rotate(${svgNumber(layer.rotation * 360)} ${svgNumber(cx.toDouble())} ${svgNumber(cy.toDouble())})""""
            } else ""
            val filter = if (colour) """ filter="url(#glow)"""" else ""
            val opacity = if (layer.opacity < 0.999) """ opacity="${svgNumber(layer.opacity)}"""" else ""
            out.append("""<g style="mix-blend-mode:$blend;isolation:isolate" fill="none" stroke-linecap="round" stroke-linejoin="round"$rotate$filter$opacity>""").append('\n')

            val writer = PathWriter(out, colour, lineLight)
            val col = FloatArray(3)
            for (task in tasks) {
                val n = task.xs.size
                if (n <= 1) continue
                val tOffset = task.tOffset + layer.colorOffset
                for (i in 0 until n - 1) {
                    val t = (tOffset + i.toDouble() / n * task.drift) % 1.0
                    palette.colorAt(if (t < 0) t + 1.0 else t, col)
                    writer.segment(task.xs[i], task.ys[i], task.xs[i + 1], task.ys[i + 1], col, task.weight, task.thickness.toFloat())
                }
                writer.flush()
            }
            lines.forEachLine { d, o ->
                col[0] = d[o + 4]; col[1] = d[o + 5]; col[2] = d[o + 6]
                writer.segment(d[o], d[o + 1], d[o + 2], d[o + 3], col, d[o + 7], 1f)
            }
            writer.flush()
            paths += writer.paths
            segments += writer.segments
            out.append("</g>\n")
        }

        if (emblemPlace != null) {
            val emblemBlend = when {
                !colour -> "multiply"
                emblem.blendMode == LayerBlendMode.SCREEN || emblem.blendMode == LayerBlendMode.ADD -> "screen"
                emblem.blendMode == LayerBlendMode.MULTIPLY -> "multiply"
                else -> "normal"
            }
            val opacity = if (emblem.opacity < 0.999) """ opacity="${svgNumber(emblem.opacity)}"""" else ""
            val fill = if (colour) "url(#emblem-colour)" else LINES_COLOUR
            out.append("""<rect width="$size" height="$size" fill="$fill" mask="url(#emblem)" style="mix-blend-mode:$emblemBlend"$opacity/>""").append('\n')
            if (emblem.glow > 0.01) leftOut += "glow"
        }
        if (params.textLayer.isEnabled && params.textLayer.text.isNotEmpty()) leftOut += "the quote"
        if (params.effectsLayer.isEnabled) leftOut += "effects"
        if ((params.graffitiLayer.isEnabled && params.graffitiLayer.strokes.isNotEmpty()) ||
            (params.drawingLayer.isEnabled && params.drawingLayer.strokes.isNotEmpty())
        ) leftOut += "drawing by hand"
        out.append("</svg>\n")
        return Stats(paths, segments, leftOut.toList())
    }

    /** The emblem's centre and unit in the square, as EmblemLayer.apply places it. */
    private class EmblemPlace(s: EmblemLayerSettings, size: Int) {
        val unit = s.size.coerceIn(0.1, 1.0) * size * 0.5 / EmblemShapes.RING_RADIUS
        val centreX = size / 2.0 + s.offsetX * size
        val centreY = size / 2.0 + s.offsetY * size
    }

    /** The emblem's shapes as a mask, and for colour its palette running outwards as a radial gradient. */
    private fun writeEmblemDefs(s: EmblemLayerSettings, place: EmblemPlace, look: Look, palettes: List<ColorPalette>, out: Appendable) {
        out.append("""<mask id="emblem" maskUnits="userSpaceOnUse">""").append('\n')
        EmblemMask(0, 0, place.centreX, place.centreY, place.unit, s.rotation)
            .writeSvgMask(EmblemShapes.geometry(s), s.rendering, s.weight, out)
        out.append("</mask>\n")
        if (look != Look.COLOUR) return
        // As EmblemLayer: the colour reaches position + spread at 1.45 units, then stays; the palette runs there and back.
        val palette = palettes[s.paletteIndex.coerceIn(0, palettes.size - 1)]
        out.append("""<radialGradient id="emblem-colour" gradientUnits="userSpaceOnUse" cx="${svgNumber(place.centreX)}" cy="${svgNumber(place.centreY)}" r="${svgNumber(place.unit * 1.45)}">""")
        val col = FloatArray(3)
        val stops = 16
        for (k in 0..stops) {
            val f = k.toDouble() / stops
            var t = (s.colorPosition + s.colorSpread * f) % 2.0
            if (t < 0) t += 2
            if (t > 1) t = 2 - t
            palette.colorAt(t, col)
            out.append("""<stop offset="${svgNumber(f)}" stop-color="${hex(col[0], col[1], col[2])}"/>""")
        }
        out.append("</radialGradient>\n")
    }

    /** Joins consecutive segments of one colour, opacity and width into a single path. */
    private class PathWriter(val out: Appendable, val colour: Boolean, val lineLight: Double) {
        var paths = 0
        var segments = 0
        private val d = StringBuilder()
        private var key = ""
        private var lastX = Float.NaN
        private var lastY = Float.NaN

        fun segment(x0: Float, y0: Float, x1: Float, y1: Float, col: FloatArray, weight: Float, width: Float) {
            if (!x0.isFinite() || !y0.isFinite() || !x1.isFinite() || !y1.isFinite()) return
            val alpha = (weight * lineLight).coerceIn(0.0, 1.0)
            // Colour in 32 steps per channel and opacity in 64 keep runs long without visible banding.
            val stroke = if (colour) hex(q(col[0], 32), q(col[1], 32), q(col[2], 32)) else LINES_COLOUR
            val k = stroke + "|" + svgNumber(q(alpha.toFloat(), 64).toDouble()) + "|" + svgNumber(width.toDouble())
            if (k != key) {
                flush()
                key = k
            }
            if (d.isEmpty() || x0 != lastX || y0 != lastY) d.append('M').append(svgCoord(x0)).append(' ').append(svgCoord(y0))
            d.append('L').append(svgCoord(x1)).append(' ').append(svgCoord(y1))
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

    /** One decimal is a tenth of a pixel at 1024: enough for lines, and keeps files small. */
    private fun svgCoord(v: Float): String {
        val r = (v * 10).roundToInt()
        return if (r % 10 == 0) (r / 10).toString() else (r / 10.0).toString()
    }

    /** A P3 working colour as sRGB hex, the colour space SVG assumes. */
    private fun hex(r: Float, g: Float, b: Float): String {
        val srgb = DoubleArray(3)
        ColorSpaces.p3ToSrgb(r.toDouble(), g.toDouble(), b.toDouble(), srgb)
        fun c(v: Double) = (v.coerceIn(0.0, 1.0) * 255 + 0.5).toInt().toString(16).padStart(2, '0')
        return "#" + c(srgb[0]) + c(srgb[1]) + c(srgb[2])
    }
}
