package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Own palettes: the Mac's file format, the copy inside settings, and drawing with it (ADR-0021). */
class OwnPalettesTest {
    private val options = RenderOptions(outputSize = 128, supersample = false, threads = 2)
    private val own = CustomPalette(
        id = "A1", name = "Moss",
        stops = listOf(
            CustomPaletteStop(id = "S1", position = 0.0, hue = 0.28, saturation = 0.8, brightness = 0.15),
            CustomPaletteStop(id = "S2", position = 1.0, hue = 0.17, saturation = 0.25, brightness = 0.95),
        ),
    )

    @Test
    fun readsTheMacPalettesFile() {
        val mac = """[{"id":"6F1C","name":"My Palette","stops":[{"id":"D2E1","position":0,"hue":0.6,"saturation":1,"brightness":0.2},{"id":"D2E2","position":1,"hue":0.9,"saturation":0.8,"brightness":1}]}]"""
        val palettes = SettingsJson.decodePalettes(mac)
        assertEquals("My Palette", palettes.single().name)
        assertEquals(0.9, palettes.single().stops[1].hue)
        assertEquals(palettes, SettingsJson.decodePalettes(SettingsJson.encodePalettes(palettes)))
    }

    @Test
    fun settingsWithoutAnOwnPaletteDoNotMentionIt() {
        assertFalse("customPalette" in SettingsJson.encode(MandalaParameters()))
        val withOwn = MandalaParameters(layers = listOf(StyleLayer(customPalette = own)), emblemLayer = EmblemLayerSettings(customPalette = own))
        assertEquals(withOwn, SettingsJson.decode(SettingsJson.encode(withOwn)))
    }

    @Test
    fun anOwnPaletteIsDrawnInsteadOfTheIndex() {
        fun render(p: MandalaParameters) = MandalaRenderer.render(p, options).image.data
        val plain = MandalaParameters(layers = listOf(StyleLayer(style = MandalaStyle.SUNBURST, paletteIndex = 3)))
        val withOwn = plain.copy(layers = listOf(plain.layers[0].copy(customPalette = own)))
        assertFalse(render(plain).contentEquals(render(withOwn)))
        val empty = plain.copy(layers = listOf(plain.layers[0].copy(customPalette = CustomPalette())))
        assertTrue(render(plain).contentEquals(render(empty)), "a palette without stops falls back to the index")
    }

    @Test
    fun nearestBuiltInFindsAPaletteItself() {
        for (i in listOf(0, 3, 7, 20)) {
            val copy = CustomPalette(stops = OwnPalettes.stopsOf(BuiltInPalettes.all[i])).toColorPalette()
            assertEquals(i, OwnPalettes.nearestBuiltIn(copy, BuiltInPalettes.all.indices.toList()))
        }
    }

    @Test
    fun hsbRoundTrip() {
        val rgb = DoubleArray(3)
        val hsb = DoubleArray(3)
        OwnPalettes.hsbToSrgb(0.83, 0.6, 0.7, rgb)
        OwnPalettes.srgbToHsb(rgb[0], rgb[1], rgb[2], hsb)
        assertEquals(0.83, hsb[0], 1e-9)
        assertEquals(0.6, hsb[1], 1e-9)
        assertEquals(0.7, hsb[2], 1e-9)
    }

    @Test
    fun coloursFromAPictureRunDarkToBrightAndSkipBlack() {
        val colours = intArrayOf(0xFF000000.toInt(), 0xFF203070.toInt(), 0xFFE04020.toInt(), 0xFFFFF0C0.toInt())
        val pixels = IntArray(4000) { colours[it % colours.size] }
        val stops = OwnPalettes.fromPixels(pixels, count = 3)
        assertEquals(3, stops.size)
        assertEquals(listOf(0.0, 0.5, 1.0), stops.map { it.position })
        assertTrue(stops.zipWithNext().all { (a, b) -> a.brightness <= b.brightness + 1e-9 || a.saturation != b.saturation })
        assertTrue(stops.none { it.brightness < 0.08 }, "black was taken in")
        assertTrue(OwnPalettes.fromPixels(IntArray(100)).size == 3, "an all-black picture gives the default stops")
    }
}
