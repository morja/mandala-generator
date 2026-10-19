package com.mathis.mandala.cli

import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.RenderOptions
import com.mathis.mandala.core.SettingsJson
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import kotlin.system.exitProcess

private const val USAGE = """Usage:
  mandala render <settings.json> <out.png> [--size N] [--threads N]
  mandala compare <a.png> <b.png> [--diff diff.png]

render   Renders a settings file saved by the macOS app to a PNG.
         --size       output size in pixels (default: the size stored in the file)
         --threads    drawing/filter threads (default: all cores; each holds a full buffer)
compare  Prints how far two images of equal size differ (mean/max error, PSNR)."""

fun main(args: Array<String>) {
    val code = try {
        when (args.firstOrNull()) {
            "render" -> render(args.drop(1))
            "compare" -> compare(args.drop(1))
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
}

private fun render(raw: List<String>): Int {
    val args = Args(raw)
    if (args.positional.size != 2) throw UsageException("render needs <settings.json> and <out.png>")
    val (input, output) = args.positional
    val params = SettingsJson.decode(File(input).readText())
    val options = RenderOptions(
        outputSize = args.int("size"),
        threads = args.int("threads") ?: Runtime.getRuntime().availableProcessors(),
        textRenderer = AwtTextRenderer(),
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
