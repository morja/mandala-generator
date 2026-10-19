package com.mathis.mandala.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The only place for colours, spacing and radii (see AGENTS.md). Calm warm neutrals frame the
// mandala, which brings its own colour; one soft amber accent marks selection and the main action.

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp

    /** Width of the control column next to the canvas on wide screens. */
    val controlsWidth = 360.dp

    /** From this window width on, canvas and controls sit side by side. */
    val twoPaneMinWidth = 600.dp
}

object Radius {
    val sm = 8.dp
    val md = 12.dp
}

object Sizes {
    val swatch = 36.dp
    val swatchRing = 2.dp
    val progress = 3.dp

    /** Room below the canvas for its status line. */
    val statusLine = 24.dp
}

/** One set of semantic colours; light and dark differ only here. */
data class Tokens(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val text: Color,
    val textMuted: Color,
    val outline: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val error: Color,
    /** Behind the rendered image; always dark, because the mandala is drawn as light on black. */
    val canvas: Color,
)

val DarkTokens = Tokens(
    background = Color(0xFF151413),
    surface = Color(0xFF1C1A18),
    surfaceRaised = Color(0xFF262320),
    text = Color(0xFFECE7E1),
    textMuted = Color(0xFFB4ACA3),
    outline = Color(0xFF4A4540),
    accent = Color(0xFFE2A85E),
    onAccent = Color(0xFF241605),
    accentSoft = Color(0xFF3B2E1F),
    error = Color(0xFFF2A39A),
    canvas = Color(0xFF000000),
)

val LightTokens = Tokens(
    background = Color(0xFFF7F4EF),
    surface = Color(0xFFFFFDFA),
    surfaceRaised = Color(0xFFEFEAE3),
    text = Color(0xFF221F1C),
    textMuted = Color(0xFF5F5851),
    outline = Color(0xFFCFC7BD),
    accent = Color(0xFF8A5210),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFF3E3CF),
    error = Color(0xFFB3261E),
    canvas = Color(0xFF000000),
)
