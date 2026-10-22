package com.mathis.mandala.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.mathis.mandala.app.Editing
import com.mathis.mandala.app.Editing.SaveFormat
import com.mathis.mandala.app.Editing.moved
import com.mathis.mandala.app.Editing.zoomedAround
import com.mathis.mandala.app.ExportProgress
import com.mathis.mandala.app.Fit
import com.mathis.mandala.app.FramePreviewState
import com.mathis.mandala.app.SaveChoice
import com.mathis.mandala.app.WallpaperTarget
import com.mathis.mandala.app.liveLabel
import com.mathis.mandala.app.pairLabel
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import com.mathis.mandala.core.Finish
import com.mathis.mandala.core.Framing
import com.mathis.mandala.core.VectorExport
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Saving in a format (ADR-0010, laid out as DM-11 A): format, the mandala placed in the frame (Pro)
 * with Whole or Fill beside it, the finish folded into "Adjust", whether the quote follows, and the
 * line drawing as SVG (Pro, ADR-0013). Size, Save and Wallpaper stay at the bottom while the rest scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SaveSheet(
    choice: SaveChoice,
    /** The framing in the frame: the preset for this mandala, or the one placed by hand with Pro. */
    framing: Framing,
    preview: FramePreviewState,
    screenAspect: Double,
    proOwned: Boolean,
    hasQuote: Boolean,
    busy: Boolean,
    export: ExportProgress?,
    onFormat: (SaveFormat) -> Unit,
    onFraming: (Framing) -> Unit,
    onTextFollows: (Boolean) -> Unit,
    onSave: (large: Boolean) -> Unit,
    onWallpaper: (WallpaperTarget) -> Unit,
    onFill: (Boolean) -> Unit,
    onFinish: (Finish) -> Unit,
    onPair: () -> Unit,
    onLive: () -> Unit,
    hasPointLayers: Boolean,
    onLineDrawing: (VectorExport.Look) -> Unit,
    onShowPro: () -> Unit,
    onDismiss: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val t = LocalTokens.current
    val aspect = Editing.aspect(choice.format, screenAspect)
    // The preset the frame is on; none once a Pro user has moved or zoomed by hand.
    val preset = when (choice.fit) { Fit.WHOLE -> false; Fit.FILL -> true; Fit.HAND -> if (proOwned) null else false }
    val (w, h) = Editing.exportSize(choice.format, screenAspect, large = false)
    val (lw, lh) = Editing.exportSize(choice.format, screenAspect, large = true)
    var large by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
      // Messages ("Saved …") show inside the sheet while it is open; behind it they would be hidden.
      Box {
        Column {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).padding(bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text("Save image", style = MaterialTheme.typography.titleMedium)
                FormatRow(choice.format, onFormat)
                // The frame leaves room for the size and buttons below on a phone.
                val maxFrameHeight = LocalConfiguration.current.screenHeightDp.toFloat() * 0.42f
                FrameWithFit(
                    aspect = aspect.toFloat(),
                    maxHeight = Dp(maxFrameHeight),
                    // A square frame looks the same either way.
                    showFit = aspect != 1.0,
                    frame = { Frame(preview, aspect.toFloat(), framing, enabled = proOwned && !busy, saving = busy, onFraming = onFraming, onCentre = { onFill(false) }) },
                    fit = { vertical -> FitToggle(preset, vertical, enabled = !busy, onFill = onFill) },
                )
                if (proOwned) {
                    Text(
                        "Drag to move, pinch to zoom, double tap to centre.",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Moving and zooming the mandala come with Pro.", style = MaterialTheme.typography.bodySmall, color = t.textMuted, modifier = Modifier.weight(1f))
                        TextButton(onClick = onShowPro) { Text("About Pro") }
                    }
                }
                Column {
                    HorizontalDivider(color = t.outline)
                    Group("Adjust", summary = finishSummary(choice.finish, proOwned)) {
                        FinishControls(choice.finish, proOwned, enabled = !busy, onFinish = onFinish, onShowPro = onShowPro)
                    }
                }
                if (hasQuote) SwitchRow("Quote moves with the mandala", choice.textFollowsMandala, onTextFollows)
                LineDrawingRow(proOwned, hasPointLayers, enabled = !busy, onLineDrawing = onLineDrawing, onShowPro = onShowPro)
            }
            HorizontalDivider(color = t.outline)
            Column(
                Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.md, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !large || !proOwned,
                        onClick = { large = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        enabled = !busy,
                        icon = {},
                    ) { Text("$w × $h", maxLines = 1) }
                    SegmentedButton(
                        selected = large && proOwned,
                        onClick = { if (proOwned) large = true else onShowPro() },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        enabled = !busy,
                        icon = {},
                    ) { Text(if (proOwned) "$lw × $lh" else "$lw × $lh · Pro", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // A wallpaper fills the screen, so it is set in the phone screen format; the frame shows it first.
                    WallpaperButton(
                        enabled = !busy,
                        pairLabel = pairLabel(proOwned),
                        liveLabel = liveLabel(proOwned),
                        onOpen = { if (choice.format != SaveFormat.SCREEN) onFormat(SaveFormat.SCREEN) },
                        onWallpaper = onWallpaper,
                        onPair = onPair,
                        onLive = onLive,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { onSave(large && proOwned) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Save") }
                }
            }
        }
        // Above the frame and buttons, which may be scrolled away; the same place as messages.
        if (export != null) ExportNote(export, Modifier.align(Alignment.TopCenter)) else MessageHost(snackbar, Modifier.align(Alignment.TopCenter))
      }
    }
}

/** Square, This screen and the fixed ratios (in a list) as one segmented row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatRow(format: SaveFormat, onFormat: (SaveFormat) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        for ((i, f) in listOf(SaveFormat.SQUARE, SaveFormat.SCREEN).withIndex()) {
            SegmentedButton(selected = format == f, onClick = { onFormat(f) }, shape = SegmentedButtonDefaults.itemShape(i, 3), icon = {}) {
                Text(f.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        SegmentedButton(selected = format.isRatio, onClick = { open = true }, shape = SegmentedButtonDefaults.itemShape(2, 3), icon = {}) {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (format.isRatio) format.label else "Ratio", maxLines = 1)
                    Icon(EditorIcons.Expand, contentDescription = null, modifier = Modifier.size(Sizes.icon))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    for (f in SaveFormat.entries.filter { it.isRatio }) {
                        DropdownMenuItem(
                            text = { Text("${f.label} · ${if (f.isLandscape) "landscape" else "portrait"}") },
                            onClick = { open = false; onFormat(f) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The frame, centred, with Whole or Fill at its bottom right when a portrait frame leaves room
 * beside it; otherwise (a landscape frame, a narrow window) the switch sits centred below.
 */
@Composable
private fun FrameWithFit(
    aspect: Float,
    maxHeight: Dp,
    showFit: Boolean,
    frame: @Composable () -> Unit,
    fit: @Composable (vertical: Boolean) -> Unit,
) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val maxW = constraints.maxWidth
        val frameW = minOf(maxW.toFloat(), maxHeight.toPx() / aspect).roundToInt()
        val frameH = (frameW * aspect).roundToInt()
        val x = (maxW - frameW) / 2
        val gap = Spacing.lg.roundToPx()
        val shown = subcompose("frame", frame).map { it.measure(Constraints.fixed(frameW, frameH)) }
        if (!showFit) return@SubcomposeLayout layout(maxW, frameH) { shown.forEach { it.place(x, 0) } }
        val side = if (aspect > 1f) subcompose("side") { fit(true) }.map { it.measure(Constraints()) } else emptyList()
        if (side.isNotEmpty() && x >= side.maxOf { it.width } + gap) {
            layout(maxW, frameH) {
                shown.forEach { it.place(x, 0) }
                side.forEach { it.place(x + frameW + gap, frameH - it.height) }
            }
        } else {
            val below = subcompose("below") { fit(false) }.map { it.measure(Constraints(maxWidth = maxW)) }
            val belowH = below.maxOf { it.height }
            val space = Spacing.md.roundToPx()
            layout(maxW, frameH + space + belowH) {
                shown.forEach { it.place(x, 0) }
                below.forEach { it.place((maxW - it.width) / 2, frameH + space) }
            }
        }
    }
}

/** Whole or Fill as a small two-part switch, stacked beside a portrait frame or in a row below. */
@Composable
private fun FitToggle(preset: Boolean?, vertical: Boolean, enabled: Boolean, onFill: (Boolean) -> Unit) {
    val t = LocalTokens.current
    val pill = RoundedCornerShape(percent = 50)
    val outer = if (vertical) RoundedCornerShape(Radius.md + Spacing.xs) else pill
    val parts = @Composable { itemModifier: Modifier ->
        for ((label, fill) in listOf("Whole" to false, "Fill" to true)) {
            val on = preset == fill
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (on) t.accent else t.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = itemModifier
                    .clip(if (vertical) RoundedCornerShape(Radius.md) else pill)
                    .background(if (on) t.accentSoft else Color.Transparent)
                    .selectable(selected = on, enabled = enabled, role = Role.RadioButton) { onFill(fill) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
    val box = Modifier
        .border(BorderStroke(OutlinedTextFieldDefaults.UnfocusedBorderThickness, t.outline), outer)
        .padding(Spacing.xs)
        .selectableGroup()
    if (vertical) {
        Column(box.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) { parts(Modifier.fillMaxWidth()) }
    } else {
        Row(box, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { parts(Modifier) }
    }
}

/**
 * The image in the chosen format. While a finger moves, the last preview is moved and scaled to the
 * new framing; the next preview replaces it when it is drawn.
 */
@Composable
private fun Frame(
    preview: FramePreviewState,
    aspect: Float,
    framing: Framing,
    enabled: Boolean,
    saving: Boolean,
    onFraming: (Framing) -> Unit,
    onCentre: () -> Unit,
) {
    val t = LocalTokens.current
    val current by rememberUpdatedState(framing)
    val change by rememberUpdatedState(onFraming)
    val centre by rememberUpdatedState(onCentre)
    var gestures: Modifier = Modifier
    if (enabled) {
        gestures = Modifier
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    var next = current.moved((pan.x / size.width).toDouble(), (pan.y / size.height).toDouble())
                    if (zoom != 1f) next = next.zoomedAround((centroid.x / size.width).toDouble(), (centroid.y / size.height).toDouble(), zoom.toDouble())
                    change(next)
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { centre() }) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(Radius.md))
            .background(t.canvas)
            .then(gestures),
    ) {
        val image = preview.image
        // Only a preview of this format; the next one follows within moments of a change.
        if (image != null && preview.height > 0 && abs(preview.height.toFloat() / preview.width - aspect) < 0.01f) {
            val drawn = preview.framing
            Image(
                image,
                contentDescription = "Preview of the saved image",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val s = (framing.zoom / drawn.zoom).toFloat()
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = s
                    scaleY = s
                    translationX = (framing.centreX - drawn.centreX * s).toFloat() * size.width
                    translationY = (framing.centreY - drawn.centreY * s).toFloat() * size.height
                },
            )
        }
        if (saving) {
            // What the export does shows at the top of the sheet; the frame only dims.
            Box(Modifier.fillMaxSize().background(t.canvas.copy(alpha = 0.6f)))
        } else if (preview.rendering) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(Sizes.progress).align(Alignment.BottomCenter))
        }
    }
}

/** What "Adjust" shows while folded: the changed values, or that nothing is changed. */
private fun finishSummary(finish: Finish, proOwned: Boolean): String = when {
    !proOwned -> "With Pro"
    finish.isIdentity -> "Unchanged"
    else -> "Brightness ${percent(finish.brightness)}, contrast ${percent(finish.contrast)}, saturation ${percent(finish.saturation)}"
}

private fun percent(v: Double) = "${(v * 100).roundToInt()} %"

/**
 * The last adjustment of the saved image or wallpaper (ADR-0014): brightness, contrast and
 * saturation, applied to the frame at once. Without Pro a line says so and opens the Pro sheet.
 */
@Composable
private fun FinishControls(finish: Finish, proOwned: Boolean, enabled: Boolean, onFinish: (Finish) -> Unit, onShowPro: () -> Unit) {
    val t = LocalTokens.current
    if (!proOwned) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Brightness, contrast and saturation of the image come with Pro.", style = MaterialTheme.typography.bodySmall, color = t.textMuted, modifier = Modifier.weight(1f))
            TextButton(onClick = onShowPro) { Text("About Pro") }
        }
        return
    }
    Column {
        ValueSlider("Brightness", finish.brightness, Finish.MIN_BRIGHTNESS.toFloat()..Finish.MAX_BRIGHTNESS.toFloat(), default = 1.0, display = ::percent) {
            if (enabled) onFinish(finish.copy(brightness = it))
        }
        ValueSlider("Contrast", finish.contrast, Finish.MIN_CONTRAST.toFloat()..Finish.MAX_CONTRAST.toFloat(), default = 1.0, display = ::percent) {
            if (enabled) onFinish(finish.copy(contrast = it))
        }
        ValueSlider("Saturation", finish.saturation, 0f..Finish.MAX_SATURATION.toFloat(), default = 1.0, display = ::percent) {
            if (enabled) onFinish(finish.copy(saturation = it))
        }
        TextButton(onClick = { onFinish(Finish()) }, enabled = enabled && !finish.isIdentity, modifier = Modifier.align(Alignment.End)) { Text("Reset") }
    }
}

/** The line drawing as SVG in one of its two looks, as a quiet row; without Pro it opens the Pro sheet. */
@Composable
private fun LineDrawingRow(proOwned: Boolean, hasPointLayers: Boolean, enabled: Boolean, onLineDrawing: (VectorExport.Look) -> Unit, onShowPro: () -> Unit) {
    val t = LocalTokens.current
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Line drawing as SVG", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Square, the layers and the emblem as lines, without glow, effects, background and quote." +
                    if (hasPointLayers) " Strange Attractor layers are left out." else "",
                style = MaterialTheme.typography.bodySmall,
                color = t.textMuted,
            )
        }
        Box {
            TextButton(onClick = { if (proOwned) open = true else onShowPro() }, enabled = enabled) {
                Text(if (proOwned) "Save" else "Pro")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Colour on black") }, onClick = { open = false; onLineDrawing(VectorExport.Look.COLOUR) })
                DropdownMenuItem(text = { Text("Dark on white, for plotters and colouring") }, onClick = { open = false; onLineDrawing(VectorExport.Look.LINES) })
            }
        }
    }
}

@Composable
private fun WallpaperButton(
    enabled: Boolean,
    pairLabel: String,
    liveLabel: String,
    onOpen: () -> Unit,
    onWallpaper: (WallpaperTarget) -> Unit,
    onPair: () -> Unit,
    onLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { onOpen(); open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Wallpaper") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (target in WallpaperTarget.entries) {
                DropdownMenuItem(text = { Text(target.label) }, onClick = { open = false; onWallpaper(target) })
            }
            DropdownMenuItem(text = { Text(pairLabel) }, onClick = { open = false; onPair() })
            DropdownMenuItem(text = { Text(liveLabel) }, onClick = { open = false; onLive() })
        }
    }
}
