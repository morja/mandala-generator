package com.mathis.mandala.cli

import com.mathis.mandala.core.Framing
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.RenderOptions
import com.mathis.mandala.core.SettingsJson
import com.mathis.mandala.core.VectorExport
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import kotlin.system.exitProcess

private const val USAGE = """Usage:
  mandala render <settings.json> <out.png> [--size N] [--width N --height N]
                 [--centre-x F] [--centre-y F] [--zoom F] [--text follows|stays] [--threads N]
  mandala compare <a.png> <b.png> [--diff diff.png]
  mandala svg <settings.json> <out.svg> [--size N] [--look glow|lines] [--gain F] [--blur yes|no]

render   Renders a settings file saved by the macOS app to a PNG.
         --size       output size in pixels of a square image (default: the size stored in the file)
         --width, --height
                      output size of an image that need not be square (both are needed)
         --centre-x, --centre-y
                      where the mandala's centre sits, as a fraction of width and height (default 0.5)
         --zoom       mandala size relative to fitting the width, 0.25 to 4 (default 1)
         --text       the quote stays in place on the canvas (default) or follows the mandala
         --threads    drawing/filter threads (default: all cores)
svg      SPIKE-2: writes the layers' curves as SVG, without glow, effects, emblem or text.
compare  Prints how far two images of equal size differ (mean/max error, PSNR)."""

fun main(args: Array<String>) {
    val code = try {
        when (args.firstOrNull()) {
            "render" -> render(args.drop(1))
            "compare" -> compare(args.drop(1))
            "svg" -> svg(args.drop(1))
            else -> { println(USAGE); 2 }
        }
    } catch (e: UsageException) {
        System.err.println(e.message)
        System.err.println(USAGE)
        2
    }
    exitProcess(code)
}

private class UsageException(message: String) : Exception(message)

private class Args(raw: List<String>) {
    val positional = mutableListOf<String>()
    val options = mutableMapOf<String, String>()

    init {
        var i = 0
        while (i < raw.size) {
            val a = raw[i]
            if (a.startsWith("--")) {
                if (i + 1 >= raw.size) throw UsageException("Missing value for $a")
                options[a.removePrefix("--")] = raw[i + 1]
                i += 2
            } else {
                positional += a
                i++
            }
        }
    }

    fun int(name: String): Int? = options[name]?.let { it.toIntOrNull() ?: throw UsageException("--$name needs a number") }

    fun double(name: String): Double? = options[name]?.let { it.toDoubleOrNull() ?: throw UsageException("--$name needs a number") }
}

private fun render(raw: List<String>): Int {
    val args = Args(raw)
    if (args.positional.size != 2) throw UsageException("render needs <settings.json> and <out.png>")
    val (input, output) = args.positional
    val params = SettingsJson.decode(File(input).readText())
    val width = args.int("width")
    val height = args.int("height")
    if ((width == null) != (height == null)) throw UsageException("--width and --height go together")
    val text = args.options["text"] ?: "stays"
    if (text != "stays" && text != "follows") throw UsageException("--text is follows or stays")
    val options = RenderOptions(
        outputSize = args.int("size"),
        width = width,
        height = height,
        framing = Framing(args.double("centre-x") ?: 0.5, args.double("centre-y") ?: 0.5, args.double("zoom") ?: 1.0),
        textFollowsMandala = text == "follows",
        threads = args.int("threads") ?: Runtime.getRuntime().availableProcessors(),
        textRenderer = AwtTextRenderer(),
        imageLoader = FileImageLoader(),
    )
    val result = MandalaRenderer.render(params, options)
    Png.write(result.image, File(output))
    result.warnings.forEach { System.err.println("warning: $it") }
    val peakMb = ManagementFactory.getMemoryPoolMXBeans()
        .filter { it.type == MemoryType.HEAP }
        .sumOf { it.peakUsage.used } / (1024 * 1024)
    println("rendered ${result.image.width}x${result.image.height} in ${result.millis} ms (${options.threads} threads, peak heap ${peakMb} MB) -> $output")
    return 0
}

private fun svg(raw: List<String>): Int {
    val args = Args(raw)
    if (args.positional.size != 2) throw UsageException("svg needs <settings.json> and <out.svg>")
    val (input, output) = args.positional
    val params = SettingsJson.decode(File(input).readText())
    val look = when (args.options["look"] ?: "glow") {
        "glow" -> VectorExport.Look.GLOW
        "lines" -> VectorExport.Look.LINES
        else -> throw UsageException("--look is glow or lines")
    }
    val start = System.nanoTime()
    val stats = File(output).bufferedWriter().use { w ->
        // Defaults found in SPIKE-2: without tone map and glow, lines need about 4 (colour) or 3 (lines) times the light.
        val gain = args.double("gain") ?: if (look == VectorExport.Look.GLOW) 4.0 else 3.0
        val blur = (args.options["blur"] ?: if (look == VectorExport.Look.GLOW) "yes" else "no") == "yes"
        VectorExport.svg(params, args.int("size") ?: 1024, look, w, gain, blur)
    }
    val ms = (System.nanoTime() - start) / 1_000_000
    stats.skipped.forEach { System.err.println("left out: $it") }
    println("svg ${stats.paths} paths, ${stats.segments} segments, ${File(output).length() / 1024} KB in $ms ms -> $output")
    return 0
}

private fun compare(raw: List<String>): Int {
    val args = Args(raw)
    if (args.positional.size != 2) throw UsageException("compare needs two PNG files")
    val a = Png.read(File(args.positional[0]))
    val b = Png.read(File(args.positional[1]))
    val diff = ImageDiff.of(a, b)
    println(diff)
    args.options["diff"]?.let { Png.write(diff.heatmap, File(it)) }
    return 0
}
