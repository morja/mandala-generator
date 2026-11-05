package com.mathis.mandala.app

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Asks for widgets to be drawn again (ADR-0020, amended 5 November 2026). The ids wait in a list
 * that survives the process; one [WidgetRenderJob] at a time works through it. A broadcast has about
 * ten seconds, too little for several 512 px pictures, so the drawing never happens in one.
 */
object WidgetRedraw {
    private const val JOB_ID = 2020
    private const val KEY_PENDING = "pending"
    private const val TAG = "MandalaWidget"

    private val lock = Any()

    /** True while a job of this process works through the list; it then picks up new ids itself. */
    private var running = false

    /** Comparing what the widgets show reads the favourites, so it runs off the caller's thread. */
    private val worker = Executors.newSingleThreadExecutor()

    fun request(context: Context, ids: Collection<Int>) {
        if (ids.isEmpty()) return
        val app = context.applicationContext
        synchronized(lock) {
            val prefs = prefs(app)
            prefs.edit().putStringSet(KEY_PENDING, pending(app) + ids.map(Int::toString)).apply()
            if (!running) schedule(app)
        }
    }

    fun requestAll(context: Context) = request(context, MandalaWidget.ids(context).toList())

    /**
     * Draws again only the widgets whose favourite or colours changed: after a favourite was deleted,
     * the daily choice or the app's theme changed, or the starter favourites were written on the
     * first start. Also forgets settings of widgets that are gone.
     */
    fun requestChanged(context: Context) {
        val app = context.applicationContext
        worker.execute {
            runCatching {
                val placed = MandalaWidget.ids(app)
                val store = WidgetSettingsStore.of(app)
                store.stale(placed.toSet()).forEach(store::remove)
                if (placed.isEmpty()) return@runCatching
                val inputs = MandalaWidget.Inputs.load(app)
                val changed = placed.filter { id ->
                    val (favourite, light) = MandalaWidget.choice(app, id, inputs)
                    store.shown(id) != WidgetChoice.shownKey(favourite, light)
                }
                request(app, changed)
            }.onFailure { Log.w(TAG, "Could not compare the widgets", it) }
        }
    }

    /** The next widget to draw, taken off the list; null (and the job ends) when the list is empty. */
    internal fun take(context: Context): Int? = synchronized(lock) {
        val ids = pending(context)
        val next = ids.firstNotNullOfOrNull { it.toIntOrNull() }
        prefs(context).edit().putStringSet(KEY_PENDING, if (next == null) emptySet() else ids - next.toString()).apply()
        if (next == null) running = false
        next
    }

    internal fun started() = synchronized(lock) { running = true }

    /** Android stopped the job: the widget it was drawing goes back on the list. */
    internal fun stopped(context: Context, current: Int?) {
        synchronized(lock) {
            running = false
            if (current != null) prefs(context).edit().putStringSet(KEY_PENDING, pending(context) + current.toString()).apply()
        }
    }

    private fun pending(context: Context): Set<String> = prefs(context).getStringSet(KEY_PENDING, null)?.toSet() ?: emptySet()

    /** Expedited from Android 12 on, so a widget fills at once also when the app has not been used for a while. */
    private fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val component = ComponentName(context, WidgetRenderJob::class.java)
        val expedited = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching {
            scheduler.schedule(JobInfo.Builder(JOB_ID, component).setExpedited(true).build()) == JobScheduler.RESULT_SUCCESS
        }.getOrDefault(false)
        if (!expedited) scheduler.schedule(JobInfo.Builder(JOB_ID, component).setOverrideDeadline(0).build())
    }

    private fun prefs(context: Context) = context.getSharedPreferences("widget_redraw", Context.MODE_PRIVATE)
}

/**
 * Draws the waiting widgets one after another, each with its own try/catch: a widget is updated as
 * soon as its picture is ready, and one that fails is logged without stopping the others.
 *
 * Android may start a new run while an older one is still being stopped (a redraw asked for in the
 * moment between the start and [onStartJob]); both share this service, so each run keeps its own
 * scope, and a lock lets only one of them draw at a time.
 */
class WidgetRenderJob : JobService() {
    private class Run(val scope: CoroutineScope) {
        @Volatile
        var current: Int? = null
    }

    private val runs = ConcurrentHashMap<JobParameters, Run>()

    override fun onStartJob(params: JobParameters): Boolean {
        WidgetRedraw.started()
        val run = Run(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        runs[params] = run
        run.scope.launch {
            val app = applicationContext
            val manager = AppWidgetManager.getInstance(app)
            drawing.withLock {
                val start = SystemClock.elapsedRealtime()
                var count = 0
                while (isActive) {
                    val id = WidgetRedraw.take(app) ?: break
                    run.current = id
                    val began = SystemClock.elapsedRealtime()
                    try {
                        MandalaWidget.draw(app, manager, id, MandalaWidget.Inputs.load(app))
                        count++
                        Log.i(TAG, "Widget $id drawn in ${SystemClock.elapsedRealtime() - began} ms")
                    } catch (e: Throwable) {
                        Log.w(TAG, "Widget $id could not be drawn", e)
                    }
                    run.current = null
                }
                if (count > 0) Log.i(TAG, "Drew $count widget(s) in ${SystemClock.elapsedRealtime() - start} ms")
            }
            runCatching { DailyWallpaper.ensureScheduled(app) }
            if (runs.remove(params) != null) jobFinished(params, false)
        }
        return true
    }

    /** Android stopped the run; the widget it was drawing and the rest are drawn when it runs again. */
    override fun onStopJob(params: JobParameters): Boolean {
        val run = runs.remove(params) ?: return false
        run.scope.cancel()
        WidgetRedraw.stopped(applicationContext, run.current)
        return true
    }

    private companion object {
        const val TAG = "MandalaWidget"

        /** One run draws at a time, in the whole process. */
        val drawing = Mutex()
    }
}
