package com.mathis.mandala.app.ui

import androidx.activity.compose.LocalActivity
import com.mathis.mandala.app.ProState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.mathis.mandala.app.DrawTool
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import com.mathis.mandala.app.EditorEvent
import com.mathis.mandala.app.Thumbnails
import com.mathis.mandala.core.Favorite
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathis.mandala.app.EditorViewModel
import com.mathis.mandala.app.PreviewState
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import com.mathis.mandala.core.MandalaParameters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onAbout: () -> Unit = {}) {
    val params by vm.params.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val drawTool by vm.drawTool.collectAsStateWithLifecycle()
    val history by vm.historyState.collectAsStateWithLifecycle()
    val pro by vm.proState.collectAsStateWithLifecycle()
    var showPro by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showFavorites by remember { mutableStateOf(false) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::openFile) }
    val saveSettings = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::saveSettingsTo)
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is EditorEvent.Message -> {
                    // With an action Compose would keep the message until it is tapped; let it go on its own.
                    val result = snackbar.showSnackbar(
                        event.text,
                        actionLabel = if (event.canUndo) "Undo" else null,
                        duration = if (event.canUndo) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.undo()
                }
                is EditorEvent.ShareImage -> {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("image/png")
                        .putExtra(Intent.EXTRA_STREAM, event.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, null))
                }
            }
        }
    }

    if (showPro) {
        val activity = LocalActivity.current
        ProSheet(pro, onBuy = { activity?.let(vm::buyPro) }, onDismiss = { showPro = false })
    }
    LaunchedEffect(pro.owned) { if (pro.owned) showPro = false }

    if (showFavorites) {
        FavoritesSheet(
            favorites = favorites,
            canAdd = preview.image != null,
            onAdd = vm::addFavorite,
            onApply = { vm.applyFavorite(it); showFavorites = false },
            onDelete = { vm.removeFavorite(it.id) },
            onDismiss = { showFavorites = false },
        )
    }

    Scaffold(
        topBar = {
            // Undo and redo on the left; dice, save, favourites and the menu on the right. The title
            // only fits next to them on wide screens.
            val wide = LocalConfiguration.current.screenWidthDp.dp >= Spacing.twoPaneMinWidth
            TopAppBar(
                title = { if (wide) Text("Mandala") },
                navigationIcon = {
                    Row {
                        IconButton(onClick = vm::goBack, enabled = history.canGoBack) { Icon(EditorIcons.Back, contentDescription = "Back") }
                        IconButton(onClick = vm::goForward, enabled = history.canGoForward) {
                            Icon(EditorIcons.Forward, contentDescription = "Forward")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::surprise) { Icon(EditorIcons.Dice, contentDescription = "Surprise me") }
                    IconButton(onClick = vm::exportToGallery, enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(Sizes.icon), strokeWidth = Sizes.progress)
                        } else {
                            Icon(EditorIcons.Save, contentDescription = "Save to gallery", tint = LocalTokens.current.accent)
                        }
                    }
                    IconButton(onClick = { showFavorites = true }) { Icon(EditorIcons.Star, contentDescription = "Favourites") }
                    MoreMenu(
                        busy = busy,
                        proOwned = pro.owned,
                        onSaveLarge = { if (pro.owned) vm.exportLargeToGallery() else showPro = true },
                        onVariation = vm::newVariation,
                        onNewLayers = vm::randomLayers,
                        onShare = vm::shareImage,
                        onOpen = { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        onSaveSettings = { saveSettings.launch(vm.suggestedSettingsName()) },
                        onAbout = onAbout,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            if (maxWidth >= Spacing.twoPaneMinWidth) {
                Row(Modifier.fillMaxSize().padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                    // The square canvas takes the smaller of width and remaining height.
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        val side = minOf(maxWidth, maxHeight - Sizes.statusLine)
                        Canvas(preview, Modifier.width(side), drawing(params, drawTool, vm))
                    }
                    ControlTabs(params, selected, vm, Modifier.width(Spacing.controlsWidth).fillMaxHeight())
                }
            } else {
                // The canvas stays in view; only the open tab scrolls, so every change is visible.
                val canvasMax = maxHeight * 0.42f
                Column(Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
                    Canvas(
                        preview,
                        Modifier.widthIn(max = canvasMax).fillMaxWidth().align(Alignment.CenterHorizontally),
                        drawing(params, drawTool, vm),
                    )
                    ControlTabs(params, selected, vm, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

/** What the canvas needs while a drawing tool is active: the tool's look and where strokes go. */
private class DrawingInput(
    val symmetry: Int,
    val colour: Color,
    /** Line width (lines) or brush diameter (spray) as a fraction of the canvas width. */
    val width: Float,
    val spray: Boolean,
    val onStroke: (List<Double>, List<Double>) -> Unit,
)

private fun drawing(params: MandalaParameters, tool: DrawTool?, vm: EditorViewModel): DrawingInput? = when (tool) {
    null -> null
    DrawTool.LINES -> params.drawingLayer.let { d ->
        DrawingInput(
            symmetry = max(1, d.symmetry),
            colour = hsbColour(d.currentHue, d.currentSaturation, d.currentBrightness),
            // Same width as the macOS overlay: strokeWeight * 1.5 % of the canvas, at least a hairline.
            width = (d.strokeWeight * 0.015).toFloat(),
            spray = false,
            onStroke = vm::addStroke,
        )
    }
    DrawTool.SPRAY -> params.graffitiLayer.let { g ->
        DrawingInput(
            symmetry = max(1, g.symmetry),
            colour = hsbColour(g.currentHue, g.currentSaturation, g.currentBrightness).copy(alpha = g.currentOpacity.toFloat()),
            width = g.currentBrushSize.toFloat(),
            spray = true,
            onStroke = vm::addStroke,
        )
    }
}

@Composable
private fun Canvas(preview: PreviewState, modifier: Modifier, drawing: DrawingInput? = null) {
    val t = LocalTokens.current
    Column(modifier.padding(bottom = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Radius.md)).background(t.canvas),
            contentAlignment = Alignment.BottomCenter,
        ) {
            preview.image?.let {
                Image(it, contentDescription = "Mandala preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            if (drawing != null) DrawingOverlay(drawing, t.accent)
            if (preview.rendering) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(Sizes.progress))
            }
        }
        val status = when {
            preview.error != null -> preview.error
            drawing != null -> "Drag on the image to draw"
            else -> null
        }
        if (status != null) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = if (preview.error != null) t.error else t.textMuted)
        }
        for (warning in preview.warnings) {
            Text(warning, style = MaterialTheme.typography.bodySmall, color = t.error)
        }
    }
}

/**
 * Catches finger strokes on the canvas and shows the one in progress with its rotated copies, like
 * the macOS drawing overlay. The finished stroke goes to the settings and appears in the next preview.
 */
@Composable
private fun DrawingOverlay(input: DrawingInput, guide: Color) {
    val xs = remember { mutableStateListOf<Double>() }
    val ys = remember { mutableStateListOf<Double>() }
    val current by rememberUpdatedState(input)
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun add(p: Offset) {
                        xs.add((p.x / size.width).toDouble().coerceIn(0.0, 1.0))
                        ys.add((p.y / size.height).toDouble().coerceIn(0.0, 1.0))
                    }
                    add(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.positionChanged()) add(change.position)
                        change.consume()
                    }
                    current.onStroke(xs.toList(), ys.toList())
                    xs.clear()
                    ys.clear()
                }
            },
    ) {
        val c = Offset(size.width / 2, size.height / 2)
        val sym = current.symmetry
        // Guides: one ray per copy.
        for (k in 0 until sym) {
            val a = k * 2 * PI / sym
            drawLine(guide.copy(alpha = 0.35f), c, c + Offset(cos(a).toFloat(), sin(a).toFloat()) * size.maxDimension, strokeWidth = 1f)
        }
        if (xs.isEmpty()) return@Canvas
        val width = max(1f, current.width * size.width)
        for (k in 0 until sym) {
            val a = k * 2 * PI / sym
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            fun at(i: Int): Offset {
                val dx = xs[i].toFloat() * size.width - c.x
                val dy = ys[i].toFloat() * size.height - c.y
                return Offset(c.x + dx * ca - dy * sa, c.y + dx * sa + dy * ca)
            }
            if (xs.size == 1) {
                drawCircle(current.colour, width / 2, at(0))
            } else {
                val path = Path().apply {
                    moveTo(at(0).x, at(0).y)
                    for (i in 1 until xs.size) at(i).let { lineTo(it.x, it.y) }
                }
                drawPath(path, current.colour, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun MoreMenu(
    busy: Boolean,
    proOwned: Boolean,
    onSaveLarge: () -> Unit,
    onVariation: () -> Unit,
    onNewLayers: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onSaveSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(EditorIcons.More, contentDescription = "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("New variation") }, onClick = { open = false; onVariation() })
            DropdownMenuItem(text = { Text("New layers") }, onClick = { open = false; onNewLayers() })
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (proOwned) "Save large (2048 px)" else "Save large (2048 px) · Pro") },
                enabled = !busy,
                onClick = { open = false; onSaveLarge() },
            )
            DropdownMenuItem(text = { Text("Share image") }, enabled = !busy, onClick = { open = false; onShare() })
            DropdownMenuItem(text = { Text("Open file…") }, onClick = { open = false; onOpen() })
            DropdownMenuItem(text = { Text("Save settings…") }, onClick = { open = false; onSaveSettings() })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("About") }, onClick = { open = false; onAbout() })
        }
    }
}

/** What Pro adds, and the purchase button with Google Play's price. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProSheet(pro: ProState, onBuy: () -> Unit, onDismiss: () -> Unit) {
    val t = LocalTokens.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Mandala Pro", style = MaterialTheme.typography.titleLarge)
            Text(
                "Save your mandalas at 2048 × 2048 pixels, sharp enough for prints and wallpapers. " +
                    "One purchase, no subscription. Everything else stays free.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onBuy, enabled = pro.available, modifier = Modifier.fillMaxWidth()) {
                Text(if (pro.price != null) "Buy for ${pro.price}" else "Buy Pro")
            }
            if (!pro.available) {
                Text("Pro is sold through Google Play and is not available in this installation right now.",
                    style = MaterialTheme.typography.bodySmall, color = t.textMuted)
            }
        }
    }
}

/** Favourites as a grid of thumbnails: tap applies, long press offers delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesSheet(
    favorites: List<Favorite>,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onApply: (Favorite) -> Unit,
    onDelete: (Favorite) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalTokens.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Favourites", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(onClick = onAdd, enabled = canAdd) { Text("Add current") }
            }
            if (favorites.isEmpty()) {
                Text(
                    "No favourites yet. Add the current mandala, or open the favourites file of the Mac app " +
                        "(favorites.json) with Open file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.textMuted,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            } else {
                Text("Tap to use, long press to delete.", style = MaterialTheme.typography.bodySmall, color = t.textMuted,
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.sm))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(Sizes.favourite),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(favorites, key = { it.id }) { fav -> FavoriteTile(fav, onApply, onDelete) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteTile(fav: Favorite, onApply: (Favorite) -> Unit, onDelete: (Favorite) -> Unit) {
    val t = LocalTokens.current
    val thumb = remember(fav.id) { Thumbnails.decode(fav.thumbnailData) }
    var menu by remember { mutableStateOf(false) }
    val styles = fav.parameters.layers.joinToString(", ") { it.style.displayName }
    Box {
        Box(
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Radius.sm))
                .background(t.canvas)
                .combinedClickable(onClick = { onApply(fav) }, onLongClick = { menu = true })
                .semantics { contentDescription = "Favourite: $styles" },
        ) {
            thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete(fav) })
        }
    }
}

