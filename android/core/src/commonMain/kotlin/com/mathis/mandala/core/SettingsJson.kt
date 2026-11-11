package com.mathis.mandala.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

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

    fun decode(text: String): MandalaParameters = json.decodeFromString(ParamsJson, text)

    fun encode(params: MandalaParameters): String = json.encodeToString(ParamsJson, params)

    /** The `formatVersion` a settings object was written with; null before the field existed. */
    fun formatVersion(text: String): Int? =
        (json.parseToJsonElement(text) as? JsonObject)?.get(ParamsJson.VERSION_KEY)?.jsonPrimitive?.intOrNull

    /** A list of settings, as the macOS app's history.json holds it. */
    fun decodeList(text: String): List<MandalaParameters> = json.decodeFromString(paramsListSerializer, text)

    fun encodeList(list: List<MandalaParameters>): String = json.encodeToString(paramsListSerializer, list)

    private val paramsListSerializer = ListSerializer(ParamsJson)

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

/**
 * [MandalaParameters] as the files hold them: the typed fields, this app's `formatVersion`, and the
 * unknown fields of the root object and the six fixed layer objects, carried in
 * [MandalaParameters.extras] so they come back out unchanged (ADR-0003). A typed field always wins
 * over a carried one with the same name; `formatVersion` is not carried, the app writes its own.
 */
object ParamsJson : JsonTransformingSerializer<MandalaParameters>(MandalaParameters.serializer()) {
    /** The version this app writes; a file with a higher one comes from a newer app. */
    const val FORMAT_VERSION = 1
    const val VERSION_KEY = "formatVersion"
    private const val EXTRAS_KEY = "extras"
    private val root = MandalaParameters.serializer().descriptor
    private val known = root.elementNames.toSet()
    private val layerKeys = listOf("baseLayer", "effectsLayer", "graffitiLayer", "drawingLayer", "textLayer", "emblemLayer")
    private val knownInLayer = layerKeys.associateWith { root.getElementDescriptor(root.getElementIndex(it)).elementNames.toSet() }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        val extras = LinkedHashMap<String, JsonElement>()
        element.forEach { (k, v) -> if (k !in known && k != VERSION_KEY) extras[k] = v }
        layerKeys.forEach { layer ->
            val obj = element[layer] as? JsonObject ?: return@forEach
            val unknown = obj.filterKeys { it !in knownInLayer.getValue(layer) }
            if (unknown.isNotEmpty()) extras[layer] = JsonObject(unknown)
        }
        return JsonObject(element.filterKeys { it != EXTRAS_KEY } + (EXTRAS_KEY to JsonObject(extras)))
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        val extras = element[EXTRAS_KEY] as? JsonObject ?: JsonObject(emptyMap())
        val out = LinkedHashMap<String, JsonElement>()
        out[VERSION_KEY] = JsonPrimitive(FORMAT_VERSION)
        element.forEach { (k, v) -> if (k != EXTRAS_KEY) out[k] = v }
        extras.forEach { (k, v) ->
            val typed = out[k]
            when {
                // Inside a layer object the typed fields win; the carried ones fill in around them.
                k in layerKeys && typed is JsonObject && v is JsonObject -> out[k] = JsonObject(v + typed)
                k !in out -> out[k] = v
            }
        }
        return JsonObject(out)
    }
}
