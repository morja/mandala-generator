package com.mathis.mandala.editing

import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.MandalaParameters

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
