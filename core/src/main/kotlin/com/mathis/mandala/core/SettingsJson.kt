package com.mathis.mandala.core

import kotlinx.serialization.json.Json

/** Reads and writes the settings JSON shared with the macOS app. */
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
}
