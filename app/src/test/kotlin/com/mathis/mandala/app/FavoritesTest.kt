package com.mathis.mandala.app

import com.mathis.mandala.core.Favorite
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FavoritesTest {
    @get:Rule
    val folder = TemporaryFolder()

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

    @Test
    fun storeRoundTripAndBrokenFile() {
        val file = File(folder.root, "favorites.json")
        val store = FavoritesStore(file)
        assertEquals(emptyList<Favorite>(), store.load())
        store.save(listOf(fav("a"), fav("b")))
        assertEquals(listOf(fav("a"), fav("b")), store.load())
        file.writeText("[{")
        assertEquals(emptyList<Favorite>(), store.load())
    }
}
