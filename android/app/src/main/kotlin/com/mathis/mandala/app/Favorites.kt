package com.mathis.mandala.app

import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.Quote
import com.mathis.mandala.core.SettingsJson
import java.io.File
import java.io.IOException

/** Keeps the favourites in one file in the macOS app's format, so the files are interchangeable. */
class FavoritesStore(private val file: File) {
    /**
     * The saved favourites. On the very first start (no file yet) these are the starter favourites
     * from `seed`, saved right away so they only come once and can be deleted like any other.
     */
    fun loadOrSeed(seed: () -> List<Favorite>): List<Favorite> {
        if (file.exists()) return load()
        val starters = runCatching(seed).getOrDefault(emptyList())
        runCatching { save(starters) }
        return starters
    }

    fun load(): List<Favorite> = try {
        if (file.isFile) SettingsJson.decodeFavorites(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun save(favorites: List<Favorite>) = writeAtomically(file, SettingsJson.encodeFavorites(favorites))
}

/**
 * Writes to a temporary file first and renames it over [file], so a crash never leaves half a file
 * behind and a reader meanwhile sees the old or the new file, never none. The temporary file has
 * its own name per write, because several stores share this function and may save at the same time.
 */
fun writeAtomically(file: File, text: String) {
    val dir = file.absoluteFile.parentFile ?: throw IOException("no folder for $file")
    dir.mkdirs()
    val tmp = File.createTempFile(file.name, ".tmp", dir)
    try {
        tmp.writeText(text)
        if (!tmp.renameTo(file)) throw IOException("could not replace $file")
    } catch (e: Exception) {
        tmp.delete()
        throw e
    }
}

class MyQuotesStore(private val file: File) {
    fun load(): List<Quote> = try {
        if (file.isFile) SettingsJson.decodeQuotes(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun save(quotes: List<Quote>) = writeAtomically(file, SettingsJson.encodeQuotes(quotes))
}
