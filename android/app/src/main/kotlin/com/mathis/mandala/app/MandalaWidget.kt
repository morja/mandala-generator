package com.mathis.mandala.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.Quote
import com.mathis.mandala.core.Quotes
import com.mathis.mandala.core.RenderOptions
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * "Mandala of the day" on the home screen (ADR-0020): today's favourite of the daily wallpaper's
 * turn. Square sizes (2×2 up to 4×4) show the picture alone; a wide one (4×2) shows the picture
 * without its text and the words beside it. A tap opens that favourite in the editor. With Pro it
 * moves on every morning with the daily wallpaper's run; without Pro it stays on the first one.
 * Plain RemoteViews, so no new package (Glance would be one).
 */
class MandalaWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = drawAsync(context, ids)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        drawAsync(context, intArrayOf(id))

    override fun onEnabled(context: Context) = DailyWallpaper.ensureScheduled(context)

    override fun onDisabled(context: Context) = DailyWallpaper.cancelIfIdle(context)

    /** Rendering takes a moment, so it runs off the main thread while the broadcast is kept open. */
    private fun drawAsync(context: Context, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        worker.execute {
            try {
                draw(app, ids)
                DailyWallpaper.ensureScheduled(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_FAVOURITE_ID = "com.mathis.mandala.app.favourite_id"

        /** Side of the rendered picture: sharp on a 4×4 widget, small enough for a RemoteViews bitmap. */
        private const val SIDE = 512

        /** Wider than this (width over height) counts as the wide layout with words. */
        private const val WIDE_RATIO = 1.3

        private val worker = Executors.newSingleThreadExecutor()

        fun isPlaced(context: Context): Boolean = ids(context).isNotEmpty()

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, MandalaWidget::class.java))

        /** Draws every placed widget again, after the morning run or a new choice; call off the main thread. */
        fun refresh(context: Context) {
            val ids = ids(context)
            if (ids.isNotEmpty()) draw(context.applicationContext, ids)
        }

        private fun draw(context: Context, ids: IntArray) {
            val manager = AppWidgetManager.getInstance(context)
            val favourite = DailyWallpaper.today(context)
            for (id in ids) {
                val views = if (favourite == null) empty(context) else picture(context, favourite, isWide(manager.getAppWidgetOptions(id)))
                manager.updateAppWidget(id, views)
            }
        }

        private fun isWide(options: Bundle): Boolean {
            // In portrait the widget is as wide as its smallest width and as high as its largest height.
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            return height > 0 && width.toDouble() / height > WIDE_RATIO
        }

        private fun empty(context: Context) = RemoteViews(context.packageName, R.layout.widget_empty).apply {
            setOnClickPendingIntent(R.id.widget_root, open(context, null))
        }

        private fun picture(context: Context, favourite: Favorite, wide: Boolean): RemoteViews {
            val params = favourite.parameters
            return if (!wide) {
                RemoteViews(context.packageName, R.layout.widget_square).apply {
                    setImageViewBitmap(R.id.widget_image, render(params))
                    setOnClickPendingIntent(R.id.widget_root, open(context, favourite.id))
                }
            } else {
                val words = words(context, params)
                RemoteViews(context.packageName, R.layout.widget_wide).apply {
                    // The words stand beside the picture, so the picture goes without them.
                    setImageViewBitmap(R.id.widget_image, render(params.copy(textLayer = params.textLayer.copy(isEnabled = false))))
                    setTextViewText(R.id.widget_quote, words.text)
                    setTextViewText(R.id.widget_author, if (words.author.isBlank()) "" else "— " + words.author)
                    setViewVisibility(R.id.widget_author, if (words.author.isBlank()) View.GONE else View.VISIBLE)
                    setOnClickPendingIntent(R.id.widget_root, open(context, favourite.id))
                }
            }
        }

        private fun render(params: MandalaParameters) = MandalaRenderer.render(
            params,
            RenderOptions(outputSize = SIDE, supersample = false, threads = Runtime.getRuntime().availableProcessors(),
                textRenderer = AndroidTextRenderer(), imageLoader = AndroidImageLoader()),
        ).image.toBitmap()

        /** The favourite's own words; without any, a quote of the day in the chosen quote languages. */
        internal fun words(context: Context, params: MandalaParameters): Quote {
            val text = params.textLayer
            if (text.text.isNotBlank()) return Quote(text.text, if (text.showAuthor) text.customAuthor else "")
            return quoteOfTheDay(Quotes.collectionsFor(QuoteLanguages(context).chosen).flatMap { it.quotes }, LocalDate.now())
        }

        /** The same quote all day, a different one the next day. */
        internal fun quoteOfTheDay(quotes: List<Quote>, day: LocalDate): Quote =
            if (quotes.isEmpty()) Quote("") else quotes[day.toEpochDay().mod(quotes.size)]

        private fun open(context: Context, favouriteId: String?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { if (favouriteId != null) putExtra(EXTRA_FAVOURITE_ID, favouriteId) }
            return PendingIntent.getActivity(context, favouriteId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
