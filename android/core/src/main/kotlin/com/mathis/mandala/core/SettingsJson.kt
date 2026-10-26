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

    /** A list of settings, as the macOS app's history.json holds it. */
    fun decodeList(text: String): List<MandalaParameters> = json.decodeFromString(paramsListSerializer, text)

    fun encodeList(list: List<MandalaParameters>): String = json.encodeToString(paramsListSerializer, list)

    private val paramsListSerializer = ListSerializer(MandalaParameters.serializer())

    private val favoritesSerializer = ListSerializer(Favorite.serializer())
    private val quotesSerializer = ListSerializer(Quote.serializer())
    private val palettesSerializer = ListSerializer(CustomPalette.serializer())

    /** Own palettes, in the macOS app's `palettes.json` format (ADR-0021). */
    fun decodePalettes(text: String): List<CustomPalette> = json.decodeFromString(palettesSerializer, text)

    fun encodePalettes(palettes: List<CustomPalette>): String = json.encodeToString(palettesSerializer, palettes)

    /** The user's own quotes (Android only; the macOS app has none). */
    fun decodeQuotes(text: String): List<Quote> = json.decodeFromString(quotesSerializer, text)

    fun encodeQuotes(quotes: List<Quote>): String = json.encodeToString(quotesSerializer, quotes)

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
