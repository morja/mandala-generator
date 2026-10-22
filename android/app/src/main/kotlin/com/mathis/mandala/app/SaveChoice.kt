package com.mathis.mandala.app

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import com.mathis.mandala.app.Editing.SaveFormat
import com.mathis.mandala.core.Framing
import com.mathis.mandala.core.Image8
import kotlin.math.max
import kotlin.math.min

/** What the save sheet is set to (ADR-0010). Kept across starts as a convenience, not part of the mandala. */
data class SaveChoice(
    val format: SaveFormat = SaveFormat.SCREEN,
    val framing: Framing = Framing(),
    val textFollowsMandala: Boolean = false,
    /** How the mandala sits in the frame; [Fit.HAND] (placed by hand, `framing`) needs Pro. */
    val fit: Fit = Fit.WHOLE,
)

/** How the mandala sits in the frame (ADR-0010): free presets, or placed by hand with Pro. */
enum class Fit {
    /** Centred, fitting the shorter side: the whole mandala shows. */
    WHOLE,
    /** Centred and zoomed until its large layers cover the corners ([com.mathis.mandala.core.MandalaRenderer.fillZoom]). */
    FILL,
    /** Moved and zoomed by hand (Pro): [SaveChoice.framing]. */
    HAND,
}

/** The last save choice, in shared preferences. */
class SaveChoiceStore(context: Context) {
    private val prefs = context.getSharedPreferences("save", Context.MODE_PRIVATE)

    fun load(): SaveChoice {
        val format = prefs.getString(KEY_FORMAT, null)?.let { name -> SaveFormat.entries.firstOrNull { it.name == name } } ?: SaveFormat.SCREEN
        val framing = Framing(
            prefs.getFloat(KEY_CENTRE_X, 0.5f).toDouble(),
            prefs.getFloat(KEY_CENTRE_Y, 0.5f).toDouble(),
            prefs.getFloat(KEY_ZOOM, 1f).toDouble(),
        ).clamped()
        // Before the presets a framing was always by hand; a centred one at zoom 1 is "Show whole".
        val fit = prefs.getString(KEY_FIT, null)?.let { name -> Fit.entries.firstOrNull { it.name == name } }
            ?: if (framing == Framing()) Fit.WHOLE else Fit.HAND
        return SaveChoice(format, framing, prefs.getBoolean(KEY_TEXT_FOLLOWS, false), fit)
    }

    fun save(choice: SaveChoice) = prefs.edit()
        .putString(KEY_FORMAT, choice.format.name)
        .putFloat(KEY_CENTRE_X, choice.framing.centreX.toFloat())
        .putFloat(KEY_CENTRE_Y, choice.framing.centreY.toFloat())
        .putFloat(KEY_ZOOM, choice.framing.zoom.toFloat())
        .putBoolean(KEY_TEXT_FOLLOWS, choice.textFollowsMandala)
        .putString(KEY_FIT, choice.fit.name)
        .apply()

    private companion object {
        const val KEY_FORMAT = "format"
        const val KEY_CENTRE_X = "centre_x"
        const val KEY_CENTRE_Y = "centre_y"
        const val KEY_ZOOM = "zoom"
        const val KEY_TEXT_FOLLOWS = "text_follows"
        const val KEY_FIT = "fit"
    }
}

/** Where "Set as wallpaper" puts the image. */
enum class WallpaperTarget(val label: String, val flags: Int) {
    HOME("Home screen", WallpaperManager.FLAG_SYSTEM),
    LOCK("Lock screen", WallpaperManager.FLAG_LOCK),
    BOTH("Home and lock screen", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
}

object Wallpapers {
    fun set(context: Context, image: Image8, target: WallpaperTarget) {
        val bitmap = image.toBitmap()
        try {
            WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, target.flags)
        } finally {
            bitmap.recycle()
        }
    }
}

/** The device screen's size in pixels, whatever the orientation: (shorter side, longer side). */
fun screenSize(activity: Activity): Pair<Int, Int> {
    val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.windowManager.maximumWindowMetrics.bounds.let { it.width() to it.height() }
    } else {
        @Suppress("DEPRECATION")
        DisplayMetrics().also { activity.windowManager.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
    }
    return min(w, h) to max(w, h)
}

/** The device screen's taller side over its shorter side. */
fun screenAspect(activity: Activity): Double = screenSize(activity).let { (s, l) -> if (s <= 0) 1.0 else l.toDouble() / s }
