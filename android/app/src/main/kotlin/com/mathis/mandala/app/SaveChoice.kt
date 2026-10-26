package com.mathis.mandala.app

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mathis.mandala.app.Editing.SaveFormat
import com.mathis.mandala.core.Finish
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
    /** Pro: brightness, contrast and saturation of the saved image or wallpaper (ADR-0014). */
    val finish: Finish = Finish(),
    /** Pro, the wallpaper pair: the home screen's brightness relative to the lock screen (ADR-0014). */
    val homeBrightness: Double = DEFAULT_HOME_BRIGHTNESS,
    /** How the mandala sat in the other formats, so each keeps its own; `fit` and `framing` are this format's. */
    val placements: Map<SaveFormat, Placement> = emptyMap(),
) {
    /** The same choice in another format, with the placement that format had last ("Whole" the first time). */
    fun withFormat(next: SaveFormat): SaveChoice {
        if (next == format) return this
        val kept = placements + (format to Placement(fit, framing))
        val placement = kept[next] ?: Placement()
        return copy(format = next, fit = placement.fit, framing = placement.framing, placements = kept - next)
    }

    companion object {
        /** Darker than the lock screen, so the icons on the home screen stay readable. */
        const val DEFAULT_HOME_BRIGHTNESS = 0.6
        const val MIN_HOME_BRIGHTNESS = 0.2
    }
}

/** How the mandala sits in one format: a preset, or placed by hand. */
data class Placement(val fit: Fit = Fit.WHOLE, val framing: Framing = Framing())

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
        val finish = Finish(
            prefs.getFloat(KEY_BRIGHTNESS, 1f).toDouble(),
            prefs.getFloat(KEY_CONTRAST, 1f).toDouble(),
            prefs.getFloat(KEY_SATURATION, 1f).toDouble(),
        ).clamped()
        val home = prefs.getFloat(KEY_HOME_BRIGHTNESS, SaveChoice.DEFAULT_HOME_BRIGHTNESS.toFloat()).toDouble().coerceIn(SaveChoice.MIN_HOME_BRIGHTNESS, 1.0)
        val placements = SaveFormat.entries.filter { it != format && prefs.contains(KEY_FIT + "_" + it.name) }.associateWith { f ->
            val name = prefs.getString(KEY_FIT + "_" + f.name, null)
            Placement(
                Fit.entries.firstOrNull { it.name == name } ?: Fit.WHOLE,
                Framing(
                    prefs.getFloat(KEY_CENTRE_X + "_" + f.name, 0.5f).toDouble(),
                    prefs.getFloat(KEY_CENTRE_Y + "_" + f.name, 0.5f).toDouble(),
                    prefs.getFloat(KEY_ZOOM + "_" + f.name, 1f).toDouble(),
                ).clamped(),
            )
        }
        return SaveChoice(format, framing, prefs.getBoolean(KEY_TEXT_FOLLOWS, false), fit, finish, home, placements)
    }

    fun save(choice: SaveChoice) = prefs.edit()
        .apply {
            for ((f, p) in choice.placements) {
                putString(KEY_FIT + "_" + f.name, p.fit.name)
                putFloat(KEY_CENTRE_X + "_" + f.name, p.framing.centreX.toFloat())
                putFloat(KEY_CENTRE_Y + "_" + f.name, p.framing.centreY.toFloat())
                putFloat(KEY_ZOOM + "_" + f.name, p.framing.zoom.toFloat())
            }
        }
        .putString(KEY_FORMAT, choice.format.name)
        .putFloat(KEY_CENTRE_X, choice.framing.centreX.toFloat())
        .putFloat(KEY_CENTRE_Y, choice.framing.centreY.toFloat())
        .putFloat(KEY_ZOOM, choice.framing.zoom.toFloat())
        .putBoolean(KEY_TEXT_FOLLOWS, choice.textFollowsMandala)
        .putString(KEY_FIT, choice.fit.name)
        .putFloat(KEY_BRIGHTNESS, choice.finish.brightness.toFloat())
        .putFloat(KEY_CONTRAST, choice.finish.contrast.toFloat())
        .putFloat(KEY_SATURATION, choice.finish.saturation.toFloat())
        .putFloat(KEY_HOME_BRIGHTNESS, choice.homeBrightness.toFloat())
        .apply()

    private companion object {
        const val KEY_FORMAT = "format"
        const val KEY_CENTRE_X = "centre_x"
        const val KEY_CENTRE_Y = "centre_y"
        const val KEY_ZOOM = "zoom"
        const val KEY_TEXT_FOLLOWS = "text_follows"
        const val KEY_FIT = "fit"
        const val KEY_BRIGHTNESS = "finish_brightness"
        const val KEY_CONTRAST = "finish_contrast"
        const val KEY_SATURATION = "finish_saturation"
        const val KEY_HOME_BRIGHTNESS = "home_brightness"
    }
}

/** Where "Set as wallpaper" puts the image. */
enum class WallpaperTarget(@StringRes val label: Int, val flags: Int) {
    HOME(R.string.wallpaper_target_home, WallpaperManager.FLAG_SYSTEM),
    LOCK(R.string.wallpaper_target_lock, WallpaperManager.FLAG_LOCK),
    BOTH(R.string.wallpaper_target_both, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
}

/** The menu entry for the live wallpaper (ADR-0014), marked when it opens the Pro sheet. */
@Composable
fun liveLabel(pro: Boolean): String = stringResource(if (pro) R.string.wallpaper_live else R.string.wallpaper_live_pro)

/** The menu entry for the wallpaper pair (ADR-0014), marked when it opens the Pro sheet. */
@Composable
fun pairLabel(pro: Boolean): String = stringResource(if (pro) R.string.wallpaper_pair else R.string.wallpaper_pair_pro)

object Wallpapers {
    fun set(context: Context, image: Image8, target: WallpaperTarget) = set(context, image, target.flags)

    /** The pair (ADR-0014): one image on the lock screen, another on the home screen. */
    fun setPair(context: Context, lock: Image8, home: Image8) {
        set(context, lock, WallpaperManager.FLAG_LOCK)
        set(context, home, WallpaperManager.FLAG_SYSTEM)
    }

    private fun set(context: Context, image: Image8, flags: Int) {
        val bitmap = image.toBitmap()
        try {
            WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, flags)
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
