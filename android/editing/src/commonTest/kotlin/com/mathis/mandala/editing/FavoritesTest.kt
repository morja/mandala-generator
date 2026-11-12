package com.mathis.mandala.editing

import com.mathis.mandala.core.Favorite
import kotlin.test.Test
import kotlin.test.assertEquals

/** Favourite list rules, newest first as in the macOS app. */
class FavoritesTest {
    private fun fav(id: String) = Favorite(id = id, parameters = Editing.initial(1uL), thumbnailData = "abc", createdAt = 1.0)

    @Test
    fun newestComesFirstAndRemoveWorksById() {
        val list = Favorites.add(Favorites.add(emptyList(), fav("a")), fav("b"))
        assertEquals(listOf("b", "a"), list.map { it.id })
        assertEquals(listOf("a"), Favorites.remove(list, "b").map { it.id })
    }

    @Test
    fun importSkipsFavouritesThatAreAlreadyThere() {
        val (merged, added) = Favorites.merge(listOf(fav("a")), listOf(fav("a"), fav("c"), fav("c")))
        assertEquals(listOf("a", "c"), merged.map { it.id })
        assertEquals(1, added)
    }
}
