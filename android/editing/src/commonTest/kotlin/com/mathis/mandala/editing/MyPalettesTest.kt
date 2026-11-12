package com.mathis.mandala.editing

import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.CustomPalette
import com.mathis.mandala.core.OwnPalettes
import kotlin.test.Test
import kotlin.test.assertEquals

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
