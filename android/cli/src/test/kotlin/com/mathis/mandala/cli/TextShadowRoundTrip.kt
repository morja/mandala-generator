package com.mathis.mandala.cli

import com.mathis.mandala.core.MacFile
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.SettingsJson
import com.mathis.mandala.editing.TextShadow
import java.io.File

/**
 * For the pixel check of the shadow sliders (QA-13): writes every settings file (and every favourite of a
 * favourites file, one file each) twice, as it is (`as-is/`) and with its text shadow set through the
 * angle, distance and softness sliders to the values they show, as a touch without a move would
 * (`sliders/`). `RenderHashes` over both folders then shows whether the sliders' mapping changes a
 * picture. Not a test: run its main from the android folder with an output folder and settings files.
 */
object TextShadowRoundTrip {
    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args[0])
        val asIs = File(out, "as-is").apply { mkdirs() }
        val sliders = File(out, "sliders").apply { mkdirs() }
        for (path in args.drop(1)) {
            val file = File(path)
            val cases: List<Pair<String, MandalaParameters>> = when (val mac = SettingsJson.decodeMacFile(file.readText())) {
                is MacFile.Settings -> listOf(file.nameWithoutExtension to mac.params)
                is MacFile.Favorites -> mac.favorites.mapIndexed { i, f -> "${file.nameWithoutExtension}-${i + 1}" to f.parameters }
            }
            for ((name, params) in cases) {
                File(asIs, "$name.json").writeText(SettingsJson.encode(params))
                val t = params.textLayer
                val direction = TextShadow.direction(t) ?: TextShadow.DEFAULT_DIRECTION
                val moved = TextShadow.withSoftness(TextShadow.withDistance(TextShadow.withDirection(t, direction), TextShadow.distance(t), direction), t.shadowBlur)
                File(sliders, "$name.json").writeText(SettingsJson.encode(params.copy(textLayer = moved)))
                println("$name offset (${t.shadowOffsetX}, ${t.shadowOffsetY}) -> (${moved.shadowOffsetX}, ${moved.shadowOffsetY}), shadow ${t.shadowOpacity}")
            }
        }
    }
}
