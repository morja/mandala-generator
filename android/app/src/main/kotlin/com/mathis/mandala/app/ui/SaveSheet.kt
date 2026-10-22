package com.mathis.mandala.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mathis.mandala.app.Editing
import com.mathis.mandala.app.Editing.SaveFormat
import com.mathis.mandala.app.Editing.moved
import com.mathis.mandala.app.Editing.zoomedAround
import com.mathis.mandala.app.ExportProgress
import com.mathis.mandala.app.Fit
import com.mathis.mandala.app.FramePreviewState
import com.mathis.mandala.app.SaveChoice
import com.mathis.mandala.app.WallpaperTarget
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import com.mathis.mandala.core.Framing
import com.mathis.mandala.core.VectorExport
import kotlin.math.abs

/**
 * Saving in a format (ADR-0010): format, the mandala placed in the frame (Pro), whether the quote
 * follows it, then save, save large (Pro) or set as wallpaper; or a line drawing as SVG (Pro, ADR-0013).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
      // Messages ("Saved …") show inside the sheet while it is open; behind it they would be hidden.
      Box {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("Save image", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                for (f in listOf(SaveFormat.SQUARE, SaveFormat.SCREEN)) {
                    FilterChip(selected = choice.format == f, onClick = { onFormat(f) }, label = { Text(f.label) })
                }
                RatioChip(choice.format, onFormat)
            }
            // A square frame looks the same either way.
            if (aspect != 1.0) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = preset == false, onClick = { onFill(false) }, enabled = !busy, label = { Text("Show whole") })
                    FilterChip(selected = preset == true, onClick = { onFill(true) }, enabled = !busy, label = { Text("Fill") })
                }
            }
            // The frame takes the width, but leaves room for the buttons below on a phone.
            val maxFrameHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f
            Frame(preview, aspect.toFloat(), framing, enabled = proOwned && !busy, saving = busy, maxHeight = maxFrameHeight, onFraming = onFraming, onCentre = { onFill(false) })
            if (proOwned) {
                Text("Drag to move the mandala, pinch to zoom, double tap to centre it.", style = MaterialTheme.typography.bodySmall, color = t.textMuted)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Moving and zooming the mandala come with Pro.", style = MaterialTheme.typography.bodySmall, color = t.textMuted, modifier = Modifier.weight(1f))
                    TextButton(onClick = onShowPro) { Text("About Pro") }
                }
            }
            if (hasQuote) SwitchRow("Quote moves with the mandala", choice.textFollowsMandala, onTextFollows)
            Button(onClick = { onSave(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save · $w × $h") }
            OutlinedButton(onClick = { if (proOwned) onSave(true) else onShowPro() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (proOwned) "Save large · $lw × $lh" else "Save large · $lw × $lh · Pro")
            }
            // A wallpaper fills the screen, so it is set in the phone screen format; the frame shows it first.
            WallpaperButton(enabled = !busy, onOpen = { if (choice.format != SaveFormat.SCREEN) onFormat(SaveFormat.SCREEN) }, onWallpaper = onWallpaper)
            LineDrawingButton(proOwned, enabled = !busy, onLineDrawing = onLineDrawing, onShowPro = onShowPro)
            Text(
                "A line drawing of the layers and the emblem, square, without glow, effects, background and quote." +
                    if (hasPointLayers) " Strange Attractor layers are left out." else "",
                style = MaterialTheme.typography.bodySmall,
                color = t.textMuted,
            )
        }
        // Above the frame and buttons, which may be scrolled away; the same place as messages.
        if (export != null) ExportNote(export, Modifier.align(Alignment.TopCenter)) else MessageHost(snackbar, Modifier.align(Alignment.TopCenter))
      }
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
    maxHeight: Dp,
    onFraming: (Framing) -> Unit,
    onCentre: () -> Unit,
) {
    val t = LocalTokens.current
    val current by rememberUpdatedState(framing)
    val change by rememberUpdatedState(onFraming)
    val centre by rememberUpdatedState(onCentre)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, maxHeight / aspect)
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
                .width(width)
                .aspectRatio(1f / aspect)
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
}

/** A chip for the fixed ratios: shows the chosen one and opens the list of portrait and landscape ratios. */
@Composable
private fun RatioChip(format: SaveFormat, onFormat: (SaveFormat) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = format.isRatio,
            onClick = { open = true },
            label = { Text(if (format.isRatio) format.label else "Other ratio") },
            trailingIcon = { Icon(EditorIcons.Expand, contentDescription = null, modifier = Modifier.size(Sizes.icon)) },
        )
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

/** Saves the SVG line drawing in one of its two looks; without Pro it opens the Pro sheet. */
@Composable
private fun LineDrawingButton(proOwned: Boolean, enabled: Boolean, onLineDrawing: (VectorExport.Look) -> Unit, onShowPro: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { if (proOwned) open = true else onShowPro() }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(if (proOwned) "Save line drawing · SVG" else "Save line drawing · SVG · Pro")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Colour on black") }, onClick = { open = false; onLineDrawing(VectorExport.Look.COLOUR) })
            DropdownMenuItem(text = { Text("Dark on white, for plotters and colouring") }, onClick = { open = false; onLineDrawing(VectorExport.Look.LINES) })
        }
    }
}

@Composable
private fun WallpaperButton(enabled: Boolean, onOpen: () -> Unit, onWallpaper: (WallpaperTarget) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { onOpen(); open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Set as wallpaper") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (target in WallpaperTarget.entries) {
                DropdownMenuItem(text = { Text(target.label) }, onClick = { open = false; onWallpaper(target) })
            }
        }
    }
}
