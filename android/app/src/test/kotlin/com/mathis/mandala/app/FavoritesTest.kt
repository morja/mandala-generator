package com.mathis.mandala.app

import com.mathis.mandala.editing.Editing
import com.mathis.mandala.editing.Favorites
import com.mathis.mandala.editing.MyQuotes
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
    fun storeRoundTripAndBrokenFile() {
        val file = File(folder.root, "favorites.json")
        val store = FavoritesStore(file)
        assertEquals(emptyList<Favorite>(), store.load())
        store.save(listOf(fav("a"), fav("b")))
        assertEquals(listOf(fav("a"), fav("b")), store.load())
        file.writeText("[{")
        assertEquals(emptyList<Favorite>(), store.load())
    }

    @Test
    fun startersComeOnlyOnTheFirstStart() {
        val store = FavoritesStore(File(folder.root, "favorites.json"))
        assertEquals(listOf("s"), store.loadOrSeed { listOf(fav("s")) }.map { it.id })
        store.save(emptyList()) // the user deleted them
        assertEquals(emptyList<Favorite>(), store.loadOrSeed { listOf(fav("s")) })
    }

    @Test
    fun aFreshInstallOpensOnTheFavouriteOfTheAppIcon() {
        val starters = com.mathis.mandala.core.SettingsJson.decodeFavorites(File("src/main/assets/starter-favorites.json").readText())
        val first = Favorites.firstMandala(starters)

        assertEquals(starters.first { it.id == Favorites.FIRST_MANDALA_ID }.parameters, first)
        // Free to save and share: a new user is not met by the Pro mark.
        assertEquals(emptySet<Any>(), com.mathis.mandala.core.ProFeatures.of(first!!))
        assertEquals(null, Favorites.firstMandala(emptyList()))
    }

    @Test
    fun bundledStartersDecodeAndRenderWithoutWarnings() {
        val text = File("src/main/assets/starter-favorites.json").readText()
        val starters = com.mathis.mandala.core.SettingsJson.decodeFavorites(text)
        assertEquals(starters.size, starters.map { it.id }.distinct().size)
        for (f in starters) {
            val result = com.mathis.mandala.core.MandalaRenderer.render(
                f.parameters,
                com.mathis.mandala.core.RenderOptions(outputSize = 64, threads = 2, textRenderer = NoTextRenderer),
            )
            assertEquals(f.id, emptyList<String>(), result.warnings)
        }
    }

    /** Stands in for the platform's text drawing in JVM tests. */
    private object NoTextRenderer : com.mathis.mandala.core.TextRenderer {
        override fun metrics(font: com.mathis.mandala.core.TextFont) =
            com.mathis.mandala.core.TextRenderer.Metrics(font.sizePx * 0.8f, font.sizePx * 0.2f, 0f)
        override fun width(text: String, font: com.mathis.mandala.core.TextFont) = text.length * font.sizePx * 0.5f
        override fun drawLines(width: Int, height: Int, lines: List<com.mathis.mandala.core.PlacedLine>) = FloatArray(width * height)
    }

    @Test
    fun myQuotesAreTrimmedNewestFirstWithoutDuplicates() {
        val q = com.mathis.mandala.core.Quote("  Be here.  ", " Me ")
        val list = MyQuotes.add(MyQuotes.add(emptyList(), q), com.mathis.mandala.core.Quote("Other", ""))
        assertEquals(listOf("Other", "Be here."), list.map { it.text })
        assertEquals(2, MyQuotes.add(list, com.mathis.mandala.core.Quote("Be here.", "Me")).size)
        assertEquals(list, MyQuotes.add(list, com.mathis.mandala.core.Quote("   ", "x")))
        assertEquals(listOf("Other"), MyQuotes.remove(list, com.mathis.mandala.core.Quote("Be here.", "Me")).map { it.text })
        val store = MyQuotesStore(File(folder.root, "my-quotes.json"))
        store.save(list)
        assertEquals(list, store.load())
    }
}
