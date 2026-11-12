package com.mathis.mandala.app

import com.mathis.mandala.core.PlacedLine
import com.mathis.mandala.core.TextFont
import com.mathis.mandala.core.TextLayerSettings
import com.mathis.mandala.core.TextRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The widget's fitting rules (ADR-0020, QA-9 rule a), on plain numbers. */
class WidgetFitTest {

    @Test
    fun theColumnIsTheWidthBesideASquarePictureAsHighAsTheWidget() {
        // Nova 5×2 and 4×2 as reported, Pixel 4×2 as laid out.
        assertEquals(145f, WidgetFit.column(327f, 182f))
        assertEquals(76f, WidgetFit.column(258f, 182f))
        assertEquals(145f, WidgetFit.column(368f, 223f))
    }

    @Test
    fun wordsAreRunsBetweenSpacesExceptInChineseAndJapanese() {
        assertEquals(listOf("Clouds", "come", "floating"), WidgetFit.words(" Clouds  come\nfloating "))
        assertEquals(emptyList<String>(), WidgetFit.words("アプリでお気に入りを追加すると"))
        assertEquals(listOf("Добавьте", "мандалу"), WidgetFit.words("Добавьте мандалу"))
    }

    @Test
    fun aShortenedQuoteEndsAfterItsLastWholeWord() {
        val text = "Hope is the thing with feathers that perches"
        // The ellipsis would fall inside "that", on the line starting at "feathers".
        assertEquals("Hope is the thing with feathers…", WidgetFit.cutAtWord(text, 23, 33))
        // A cut right after a word keeps it; trailing punctuation goes.
        assertEquals("One, two…", WidgetFit.cutAtWord("One, two, three", 0, 9))
        // The line's only word is cut inside rather than dropped.
        assertEquals("Wonder…", WidgetFit.cutAtWord("Wonderful things", 0, 6))
    }

    @Test
    fun theQuoteGetsAsManyLinesAsFitAndNoMoreThanItHas() {
        val lineHeight = { n: Int -> n * 20f + 4f }
        assertEquals(4, WidgetFit.lineBudget(90f, 6, lineHeight))
        assertEquals(3, WidgetFit.lineBudget(90f, 3, lineHeight))
        assertEquals(0, WidgetFit.lineBudget(20f, 6, lineHeight))
        assertEquals(0, WidgetFit.lineBudget(90f, 0, lineHeight))
    }

    @Test
    fun theWideLayoutNeedsAWideBoxAColumnTwoLinesAndWholeWords() {
        assertTrue(WidgetFit.showsWide(true, 145f, 140f, lines = 3, totalLines = 5, wordsFit = true))
        assertFalse("square box", WidgetFit.showsWide(false, 145f, 140f, 3, 5, true))
        assertFalse("narrow column, Nova 4×2", WidgetFit.showsWide(true, 76f, 140f, 3, 5, true))
        assertFalse("one line of a longer quote", WidgetFit.showsWide(true, 145f, 140f, 1, 5, true))
        assertTrue("a one-line quote shown whole", WidgetFit.showsWide(true, 145f, 140f, 1, 1, true))
        assertFalse("a word wider than the column", WidgetFit.showsWide(true, 145f, 140f, 3, 5, false))
        assertTrue("no words at all", WidgetFit.showsWide(true, 145f, 140f, 0, 0, true))
    }

    @Test
    fun shrinkingStopsAtTheFirstSizeThatFitsAndGivesUpBelowTheLimit() {
        assertEquals(1.0, WidgetFit.shrink { true }!!, 1e-9)
        assertEquals(0.81, WidgetFit.shrink { it < 0.85 }!!, 1e-9)
        assertNull(WidgetFit.shrink { it < WidgetFit.SHRINK_LIMIT - 0.01 })
    }

    /** Every character half the size wide; lines as high as the size. */
    private object Fake : TextRenderer {
        override fun metrics(font: TextFont) = TextRenderer.Metrics(font.sizePx * 0.8f, font.sizePx * 0.2f, 0f)
        override fun width(text: String, font: TextFont) = text.length * font.sizePx * 0.5f
        override fun drawLines(width: Int, height: Int, lines: List<PlacedLine>) = FloatArray(width * height)
    }

    private val text = TextLayerSettings(isEnabled = true, text = "Short words", customAuthor = "Someone", fontSize = 0.07, offsetY = 0.5)

    @Test
    fun aShortQuoteFitsThePictureAtItsOwnSize() {
        assertTrue(WidgetFit.pictureFits(text, 512, 512, 1.0, Fake))
    }

    @Test
    fun aLongQuoteFitsOnlySmallerOrNotAtAll() {
        val long = text.copy(text = List(60) { "word" }.joinToString(" "))
        assertFalse(WidgetFit.pictureFits(long, 512, 400, 1.0, Fake))
        val factor = WidgetFit.shrink { WidgetFit.pictureFits(long, 512, 400, it, Fake) }
        assertTrue(factor == null || factor < 1.0)
        val endless = text.copy(text = List(400) { "word" }.joinToString(" "))
        assertNull(WidgetFit.shrink { WidgetFit.pictureFits(endless, 512, 400, it, Fake) })
    }

    @Test
    fun aWordWiderThanThePictureDoesNotFit() {
        val word = text.copy(text = "x".repeat(40), fontWidth = 0.5)
        assertFalse(WidgetFit.pictureFits(word, 512, 512, 1.0, Fake))
    }

    @Test
    fun wordsNearTheBottomDoNotFit() {
        assertFalse(WidgetFit.pictureFits(text.copy(offsetY = 0.02), 512, 512, 1.0, Fake))
    }
}
