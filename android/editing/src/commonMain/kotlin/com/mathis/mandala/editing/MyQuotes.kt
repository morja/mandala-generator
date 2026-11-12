package com.mathis.mandala.editing

import com.mathis.mandala.core.Quote
import com.mathis.mandala.core.Quotes

/** The user's own quotes, offered next to the built-in ones. Newest first, no duplicates. */
object MyQuotes {
    fun add(list: List<Quote>, quote: Quote): List<Quote> {
        val clean = Quote(quote.text.trim(), quote.author.trim())
        if (clean.text.isEmpty()) return list
        return listOf(clean) + list.filterNot { it.text == clean.text }
    }

    fun remove(list: List<Quote>, quote: Quote): List<Quote> = list.filterNot { it == quote }

    /** Whether the words on the picture (text and author as typed) are among the user's quotes: the bookmark is filled. */
    fun contains(list: List<Quote>, text: String, author: String): Boolean =
        list.any { it.text == text.trim() && it.author == author.trim() }

    /** The bookmark tapped again: the words as they stand leave the user's quotes. */
    fun removeWords(list: List<Quote>, text: String, author: String): List<Quote> =
        list.filterNot { it.text == text.trim() && it.author == author.trim() }
}

/** Which quote languages the quote list shows (ADR-0019), and what "New quote" picks from. */
object QuoteChoice {
    /**
     * The languages switched on: the saved choice where it names known collections, else the device's
     * [preferred] languages that have quotes, then English.
     */
    fun languages(saved: List<String>?, preferred: List<String>): List<String> =
        saved?.filter { it.isNotEmpty() }?.let(Quotes::collectionsFor)?.map { it.language }?.takeIf { it.isNotEmpty() }
            ?: Quotes.defaultLanguages(preferred)

    /** A language switched on or off; null when it would switch off the last one (that one stays on). */
    fun toggled(current: List<String>, language: String): List<String>? {
        val next = if (language in current) current - language else current + language
        return next.takeIf { it.isNotEmpty() }
    }

    /** What "New quote" picks from: the collections switched on and the user's own quotes. */
    fun pool(languages: List<String>, mine: List<Quote>): List<Quote> =
        Quotes.collectionsFor(languages).flatMap { it.quotes } + mine
}
