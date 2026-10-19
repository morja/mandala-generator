package com.mathis.mandala.cli

import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.RenderOptions
import com.mathis.mandala.core.SettingsJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders every fixture and compares it with the macOS reference image (tools/reference-renderer).
 * Runs in the full check only: ./gradlew :cli:test -Pparity
 */
class ParityTest {
    private val root = File(System.getProperty("mandala.root") ?: ".")

    /** Minimum PSNR per fixture. Lower values are known, documented deviations (ADR-0004). */
    private val minPsnr = mapOf(
        "spirograph-basic" to 50.0,
        "rose-ripple" to 50.0,
        "hypocycloid-rotated-wash" to 45.0,
        "phyllotaxis-solid-background" to 50.0,
        // Core Image's handling of over-range values in Add blending is not reproduced exactly.
        "wave-two-layers" to 34.0,
        // Core Image's turbulence noise is Apple's own; the displacement pattern differs.
        "painted-default" to 25.0,
        "painted-heavy" to 20.0,
        // Very dense, overexposed centre: Core Image's over-range handling again (see wave-two-layers).
        "hyperboloid-tilted" to 40.0,
    )

    @Test
    fun matchesMacReferences() {
        val failures = mutableListOf<String>()
        val settings = File(root, "fixtures/settings").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        assertTrue(settings.isNotEmpty(), "no fixtures found")
        for (file in settings) {
            val name = file.nameWithoutExtension
            val reference = File(root, "fixtures/reference/$name.png")
            if (!reference.exists()) { failures += "$name: reference image missing"; continue }
            val params = SettingsJson.decode(file.readText())
            val result = MandalaRenderer.render(params, RenderOptions(threads = 4))
            val diff = ImageDiff.of(Png.read(reference), result.image)
            val min = minPsnr[name] ?: 45.0
            println("$name: $diff (min ${"%.0f".format(min)} dB)")
            if (diff.psnr < min) failures += "$name: $diff, below $min dB"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
