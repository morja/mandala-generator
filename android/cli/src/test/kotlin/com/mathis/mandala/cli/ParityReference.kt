package com.mathis.mandala.cli

import com.mathis.mandala.core.Encoding
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.RenderOptions
import com.mathis.mandala.core.SettingsJson
import com.mathis.mandala.core.WorkingSpace
import java.io.File

/**
 * Writes the JVM renders the iPhone framework is compared with (`ios/scripts/check-full`, PLAN-4
 * section 3.11): every fixture of `fixtures/settings` as the editor preview (512, supersampled, glow
 * reference 800) and the three SPIKE-3 cases as the 1024 export, as sRGB RGBA bytes like
 * `IosBridge.rgba`, plus `cases.txt` listing them, without text renderer or image loader. The text
 * cases of `ios/Dahlia/Tests/TextCases` (starter favourites with quotes and one fixture per font) go
 * through the JVM's `AwtTextRenderer` with the Mac's fonts into `text-cases.txt`: the iPhone draws
 * them with Core Text, so they are compared with their own threshold (PLAN-4 section 3.11).
 * Not a test: run its main from the android folder.
 *
 * Usage: ParityReference <output folder>
 */
object ParityReference {
    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args[0]).also { it.mkdirs() }
        val root = File(".")
        val cases = mutableListOf<Triple<File, Int, Int>>() // settings file, size, glow reference
        File(root, "fixtures/settings").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
            .forEach { cases += Triple(it, 512, 800) }
        File(root, "../ios/SpikeBench/Bench").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
            .forEach { cases += Triple(it, 1024, 1600) }

        val manifest = StringBuilder()
        for ((file, size, glow) in cases) {
            val name = (if (file.path.contains("SpikeBench")) "bench-" else "") + file.nameWithoutExtension
            val params = SettingsJson.decode(file.readText())
            val image = MandalaRenderer.render(
                params,
                RenderOptions(outputSize = size, glowReferenceWidth = glow, threads = 4),
            ).image
            File(out, "$name-$size.rgba").writeBytes(rgba(image))
            manifest.appendLine("$name $size $glow ${file.absolutePath}")
        }
        File(out, "cases.txt").writeText(manifest.toString())
        println("wrote ${cases.size} references to $out")

        val text = StringBuilder()
        val textCases = File(root, "../ios/Dahlia/Tests/TextCases").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        val renderer = AwtTextRenderer()
        for (file in textCases) {
            val params = SettingsJson.decode(file.readText())
            val image = MandalaRenderer.render(
                params,
                RenderOptions(outputSize = 512, glowReferenceWidth = 800, threads = 4, textRenderer = renderer),
            ).image
            File(out, "${file.nameWithoutExtension}-512.rgba").writeBytes(rgba(image))
            text.appendLine("${file.nameWithoutExtension} 512 800 ${file.absolutePath}")
        }
        File(out, "text-cases.txt").writeText(text.toString())
        println("wrote ${textCases.size} text references to $out")
    }

    /** sRGB RGBA bytes with alpha 255, as `IosBridge.rgba`. */
    private fun rgba(image: com.mathis.mandala.core.Image8): ByteArray {
        val srgb = if (image.encoding == Encoding.SRGB) image else WorkingSpace.convertToSrgb(image)
        val rgba = ByteArray(image.width * image.height * 4)
        for (p in 0 until image.width * image.height) {
            rgba[p * 4] = srgb.data[p * 3]
            rgba[p * 4 + 1] = srgb.data[p * 3 + 1]
            rgba[p * 4 + 2] = srgb.data[p * 3 + 2]
            rgba[p * 4 + 3] = -1
        }
        return rgba
    }
}
