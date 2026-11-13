package com.mathis.mandala.cli

import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.RenderOptions
import com.mathis.mandala.core.SettingsJson
import com.mathis.mandala.core.TextLayerSettings
import com.mathis.mandala.editing.Editing
import com.mathis.mandala.editing.TextColours
import java.io.File

/**
 * Before and after "Stand out" (DM-24) with the real renderer: renders a settings file as it is,
 * then with the colours the rule picks from the picture without text, as the app does (the swatches
 * and the area from a small render without the text). Not a test: run its main from the android
 * folder with an output folder and settings files; prints what the rule chose.
 */
object TextGlowShots {
    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args[0])
        val options = RenderOptions(outputSize = 1024, textRenderer = AwtTextRenderer(), imageLoader = FileImageLoader(File(".")))
        for (path in args.drop(1)) {
            val file = File(path)
            val params = SettingsJson.decode(file.readText())
            val name = file.nameWithoutExtension
            Png.write(MandalaRenderer.render(params, options).image, File(out, "$name-before.png"))

            // The picture without its text, as EditorViewModel samples it.
            val sample = MandalaRenderer.render(
                params.copy(textLayer = TextLayerSettings()),
                RenderOptions(outputSize = Editing.SAMPLE_SIZE, supersample = false, glowReferenceWidth = Editing.QUICK_GLOW_REFERENCE, imageLoader = FileImageLoader(File("."))),
            ).image
            val argb = sample.toArgbPixels()
            val swatches = TextColours.pictureSwatches(argb)
            val r = TextColours.standOut(argb, sample.width, sample.height, TextColours.textArea(params.textLayer), swatches)
            if (r == null) {
                println("$name: no text area")
                continue
            }
            val after = params.copy(textLayer = TextColours.applyStandOut(params.textLayer, r))
            Png.write(MandalaRenderer.render(after, options).image, File(out, "$name-stand-out.png"))
            File(out, "$name-stand-out.json").writeText(SettingsJson.encode(after))
            fun f(x: Double) = "%.3f".format(x)
            println(
                "$name: swatches ${swatches.joinToString { "${f(it.hue)}/${f(it.saturation)}" }}; " +
                    "dominant ${f(r.dominantHue)} clarity ${f(r.clarity)}; glow ${r.glow}; text ${r.text}; contrast ${"%.1f".format(r.contrast)}",
            )
        }
    }
}
