package com.mathis.mandala.app

import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.CustomPalette
import com.mathis.mandala.core.OwnPalettes
import org.junit.Assert.assertEquals
import org.junit.Test

/** The list of own palettes (ADR-0021). */
class MyPalettesTest {
    private val gold = CustomPalette("G", "Gold copy", OwnPalettes.stopsOf(BuiltInPalettes.all[17]))
    private val other = CustomPalette("O", "Other", OwnPalettes.defaultStops())

    @Test
    fun listKeepsOrderAndReplacesById() {
        val list = MyPalettes.put(MyPalettes.put(emptyList(), gold), other)
        assertEquals(listOf("G", "O"), list.map { it.id })
        val again = MyPalettes.put(list, gold.copy(name = "New name"))
        assertEquals(listOf("New name", "Other"), again.map { it.name })
        assertEquals(listOf("O"), MyPalettes.remove(again, "G").map { it.id })
    }
}
