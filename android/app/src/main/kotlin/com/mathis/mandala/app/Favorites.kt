package com.mathis.mandala.app

import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.Quote
import com.mathis.mandala.core.SettingsJson
import java.io.File
import java.io.IOException

/** Favourite list rules. Newest first, as in the macOS app. */
object Favorites {
    /** The starter favourite the app icon was made from; a fresh install opens on it. */
    const val FIRST_MANDALA_ID = "3F655220-7E77-466C-B250-E25740453709"

    /** The mandala of a fresh install, taken from the starter favourites. */
    fun firstMandala(starters: List<Favorite>): MandalaParameters? =
        starters.firstOrNull { it.id == FIRST_MANDALA_ID }?.parameters

    fun add(list: List<Favorite>, favorite: Favorite): List<Favorite> = listOf(favorite) + list

    fun remove(list: List<Favorite>, id: String): List<Favorite> = list.filterNot { it.id == id }

    /** The list after an import, how many were added, and how many new ones did not fit. */
    data class Merged(val list: List<Favorite>, val added: Int)

    /** Adds imported favourites that are not there yet (same id). */
    fun merge(list: List<Favorite>, imported: List<Favorite>): Merged {
        val known = list.mapTo(HashSet()) { it.id }
        val fresh = imported.filter { known.add(it.id) }
        return Merged(list + fresh, fresh.size)
    }
}

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

/** The user's own quotes, offered next to the built-in ones. Newest first, no duplicates. */
object MyQuotes {
    fun add(list: List<Quote>, quote: Quote): List<Quote> {
        val clean = Quote(quote.text.trim(), quote.author.trim())
        if (clean.text.isEmpty()) return list
        return listOf(clean) + list.filterNot { it.text == clean.text }
    }

    fun remove(list: List<Quote>, quote: Quote): List<Quote> = list.filterNot { it == quote }
}

class MyQuotesStore(private val file: File) {
    fun load(): List<Quote> = try {
        if (file.isFile) SettingsJson.decodeQuotes(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun save(quotes: List<Quote>) = writeAtomically(file, SettingsJson.encodeQuotes(quotes))
}
