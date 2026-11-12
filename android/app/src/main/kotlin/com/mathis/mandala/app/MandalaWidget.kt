package com.mathis.mandala.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.ContextCompat
import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.Quote
import com.mathis.mandala.core.Quotes
import com.mathis.mandala.core.RenderOptions
import java.io.File
import java.time.LocalDate

/**
 * "Mandala of the day" on the home screen (ADR-0020): the daily wallpaper's turn or a fixed favourite,
 * per widget, in the app's colours or always light or dark. Square sizes (2×2 up to 4×4) show the
 * picture alone; a wide one (4×2) shows the picture without its text and the words beside it. A tap
 * opens the app as it was and offers that favourite. The receiver only asks for a redraw;
 * [WidgetRenderJob] draws each widget on its own. Plain RemoteViews, so no new package.
 */
class MandalaWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetRedraw.request(context, ids.toList())

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        WidgetRedraw.request(context, listOf(id))

    override fun onDeleted(context: Context, ids: IntArray) {
        val store = WidgetSettingsStore.of(context)
        ids.forEach(store::remove)
    }

    /** Android gave restored widgets new ids; their settings move along. */
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        val store = WidgetSettingsStore.of(context)
        oldIds.zip(newIds).forEach { (from, to) -> store.move(from, to) }
        WidgetRedraw.request(context, newIds.toList())
    }

    override fun onEnabled(context: Context) = DailyWallpaper.ensureScheduled(context)

    override fun onDisabled(context: Context) = DailyWallpaper.cancelIfIdle(context)

    /** What every widget needs to pick its favourite and colours; read once per widget, so a change during a run counts. */
    class Inputs(val favorites: List<Favorite>, val dailyTurn: Favorite?, val appLight: Boolean) {
        companion object {
            fun load(context: Context): Inputs {
                val favorites = FavoritesStore(File(context.filesDir, "favorites.json")).load()
                return Inputs(favorites, DailyWallpaper.today(context, favorites), Appearance(context).light)
            }
        }
    }

    /** The widget's colours from `res/values/widget.xml`, set on the views since the phone's night mode no longer decides. */
    private class Colours(context: Context, val light: Boolean) {
        val background = if (light) R.drawable.widget_background_light else R.drawable.widget_background_dark
        val text = ContextCompat.getColor(context, if (light) R.color.widget_text_light else R.color.widget_text_dark)
        val muted = ContextCompat.getColor(context, if (light) R.color.widget_muted_light else R.color.widget_muted_dark)
    }

    companion object {
        const val EXTRA_FAVOURITE_ID = "com.mathis.mandala.app.favourite_id"

        private const val TAG = "MandalaWidget"

        /** Longer side of the rendered picture: sharp on a 4×4 widget, small enough for a RemoteViews bitmap. */
        private const val SIDE = 512

        /** Wider than this (width over height) counts as the wide layout with words. */
        internal const val WIDE_RATIO = 1.3

        fun isPlaced(context: Context): Boolean = ids(context).isNotEmpty()

        fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, MandalaWidget::class.java))

        /** Draws every placed widget again, after the morning run or a new choice. */
        fun refresh(context: Context) = WidgetRedraw.requestAll(context)

        /** The favourite and colours a widget shows with the current settings. */
        fun choice(context: Context, id: Int, inputs: Inputs): Pair<Favorite?, Boolean> {
            val settings = WidgetSettingsStore.of(context).load(id)
            return WidgetChoice.favourite(settings, inputs.favorites, inputs.dailyTurn) to WidgetChoice.isLight(settings.look, inputs.appLight)
        }

        /**
         * Draws one widget and remembers what it shows. A picture that cannot be rendered falls back to
         * the favourite's thumbnail; the empty state only shows when there is no favourite at all.
         */
        internal fun draw(context: Context, manager: AppWidgetManager, id: Int, inputs: Inputs) {
            val (favourite, light) = choice(context, id, inputs)
            val colours = Colours(context, light)
            val views = if (favourite == null) {
                empty(context, id, colours)
            } else {
                try {
                    picture(context, id, favourite, manager.getAppWidgetOptions(id), colours)
                } catch (e: Exception) {
                    Log.w(TAG, "Widget $id: rendering failed, showing the thumbnail", e)
                    thumbnail(context, id, favourite, colours) ?: return
                } catch (e: OutOfMemoryError) {
                    Log.w(TAG, "Widget $id: out of memory, showing the thumbnail", e)
                    thumbnail(context, id, favourite, colours) ?: return
                }
            }
            // The one place the local builds' mark goes on, after the picture is drawn.
            WidgetMark.onto(views, WidgetMark.isOn(context))
            manager.updateAppWidget(id, views)
            WidgetSettingsStore.of(context).setShown(id, WidgetChoice.shownKey(favourite, light))
        }

        /** Height over width of the placed widget; in portrait it is as wide as its smallest width and as high as its largest height. */
        fun aspect(options: Bundle): Double {
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            return if (width > 0 && height > 0) height.toDouble() / width else 1.0
        }

        /** Whether the widget is wide enough for the words beside the picture. */
        fun isWide(options: Bundle): Boolean = 1 / aspect(options) > WIDE_RATIO

        /** Small, wide or large, as the widget stands in portrait. */
        fun shape(options: Bundle): WidgetShape = WidgetChoice.shape(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH), options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT), isWide(options))

        /** The widget's name in the menu and on its settings page. */
        fun title(shape: WidgetShape): Int = when (shape) {
            WidgetShape.SMALL -> R.string.widget_title_small
            WidgetShape.WIDE -> R.string.widget_title_wide
            WidgetShape.LARGE -> R.string.widget_title_large
        }

        /**
         * The views a widget with [favourite] shows in a box with [options], in the [wide] or square
         * layout, for the widget sheet of the local builds (`bin/widget-sheet`), which draws both
         * layouts in every launcher size with fixed words and pictures. Without the mark.
         */
        internal fun views(
            context: Context,
            favourite: Favorite?,
            options: Bundle,
            light: Boolean,
            wide: Boolean,
            quoteFor: (MandalaParameters) -> Quote,
            renderer: (MandalaParameters, Double) -> android.graphics.Bitmap,
        ): RemoteViews {
            val colours = Colours(context, light)
            return if (favourite == null) empty(context, 0, colours) else picture(context, 0, favourite, options, colours, wide, quoteFor, renderer)
        }

        private fun empty(context: Context, id: Int, colours: Colours) = RemoteViews(context.packageName, R.layout.widget_empty).apply {
            setInt(R.id.widget_root, "setBackgroundResource", colours.background)
            setTextColor(R.id.widget_empty_text, colours.muted)
            setOnClickPendingIntent(R.id.widget_root, open(context, id, null))
        }

        private fun picture(
            context: Context,
            id: Int,
            favourite: Favorite,
            options: Bundle,
            colours: Colours,
            wide: Boolean = isWide(options),
            quoteFor: (MandalaParameters) -> Quote = { words(context, it) },
            renderer: (MandalaParameters, Double) -> android.graphics.Bitmap = ::render,
        ): RemoteViews {
            val params = favourite.parameters
            return if (!wide) {
                square(context, id, favourite, colours, renderer(params, aspect(options)))
            } else {
                val words = quoteFor(params)
                RemoteViews(context.packageName, R.layout.widget_wide).apply {
                    setInt(R.id.widget_root, "setBackgroundResource", colours.background)
                    // The words stand beside the picture, so the picture goes without them.
                    setImageViewBitmap(R.id.widget_image, renderer(params.copy(textLayer = params.textLayer.copy(isEnabled = false)), 1.0))
                    setTextViewText(R.id.widget_quote, words.text)
                    setTextColor(R.id.widget_quote, colours.text)
                    setTextViewText(R.id.widget_author, if (words.author.isBlank()) "" else "— " + words.author)
                    setTextColor(R.id.widget_author, colours.muted)
                    setViewVisibility(R.id.widget_author, if (words.author.isBlank()) View.GONE else View.VISIBLE)
                    setOnClickPendingIntent(R.id.widget_root, open(context, id, favourite.id))
                }
            }
        }

        // Launcher cells are rarely square: the whole mandala in the widget's own shape, the background going on.
        private fun square(context: Context, id: Int, favourite: Favorite, colours: Colours, image: android.graphics.Bitmap) =
            RemoteViews(context.packageName, R.layout.widget_square).apply {
                setInt(R.id.widget_root, "setBackgroundResource", colours.background)
                setImageViewBitmap(R.id.widget_image, image)
                setOnClickPendingIntent(R.id.widget_root, open(context, id, favourite.id))
            }

        /** The favourite's stored 128 px thumbnail, when the full picture cannot be made. */
        private fun thumbnail(context: Context, id: Int, favourite: Favorite, colours: Colours): RemoteViews? =
            Thumbnails.decode(favourite.thumbnailData)?.let { square(context, id, favourite, colours, it.asAndroidBitmap()) }

        /** The picture with `aspect` as height over width, its longer side [SIDE]. */
        internal fun render(params: MandalaParameters, aspect: Double): android.graphics.Bitmap {
            val (w, h) = if (aspect >= 1) (SIDE / aspect).toInt() to SIDE else SIDE to (SIDE * aspect).toInt()
            return MandalaRenderer.render(
                params,
                RenderOptions(width = w, height = h, supersample = false, threads = Runtime.getRuntime().availableProcessors(),
                    textRenderer = AndroidTextRenderer(), imageLoader = AndroidImageLoader()),
            ).image.toBitmap()
        }

        /** The favourite's own words; without any, a quote of the day in the chosen quote languages. */
        internal fun words(
            context: Context,
            params: MandalaParameters,
            languages: List<String> = QuoteLanguages(context).chosen,
            day: LocalDate = LocalDate.now(),
        ): Quote {
            val text = params.textLayer
            if (text.text.isNotBlank()) return Quote(text.text, if (text.showAuthor) text.customAuthor else "")
            return quoteOfTheDay(Quotes.collectionsFor(languages).flatMap { it.quotes }, day)
        }

        /** The same quote all day, a different one the next day. */
        internal fun quoteOfTheDay(quotes: List<Quote>, day: LocalDate): Quote =
            if (quotes.isEmpty()) Quote("") else quotes[day.toEpochDay().mod(quotes.size)]

        /** Opens the app as it was; the favourite only comes as an offer (ADR-0020, amended 5 November 2026). */
        private fun open(context: Context, id: Int, favouriteId: String?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { if (favouriteId != null) putExtra(EXTRA_FAVOURITE_ID, favouriteId) }
            // One request code per widget, so two widgets with different favourites keep their own extras.
            return PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
