package com.mathis.mandala.app

import com.mathis.mandala.app.Editing.layer
import com.mathis.mandala.app.Editing.newVariation
import com.mathis.mandala.app.Editing.updateLayer
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.MandalaStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditingTest {
    private val start = Editing.initial(42uL)

    @Test
    fun offeredStylesAreAllPorted() {
        assertTrue(MandalaRenderer.supportedStyles.containsAll(Editing.styles))
        assertTrue(start.layer.style in Editing.styles)
    }

    @Test
    fun updatingTheLayerKeepsEverythingElse() {
        val changed = start.updateLayer { it.copy(symmetry = 3) }
        assertEquals(3, changed.layer.symmetry)
        assertEquals(start.copy(layers = changed.layers), changed)
    }

    @Test
    fun newVariationChangesOnlyTheSeeds() {
        val varied = start.newVariation(7uL)
        assertEquals(7uL, varied.seed)
        assertEquals(7uL, varied.layer.seed)
        assertEquals(start.layer.copy(seed = 7uL), varied.layer)
    }

    @Test
    fun fileNameFollowsTheMacScheme() {
        val p = start.updateLayer { it.copy(style = MandalaStyle.ROSE_CURVES, paletteIndex = 5, symmetry = 6) }
        assertTrue(Editing.fileName(p), Regex("mandala-rose-p5-x6-[0-9a-f]{8}\\.png").matches(Editing.fileName(p)))
        assertNotEquals(Editing.fileName(p), Editing.fileName(p.newVariation(1uL)))
        assertEquals(Editing.fileName(p), Editing.fileName(p.copy()))
    }
}
