package com.mathis.mandala.app

import androidx.annotation.StringRes
import com.mathis.mandala.editing.DrawTool
import com.mathis.mandala.editing.Editing.SaveFormat

// The shared editing module (android/editing) names its choices by enum entry; the app maps each
// entry to its string resource here.

/** The label of a save format in the save sheet. */
@get:StringRes
val SaveFormat.label: Int
    get() = when (this) {
        SaveFormat.SQUARE -> R.string.format_square
        SaveFormat.SCREEN -> R.string.format_screen
        SaveFormat.FOUR_FIVE -> R.string.format_4_5
        SaveFormat.THREE_FOUR -> R.string.format_3_4
        SaveFormat.TWO_THREE -> R.string.format_2_3
        SaveFormat.NINE_SIXTEEN -> R.string.format_9_16
        SaveFormat.FIVE_FOUR -> R.string.format_5_4
        SaveFormat.FOUR_THREE -> R.string.format_4_3
        SaveFormat.THREE_TWO -> R.string.format_3_2
        SaveFormat.SIXTEEN_NINE -> R.string.format_16_9
    }

/** The label of a hand-drawing tool. */
@get:StringRes
val DrawTool.label: Int
    get() = when (this) {
        DrawTool.LINES -> R.string.draw_tool_lines
        DrawTool.SPRAY -> R.string.draw_tool_spray
    }
