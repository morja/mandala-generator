package com.mathis.mandala.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mathis.mandala.app.EditorEvent
import com.mathis.mandala.app.Thumbnails
import com.mathis.mandala.core.Favorite
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathis.mandala.app.EditorViewModel
import com.mathis.mandala.app.Editing
import com.mathis.mandala.app.PreviewState
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.ColorPalette
import com.mathis.mandala.core.LayerBlendMode
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.StyleLayer
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel) {
    val params by vm.params.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
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
                    val result = snackbar.showSnackbar(event.text, actionLabel = if (event.canUndo) "Undo" else null)
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
            TopAppBar(
                title = { Text("Mandala") },
                actions = {
                    IconButton(onClick = { showFavorites = true }) { Icon(EditorIcons.Star, contentDescription = "Favourites") }
                    MoreMenu(
                        busy = busy,
                        onOpen = { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        onSaveSettings = { saveSettings.launch(vm.suggestedSettingsName()) },
                        onShare = vm::shareImage,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val actions: @Composable () -> Unit = {
                Actions(saving = busy, onVariation = vm::newVariation, onSave = vm::exportToGallery)
            }
            if (maxWidth >= Spacing.twoPaneMinWidth) {
                Row(Modifier.fillMaxSize().padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        // The square canvas takes the smaller of width and remaining height.
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            val side = minOf(maxWidth, maxHeight - Sizes.statusLine)
                            Canvas(preview, Modifier.width(side))
                        }
                        actions()
                    }
                    Column(Modifier.width(Spacing.controlsWidth).verticalScroll(rememberScrollState())) {
                        Controls(params, selected, vm)
                    }
                }
            } else {
                // Canvas and buttons stay in view; only the controls scroll, so every change is visible.
                val canvasMax = maxHeight * 0.5f
                Column(
                    Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Canvas(preview, Modifier.widthIn(max = canvasMax).fillMaxWidth().align(Alignment.CenterHorizontally))
                    actions()
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = Spacing.lg)) {
                        Controls(params, selected, vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun Canvas(preview: PreviewState, modifier: Modifier) {
    val t = LocalTokens.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Radius.md)).background(t.canvas),
            contentAlignment = Alignment.BottomCenter,
        ) {
            preview.image?.let {
                Image(it, contentDescription = "Mandala preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            if (preview.rendering) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(Sizes.progress))
            }
        }
        val status = when {
            preview.error != null -> preview.error
            preview.rendering -> "Rendering…"
            preview.millis != null -> "Preview rendered in ${seconds(preview.millis)} s"
            else -> ""
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = if (preview.error != null) t.error else t.textMuted)
        for (warning in preview.warnings) {
            Text(warning, style = MaterialTheme.typography.bodySmall, color = t.error)
        }
    }
}

@Composable
private fun MoreMenu(busy: Boolean, onOpen: () -> Unit, onSaveSettings: () -> Unit, onShare: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(EditorIcons.More, contentDescription = "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Open file…") }, onClick = { open = false; onOpen() })
            DropdownMenuItem(text = { Text("Save settings…") }, onClick = { open = false; onSaveSettings() })
            DropdownMenuItem(text = { Text("Share image") }, enabled = !busy, onClick = { open = false; onShare() })
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

@Composable
private fun Actions(saving: Boolean, onVariation: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(onClick = onVariation, modifier = Modifier.weight(1f)) { Text("New variation") }
        Button(onClick = onSave, enabled = !saving, modifier = Modifier.weight(1f)) {
            Text(if (saving) "Saving…" else "Save to gallery")
        }
    }
}

@Composable
private fun Controls(params: MandalaParameters, selected: Int, vm: EditorViewModel) {
    val index = selected.coerceIn(0, params.layers.lastIndex)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LayersSection(params, index, vm)
        SectionTitle("Layer ${index + 1} · ${params.layers[index].style.displayName}")
        LayerControls(params.layers[index], vm::updateSelected)
    }
}

/** The layer list, top layer first (it is drawn last, on top), plus add, duplicate and delete. */
@Composable
private fun LayersSection(params: MandalaParameters, selected: Int, vm: EditorViewModel) {
    val t = LocalTokens.current
    val layers = params.layers
    SectionTitle("Layers")
    Text("The top layer is drawn over the ones below.", style = MaterialTheme.typography.bodySmall, color = t.textMuted)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.sm)) {
        for (i in layers.indices.reversed()) {
            LayerRow(
                number = i + 1,
                layer = layers[i],
                selected = i == selected,
                canMoveUp = i < layers.lastIndex,
                canMoveDown = i > 0,
                onSelect = { vm.select(i) },
                onToggle = { vm.updateLayer(i) { it.copy(isEnabled = !it.isEnabled) } },
                onMoveUp = { vm.moveLayer(i, +1) },
                onMoveDown = { vm.moveLayer(i, -1) },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        TextButton(onClick = vm::addLayer, enabled = layers.size < Editing.MAX_LAYERS) { Text("Add layer") }
        TextButton(onClick = vm::duplicateSelected, enabled = layers.size < Editing.MAX_LAYERS) { Text("Duplicate") }
        TextButton(onClick = vm::removeSelected, enabled = layers.size > 1) { Text("Delete") }
    }
}

@Composable
private fun LayerRow(
    number: Int,
    layer: StyleLayer,
    selected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val t = LocalTokens.current
    val palette = BuiltInPalettes.all[layer.paletteIndex.coerceIn(0, BuiltInPalettes.all.lastIndex)]
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (selected) t.accentSoft else t.surface)
            .clickable(onClick = onSelect)
            .semantics { this.selected = selected }
            .padding(start = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Sizes.swatchSmall).clip(CircleShape).background(paletteBrush(palette)))
        Column(Modifier.weight(1f).padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text("$number · ${layer.style.displayName}", style = MaterialTheme.typography.bodyMedium,
                color = if (layer.isEnabled) t.text else t.textMuted)
            Text("${layer.blendMode.displayName} · ${(layer.opacity * 100).roundToInt()} %",
                style = MaterialTheme.typography.bodySmall, color = t.textMuted)
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (layer.isEnabled) EditorIcons.Visible else EditorIcons.Hidden,
                contentDescription = if (layer.isEnabled) "Hide layer $number" else "Show layer $number",
                tint = if (layer.isEnabled) t.text else t.textMuted,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(EditorIcons.MoveUp, contentDescription = "Move layer $number up")
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(EditorIcons.MoveDown, contentDescription = "Move layer $number down")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerControls(layer: StyleLayer, update: ((StyleLayer) -> StyleLayer) -> Unit) {
    SubTitle("Style")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (style in Editing.styles) {
            FilterChip(
                selected = layer.style == style,
                onClick = { update { it.copy(style = style) } },
                label = { Text(style.displayName) },
            )
        }
    }

    SubTitle("Palette")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        BuiltInPalettes.all.forEachIndexed { index, palette ->
            Swatch(palette, selected = layer.paletteIndex == index) { update { it.copy(paletteIndex = index) } }
        }
    }

    SubTitle("Blending")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (mode in LayerBlendMode.entries) {
            FilterChip(
                selected = layer.blendMode == mode,
                onClick = { update { it.copy(blendMode = mode) } },
                label = { Text(mode.displayName) },
            )
        }
    }
    ValueSlider("Opacity", layer.opacity) { v -> update { it.copy(opacity = v) } }

    SubTitle("Shape")
    SteppedSlider("Symmetry", layer.symmetry, 1..8) { v -> update { it.copy(symmetry = v) } }
    ValueSlider("Scale", layer.scale, 0.1f..1.2f) { v -> update { it.copy(scale = v) } }
    ValueSlider("Complexity", layer.complexity) { v -> update { it.copy(complexity = v) } }
    ValueSlider("Density", layer.density) { v -> update { it.copy(density = v) } }
    ValueSlider("Ripple", layer.ripple) { v -> update { it.copy(ripple = v) } }

    SubTitle("Light and colour")
    ValueSlider("Glow", layer.glowIntensity) { v -> update { it.copy(glowIntensity = v) } }
    ValueSlider("Colour drift", layer.colorDrift) { v -> update { it.copy(colorDrift = v) } }
    ValueSlider("Painted", layer.abstractLevel) { v -> update { it.copy(abstractLevel = v) } }
    ValueSlider("Saturation", layer.saturation) { v -> update { it.copy(saturation = v) } }
    ValueSlider("Brightness", layer.brightness) { v -> update { it.copy(brightness = v) } }
}

@Composable
private fun SubTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = LocalTokens.current.textMuted,
        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs))
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs))
}

@Composable
private fun Swatch(palette: ColorPalette, selected: Boolean, onClick: () -> Unit) {
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

@Composable
private fun ValueSlider(label: String, value: Double, range: ClosedFloatingPointRange<Float> = 0f..1f, onChange: (Double) -> Unit) {
    SliderRow(label, String.format(Locale.US, "%.2f", value)) {
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toDouble()) }, valueRange = range)
    }
}

@Composable
private fun SteppedSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    SliderRow(label, value.toString()) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}

@Composable
private fun SliderRow(label: String, value: String, slider: @Composable () -> Unit) {
    val t = LocalTokens.current
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = t.textMuted, textAlign = TextAlign.End)
        }
        slider()
    }
}

private fun paletteBrush(palette: ColorPalette): Brush =
    Brush.sweepGradient(palette.stops.map { Color(it.color.r.toFloat(), it.color.g.toFloat(), it.color.b.toFloat(), 1f, ColorSpaces.DisplayP3) })

private fun seconds(millis: Long): String = String.format(Locale.US, "%.1f", millis / 1000.0)
