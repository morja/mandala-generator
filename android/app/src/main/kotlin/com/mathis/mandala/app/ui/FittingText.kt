package com.mathis.mandala.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.isSpecified

// One rule for rows of labels that must stay on one line without scrolling sideways (IOS-5 finding 8,
// QA-14): all labels of a row share one text role. When one of them does not fit, all take the next
// smaller role together; when even the smallest role does not fit (large text sizes), they shrink
// together instead of running into each other or wrapping, but never below [minimum] at the normal
// text size (the theme's smallest role, so they stay as readable as the smallest text anywhere).
// An ellipsis stays the last resort.

/**
 * The first of [roles] in which the one-line widths of [labels] (in px, at the current font scale)
 * pass [fits] for the [space] (in px) the row has; otherwise the last role scaled down until they do.
 */
@Composable
internal fun fittingStyle(
    labels: List<String>,
    roles: List<TextStyle>,
    space: Float,
    minimum: TextStyle = MaterialTheme.typography.labelSmall,
    fits: (widths: List<Float>, space: Float) -> Boolean,
): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    fun widths(style: TextStyle) = labels.map { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width.toFloat() }
    val passes = { widths: List<Float> -> fits(widths, space) }
    return remember(labels, roles, space, minimum, measurer, density) {
        // The smallest size at font scale 1, where sp and dp are the same.
        val floor = minimum.fontSize.value * density.density
        roles.firstOrNull { passes(widths(it)) } ?: shrunk(roles.last(), floor, density, ::widths, passes)
    }
}

/** [style] at the largest scale, in small steps, at which [fits] passes, but not below [floor] px. */
private fun shrunk(style: TextStyle, floor: Float, density: Density, widths: (TextStyle) -> List<Float>, fits: (List<Float>) -> Boolean): TextStyle {
    var scaled = style
    var scale = 1f
    while (true) {
        val next = scale - SCALE_STEP
        val candidate = scaledBy(style, next)
        if (next <= 0f || with(density) { candidate.fontSize.toPx() } < floor) return scaled
        scale = next
        scaled = candidate
        if (fits(widths(scaled))) return scaled
    }
}

private const val SCALE_STEP = 0.025f

private fun scaledBy(style: TextStyle, scale: Float) = style.copy(
    fontSize = style.fontSize * scale,
    lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight,
    letterSpacing = if (style.letterSpacing.isSpecified) style.letterSpacing * scale else style.letterSpacing,
)
