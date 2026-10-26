package com.mathis.mandala.app

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.Finish
import com.mathis.mandala.core.Framing
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.RenderOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.min

/**
 * A new wallpaper every morning (ADR-0014, Pro): the favourites in turn, filled to the screen and
 * set as the pair, lock screen with the save sheet's finish, home screen darker. The same morning
 * run moves the home screen widget on to the next favourite (ADR-0020), also while the daily
 * wallpaper itself is off.
 */
object DailyWallpaper {
    private const val JOB_ID = 2014

    /** The job may start from this hour on, preferably while charging, and at the latest at [LATEST]. */
    private val EARLIEST: LocalTime = LocalTime.of(4, 0)
    private val LATEST: LocalTime = LocalTime.of(8, 0)

    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)

    /**
     * The favourites that take turns, by id; null means all of them. A favourite added later joins
     * only when chosen (UX-1, 13).
     */
    fun chosen(context: Context): Set<String>? = prefs(context).getStringSet(KEY_CHOSEN, null)?.toSet()

    fun setChosen(context: Context, ids: Set<String>?) {
        prefs(context).edit().apply { if (ids == null) remove(KEY_CHOSEN) else putStringSet(KEY_CHOSEN, ids) }.apply()
    }

    /**
     * The favourite whose turn it is and the next turn counter: the chosen ones in their order, or all
     * when none is chosen or every chosen one has been deleted.
     */
    internal fun turn(favorites: List<Favorite>, chosen: Set<String>?, next: Int): Pair<Favorite, Int>? {
        val pool = chosen?.let { ids -> favorites.filter { it.id in ids } }?.ifEmpty { null } ?: favorites
        if (pool.isEmpty()) return null
        val index = next.mod(pool.size)
        return pool[index] to index + 1
    }

    /**
     * The choice after tapping one favourite in the picker. "All" shows every favourite ticked, so the
     * first tap leaves out the tapped one; leaving out the last one, or ticking every one, means all again.
     */
    fun toggled(chosen: Set<String>?, all: List<String>, id: String): Set<String>? {
        val current = chosen?.intersect(all.toSet()) ?: all.toSet()
        val next = if (id in current) current - id else current + id
        return if (next.isEmpty() || next == all.toSet()) null else next
    }

    /** Name of the favourite set last, for the menu. */
    fun lastName(context: Context): String? = prefs(context).getString(KEY_LAST_NAME, null)

    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
        if (on) schedule(context) else cancelIfIdle(context)
    }

    /** The morning run is needed while the daily wallpaper is on or a widget is on the home screen, with Pro. */
    private fun needed(context: Context): Boolean =
        ProPurchases.ownedOffline(context) && (isOn(context) || MandalaWidget.isPlaced(context))

    /**
     * Plans the run again when it is needed but none is waiting: Android drops planned jobs when the
     * app is force-stopped or reinstalled, and a backup brings the switch back without its job.
     */
    fun ensureScheduled(context: Context) {
        if (needed(context) && context.getSystemService(JobScheduler::class.java).getPendingJob(JOB_ID) == null) schedule(context)
    }

    /** Stops the morning run once neither the daily wallpaper nor a widget needs it. */
    fun cancelIfIdle(context: Context) {
        if (!needed(context)) context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }

    /**
     * Today's favourite, for the widget: the one whose turn it was last, or the first of the chosen
     * ones before the first run. Without Pro it stays the first one (ADR-0020).
     */
    fun today(context: Context): Favorite? {
        val favorites = FavoritesStore(File(context.filesDir, "favorites.json")).load()
        val last = prefs(context).getString(KEY_LAST_ID, null)
        if (ProPurchases.ownedOffline(context)) favorites.firstOrNull { it.id == last }?.let { return it }
        return turn(favorites, chosen(context), 0)?.first
    }

    /** Moves the turn on to the next favourite and remembers it; null when there is none. */
    fun advance(context: Context): Favorite? {
        val favorites = FavoritesStore(File(context.filesDir, "favorites.json")).load()
        val prefs = prefs(context)
        val (favourite, nextTurn) = turn(favorites, chosen(context), prefs.getInt(KEY_NEXT, 0)) ?: return null
        prefs.edit().putInt(KEY_NEXT, nextTurn).putString(KEY_LAST_NAME, displayName(favourite)).putString(KEY_LAST_ID, favourite.id).apply()
        return favourite
    }

    /** Unnamed favourites are called by their styles, as the menu calls the mandala. */
    private fun displayName(favourite: Favorite): String =
        favourite.name.ifEmpty { favourite.parameters.layers.filter { it.isEnabled }.joinToString(", ") { it.style.displayName } }

    /** Plans the next run for tomorrow morning (or this morning, before 4 o'clock). */
    fun schedule(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val (earliest, latest) = nextWindow(now).let { (a, b) -> a.toMillis() to b.toMillis() }
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, DailyWallpaperJob::class.java))
            .setMinimumLatency(earliest)
            .setOverrideDeadline(latest)
            .setRequiresCharging(true)
            .setPersisted(true)
            .build()
        context.getSystemService(JobScheduler::class.java).schedule(job)
    }

    /** From now: how long until the next morning's window opens, and until it closes. */
    internal fun nextWindow(now: LocalDateTime): Pair<Duration, Duration> {
        val day = if (now.toLocalTime().isBefore(EARLIEST)) now.toLocalDate() else now.toLocalDate().plusDays(1)
        return Duration.between(now, LocalDateTime.of(day, EARLIEST)) to Duration.between(now, LocalDateTime.of(day, LATEST))
    }

    /** After Android stopped a run, tries again in a quarter of an hour, charging or not. */
    fun retrySoon(context: Context) {
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, DailyWallpaperJob::class.java))
            .setMinimumLatency(RETRY_MS)
            .setOverrideDeadline(RETRY_MS * 4)
            .setPersisted(true)
            .build()
        context.getSystemService(JobScheduler::class.java).schedule(job)
    }

    /**
     * Renders the next favourite and sets it as the pair; returns its name, or null when there is
     * nothing to set (no favourites, no Pro). Takes 10 to 20 seconds on a phone.
     */
    fun setNext(context: Context, threads: Int = Runtime.getRuntime().availableProcessors()): String? {
        if (!ProPurchases.ownedOffline(context)) return null
        val favourite = advance(context) ?: return null
        val params: MandalaParameters = favourite.parameters
        val name = displayName(favourite)
        if (LiveWallpaper.isActive(context)) {
            // The live wallpaper fades over to the next favourite itself; a still image would replace it.
            LiveWallpaper.setMandala(context, params)
            return name
        }
        val choice = SaveChoiceStore(context).load()
        val (short, long) = screenSize(context)
        val aspect = if (short <= 0) 1.0 else long.toDouble() / short
        val (w, h) = Editing.exportSize(Editing.SaveFormat.SCREEN, aspect, large = true)
        val zoom = MandalaRenderer.fillZoom(params, Editing.aspect(Editing.SaveFormat.SCREEN, aspect), threads)
        val lock = MandalaRenderer.render(
            params,
            RenderOptions(
                width = w, height = h, framing = Framing(zoom = zoom), supersample = false, threads = threads,
                textRenderer = AndroidTextRenderer(), imageLoader = AndroidImageLoader(), finish = choice.finish,
            ),
        ).image
        val home = Finish(brightness = choice.homeBrightness).apply(lock, threads)
        Wallpapers.setPair(context, lock, home)
        return name
    }

    private fun screenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            @Suppress("DEPRECATION")
            DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
        }
        return min(w, h) to max(w, h)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("daily", Context.MODE_PRIVATE)

    private const val KEY_ON = "on"
    private const val KEY_NEXT = "next"
    private const val KEY_LAST_NAME = "last_name"
    private const val KEY_CHOSEN = "chosen"
    private const val KEY_LAST_ID = "last_id"
    private const val RETRY_MS = 15 * 60 * 1000L
}

/** Runs [DailyWallpaper.setNext] (or only moves the turn on, for the widget) in the morning and plans the next day. */
class DailyWallpaperJob : JobService() {
    private var scope: CoroutineScope? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val wallpaper = DailyWallpaper.isOn(this) && ProPurchases.ownedOffline(this)
        val widget = MandalaWidget.isPlaced(this) && ProPurchases.ownedOffline(this)
        if (!wallpaper && !widget) return false
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scope = it }
        s.launch {
            runCatching { if (wallpaper) DailyWallpaper.setNext(applicationContext) else DailyWallpaper.advance(applicationContext) }
            runCatching { MandalaWidget.refresh(applicationContext) }
            DailyWallpaper.schedule(applicationContext)
            jobFinished(params, false)
        }
        return true
    }

    /** Android stopped the job (charger pulled, for example); it runs again soon. */
    override fun onStopJob(params: JobParameters): Boolean {
        scope?.cancel()
        DailyWallpaper.retrySoon(applicationContext)
        return false
    }
}
