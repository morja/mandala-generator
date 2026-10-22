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
        "painted-default" to 44.0,
        "painted-heavy" to 26.0,
        // Very dense, overexposed centre: Core Image's over-range handling again (see wave-two-layers).
        "hyperboloid-tilted" to 40.0,
        // Core Image's random generator is Apple's own; amount, offset and grain size match, the pattern
        // differs. Since TUNE-1 both renderers add grain (the Mac's was transparent before), so two
        // different noise patterns are compared.
        "fx-grain" to 32.0,
        // Two additions leave alpha 3, which Core Image's final colour conversion takes into account.
        "fx-chromatic" to 42.0,
        // Chaotic map: tiny sin/cos differences between Apple's and Java's maths library grow with every
        // iteration, so points land elsewhere; the attractor's shape and density are the same.
        "style-strange-attractor" to 15.0,
        // A few connection lines flip where a random keep-or-skip decision sits on a rounding edge.
        "style-constellation-web" to 35.0,
        // Very large, thin ellipses (three times the layer radius): last-bit differences shift fine lines.
        "style-deep-nebula" to 33.0,
        // CIRandomGenerator is Apple's own; mean level matches, the noise pattern differs.
        "background-grain" to 20.0,
        "hand-graffiti" to 27.0,
        "hand-both" to 30.0,
        // Emblem fixtures (emblem-*) keep the default: own antialiasing instead of Core Graphics, 50–63 dB.
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
            val result = MandalaRenderer.render(params, RenderOptions(threads = 4, textRenderer = AwtTextRenderer(), imageLoader = FileImageLoader(root)))
            val diff = ImageDiff.of(Png.read(reference), result.image)
            val min = minPsnr[name] ?: 45.0
            println("$name: $diff (min ${"%.0f".format(min)} dB)")
            if (diff.psnr < min) failures += "$name: $diff, below $min dB"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
