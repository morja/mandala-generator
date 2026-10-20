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
)

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
        return SaveChoice(format, framing, prefs.getBoolean(KEY_TEXT_FOLLOWS, false))
    }

    fun save(choice: SaveChoice) = prefs.edit()
        .putString(KEY_FORMAT, choice.format.name)
        .putFloat(KEY_CENTRE_X, choice.framing.centreX.toFloat())
        .putFloat(KEY_CENTRE_Y, choice.framing.centreY.toFloat())
        .putFloat(KEY_ZOOM, choice.framing.zoom.toFloat())
        .putBoolean(KEY_TEXT_FOLLOWS, choice.textFollowsMandala)
        .apply()

    private companion object {
        const val KEY_FORMAT = "format"
        const val KEY_CENTRE_X = "centre_x"
        const val KEY_CENTRE_Y = "centre_y"
        const val KEY_ZOOM = "zoom"
        const val KEY_TEXT_FOLLOWS = "text_follows"
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

/** The device screen's taller side over its shorter side, whatever the orientation. */
fun screenAspect(activity: Activity): Double {
    val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.windowManager.maximumWindowMetrics.bounds.let { it.width() to it.height() }
    } else {
        @Suppress("DEPRECATION")
        DisplayMetrics().also { activity.windowManager.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
    }
    return if (min(w, h) <= 0) 1.0 else max(w, h).toDouble() / min(w, h)
}
