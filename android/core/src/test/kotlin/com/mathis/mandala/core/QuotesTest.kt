package com.mathis.mandala.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuotesTest {
    @Test
    fun englishIsAlwaysOfferedAfterTheDeviceLanguages() {
        assertEquals(listOf("de", "en"), Quotes.defaultLanguages(listOf("de", "fr")))
        assertEquals(listOf("en", "de"), Quotes.defaultLanguages(listOf("en", "de")))
        assertEquals(listOf("en"), Quotes.defaultLanguages(listOf("ja")))
    }

    @Test
    fun authorsAreFoundInEveryLanguage() {
        for (c in Quotes.collections) for (q in c.quotes) assertEquals(q.author, Quotes.authorOf(q.text), q.text)
    }

    @Test
    fun everyCollectionHasSourcedQuotesWithAuthorsAndNoDuplicates() {
        assertEquals(Quotes.collections.size, Quotes.collections.map { it.language }.toSet().size)
        for (c in Quotes.collections) {
            assertTrue(c.quotes.isNotEmpty(), c.language)
            assertTrue(c.groups.all { it.source.isNotBlank() }, c.language)
            assertTrue(c.quotes.all { it.author.isNotBlank() }, c.language)
        }
        assertEquals(Quotes.all.size, Quotes.all.map { it.text }.toSet().size)
    }
}
