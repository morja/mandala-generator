package com.mathis.mandala.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import com.mathis.mandala.app.Editing.strokeCount
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import com.mathis.mandala.core.Quote
import com.mathis.mandala.core.Quotes
import com.mathis.mandala.core.TextLayerSettings
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Switch
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
import com.mathis.mandala.core.BaseLayerSettings
import com.mathis.mandala.core.BaseLayerType
import com.mathis.mandala.core.EffectsLayerSettings
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
    val drawTool by vm.drawTool.collectAsStateWithLifecycle()
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
                            Canvas(preview, Modifier.width(side), drawing(params, drawTool, vm))
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
                    Canvas(
                        preview,
                        Modifier.widthIn(max = canvasMax).fillMaxWidth().align(Alignment.CenterHorizontally),
                        drawing(params, drawTool, vm),
                    )
                    actions()
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = Spacing.lg)) {
                        Controls(params, selected, vm)
                    }
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

private fun hsbColour(h: Double, s: Double, b: Double): Color =
    Color.hsv((h * 360).toFloat().coerceIn(0f, 360f), s.toFloat().coerceIn(0f, 1f), b.toFloat().coerceIn(0f, 1f))

@Composable
private fun Canvas(preview: PreviewState, modifier: Modifier, drawing: DrawingInput? = null) {
    val t = LocalTokens.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
            drawing != null && !preview.rendering -> "Drag on the image to draw"
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
        BackgroundSection(params.baseLayer, vm::updateBackground)
        EffectsSection(params.effectsLayer, vm::updateEffects, vm::newEffectsPattern)
        TextSection(params.textLayer, vm::updateText, vm::randomQuote, vm::useQuote)
        DrawingSection(params, vm)
        LayersSection(params, index, vm)
        SectionTitle("Layer ${index + 1} · ${params.layers[index].style.displayName}")
        LayerControls(params.layers[index], vm::updateSelected)
    }
}

/** Background behind all layers: off, a colour, a gradient or sunburst rays, with fitting sliders. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundSection(base: BaseLayerSettings, update: ((BaseLayerSettings) -> BaseLayerSettings) -> Unit) {
    SectionTitle("Background")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChip(selected = !base.isEnabled, onClick = { update { it.copy(isEnabled = false) } }, label = { Text("Off") })
        for (type in Editing.backgrounds) {
            FilterChip(
                selected = base.isEnabled && base.type == type,
                onClick = { update { it.copy(isEnabled = true, type = type) } },
                label = { Text(backgroundName(type)) },
            )
        }
    }
    if (!base.isEnabled || base.type !in Editing.backgrounds) return

    val twoColours = base.type != BaseLayerType.COLOR && base.type != BaseLayerType.GRAIN
    ColourSliders(if (twoColours) "Colour 1" else "Colour", base.hue, base.saturation, base.brightness) { h, s, b ->
        update { it.copy(hue = h, saturation = s, brightness = b) }
    }
    if (twoColours) {
        ColourSliders("Colour 2", base.hue2, base.saturation2, base.brightness2) { h, s, b ->
            update { it.copy(hue2 = h, saturation2 = s, brightness2 = b) }
        }
    }
    when (base.type) {
        BaseLayerType.GRADIENT -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Radial", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = base.isRadial, onCheckedChange = { r -> update { it.copy(isRadial = r) } })
            }
            if (!base.isRadial) ValueSlider("Angle", base.gradientAngle) { v -> update { it.copy(gradientAngle = v) } }
        }
        BaseLayerType.SUNBURST -> {
            ValueSlider("Rays", base.patternScale) { v -> update { it.copy(patternScale = v) } }
            ValueSlider("Sharpness", base.patternSharpness) { v -> update { it.copy(patternSharpness = v) } }
        }
        BaseLayerType.GRAIN -> {
            ValueSlider("Grain amount", base.grainAmount) { v -> update { it.copy(grainAmount = v) } }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Coloured grain", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = base.grainColored, onCheckedChange = { c -> update { it.copy(grainColored = c) } })
            }
        }
        else -> Unit
    }
    ValueSlider("Background opacity", base.opacity) { v -> update { it.copy(opacity = v) } }
}

@Composable
private fun ColourSliders(title: String, hue: Double, saturation: Double, brightness: Double, onChange: (Double, Double, Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
        Box(
            Modifier
                .size(Sizes.swatchSmall)
                .clip(CircleShape)
                .background(Color.hsv((hue * 360).toFloat().coerceIn(0f, 360f), saturation.toFloat(), brightness.toFloat())),
        )
        Spacer(Modifier.width(Spacing.sm))
        SubTitle(title)
    }
    ValueSlider("Hue", hue) { v -> onChange(v, saturation, brightness) }
    ValueSlider("Saturation", saturation) { v -> onChange(hue, v, brightness) }
    ValueSlider("Brightness", brightness) { v -> onChange(hue, saturation, v) }
}

private fun backgroundName(type: BaseLayerType) = when (type) {
    BaseLayerType.COLOR -> "Colour"
    BaseLayerType.GRADIENT -> "Gradient"
    BaseLayerType.SUNBURST -> "Sunburst"
    BaseLayerType.GRAIN -> "Grain"
    else -> type.name.lowercase()
}

/**
 * The effects layer over the whole image. Off by default; when on, its sliders can be folded away
 * so the page stays short.
 */
@Composable
private fun EffectsSection(
    fx: EffectsLayerSettings,
    update: ((EffectsLayerSettings) -> EffectsLayerSettings) -> Unit,
    newPattern: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
        Text("Effects", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (fx.isEnabled) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide sliders" else "Show sliders") }
        }
        Switch(checked = fx.isEnabled, onCheckedChange = { on -> update { it.copy(isEnabled = on) }; if (on) expanded = true })
    }
    if (!fx.isEnabled || !expanded) return

    SubTitle("Light")
    ValueSlider("Brightness", fx.brightness) { v -> update { it.copy(brightness = v) } }
    ValueSlider("Contrast", fx.contrast) { v -> update { it.copy(contrast = v) } }
    ValueSlider("Vignette", fx.vignette) { v -> update { it.copy(vignette = v) } }
    ValueSlider("Bloom", fx.bloom) { v -> update { it.copy(bloom = v) } }
    ValueSlider("Highlights", fx.highlights) { v -> update { it.copy(highlights = v) } }

    SubTitle("Tone")
    ValueSlider("Wash", fx.wash) { v -> update { it.copy(wash = v) } }
    ValueSlider("Sepia", fx.sepia) { v -> update { it.copy(sepia = v) } }
    ValueSlider("Fade", fx.fade) { v -> update { it.copy(fade = v) } }

    SubTitle("Texture")
    ValueSlider("Grain", fx.grain) { v -> update { it.copy(grain = v) } }
    ValueSlider("Clarity", fx.localContrast) { v -> update { it.copy(localContrast = v) } }
    ValueSlider("Relief", fx.relief) { v -> update { it.copy(relief = v) } }
    if (fx.relief > 0) ValueSlider("Relief angle", fx.reliefAngle) { v -> update { it.copy(reliefAngle = v) } }
    ValueSlider("Chromatic", fx.chromatic) { v -> update { it.copy(chromatic = v) } }

    SubTitle("Spots and sparkle")
    ValueSlider("Dimming", fx.dimming) { v -> update { it.copy(dimming = v) } }
    ValueSlider("Erasure", fx.erasure) { v -> update { it.copy(erasure = v) } }
    ValueSlider("Stars", fx.stars) { v -> update { it.copy(stars = v) } }
    ValueSlider("Glitter", fx.glitter) { v -> update { it.copy(glitter = v) } }
    TextButton(onClick = newPattern) { Text("New pattern") }
}

/**
 * Drawing by hand, as on the Mac: glowing lines (drawing layer) or soft spray paint (graffiti layer),
 * each repeated around the centre. "Draw on image" turns drags on the canvas into strokes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrawingSection(params: MandalaParameters, vm: EditorViewModel) {
    val active by vm.drawTool.collectAsStateWithLifecycle()
    var tool by rememberSaveable { mutableStateOf(DrawTool.LINES) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val count = params.strokeCount(tool)
    val lines = params.drawingLayer
    val spray = params.graffitiLayer

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
        Text("Drawing", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide sliders" else "Show sliders") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        for (option in DrawTool.entries) {
            FilterChip(
                selected = tool == option,
                onClick = {
                    tool = option
                    if (active != null) vm.setDrawTool(option)
                },
                label = { Text("${option.label} (${params.strokeCount(option)})") },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.xs)) {
        if (active == null) {
            Button(onClick = { vm.setDrawTool(tool) }) { Text("Draw on image") }
        } else {
            FilledTonalButton(onClick = { vm.setDrawTool(null) }) { Text("Done drawing") }
        }
        OutlinedButton(onClick = { vm.removeLastStroke(tool) }, enabled = count > 0) { Text("Undo") }
        OutlinedButton(onClick = { vm.clearStrokes(tool) }, enabled = count > 0) { Text("Clear") }
    }
    val enabled = if (tool == DrawTool.LINES) lines.isEnabled else spray.isEnabled
    if (count > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Show ${tool.label.lowercase()}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { on ->
                if (tool == DrawTool.LINES) vm.updateDrawing { it.copy(isEnabled = on) } else vm.updateGraffiti { it.copy(isEnabled = on) }
            })
        }
    }

    when (tool) {
        DrawTool.LINES -> {
            SteppedSlider("Symmetry", lines.symmetry.coerceIn(1, 16), 1..16) { v -> vm.updateDrawing { it.copy(symmetry = v) } }
            ColourSliders("Line colour", lines.currentHue, lines.currentSaturation, lines.currentBrightness) { h, sa, b ->
                vm.updateDrawing { it.copy(currentHue = h, currentSaturation = sa, currentBrightness = b) }
            }
            if (!expanded) return
            ValueSlider("Weight", lines.strokeWeight) { v -> vm.updateDrawing { it.copy(strokeWeight = v) } }
            ValueSlider("Glow", lines.glowIntensity) { v -> vm.updateDrawing { it.copy(glowIntensity = v) } }
            ValueSlider("Opacity", lines.opacity) { v -> vm.updateDrawing { it.copy(opacity = v) } }
            BlendChips(lines.blendMode) { m -> vm.updateDrawing { it.copy(blendMode = m) } }
        }
        DrawTool.SPRAY -> {
            SteppedSlider("Symmetry", spray.symmetry.coerceIn(1, 16), 1..16) { v -> vm.updateGraffiti { it.copy(symmetry = v) } }
            ColourSliders("Spray colour", spray.currentHue, spray.currentSaturation, spray.currentBrightness) { h, sa, b ->
                vm.updateGraffiti { it.copy(currentHue = h, currentSaturation = sa, currentBrightness = b) }
            }
            ValueSlider("Brush size", spray.currentBrushSize, 0.01f..0.2f) { v -> vm.updateGraffiti { it.copy(currentBrushSize = v) } }
            ValueSlider("Paint opacity", spray.currentOpacity) { v -> vm.updateGraffiti { it.copy(currentOpacity = v) } }
            if (!expanded) return
            ValueSlider("Softness", spray.softness) { v -> vm.updateGraffiti { it.copy(softness = v) } }
            ValueSlider("Layer opacity", spray.opacity) { v -> vm.updateGraffiti { it.copy(opacity = v) } }
            BlendChips(spray.blendMode) { m -> vm.updateGraffiti { it.copy(blendMode = m) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlendChips(mode: LayerBlendMode, onChange: (LayerBlendMode) -> Unit) {
    SubTitle("Blending")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (m in LayerBlendMode.entries) {
            FilterChip(selected = mode == m, onClick = { onChange(m) }, label = { Text(m.displayName) })
        }
    }
}

/** Text over the mandala: quotes from the macOS app's collection or own text, with font and light. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextSection(
    text: TextLayerSettings,
    update: ((TextLayerSettings) -> TextLayerSettings) -> Unit,
    randomQuote: () -> Unit,
    useQuote: (Quote) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
        Text("Text", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (text.isEnabled) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide sliders" else "Show sliders") }
        }
        Switch(checked = text.isEnabled, onCheckedChange = { on ->
            if (on && text.text.isEmpty()) randomQuote() else update { it.copy(isEnabled = on) }
        })
    }
    if (!text.isEnabled) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(onClick = randomQuote) { Text("Random quote") }
        OutlinedButton(onClick = { choosing = true }) { Text("Choose quote") }
    }
    OutlinedTextField(
        value = text.text,
        onValueChange = { v -> update { it.copy(text = v, customAuthor = if (v == it.text) it.customAuthor else Quotes.authorOf(v) ?: "") } },
        label = { Text("Text") },
        maxLines = 4,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Show author", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = text.showAuthor, onCheckedChange = { a -> update { it.copy(showAuthor = a) } })
    }
    if (choosing) QuoteDialog(onPick = { useQuote(it); choosing = false }, onDismiss = { choosing = false })
    if (!expanded) return

    SubTitle("Font")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for ((name, display) in Quotes.fonts) {
            FilterChip(selected = text.fontName == name, onClick = { update { it.copy(fontName = name) } }, label = { Text(display) })
        }
    }
    ValueSlider("Size", text.fontSize, 0.02f..0.2f) { v -> update { it.copy(fontSize = v) } }
    ValueSlider("Width", text.fontWidth, 0.1f..1f) { v -> update { it.copy(fontWidth = v) } }
    ValueSlider("Height on image", text.offsetY) { v -> update { it.copy(offsetY = v) } }
    SubTitle("Light")
    ValueSlider("Glow", text.glow) { v -> update { it.copy(glow = v) } }
    ValueSlider("Cloud", text.cloudOpacity) { v -> update { it.copy(cloudOpacity = v) } }
    ValueSlider("Cloud size", text.cloudScale) { v -> update { it.copy(cloudScale = v) } }
    ValueSlider("Shadow", text.shadowOpacity) { v -> update { it.copy(shadowOpacity = v) } }
    SubTitle("Colour")
    ValueSlider("Hue", text.hue) { v -> update { it.copy(hue = v) } }
    ValueSlider("Saturation", text.saturation) { v -> update { it.copy(saturation = v) } }
    ValueSlider("Brightness", text.brightness, 0f..1.5f) { v -> update { it.copy(brightness = v) } }
}

@Composable
private fun QuoteDialog(onPick: (Quote) -> Unit, onDismiss: () -> Unit) {
    val t = LocalTokens.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Quotes") },
        text = {
            LazyColumn {
                items(Quotes.all) { q ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(q) }.padding(vertical = Spacing.sm)) {
                        Text(q.text, style = MaterialTheme.typography.bodyMedium)
                        Text(q.author, style = MaterialTheme.typography.bodySmall, color = t.textMuted)
                    }
                }
            }
        },
    )
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
    for ((group, styles) in Editing.styleGroups) {
        Text(group, style = MaterialTheme.typography.bodySmall, color = LocalTokens.current.textMuted,
            modifier = Modifier.padding(top = Spacing.xs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (style in styles) {
                FilterChip(
                    selected = layer.style == style,
                    onClick = { update { it.copy(style = style) } },
                    label = { Text(style.displayName) },
                )
            }
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
