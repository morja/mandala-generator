package com.mathis.mandala.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.min as minDp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathis.mandala.app.EditorViewModel
import com.mathis.mandala.app.R
import com.mathis.mandala.app.ui.theme.DarkTokens
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import com.mathis.mandala.core.TextLayerSettings
import com.mathis.mandala.editing.Hsb
import com.mathis.mandala.editing.TextColourTarget
import com.mathis.mandala.editing.TextColours
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// The text's two colours (DM-24 A2, Mathis 13 November 2026): the Colour group of the Text tab and
// the colour sheet with its field, hue strip and before/after.

private fun Hsb.colour(): Color = hsbColour(hue, saturation, brightness.coerceAtMost(1.0))

/**
 * The Colour group: two dots in its head (Text, Glow) that open the sheet on their side, the Text |
 * Glow switch, the picture's colours plus white with "Stand out", "More colours" and the folded
 * "Fine tune" sliders for the colour the switch shows.
 */
@Composable
internal fun TextColourGroup(text: TextLayerSettings, vm: EditorViewModel) {
    val t = LocalTokens.current
    val target by vm.textColourTarget.collectAsStateWithLifecycle()
    val swatches by vm.textSwatches.collectAsStateWithLifecycle()
    val params by vm.params.collectAsStateWithLifecycle()
    val textColour = TextColours.text(text)
    val glowColour = TextColours.glow(text)
    val edited = TextColours.of(text, target)
    val td = TextLayerSettings()

    Group(
        stringResource(R.string.controls_colour),
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                HeadDot(stringResource(R.string.controls_text_colour_text), stringResource(R.string.controls_text_colour_open_text), textColour.colour()) {
                    vm.openTextColourSheet(TextColourTarget.TEXT)
                }
                HeadDot(stringResource(R.string.controls_text_colour_glow), stringResource(R.string.controls_text_colour_open_glow), glowColour.colour()) {
                    vm.openTextColourSheet(TextColourTarget.GLOW)
                }
            }
        },
        changed = TextColours.changed(text),
    ) {
        // The picture's colours are looked at only while the group is open, once the picture rests.
        val pictureKey = params.copy(textLayer = td)
        LaunchedEffect(pictureKey) { vm.prepareTextSwatches() }

        TargetSwitch(target, textColour, glowColour, vm::setTextColourTarget)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs, bottom = Spacing.sm).heightIn(min = Sizes.icon + Spacing.sm),
        ) {
            // The switch above says which colour the swatches set; the label stays one short line.
            Text(
                stringResource(R.string.controls_text_colour_from_picture),
                style = MaterialTheme.typography.bodySmall, color = t.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = Spacing.sm),
            )
            val standOutDescription = stringResource(R.string.controls_text_colour_stand_out_description)
            OutlinedButton(
                onClick = vm::standOut,
                colors = outlinedColors(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md),
                modifier = Modifier.semantics { contentDescription = standOutDescription },
            ) {
                Icon(EditorIcons.StandOut, contentDescription = null, modifier = Modifier.size(Sizes.icon - Spacing.xs))
                Text(stringResource(R.string.controls_text_colour_stand_out), maxLines = 1, modifier = Modifier.padding(start = Spacing.sm))
            }
        }
        SwatchRow(swatches, edited, vm::pickTextColour)
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { vm.openTextColourSheet() }) { Text(stringResource(R.string.controls_text_colour_more)) }
        }
        FineTune(text, target, edited, vm)
    }
}

/** A label with a colour dot in the group's head; a tap opens the sheet on that colour. */
@Composable
private fun HeadDot(label: String, description: String, colour: Color, onClick: () -> Unit) {
    val t = LocalTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(Radius.sm))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .heightIn(min = Sizes.icon * 2)
            .padding(horizontal = Spacing.xs),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = t.textMuted, modifier = Modifier.padding(end = Spacing.xs))
        Box(Modifier.size(Sizes.swatchSmall).clip(CircleShape).background(colour).border(Sizes.swatchRing / 2, t.outline, CircleShape))
    }
}

/** The Text | Glow switch, each side with its colour; the same in the group and in the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetSwitch(target: TextColourTarget, text: Hsb, glow: Hsb, onTarget: (TextColourTarget) -> Unit) {
    val t = LocalTokens.current
    val choices = listOf(
        Triple(TextColourTarget.TEXT, R.string.controls_text_colour_text, text),
        Triple(TextColourTarget.GLOW, R.string.controls_text_colour_glow, glow),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        for ((i, choice) in choices.withIndex()) {
            val (value, label, colour) = choice
            SegmentedButton(
                selected = target == value,
                onClick = { onTarget(value) },
                shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(activeContainerColor = t.accentSoft),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(Spacing.lg).clip(CircleShape).background(colour.colour()).border(Sizes.swatchRing / 2, t.outline, CircleShape))
                    Text(stringResource(label), maxLines = 1, modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
    }
}

/** The picture's colours plus white in one row; the swatches shrink on a narrow phone. */
@Composable
private fun SwatchRow(swatches: List<Hsb>, edited: Hsb, onPick: (Hsb) -> Unit) {
    val t = LocalTokens.current
    val white = stringResource(R.string.controls_text_colour_white)
    val selectedState = stringResource(R.string.controls_text_colour_chosen)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        val slots = TextColours.PICTURE_SWATCHES + 1
        val size = minDp(Sizes.swatch, (maxWidth - Spacing.xs * (slots - 1)) / slots)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            for ((i, c) in swatches.withIndex()) {
                val chosen = TextColours.matches(c, edited)
                val label = if (c == Hsb.WHITE) white else stringResource(R.string.controls_text_colour_swatch, i + 1)
                Box(
                    Modifier.weight(1f).heightIn(min = Sizes.icon * 2),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(size)
                            .then(if (chosen) Modifier.border(Sizes.swatchRing, t.accent, CircleShape).padding(Sizes.swatchRing * 2) else Modifier)
                            .clip(CircleShape)
                            .background(c.colour())
                            .border(Sizes.swatchRing / 2, t.outline, CircleShape)
                            .selectable(selected = chosen, role = Role.RadioButton) { onPick(c) }
                            .semantics {
                                contentDescription = label
                                if (chosen) stateDescription = selectedState
                            },
                    )
                }
            }
            // Room for the colours still to come, so white keeps its place while the picture is looked at.
            repeat(slots - swatches.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** "Fine tune", folded: hue, saturation and brightness of the colour the switch shows. */
@Composable
private fun FineTune(text: TextLayerSettings, target: TextColourTarget, edited: Hsb, vm: EditorViewModel) {
    val t = LocalTokens.current
    var open by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (open) R.string.controls_group_state_open else R.string.controls_group_state_closed)
    androidx.compose.material3.HorizontalDivider(color = t.outline, modifier = Modifier.padding(top = Spacing.sm))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.icon * 2)
            .clickable(role = Role.Button) { open = !open }
            .semantics { stateDescription = state },
    ) {
        Text(stringResource(R.string.controls_text_colour_fine_tune), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(if (open) EditorIcons.Less else EditorIcons.Expand, contentDescription = null, tint = t.textMuted)
    }
    if (!open) return
    // The text keeps its boosted range (up to 2); the glow goes to 1, and a double tap brings it back to the letters' colour.
    val brightnessRange = TextColours.brightnessRange(target).floats()
    val defaults = TextColours.fineTuneDefaults(text, target)
    ValueSlider(stringResource(R.string.controls_hue), edited.hue, default = defaults.hue) { v -> vm.setTextColour { it.copy(hue = v) } }
    ValueSlider(stringResource(R.string.controls_saturation), edited.saturation, default = defaults.saturation) { v -> vm.setTextColour { it.copy(saturation = v) } }
    ValueSlider(stringResource(R.string.controls_brightness), edited.brightness, brightnessRange, default = defaults.brightness) { v -> vm.setTextColour { it.copy(brightness = v) } }
}

// ── The colour sheet ──

/**
 * The colour sheet over the tab area only, so the picture above stays in full view: the switch,
 * "Same as text" for the glow, before and after (the same crop of the picture behind the text, as
 * it was when the sheet opened and as it is now), the saturation-brightness field and the hue strip.
 * Dragging changes the picture at once (a small render while the finger moves, see EditorViewModel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextColourSheetOverlay(text: TextLayerSettings, vm: EditorViewModel) {
    val t = LocalTokens.current
    val target by vm.textColourTarget.collectAsStateWithLifecycle()
    val before by vm.textColourBefore.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val cancel = { vm.closeTextColourSheet(keep = false) }
    BackHandler(onBack = cancel)

    Box(Modifier.fillMaxSize()) {
        // The scrim cancels, like the back gesture.
        val cancelLabel = stringResource(R.string.controls_cancel)
        Box(
            Modifier.fillMaxSize().background(BottomSheetDefaults.ScrimColor)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = cancelLabel, onClick = cancel),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Radius.md, topEnd = Radius.md))
                .background(t.surfaceRaised)
                // Taps on the sheet itself do not reach the scrim (and TalkBack does not offer the sheet as a button).
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            // The head with Cancel and Done stays in view while the rest scrolls under a large picture (IOS-12 5).
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(vertical = Spacing.sm)
                        .size(width = Spacing.xl + Spacing.sm, height = Spacing.xs).clip(CircleShape).background(t.outline),
                )
                Row(Modifier.fillMaxWidth().padding(bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.controls_text_colour_sheet_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    TextButton(onClick = cancel) { Text(stringResource(R.string.controls_cancel), maxLines = 1) }
                    Button(onClick = { vm.closeTextColourSheet(keep = true) }, modifier = Modifier.padding(start = Spacing.xs)) {
                        Text(stringResource(R.string.controls_done), maxLines = 1)
                    }
                }
            }
            Column(
                Modifier.weight(1f, fill = false).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.lg),
            ) {
                TargetSwitch(target, TextColours.text(text), TextColours.glow(text), vm::setTextColourTarget)
                // One height on both sides, so the sheet does not jump when the switch changes.
                Row(Modifier.fillMaxWidth().heightIn(min = Sizes.icon * 2), verticalAlignment = Alignment.CenterVertically) {
                    if (target == TextColourTarget.GLOW) {
                        SwitchRow(stringResource(R.string.controls_text_colour_same_as_text), text.glowSameAsText, vm::setGlowSameAsText)
                    } else {
                        Text(stringResource(R.string.controls_text_colour_letters), style = MaterialTheme.typography.bodySmall, color = t.textMuted)
                    }
                }
                val area = TextColours.textArea(text)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    CompareHalf(before, area, stringResource(R.string.controls_text_colour_before), Modifier.weight(1f))
                    CompareHalf(preview.image, area, stringResource(R.string.controls_text_colour_after), Modifier.weight(1f))
                }
                val edited = TextColours.of(text, target)
                ColourField(edited, Modifier.padding(top = Spacing.md)) { x, y -> vm.setTextColour { TextColours.withFieldPoint(it, x, y) } }
                HueStrip(edited.hue, Modifier.padding(top = Spacing.md)) { h -> vm.setTextColour { TextColours.withHueFromStrip(it, h) } }
            }
        }
    }
}

/** One half of the before/after: the picture's crop around the text, with a caption. */
@Composable
private fun CompareHalf(image: ImageBitmap?, area: DoubleArray, caption: String, modifier: Modifier) {
    val t = LocalTokens.current
    Box(modifier.height(Sizes.icon * 2 + Spacing.sm).clip(RoundedCornerShape(Radius.sm)).background(t.canvas)) {
        if (image != null) {
            Canvas(Modifier.fillMaxSize()) {
                // The text area's centre, and a crop of the half's shape around it, as wide as the text box.
                val iw = image.width
                val ih = image.height
                val cx = (area[0] + area[2]) / 2 * iw
                val cy = (area[1] + area[3]) / 2 * ih
                val aspect = size.width / size.height
                var sw = max(1.0, (area[2] - area[0]) * iw)
                var sh = sw / aspect
                if (sh > ih) { sh = ih.toDouble(); sw = sh * aspect }
                sw = min(sw, iw.toDouble())
                val sx = (cx - sw / 2).coerceIn(0.0, iw - sw)
                val sy = (cy - sh / 2).coerceIn(0.0, ih - sh)
                drawImage(
                    image,
                    srcOffset = IntOffset(sx.roundToInt(), sy.roundToInt()),
                    srcSize = IntSize(sw.roundToInt().coerceIn(1, iw), sh.roundToInt().coerceIn(1, ih)),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                )
            }
        }
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = DarkTokens.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.xs)
                .clip(RoundedCornerShape(Spacing.xs)).background(t.canvas.copy(alpha = 0.6f)).padding(horizontal = Spacing.xs),
        )
    }
}

/** Saturation to the right, brightness up, in the colour's hue; a drag or a tap sets both. */
@Composable
private fun ColourField(colour: Hsb, modifier: Modifier, onPoint: (Double, Double) -> Unit) {
    val label = stringResource(R.string.controls_text_colour_field)
    val current by rememberUpdatedState(onPoint)
    val pure = hsbColour(colour.hue, 1.0, 1.0)
    val marker = colour.colour()
    Canvas(
        modifier.fillMaxWidth().height(Spacing.xl * 5).clip(RoundedCornerShape(Radius.sm))
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    current(down.position.x / size.width.toDouble(), down.position.y / size.height.toDouble())
                    drag(down.id) { change ->
                        change.consume()
                        current(change.position.x / size.width.toDouble(), change.position.y / size.height.toDouble())
                    }
                }
            },
    ) {
        drawRect(pure)
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black)))
        val centre = Offset(colour.saturation.toFloat().coerceIn(0f, 1f) * size.width, (1 - colour.brightness.toFloat().coerceIn(0f, 1f)) * size.height)
        val r = (Spacing.sm + Spacing.xs / 4).toPx()
        drawCircle(marker, r, centre)
        drawCircle(Color.White, r, centre, style = Stroke(Sizes.swatchRing.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.6f), r + Sizes.swatchRing.toPx() / 2, centre, style = Stroke(Sizes.swatchRing.toPx() / 2))
    }
}

/** The hues side by side; a drag or a tap sets the hue. */
@Composable
private fun HueStrip(hue: Double, modifier: Modifier, onHue: (Double) -> Unit) {
    val label = stringResource(R.string.controls_hue)
    val current by rememberUpdatedState(onHue)
    val hues = remember { (0..6).map { hsbColour(it / 6.0, 1.0, 1.0) } }
    Canvas(
        modifier.fillMaxWidth().height(Sizes.icon).clip(CircleShape)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                val inset = Sizes.icon.toPx() / 2
                fun at(x: Float) = ((x - inset) / (size.width - 2 * inset)).toDouble().coerceIn(0.0, 1.0)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    current(at(down.position.x))
                    drag(down.id) { change ->
                        change.consume()
                        current(at(change.position.x))
                    }
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(hues))
        val r = size.height / 2
        val centre = Offset(r + hue.toFloat().coerceIn(0f, 1f) * (size.width - 2 * r), r)
        drawCircle(hsbColour(hue, 1.0, 1.0), r - Sizes.swatchRing.toPx(), centre)
        drawCircle(Color.White, r - Sizes.swatchRing.toPx(), centre, style = Stroke(Sizes.swatchRing.toPx()))
    }
}
