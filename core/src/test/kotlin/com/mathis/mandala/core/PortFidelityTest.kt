package com.mathis.mandala.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Building blocks that must match the macOS app exactly. Expected values come from the Swift code. */
class PortFidelityTest {

    @Test
    fun rngMatchesSwiftBitForBit() {
        val cases = mapOf(
            42uL to listOf("45454805674", "11532217803599905471", "0.08652420193869607", "0.3143168680251162"),
            0uL to listOf("10092791515770273427", "4846520707914586670", "0.12646515757633525", "0.9847659112746199"),
            17463462634133840693uL to listOf("12553406193712577459", "14987786285809102084", "0.16178670094705136", "0.01752261888953767"),
        )
        for ((seed, expected) in cases) {
            val rng = SeededRng(seed)
            assertEquals(expected[0], rng.next().toString(), "seed $seed")
            assertEquals(expected[1], rng.next().toString(), "seed $seed")
            assertEquals(expected[2].toDouble(), rng.nextDouble(), "seed $seed")
            assertEquals(expected[3].toDouble(), rng.nextDouble(), "seed $seed")
        }
    }

    @Test
    fun paletteStopsConvertToP3LikeNSColor() {
        // NSColor(red:green:blue:).usingColorSpace(.displayP3) on macOS.
        val cases = listOf(
            doubleArrayOf(1.0, 0.0, 0.5) to doubleArrayOf(0.917471706867218, 0.20035654306411743, 0.4977555274963379),
            doubleArrayOf(0.0, 1.0, 0.6) to doubleArrayOf(0.4584309458732605, 0.9852586388587952, 0.6361858248710632),
            doubleArrayOf(0.0, 0.05, 0.2) to doubleArrayOf(0.009036412462592125, 0.048526324331760406, 0.19119861721992493),
        )
        for ((srgb, p3) in cases) {
            val c = Rgb.fromSrgb(srgb[0], srgb[1], srgb[2])
            assertTrue(abs(c.r - p3[0]) < 1e-3 && abs(c.g - p3[1]) < 1e-3 && abs(c.b - p3[2]) < 1e-3, "$srgb -> $c")
        }
    }

    @Test
    fun builtInPalettesKeepTheMacOrder() {
        val ids = BuiltInPalettes.all.map { it.id }
        assertEquals(18, ids.size)
        assertEquals("psychedelic", ids.first())
        assertEquals("synthwave", ids[15])
        assertEquals("gold", ids.last())
    }

    @Test
    fun decodesSettingsSavedByTheMacApp() {
        val json = """{"outputSize":1400,"layers":[{"paletteIndex":15,"style":"waveInterference","isEnabled":true,
            "seed":17463462634133840693,"blendMode":"screen","symmetry":4}],"seed":13730275572896771193,
            "baseLayer":{"isEnabled":false,"type":"gradient"},"effectsLayer":{"isEnabled":true,"vignette":0.3},
            "someFutureField":1}"""
        val p = SettingsJson.decode(json)
        assertEquals(1400, p.outputSize)
        assertEquals(13730275572896771193uL, p.seed)
        assertEquals(MandalaStyle.WAVE_INTERFERENCE, p.layers[0].style)
        assertEquals(17463462634133840693uL, p.layers[0].seed)
        assertEquals(0.6, p.layers[0].complexity, "missing fields use the Swift defaults")
        assertTrue(p.effectsLayer.isEnabled)
    }

    @Test
    fun unknownEnumValuesFallBackLikeSwift() {
        val p = SettingsJson.decode("""{"layers":[{"style":"noSuchStyle","blendMode":"weird"}]}""")
        assertEquals(MandalaStyle.MIXED, p.layers[0].style)
        assertEquals(LayerBlendMode.SCREEN, p.layers[0].blendMode)
    }

    @Test
    fun settingsSurviveARoundTrip() {
        val json = """{"layers":[{"style":"roseCurves","seed":18446744073709551615}],"textLayer":{"isEnabled":true,"text":"Om"}}"""
        val p = SettingsJson.decode(json)
        val again = SettingsJson.decode(SettingsJson.encode(p))
        assertEquals(p, again)
        assertEquals(ULong.MAX_VALUE, again.layers[0].seed)
    }

    @Test
    fun renderIsDeterministic() {
        val p = SettingsJson.decode("""{"layers":[{"style":"spirograph","abstractLevel":0.5}],"outputSize":96}""")
        val a = MandalaRenderer.render(p, RenderOptions(threads = 3)).image.data
        val b = MandalaRenderer.render(p, RenderOptions(threads = 1)).image.data
        var maxDiff = 0f
        for (i in a.indices) maxDiff = maxOf(maxDiff, abs(a[i] - b[i]))
        assertTrue(maxDiff <= 1f / 255f + 1e-6f, "thread count changed the image by $maxDiff")
    }
}
