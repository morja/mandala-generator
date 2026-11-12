package com.mathis.mandala.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.mathis.mandala.core.Quotes
import com.mathis.mandala.core.TextFont
import com.mathis.mandala.core.TextLayerSettings
import com.mathis.mandala.core.TextRenderer
import kotlin.math.max
import kotlin.math.min

/**
 * How the widget's words fit its box (ADR-0020, amended 12 November 2026, QA-9 rule a). Everything is
 * measured with the font and size the views will use, at the current font scale, before the views
 * are made; the result goes into the RemoteViews as the layout choice, `setMaxLines` and a text size.
 *
 * The rules themselves are plain functions on numbers ([column], [lineBudget], [showsWide],
 * [shrink], [pictureFits]) so the unit tests cover them; [WidgetMeasure] does the Android measuring.
 */
object WidgetFit {

    /**
     * Nova lays out about 8 % less height than it reports (Cockpit docs/android-widget-sheet.md); the
     * lines are budgeted for that smaller box, so no launcher cuts the last one.
     */
    const val LAUNCHER_SLACK = 0.92f

    /** Fewer quote lines than this beside the picture read as a fragment: the square picture instead. */
    const val MIN_QUOTE_LINES = 2

    /** Steps of the square picture's text size, from the favourite's own size down to the lower limit. */
    const val SHRINK_STEP = 0.9

    /** The square picture's words go no smaller than this share of the favourite's own size; below, they are left out. */
    const val SHRINK_LIMIT = 0.6

    /** Room the picture's words keep from its edge, as a share of the picture's shorter side. */
    const val PICTURE_MARGIN = 0.04f

    /**
     * Width beside the picture in the wide layout, padding included. The picture is square and as
     * high as the widget, so it takes the widget's height off its width; the reported height is used
     * there (the larger one), so the column is never wider than estimated.
     */
    fun column(widthDp: Float, heightDp: Float): Float = widthDp - heightDp

    /**
     * The words of [text] that must not break inside: runs between spaces, except runs in Chinese or
     * Japanese script, which break between any two characters.
     */
    fun words(text: String): List<String> = text.split(Regex("\\s+")).filter { word ->
        word.isNotEmpty() && word.none {
            val script = Character.UnicodeScript.of(it.code)
            script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA
        }
    }

    /**
     * [text] shortened to end in an ellipsis at [cut], on its last line starting at [lineStart]: after
     * the last whole word before the cut, with trailing punctuation dropped; inside the word only when
     * that word is the line's first.
     */
    fun cutAtWord(text: String, lineStart: Int, cut: Int): String {
        val head = text.substring(0, cut.coerceIn(0, text.length))
        val space = head.indexOfLast { it.isWhitespace() }
        val kept = if (space > lineStart && cut < text.length && !text[cut].isWhitespace()) head.substring(0, space) else head
        return kept.trimEnd().trimEnd(',', ';', ':', '—', '–', '-', '.').trimEnd() + "…"
    }

    /**
     * The most lines out of [totalLines] whose height ([heightOf], n lines ending in an ellipsis
     * where cut) stays within [room]; 0 when not even one line fits.
     */
    fun lineBudget(room: Float, totalLines: Int, heightOf: (Int) -> Float): Int {
        var lines = 0
        while (lines < totalLines && heightOf(lines + 1) <= room) lines++
        return lines
    }

    /**
     * Whether the wide layout keeps the words: a wide box ([wideShape]), a text column of at least
     * [minColumn], at least [MIN_QUOTE_LINES] lines of the quote (all of it when shorter) and no word
     * wider than the column ([wordsFit]), so nothing breaks inside a word. A blank quote needs no room.
     */
    fun showsWide(wideShape: Boolean, columnDp: Float, minColumn: Float, lines: Int, totalLines: Int, wordsFit: Boolean): Boolean {
        if (!wideShape || columnDp < minColumn) return false
        if (totalLines == 0) return true
        return wordsFit && lines >= min(MIN_QUOTE_LINES, totalLines)
    }

    /**
     * The largest factor of the text size, from 1 down in [SHRINK_STEP] steps to [SHRINK_LIMIT], for
     * which [fits] holds; null when it fits at none, and the words are left out.
     */
    fun shrink(fits: (Double) -> Boolean): Double? {
        var factor = 1.0
        while (factor >= SHRINK_LIMIT - 1e-9) {
            if (fits(factor)) return factor
            factor *= SHRINK_STEP
        }
        return null
    }

    /**
     * Whether core's text layer, with its size times [factor], stays inside a [width] × [height]
     * picture with [PICTURE_MARGIN] free around it. Repeats the measuring of core's `TextLayer.layout`
     * for a frame of the whole canvas (the widget renders without `textFollowsMandala`): the same
     * greedy wrap, line heights, gaps and the paddings above quote and author, with the same
     * [renderer] the picture is drawn with. Keep in step with `TextLayer.layout`.
     */
    fun pictureFits(s: TextLayerSettings, width: Int, height: Int, factor: Double, renderer: TextRenderer): Boolean {
        val size = min(width, height)
        val fs = (s.fontSize * factor * size).toFloat()
        val font = TextFont(s.fontName, fs, false, (s.tracking * fs * 0.12).toFloat())
        val maxWidth = size * max(0.05f, s.fontWidth.toFloat())
        val author = if (s.showAuthor) s.customAuthor.ifEmpty { Quotes.authorOf(s.text) ?: "" }.takeIf { it.isNotEmpty() } else null
        val authorFont = font.copy(sizePx = fs * s.authorScale.toFloat(), italic = s.authorItalic)

        val quoteLines = wrap(s.text, font, maxWidth, renderer)
        val authorLines = author?.let { wrap("— $it", authorFont, maxWidth, renderer) } ?: emptyList()
        val quoteH = blockHeight(quoteLines.size, font, renderer)
        val authorH = if (author != null) blockHeight(authorLines.size, authorFont, renderer) else 0f
        val gap = if (author != null) fs * 0.75f else 0f
        val total = quoteH + gap + authorH

        // Core centres the block at offsetY of the height, counted from the bottom; the quote is drawn
        // from 0.3 of the size above the block, the author ends 0.2 of the size above its bottom.
        val bottomUp = s.offsetY.toFloat() * height - total / 2f
        val top = height - (bottomUp + total) - fs * 0.3f
        val bottom = height - bottomUp - fs * 0.2f
        val margin = size * PICTURE_MARGIN
        if (top < margin || bottom > height - margin) return false

        // Lines are centred on the picture; a word wider than the box stays whole and may reach past it.
        val widest = (quoteLines.map { renderer.width(it, font) } + authorLines.map { renderer.width(it, authorFont) }).maxOrNull() ?: 0f
        return widest <= width - 2 * margin
    }

    // Core's greedy wrap: words wider than the box stay whole on their own line.
    private fun wrap(text: String, font: TextFont, maxWidth: Float, renderer: TextRenderer): List<String> {
        val out = ArrayList<String>()
        for (paragraph in text.split('\n')) {
            var line = ""
            for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (renderer.width(candidate, font) <= maxWidth || line.isEmpty()) {
                    line = candidate
                } else {
                    out += line
                    line = word
                }
            }
            out += line
        }
        return out.filter { it.isNotEmpty() }
    }

    private fun blockHeight(lines: Int, font: TextFont, renderer: TextRenderer): Float {
        if (lines == 0) return 0f
        val m = renderer.metrics(font)
        return lines * (m.ascent + m.descent + m.leading) + (lines - 1) * font.sizePx * 0.18f
    }
}

/** What the wide layout shows: whether it keeps the words, and how many lines the quote gets. */
data class WidePlan(val wide: Boolean, val quoteLines: Int, val quote: String = "")

/**
 * The Android side of [WidgetFit]: `StaticLayout` with the paint of the widget's text views (serif,
 * the widget's text sizes from `res/values/widget.xml`, scaled by the context's font scale, the same
 * break strategy, hyphenation and font padding as the layouts) in the box Android reports in the
 * widget's options (portrait: the smallest width and the largest height).
 */
object WidgetMeasure {

    /** The plan of the wide layout for [quote] and [author] (already with its dash) in a box with [options]. */
    fun wide(context: Context, options: Bundle, wideShape: Boolean, quote: String, author: String): WidePlan {
        val res = context.resources
        val density = res.displayMetrics.density
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()
        val padding = res.getDimension(R.dimen.widget_padding)
        val gap = res.getDimension(R.dimen.widget_gap)

        val columnDp = WidgetFit.column(widthDp, heightDp)
        val inner = (columnDp * density - 2 * padding).toInt()
        if (inner <= 0) return WidePlan(false, 0, quote)
        val quotePaint = paint(res.getDimension(R.dimen.widget_quote_size), Typeface.create("serif", Typeface.NORMAL))
        val authorPaint = paint(res.getDimension(R.dimen.widget_author_size), Typeface.create("serif", Typeface.ITALIC))

        // The author keeps its one line (ending in an ellipsis where it is too long); the quote gets the rest.
        val authorHeight = if (author.isBlank()) 0f else layout(author, authorPaint, inner, 1).height + gap
        val room = heightDp * WidgetFit.LAUNCHER_SLACK * density - 2 * padding - authorHeight
        val total = if (quote.isBlank()) 0 else layout(quote, quotePaint, inner, Int.MAX_VALUE).lineCount
        val lines = WidgetFit.lineBudget(room, total) { layout(quote, quotePaint, inner, it).height.toFloat() }

        // Only the words that show have to fit the column whole.
        val shown = if (lines == 0) quote else quote.substring(0, layout(quote, quotePaint, inner, Int.MAX_VALUE).getLineEnd(lines - 1))
        val wordsFit = WidgetFit.words(shown).all { quotePaint.measureText(it) <= inner }

        // A shortened quote ends after its last whole word, not inside one.
        val text = if (lines in 1 until total) {
            val cut = layout(quote, quotePaint, inner, lines)
            val last = lines - 1
            val end = if (cut.getEllipsisCount(last) > 0) cut.getLineStart(last) + cut.getEllipsisStart(last) else cut.getLineEnd(last)
            WidgetFit.cutAtWord(quote, cut.getLineStart(last), end)
        } else {
            quote
        }

        val minColumn = res.getDimension(R.dimen.widget_min_text_column) / density
        return WidePlan(WidgetFit.showsWide(wideShape, columnDp, minColumn, lines, total, wordsFit), lines, text)
    }

    /**
     * The empty widget's text size (px) and line limit: its own size where the text fits the box with
     * every word whole, else smaller in steps down to `widget_min_text_size`, the sheet's smallest
     * legible size.
     */
    fun empty(context: Context, options: Bundle, text: String): Pair<Float, Int> {
        val res = context.resources
        val density = res.displayMetrics.density
        val padding = res.getDimension(R.dimen.widget_padding)
        val width = (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) * density - 2 * padding).toInt()
        val room = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) * WidgetFit.LAUNCHER_SLACK * density - 2 * padding
        val own = res.getDimension(R.dimen.widget_author_size)
        if (width <= 0 || room <= 0) return own to Int.MAX_VALUE
        val smallest = res.getDimension(R.dimen.widget_min_text_size)

        var size = own
        while (true) {
            val face = paint(size, Typeface.DEFAULT)
            val full = layout(text, face, width, Int.MAX_VALUE)
            if (full.height <= room && WidgetFit.words(text).all { face.measureText(it) <= width }) return size to Int.MAX_VALUE
            if (size * WidgetFit.SHRINK_STEP.toFloat() < smallest) {
                // Still too long at the smallest size: as many lines as fit, ending in an ellipsis.
                val lines = WidgetFit.lineBudget(room, full.lineCount) { layout(text, paint(size, Typeface.DEFAULT), width, it).height.toFloat() }
                return size to max(1, lines)
            }
            size *= WidgetFit.SHRINK_STEP.toFloat()
        }
    }

    private fun paint(size: Float, face: Typeface) = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = face
    }

    // As a TextView lays out: font padding, high quality breaks, no hyphens, fallback line spacing.
    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setUseLineSpacingFromFallbacks(true)
            .setMaxLines(maxLines)
            .setEllipsize(if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
            .build()
}
