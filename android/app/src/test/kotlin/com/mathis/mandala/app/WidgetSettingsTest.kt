package com.mathis.mandala.app

import com.mathis.mandala.core.Favorite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings per home screen widget, the choice of its favourite and its colours (ADR-0020, 5 November 2026). */
class WidgetSettingsTest {
    private class MapValues : KeyValues {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
        override fun keys(): Set<String> = map.keys.toSet()
    }

    private fun fav(id: String) = Favorite(id = id, parameters = Editing.initial(1uL), thumbnailData = "abc", createdAt = 1.0)

    @Test
    fun aNewWidgetHasTheDefaults() {
        val store = WidgetSettingsStore(MapValues())
        assertEquals(WidgetSettings(favouriteId = null, look = WidgetLook.APP), store.load(7))
        assertNull(store.shown(7))
    }

    @Test
    fun settingsAreKeptPerWidgetAndRemovedWithIt() {
        val values = MapValues()
        val store = WidgetSettingsStore(values)
        store.save(1, WidgetSettings("a", WidgetLook.DARK))
        store.save(2, WidgetSettings(null, WidgetLook.LIGHT))
        store.setShown(1, "a|dark")
        assertEquals(WidgetSettings("a", WidgetLook.DARK), store.load(1))
        assertEquals(WidgetSettings(null, WidgetLook.LIGHT), store.load(2))

        store.remove(1)
        assertEquals(WidgetSettings(), store.load(1))
        assertNull(store.shown(1))
        assertEquals(WidgetSettings(null, WidgetLook.LIGHT), store.load(2))
        assertTrue(values.map.keys.none { it.startsWith("w.1.") })
    }

    @Test
    fun theDefaultsTakeNoRoomAndUnknownLooksFallBack() {
        val values = MapValues()
        val store = WidgetSettingsStore(values)
        store.save(3, WidgetSettings())
        assertTrue(values.map.isEmpty())
        values.map["w.3.look"] = "SEPIA"
        assertEquals(WidgetLook.APP, store.load(3).look)
    }

    @Test
    fun restoredWidgetsTakeTheirSettingsAlongAndGoneOnesAreFound() {
        val store = WidgetSettingsStore(MapValues())
        store.save(10, WidgetSettings("b", WidgetLook.LIGHT))
        store.move(10, 20)
        assertEquals(WidgetSettings("b", WidgetLook.LIGHT), store.load(20))
        assertEquals(WidgetSettings(), store.load(10))
        store.save(30, WidgetSettings(look = WidgetLook.DARK))
        assertEquals(setOf(30), store.stale(setOf(20)))
    }

    @Test
    fun theDailyTurnIsTheDefault() {
        val daily = fav("daily")
        assertEquals(daily, WidgetChoice.favourite(WidgetSettings(), listOf(fav("a"), daily), daily))
    }

    @Test
    fun aFixedFavouriteWinsOverTheDailyTurn() {
        val fixed = fav("fixed")
        assertEquals(fixed, WidgetChoice.favourite(WidgetSettings("fixed"), listOf(fav("daily"), fixed), fav("daily")))
    }

    @Test
    fun aDeletedFixedFavouriteFallsBackToTheDailyTurn() {
        val daily = fav("daily")
        assertEquals(daily, WidgetChoice.favourite(WidgetSettings("gone"), listOf(daily), daily))
        assertNull(WidgetChoice.favourite(WidgetSettings("gone"), emptyList(), null))
    }

    @Test
    fun likeTheAppFollowsTheAppsOwnSetting() {
        assertTrue(WidgetChoice.isLight(WidgetLook.APP, appLight = true))
        assertFalse(WidgetChoice.isLight(WidgetLook.APP, appLight = false))
        assertTrue(WidgetChoice.isLight(WidgetLook.LIGHT, appLight = false))
        assertFalse(WidgetChoice.isLight(WidgetLook.DARK, appLight = true))
    }

    @Test
    fun aWidgetIsDrawnAgainWhenItsFavouriteOrColoursChange() {
        val a = fav("a")
        assertEquals(WidgetChoice.shownKey(a, true), WidgetChoice.shownKey(fav("a"), true))
        assertNotEquals(WidgetChoice.shownKey(a, true), WidgetChoice.shownKey(a, false))
        assertNotEquals(WidgetChoice.shownKey(a, true), WidgetChoice.shownKey(fav("b"), true))
        assertNotEquals(WidgetChoice.shownKey(a, true), WidgetChoice.shownKey(null, true))
    }

    @Test
    fun aWidgetIsNamedByItsShape() {
        assertEquals(WidgetShape.WIDE, WidgetChoice.shape(320, 150, wide = true))
        assertEquals(WidgetShape.SMALL, WidgetChoice.shape(150, 180, wide = false))
        assertEquals(WidgetShape.LARGE, WidgetChoice.shape(330, 400, wide = false))
    }
}
