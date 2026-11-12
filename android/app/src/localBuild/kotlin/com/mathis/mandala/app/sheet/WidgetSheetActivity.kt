package com.mathis.mandala.app.sheet

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.LocaleList
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.mathis.mandala.app.MandalaWidget
import com.mathis.mandala.app.R
import com.mathis.mandala.app.WidgetChoice
import com.mathis.mandala.app.WidgetLook
import com.mathis.mandala.app.WidgetMark
import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.Quote
import com.mathis.mandala.core.Quotes
import com.mathis.mandala.core.SettingsJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Local builds only: draws the home screen widget in the sizes real launchers give it, in both
 * layouts (the square picture and the wide one with the words beside it), checks the layout rules and
 * writes the pictures for `bin/widget-sheet` (Cockpit docs/android-widget-sheet.md).
 *
 * The views are the real ones (`MandalaWidget.views`), applied into a view of exactly the launcher's
 * box. The favourite, the day of the quote of the day and the own quotes come from the assets
 * `widget-sheet*.json`, so the pictures only change when the layout or the built-in quotes change. Text
 * size and language come from a configuration context, never from the device settings; the widget's
 * light or dark look is its own setting (like the app, light, dark), not the phone's night mode.
 * Findings on the layout a box does not show are warnings, so the run fails only on what users see.
 *
 * Extras, each a comma list: layouts (square, wide, empty), data (daily, short, typical, longest, own),
 * texts (system text size in %), locales, looks (APP, LIGHT, DARK), themes (day, night: the
 * configuration's night mode), launchers; `appLight` (true or false, the app's own theme for the look
 * APP, default dark like the app) and `mark` (on draws the local builds' mark, default off as the
 * references want it).
 */
class WidgetSheetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val out = File(getExternalFilesDir(null), "sheet").apply { deleteRecursively(); mkdirs() }
        val extras = intent.extras
        fun list(key: String, default: String) = (extras?.getString(key) ?: default).split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val layouts = list("layouts", "square,wide,empty")
        val data = list("data", "daily,short,typical,longest,own")
        val texts = list("texts", "100").map { it.toInt() }
        val locales = list("locales", "de")
        val looks = list("looks", "LIGHT,DARK")
        val themes = list("themes", "day")
        val launchers = list("launchers", BOXES.map { it.launcher }.distinct().joinToString(","))
        val appLight = extras?.getString("appLight") == "true"
        val mark = extras?.getString("mark") == "on"
        val fixture = Fixture.load(this)

        // Android freezes a cached app in the background, so the work runs while this stays open.
        thread {
            val cases = JSONArray()
            val pictures = Pictures()
            for (box in BOXES.filter { it.launcher in launchers }) {
                pictures.nextBox()
                val wide = MandalaWidget.isWide(box.options())
                // The layout this launcher shows first, then the other one, then the widget without a favourite.
                val order = (if (wide) listOf("wide", "square") else listOf("square", "wide")) + "empty"
                for (layout in order.filter { it in layouts }) {
                    val sets = if (layout == "empty") listOf("none") else data
                    // The square layout is the picture alone, edge to edge: neither the text size nor the
                    // look's card colour shows, so it is drawn once for them.
                    val sizes = if (layout == "square") texts.take(1) else texts
                    val colours = if (layout == "square") looks.take(1) else looks
                    for (set in sets) for (text in sizes) for (locale in locales) for (look in colours) for (theme in themes) {
                        val case = Case(box, layout, layout == (if (wide) "wide" else "square"), set, text, locale, look, theme)
                        cases.put(draw(out, case, fixture, pictures, appLight, mark))
                    }
                }
            }
            File(out, "cases.json").writeText(cases.toString(1))
            File(out, "done").writeText("done")

            // Closed once done, so that the next run starts a fresh one instead of reaching this one.
            runOnUiThread { finish() }
        }
    }

    private fun draw(out: File, case: Case, fixture: Fixture, pictures: Pictures, appLight: Boolean, mark: Boolean): JSONObject {
        val box = case.box
        val config = Configuration(resources.configuration).apply {
            fontScale = case.text / 100f
            setLocales(LocaleList(Locale.forLanguageTag(case.locale)))
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (case.theme == "night") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val context = createConfigurationContext(config)

        // What a fresh phone in this language shows: its own quotes, else the English ones (ADR-0019).
        val languages = Quotes.defaultLanguages(listOf(case.locale))
        val favourite = if (case.layout == "empty") null else fixture.favourite(case.set, case.locale, languages)
        val look = WidgetLook.entries.firstOrNull { it.name == case.look } ?: WidgetLook.APP
        val views = MandalaWidget.views(
            context, favourite, box.options(), WidgetChoice.isLight(look, appLight), wide = case.layout == "wide",
            quoteFor = { MandalaWidget.words(context, it, languages, fixture.day) },
            renderer = pictures::get,
        )
        WidgetMark.onto(views, mark)

        val density = context.resources.displayMetrics.density
        val w = (box.w * density).toInt()
        val h = (box.h * density).toInt()
        val frame = FrameLayout(context)
        val view = views.apply(context, frame)
        frame.addView(view, FrameLayout.LayoutParams(w, h))
        frame.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, w, h)

        // Transparent around the rounded corners, so the sheet's wallpaper shows there.
        val picture = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(picture)
        // The launcher clips the widget to its rounded outline in hardware; a bitmap canvas does not, so the sheet clips here.
        val radius = context.resources.getDimension(R.dimen.widget_radius)
        canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW) })
        frame.draw(canvas)
        val widget = if (case.layout == "empty") "empty" else "${case.layout}-${case.set}"
        val name = "${box.launcher}_${box.cells}_${widget}_t${case.text}_fsys_${case.look.lowercase()}_${case.locale}_${case.theme}"
        File(out, "$name.png").outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
        picture.recycle()

        val aspect = box.reportedW / box.reportedH
        val shown = when {
            case.layout == "empty" -> "empty, no favourite"
            case.picked -> "${case.layout}, the launcher shows this"
            else -> "${case.layout}, not shown in this box"
        }
        // A layout this launcher does not show in this box counts as a warning: it only matters if the
        // wide threshold or the launcher's reported size changes. What the launcher shows has to pass.
        val findings = SheetRules(view, density).check().map {
            if (case.picked || case.layout == "empty" || it.level != "error") it
            else it.copy(level = "warn", what = it.what + " (layout not shown in this box)")
        }
        return JSONObject()
            .put("name", name).put("launcher", box.launcher).put("cells", box.cells)
            .put("widget", if (case.layout == "empty") "empty" else "${case.layout} · ${case.set}")
            .put("w", box.w).put("h", box.h).put("reported", "${box.reportedW.toInt()} × ${box.reportedH.toInt()}")
            .put("step", shown).put("variants", "aspect %.2f, wide above %.1f".format(Locale.ROOT, aspect, MandalaWidget.WIDE_RATIO))
            .put("text", case.text).put("font", "system").put("look", case.look.lowercase()).put("locale", case.locale).put("theme", case.theme)
            .put("findings", JSONArray(findings.map { JSONObject().put("level", it.level).put("rule", it.rule).put("what", it.what) }))
    }

    private data class Case(
        val box: Box, val layout: String, val picked: Boolean, val set: String,
        val text: Int, val locale: String, val look: String, val theme: String,
    )

    /**
     * The mandala pictures, rendered once: the wide layout's picture without words is the same in every
     * box, the square one follows the box's shape and the favourite's words.
     */
    private class Pictures {
        private val square = HashMap<Pair<MandalaParameters, Double>, Bitmap>()
        private val wide = HashMap<Pair<MandalaParameters, Double>, Bitmap>()

        fun nextBox() = square.clear()

        fun get(params: MandalaParameters, aspect: Double): Bitmap {
            val cache = if (aspect == 1.0) wide else square
            return cache.getOrPut(params to aspect) { MandalaWidget.render(params, aspect) }
        }
    }

    /** The fixed favourite (`assets/widget-sheet-favourites.json`), day and own quotes (`assets/widget-sheet.json`). */
    private class Fixture(val day: LocalDate, private val base: Favorite, private val own: Map<String, Quote>) {

        /**
         * The favourite with the words of [set]: none (the quote of the day), or the shortest, the middle
         * or the longest built-in quote in [languages], or a long own quote in the case's language.
         */
        fun favourite(set: String, locale: String, languages: List<String>): Favorite {
            val text = base.parameters.textLayer
            val builtIn = Quotes.collectionsFor(languages).flatMap { it.quotes }.sortedBy { it.text.length }
            val quote = when (set) {
                "short" -> builtIn.first()
                "typical" -> builtIn[builtIn.size / 2]
                "longest" -> builtIn.last()
                "own" -> own[Locale.forLanguageTag(locale).language] ?: own.getValue("en")
                else -> null
            }
            val layer = if (quote == null) text.copy(isEnabled = false, text = "")
            else text.copy(isEnabled = true, text = quote.text, customAuthor = quote.author, showAuthor = quote.author.isNotBlank())
            return base.copy(parameters = base.parameters.copy(textLayer = layer))
        }

        companion object {
            fun load(activity: Activity): Fixture {
                fun asset(name: String) = activity.assets.open(name).bufferedReader().use { it.readText() }
                val json = JSONObject(asset("widget-sheet.json"))
                // Read as the app reads favourites: org.json would round the renderer's 64-bit seeds.
                val favourite = SettingsJson.decodeFavorites(asset("widget-sheet-favourites.json")).first()
                val own = json.getJSONObject("own").let { o ->
                    o.keys().asSequence().associateWith { Quote(o.getJSONObject(it).getString("text"), o.getJSONObject(it).getString("author")) }
                }
                return Fixture(LocalDate.parse(json.getString("day")), favourite, own)
            }
        }
    }

    /** A launcher box in dp, as laid out and as reported (Cockpit docs/android-widget-sheet.md). */
    private data class Box(val launcher: String, val cells: String, val w: Float, val h: Float, val reportedW: Float, val reportedH: Float) {
        /** The options Android hands the widget: in portrait as wide as the reported width and as high as the reported height. */
        fun options() = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, reportedW.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, reportedW.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, reportedH.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, reportedH.toInt())
        }
    }

    private companion object {
        // The measured boxes of the Cockpit table, the most used first: the emulator's Pixel Launcher and
        // Mathis' Nova 5×2. Nova lays out about 8 % less height than it reports.
        val BOXES = listOf(
            Box("pixel-api34", "4x2", 368f, 223f, 368f, 223f),
            // The same box as Mathis saw it on 12 November 2026: the launcher's options gave 1.25 wide over
            // high, so the square picture went into the wide box. Reported width not measured, derived.
            Box("pixel-seen", "4x2", 368f, 223f, 279f, 223f),
            Box("nova-s23", "5x2", 327f, 168f, 327f, 182f),
            Box("nova-s23", "4x2", 258f, 168f, 258f, 182f),
            Box("oneui-s23", "4x2", 376f, 212f, 376f, 212f),
            Box("oneui-tab", "4x2", 481f, 254f, 481f, 254f),
            Box("nova-s23", "3x2", 190f, 168f, 190f, 182f),
            Box("nova-s23", "2x2", 121f, 168f, 121f, 182f),
            Box("nova-s23", "5x3", 327f, 260f, 327f, 282f),
            Box("nova-s23", "5x4", 327f, 351f, 327f, 381f),
            Box("pixel-api34", "2x2", 176f, 223f, 176f, 223f),
            Box("pixel-api34", "4x4", 368f, 462f, 368f, 462f),
            Box("oneui-tab", "2x2", 216f, 254f, 216f, 254f),
            Box("oneui-tab", "4x4", 481f, 552f, 481f, 552f),
        )
    }
}

/** One finding of the layout rules, as the sheet lists it. */
private data class Finding(val level: String, val rule: String, val what: String)

/**
 * The layout rules of the sheet (Cockpit docs/android-widget-sheet.md) on the laid-out widget: cut text,
 * hidden lines, clipped or overlapping views, content near the edge, tiny text and uneven free space.
 * The mandala bleeds to the widget's edge by design, so it counts for neither the edge nor the free
 * space; the words beside it are measured in the room right of the picture.
 */
private class SheetRules(private val root: View, private val density: Float) {
    private val findings = mutableListOf<Finding>()

    // Words that may end in an ellipsis by design: the quote.
    private val sentences = setOf(R.id.widget_quote)

    // Ornaments that sit on purpose at the edge, and the picture that fills its side.
    private val ornaments = setOf(R.id.widget_mark)
    private val bleed = setOf(R.id.widget_image)

    fun check(): List<Finding> {
        val leaves = mutableListOf<Pair<View, Rect>>()
        walk(root, 0, 0, leaves)
        val box = Rect(0, 0, root.width, root.height)

        // The room for the words: the widget less a picture at its left.
        val room = Rect(box)
        for ((view, rect) in leaves) if (view.id in bleed && rect.left <= 0 && rect.right < box.right) room.left = maxOf(room.left, rect.right)

        for ((view, rect) in leaves) {
            val label = name(view)
            if (view is TextView) text(view, label)
            if (!box.contains(rect)) findings += Finding("error", "clipped", "$label reaches past the widget")
            if (view.id !in ornaments && view.id !in bleed &&
                (rect.left - room.left < dp(EDGE) || rect.top < dp(EDGE) || box.right - rect.right < dp(EDGE) || box.bottom - rect.bottom < dp(EDGE))
            ) {
                findings += Finding("error", "edge", "$label is closer than $EDGE dp to the edge")
            }
        }

        // Two texts or icons on top of each other.
        val plain = leaves.filter { it.first.id !in ornaments }
        for (i in plain.indices) for (j in i + 1 until plain.size) {
            if (Rect.intersects(plain[i].second, plain[j].second)) {
                findings += Finding("error", "overlap", "${name(plain[i].first)} and ${name(plain[j].first)} overlap")
            }
        }

        // Free space above and below, left and right of the words, in their room.
        val blocks = leaves.filter { it.first is TextView }.map { it.second }
        if (blocks.isNotEmpty()) {
            val all = Rect(blocks.first()).apply { blocks.forEach { union(it) } }
            if (abs(all.top - room.top - (room.bottom - all.bottom)) > dp(UNEVEN)) {
                findings += Finding("error", "uneven-v", "free space above ${px(all.top - room.top)} dp, below ${px(room.bottom - all.bottom)} dp")
            }
            if (abs(all.left - room.left - (room.right - all.right)) > dp(UNEVEN)) {
                findings += Finding("error", "uneven-h", "free space left ${px(all.left - room.left)} dp, right ${px(room.right - all.right)} dp")
            }
        }
        return findings.distinct()
    }

    private fun text(view: TextView, label: String) {
        if (view.textSize / density < TINY) findings += Finding("error", "tiny", "$label is ${view.textSize / density} dp")
        val layout = view.layout ?: return
        val ellipsized = (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }
        if (ellipsized) {
            findings += if (view.id in sentences) Finding("warn", "shortened", "$label ends in an ellipsis after ${layout.lineCount} lines")
            else Finding("error", "cut", "$label \"${view.text}\" ends in an ellipsis")
        }
        val room = view.height - view.compoundPaddingTop - view.compoundPaddingBottom
        if (layout.height > room + 1) {
            findings += Finding("error", "cut-lines", "$label needs ${px(layout.height)} dp for ${layout.lineCount} lines, has ${px(maxOf(room, 0))}")
        }
        // A one-line view that wraps shows only its first line, without an ellipsis.
        if (view.maxLines == 1 && layout.lineCount > 1 && view.ellipsize == null) {
            findings += Finding("error", "cut", "$label \"${view.text}\" shows only its first line")
        }
    }

    // Visible texts and pictures by their place in the widget.
    private fun walk(view: View, x: Int, y: Int, leaves: MutableList<Pair<View, Rect>>) {
        if (view.visibility != View.VISIBLE) return
        val left = x + view.left
        val top = y + view.top
        val rect = Rect(left, top, left + view.width, top + view.height)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walk(view.getChildAt(i), left, top, leaves)
        } else if (view is TextView || view is ImageView) {
            if ((view as? TextView)?.text?.isEmpty() == true) return
            leaves += view to rect
        }
    }

    private fun name(view: View) = if (view.id == View.NO_ID) view.javaClass.simpleName else view.resources.getResourceEntryName(view.id)
    private fun dp(value: Int) = (value * density).toInt()
    private fun px(value: Int) = (value / density).toInt()

    private companion object {
        const val EDGE = 8
        const val UNEVEN = 4
        const val TINY = 10f
    }
}
