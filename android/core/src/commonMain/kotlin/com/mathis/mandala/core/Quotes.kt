package com.mathis.mandala.core

import com.mathis.mandala.core.quotes.quotesDe
import com.mathis.mandala.core.quotes.quotesEn

// Quotes offered by the text layer, one collection per language (ADR-0019). The macOS app's original
// collection is not shipped: most of its wordings are modern translations or by living authors
// (ADR-0007). Users add their own in the app.

@kotlinx.serialization.Serializable
data class Quote(val text: String, val author: String = "")

/** Quotes from one edition; [source] names it (work, translator, year) for the public-domain check. */
class QuoteGroup(val source: String, val quotes: List<Quote>)

/** The built-in quotes of one language; [language] is an ISO 639 code, [name] the language's own name. */
class QuoteCollection(val language: String, val name: String, val groups: List<QuoteGroup>) {
    val quotes: List<Quote> = groups.flatMap { it.quotes }
}

object Quotes {
    // Public domain only: authors and translators who died more than 70 years ago (ADR-0007).
    val collections: List<QuoteCollection> = listOf(quotesEn, quotesDe)

    /** Every built-in quote in every language. */
    val all: List<Quote> = collections.flatMap { it.quotes }

    /**
     * The languages offered when the user has not chosen: the device's [preferred] languages (ISO 639
     * codes, most preferred first) that have a collection, then English (ADR-0019).
     */
    fun defaultLanguages(preferred: List<String>): List<String> =
        (preferred.map { it.lowercase() } + "en").distinct().filter { code -> collections.any { it.language == code } }

    /** The collections for [languages], in that order; unknown codes are skipped. */
    fun collectionsFor(languages: List<String>): List<QuoteCollection> =
        languages.mapNotNull { code -> collections.firstOrNull { it.language == code } }

    /** Fonts the macOS app offers for text: PostScript or family name to display name. */
    val fonts: List<Pair<String, String>> = listOf(
        "Zapfino" to "Zapfino",
        "Palatino-Roman" to "Palatino",
        "Didot" to "Didot",
        "Baskerville" to "Baskerville",
        "Georgia" to "Georgia",
        "Optima-Regular" to "Optima",
        "GillSans" to "Gill Sans",
        "Futura-Medium" to "Futura",
        "HoeflerText-Regular" to "Hoefler Text",
        "Copperplate" to "Copperplate",
        "AmericanTypewriter" to "Typewriter",
        "Papyrus" to "Papyrus",
        "TimesNewRomanPSMT" to "Times New Roman",
        "HelveticaNeue" to "Helvetica Neue",
    )

    /** The author of a known quote in any language, matched like the macOS app (same text, or text ending with it). */
    fun authorOf(text: String): String? = all.firstOrNull { it.text == text || text.endsWith(it.text) }?.author
}
