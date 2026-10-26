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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
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
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // DM-18 A3: Picture or Video as tabs in place of a title, the format as a menu beside them.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeTabs(moving, enabled = !busy, onMoving, Modifier.weight(1f))
                    FormatMenu(choice.format, onFormat)
                }
                FrameWithFit(
                    aspect = aspect.toFloat(),
                    modifier = Modifier.weight(1f),
                    // A square frame looks the same either way; the line below the frame offers the way back.
                    showFit = aspect != 1.0,
                    frame = { Frame(preview, aspect.toFloat(), framing, enabled = proOwned && !busy, saving = busy, onFraming = onFraming, onCentre = { onFill(false) }) },
                    fit = { vertical -> FitToggle(preset, vertical, enabled = !busy, onFill = onFill) },
                )
                val adjustOpen = rememberSaveable { mutableStateOf(false) }
                // One line under the frame: how to place by hand, or, once placed, that it is and the way back.
                // It gives its line to the frame while the sliders are open.
                // Both lines are two small lines high, so the frame keeps its size when one replaces the other.
                val lineHeight = Modifier.fillMaxWidth().heightIn(min = Spacing.xl + Spacing.sm)
                if (proOwned && preset == null && !adjustOpen.value) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = lineHeight) {
                        Text(stringResource(R.string.save_placed_by_hand), style = MaterialTheme.typography.bodySmall, color = t.textMuted)
                        Text(
                            stringResource(R.string.save_reset),
                            style = MaterialTheme.typography.labelLarge,
                            color = t.accent,
                            modifier = Modifier.padding(start = Spacing.sm).clip(RoundedCornerShape(Radius.sm))
                                .clickable(enabled = !busy, role = Role.Button) { onFill(false) }
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        )
                    }
                } else if (proOwned && !adjustOpen.value) {
                    Box(lineHeight, contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.save_frame_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.textMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else if (!proOwned) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.save_frame_pro), style = MaterialTheme.typography.bodySmall, color = t.textMuted, modifier = Modifier.weight(1f))
                        TextButton(onClick = onShowPro) { Text(stringResource(R.string.save_about_pro)) }
                    }
                }
                Column {
                    // One line across the whole sheet: the picture above, what is set and saved below it.
                    HorizontalDivider(color = t.outline, modifier = Modifier.fullBleed(Spacing.lg))
                    Group(
                        stringResource(R.string.save_adjust),
                        summary = finishSummary(choice.finish, proOwned, quoteMoves = hasQuote && choice.textFollowsMandala),
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
                        // Adjust uses the line above it instead of a second frame.
                        framed = false,
                    ) {
                        FinishControls(choice.finish, proOwned, enabled = !busy, onFinish = onFinish, onShowPro = onShowPro, hasQuote = hasQuote)
                        // The quote's place is part of Adjust (Pro, ADR-0016), so it costs the frame no line of its own.
                        if (hasQuote && proOwned) SwitchRow(stringResource(R.string.save_quote_follows), choice.textFollowsMandala, onTextFollows)
                    }
                }
                if (moving) LengthRow(choice.videoSeconds, enabled = !busy, onVideoSeconds)
            }
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
                        // The tab already says Video; the short label leaves room for the size.
                        stringResource(if (proOwned) R.string.save_save else R.string.save_save_pro), sizes, chosen, enabled = !busy,
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

/**
 * Square, This screen and the fixed ratios as one menu. The button is titled "Format", not the chosen
 * one (the frame shows that, the menu ticks it), so it keeps its width and the tabs beside it stay put.
 */
@Composable
private fun FormatMenu(format: SaveFormat, onFormat: (SaveFormat) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val chosen = stringResource(format.label)
    Box {
        OutlinedButton(colors = outlinedColors(), onClick = { open = true }, modifier = Modifier.semantics { stateDescription = chosen }) {
            Text(stringResource(R.string.save_format), maxLines = 1)
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
                // Narrower than a button's minimum, so the label beside it keeps its room.
                onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxHeight().width(Sizes.icon + Spacing.lg + Spacing.xs),
                shape = RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50),
                contentPadding = PaddingValues(end = Spacing.xs),
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
 * The frame, as large as the given room allows and centred in it. Tall frames (This screen, 9:16, 2:3)
 * have Whole and Fill as two icons beside their lower right corner, in the room the frame leaves; the
 * others (4:5, 3:4, landscape) have them in a row below.
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
        fun frameSize(height: Int, width: Int = maxW): Pair<Int, Int> {
            val w = minOf(width.toFloat(), height / aspect).roundToInt().coerceAtLeast(0)
            return w to (w * aspect).roundToInt()
        }
        val gap = Spacing.sm.roundToPx()
        if (showFit && aspect >= SIDE_FIT_ASPECT) {
            val side = subcompose("side") { fit(true) }.map { it.measure(Constraints()) }
            val sideW = side.maxOf { it.width }
            // Centred, and narrower only when the room at its right is too small for the icons.
            val (w, h) = frameSize(maxH, maxW - 2 * (sideW + gap))
            val shown = subcompose("frame", frame).map { it.measure(Constraints.fixed(w, h)) }
            val x = (maxW - w) / 2
            val y = (maxH - h) / 2
            layout(maxW, maxH) {
                shown.forEach { it.place(x, y) }
                // Centred in the strip at the frame's right, level with its bottom.
                side.forEach { it.place(x + w + (maxW - x - w - it.width) / 2, y + h - it.height) }
            }
        } else if (!showFit) {
            val (w, h) = frameSize(maxH)
            val shown = subcompose("frame", frame).map { it.measure(Constraints.fixed(w, h)) }
            layout(maxW, maxH) { shown.forEach { it.place((maxW - w) / 2, (maxH - h) / 2) } }
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

/** Whole or Fill as two round icon buttons, stacked beside a tall frame or in a row below the others. */
@Composable
private fun FitToggle(preset: Boolean?, vertical: Boolean, enabled: Boolean, onFill: (Boolean) -> Unit) {
    val t = LocalTokens.current
    val parts = @Composable {
        for ((icon, label, fill) in listOf(Triple(EditorIcons.FitWhole, R.string.save_fit_whole, false), Triple(EditorIcons.FitFill, R.string.save_fit_fill, true))) {
            val on = preset == fill
            Box(
                Modifier.size(Sizes.icon * 2).clip(CircleShape)
                    .background(if (on) t.accentSoft else Color.Transparent)
                    .border(BorderStroke(OutlinedTextFieldDefaults.UnfocusedBorderThickness, if (on) t.accentSoft else t.outline), CircleShape)
                    .selectable(selected = on, enabled = enabled, role = Role.RadioButton) { onFill(fill) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = stringResource(label), tint = if (on) t.accent else t.text)
            }
        }
    }
    if (vertical) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { parts() }
    } else {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { parts() }
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
private fun finishSummary(finish: Finish, proOwned: Boolean, quoteMoves: Boolean): String {
    if (!proOwned) return stringResource(R.string.save_adjust_with_pro)
    val parts = listOfNotNull(
        if (finish.isIdentity) null else stringResource(R.string.save_adjust_summary, percent(finish.brightness), percent(finish.contrast), percent(finish.saturation)),
        if (quoteMoves) stringResource(R.string.save_adjust_quote_moves) else null,
    )
    return if (parts.isEmpty()) stringResource(R.string.save_adjust_unchanged) else parts.joinToString(", ")
}

private fun percent(v: Double) = "${(v * 100).roundToInt()} %"

/**
 * The last adjustment of the saved image or wallpaper (ADR-0014): brightness, contrast and
 * saturation, applied to the frame at once, and below them whether the quote moves with the mandala.
 * Without Pro a line says so and opens the Pro sheet.
 */
@Composable
private fun FinishControls(finish: Finish, proOwned: Boolean, enabled: Boolean, onFinish: (Finish) -> Unit, onShowPro: () -> Unit, hasQuote: Boolean) {
    val t = LocalTokens.current
    if (!proOwned) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (hasQuote) R.string.save_adjust_pro_quote else R.string.save_adjust_pro), style = MaterialTheme.typography.bodySmall, color = t.textMuted, modifier = Modifier.weight(1f))
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

/** Reaches past the sheet's side padding of `padding`, so a line can run across the whole sheet. */
private fun Modifier.fullBleed(padding: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val extra = padding.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}
