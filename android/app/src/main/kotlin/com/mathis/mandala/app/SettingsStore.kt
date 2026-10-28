package com.mathis.mandala.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

/**
 * The last preview as drawn, with the settings it shows. After a restart the first preview can take
 * many seconds (the drawing code is not compiled yet after an update), so this one stands in meanwhile.
 */
class PreviewStore(private val dir: File) {
    private val image = File(dir, "last-preview.png")
    private val settings = File(dir, "last-preview.json")

    /** The kept preview if it shows exactly `params`, else null. */
    fun load(params: MandalaParameters): Bitmap? = try {
        if (image.isFile && settings.isFile && settings.readText() == SettingsJson.encode(params)) BitmapFactory.decodeFile(image.path) else null
    } catch (e: Exception) {
        null
    }

    fun save(params: MandalaParameters, bitmap: Bitmap) {
        // The settings go last: a half-written pair never matches.
        settings.delete()
        val tmp = File(dir, image.name + ".tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!tmp.renameTo(image)) return
        writeAtomically(settings, SettingsJson.encode(params))
    }
}

/** The history (back and forward) across restarts, in the format of the macOS app's history.json. */
class HistoryStore(private val file: File) {
    fun load(): List<MandalaParameters> = try {
        if (file.isFile) SettingsJson.decodeList(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun save(history: List<MandalaParameters>) = writeAtomically(file, SettingsJson.encodeList(history))
}
