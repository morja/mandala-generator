package com.mathis.mandala.core

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The SVG line drawing (ADR-0013): valid, deterministic, and honest about what it leaves out. */
class VectorExportTest {
    private val fixtures = File(System.getProperty("user.dir")).let { if (File(it, "fixtures").isDirectory) it else it.parentFile }
        .resolve("fixtures/settings")

    private fun fixture(name: String) = SettingsJson.decode(fixtures.resolve("$name.json").readText())

    private fun svg(params: MandalaParameters, look: VectorExport.Look = VectorExport.Look.COLOUR, size: Int = 512): Pair<String, VectorExport.Stats> {
        val out = StringBuilder()
        val stats = VectorExport.svg(params, size, look, out)
        return out.toString() to stats
    }

    private fun parses(text: String) {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(text.byteInputStream())
    }

    @Test
    fun everyLineStyleGivesValidSvgWithPaths() {
        for (style in MandalaStyle.entries - MandalaStyle.STRANGE_ATTRACTOR) {
            val params = MandalaParameters(layers = listOf(StyleLayer(style = style, seed = 7uL)))
            for (look in VectorExport.Look.entries) {
                val (text, stats) = svg(params, look)
                parses(text)
                assertTrue(stats.paths > 0 && stats.segments > 0, "$style $look has no paths")
            }
        }
    }

    @Test
    fun sameSettingsGiveTheSameFile() {
        val params = fixture("style-mixed")
        assertEquals(svg(params).first, svg(params).first)
    }

    @Test
    fun linesLookIsOneDarkColourOnWhite() {
        val (text, _) = svg(fixture("spirograph-basic"), VectorExport.Look.LINES)
        assertTrue(text.contains("""<rect width="512" height="512" fill="#ffffff"/>"""))
        val strokes = Regex("""stroke="(#[0-9a-f]{6})"""").findAll(text).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("#1f1f1f"), strokes)
        assertFalse(text.contains("filter="))
    }

    @Test
    fun strangeAttractorIsLeftOutAndSaid() {
        val params = fixture("style-strange-attractor")
        assertTrue(VectorExport.hasPointLayers(params))
        val (text, stats) = svg(params)
        parses(text)
        assertEquals(0, stats.paths)
        assertTrue("Strange Attractor points" in stats.leftOut, stats.leftOut.joinToString())
    }

    @Test
    fun pixelStepsAreListedAsLeftOut() {
        val (_, stats) = svg(fixture("emblem-glow-light"))
        assertTrue("glow" in stats.leftOut, stats.leftOut.joinToString())
        val (_, plain) = svg(MandalaParameters(layers = listOf(StyleLayer(style = MandalaStyle.ROSE_CURVES, glowIntensity = 0.0, abstractLevel = 0.0))))
        assertTrue(plain.leftOut.isEmpty(), plain.leftOut.joinToString())
    }

    @Test
    fun everyEmblemRenderingBecomesAMask() {
        for (rendering in EmblemRendering.entries) {
            for (motif in EmblemMotif.entries) {
                val params = MandalaParameters(
                    layers = emptyList(),
                    emblemLayer = EmblemLayerSettings(isEnabled = true, motif = motif, rendering = rendering, frame = EmblemFrame.LOTUS),
                )
                val (text, _) = svg(params)
                parses(text)
                val mask = text.substringAfter("<mask id=\"emblem\"").substringBefore("</mask>")
                assertTrue(mask.contains("#fff"), "$motif $rendering draws nothing")
                // Outline cuts the narrowed shape out of the widened one.
                if (rendering == EmblemRendering.DOUBLE_LINE) assertTrue(mask.contains("#000"), "$motif outline cuts nothing")
                assertTrue(text.contains("mask=\"url(#emblem)\""))
            }
        }
    }

    @Test
    fun emblemColourRunsAlongItsPalette() {
        val (text, _) = svg(MandalaParameters(layers = emptyList(), emblemLayer = EmblemLayerSettings(isEnabled = true)))
        assertEquals(17, Regex("<stop ").findAll(text).count())
        val (lines, _) = svg(MandalaParameters(layers = emptyList(), emblemLayer = EmblemLayerSettings(isEnabled = true)), VectorExport.Look.LINES)
        assertFalse(lines.contains("radialGradient"))
    }

    @Test
    fun numbersAreShort() {
        assertEquals("12", svgNumber(12.0))
        assertEquals("12.5", svgNumber(12.5))
        assertEquals("0.33", svgNumber(1 / 3.0))
        assertEquals("-3.1", svgNumber(-3.1))
        assertEquals("0", svgNumber(1e-9))
    }
}
