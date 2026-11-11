package com.mathis.mandala.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unknown fields of a settings file survive a round trip (ADR-0003, F8 of REV-2). */
class ExtrasRoundTripTest {
    private val fromANewerApp = """
        {"formatVersion":7,"futureKnob":0.5,"seed":42,
         "effectsLayer":{"vignette":0.1,"futureGlow":2},
         "layers":[{"style":"spirograph","futureSpin":1}]}
    """.trimIndent()

    private fun written(params: MandalaParameters) = Json.parseToJsonElement(SettingsJson.encode(params)).jsonObject

    @Test
    fun unknownFieldsOfTheRootAndTheLayerObjectsComeBackOut() {
        val out = written(SettingsJson.decode(fromANewerApp))
        assertEquals(0.5, out.getValue("futureKnob").jsonPrimitive.double)
        val effects = out.getValue("effectsLayer").jsonObject
        assertEquals(2, effects.getValue("futureGlow").jsonPrimitive.int)
        assertEquals(0.1, effects.getValue("vignette").jsonPrimitive.double)
    }

    @Test
    fun typedFieldsWinAndTheVersionIsThisApps() {
        val params = SettingsJson.decode(fromANewerApp)
        val edited = params.copy(effectsLayer = params.effectsLayer.copy(vignette = 0.9))
        val out = written(edited)
        assertEquals(0.9, out.getValue("effectsLayer").jsonObject.getValue("vignette").jsonPrimitive.double)
        assertEquals(2, out.getValue("effectsLayer").jsonObject.getValue("futureGlow").jsonPrimitive.int)
        assertEquals(ParamsJson.FORMAT_VERSION, out.getValue("formatVersion").jsonPrimitive.int)
        assertEquals(7, SettingsJson.formatVersion(fromANewerApp))
        assertNull(SettingsJson.formatVersion("""{"seed":1}"""))
    }

    @Test
    fun unknownFieldsInsideTheLayerListAreNotKept() {
        val out = written(SettingsJson.decode(fromANewerApp))
        assertNull(out.getValue("layers").jsonArray[0].jsonObject["futureSpin"])
    }

    @Test
    fun aFileWithoutUnknownFieldsCarriesNothing() {
        val params = SettingsJson.decode("""{"seed":1}""")
        assertTrue(params.extras.isEmpty())
        assertNull(written(params)["extras"])
        assertTrue(params == MandalaParameters(seed = 1uL))
    }

    @Test
    fun favouritesKeepTheUnknownFieldsOfTheirParameters() {
        val file = """[{"id":"A","parameters":$fromANewerApp,"name":"One"}]"""
        val favourites = SettingsJson.decodeFavorites(file)
        val out = Json.parseToJsonElement(SettingsJson.encodeFavorites(favourites)).jsonArray[0].jsonObject
        assertEquals(0.5, out.getValue("parameters").jsonObject.getValue("futureKnob").jsonPrimitive.double)
    }
}
