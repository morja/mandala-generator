package com.mathis.mandala.app

import com.mathis.mandala.core.CustomPalette
import com.mathis.mandala.core.SettingsJson
import java.io.File

/** Keeps the own palettes in `palettes.json`, in the macOS app's format. */
class MyPalettesStore(private val file: File) {
    fun load(): List<CustomPalette> = try {
        if (file.isFile) SettingsJson.decodePalettes(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun save(palettes: List<CustomPalette>) = writeAtomically(file, SettingsJson.encodePalettes(palettes))
}
