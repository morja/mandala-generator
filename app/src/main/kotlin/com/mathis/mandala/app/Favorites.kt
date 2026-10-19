package com.mathis.mandala.app

import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.SettingsJson
import java.io.File

/** Favourite list rules. Newest first, as in the macOS app. */
object Favorites {
    fun add(list: List<Favorite>, favorite: Favorite): List<Favorite> = listOf(favorite) + list

    fun remove(list: List<Favorite>, id: String): List<Favorite> = list.filterNot { it.id == id }

    /** Adds imported favourites that are not there yet (same id). Returns the list and how many were added. */
    fun merge(list: List<Favorite>, imported: List<Favorite>): Pair<List<Favorite>, Int> {
        val known = list.mapTo(HashSet()) { it.id }
        val fresh = imported.filter { known.add(it.id) }
        return list + fresh to fresh.size
    }
}

/** Keeps the favourites in one file in the macOS app's format, so the files are interchangeable. */
class FavoritesStore(private val file: File) {
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
