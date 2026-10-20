package com.mathis.mandala.app

import android.content.Context

/** Dark is the default (the mandala glows most on it); the light theme is a choice kept across starts. */
class Appearance(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    var light: Boolean
        get() = prefs.getBoolean(KEY_LIGHT, false)
        set(value) = prefs.edit().putBoolean(KEY_LIGHT, value).apply()

    private companion object {
        const val KEY_LIGHT = "light_theme"
    }
}
