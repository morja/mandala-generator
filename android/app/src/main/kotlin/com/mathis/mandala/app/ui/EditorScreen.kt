package com.mathis.mandala.app.ui

import kotlinx.coroutines.launch
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.roundToInt
import com.mathis.mandala.app.Editing
import androidx.activity.compose.LocalActivity
import com.mathis.mandala.app.ProState
import com.mathis.mandala.app.ProRequest
import androidx.compose.material3.OutlinedButton
import com.mathis.mandala.app.screenAspect
import com.mathis.mandala.app.screenSize
import com.mathis.mandala.app.pairLabel
import com.mathis.mandala.app.liveLabel
import com.mathis.mandala.app.LiveWallpaper
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.CircularProgressIndicator
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
import com.mathis.mandala.app.MoveTarget
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.mathis.mandala.core.VectorExport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    vm: EditorViewModel,
    onAbout: () -> Unit = {},
    lightTheme: Boolean = false,
    onLightTheme: (Boolean) -> Unit = {},
) {
    val params by vm.params.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val export by vm.export.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val drawTool by vm.drawTool.collectAsStateWithLifecycle()
    val moveTarget by vm.moveTarget.collectAsStateWithLifecycle()
    val history by vm.historyState.collectAsStateWithLifecycle()
    val pro by vm.proState.collectAsStateWithLifecycle()
    val saveChoice by vm.saveChoice.collectAsStateWithLifecycle()
    val framePreview by vm.framePreview.collectAsStateWithLifecycle()
    val shownFraming by vm.shownFraming.collectAsStateWithLifecycle()
    val phoneAspect by vm.screenAspect.collectAsStateWithLifecycle()
    val fastPreview by vm.fastPreview.collectAsStateWithLifecycle()
    val candidate by vm.candidate.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }
    val proRequest by vm.proRequest.collectAsStateWithLifecycle()
    val missingPro by vm.missingPro.collectAsStateWithLifecycle()
    var showPair by remember { mutableStateOf(false) }
    var showLive by remember { mutableStateOf(false) }
    val live by vm.live.collectAsStateWithLifecycle()
    val pairPreview by vm.pairPreview.collectAsStateWithLifecycle()
    val daily by vm.daily.collectAsStateWithLifecycle()
    LaunchedEffect(showPair) { vm.setPairOpen(showPair) }
    // The wallpaper pair is Pro; without it the entry opens the Pro sheet.
    val openPair = { if (pro.owned) showPair = true else vm.showPro() }
    val openLive = { if (pro.owned) showLive = true else vm.showPro() }
    // Kept when Android recreates the screen, which setting a wallpaper does.
    var showSave by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showFavorites by remember { mutableStateOf(false) }
    // A message goes as soon as the next action happens: a change to the mandala (other than the one
    // it reports) or opening a sheet. Its Undo would no longer fit anyway.
    var messageFor by remember { mutableStateOf<MandalaParameters?>(null) }
    LaunchedEffect(params) { if (messageFor != null && params != messageFor) snackbar.currentSnackbarData?.dismiss() }
    LaunchedEffect(showSave, showFavorites, proRequest, showMenu) {
        if (showSave || showFavorites || proRequest != null || showMenu) snackbar.currentSnackbarData?.dismiss()
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::openFile) }
    val saveSettings = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::saveSettingsTo)
    }
    // The look chosen in the save sheet waits here while Android's file picker is open.
    var lineDrawingLook by remember { mutableStateOf(VectorExport.Look.COLOUR) }
    val saveLineDrawing = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")) { uri ->
        uri?.let { vm.saveLineDrawing(lineDrawingLook, it) }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is EditorEvent.Message -> {
                    // Short, also with Undo: with an action Compose would keep the message until it is tapped.
                    messageFor = vm.params.value
                    val result = snackbar.showSnackbar(
                        event.text,
                        actionLabel = if (event.undo != null) "Undo" else null,
                        duration = SnackbarDuration.Short,
                    )
                    messageFor = null
                    if (result == SnackbarResult.ActionPerformed) event.undo?.invoke()
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

    val activity = LocalActivity.current
    LaunchedEffect(activity) { activity?.let { vm.setScreen(screenAspect(it), screenSize(it).first) } }
    LaunchedEffect(showSave) { vm.setSaveOpen(showSave) }
    if (showSave) {
        SaveSheet(
            choice = saveChoice,
            framing = shownFraming,
            preview = framePreview,
            screenAspect = phoneAspect,
            proOwned = pro.owned,
            hasQuote = params.textLayer.isEnabled && params.textLayer.text.isNotEmpty(),
            busy = busy,
            export = export,
            onFormat = vm::setSaveFormat,
            onFraming = vm::setFraming,
            onTextFollows = vm::setTextFollowsMandala,
            onSave = vm::saveImage,
            onWallpaper = { vm.setWallpaper(it) },
            onFill = vm::setFill,
            onFinish = vm::setFinish,
            onPair = { showSave = false; openPair() },
            onLive = { showSave = false; openLive() },
            hasPointLayers = VectorExport.hasPointLayers(params),
            onLineDrawing = { look -> lineDrawingLook = look; saveLineDrawing.launch(vm.suggestedLineDrawingName(look)) },
            onShowPro = vm::showPro,
            onDismiss = { showSave = false },
            snackbar = snackbar,
        )
    }
    if (showPair) {
        PairSheet(
            preview = pairPreview,
            screenAspect = Editing.aspect(Editing.SaveFormat.SCREEN, phoneAspect),
            homeBrightness = saveChoice.homeBrightness,
            busy = busy,
            export = export,
            onHomeBrightness = vm::setHomeBrightness,
            onSet = vm::setWallpaperPair,
            onDismiss = { showPair = false },
            snackbar = snackbar,
        )
    }
    if (showLive) {
        LiveSheet(
            settings = live,
            onSettings = vm::setLiveSettings,
            onSet = {
                showLive = false
                // Android previews it and sets it when the user agrees; once on, it follows by itself.
                if (!vm.useForLiveWallpaper()) runCatching { context.startActivity(LiveWallpaper.pickIntent(context)) }
            },
            onDismiss = { showLive = false },
        )
    }
    proRequest?.let { request ->
        ProSheet(pro, request, onBuy = { activity?.let(vm::buyPro) }, onWithout = vm::continueWithoutPro, onDismiss = vm::closePro)
    }
    LaunchedEffect(pro.owned) { if (pro.owned) vm.closePro() }

    if (showMenu) {
        // Every entry but the dice closes the sheet, so its result or message shows.
        fun closing(action: () -> Unit): () -> Unit = { showMenu = false; vm.closeCandidate(); action() }
        MenuSheet(
            params = params,
            image = preview.image,
            candidate = candidate,
            busy = busy,
            fastPreview = fastPreview,
            lightTheme = lightTheme,
            actions = MenuActions(
                onFavourite = closing(vm::addFavorite),
                onShare = closing(vm::shareImage),
                onWallpaper = { target -> showMenu = false; vm.closeCandidate(); vm.setWallpaper(target, filled = true) },
                onPair = { showMenu = false; vm.closeCandidate(); openPair() },
                pairLabel = pairLabel(pro.owned),
                onLive = { showMenu = false; vm.closeCandidate(); openLive() },
                liveLabel = liveLabel(pro.owned),
                dailyOn = daily.on,
                dailySubtitle = when {
                    !pro.owned -> "Your favourites in turn, every morning · Pro"
                    daily.on && daily.lastName != null -> "Today: ${daily.lastName}"
                    else -> "Your favourites in turn, on lock and home screen"
                },
                onDaily = { on -> if (pro.owned) vm.setDaily(on) else vm.showPro() },
                onTryNew = vm::tryNew,
                onUseNew = { showMenu = false; vm.useCandidate() },
                onBackToMenu = vm::closeCandidate,
                onOpen = closing { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                onSaveSettings = closing { saveSettings.launch(vm.suggestedSettingsName()) },
                onFastPreview = vm::setFastPreview,
                onLightTheme = onLightTheme,
                onAbout = closing(onAbout),
            ),
            onDismiss = { showMenu = false; vm.closeCandidate() },
        )
    }

    if (showFavorites) {
        FavoritesSheet(
            favorites = favorites,
            canAdd = preview.image != null,
            onAdd = vm::addFavorite,
            onApply = { vm.applyFavorite(it); showFavorites = false },
            onDelete = { vm.removeFavorite(it.id) },
            onDismiss = { showFavorites = false },
            snackbar = snackbar,
        )
    }

    Scaffold(
        topBar = {
            // Undo and redo on the left; dice, save, favourites and the menu on the right. The title
            // only fits next to them on wide screens.
            val wide = LocalConfiguration.current.screenWidthDp.dp >= Spacing.twoPaneMinWidth
            TopAppBar(
                title = { if (wide) Text("Dahlia") },
                navigationIcon = {
                    Row {
                        IconButton(onClick = vm::goBack, enabled = history.canGoBack) { Icon(EditorIcons.Back, contentDescription = "Back") }
                        IconButton(onClick = vm::goForward, enabled = history.canGoForward) {
                            Icon(EditorIcons.Forward, contentDescription = "Forward")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::surprise) { Icon(EditorIcons.DieFive, contentDescription = "Surprise me") }
                    IconButton(onClick = { showSave = true }, enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(Sizes.icon), strokeWidth = Sizes.progress)
                        } else {
                            Icon(EditorIcons.Save, contentDescription = "Save image", tint = LocalTokens.current.accent)
                        }
                    }
                    IconButton(onClick = { showFavorites = true }) { Icon(EditorIcons.Star, contentDescription = "Favourites") }
                    IconButton(onClick = { showMenu = true }) { Icon(EditorIcons.More, contentDescription = "More") }
                },
            )
        },
        snackbarHost = {
            if (!showFavorites && !showSave && !showPair) {
                // A running export (from the menu) shows where its message will follow.
                export?.let { ExportNote(it) } ?: MessageHost(snackbar)
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            if (maxWidth >= Spacing.twoPaneMinWidth) {
                Row(Modifier.fillMaxSize().padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                    // The square canvas takes the smaller of width and remaining height.
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        val side = minOf(maxWidth, maxHeight - Sizes.statusLine)
                        Canvas(preview, Modifier.width(side), drawing(params, drawTool, vm), moving(params, selected, moveTarget, vm),
                            proMark = if (missingPro.isNotEmpty()) vm::showPro else null)
                    }
                    ControlTabs(params, selected, vm, Modifier.width(Spacing.controlsWidth).fillMaxHeight(), pro.owned, onShowPro = { vm.showPro() })
                }
            } else {
                // The canvas stays in view; only the open tab scrolls, so every change is visible.
                val canvasMax = maxHeight * 0.5f
                var header by remember { mutableStateOf<ControlHeader?>(null) }
                FoldingCanvas(
                    preview = preview,
                    header = header,
                    fullSide = minOf(maxWidth - Spacing.lg * 2, canvasMax),
                    drawing = drawing(params, drawTool, vm),
                    move = moving(params, selected, moveTarget, vm),
                    proMark = if (missingPro.isNotEmpty()) vm::showPro else null,
                ) { modifier ->
                    ControlTabs(params, selected, vm, modifier, pro.owned, onShowPro = { vm.showPro() }, onHeader = { header = it })
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

/** What the canvas needs in the move mode (ADR-0011): where the target sits, about how large it is, and where gestures go. */
private class MoveInput(
    /** Centre as fractions of the canvas, and a ring radius as a fraction of its width. */
    val x: Float,
    val y: Float,
    val radius: Float,
    val label: String,
    val onMove: (dx: Double, dy: Double, zoom: Double, turnDegrees: Double) -> Unit,
)

private fun moving(params: MandalaParameters, selected: Int, target: MoveTarget?, vm: EditorViewModel): MoveInput? = when (target) {
    null -> null
    MoveTarget.LAYER -> params.layers.getOrNull(selected)?.let { l ->
        MoveInput((0.5 + l.offsetX).toFloat(), (0.5 + l.offsetY).toFloat(), (0.36 * l.scale).toFloat(), "layer ${selected + 1}", vm::moveBy)
    }
    MoveTarget.EMBLEM -> params.emblemLayer.let { e ->
        MoveInput((0.5 + e.offsetX).toFloat(), (0.5 + e.offsetY).toFloat(), (0.5 * e.size).toFloat(), "the emblem", vm::moveBy)
    }
}

/**
 * Phones: a button in the image's bottom right corner folds it into a small square at the top left,
 * with what the open tab is about beside it, so the controls get the room; the button, a tap on the
 * small image, or pulling the controls down once they are at the top brings it back (DM-4, variant
 * B). The size stays as chosen while the controls scroll. Folding only scales the shown preview,
 * so it never renders. Drawing and moving keep it large.
 */
@Composable
private fun FoldingCanvas(
    preview: PreviewState,
    header: ControlHeader?,
    fullSide: androidx.compose.ui.unit.Dp,
    drawing: DrawingInput?,
    move: MoveInput?,
    proMark: (() -> Unit)?,
    controls: @Composable (Modifier) -> Unit,
) {
    val t = LocalTokens.current
    var small by rememberSaveable { mutableStateOf(false) }
    val pinned = drawing != null || move != null
    // 0 = full image, 1 = small; follows a pull while the finger is down.
    val foldAnim = remember { androidx.compose.animation.core.Animatable(if (small) 1f else 0f) }
    LaunchedEffect(small, pinned) { foldAnim.animateTo(if (small && !pinned) 1f else 0f) }
    val fold = foldAnim.value
    val compactSide = fullSide * Editing.COMPACT_CANVAS_FRACTION
    val range = with(androidx.compose.ui.platform.LocalDensity.current) { (fullSide - compactSide).toPx() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Pulling down past the top of the controls grows the small image with the finger; let go past a
    // third of the way and it becomes large. Only a drag counts, not a fling that reaches the top.
    val pull = remember { floatArrayOf(0f) }
    val connection = remember(small, pinned, range) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (pull[0] <= 0f || available.y >= 0f) return Offset.Zero
                val used = maxOf(available.y, -pull[0])
                pull[0] += used
                scope.launch { foldAnim.snapTo((1f - pull[0] / range).coerceIn(0f, 1f)) }
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (!small || pinned || range <= 0f || available.y <= 0f ||
                    source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput
                ) return Offset.Zero
                pull[0] += available.y
                scope.launch { foldAnim.snapTo((1f - pull[0] / range).coerceIn(0f, 1f)) }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (pull[0] <= 0f) return androidx.compose.ui.unit.Velocity.Zero
                val enough = pull[0] > range / 3f
                pull[0] = 0f
                if (enough) small = false else foldAnim.animateTo(1f)
                return available
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        val side = fullSide + (compactSide - fullSide) * fold
        // Centred when full (if narrower than the screen), at the left when small.
        val inset = (maxWidth - fullSide) / 2 * (1f - fold)
        Column(Modifier.fillMaxSize().nestedScroll(connection)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(start = inset)) {
                Canvas(
                    preview,
                    Modifier.width(side),
                    drawing,
                    move,
                    showStatus = fold < 0.5f,
                    onTap = if (small && !pinned) ({ small = false }) else null,
                    // Too small to tap when folded; the controls still carry their marks.
                    proMark = proMark.takeIf { fold < 0.5f },
                ) {
                    // Bottom right, where the thumb reaches it.
                    if (!pinned) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(Spacing.xs)
                                .clip(CircleShape)
                                .background(t.surface)
                                .clickable(onClickLabel = if (small) "Show the image large" else "Make the image small") { small = !small }
                                .padding(Spacing.xs),
                        ) {
                            Icon(
                                if (small) EditorIcons.Enlarge else EditorIcons.Shrink,
                                contentDescription = if (small) "Larger image" else "Smaller image",
                                tint = t.text,
                                modifier = Modifier.size(Sizes.icon),
                            )
                        }
                    }
                }
                if (fold > 0.5f && header != null) {
                    Column(
                        Modifier.padding(start = Spacing.md).alpha(((fold - 0.5f) * 2f).coerceIn(0f, 1f)),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(header.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(header.detail, style = MaterialTheme.typography.bodySmall, color = t.textMuted, maxLines = 1)
                    }
                }
            }
            controls(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun Canvas(
    preview: PreviewState,
    modifier: Modifier,
    drawing: DrawingInput? = null,
    move: MoveInput? = null,
    showStatus: Boolean = true,
    onTap: (() -> Unit)? = null,
    /** Shown while the mandala uses Pro features the user does not own (ADR-0016); opens the Pro sheet. */
    proMark: (() -> Unit)? = null,
    corner: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    val t = LocalTokens.current
    Column(modifier.padding(bottom = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Radius.md)).background(t.canvas)
                .then(if (onTap != null) Modifier.clickable(onClickLabel = "Show the image large", onClick = onTap) else Modifier),
            contentAlignment = Alignment.BottomCenter,
        ) {
            preview.image?.let {
                Image(it, contentDescription = "Mandala preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            if (drawing != null) DrawingOverlay(drawing, t.accent)
            if (move != null) MoveOverlay(move, t.accent, t.canvas)
            if (proMark != null) {
                Text(
                    "Pro",
                    style = MaterialTheme.typography.labelLarge,
                    color = t.accent,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Spacing.sm)
                        .clip(CircleShape)
                        .background(t.surface)
                        .clickable(onClickLabel = "What needs Pro", onClick = proMark)
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        .semantics { contentDescription = "This mandala uses Pro features" },
                )
            }
            corner()
            if (preview.rendering) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(Sizes.progress))
            }
        }
        val status = when {
            preview.error != null -> preview.error
            drawing != null -> "Drag on the image to draw"
            move != null -> "Drag to move ${move.label}, pinch to size and turn it"
            else -> null
        }
        if (!showStatus) return@Column
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

/**
 * Moves the selected layer or the emblem with the fingers: drag moves, pinch sizes, two fingers
 * turn. A ring shows where it is at once; the preview follows when the fingers rest.
 */
@Composable
private fun MoveOverlay(input: MoveInput, guide: Color, halo: Color) {
    val current by rememberUpdatedState(input)
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    current.onMove((pan.x / size.width).toDouble(), (pan.y / size.height).toDouble(), zoom.toDouble(), rotation.toDouble())
                }
            },
    ) {
        val c = Offset(current.x * size.width, current.y * size.height)
        // A dark edge under the accent keeps the ring visible over bright mandalas.
        drawCircle(halo, current.radius * size.width, c, style = Stroke(width = 7f))
        drawCircle(guide, current.radius * size.width, c, style = Stroke(width = 3f))
        drawCircle(halo, 10f, c)
        drawCircle(guide, 7f, c)
    }
}

/**
 * What Pro adds and the purchase button with Google Play's price (ADR-0016). Opened for a step the
 * mandala's Pro features block, it names them and offers the step without them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProSheet(pro: ProState, request: ProRequest, onBuy: () -> Unit, onWithout: () -> Unit, onDismiss: () -> Unit) {
    val t = LocalTokens.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Dahlia Pro", style = MaterialTheme.typography.titleLarge)
            if (request.features.isNotEmpty()) {
                Text(
                    if (request.action != null) "This mandala uses what comes with Pro. To keep it as it is, get Pro:"
                    else "This mandala uses what comes with Pro:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(Modifier.padding(start = Spacing.md)) {
                    for (feature in request.features) Text("· ${feature.label}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            request.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                "Pro adds up to five layers, emblems, text and quotes, and moving layers and the emblem. " +
                    "Save at 2048 pixels wide with the mandala placed by hand and adjusted, and as a line drawing. " +
                    "Wallpapers: home and lock as a pair, a new one every day, and a live wallpaper. " +
                    "Try everything before you buy. One purchase, no subscription.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textMuted,
            )
            Button(onClick = onBuy, enabled = pro.available, modifier = Modifier.fillMaxWidth()) {
                Text(if (pro.price != null) "Buy for ${pro.price}" else "Buy Pro")
            }
            if (request.features.isNotEmpty()) {
                OutlinedButton(onClick = onWithout, modifier = Modifier.fillMaxWidth()) {
                    Text(request.action?.let { "$it without them" } ?: "Remove them")
                }
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
    snackbar: SnackbarHostState,
) {
    val t = LocalTokens.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
      // Messages show inside the sheet while it is open; behind it they would be hidden. They sit near
      // the top, because the sheet's lower part can be below the screen while it is half open.
      Box {
        Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Favourites", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(onClick = onAdd, enabled = canAdd) { Text("Add current") }
            }
            // Below the title, so a message does not cover "Add current"; it takes no room while empty.
            MessageHost(snackbar)
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

