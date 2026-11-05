package com.mathis.mandala.app

import android.content.Context
import android.content.SharedPreferences
import com.mathis.mandala.core.Favorite

/** How the menu and the settings page name a widget: launchers do not tell their cells, so by shape and size. */
enum class WidgetShape { SMALL, WIDE, LARGE }

/** The widget's colours (ADR-0020): like the app's own light or dark setting, or always one of them. */
enum class WidgetLook { APP, LIGHT, DARK }

/**
 * One placed widget's settings: a fixed favourite by id, or null for the daily turn (ADR-0020,
 * amended 5 November 2026), and its colours.
 */
data class WidgetSettings(val favouriteId: String? = null, val look: WidgetLook = WidgetLook.APP)

/** A few strings by key, so the store can be tested without Android. */
interface KeyValues {
    fun get(key: String): String?
    fun put(key: String, value: String?)
    fun keys(): Set<String>
}

class SharedKeyValues(private val prefs: SharedPreferences) : KeyValues {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
    override fun keys(): Set<String> = prefs.all.keys
}

/**
 * The settings of every placed widget by its `appWidgetId`, and what each one shows now, so a change
 * elsewhere (a deleted favourite, the app's theme) redraws only the widgets it affects.
 */
class WidgetSettingsStore(private val values: KeyValues) {

    fun load(id: Int): WidgetSettings = WidgetSettings(
        favouriteId = values.get(key(id, FAVOURITE)),
        look = values.get(key(id, LOOK))?.let { name -> WidgetLook.entries.firstOrNull { it.name == name } } ?: WidgetLook.APP,
    )

    fun save(id: Int, settings: WidgetSettings) {
        values.put(key(id, FAVOURITE), settings.favouriteId)
        values.put(key(id, LOOK), settings.look.takeIf { it != WidgetLook.APP }?.name)
    }

    /** What the widget was last drawn with ([shownKey]); null before its first picture. */
    fun shown(id: Int): String? = values.get(key(id, SHOWN))

    fun setShown(id: Int, shown: String?) = values.put(key(id, SHOWN), shown)

    /** Forgets a widget taken off the home screen. */
    fun remove(id: Int) {
        for (field in listOf(FAVOURITE, LOOK, SHOWN)) values.put(key(id, field), null)
    }

    /** Moves the settings to new ids after Android restored widgets from a backup. */
    fun move(from: Int, to: Int) {
        if (from == to) return
        save(to, load(from))
        remove(from)
    }

    /** Ids with settings or a picture that are no longer placed, e.g. after a launcher was cleared. */
    fun stale(placed: Set<Int>): Set<Int> =
        values.keys().mapNotNull { it.removePrefix(PREFIX).substringBefore('.').toIntOrNull() }.filterTo(HashSet()) { it !in placed }

    companion object {
        private const val PREFIX = "w."
        private const val FAVOURITE = "favourite"
        private const val LOOK = "look"
        private const val SHOWN = "shown"

        private fun key(id: Int, field: String) = "$PREFIX$id.$field"

        fun of(context: Context) =
            WidgetSettingsStore(SharedKeyValues(context.getSharedPreferences("widgets", Context.MODE_PRIVATE)))
    }
}

/** Rules for one widget: which favourite, which colours, how many cells. Plain Kotlin, tested. */
object WidgetChoice {
    /** The fixed favourite while it exists; otherwise (daily turn, or the fixed one deleted) the daily turn's. */
    fun favourite(settings: WidgetSettings, favorites: List<Favorite>, dailyTurn: Favorite?): Favorite? =
        settings.favouriteId?.let { id -> favorites.firstOrNull { it.id == id } } ?: dailyTurn

    /** Whether the widget is light; "like the app" follows the app's own setting, not the phone's. */
    fun isLight(look: WidgetLook, appLight: Boolean): Boolean = when (look) {
        WidgetLook.APP -> appLight
        WidgetLook.LIGHT -> true
        WidgetLook.DARK -> false
    }

    /** What decides the widget's picture: its favourite and colours. Drawn again when this changes. */
    fun shownKey(favourite: Favorite?, light: Boolean): String = (favourite?.id ?: "-") + if (light) "|light" else "|dark"

    /** Wide when it gets the words beside the picture; otherwise small below [LARGE_DP] on its shorter side. */
    fun shape(widthDp: Int, heightDp: Int, wide: Boolean): WidgetShape = when {
        wide -> WidgetShape.WIDE
        minOf(widthDp, heightDp) < LARGE_DP -> WidgetShape.SMALL
        else -> WidgetShape.LARGE
    }

    /** A 2×2 widget is about 150 to 180 dp on its shorter side, a 3×3 or 4×4 one over 240 dp. */
    private const val LARGE_DP = 200
}
