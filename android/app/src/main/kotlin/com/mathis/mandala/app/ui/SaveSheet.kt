package com.mathis.mandala.app.ui

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
import androidx.compose.material3.SnackbarHost
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
import com.mathis.mandala.app.FramePreviewState
import com.mathis.mandala.app.SaveChoice
import com.mathis.mandala.app.WallpaperTarget
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import com.mathis.mandala.core.Framing
import kotlin.math.abs

/**
 * Saving in a format (ADR-0010): format, the mandala placed in the frame (Pro), whether the quote
 * follows it, then save, save large (Pro) or set as wallpaper.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SaveSheet(
    choice: SaveChoice,
    preview: FramePreviewState,
    screenAspect: Double,
    proOwned: Boolean,
    hasQuote: Boolean,
    busy: Boolean,
    onFormat: (SaveFormat) -> Unit,
    onFraming: (Framing) -> Unit,
    onTextFollows: (Boolean) -> Unit,
    onSave: (large: Boolean) -> Unit,
    onWallpaper: (WallpaperTarget) -> Unit,
    onShowPro: () -> Unit,
    onDismiss: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val t = LocalTokens.current
    val aspect = Editing.aspect(choice.format, screenAspect)
    val framing = if (proOwned) choice.framing else Framing()
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
                for (f in SaveFormat.entries) {
                    FilterChip(selected = choice.format == f, onClick = { onFormat(f) }, label = { Text(f.label) })
                }
            }
            // The frame takes the width, but leaves room for the buttons below on a phone.
            val maxFrameHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f
            Frame(preview, aspect.toFloat(), framing, enabled = proOwned, maxHeight = maxFrameHeight, onFraming = onFraming)
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
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(Sizes.progress))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter))
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
    maxHeight: Dp,
    onFraming: (Framing) -> Unit,
) {
    val t = LocalTokens.current
    val current by rememberUpdatedState(framing)
    val change by rememberUpdatedState(onFraming)
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
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { change(Framing()) }) }
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
            if (preview.rendering) LinearProgressIndicator(Modifier.fillMaxWidth().height(Sizes.progress).align(Alignment.BottomCenter))
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
