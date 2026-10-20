package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Emblem settings as the macOS app writes them, and the geometry details the renderer relies on. */
class EmblemLayerTest {

    @Test
    fun missingEmblemLayerGivesTheSwiftDefaults() {
        val e = SettingsJson.decode("""{"layers":[{"style":"spirograph"}]}""").emblemLayer
        assertEquals(EmblemLayerSettings(), e)
        assertEquals(false, e.isEnabled)
        assertEquals(EmblemMotif.OM, e.motif)
        assertEquals(EmblemRendering.FILLED, e.rendering)
        assertEquals(EmblemFrame.RING, e.frame)
        assertEquals(EmblemFlowerVariant.FLOWER, e.flowerVariant)
        assertEquals(LayerBlendMode.NORMAL, e.blendMode)
        assertEquals(1uL, e.seed)
        assertEquals(8, e.petals)
    }

    @Test
    fun unknownValuesFallBackLikeTheSwiftDecoders() {
        val e = SettingsJson.decode(
            """{"emblemLayer":{"isEnabled":true,"motif":"pentagram","rendering":"neon","frame":"square",
               "flowerVariant":"egg","blendMode":"overlay","petals":12,"futureField":3}}""",
        ).emblemLayer
        assertEquals(true, e.isEnabled)
        assertEquals(EmblemMotif.OM, e.motif)
        assertEquals(EmblemRendering.FILLED, e.rendering)
        assertEquals(EmblemFrame.RING, e.frame)
        assertEquals(EmblemFlowerVariant.FLOWER, e.flowerVariant)
        // An unknown blend falls back to the field's default (Normal for the emblem), as in Swift.
        assertEquals(LayerBlendMode.NORMAL, e.blendMode)
        assertEquals(12, e.petals)
    }

    @Test
    fun fieldNamesMatchTheMacFile() {
        val e = EmblemLayerSettings(
            isEnabled = true, motif = EmblemMotif.TREE_OF_LIFE, rendering = EmblemRendering.DOUBLE_LINE,
            frame = EmblemFrame.ENSO, flowerVariant = EmblemFlowerVariant.METATRON, seed = 18_000_000_000_000_000_000uL,
        )
        val json = SettingsJson.encode(MandalaParameters(emblemLayer = e))
        for (part in listOf(""""motif":"treeOfLife"""", """"rendering":"doubleLine"""", """"frame":"enso"""",
            """"flowerVariant":"metatron"""", """"seed":18000000000000000000""", """"colorPosition":0.2""")) {
            assertTrue(part in json, "missing $part")
        }
        assertEquals(e, SettingsJson.decode(json).emblemLayer)
    }

    @Test
    fun treeTwigsEndOnTheRing() {
        val g = EmblemShapes.treeOfLife(seed = 7uL, branching = 0.5, roots = true, organic = 0.0)
        val tips = g.strokes.map { it.last() }.filter { kotlin.math.hypot(it.x, it.y) > 0.9 }
        // 8 + 28 crown twigs and half as many roots.
        assertEquals(36 + 18, tips.size)
        for (t in tips) assertEquals(EmblemShapes.RING_RADIUS, kotlin.math.hypot(t.x, t.y), 1e-9)
    }

    @Test
    fun maskIsCoverageInsideOutAndCutsHolesEvenOdd() {
        Parallel(2).use { par ->
            val g = EmblemGeometry()
            // A square of half-width 0.5 with a square hole of half-width 0.25 (unit 100 px, 200 px image).
            fun square(h: Double) = listOf(Pt(-h, -h), Pt(h, -h), Pt(h, h), Pt(-h, h))
            g.fills += listOf(square(0.5), square(0.25))
            val mask = EmblemMask(200, 100.0, 0.0).draw(g, EmblemRendering.FILLED, 0.5, par)
            fun at(x: Int, y: Int) = mask[y * 200 + x].toInt() and 0xFF
            assertEquals(255, at(60, 100)) // in the ring
            assertEquals(0, at(100, 100)) // in the hole
            assertEquals(0, at(10, 100)) // outside
            // Edge at x = 50: pixel 49 outside, pixel 50 inside.
            assertEquals(0, at(49, 100))
            assertEquals(255, at(50, 100))
        }
    }
}
