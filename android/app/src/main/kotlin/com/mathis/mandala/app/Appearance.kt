package com.mathis.mandala.app

import android.content.Context

/**
 * Dark is the default (the mandala glows most on it); the light theme is a choice kept across starts.
 * So is the fast preview, on by default: its glow is blurred at a smaller size (ADR-0004), and the
 * size of the phone preview above the controls.
 */
class Appearance(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    var light: Boolean
        get() = prefs.getBoolean(KEY_LIGHT, false)
        set(value) = prefs.edit().putBoolean(KEY_LIGHT, value).apply()

    var fastPreview: Boolean
        get() = prefs.getBoolean(KEY_FAST_PREVIEW, true)
        set(value) = prefs.edit().putBoolean(KEY_FAST_PREVIEW, value).apply()

    /** Small pictures beside the names in the style and emblem lists; off, the lists are shorter. */
    var chipPreviews: Boolean
        get() = prefs.getBoolean(KEY_CHIP_PREVIEWS, true)
        set(value) = prefs.edit().putBoolean(KEY_CHIP_PREVIEWS, value).apply()

    /** The preview's side as a fraction of full ([PreviewSize]); an older build kept a name under another key. */
    var previewSize: Float
        get() = prefs.getFloat(KEY_PREVIEW_FRACTION, PreviewSize.FULL).coerceIn(PreviewSize.SMALL, PreviewSize.FULL)
        set(value) = prefs.edit().putFloat(KEY_PREVIEW_FRACTION, value).remove(KEY_PREVIEW_SIZE).apply()

    private companion object {
        const val KEY_LIGHT = "light_theme"
        const val KEY_FAST_PREVIEW = "fast_preview"
        const val KEY_PREVIEW_SIZE = "preview_size"
        const val KEY_PREVIEW_FRACTION = "preview_fraction"
        const val KEY_CHIP_PREVIEWS = "chip_previews"
    }
}
