package com.mathis.mandala.app

import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.SettingsJson
import java.io.File

/**
 * Keeps the last settings across app restarts, as one JSON file in the same format as the macOS
 * app's saved settings. Writes go to a temporary file first, so a crash never leaves half a file.
 */
class SettingsStore(private val file: File) {

    /** The saved settings, or null when there are none or the file cannot be read. */
    fun load(): MandalaParameters? = try {
        if (file.isFile) SettingsJson.decode(file.readText()).takeIf { it.layers.isNotEmpty() } else null
    } catch (e: Exception) {
        null
    }

    fun save(params: MandalaParameters) = writeAtomically(file, SettingsJson.encode(params))
}
