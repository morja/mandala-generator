package com.mathis.mandala.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Background types of the macOS base layer that are computed per pixel. Like there, the colours
 * go through the same tone map as the mandala lines (x / (x + 0.55)), so they come out darker
 * than the picked colour, and the result is tagged Display P3.
 *
 * On a canvas that is not square (ADR-0010) radial backgrounds stay round, with their radius
 * measured on the shorter side, and sit at the mandala's centre (`centreX`, `centreY`); a linear
 * gradient runs across the whole canvas.
 */
internal object Background {

    fun supports(type: BaseLayerType) =
        type == BaseLayerType.GRADIENT || type == BaseLayerType.SUNBURST || type == BaseLayerType.GRAIN ||
            type == BaseLayerType.AUTO || type == BaseLayerType.IMAGE

    fun render(settings: BaseLayerSettings, width: Int, height: Int, centreX: Double, centreY: Double, par: Parallel): Image8 {
        val c1 = hsbToRgb(settings.hue, settings.saturation, settings.brightness)
        val c2 = hsbToRgb(settings.hue2, settings.saturation2, settings.brightness2)
        val out = Image8(width, height, Encoding.DISPLAY_P3)
        val opacity = settings.opacity.toFloat()
        val fill: (Int, Int, FloatArray) -> Unit = when (settings.type) {
            BaseLayerType.SUNBURST -> sunburst(settings, c1, c2, width, height, centreX, centreY)
            BaseLayerType.GRAIN -> grain(settings, c1)
            else -> if (settings.isRadial) radialGradient(c1, c2, width, height, centreX, centreY) else linearGradient(settings, c1, c2, width, height)
        }
        par.forRange(height) { from, until ->
            val c = FloatArray(3)
            for (y in from until until) {
                for (x in 0 until width) {
                    fill(x, y, c)
                    val o = (y * width + x) * 3
                    for (k in 0..2) {
                        val v = c[k] * opacity
                        out.data[o + k] = WorkingSpace.quantize(v / (v + 0.55f))
                    }
                }
            }
        }
        return out
    }

    // ── Image (drawBaseLayer .image in the macOS app) ──
    // The picture is stretched to the square, scaled by strength and opacity and tone-mapped like
    // the lines, so it sits darker behind the mandala. Without a picture: colour 1. On a canvas that
    // is not square, `picture` is the square of its longer side and the middle of it is used.

    fun renderImage(settings: BaseLayerSettings, picture: Image8?, width: Int, height: Int, par: Parallel): Image8 {
        val strength = (settings.imageBlend * settings.opacity).toFloat()
        val out = Image8(width, height, Encoding.DISPLAY_P3)
        val c1 = hsbToRgb(settings.hue, settings.saturation, settings.brightness)
        val fallback = floatArrayOf(c1.r.toFloat(), c1.g.toFloat(), c1.b.toFloat())
        val side = max(width, height)
        val ox = (side - width) / 2
        val oy = (side - height) / 2
        par.forRange(height) { from, until ->
            for (y in from until until) {
                for (x in 0 until width) {
                    val o = (y * width + x) * 3
                    val po = ((y + oy) * side + x + ox) * 3
                    for (k in 0..2) {
                        val v = (if (picture != null) picture.get(po + k) / 255f else fallback[k]) * strength
                        out.data[o + k] = WorkingSpace.quantize(v / (v + 0.55f))
                    }
                }
            }
        }
        return out
    }

    // ── Auto (drawBackground plus drawGrassFibers in the macOS app) ──
    // A dark radial gradient tinted by the first layer's palette, a faint wash of its middle colour,
    // and thousands of short "grass" strokes around the centre, all tone-mapped like the lines.
    // It is drawn from the first layer around the mandala, so it takes the mandala's centre and size.

    /** Threads the macOS app splits the fibres over; each draws from its own random stream. */
    private const val MAC_FIBRE_THREADS = 12

    fun renderAuto(
        params: MandalaParameters, palettes: List<ColorPalette>, width: Int, height: Int,
        centreX: Double, centreY: Double, side: Int, par: Parallel,
    ): Image8 {
        val first = params.layers.firstOrNull()
        val palette = OwnPalettes.resolve(first?.paletteIndex ?: 0, first?.customPalette, palettes)
        val col0 = FloatArray(3).also { palette.colorAt(0.0, it) }
        val col1 = FloatArray(3).also { palette.colorAt(0.5, it) }

        // Core Image blends and adds in linear light and hands back 8-bit sRGB values, which the
        // macOS app copies into its float buffer as they are.
        val inner = DoubleArray(3) { ColorSpaces.decode(col0[it] * 0.12) }
        val wash = DoubleArray(3) { ColorSpaces.decode(col1[it] * 0.06) }
        val r0 = side * 0.55
        val r1 = side * 0.85
        // Strip by strip: the gradient for the strip's rows, then all fibres clipped to them.
        return PixelBuffer.drawInStrips(width, height) { strip ->
            val top = strip.rowsFrom
            par.forRange(strip.rowsUntil - top) { from, until ->
                for (r in from until until) {
                    val y = top + r
                    for (x in 0 until width) {
                        val dx = x + 0.5 - centreX
                        val dy = y + 0.5 - centreY
                        val t = ((sqrt(dx * dx + dy * dy) - r0) / (r1 - r0)).coerceIn(0.0, 1.0)
                        val o = (r * width + x) * 3
                        for (k in 0..2) {
                            val enc = ColorSpaces.encode(inner[k] * (1 - t) + wash[k])
                            strip.data[o + k] = (min(255.0, max(0.0, enc * 255 + 0.5)).toInt() / 255.0).toFloat()
                        }
                    }
                }
            }
            if (first != null) grassFibres(strip, params.seed, first, palette, centreX.toFloat(), centreY.toFloat(), side)
        }
    }

    private fun grassFibres(buffer: PixelBuffer, seed: ULong, layer: StyleLayer, palette: ColorPalette, cx: Float, cy: Float, side: Int) {
        val maxRadius = side.toFloat() * 0.48f
        val lines = (15000.0 * layer.density + 500).toInt()
        val symmetry = layer.symmetry.coerceIn(1, 8)
        val chunk = max(1, lines / MAC_FIBRE_THREADS)
        val col = FloatArray(3)
        for (tid in 0 until MAC_FIBRE_THREADS) {
            val start = tid * chunk
            val end = if (tid == MAC_FIBRE_THREADS - 1) lines else min(start + chunk, lines)
            if (start >= end) continue
            val rng = SeededRng(seed + tid.toULong() * 6364136223846793005uL + 1uL)
            for (i in start until end) {
                val angle = rng.nextDouble() * Math.PI * 2.0
                val startR = rng.nextDouble().toFloat() * maxRadius * 0.95f
                val length = rng.nextDouble(3.0, 18.0).toFloat() * (1.0f + layer.complexity.toFloat() * 1.5f)
                val wobble = rng.nextDouble(-0.15, 0.15).toFloat()
                palette.colorAt(rng.nextDouble(), col)
                val weight = rng.nextDouble(0.03, 0.25).toFloat() * layer.density.toFloat()
                for (k in 0 until symmetry) {
                    val sa = (angle + k * Math.PI * 2.0 / symmetry).toFloat()
                    val x0 = cx + cos(sa) * startR
                    val y0 = cy + sin(sa) * startR
                    val ea = sa + wobble
                    buffer.addLine(x0, y0, x0 + cos(ea) * length, y0 + sin(ea) * length, col[0], col[1], col[2], weight)
                }
            }
        }
    }

    // ── Gradients (CIRadialGradient / CISmoothLinearGradient in the macOS app) ──
    // Core Image blends the two colours in linear light and hands back 8-bit sRGB values,
    // which the macOS renderer then copies into its buffer as they are.

    private fun radialGradient(c1: Rgb, c2: Rgb, width: Int, height: Int, centreX: Double, centreY: Double): (Int, Int, FloatArray) -> Unit {
        val r1 = min(width, height) * 0.72
        return { x, y, out ->
            val dx = x + 0.5 - centreX
            val dy = y + 0.5 - centreY
            mixLinear(c1, c2, min(1.0, sqrt(dx * dx + dy * dy) / r1), out)
        }
    }

    private fun linearGradient(settings: BaseLayerSettings, c1: Rgb, c2: Rgb, width: Int, height: Int): (Int, Int, FloatArray) -> Unit {
        val angle = settings.gradientAngle * Math.PI * 2
        val ca = cos(angle)
        val sa = sin(angle)
        // Half the length of the gradient: half the side on a square; on other canvases it weighs
        // width and height by the direction, so it runs from edge to edge both ways.
        val half = if (width == height) width * 0.5 else 0.5 * (abs(ca) * width + abs(sa) * height) / (abs(ca) + abs(sa))
        // Core Image's y axis points up; the image rows run down.
        val dx = ca * half
        val dy = sa * half
        val len2 = 4 * (dx * dx + dy * dy)
        val centreX = width / 2.0
        val centreY = height / 2.0
        return { x, y, out ->
            val px = x + 0.5 - (centreX - dx)
            val py = (height - (y + 0.5)) - (centreY - dy)
            val t = ((px * 2 * dx + py * 2 * dy) / len2).coerceIn(0.0, 1.0)
            mixLinear(c1, c2, t * t * (3 - 2 * t), out)
        }
    }

    private fun mixLinear(a: Rgb, b: Rgb, t: Double, out: FloatArray) {
        out[0] = mixChannel(a.r, b.r, t)
        out[1] = mixChannel(a.g, b.g, t)
        out[2] = mixChannel(a.b, b.b, t)
    }

    private fun mixChannel(a: Double, b: Double, t: Double): Float {
        val lin = ColorSpaces.decode(a) + (ColorSpaces.decode(b) - ColorSpaces.decode(a)) * t
        val enc = ColorSpaces.encode(lin)
        return (min(255.0, max(0.0, enc * 255 + 0.5)).toInt() / 255.0).toFloat()
    }

    // ── Grain (CIRandomGenerator tinted by colour 1 in the macOS app) ──
    // Core Image's random pattern is Apple's own; amount, tint and base level match.

    private fun grain(settings: BaseLayerSettings, c1: Rgb): (Int, Int, FloatArray) -> Unit {
        val ga = settings.grainAmount.toFloat()
        val bd = (settings.brightness * 0.6).toFloat()
        val tint = if (settings.grainColored) floatArrayOf(c1.r.toFloat(), c1.g.toFloat(), c1.b.toFloat()) else floatArrayOf(1f, 1f, 1f)
        val base = floatArrayOf(c1.r.toFloat() * bd, c1.g.toFloat() * bd, c1.b.toFloat() * bd)
        return { x, y, out ->
            // The macOS app runs this colour matrix in a default Core Image context, i.e. in linear
            // light, and copies the encoded 8-bit result into its buffer (matched by mean level).
            for (k in 0..2) {
                val n = Filters.hash01(x, y, 211 + k).toFloat()
                val lin = (n * tint[k] * ga + base[k]).coerceIn(0f, 1f)
                // CIRandomGenerator also randomises alpha, which acts as coverage when the result is
                // drawn onto the black buffer.
                val alpha = Filters.hash01(x, y, 214).toFloat()
                out[k] = (ColorSpaces.encode(lin.toDouble() * alpha) * 255 + 0.5).toInt().coerceIn(0, 255) / 255f
            }
        }
    }

    // ── Sunburst (computed per pixel in the macOS app too) ──

    private fun sunburst(settings: BaseLayerSettings, c1: Rgb, c2: Rgb, width: Int, height: Int, centreX: Double, centreY: Double): (Int, Int, FloatArray) -> Unit {
        val cx = centreX.toFloat()
        val cy = centreY.toFloat()
        val size = min(width, height)
        // A whole number of rays (rounded half away from zero, like Swift), so they meet where the angle wraps round.
        val numRays = (settings.patternScale * 14.0 + 3.0).let { kotlin.math.sign(it) * kotlin.math.floor(abs(it) + 0.5) }.toFloat()
        val edgeWidth = max(0.015f, 0.48f * (1.0f - settings.patternSharpness.toFloat() * 0.93f))
        val invHalfW = 1.0f / (size * 0.5f)
        val a1 = floatArrayOf(c1.r.toFloat(), c1.g.toFloat(), c1.b.toFloat())
        val a2 = floatArrayOf(c2.r.toFloat(), c2.g.toFloat(), c2.b.toFloat())
        return { x, y, out ->
            val dx = x - cx
            val dy = y - cy
            val angle = atan2(dy, dx)
            val dist = sqrt(dx * dx + dy * dy) * invHalfW
            val raw = (sin(angle * numRays) + 1.0f) * 0.5f
            var t = ((raw - (0.5f - edgeWidth)) / (2.0f * edgeWidth)).coerceIn(0f, 1f)
            val radial = max(0.0f, 1.0f - dist * dist * 0.45f)
            t = t * radial + 0.5f * (1.0f - radial)
            for (k in 0..2) out[k] = a1[k] * (1.0f - t) + a2[k] * t
        }
    }
}
