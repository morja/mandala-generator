package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Reading the files the macOS app writes: saved settings and its favourites list. */
class MacFilesTest {
    private val favoritesFile = """[{"thumbnailData":"/9j/4AAQ","createdAt":814128061.071701,"id":"DA229425-FF22-424D-B753-055629D7E1D9",
        "name":"","parameters":{"layers":[{"style":"hyperboloid","seed":17463462634133840693}],"seed":42}}]"""

    @Test
    fun favouritesFileIsRecognisedAndRead() {
        val file = assertIs<MacFile.Favorites>(SettingsJson.decodeMacFile(favoritesFile))
        val fav = file.favorites.single()
        assertEquals("DA229425-FF22-424D-B753-055629D7E1D9", fav.id)
        assertEquals(MandalaStyle.HYPERBOLOID, fav.parameters.layers.single().style)
        assertEquals(814128061.071701, fav.createdAt)
        assertEquals("/9j/4AAQ", fav.thumbnailData)
    }

    @Test
    fun favouritesSurviveARoundTrip() {
        val favs = SettingsJson.decodeFavorites(favoritesFile)
        assertEquals(favs, SettingsJson.decodeFavorites(SettingsJson.encodeFavorites(favs)))
    }

    @Test
    fun settingsFileIsRecognised() {
        val file = assertIs<MacFile.Settings>(SettingsJson.decodeMacFile("""{"layers":[{"style":"spirograph"}]}"""))
        assertEquals(MandalaStyle.SPIROGRAPH, file.params.layers.single().style)
    }

    @Test
    fun creationTimeUsesTheSwiftReferenceDate() {
        // 1 January 2001 00:00 UTC is 978307200 s after 1970.
        assertEquals(0.0, Favorite.createdNow(978_307_200_000L))
    }
}
