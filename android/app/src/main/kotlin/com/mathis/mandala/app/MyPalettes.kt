package com.mathis.mandala.app

import com.mathis.mandala.core.CustomPalette
import com.mathis.mandala.core.SettingsJson
import java.io.File

/** The user's own palettes (ADR-0021): in the order made, as on the Mac; saving one again replaces it. */
object MyPalettes {
    fun put(list: List<CustomPalette>, palette: CustomPalette): List<CustomPalette> {
        val at = list.indexOfFirst { it.id == palette.id }
        return if (at < 0) list + palette else list.toMutableList().also { it[at] = palette }
    }

    fun remove(list: List<CustomPalette>, id: String): List<CustomPalette> = list.filterNot { it.id == id }
}

/** Keeps the own palettes in `palettes.json`, in the macOS app's format. */
class MyPalettesStore(private val file: File) {
    fun load(): List<CustomPalette> = try {
        if (file.isFile) SettingsJson.decodePalettes(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun save(palettes: List<CustomPalette>) = writeAtomically(file, SettingsJson.encodePalettes(palettes))
}
