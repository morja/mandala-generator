package com.mathis.mandala.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.mathis.mandala.app.Editing
import com.mathis.mandala.app.Editing.SaveFormat
import com.mathis.mandala.app.Editing.moved
import com.mathis.mandala.app.Editing.zoomedAround
import com.mathis.mandala.app.ExportProgress
import com.mathis.mandala.app.Fit
import com.mathis.mandala.app.FramePreviewState
import com.mathis.mandala.app.R
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
import com.mathis.mandala.core.LayerAnimation
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Saving in a format (ADR-0010, laid out as DM-11 A, without scrolling): format, the mandala placed in the frame (Pro)
 * with Whole or Fill beside it, the finish folded into "Adjust", and whether the quote follows. Size, Save
 * and Wallpaper stay at the bottom. The line drawing (ADR-0013) has its own page in the menu, since none of
 * this applies to it. Each format keeps its own placement. "Video" (UX-1 12, DM-16 A) keeps all of this,
 * turns the layers in the frame as the video will, and offers the length, Share and Save video instead.
 * Laid out as DM-18 A3 (26 October 2026): Picture | Video as tabs with the format menu beside them in
 * place of a title, the size in the save button, so the frame keeps most of the height.
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
    onShowPro: () -> Unit,
    onDismiss: () -> Unit,
    snackbar: SnackbarHostState,
    makingVideo: Boolean = false,
    onMoving: (Boolean) -> Unit = {},
    onVideoSeconds: (Int) -> Unit = {},
    onSaveVideo: (large: Boolean, share: Boolean) -> Unit = { _, _ -> },
    onCancelVideo: () -> Unit = {},
) {
    val t = LocalTokens.current
    val aspect = Editing.aspect(choice.format, screenAspect)
    // The preset the frame is on; none once a Pro user has moved or zoomed by hand.
    val preset = when (choice.fit) { Fit.WHOLE -> false; Fit.FILL -> true; Fit.HAND -> if (proOwned) null else false }
    val moving = choice.moving
    val (w, h) = if (moving) Editing.videoSize(choice.format, screenAspect, large = false) else Editing.exportSize(choice.format, screenAspect, large = false)
    val (lw, lh) = if (moving) Editing.videoSize(choice.format, screenAspect, large = true) else Editing.exportSize(choice.format, screenAspect, large = true)
    var large by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
      // Messages ("Saved …") show inside the sheet while it is open; behind it they would be hidden.
      Box {
        // Nothing scrolls inside the sheet (a drag at the top of a scrolled list closed it too easily):
        // the frame takes the height the other parts leave, and shrinks while "Adjust" is open.
        Column(Modifier.fillMaxHeight()) {
            Column(
                // The bottom padding keeps the last switch off the divider below.
                Modifier.weight(1f).padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // DM-18 A3: Picture or Video as tabs in place of a title, the format as a menu beside them.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeTabs(moving, enabled = !busy, onMoving, Modifier.weight(1f))
                    FormatMenu(choice.format, onFormat)
                }
                FrameWithFit(
                    aspect = aspect.toFloat(),
                    modifier = Modifier.weight(1f),
                    // A square frame looks the same either way; once placed by hand it offers the way back.
                    showFit = aspect != 1.0 || preset == null,
                    frame = { Frame(preview, aspect.toFloat(), framing, enabled = proOwned && !busy, saving = busy, onFraming = onFraming, onCentre = { onFill(false) }) },
                    fit = { vertical ->
                        if (aspect == 1.0) {
                            OutlinedButton(colors = outlinedColors(), onClick = { onFill(false) }, enabled = !busy) { Text(stringResource(R.string.save_reset)) }
                        } else {
                            FitToggle(preset, vertical, enabled = !busy, onFill = onFill)
                        }
                    },
                )
                val adjustOpen = rememberSaveable { mutableStateOf(false) }
                // The hint shows until the mandala has once been placed by hand, and gives its line to the
                // frame while the sliders are open.
                if (proOwned && !choice.framedByHand && !adjustOpen.value) {
                    Text(
                        stringResource(R.string.save_frame_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (!proOwned) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.save_frame_pro), style = MaterialTheme.typography.bodySmall, color = t.textMuted, modifier = Modifier.weight(1f))
                        TextButton(onClick = onShowPro) { Text(stringResource(R.string.save_about_pro)) }
                    }
                }
                Column {
                    HorizontalDivider(color = t.outline)
                    Group(
                        stringResource(R.string.save_adjust),
                        summary = finishSummary(choice.finish, proOwned),
                        // Reset in the heading, only when something is changed; it saves the frame a line. Plain
                        // text, not a button, so the heading keeps its height and the frame does not jump.
                        trailing = if (proOwned && !choice.finish.isIdentity) {
                            {
                                Text(
                                    stringResource(R.string.save_reset),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = t.accent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Radius.sm))
                                        .clickable(enabled = !busy, role = Role.Button) { onFinish(Finish()) }
                                        .padding(horizontal = Spacing.sm),
                                )
                            }
                        } else null,
                        openState = adjustOpen,
                        // Adjust uses the sheet's surrounding dividers instead of a second frame.
                        framed = false,
                    ) {
                        FinishControls(choice.finish, proOwned, enabled = !busy, onFinish = onFinish, onShowPro = onShowPro)
                    }
                }
                if (moving) LengthRow(choice.videoSeconds, enabled = !busy, onVideoSeconds)
                if (hasQuote) SwitchRow(stringResource(R.string.save_quote_follows), choice.textFollowsMandala, onTextFollows)
            }
            HorizontalDivider(color = t.outline)
            Column(
                Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.md, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // The size sits in the save button; its arrow offers the large one (Pro).
                val sizes = listOf(stringResource(R.string.save_size, w, h), stringResource(if (proOwned) R.string.save_size else R.string.save_size_pro, lw, lh))
                val chosen = if (large && proOwned) 1 else 0
                val pick = { i: Int -> if (i == 0) large = false else if (proOwned) large = true else onShowPro() }
                if (moving) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // A video goes mostly straight to a story or status, so Share takes the wallpaper's place.
                    if (makingVideo) {
                        OutlinedButton(colors = outlinedColors(), onClick = onCancelVideo, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save_cancel)) }
                    } else {
                        OutlinedButton(colors = outlinedColors(), onClick = { onSaveVideo(large && proOwned, true) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.action_share), maxLines = 1)
                        }
                    }
                    SaveButton(
                        stringResource(if (proOwned) R.string.save_video else R.string.save_video_pro_label), sizes, chosen, enabled = !busy,
                        onSave = { onSaveVideo(large && proOwned, false) }, onSize = pick, modifier = Modifier.weight(1f),
                    )
                } else Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                    SaveButton(
                        stringResource(R.string.save_save), sizes, chosen, enabled = !busy,
                        onSave = { onSave(large && proOwned) }, onSize = pick, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        // Above the frame and buttons, which may be scrolled away; the same place as messages.
        if (export != null) ExportNote(export, Modifier.align(Alignment.TopCenter)) else MessageHost(snackbar, Modifier.align(Alignment.TopCenter))
      }
    }
}

/** A picture, or the layers turning as a video (UX-1 12): tabs like the editor's, in place of a title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeTabs(moving: Boolean, enabled: Boolean, onMoving: (Boolean) -> Unit, modifier: Modifier) {
    PrimaryTabRow(selectedTabIndex = if (moving) 1 else 0, modifier = modifier, containerColor = BottomSheetDefaults.ContainerColor, divider = {}) {
        for ((label, video) in listOf(R.string.save_still to false, R.string.save_moving to true)) {
            Tab(selected = moving == video, onClick = { onMoving(video) }, enabled = enabled) {
                Text(stringResource(label), style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.padding(vertical = Spacing.md))
            }
        }
    }
}

/** The video's length; a longer one turns more calmly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LengthRow(seconds: Int, enabled: Boolean, onSeconds: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.save_length), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow {
            for ((i, s) in LayerAnimation.SECONDS.withIndex()) {
                SegmentedButton(selected = seconds == s, onClick = { onSeconds(s) }, shape = SegmentedButtonDefaults.itemShape(i, LayerAnimation.SECONDS.size), enabled = enabled, icon = {}) {
                    Text(stringResource(R.string.save_video_seconds, s), maxLines = 1)
                }
            }
        }
    }
}

/** Square, This screen and the fixed ratios as one menu; the button names the chosen format. */
@Composable
private fun FormatMenu(format: SaveFormat, onFormat: (SaveFormat) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(colors = outlinedColors(), onClick = { open = true }) {
            Text(stringResource(format.label), maxLines = 1)
            Icon(EditorIcons.Expand, contentDescription = null, modifier = Modifier.size(Sizes.icon))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (f in SaveFormat.entries) {
                val label = when {
                    !f.isRatio -> stringResource(f.label)
                    f.isLandscape -> stringResource(R.string.save_ratio_landscape, stringResource(f.label))
                    else -> stringResource(R.string.save_ratio_portrait, stringResource(f.label))
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = if (f == format) ({ Icon(EditorIcons.Check, contentDescription = null) }) else null,
                    onClick = { open = false; onFormat(f) },
                )
            }
        }
    }
}

/** Save with the size in a second line, and an arrow beside it that picks the size. */
@Composable
private fun SaveButton(label: String, sizes: List<String>, chosen: Int, enabled: Boolean, onSave: () -> Unit, onSize: (Int) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Button(
            onClick = onSave, enabled = enabled, modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sizes[chosen], style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        Box {
            Button(
                onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxHeight(),
                shape = RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50),
                contentPadding = PaddingValues(horizontal = Spacing.sm),
            ) { Icon(EditorIcons.Expand, contentDescription = stringResource(R.string.save_choose_size)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for ((i, size) in sizes.withIndex()) {
                    DropdownMenuItem(
                        text = { Text(size) },
                        trailingIcon = if (i == chosen) ({ Icon(EditorIcons.Check, contentDescription = null) }) else null,
                        onClick = { open = false; onSize(i) },
                    )
                }
            }
        }
    }
}

/**
 * The frame, as large as the given room allows and centred in it, with Whole or Fill at its bottom
 * right when it is tall; otherwise (4:5, 3:4, landscape)
 * the switch sits centred below.
 */
@Composable
private fun FrameWithFit(
    aspect: Float,
    modifier: Modifier,
    showFit: Boolean,
    frame: @Composable () -> Unit,
    fit: @Composable (vertical: Boolean) -> Unit,
) {
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        val gap = Spacing.lg.roundToPx()
        fun frameSize(height: Int, width: Int = maxW): Pair<Int, Int> {
            val w = minOf(width.toFloat(), height / aspect).roundToInt().coerceAtLeast(0)
            return w to (w * aspect).roundToInt()
        }
        // Tall frames (This screen, 9:16, 2:3) always keep the switch beside them, so it does not jump
        // while "Adjust" opens. The frame stays centred, giving up the room on both sides, unless the
        // height allows a larger one: then frame and switch are centred together.
        val side = if (showFit && aspect >= SIDE_FIT_ASPECT) subcompose("side") { fit(true) }.map { it.measure(Constraints()) } else emptyList()
        val besides = side.isNotEmpty()
        val switchW = if (besides) side.maxOf { it.width } + gap else 0
        val centred = frameSize(maxH, maxW - 2 * switchW)
        val grouped = frameSize(maxH, maxW - switchW)
        val (sideW, sideH) = if (grouped.second > centred.second) grouped else centred
        if (!showFit || besides) {
            val shown = subcompose("frame", frame).map { it.measure(Constraints.fixed(sideW, sideH)) }
            val x = if (grouped.second > centred.second) (maxW - sideW - switchW) / 2 else (maxW - sideW) / 2
            val y = (maxH - sideH) / 2
            layout(maxW, maxH) {
                shown.forEach { it.place(x, y) }
                side.takeIf { besides }?.forEach { it.place(x + sideW + gap, y + sideH - it.height) }
            }
        } else {
            val below = subcompose("below") { fit(false) }.map { it.measure(Constraints(maxWidth = maxW)) }
            val belowH = below.maxOf { it.height }
            val space = Spacing.md.roundToPx()
            val (w, h) = frameSize(maxH - space - belowH)
            val shown = subcompose("frame", frame).map { it.measure(Constraints.fixed(w, h)) }
            val y = (maxH - h - space - belowH) / 2
            layout(maxW, maxH) {
                shown.forEach { it.place((maxW - w) / 2, y) }
                below.forEach { it.place((maxW - it.width) / 2, y + h + space) }
            }
        }
    }
}

/** From this height over width on, Whole and Fill sit beside the frame instead of below it. */
private const val SIDE_FIT_ASPECT = 1.5f

/** Whole or Fill as a small two-part switch, stacked beside a portrait frame or in a row below. */
@Composable
private fun FitToggle(preset: Boolean?, vertical: Boolean, enabled: Boolean, onFill: (Boolean) -> Unit) {
    val t = LocalTokens.current
    val pill = RoundedCornerShape(percent = 50)
    val outer = if (vertical) RoundedCornerShape(Radius.md + Spacing.xs) else pill
    val parts = @Composable { itemModifier: Modifier ->
        for ((label, fill) in listOf(R.string.save_fit_whole to false, R.string.save_fit_fill to true)) {
            val on = preset == fill
            Text(
                stringResource(label),
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
                contentDescription = stringResource(R.string.save_preview_description),
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
@Composable
private fun finishSummary(finish: Finish, proOwned: Boolean): String = when {
    !proOwned -> stringResource(R.string.save_adjust_with_pro)
    finish.isIdentity -> stringResource(R.string.save_adjust_unchanged)
    else -> stringResource(R.string.save_adjust_summary, percent(finish.brightness), percent(finish.contrast), percent(finish.saturation))
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
            Text(stringResource(R.string.save_adjust_pro), style = MaterialTheme.typography.bodySmall, color = t.textMuted, modifier = Modifier.weight(1f))
            TextButton(onClick = onShowPro) { Text(stringResource(R.string.save_about_pro)) }
        }
        return
    }
    Column {
        ValueSlider(stringResource(R.string.save_brightness), finish.brightness, Finish.MIN_BRIGHTNESS.toFloat()..Finish.MAX_BRIGHTNESS.toFloat(), default = 1.0, display = ::percent) {
            if (enabled) onFinish(finish.copy(brightness = it))
        }
        ValueSlider(stringResource(R.string.save_contrast), finish.contrast, Finish.MIN_CONTRAST.toFloat()..Finish.MAX_CONTRAST.toFloat(), default = 1.0, display = ::percent) {
            if (enabled) onFinish(finish.copy(contrast = it))
        }
        ValueSlider(stringResource(R.string.save_saturation), finish.saturation, 0f..Finish.MAX_SATURATION.toFloat(), default = 1.0, display = ::percent) {
            if (enabled) onFinish(finish.copy(saturation = it))
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
        OutlinedButton(colors = outlinedColors(), onClick = { onOpen(); open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_wallpaper)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (target in WallpaperTarget.entries) {
                DropdownMenuItem(text = { Text(stringResource(target.label)) }, onClick = { open = false; onWallpaper(target) })
            }
            DropdownMenuItem(text = { Text(pairLabel) }, onClick = { open = false; onPair() })
            DropdownMenuItem(text = { Text(liveLabel) }, onClick = { open = false; onLive() })
        }
    }
}
