package com.mathis.mandala.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalTokens = staticCompositionLocalOf { DarkTokens }

/** Maps the tokens onto Material 3, so standard components follow them. */
@Composable
fun MandalaTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val t = if (dark) DarkTokens else LightTokens
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val colors = base.copy(
        primary = t.accent,
        onPrimary = t.onAccent,
        primaryContainer = t.accentSoft,
        onPrimaryContainer = t.text,
        secondaryContainer = t.accentSoft,
        onSecondaryContainer = t.text,
        background = t.background,
        onBackground = t.text,
        surface = t.background,
        onSurface = t.text,
        surfaceVariant = t.surfaceRaised,
        onSurfaceVariant = t.textMuted,
        surfaceContainerLowest = t.surface,
        surfaceContainerLow = t.surface,
        surfaceContainer = t.surface,
        surfaceContainerHigh = t.surfaceRaised,
        surfaceContainerHighest = t.surfaceRaised,
        inverseSurface = t.text,
        inverseOnSurface = t.background,
        // Accent on the inverted snackbar: the other theme's accent keeps the contrast.
        inversePrimary = (if (dark) LightTokens else DarkTokens).accent,
        outline = t.outline,
        outlineVariant = t.outline,
        error = t.error,
    )
    val shapes = Shapes(
        small = RoundedCornerShape(Radius.sm),
        medium = RoundedCornerShape(Radius.md),
    )
    CompositionLocalProvider(LocalTokens provides t) {
        MaterialTheme(colorScheme = colors, shapes = shapes, content = content)
    }
}
