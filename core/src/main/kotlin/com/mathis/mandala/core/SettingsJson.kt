package com.mathis.mandala.core

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** What a JSON file from the macOS app contains. */
sealed interface MacFile {
    data class Settings(val params: MandalaParameters) : MacFile
    data class Favorites(val favorites: List<Favorite>) : MacFile
}

/** Reads and writes the settings and favourites JSON shared with the macOS app. */
object SettingsJson {
    private val json = Json {
        ignoreUnknownKeys = true
        // Unknown enum values fall back to the property default, like the Swift decoders.
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun decode(text: String): MandalaParameters = json.decodeFromString(MandalaParameters.serializer(), text)

    fun encode(params: MandalaParameters): String = json.encodeToString(MandalaParameters.serializer(), params)

    private val favoritesSerializer = ListSerializer(Favorite.serializer())

    /** The macOS favourites file (`~/Library/Application Support/MandalaGenerator/favorites.json`). */
    fun decodeFavorites(text: String): List<Favorite> = json.decodeFromString(favoritesSerializer, text)

    fun encodeFavorites(favorites: List<Favorite>): String = json.encodeToString(favoritesSerializer, favorites)

    /** Tells a saved settings file (an object) from the favourites file (a list). */
    fun decodeMacFile(text: String): MacFile = when (json.parseToJsonElement(text)) {
        is JsonArray -> MacFile.Favorites(decodeFavorites(text))
        is JsonObject -> MacFile.Settings(decode(text))
        else -> throw IllegalArgumentException("Neither settings nor favourites")
    }
}
