package com.mathis.mandala.editing

import com.mathis.mandala.core.Favorite

/**
 * Whose turn it is among the favourites, for the daily wallpaper and the widget (ADR-0014, ADR-0020).
 * The platforms decide when a turn moves on (Android: the morning job; iPhone: by date); the order
 * and the choice of favourites are the same on both.
 */
object DailyTurn {
    /**
     * The favourite whose turn it is and the next turn counter: the chosen ones in their order, or all
     * when none is chosen or every chosen one has been deleted.
     */
    fun turn(favorites: List<Favorite>, chosen: Set<String>?, next: Int): Pair<Favorite, Int>? {
        val pool = chosen?.let { ids -> favorites.filter { it.id in ids } }?.ifEmpty { null } ?: favorites
        if (pool.isEmpty()) return null
        val index = next.mod(pool.size)
        return pool[index] to index + 1
    }

    /**
     * The choice after tapping one favourite in the picker. "All" shows every favourite ticked, so the
     * first tap leaves out the tapped one; leaving out the last one, or ticking every one, means all again.
     */
    fun toggled(chosen: Set<String>?, all: List<String>, id: String): Set<String>? {
        val current = chosen?.intersect(all.toSet()) ?: all.toSet()
        val next = if (id in current) current - id else current + id
        return if (next.isEmpty() || next == all.toSet()) null else next
    }

    /** Unnamed favourites are called by their styles, as the menu calls the mandala. */
    fun displayName(favourite: Favorite): String =
        favourite.name.ifEmpty { favourite.parameters.layers.filter { it.isEnabled }.joinToString(", ") { it.style.displayName } }
}
