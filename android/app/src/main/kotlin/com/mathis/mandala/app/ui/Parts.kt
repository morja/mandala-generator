package com.mathis.mandala.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import com.mathis.mandala.app.Editing
import com.mathis.mandala.app.R
import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.ColorPalette
import com.mathis.mandala.core.LayerBlendMode
import com.mathis.mandala.core.Rgb
import java.util.Locale
import kotlin.math.roundToInt

// Building blocks of the control tabs: foldable groups and one-line sliders, so a tab shows a few
// headings at first and only the open group's sliders take room.

/**
 * One section style across every tab. Headers have the same minimum height, type and right-hand
 * chevron; a small indentation inside the frame marks a child section. Large actions belong inside the open content.
 * The save sheet can omit the container because its surrounding dividers already frame Adjust.
 */
@Composable
internal fun Group(
    title: String,
    summary: String? = null,
    initiallyOpen: Boolean = false,
    inset: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    openState: MutableState<Boolean> = rememberSaveable { mutableStateOf(initiallyOpen) },
    framed: Boolean = true,
    /** Something inside differs from its default: a dot beside the title shows it while folded too. */
    changed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    var open by openState
    val toggleLabel = stringResource(if (open) R.string.controls_group_close else R.string.controls_group_open, title)
    val state = stringResource(if (open) R.string.controls_group_state_open else R.string.controls_group_state_closed)
    val shape = RoundedCornerShape(Radius.sm)
    val container = if (framed) Modifier
        .clip(shape)
        .background(t.surface)
        .border(Sizes.swatchRing, t.outline, shape)
    else Modifier

    Column(
        Modifier.fillMaxWidth()
            .then(if (inset) Modifier.padding(start = Spacing.sm) else Modifier)
            .padding(top = Spacing.sm)
            .then(container),
    ) {
        Row(
            Modifier.fillMaxWidth()
                // Unframed (the save sheet's Adjust) it is a lighter heading and takes less height.
                .heightIn(min = if (framed) Sizes.icon * 2 else Sizes.icon + Spacing.md)
                .clickable(onClickLabel = toggleLabel) { open = !open }
                .semantics { stateDescription = state }
                // Without a frame the heading lines up with the rows around it.
                .then(if (framed) Modifier.padding(horizontal = Spacing.sm) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (changed) {
                val note = stringResource(R.string.controls_group_changed)
                Box(Modifier.padding(start = Spacing.sm).size(Spacing.sm).clip(CircleShape).background(t.accent)
                    .semantics { contentDescription = note })
            }
            Text(
                summary.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = t.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm),
            )
            trailing?.invoke()
            Icon(if (open) EditorIcons.Less else EditorIcons.Expand, contentDescription = null,
                tint = t.textMuted, modifier = Modifier.padding(start = Spacing.sm))
        }
        if (open) {
            if (framed) HorizontalDivider(color = t.outline)
            Column(
                Modifier.then(if (framed) Modifier.padding(horizontal = Spacing.lg) else Modifier).padding(top = Spacing.sm, bottom = Spacing.md),
                content = content,
            )
        }
    }
}

/** A direct random action with a visible explanation of what changes. */
@Composable
internal fun RandomActionRow(label: String, summary: String?, description: String, onClick: () -> Unit) {
    val t = LocalTokens.current
    // A plain row like the sliders around it, the die on the right where a slider ends.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.icon * 2)
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(summary.orEmpty(), style = MaterialTheme.typography.bodySmall, color = t.textMuted,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm))
        Icon(EditorIcons.DieOne, contentDescription = null, tint = t.accent)
    }
}

/**
 * Outlined buttons in the normal text colour: Material's default (onSurfaceVariant, our muted text) made
 * them read as disabled next to a filled button.
 */
@Composable
internal fun outlinedColors() = ButtonDefaults.outlinedButtonColors(contentColor = LocalTokens.current.text)

@Composable
internal fun outlinedIconColors() = IconButtonDefaults.outlinedIconButtonColors(contentColor = LocalTokens.current.text)

/** Whether any value differs from its default, pairs of (value, default). */
internal fun differs(vararg values: Pair<Double, Double>): Boolean = values.any { (v, d) -> kotlin.math.abs(v - d) > 1e-6 }

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
    val resetLabel = stringResource(R.string.controls_slider_reset, label)
    Row(
        Modifier
            .fillMaxWidth()
            .onDoubleTap(onReset)
            .semantics { customActions = listOf(CustomAccessibilityAction(resetLabel) { onReset(); true }) },
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

/** Hue, saturation and brightness in one section, with the colour as its summary dot. */
@Composable
internal fun ColourGroup(
    title: String,
    hue: Double,
    saturation: Double,
    brightness: Double,
    /** Defaults of hue, saturation and brightness, for the double-tap reset. */
    defaults: Triple<Double, Double, Double>,
    initiallyOpen: Boolean = false,
    /** Inside another group's content (see [Group]). */
    inset: Boolean = false,
    brightnessRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onChange: (Double, Double, Double) -> Unit,
) {
    Group(title, inset = inset, initiallyOpen = initiallyOpen, trailing = { ColourDot(hsbColour(hue, saturation, brightness)) },
        changed = differs(hue to defaults.first, saturation to defaults.second, brightness to defaults.third)) {
        ValueSlider(stringResource(R.string.controls_hue), hue, default = defaults.first) { v -> onChange(v, saturation, brightness) }
        ValueSlider(stringResource(R.string.save_saturation), saturation, default = defaults.second) { v -> onChange(hue, v, brightness) }
        ValueSlider(stringResource(R.string.save_brightness), brightness, brightnessRange, default = defaults.third) { v -> onChange(hue, saturation, v) }
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
            FilterChip(selected = mode == m, onClick = { onChange(m) }, label = { Text(stringResource(m.labelRes)) })
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
    if (palette.stops.size < 2) SolidColor(palette.stops.firstOrNull()?.let { p3Colour(it.color) } ?: Color.White)
    else Brush.sweepGradient(*palette.stops.map { it.position.toFloat() to p3Colour(it.color) }.toTypedArray())

/** The palette from left to right, at its stops' positions (the own-palette editor's bar). */
internal fun paletteBar(palette: ColorPalette): Brush =
    if (palette.stops.size < 2) SolidColor(palette.stops.firstOrNull()?.let { p3Colour(it.color) } ?: Color.White)
    else Brush.horizontalGradient(*palette.stops.map { it.position.toFloat() to p3Colour(it.color) }.toTypedArray())

internal fun p3Colour(c: Rgb): Color = Color(c.r.toFloat(), c.g.toFloat(), c.b.toFloat(), 1f, ColorSpaces.DisplayP3)

/** A large action with its icon above the label, several side by side (DM-13 A). */
@Composable
internal fun RowScope.ActionTile(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val t = LocalTokens.current
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(Radius.md)).background(t.surfaceRaised)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) t.accent else t.textMuted)
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) t.text else t.textMuted, maxLines = 1)
    }
}
