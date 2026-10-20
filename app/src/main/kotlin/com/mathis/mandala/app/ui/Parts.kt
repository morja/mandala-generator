package com.mathis.mandala.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import com.mathis.mandala.core.ColorPalette
import com.mathis.mandala.core.LayerBlendMode
import java.util.Locale
import kotlin.math.roundToInt

// Building blocks of the control tabs: foldable groups and one-line sliders, so a tab shows a few
// headings at first and only the open group's sliders take room.

/**
 * A foldable group: a heading row (tap to open or close) with an optional summary of the current
 * value and an optional control on the right, such as an on/off switch.
 */
@Composable
internal fun Group(
    title: String,
    summary: String? = null,
    initiallyOpen: Boolean = false,
    nested: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (open) "Close $title" else "Open $title") { open = !open }
                .semantics { stateDescription = if (open) "Open" else "Closed" }
                .padding(vertical = if (nested) Spacing.xs else Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (open) EditorIcons.Less else EditorIcons.Expand, contentDescription = null, tint = t.textMuted)
            Spacer(Modifier.width(Spacing.sm))
            Text(title, style = if (nested) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall)
            Text(
                summary.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = t.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm),
            )
            trailing?.invoke()
        }
        if (open) Column(Modifier.padding(bottom = Spacing.sm), content = content)
        if (!nested) HorizontalDivider(color = t.outline)
    }
}

/** A labelled on/off row. */
@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun ValueSlider(label: String, value: Double, range: ClosedFloatingPointRange<Float> = 0f..1f, onChange: (Double) -> Unit) {
    SliderRow(label, String.format(Locale.US, "%.2f", value)) {
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toDouble()) }, valueRange = range)
    }
}

@Composable
internal fun SteppedSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    SliderRow(label, value.toString()) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}

/** Label on the left, slider in the middle, value on the right: one line per setting. */
@Composable
private fun SliderRow(label: String, value: String, slider: @Composable () -> Unit) {
    val t = LocalTokens.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.34f),
        )
        Box(Modifier.weight(0.52f)) { slider() }
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = t.textMuted,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.weight(0.14f),
        )
    }
}

/** Hue, saturation and brightness of one colour, as a nested group with the colour as summary dot. */
@Composable
internal fun ColourGroup(
    title: String,
    hue: Double,
    saturation: Double,
    brightness: Double,
    initiallyOpen: Boolean = false,
    onChange: (Double, Double, Double) -> Unit,
) {
    Group(title, nested = true, initiallyOpen = initiallyOpen, trailing = { ColourDot(hsbColour(hue, saturation, brightness)) }) {
        ValueSlider("Hue", hue) { v -> onChange(v, saturation, brightness) }
        ValueSlider("Saturation", saturation) { v -> onChange(hue, v, brightness) }
        ValueSlider("Brightness", brightness) { v -> onChange(hue, saturation, v) }
    }
}

@Composable
internal fun ColourDot(colour: Color) {
    Box(Modifier.size(Sizes.swatchSmall).clip(CircleShape).background(colour))
}

internal fun hsbColour(h: Double, s: Double, b: Double): Color =
    Color.hsv((h * 360).toFloat().coerceIn(0f, 360f), s.toFloat().coerceIn(0f, 1f), b.toFloat().coerceIn(0f, 1f))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlendChips(mode: LayerBlendMode, onChange: (LayerBlendMode) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (m in LayerBlendMode.entries) {
            FilterChip(selected = mode == m, onClick = { onChange(m) }, label = { Text(m.displayName) })
        }
    }
}

@Composable
internal fun Swatch(palette: ColorPalette, selected: Boolean, onClick: () -> Unit) {
    val t = LocalTokens.current
    Box(
        Modifier
            .size(Sizes.swatch)
            .border(Sizes.swatchRing, if (selected) t.accent else Color.Transparent, CircleShape)
            .padding(Sizes.swatchRing * 2)
            .clip(CircleShape)
            .background(paletteBrush(palette))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = palette.name
                this.selected = selected
            },
    )
}

internal fun paletteBrush(palette: ColorPalette): Brush =
    Brush.sweepGradient(palette.stops.map { Color(it.color.r.toFloat(), it.color.g.toFloat(), it.color.b.toFloat(), 1f, ColorSpaces.DisplayP3) })
