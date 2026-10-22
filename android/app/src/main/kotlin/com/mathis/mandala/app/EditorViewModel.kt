package com.mathis.mandala.app

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mathis.mandala.app.Editing.addLayer
import com.mathis.mandala.app.Randomize.randomAll
import com.mathis.mandala.app.Randomize.randomLayer
import com.mathis.mandala.app.Randomize.randomVariant
import com.mathis.mandala.app.Randomize.randomLayers
import com.mathis.mandala.app.Editing.addStroke
import com.mathis.mandala.app.Editing.clearStrokes
import com.mathis.mandala.app.Editing.removeLastStroke
import com.mathis.mandala.app.Editing.strokeCount
import com.mathis.mandala.app.Editing.updateDrawing
import com.mathis.mandala.app.Editing.updateGraffiti
import com.mathis.mandala.app.Editing.duplicateLayer
import com.mathis.mandala.app.Editing.moveLayer
import com.mathis.mandala.app.Editing.movedBy
import com.mathis.mandala.app.Editing.newVariation
import com.mathis.mandala.app.Editing.removeLayer
import com.mathis.mandala.app.Editing.newPattern
import com.mathis.mandala.app.Editing.updateBackground
import com.mathis.mandala.app.Editing.updateEffects
import com.mathis.mandala.app.Editing.updateLayer
import com.mathis.mandala.app.Editing.updateText
import com.mathis.mandala.app.Editing.updateEmblem
import com.mathis.mandala.app.Editing.reset
import com.mathis.mandala.app.Editing.newShape
import com.mathis.mandala.app.Randomize.randomized
import com.mathis.mandala.app.Editing.withQuote
import com.mathis.mandala.core.BaseLayerSettings
import com.mathis.mandala.core.BaseLayerType
import com.mathis.mandala.core.DrawingLayerSettings
import com.mathis.mandala.core.EffectsLayerSettings
import com.mathis.mandala.core.EmblemLayerSettings
import com.mathis.mandala.core.GraffitiLayerSettings
import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.Framing
import com.mathis.mandala.core.Image8
import com.mathis.mandala.core.LayerCache
import com.mathis.mandala.core.MacFile
import com.mathis.mandala.core.Quote
import com.mathis.mandala.core.Quotes
import com.mathis.mandala.core.TextLayerSettings
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.RenderCancelledException
import com.mathis.mandala.core.RenderOptions
import com.mathis.mandala.core.SettingsJson
import com.mathis.mandala.core.StyleLayer
import com.mathis.mandala.core.VectorExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.random.Random

data class PreviewState(
    val image: ImageBitmap? = null,
    val rendering: Boolean = true,
    val millis: Long? = null,
    val error: String? = null,
    /** Parts of the settings the Android renderer cannot draw yet. */
    val warnings: List<String> = emptyList(),
)

/** The preview in the save sheet and the framing it was drawn with (to move it while a finger moves). */
data class FramePreviewState(
    val image: ImageBitmap? = null,
    val rendering: Boolean = true,
    val framing: Framing = Framing(),
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * A running export: what it does ("Creating the wallpaper") and the share of the image drawn, from
 * 0 to 1; null while the share is not known yet or Android is at work (saving, setting the wallpaper).
 */
data class ExportProgress(val label: String, val share: Float? = null)

/** How much a new mandala from the menu changes (DM-7): more dots on its die, more change. */
enum class NewKind(val label: String) { VARIATION("Variation"), LAYERS("New layers"), EVERYTHING("Everything") }

/** A new mandala shown next to the current one in the menu; nothing is replaced until it is used. */
data class Candidate(val kind: NewKind, val params: MandalaParameters, val image: ImageBitmap? = null, val rendering: Boolean = true)

/** One-off things for the screen to do. */
data class HistoryState(val canGoBack: Boolean, val canGoForward: Boolean)

sealed interface EditorEvent {
    /** `undo` restores what the message reports; changes to the mandala are undone with Back instead. */
    data class Message(val text: String, val undo: (() -> Unit)? = null) : EditorEvent
    data class ShareImage(val uri: Uri) : EditorEvent
}

/**
 * Holds the settings, the selected layer and the favourites; renders the preview after each change,
 * keeps the last state across restarts, reads and writes the macOS app's files, saves and shares images.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {
    // A few kilobytes each; reading them here keeps the first preview from showing anything else.
    private val store = SettingsStore(File(application.filesDir, "last-settings.json"))
    private val favoritesStore = FavoritesStore(File(application.filesDir, "favorites.json"))

    private val _params = MutableStateFlow(store.load() ?: Editing.initial(randomSeed()))
    val params: StateFlow<MandalaParameters> = _params.asStateFlow()

    private val _selected = MutableStateFlow(0)
    val selected: StateFlow<Int> = _selected.asStateFlow()

    private val _preview = MutableStateFlow(PreviewState())
    val preview: StateFlow<PreviewState> = _preview.asStateFlow()

    private val _favorites = MutableStateFlow(
        favoritesStore.loadOrSeed {
            // Examples from the macOS app's favourites, so new users see what the app can do.
            application.assets.open(STARTER_FAVORITES).use { SettingsJson.decodeFavorites(it.reader().readText()) }
        },
    )
    val favorites: StateFlow<List<Favorite>> = _favorites.asStateFlow()

    /** True while an export or share render runs. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** What the running export does and how far it is; null when none runs. */
    private val _export = MutableStateFlow<ExportProgress?>(null)
    val export: StateFlow<ExportProgress?> = _export.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    private val historyStore = HistoryStore(File(application.filesDir, "history.json"))
    private val history = History<MandalaParameters>().apply { restore(historyStore.load()) }

    private val _historyState = MutableStateFlow(HistoryState(history.canGoBack, history.canGoForward))
    val historyState: StateFlow<HistoryState> = _historyState.asStateFlow()

    private val threads = Runtime.getRuntime().availableProcessors()
    private val pro = ProPurchases(application) { message(it) }.also { it.connect() }

    /** Whether Pro is owned, and its price. */
    val proState: StateFlow<ProState> = pro.state

    fun buyPro(activity: android.app.Activity) = pro.buy(activity)

    // ── Save sheet (ADR-0010) ──

    private val saveStore = SaveChoiceStore(application)
    private val _saveChoice = MutableStateFlow(saveStore.load())
    val saveChoice: StateFlow<SaveChoice> = _saveChoice.asStateFlow()

    private val _screenAspect = MutableStateFlow(DEFAULT_SCREEN_ASPECT)
    val screenAspect: StateFlow<Double> = _screenAspect.asStateFlow()

    private val _saveOpen = MutableStateFlow(false)
    private val _framePreview = MutableStateFlow(FramePreviewState())
    val framePreview: StateFlow<FramePreviewState> = _framePreview.asStateFlow()

    /** The screen's shorter side in pixels; a wallpaper may reuse a saved image at least this wide. */
    private var screenShortSide = 0

    fun setScreen(aspect: Double, shortSide: Int) {
        _screenAspect.value = aspect
        screenShortSide = shortSide
    }

    fun setSaveOpen(open: Boolean) {
        _saveOpen.value = open
        if (!open) lastExport = null
    }

    /**
     * The image the save sheet rendered last and what it was rendered from. While the sheet stays
     * open, saving again or setting the wallpaper reuses it instead of rendering again.
     */
    private data class ExportKey(
        val params: MandalaParameters, val framing: Framing, val textFollows: Boolean,
        val width: Int, val height: Int, val supersample: Boolean,
    )
    private var lastExport: Pair<ExportKey, Image8>? = null

    fun setSaveFormat(format: Editing.SaveFormat) = _saveChoice.update { it.copy(format = format) }

    /** "Fill" or "Show whole", free in every format; with Pro it ends placing by hand. */
    fun setFill(fill: Boolean) = _saveChoice.update { it.copy(fit = if (fill) Fit.FILL else Fit.WHOLE) }

    private fun aspectOf(format: Editing.SaveFormat) = Editing.aspect(format, _screenAspect.value)

    /** Moving and zooming the mandala is part of Pro; without it the mandala stays on its preset. */
    fun setFraming(framing: Framing) {
        if (pro.state.value.owned) _saveChoice.update { it.copy(framing = framing.clamped(), fit = Fit.HAND) }
    }

    fun setTextFollowsMandala(follows: Boolean) = _saveChoice.update { it.copy(textFollowsMandala = follows) }

    /** Fill zooms measured recently, by mandala and shape; touched only on the main thread. */
    private val fillZooms = LinkedHashMap<Pair<MandalaParameters, Double>, Double>()

    /** The framing the save sheet shows and images are drawn with, for the current mandala. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val shownFraming: StateFlow<Framing> = combine(_saveChoice, _params, _screenAspect, pro.state) { c, p, a, pr -> FramingInput(c, p, Editing.aspect(c.format, a), pr.owned) }
        .mapLatest { framingFor(it.choice, it.params, it.aspect, it.pro) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Framing())

    private data class FramingInput(val choice: SaveChoice, val params: MandalaParameters, val aspect: Double, val pro: Boolean)

    /** The framing for `choice`; "Fill" measures the mandala once (about a tenth of a second) and keeps the zoom. */
    private suspend fun framingFor(choice: SaveChoice, params: MandalaParameters, aspect: Double, pro: Boolean): Framing {
        val zoom = if (choice.fit == Fit.FILL) fillZoom(params, aspect) else 1.0
        return Editing.framingFor(choice, zoom, pro)
    }

    private suspend fun fillZoom(params: MandalaParameters, aspect: Double): Double {
        val key = params to aspect
        fillZooms[key]?.let { return it }
        val zoom = withContext(Dispatchers.Default) { MandalaRenderer.fillZoom(params, aspect, threads) }
        fillZooms[key] = zoom
        if (fillZooms.size > FILL_ZOOMS_KEPT) fillZooms.remove(fillZooms.keys.first())
        return zoom
    }


    override fun onCleared() {
        pro.close()
        super.onCleared()
    }

    /** Previews rendered recently, by their settings; about 1 MB each. */
    private val recentPreviews = RecentImages<MandalaParameters, PreviewState>(RECENT_PREVIEWS)

    private val appearance = Appearance(application)
    private val _fastPreview = MutableStateFlow(appearance.fastPreview)

    /** Whether the preview blurs its glow at a smaller size (quicker, a touch softer; saved images never). */
    val fastPreview: StateFlow<Boolean> = _fastPreview.asStateFlow()

    fun setFastPreview(on: Boolean) {
        appearance.fastPreview = on
        // The kept previews were drawn with the other glow.
        recentPreviews.clear()
        _fastPreview.value = on
    }

    /** What the last rendered preview was drawn from, and how. */
    private var renderedParams: MandalaParameters? = null
    private var renderedFast = false
    /** How long the last preview took per layer it drew, to guess the next one's time. */
    private var millisPerLayer = 0L

    /** The layer images of the last rendered preview, so a change to one layer redraws only that one. */
    private val previewCache = LayerCache()

    /** Save sheet previews rendered recently, by everything they were rendered from. */
    private val recentFrames = RecentImages<FrameRequest, FramePreviewState>(RECENT_FRAMES)

    private val textRenderer = AndroidTextRenderer()
    private val imageLoader = AndroidImageLoader()

    init {
        viewModelScope.launch {
            // A state shown recently (history, undo, a switch flipped back) comes back at once;
            // anything else is rendered once the settings rest for a moment.
            combine(_params, _fastPreview) { p, f -> p to f }.collectLatest { (p, fast) ->
                val known = recentPreviews[p]
                if (known != null) {
                    _preview.value = known
                    return@collectLatest
                }
                delay(DEBOUNCE_MS)
                val redrawn = if (renderedFast == fast) redrawnLayers(p) else p.layers.count { it.isEnabled }
                if (redrawn >= Editing.QUICK_MIN_LAYERS && millisPerLayer * redrawn > Editing.QUICK_AFTER_MS) renderQuickPreview(p)
                renderPreview(p, fast, redrawn)
            }
        }
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _params.debounce(SAVE_DELAY_MS).collectLatest { p -> withContext(Dispatchers.IO) { runCatching { store.save(p) } } }
        }
        viewModelScope.launch {
            // Like the macOS app, every state that settles goes into the history.
            @OptIn(FlowPreview::class)
            _params.debounce(HISTORY_DELAY_MS).collect {
                record(it)
                val snapshot = history.entries
                withContext(Dispatchers.IO) { runCatching { historyStore.save(snapshot) } }
            }
        }
        viewModelScope.launch {
            _favorites.drop(1).collectLatest { f -> withContext(Dispatchers.IO) { runCatching { favoritesStore.save(f) } } }
        }
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _saveChoice.drop(1).debounce(SAVE_DELAY_MS).collectLatest { c -> withContext(Dispatchers.IO) { runCatching { saveStore.save(c) } } }
        }
        viewModelScope.launch {
            // The save sheet's preview, only while the sheet is open.
            @OptIn(FlowPreview::class)
            combine(_params, _saveChoice, _screenAspect, _saveOpen, pro.state) { p, c, a, open, pr -> if (open) FrameRequest(p, c, a, pr.owned) else null }
                .debounce(FRAME_DEBOUNCE_MS)
                .collectLatest { request -> if (request != null) renderFramePreview(request) }
        }
    }

    private data class FrameRequest(val params: MandalaParameters, val choice: SaveChoice, val aspect: Double, val pro: Boolean)

    // ── Layers ──

    fun select(index: Int) {
        _selected.value = index.coerceIn(0, _params.value.layers.lastIndex)
    }

    fun updateSelected(change: (StyleLayer) -> StyleLayer) = _params.update { it.updateLayer(_selected.value, change) }

    fun updateLayer(index: Int, change: (StyleLayer) -> StyleLayer) = _params.update { it.updateLayer(index, change) }

    fun addLayer() = applyLayerOperation { it.addLayer(randomSeed()) }

    fun duplicateSelected() = applyLayerOperation { it.duplicateLayer(_selected.value, randomSeed()) }

    fun removeSelected() = applyLayerOperation { it.removeLayer(_selected.value) }

    fun moveLayer(index: Int, delta: Int) = applyLayerOperation { it.moveLayer(index, delta) }

    fun newVariation() = _params.update { it.newVariation(::randomSeed) }

    fun updateBackground(change: (BaseLayerSettings) -> BaseLayerSettings) = _params.update { it.updateBackground(change) }

    fun updateEffects(change: (EffectsLayerSettings) -> EffectsLayerSettings) = _params.update { it.updateEffects(change) }

    fun newEffectsPattern() = updateEffects { it.newPattern(::randomSeed) }

    fun updateText(change: (TextLayerSettings) -> TextLayerSettings) = _params.update { it.updateText(change) }

    fun updateEmblem(change: (EmblemLayerSettings) -> EmblemLayerSettings) = _params.update { it.updateEmblem(change) }

    fun randomizeEmblem() = updateEmblem { it.randomized(Random) }

    fun resetEmblem() = updateEmblem { it.reset() }

    fun newEmblemShape() = updateEmblem { it.newShape(Random) }

    fun useQuote(quote: Quote) = updateText { it.withQuote(quote) }

    fun randomQuote() = useQuote((Quotes.all + _myQuotes.value).random())

    private val myQuotesStore = MyQuotesStore(File(application.filesDir, "my-quotes.json"))
    private val _myQuotes = MutableStateFlow(myQuotesStore.load())

    /** Quotes the user saved, newest first. */
    val myQuotes: StateFlow<List<Quote>> = _myQuotes.asStateFlow()

    /** Keeps the text currently on the image (with its author) among the user's quotes. */
    fun saveTextAsMyQuote() {
        val text = _params.value.textLayer
        changeMyQuotes { MyQuotes.add(it, Quote(text.text, text.customAuthor)) }
        message("Saved to your quotes")
    }

    fun removeMyQuote(quote: Quote) = changeMyQuotes { MyQuotes.remove(it, quote) }

    private fun changeMyQuotes(change: (List<Quote>) -> List<Quote>) {
        val next = change(_myQuotes.value)
        _myQuotes.value = next
        viewModelScope.launch(Dispatchers.IO) { runCatching { myQuotesStore.save(next) } }
    }

    /** Uses a picked photo as the background (copied into the app's files). */
    fun useBackgroundImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val url = withContext(Dispatchers.IO) { BackgroundImages.import(getApplication(), uri) }
                updateBackground { it.copy(isEnabled = true, type = BaseLayerType.IMAGE, imageURL = url) }
            } catch (e: Exception) {
                message("Could not open the picture: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    // ── History and dice ──

    private fun record(params: MandalaParameters) {
        history.record(params)
        _historyState.value = HistoryState(history.canGoBack, history.canGoForward)
    }

    fun goBack() = navigate { history.back() }

    fun goForward() = navigate { history.forward() }

    private fun navigate(step: () -> MandalaParameters?) {
        record(_params.value) // a change younger than the history delay is kept too
        val target = step() ?: return
        _params.value = target
        _selected.value = _selected.value.coerceIn(0, target.layers.lastIndex)
        _historyState.value = HistoryState(history.canGoBack, history.canGoForward)
    }

    /** New layers and background, like "Randomize All" on the Mac. */
    fun surprise() {
        _params.update { it.randomAll(Random) }
        _selected.value = 0
    }

    private val _candidate = MutableStateFlow<Candidate?>(null)

    /** The new mandala the menu shows next to the current one, if any. */
    val candidate: StateFlow<Candidate?> = _candidate.asStateFlow()
    private var candidateJob: Job? = null

    /** Makes a new mandala of this kind from the current one and draws it small; the current one stays. */
    fun tryNew(kind: NewKind) {
        val current = _params.value
        val next = when (kind) {
            NewKind.VARIATION -> current.newVariation(::randomSeed)
            NewKind.LAYERS -> current.randomLayers(Random)
            NewKind.EVERYTHING -> current.randomAll(Random)
        }
        // The last picture stays while the next one is drawn.
        _candidate.value = Candidate(kind, next, image = _candidate.value?.image)
        candidateJob?.cancel()
        val fast = _fastPreview.value
        candidateJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    val job = coroutineContext.job
                    MandalaRenderer.render(
                        next,
                        RenderOptions(
                            outputSize = Editing.CANDIDATE_SIZE, threads = threads, textRenderer = textRenderer, imageLoader = imageLoader,
                            isCancelled = { !job.isActive },
                            glowReferenceWidth = if (fast) Editing.CANDIDATE_SIZE * 2 else RenderOptions().glowReferenceWidth,
                        ),
                    )
                }
                val image = result.image.toBitmap().asImageBitmap()
                _candidate.update { if (it?.params == next) it.copy(image = image, rendering = false) else it }
            } catch (e: RenderCancelledException) {
                // A newer one is already being drawn.
            } catch (e: OutOfMemoryError) {
                _candidate.update { it?.copy(rendering = false) }
            }
        }
    }

    /** Replaces the mandala with the new one; its small picture stands in until the preview is drawn. */
    fun useCandidate() {
        val c = _candidate.value ?: return
        closeCandidate()
        c.image?.let { image -> if (recentPreviews[c.params] == null) _preview.update { it.copy(image = image, rendering = true) } }
        _params.value = c.params
        _selected.value = 0
    }

    fun closeCandidate() {
        candidateJob?.cancel()
        _candidate.value = null
    }

    /** New layers, like "Randomize Layers" on the Mac. */
    fun randomLayers() {
        _params.update { it.randomLayers(Random) }
        _selected.value = 0
    }

    fun randomizeSelected() = _params.update { it.randomLayer(_selected.value, Random) }

    /** A new variant of the selected layer in the same style. */
    fun varySelected() = _params.update { it.randomVariant(_selected.value, Random) }

    // ── Positions of layers and the emblem (ADR-0011) ──

    private val _moveTarget = MutableStateFlow<MoveTarget?>(null)

    /** What drags on the canvas move: the selected layer or the emblem; null while they do not. Pro only. */
    val moveTarget: StateFlow<MoveTarget?> = _moveTarget.asStateFlow()

    fun setMoveTarget(target: MoveTarget?) {
        if (target != null && !pro.state.value.owned) return
        _moveTarget.value = target
        if (target != null) _drawTool.value = null
    }

    /** Moves, sizes and turns the move target by a gesture on the canvas (fractions of its width). */
    fun moveBy(dx: Double, dy: Double, zoom: Double, turnDegrees: Double) {
        if (!pro.state.value.owned) return
        when (_moveTarget.value) {
            MoveTarget.LAYER -> updateSelected { it.movedBy(dx, dy, zoom, turnDegrees) }
            MoveTarget.EMBLEM -> updateEmblem { it.movedBy(dx, dy, zoom, turnDegrees) }
            null -> Unit
        }
    }

    // ── Drawing by hand ──

    private val _drawTool = MutableStateFlow<DrawTool?>(null)

    /** The tool that turns drags on the canvas into strokes; null while the canvas is not for drawing. */
    val drawTool: StateFlow<DrawTool?> = _drawTool.asStateFlow()

    fun setDrawTool(tool: DrawTool?) {
        _drawTool.value = tool
        if (tool != null) _moveTarget.value = null
    }

    fun updateDrawing(change: (DrawingLayerSettings) -> DrawingLayerSettings) = _params.update { it.updateDrawing(change) }

    fun updateGraffiti(change: (GraffitiLayerSettings) -> GraffitiLayerSettings) = _params.update { it.updateGraffiti(change) }

    fun addStroke(xs: List<Double>, ys: List<Double>) {
        val tool = _drawTool.value ?: return
        _params.update { it.addStroke(tool, xs, ys) }
    }

    fun removeLastStroke(tool: DrawTool) = _params.update { it.removeLastStroke(tool) }

    fun clearStrokes(tool: DrawTool) {
        if (_params.value.strokeCount(tool) == 0) return
        _params.update { it.clearStrokes(tool) }
    }

    /** Applies a layer operation and selects the layer it returns. */
    private fun applyLayerOperation(operation: (MandalaParameters) -> Pair<MandalaParameters, Int>) {
        val (next, index) = operation(_params.value)
        _params.value = next
        _selected.value = index
    }

    /** Replaces all settings (favourite or file); Back in the history brings the previous ones back. */
    private fun replace(params: MandalaParameters) {
        _params.value = params
        _selected.value = 0
    }

    // ── Favourites ──

    fun addFavorite() {
        val image = _preview.value.image ?: return
        val favorite = Favorite(
            id = UUID.randomUUID().toString().uppercase(),
            parameters = _params.value,
            thumbnailData = Thumbnails.encode(image),
            createdAt = Favorite.createdNow(System.currentTimeMillis()),
        )
        _favorites.update { Favorites.add(it, favorite) }
        message("Added to favourites")
    }

    fun applyFavorite(favorite: Favorite) {
        // Its thumbnail stands in until the preview is rendered.
        if (recentPreviews[favorite.parameters] == null) {
            Thumbnails.decode(favorite.thumbnailData)?.let { thumb -> _preview.update { it.copy(image = thumb, rendering = true) } }
        }
        replace(favorite.parameters)
    }

    fun removeFavorite(id: String) {
        val before = _favorites.value
        val index = before.indexOfFirst { it.id == id }
        if (index < 0) return
        val removed = before[index]
        _favorites.update { Favorites.remove(it, id) }
        message("Favourite deleted", undo = {
            _favorites.update { list -> if (list.any { it.id == removed.id }) list else list.toMutableList().apply { add(index.coerceAtMost(size), removed) } }
        })
    }

    // ── Files of the macOS app ──

    /** Opens a settings file or a favourites file saved by the macOS app (or by this app). */
    fun openFile(uri: Uri) {
        viewModelScope.launch {
            val text = try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                } ?: error("empty")
            } catch (e: Exception) {
                message("Could not read the file.")
                return@launch
            }
            when (val file = runCatching { SettingsJson.decodeMacFile(text) }.getOrNull()) {
                is MacFile.Settings -> {
                    if (file.params.layers.isEmpty()) {
                        message("The file has no layers.")
                    } else {
                        replace(file.params)
                    }
                }
                is MacFile.Favorites -> {
                    var added = 0
                    _favorites.update { current -> Favorites.merge(current, file.favorites).also { added = it.second }.first }
                    val already = file.favorites.size - added
                    message("Imported $added favourites" + if (already > 0) " ($already were already there)" else "")
                }
                null -> message("This is not a Mandala settings or favourites file.")
            }
        }
    }

    fun saveSettingsTo(uri: Uri) {
        val text = SettingsJson.encode(_params.value)
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) }
                        ?: error("no stream")
                }
            }.isSuccess
            message(if (ok) "Settings saved" else "Could not save the settings.")
        }
    }

    fun suggestedSettingsName(): String = Editing.fileName(_params.value).removeSuffix(".png") + ".json"

    // ── Images ──

    /**
     * Saves in the chosen format and framing: 1024 px wide, or with Pro (`large`) 2048 px wide, drawn
     * without supersampling so it needs no more memory than the normal export.
     */
    fun saveImage(large: Boolean) {
        if (large && !pro.state.value.owned) return
        val choice = _saveChoice.value
        val (w, h) = Editing.exportSize(choice.format, _screenAspect.value, large)
        val params = _params.value
        val framing: suspend () -> Framing = { framingFor(choice, params, aspectOf(choice.format), pro.state.value.owned) }
        renderFullSize("Creating the image", w, h, large, framing, choice.textFollowsMandala) { image, fileName ->
            exportStep("Saving to Pictures")
            withContext(Dispatchers.IO) { GalleryExport.savePng(getApplication(), image, fileName) }
            message("Saved $w × $h to Pictures/${GalleryExport.FOLDER}")
        }
    }

    /**
     * Sets the image as wallpaper in the phone screen format, at the largest size the user may save;
     * an image of this screen format just saved is reused when it is at least as wide as the screen.
     * From the save sheet it takes the framing in the frame; from the menu (`filled`) the mandala
     * fills the screen, which suits a wallpaper best.
     */
    fun setWallpaper(target: WallpaperTarget, filled: Boolean = false) {
        val choice = _saveChoice.value
        val large = pro.state.value.owned
        val (w, h) = Editing.exportSize(Editing.SaveFormat.SCREEN, _screenAspect.value, large)
        val screen = aspectOf(Editing.SaveFormat.SCREEN)
        val params = _params.value
        val framing: suspend () -> Framing = { framingFor(if (filled) choice.copy(fit = Fit.FILL) else choice, params, screen, large) }
        val reuse = { k: ExportKey, f: Framing ->
            k.params == params && k.framing == f && k.textFollows == choice.textFollowsMandala &&
                k.width >= screenShortSide && kotlin.math.abs(k.height.toDouble() / k.width - h.toDouble() / w) < 0.002
        }
        renderFullSize("Creating the wallpaper", w, h, large, framing, choice.textFollowsMandala, reuse) { image, _ ->
            exportStep("Setting the wallpaper")
            withContext(Dispatchers.IO) { Wallpapers.set(getApplication(), image, target) }
            _export.value = null
            // Android recreates the screen when the wallpaper changes; a message sent right away is lost with it.
            delay(WALLPAPER_MESSAGE_DELAY_MS)
            message("Wallpaper set")
        }
    }

    fun shareImage() = renderFullSize("Creating the image", Editing.EXPORT_SIZE, Editing.EXPORT_SIZE, large = false, { Framing() }, textFollows = false) { image, fileName ->
        exportStep("Preparing to share")
        val uri = withContext(Dispatchers.IO) { ImageShare.prepare(getApplication(), image, fileName) }
        _events.send(EditorEvent.ShareImage(uri))
    }

    /**
     * Writes the mandala as an SVG line drawing (ADR-0013, Pro) to a file the user picked: the
     * layers' lines and the emblem, square and centred, without the pixel steps.
     */
    fun saveLineDrawing(look: VectorExport.Look, uri: Uri) {
        if (!pro.state.value.owned || _busy.value) return
        val snapshot = _params.value
        _busy.value = true
        _export.value = ExportProgress("Creating the line drawing")
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt") ?: error("no stream")
                    stream.bufferedWriter().use { VectorExport.svg(snapshot, Editing.EXPORT_SIZE, look, it) }
                }
                message("Line drawing saved")
            } catch (e: OutOfMemoryError) {
                message("Not enough memory for this line drawing.")
            } catch (e: Exception) {
                message("Could not save the line drawing: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _export.value = null
                _busy.value = false
            }
        }
    }

    fun suggestedLineDrawingName(look: VectorExport.Look): String =
        Editing.fileName(_params.value).removeSuffix(".png") + (if (look == VectorExport.Look.LINES) "-lines" else "") + ".svg"

    /** Shows `label` while Android works on the drawn image, for which there is no share to show. */
    private fun exportStep(label: String) {
        _export.value = ExportProgress(label)
    }

    /** Renders the image at full size and hands it to `then`; `label` says what for while it is drawn. */
    private fun renderFullSize(
        label: String, width: Int, height: Int, large: Boolean, framingOf: suspend () -> Framing, textFollows: Boolean,
        reuse: ((ExportKey, Framing) -> Boolean)? = null,
        then: suspend (Image8, String) -> Unit,
    ) {
        if (_busy.value) return
        val snapshot = _params.value
        _busy.value = true
        _export.value = ExportProgress(label)
        viewModelScope.launch {
            try {
                val framing = framingOf()
                val key = ExportKey(snapshot, framing, textFollows, width, height, !large)
                val cached = lastExport?.takeIf { (k, _) -> k == key || reuse?.invoke(k, framing) == true }?.second
                if (cached != null) {
                    then(cached, Editing.fileName(snapshot, cached.width, cached.height))
                    return@launch
                }
                // Free the previous image first; the new render needs the memory more.
                lastExport = null
                val image = withContext(Dispatchers.Default) {
                    // Whole percents, so the screen redraws at most a hundred times.
                    var shown = 0
                    val options = RenderOptions(
                        onProgress = { share ->
                            val percent = (share * 100).toInt()
                            if (percent > shown) {
                                shown = percent
                                _export.value = ExportProgress(label, percent / 100f)
                            }
                        },
                        width = width,
                        height = height,
                        framing = framing,
                        textFollowsMandala = textFollows,
                        supersample = !large,
                        threads = threads,
                        textRenderer = textRenderer,
                        imageLoader = imageLoader,
                    )
                    MandalaRenderer.render(snapshot, options).image
                }
                if (_saveOpen.value) lastExport = key to image
                then(image, Editing.fileName(snapshot, width, height))
            } catch (e: OutOfMemoryError) {
                message("Not enough memory for this image.")
            } catch (e: Exception) {
                message("Could not create the image: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _export.value = null
                _busy.value = false
            }
        }
    }

    private fun message(text: String, undo: (() -> Unit)? = null) {
        _events.trySend(EditorEvent.Message(text, undo))
    }

    private suspend fun renderFramePreview(request: FrameRequest) {
        recentFrames[request]?.let {
            _framePreview.value = it
            return
        }
        val (w, h) = Editing.size(request.choice.format, request.aspect, Editing.FRAME_PREVIEW_WIDTH)
        val framing = framingFor(request.choice, request.params, Editing.aspect(request.choice.format, request.aspect), request.pro)
        _framePreview.update { it.copy(rendering = true) }
        try {
            val result = withContext(Dispatchers.Default) {
                val job = coroutineContext.job
                MandalaRenderer.render(
                    request.params,
                    RenderOptions(
                        width = w, height = h, framing = framing, textFollowsMandala = request.choice.textFollowsMandala,
                        threads = threads, textRenderer = textRenderer, imageLoader = imageLoader, isCancelled = { !job.isActive },
                    ),
                )
            }
            _framePreview.value = FramePreviewState(result.image.toBitmap().asImageBitmap(), rendering = false, framing = framing, width = w, height = h)
            recentFrames[request] = _framePreview.value
        } catch (e: RenderCancelledException) {
            // A newer framing is already being rendered.
        } catch (e: OutOfMemoryError) {
            _framePreview.update { it.copy(rendering = false) }
        }
    }

    /** Visible layers the next preview cannot take from the last one. */
    private fun redrawnLayers(params: MandalaParameters): Int {
        val before = renderedParams?.takeIf { it.seed == params.seed }?.layers.orEmpty()
        return params.layers.count { it.isEnabled && it !in before }
    }

    /** A small, rough image while a slow change is drawn; not kept as the preview's result. */
    private suspend fun renderQuickPreview(params: MandalaParameters) {
        try {
            val result = withContext(Dispatchers.Default) {
                val job = coroutineContext.job
                MandalaRenderer.render(
                    params,
                    RenderOptions(
                        outputSize = Editing.QUICK_SIZE, glowReferenceWidth = Editing.QUICK_GLOW_REFERENCE, threads = threads,
                        textRenderer = textRenderer, imageLoader = imageLoader, isCancelled = { !job.isActive },
                    ),
                )
            }
            _preview.update { it.copy(image = result.image.toBitmap().asImageBitmap(), rendering = true, error = null) }
        } catch (e: RenderCancelledException) {
            // A newer change is already being rendered.
        } catch (e: OutOfMemoryError) {
            // The full preview reports it.
        }
    }

    private suspend fun renderPreview(params: MandalaParameters, fast: Boolean, redrawn: Int) {
        _preview.update { it.copy(rendering = true, error = null) }
        try {
            val result = withContext(Dispatchers.Default) {
                val job = coroutineContext.job
                MandalaRenderer.render(
                    params,
                    RenderOptions(
                        outputSize = Editing.PREVIEW_SIZE, threads = threads, textRenderer = textRenderer, imageLoader = imageLoader,
                        isCancelled = { !job.isActive }, layerCache = previewCache,
                        glowReferenceWidth = if (fast) Editing.FAST_GLOW_REFERENCE else RenderOptions().glowReferenceWidth,
                    ),
                )
            }
            val bitmap = result.image.toBitmap().asImageBitmap()
            _preview.value = PreviewState(image = bitmap, rendering = false, millis = result.millis, warnings = result.warnings)
            recentPreviews[params] = _preview.value
            renderedParams = params
            renderedFast = fast
            millisPerLayer = result.millis / maxOf(1, redrawn)
        } catch (e: RenderCancelledException) {
            // A newer change is already being rendered.
        } catch (e: OutOfMemoryError) {
            previewCache.clear()
            _preview.update { it.copy(rendering = false, error = "Not enough memory for the preview.") }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 150L
        const val SAVE_DELAY_MS = 400L
        const val HISTORY_DELAY_MS = 700L
        const val FRAME_DEBOUNCE_MS = 60L
        const val RECENT_PREVIEWS = 30
        const val RECENT_FRAMES = 6
        const val WALLPAPER_MESSAGE_DELAY_MS = 1500L
        const val FILL_ZOOMS_KEPT = 8

        /** Until the screen reports its own: a common 20:9 phone. */
        const val DEFAULT_SCREEN_ASPECT = 20.0 / 9.0
        const val STARTER_FAVORITES = "starter-favorites.json"

        fun randomSeed(): ULong = Random.nextLong().toULong()
    }
}
