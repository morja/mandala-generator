package com.mathis.mandala.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import com.mathis.mandala.app.ui.theme.Sizes

/** The few icons the editor needs, drawn on a 24 x 24 grid (no icon library needed). */
object EditorIcons {
    val MoveUp = icon("M12,4 L4,12 L5.4,13.4 L11,7.8 L11,20 L13,20 L13,7.8 L18.6,13.4 L20,12 Z")
    val MoveDown = icon("M12,20 L20,12 L18.6,10.6 L13,16.2 L13,4 L11,4 L11,16.2 L5.4,10.6 L4,12 Z")
    val Star = icon("M12,17.27 L18.18,21 L16.54,13.97 L22,9.24 L14.81,8.63 L12,2 L9.19,8.63 L2,9.24 L7.46,13.97 L5.82,21 Z")
    val More = icon("M12,8a2,2 0 1,0 0,-4a2,2 0 1,0 0,4zM12,14a2,2 0 1,0 0,-4a2,2 0 1,0 0,4zM12,20a2,2 0 1,0 0,-4a2,2 0 1,0 0,4z")
    val Visible = icon(EYE)
    val Hidden = icon(EYE, strike = true)

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
