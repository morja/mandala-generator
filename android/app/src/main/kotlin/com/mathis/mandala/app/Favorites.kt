package com.mathis.mandala.app

import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.Quote
import com.mathis.mandala.core.SettingsJson
import java.io.File

/** Favourite list rules. Newest first, as in the macOS app. */
object Favorites {
    fun add(list: List<Favorite>, favorite: Favorite): List<Favorite> = listOf(favorite) + list

    fun remove(list: List<Favorite>, id: String): List<Favorite> = list.filterNot { it.id == id }

    /** The list after an import, how many were added, and how many new ones did not fit. */
    data class Merged(val list: List<Favorite>, val added: Int, val left: Int)

    /** Adds imported favourites that are not there yet (same id), at most `room` of them (ADR-0016). */
    fun merge(list: List<Favorite>, imported: List<Favorite>, room: Int = Int.MAX_VALUE): Merged {
        val known = list.mapTo(HashSet()) { it.id }
        val fresh = imported.filter { known.add(it.id) }
        val kept = fresh.take(room.coerceAtLeast(0))
        return Merged(list + kept, kept.size, fresh.size - kept.size)
    }

    /** How many more own favourites fit under `limit`; the built-in starters (`starterIds`) do not count. */
    fun room(list: List<Favorite>, starterIds: Set<String>, limit: Int): Int =
        (limit - list.count { it.id !in starterIds }).coerceAtLeast(0)
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

/** Writes to a temporary file first, so a crash never leaves half a file behind. */
fun writeAtomically(file: File, text: String) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(file)) {
        file.delete()
        tmp.renameTo(file)
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
