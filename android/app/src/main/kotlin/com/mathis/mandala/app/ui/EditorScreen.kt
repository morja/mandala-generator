package com.mathis.mandala.app.ui

import kotlinx.coroutines.launch
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.roundToInt
import com.mathis.mandala.app.Editing
import com.mathis.mandala.app.PreviewSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.onClick
import androidx.activity.compose.LocalActivity
import one.tearoom.unlock.UnlockMessage
import one.tearoom.unlock.UnlockState
import com.mathis.mandala.app.textRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import androidx.compose.foundation.border
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.res.stringResource
import com.mathis.mandala.app.R

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
    val makingVideo by vm.makingVideo.collectAsStateWithLifecycle()
    // A video takes minutes; the screen stays on so the phone does not sleep in the middle of it.
    val view = LocalView.current
    DisposableEffect(makingVideo) {
        view.keepScreenOn = makingVideo
        onDispose { view.keepScreenOn = false }
    }
    val selected by vm.selected.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val drawTool by vm.drawTool.collectAsStateWithLifecycle()
    val moveTarget by vm.moveTarget.collectAsStateWithLifecycle()
    val history by vm.historyState.collectAsStateWithLifecycle()
    val pro by vm.proState.collectAsStateWithLifecycle()
    // Google's guidance: ask Play on every resume (purchases made elsewhere, payments completed, refunds).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshPro() }
    val saveChoice by vm.saveChoice.collectAsStateWithLifecycle()
    val framePreview by vm.framePreview.collectAsStateWithLifecycle()
    val shownFraming by vm.shownFraming.collectAsStateWithLifecycle()
    val phoneAspect by vm.screenAspect.collectAsStateWithLifecycle()
    val fastPreview by vm.fastPreview.collectAsStateWithLifecycle()
    val previewSize by vm.previewSize.collectAsStateWithLifecycle()
    val plainPreview by vm.plainPreview.collectAsStateWithLifecycle()
    // Holding the preview shows it without the scene effects, only while they are on (UX-1 16).
    val compare = if (params.effectsLayer.isEnabled) Compare(plainPreview, vm::preparePlainPreview) else null
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
    val openPair = { if (pro.unlocked) showPair = true else vm.showPro() }
    val openLive = { if (pro.unlocked) showLive = true else vm.showPro() }
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

    val undoLabel = stringResource(R.string.editor_undo)
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is EditorEvent.Message -> {
                    // Short, also with Undo: with an action Compose would keep the message until it is tapped.
                    messageFor = vm.params.value
                    val result = snackbar.showSnackbar(
                        event.text,
                        actionLabel = if (event.undo != null) undoLabel else null,
                        duration = SnackbarDuration.Short,
                    )
                    messageFor = null
                    if (result == SnackbarResult.ActionPerformed) event.undo?.invoke()
                }
                is EditorEvent.ShareImage -> {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType(event.type)
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
    val play by vm.play.collectAsStateWithLifecycle()
    play?.let { state ->
        PlaySheet(
            state = state,
            image = preview.image,
            onMode = vm::setPlayMode,
            onTouch = vm::playTouch,
            onGesture = vm::playGesture,
            onReset = vm::resetPlay,
            onAgain = vm::playAgain,
            onKeep = vm::addFavorite,
            onDismiss = vm::closePlay,
        )
    }
    if (showSave) {
        SaveSheet(
            choice = saveChoice,
            framing = shownFraming,
            preview = framePreview,
            screenAspect = phoneAspect,
            proOwned = pro.unlocked,
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
            onShowPro = vm::showPro,
            onDismiss = { showSave = false },
            snackbar = snackbar,
            makingVideo = makingVideo,
            onMoving = vm::setMoving,
            onVideoSeconds = vm::setVideoSeconds,
            onSaveVideo = vm::saveVideo,
            onCancelVideo = vm::cancelVideo,
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
                vm.useForLiveWallpaper { runCatching { context.startActivity(LiveWallpaper.pickIntent(context)) } }
            },
            onDismiss = { showLive = false },
        )
    }
    proRequest?.let { request ->
        ProSheet(
            pro, request,
            onBuy = { activity?.let(vm::buyPro) },
            onRetry = vm::refreshPro,
            onRestore = vm::restorePro,
            onWithout = vm::continueWithoutPro,
            onDismiss = vm::closePro,
        )
    }
    LaunchedEffect(pro.unlocked) { if (pro.unlocked) vm.closePro() }

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
                pairLabel = pairLabel(pro.unlocked),
                onLive = { showMenu = false; vm.closeCandidate(); openLive() },
                liveLabel = liveLabel(pro.unlocked),
                dailyOn = daily.on,
                dailySubtitle = when {
                    !pro.unlocked -> stringResource(R.string.editor_daily_pro)
                    daily.on && daily.lastName != null -> stringResource(R.string.editor_daily_today, daily.lastName.orEmpty())
                    else -> stringResource(R.string.editor_daily_off)
                },
                onDaily = { on -> if (pro.unlocked) vm.setDaily(on) else vm.showPro() },
                favourites = favorites,
                dailyChosen = daily.chosen,
                onToggleDaily = vm::toggleDailyFavourite,
                onChooseAllDaily = vm::chooseAllDailyFavourites,
                lineDrawingPro = pro.unlocked,
                hasPointLayers = VectorExport.hasPointLayers(params),
                onLineDrawing = { look ->
                    if (pro.unlocked) {
                        showMenu = false
                        lineDrawingLook = look
                        saveLineDrawing.launch(vm.suggestedLineDrawingName(look))
                    } else {
                        vm.showPro()
                    }
                },
                onTryNew = vm::tryNew,
                onPlay = closing(vm::openPlay),
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
            onDelete = { ids -> if (ids.size == 1) vm.removeFavorite(ids.first()) else vm.removeFavorites(ids) },
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
                        IconButton(onClick = vm::goBack, enabled = history.canGoBack) { Icon(EditorIcons.Back, contentDescription = stringResource(R.string.editor_back)) }
                        IconButton(onClick = vm::goForward, enabled = history.canGoForward) {
                            Icon(EditorIcons.Forward, contentDescription = stringResource(R.string.editor_forward))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::surprise) { Icon(EditorIcons.DieFive, contentDescription = stringResource(R.string.menu_die_everything), tint = LocalTokens.current.accent) }
                    IconButton(onClick = { showSave = true }, enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(Sizes.icon), strokeWidth = Sizes.progress)
                        } else {
                            Icon(EditorIcons.Save, contentDescription = stringResource(R.string.editor_save_image), tint = LocalTokens.current.accent)
                        }
                    }
                    IconButton(onClick = { showFavorites = true }) { Icon(EditorIcons.Star, contentDescription = stringResource(R.string.editor_favourites)) }
                    IconButton(onClick = { showMenu = true }) { Icon(EditorIcons.More, contentDescription = stringResource(R.string.editor_more)) }
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
                            compare = compare, proMark = if (missingPro.isNotEmpty()) vm::showPro else null)
                    }
                    ControlTabs(params, selected, vm, Modifier.width(Spacing.controlsWidth).fillMaxHeight(), pro.unlocked, onShowPro = { vm.showPro() })
                }
            } else {
                // The canvas stays in view; only the open tab scrolls, so every change is visible.
                val canvasMax = maxHeight * 0.5f
                var header by remember { mutableStateOf<ControlHeader?>(null) }
                FoldingCanvas(
                    preview = preview,
                    header = header,
                    fullSide = minOf(maxWidth - Spacing.lg * 2, canvasMax),
                    size = previewSize,
                    onSize = vm::setPreviewSize,
                    compare = compare,
                    drawing = drawing(params, drawTool, vm),
                    move = moving(params, selected, moveTarget, vm),
                    proMark = if (missingPro.isNotEmpty()) vm::showPro else null,
                ) { modifier ->
                    ControlTabs(params, selected, vm, modifier, pro.unlocked, onShowPro = { vm.showPro() }, onHeader = { header = it })
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
    /** Centre as fractions of the canvas, and a ring radius as a fraction of its width (for the text, half its box width). */
    val x: Float,
    val y: Float,
    val radius: Float,
    val target: MoveTarget,
    /** The layer's number from 1, or null for the emblem and the text. */
    val layer: Int?,
    /** Whether it sits on the vertical or the horizontal centre line, where moving snaps it (UX-1 20). */
    val onCentreX: Boolean,
    val onCentreY: Boolean,
    val onMove: (dx: Double, dy: Double, zoom: Double, turnDegrees: Double) -> Unit,
)

private fun moving(params: MandalaParameters, selected: Int, target: MoveTarget?, vm: EditorViewModel): MoveInput? = when (target) {
    null -> null
    MoveTarget.LAYER -> params.layers.getOrNull(selected)?.let { l ->
        MoveInput((0.5 + l.offsetX).toFloat(), (0.5 + l.offsetY).toFloat(), (0.36 * l.scale).toFloat(), target, selected + 1,
            l.offsetX == 0.0, l.offsetY == 0.0, vm::moveBy)
    }
    MoveTarget.EMBLEM -> params.emblemLayer.let { e ->
        MoveInput((0.5 + e.offsetX).toFloat(), (0.5 + e.offsetY).toFloat(), (0.5 * e.size).toFloat(), target, null,
            e.offsetX == 0.0, e.offsetY == 0.0, vm::moveBy)
    }
    // The text stays centred across, so only its height has a line to snap to.
    MoveTarget.TEXT -> params.textLayer.let { tx ->
        MoveInput(0.5f, (1.0 - tx.offsetY).toFloat(), (tx.fontWidth / 2).toFloat(), target, null, false, tx.offsetY == 0.5, vm::moveBy)
    }
}

/** The preview without the scene effects, shown while a finger holds the canvas (UX-1 16). */
private class Compare(
    /** Null until drawn; the preview with effects stays until then. */
    val image: androidx.compose.ui.graphics.ImageBitmap?,
    /** Called when a finger goes down, so the image is usually ready when the hold counts. */
    val prepare: () -> Unit,
)

/**
 * Phones: a button in the image's bottom right corner steps it down from full to a middle size and
 * to a small square at the top left, with what the open tab is about beside it, so the controls get
 * the room (DM-4, variant B; UX-1 11). The button on the small image, a tap on a smaller image, or
 * pulling the controls down once they are at the top brings it back; a pull stops at the nearest
 * size. The size stays as chosen while the controls scroll and across starts. Folding only scales
 * the shown preview, so it never renders. Drawing and moving keep it large.
 */
@Composable
private fun FoldingCanvas(
    preview: PreviewState,
    header: ControlHeader?,
    fullSide: androidx.compose.ui.unit.Dp,
    size: PreviewSize,
    onSize: (PreviewSize) -> Unit,
    compare: Compare?,
    drawing: DrawingInput?,
    move: MoveInput?,
    proMark: (() -> Unit)?,
    controls: @Composable (Modifier) -> Unit,
) {
    val t = LocalTokens.current
    val pinned = drawing != null || move != null
    val shown = if (pinned) PreviewSize.FULL else size
    // The shown side as a fraction of the full one; follows a pull while the finger is down.
    val scale = remember { androidx.compose.animation.core.Animatable(shown.fraction) }
    LaunchedEffect(shown) { scale.animateTo(shown.fraction) }
    val fraction = scale.value
    val fullPx = with(androidx.compose.ui.platform.LocalDensity.current) { fullSide.toPx() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val latestSize by rememberUpdatedState(size)
    // Pulling down past the top of the controls grows the image with the finger; let go and it takes
    // the nearest size. Only a drag counts, not a fling that reaches the top.
    val pull = remember { floatArrayOf(0f) }
    val connection = remember(pinned, fullPx) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            fun follow() {
                val f = (latestSize.fraction + pull[0] / fullPx).coerceIn(latestSize.fraction, 1f)
                scope.launch { scale.snapTo(f) }
            }

            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (pull[0] <= 0f || available.y >= 0f) return Offset.Zero
                val used = maxOf(available.y, -pull[0])
                pull[0] += used
                follow()
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (latestSize == PreviewSize.FULL || pinned || fullPx <= 0f || available.y <= 0f ||
                    source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput
                ) return Offset.Zero
                pull[0] += available.y
                follow()
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (pull[0] <= 0f) return androidx.compose.ui.unit.Velocity.Zero
                pull[0] = 0f
                val f = scale.value
                val nearest = PreviewSize.entries.filter { it.fraction >= latestSize.fraction }.minBy { kotlin.math.abs(it.fraction - f) }
                if (nearest != latestSize) onSize(nearest) else scale.animateTo(latestSize.fraction)
                return available
            }
        }
    }
    // Past halfway from full to the middle size the status gives way to the tab's title beside the
    // image; past halfway from the middle to the small size the Pro mark is too small to tap.
    val towardsMiddle = (PreviewSize.FULL.fraction + PreviewSize.MIDDLE.fraction) / 2f
    val towardsSmall = (PreviewSize.MIDDLE.fraction + PreviewSize.SMALL.fraction) / 2f
    val headerAlpha = ((towardsMiddle - fraction) / (towardsMiddle - PreviewSize.MIDDLE.fraction)).coerceIn(0f, 1f)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        val side = fullSide * fraction
        // Centred when full (if narrower than the screen), at the left when smaller.
        val inset = (maxWidth - fullSide) / 2 * ((fraction - PreviewSize.SMALL.fraction) / (1f - PreviewSize.SMALL.fraction))
        Column(Modifier.fillMaxSize().nestedScroll(connection)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(start = inset)) {
                Canvas(
                    preview,
                    Modifier.width(side),
                    drawing,
                    move,
                    compare = compare,
                    showStatus = fraction > towardsMiddle,
                    // The small image is too small for the labels on it; the controls still carry their marks.
                    showLabels = fraction > towardsSmall,
                    onTap = if (size != PreviewSize.FULL && !pinned) ({ onSize(PreviewSize.FULL) }) else null,
                    proMark = proMark,
                ) {
                    // Bottom right, where the thumb reaches it.
                    if (!pinned) {
                        val small = size == PreviewSize.SMALL
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(Spacing.xs)
                                .clip(CircleShape)
                                .background(t.surface)
                                .clickable(onClickLabel = if (small) stringResource(R.string.editor_show_large) else stringResource(R.string.editor_make_small)) {
                                    onSize(if (small) PreviewSize.FULL else PreviewSize.entries[size.ordinal + 1])
                                }
                                .padding(Spacing.xs),
                        ) {
                            Icon(
                                if (small) EditorIcons.Enlarge else EditorIcons.Shrink,
                                contentDescription = if (small) stringResource(R.string.editor_larger_image) else stringResource(R.string.editor_smaller_image),
                                tint = t.text,
                                modifier = Modifier.size(Sizes.icon),
                            )
                        }
                    }
                }
                if (headerAlpha > 0f && header != null) {
                    Column(
                        Modifier.padding(start = Spacing.md).alpha(headerAlpha),
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
    compare: Compare? = null,
    showStatus: Boolean = true,
    showLabels: Boolean = true,
    onTap: (() -> Unit)? = null,
    /** Shown while the mandala uses Pro features the user does not own (ADR-0016); opens the Pro sheet. */
    proMark: (() -> Unit)? = null,
    corner: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    val t = LocalTokens.current
    val showLarge = stringResource(R.string.editor_show_large)
    val proMarkAction = stringResource(R.string.editor_pro_mark_action)
    val proMarkDescription = stringResource(R.string.editor_pro_mark_description)
    // Drawing and moving take the fingers; holding only counts outside them.
    val holdable = compare.takeIf { drawing == null && move == null }
    var holding by remember { mutableStateOf(false) }
    val comparing = holding && holdable != null
    val latestCompare by rememberUpdatedState(holdable)
    val latestTap by rememberUpdatedState(onTap)
    Column(modifier.padding(bottom = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Radius.md)).background(t.canvas),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // A tap shows a smaller image large; a long press shows it without the scene effects until
            // the finger lifts. The buttons and overlays on top keep their own touches.
            Box(
                Modifier.fillMaxSize()
                    .then(
                        if (onTap != null || holdable != null) Modifier.pointerInput(onTap != null, holdable != null) {
                            detectTapGestures(
                                onPress = {
                                    latestCompare?.prepare?.invoke()
                                    try { tryAwaitRelease() } finally { holding = false }
                                },
                                onLongPress = if (holdable != null) ({ holding = true }) else null,
                                onTap = if (onTap != null) ({ latestTap?.invoke() }) else null,
                            )
                        } else Modifier,
                    )
                    .then(if (onTap != null) Modifier.semantics { onClick(label = showLarge) { onTap(); true } } else Modifier),
            ) {
                (if (comparing) holdable?.image ?: preview.image else preview.image)?.let {
                    Image(it, contentDescription = stringResource(R.string.editor_preview), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
            if (drawing != null) DrawingOverlay(drawing, t.accent)
            if (move != null) MoveOverlay(move, t.accent, t.canvas)
            if (proMark != null && showLabels) {
                Text(
                    "Pro",
                    style = MaterialTheme.typography.labelLarge,
                    color = t.accent,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Spacing.sm)
                        .clip(CircleShape)
                        .background(t.surface)
                        .clickable(onClickLabel = proMarkAction, onClick = proMark)
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        .semantics { contentDescription = proMarkDescription },
                )
            }
            if (comparing && showLabels) {
                Text(
                    stringResource(R.string.editor_without_effects),
                    style = MaterialTheme.typography.labelLarge,
                    color = t.textMuted,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.sm)
                        .clip(CircleShape)
                        .background(t.surface)
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
            corner()
            if (preview.rendering || comparing && holdable?.image == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(Sizes.progress))
            }
        }
        val status = when {
            preview.error != null -> preview.error
            drawing != null -> stringResource(R.string.editor_draw_hint)
            move != null -> when (move.target) {
                MoveTarget.LAYER -> stringResource(R.string.editor_move_layer_hint, move.layer ?: 1)
                MoveTarget.EMBLEM -> stringResource(R.string.editor_move_emblem_hint)
                MoveTarget.TEXT -> stringResource(R.string.editor_move_text_hint)
            }
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
 * Moves the selected layer, the emblem or the text with the fingers: drag moves, pinch sizes, two
 * fingers turn (not the text). A ring, or a line for the text, shows where it is at once; the
 * preview follows when the fingers rest. On a centre line a guide crosses the image and the phone
 * ticks once (UX-1 20).
 */
@Composable
private fun MoveOverlay(input: MoveInput, guide: Color, halo: Color) {
    val current by rememberUpdatedState(input)
    val haptics = LocalHapticFeedback.current
    var wasOnCentre by remember { mutableStateOf(input.onCentreX to input.onCentreY) }
    LaunchedEffect(input.onCentreX, input.onCentreY) {
        if (input.onCentreX && !wasOnCentre.first || input.onCentreY && !wasOnCentre.second) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        wasOnCentre = input.onCentreX to input.onCentreY
    }
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    current.onMove((pan.x / size.width).toDouble(), (pan.y / size.height).toDouble(), zoom.toDouble(), rotation.toDouble())
                }
            },
    ) {
        // A dark edge under the accent keeps the marks visible over bright mandalas.
        fun line(from: Offset, to: Offset, width: Float) {
            drawLine(halo, from, to, strokeWidth = width + 4f, cap = StrokeCap.Round)
            drawLine(guide, from, to, strokeWidth = width, cap = StrokeCap.Round)
        }
        if (current.onCentreX) line(Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2f)
        if (current.onCentreY) line(Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f)
        val c = Offset(current.x * size.width, current.y * size.height)
        if (current.target == MoveTarget.TEXT) {
            val half = current.radius * size.width
            line(Offset(c.x - half, c.y), Offset(c.x + half, c.y), 3f)
        } else {
            drawCircle(halo, current.radius * size.width, c, style = Stroke(width = 7f))
            drawCircle(guide, current.radius * size.width, c, style = Stroke(width = 3f))
        }
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
private fun ProSheet(
    pro: UnlockState,
    request: ProRequest,
    onBuy: () -> Unit,
    onRetry: () -> Unit,
    onRestore: () -> Unit,
    onWithout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalTokens.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.editor_pro_title), style = MaterialTheme.typography.titleLarge)
            if (request.features.isNotEmpty()) {
                Text(
                    if (request.action != null) stringResource(R.string.editor_pro_uses_keep)
                    else stringResource(R.string.editor_pro_uses),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(Modifier.padding(start = Spacing.md)) {
                    for (feature in request.features) Text(stringResource(R.string.editor_pro_feature_item, stringResource(feature.labelRes)), style = MaterialTheme.typography.bodyMedium)
                }
            }
            request.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                stringResource(R.string.editor_pro_summary),
                style = MaterialTheme.typography.bodySmall,
                color = t.textMuted,
            )
            Button(onClick = onBuy, enabled = pro.canBuy, modifier = Modifier.fillMaxWidth()) {
                Text(pro.price?.let { stringResource(R.string.editor_pro_buy_for, it) } ?: stringResource(R.string.editor_pro_buy))
            }
            if (request.features.isNotEmpty()) {
                OutlinedButton(colors = outlinedColors(), onClick = onWithout, modifier = Modifier.fillMaxWidth()) {
                    Text(request.action?.let { stringResource(R.string.editor_pro_without, it) } ?: stringResource(R.string.editor_pro_remove))
                }
            }
            // Feedback from Google Play: a pending payment, why there is no price, the last purchase or restore.
            if (pro.pending) {
                Text(stringResource(R.string.pro_pending), style = MaterialTheme.typography.bodySmall, color = t.textMuted)
            }
            pro.priceProblem?.takeIf { !pro.loadingPrice }?.let { problem ->
                Text(stringResource(problem.textRes), style = MaterialTheme.typography.bodySmall, color = t.textMuted)
            }
            pro.message?.takeIf { !(it == UnlockMessage.PENDING && pro.pending) }?.let { message ->
                Text(stringResource(message.textRes), style = MaterialTheme.typography.bodyMedium)
            }
            // One per line, so long translations fit at 360 dp.
            if (pro.priceProblem != null && !pro.loadingPrice) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.editor_pro_retry)) }
            }
            TextButton(onClick = onRestore, enabled = !pro.busy) { Text(stringResource(R.string.editor_pro_restore)) }
        }
    }
}

/**
 * Favourites as a grid of thumbnails: tap applies. A long press starts selecting; then taps add or remove
 * favourites, and the heading offers deleting them all at once (with Undo in the message).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesSheet(
    favorites: List<Favorite>,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onApply: (Favorite) -> Unit,
    onDelete: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val t = LocalTokens.current
    var selected by rememberSaveable { mutableStateOf(emptySet<String>()) }
    // A favourite deleted elsewhere (or by Undo's opposite) drops out of the selection.
    val selection = selected.filterTo(mutableSetOf()) { id -> favorites.any { it.id == id } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
      // Messages show inside the sheet while it is open; behind it they would be hidden. They sit near
      // the top, because the sheet's lower part can be below the screen while it is half open.
      Box {
        Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selection.isEmpty()) {
                    Text(stringResource(R.string.editor_favourites), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Button(onClick = onAdd, enabled = canAdd) { Text(stringResource(R.string.editor_favourites_add)) }
                } else {
                    Text(stringResource(R.string.editor_favourites_selected, selection.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selected = emptySet() }) { Text(stringResource(R.string.editor_favourites_cancel)) }
                    Button(onClick = { onDelete(selection); selected = emptySet() }) { Text(stringResource(R.string.editor_favourite_delete)) }
                }
            }
            // Below the title, so a message does not cover "Add current"; it takes no room while empty.
            MessageHost(snackbar)
            if (favorites.isEmpty()) {
                Text(
                    stringResource(R.string.editor_favourites_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.textMuted,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            } else {
                Text(stringResource(R.string.editor_favourites_hint), style = MaterialTheme.typography.bodySmall, color = t.textMuted,
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.sm))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(Sizes.favourite),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(favorites, key = { it.id }) { fav ->
                        FavoriteTile(
                            fav,
                            selected = fav.id in selection,
                            selecting = selection.isNotEmpty(),
                            onApply = onApply,
                            onToggle = { selected = if (fav.id in selection) selection - fav.id else selection + fav.id },
                        )
                    }
                }
            }
        }
      }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FavoriteTile(fav: Favorite, selected: Boolean, selecting: Boolean, onApply: (Favorite) -> Unit, onToggle: () -> Unit) {
    val t = LocalTokens.current
    val thumb = remember(fav.id) { Thumbnails.decode(fav.thumbnailData) }
    val styles = fav.parameters.layers.map { stringResource(it.style.labelRes) }.joinToString(", ")
    val description = stringResource(R.string.editor_favourite_description, styles)
    val shape = RoundedCornerShape(Radius.sm)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(t.canvas)
            .then(if (selected) Modifier.border(Sizes.swatchRing, t.accent, shape) else Modifier)
            .combinedClickable(onClick = { if (selecting) onToggle() else onApply(fav) }, onLongClick = onToggle)
            .semantics {
                contentDescription = description
                if (selecting) this.selected = selected
            },
    ) {
        thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (selected) {
            Icon(EditorIcons.Check, contentDescription = null, tint = t.onAccent,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs).clip(CircleShape).background(t.accent).padding(Spacing.xs))
        }
    }
}

