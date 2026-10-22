package com.mathis.mandala.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
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
import com.mathis.mandala.app.Editing
import com.mathis.mandala.core.BuiltInPalettes
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

/** A labelled on/off row; the whole row switches. */
@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun ValueSlider(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    default: Double,
    display: (Double) -> String = { String.format(Locale.US, "%.2f", it) },
    onChange: (Double) -> Unit,
) {
    SliderRow(label, display(value), onReset = { onChange(default) }) {
        // A saved value outside the range only puts the knob at the end; the value (shown on the right)
        // stays as it is until the slider is moved.
        Slider(value = value.toFloat().coerceIn(range.start, range.endInclusive), onValueChange = { onChange(it.toDouble()) }, valueRange = range)
    }
}

/** A range of settings values as the slider's float range. */
internal fun ClosedFloatingPointRange<Double>.floats(): ClosedFloatingPointRange<Float> = start.toFloat()..endInclusive.toFloat()

@Composable
internal fun SteppedSlider(label: String, value: Int, range: IntRange, default: Int, onChange: (Int) -> Unit) {
    SliderRow(label, value.toString(), onReset = { onChange(default) }) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}

/**
 * Label on the left, slider in the middle, value on the right: one line per setting. A double tap
 * anywhere on the line puts the setting back to its default (also offered as an accessibility action).
 */
@Composable
private fun SliderRow(label: String, value: String, onReset: () -> Unit, slider: @Composable () -> Unit) {
    val t = LocalTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .onDoubleTap(onReset)
            .semantics { customActions = listOf(CustomAccessibilityAction("Reset $label") { onReset(); true }) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

/**
 * Calls `action` on a double tap, watching the pointer before the slider sees it and consuming
 * nothing, so taps and drags still reach the slider. The slider jumps to the second tap; the reset
 * on its release then wins.
 */
private fun Modifier.onDoubleTap(action: () -> Unit): Modifier = composed {
    val current by rememberUpdatedState(action)
    pointerInput(Unit) {
        val timeout = viewConfiguration.doubleTapTimeoutMillis
        val slop = viewConfiguration.touchSlop
        var lastUpTime = -1L
        var lastUpPosition = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val second = lastUpTime >= 0 && down.uptimeMillis - lastUpTime <= timeout &&
                (down.position - lastUpPosition).getDistance() <= slop * 4
            var moved = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > slop) moved = true
                if (!change.pressed) {
                    if (moved) {
                        lastUpTime = -1L
                    } else if (second) {
                        lastUpTime = -1L
                        current()
                    } else {
                        lastUpTime = change.uptimeMillis
                        lastUpPosition = change.position
                    }
                    break
                }
            }
        }
    }
}

/** Hue, saturation and brightness of one colour, as a nested group with the colour as summary dot. */
@Composable
internal fun ColourGroup(
    title: String,
    hue: Double,
    saturation: Double,
    brightness: Double,
    /** Defaults of hue, saturation and brightness, for the double-tap reset. */
    defaults: Triple<Double, Double, Double>,
    initiallyOpen: Boolean = false,
    brightnessRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onChange: (Double, Double, Double) -> Unit,
) {
    Group(title, nested = true, initiallyOpen = initiallyOpen, trailing = { ColourDot(hsbColour(hue, saturation, brightness)) }) {
        ValueSlider("Hue", hue, default = defaults.first) { v -> onChange(v, saturation, brightness) }
        ValueSlider("Saturation", saturation, default = defaults.second) { v -> onChange(hue, v, brightness) }
        ValueSlider("Brightness", brightness, brightnessRange, default = defaults.third) { v -> onChange(hue, saturation, v) }
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

/** The palettes a picker offers (TUNE-1), with a hidden one the setting uses still shown as selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaletteSwatches(current: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (index in Editing.paletteChoices(current)) {
            Swatch(BuiltInPalettes.all[index], selected = current == index) { onPick(index) }
        }
    }
}

internal fun paletteBrush(palette: ColorPalette): Brush =
    Brush.sweepGradient(palette.stops.map { Color(it.color.r.toFloat(), it.color.g.toFloat(), it.color.b.toFloat(), 1f, ColorSpaces.DisplayP3) })
