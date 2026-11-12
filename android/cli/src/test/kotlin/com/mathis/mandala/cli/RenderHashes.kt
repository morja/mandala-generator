package com.mathis.mandala.cli

import com.mathis.mandala.core.Finish
import com.mathis.mandala.core.Framing
import com.mathis.mandala.core.Image8
import com.mathis.mandala.core.LayerAnimation
import com.mathis.mandala.core.LayerCache
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.Parallel
import com.mathis.mandala.core.RenderOptions
import com.mathis.mandala.core.SettingsJson
import com.mathis.mandala.core.VideoFrames
import java.io.File
import java.security.MessageDigest

/**
 * Prints a SHA-256 of the pixels of every fixture and benchmark case in several render variants, one
 * line each. Two runs (before and after a change to `core`) that print the same lines drew the same
 * pixels. Not a test: run its main from the android folder, optionally with a name filter.
 *
 * Variants: the editor preview (512, supersampled, fast glow), the free export (1024, supersampled),
 * the Pro export (2048, not supersampled), a portrait canvas with framing, a finish, a run with three
 * threads, and three frames of the moving picture (turning layers through a layer cache) with their
 * video planes.
 */
object RenderHashes {
    @JvmStatic
    fun main(args: Array<String>) {
        val root = File(".")
        val filter = args.getOrNull(0)?.let { Regex(it) }
        val files = (File(root, "fixtures/settings").listFiles { f -> f.extension == "json" }!!.toList() +
            File(root, "../ios/SpikeBench/Bench").listFiles { f -> f.extension == "json" }!!.map { it })
            .sortedBy { it.path }
            .filter { filter == null || filter.containsMatchIn(it.nameWithoutExtension) }

        val base = RenderOptions(textRenderer = AwtTextRenderer(), imageLoader = FileImageLoader(root))
        for (file in files) {
            val name = (if (file.path.contains("SpikeBench")) "bench-" else "") + file.nameWithoutExtension
            val params = SettingsJson.decode(file.readText())

            fun line(variant: String, image: Image8) = println("$name $variant ${sha(image.data)}")

            line("preview512", MandalaRenderer.render(params, base.copy(outputSize = 512, glowReferenceWidth = 800)).image)
            line("export1024", MandalaRenderer.render(params, base.copy(outputSize = 1024)).image)
            line("pro2048", MandalaRenderer.render(params, base.copy(outputSize = 2048, supersample = false)).image)
            line("threads3", MandalaRenderer.render(params, base.copy(outputSize = 768, threads = 3)).image)
            line(
                "portrait",
                MandalaRenderer.render(params, base.copy(width = 540, height = 960, framing = Framing(0.42, 0.55, 1.3))).image,
            )
            line(
                "finish",
                MandalaRenderer.render(params, base.copy(outputSize = 512, finish = Finish(1.1, 1.2, 0.8))).image,
            )

            // Moving picture: the first frame draws the layers into the cache, the others turn them.
            val cache = LayerCache()
            val frameOptions = LayerAnimation.options(base.copy(outputSize = 256), cache)
            for (frame in listOf(0, 7, 131)) {
                val image = MandalaRenderer.render(LayerAnimation.frame(params, frame, 240), frameOptions).image
                line("frame$frame", image)
                val y = ByteArray(image.width * image.height)
                val u = ByteArray(y.size / 4)
                val v = ByteArray(y.size / 4)
                Parallel(4).use { VideoFrames.toI420(image, y, u, v, it) }
                println("$name frame$frame-i420 ${sha(y + u + v)}")
            }
        }
    }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)
}
