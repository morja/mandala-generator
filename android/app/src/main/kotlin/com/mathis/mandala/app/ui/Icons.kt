package com.mathis.mandala.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import com.mathis.mandala.app.ui.theme.Sizes

/** The few icons the editor needs, drawn on a 24 x 24 grid (no icon library needed). */
object EditorIcons {
    val MoveUp = icon("M12,4 L4,12 L5.4,13.4 L11,7.8 L11,20 L13,20 L13,7.8 L18.6,13.4 L20,12 Z")
    val MoveDown = icon("M12,20 L20,12 L18.6,10.6 L13,16.2 L13,4 L11,4 L11,16.2 L5.4,10.6 L4,12 Z")
    val Star = icon("M12,17.27 L18.18,21 L16.54,13.97 L22,9.24 L14.81,8.63 L12,2 L9.19,8.63 L2,9.24 L7.46,13.97 L5.82,21 Z")
    val More = icon("M12,8a2,2 0 1,0 0,-4a2,2 0 1,0 0,4zM12,14a2,2 0 1,0 0,-4a2,2 0 1,0 0,4zM12,20a2,2 0 1,0 0,-4a2,2 0 1,0 0,4z")
    val Back = icon("M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z")
    val Forward = icon("M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z")
    /** Dice for everything random: more dots, more change (DM-7). Five is "Surprise me", everything new. */
    val DieOne = line(DIE, soft = DIE, dots = dots(12 to 12))
    val DieThree = line(DIE, soft = DIE, dots = dots(8.3f to 8.3f, 12 to 12, 15.7f to 15.7f))
    val DieFive = line(DIE, soft = DIE, dots = dots(8.3f to 8.3f, 15.7f to 8.3f, 12 to 12, 8.3f to 15.7f, 15.7f to 15.7f))

    // Line icons for the menu sheet: an accent line over a soft fill of the same colour.
    val StarLine = line(STAR, soft = STAR)
    val Share = line("M8.5,11H6a1,1 0 0,0 -1,1v7.5a1,1 0 0,0 1,1h12a1,1 0 0,0 1,-1V12a1,1 0 0,0 -1,-1h-2.5M12,15V3.5M8.5,7L12,3.5 15.5,7",
        soft = "M5,11h14v8.5a1,1 0 0,1 -1,1H6a1,1 0 0,1 -1,-1z")
    val Wallpaper = line("M6,4.5h12a2.5,2.5 0 0,1 2.5,2.5v10a2.5,2.5 0 0,1 -2.5,2.5H6a2.5,2.5 0 0,1 -2.5,-2.5V7A2.5,2.5 0 0,1 6,4.5z" +
        "M12,8.8a3.2,3.2 0 1,0 0,6.4a3.2,3.2 0 1,0 0,-6.4zM12,6.8v1.6M12,15.6v1.6M6.8,12h1.6M15.6,12h1.6",
        soft = "M6,4.5h12a2.5,2.5 0 0,1 2.5,2.5v10a2.5,2.5 0 0,1 -2.5,2.5H6a2.5,2.5 0 0,1 -2.5,-2.5V7A2.5,2.5 0 0,1 6,4.5z")
    val Folder = line("M3.5,7.5V18a1.5,1.5 0 0,0 1.5,1.5h14a1.5,1.5 0 0,0 1.5,-1.5V9.5A1.5,1.5 0 0,0 19,8h-7l-2,-2.5H5A1.5,1.5 0 0,0 3.5,7z",
        soft = "M3.5,9.5h17v8.5a1.5,1.5 0 0,1 -1.5,1.5H5a1.5,1.5 0 0,1 -1.5,-1.5z")
    val FileSave = line("M14,3.5H7A1.5,1.5 0 0,0 5.5,5v14A1.5,1.5 0 0,0 7,20.5h10a1.5,1.5 0 0,0 1.5,-1.5V8zM14,3.5V8h4.5M12,11v6M9.5,14.5L12,17 14.5,14.5",
        soft = "M14,3.5H7A1.5,1.5 0 0,0 5.5,5v14A1.5,1.5 0 0,0 7,20.5h10a1.5,1.5 0 0,0 1.5,-1.5V8z")
    val Bolt = line("M13,3L5,13.5h6L10,21l8,-10.5h-6z", soft = "M13,3L5,13.5h6L10,21l8,-10.5h-6z")
    val Moon = line("M19.5,14.5A8,8 0 0,1 9.5,4.5a8,8 0 1,0 10,10z", soft = "M19.5,14.5A8,8 0 0,1 9.5,4.5a8,8 0 1,0 10,10z")
    val Info = line("M12,3.5a8.5,8.5 0 1,0 0,17a8.5,8.5 0 1,0 0,-17zM12,11v5.5", soft = "M12,3.5a8.5,8.5 0 1,0 0,17a8.5,8.5 0 1,0 0,-17z",
        dots = dots(12 to 7.8f, radius = 0.9f))
    /** The line drawing: a pen. */
    val LineArt = line("M4,20l1.2,-4.6L15.5,5.1a2,2 0 0,1 2.8,0l0.6,0.6a2,2 0 0,1 0,2.8L8.6,18.8zM13.5,7.1l3.4,3.4",
        soft = "M4,20l1.2,-4.6L15.5,5.1a2,2 0 0,1 2.8,0l0.6,0.6a2,2 0 0,1 0,2.8L8.6,18.8z")
    /** "More": two sliders. */
    val Tune = line("M4,7.5h8.5M17.5,7.5h2.5M4,16.5h2.5M11.5,16.5h8.5M15,5a2.5,2.5 0 1,0 0,5a2.5,2.5 0 1,0 0,-5zM9,14a2.5,2.5 0 1,0 0,5a2.5,2.5 0 1,0 0,-5z")
    /** The words card: the quote collection, a new quote (two arrows, not a die: the die is the look), keeping a quote. */
    val QuoteList = line("M5,6.5h14M5,12h14M5,17.5h9")
    val NewQuote = line("M4,8h11M12,5l3,3 -3,3M20,16H9M12,13l-3,3 3,3")
    val Keep = line("M7,4h10v16l-5,-4 -5,4z")
    val Kept = line("M7,4h10v16l-5,-4 -5,4z", soft = "M7,4h10v16l-5,-4 -5,4z")
    /** Save sheet: the whole mandala inside the frame, or the mandala filling it past its edges. */
    val FitWhole = line("M4.5,4.5h15v15h-15zM12,7.5a4.5,4.5 0 1,0 0,9a4.5,4.5 0 1,0 0,-9z")
    val FitFill = line("M4.5,4.5h15v15h-15zM12,2.5a9.5,9.5 0 1,0 0,19a9.5,9.5 0 1,0 0,-19z", soft = "M4.5,4.5h15v15h-15z")
    /** Clear: a bin. */
    val Plus = line("M12,5v14M5,12h14")
    val FromPicture = line("M12,4a8,8 0 1,0 0,16a8,8 0 1,0 0,-16zM12,4v5M12,15v5M4,12h5M15,12h5")
    val Trash = line("M4.5,7h15M9.5,7V4.5h5V7M6.5,7l1,13h9l1,-13M10.5,10.5v6M13.5,10.5v6")
    val Chevron = line("M9,6l6,6 -6,6")
    val ChevronBack = line("M15,6l-6,6 6,6")
    val Arrow = line("M5,12h14M14,7l5,5 -5,5")
    val Check = line("M5,12.5l4.5,4.5L19,7.5")

    val Save = icon("M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z")
    val Expand = icon("M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z")
    val Less = icon("M12,8l-6,6 1.41,1.41L12,10.83l4.59,4.58L18,14z")
    val Close = icon("M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    /** Make the image small / large again (Material "close fullscreen" and "open in full"). */
    val Shrink = icon("M22,3.41l-5.29,5.29L20,12h-8V4l3.29,3.29L20.59,2L22,3.41zM3.41,22l5.29,-5.29L12,20v-8H4l3.29,3.29L2,20.59L3.41,22z")
    val Enlarge = icon("M21,11V3h-8l3.29,3.29l-10,10L3,13v8h8l-3.29,-3.29l10,-10L21,11z")
    val Visible = icon(EYE)
    val Hidden = icon(EYE, strike = true)

    private const val DIE = "M7.5,3.5h9a4,4 0 0,1 4,4v9a4,4 0 0,1 -4,4h-9a4,4 0 0,1 -4,-4v-9a4,4 0 0,1 4,-4z"
    private const val STAR = "M12,3.5l2.6,5.3 5.8,0.8 -4.2,4.1 1,5.8 -5.2,-2.7 -5.2,2.7 1,-5.8 -4.2,-4.1 5.8,-0.8z"

    /** How strongly the soft fill under a line icon shows the icon's colour. */
    private const val SOFT_ALPHA = 0.22f

    private fun dots(vararg centres: Pair<Number, Number>, radius: Float = 1.5f): String = centres.joinToString("") { (x, y) ->
        val cx = x.toFloat()
        val cy = y.toFloat()
        "M${cx - radius},${cy}a$radius,$radius 0 1,0 ${2 * radius},0a$radius,$radius 0 1,0 ${-2 * radius},0z"
    }

    /** An outlined icon: `stroke` as a line, `soft` as a faint fill below it, `dots` filled solid. */
    private fun line(stroke: String, soft: String? = null, dots: String? = null): ImageVector =
        ImageVector.Builder(defaultWidth = Sizes.icon, defaultHeight = Sizes.icon, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                if (soft != null) addPath(pathData = addPathNodes(soft), fill = SolidColor(Color.Black), fillAlpha = SOFT_ALPHA)
                addPath(
                    pathData = addPathNodes(stroke),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                if (dots != null) addPath(pathData = addPathNodes(dots), fill = SolidColor(Color.Black))
            }
            .build()

    private const val EYE = "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 " +
        "-11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 " +
        "3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z"

    private fun icon(path: String, strike: Boolean = false): ImageVector =
        ImageVector.Builder(defaultWidth = Sizes.icon, defaultHeight = Sizes.icon, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .apply {
                if (strike) {
                    addPath(
                        pathData = addPathNodes("M3,3 L21,21"),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2.5f,
                        strokeLineCap = StrokeCap.Round,
                    )
                }
            }
            .build()
}
