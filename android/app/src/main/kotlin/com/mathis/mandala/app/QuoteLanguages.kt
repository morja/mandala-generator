package com.mathis.mandala.app

import android.content.Context
import android.os.LocaleList
import com.mathis.mandala.core.Quotes

/**
 * The quote languages switched on in the quote list, kept across starts. Until the user chooses, the
 * device's preferred languages that have quotes, then English (ADR-0019).
 */
class QuoteLanguages(context: Context) {
    private val prefs = context.getSharedPreferences("quotes", Context.MODE_PRIVATE)

    var chosen: List<String>
        get() = prefs.getString(KEY_LANGUAGES, null)?.split(",")?.filter { it.isNotEmpty() }?.let(Quotes::collectionsFor)?.map { it.language }
            ?.takeIf { it.isNotEmpty() } ?: Quotes.defaultLanguages(deviceLanguages())
        set(value) = prefs.edit().putString(KEY_LANGUAGES, value.joinToString(",")).apply()

    private fun deviceLanguages(): List<String> = LocaleList.getDefault().let { list -> (0 until list.size()).map { list[it].language } }

    private companion object {
        const val KEY_LANGUAGES = "languages"
    }
}
