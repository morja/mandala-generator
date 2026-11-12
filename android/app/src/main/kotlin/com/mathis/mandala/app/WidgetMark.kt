package com.mathis.mandala.app

import android.content.Context
import android.view.View
import android.widget.RemoteViews

/**
 * Local builds (debug, fast, free) mark the widget with the launcher icon's dot bottom right, so a
 * widget of a locally installed build is told apart (global rule of 5 November 2026). A switch on the
 * menu's Widgets page turns it off for screenshots; on by default. Release builds never show it: their
 * `widget_mark_build` is false and their mark drawable is empty.
 */
object WidgetMark {
    private const val KEY = "widget_mark"

    /** Whether this build marks its widgets at all (the local builds). */
    fun inBuild(context: Context): Boolean = context.resources.getBoolean(R.bool.widget_mark_build)

    fun isOn(context: Context): Boolean = inBuild(context) && prefs(context).getBoolean(KEY, true)

    /** Switches the mark and draws every placed widget again. */
    fun set(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY, on).apply()
        WidgetRedraw.requestAll(context)
    }

    /** Shows or hides the mark on a widget's finished views. */
    fun onto(views: RemoteViews, on: Boolean) = views.setViewVisibility(R.id.widget_mark, if (on) View.VISIBLE else View.GONE)

    private fun prefs(context: Context) = context.getSharedPreferences("widget_debug", Context.MODE_PRIVATE)
}
