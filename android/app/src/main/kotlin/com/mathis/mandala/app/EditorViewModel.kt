package com.mathis.mandala.app

import android.app.Application
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mathis.mandala.app.Editing.addLayer
import com.mathis.mandala.app.Editing.changeOwnPalettes
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
import com.mathis.mandala.app.Editing.reseedLayer
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
import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.DrawingLayerSettings
import com.mathis.mandala.core.EffectsLayerSettings
import com.mathis.mandala.core.EmblemLayerSettings
import com.mathis.mandala.core.GraffitiLayerSettings
import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.Finish
import com.mathis.mandala.core.Framing
import com.mathis.mandala.core.Image8
import com.mathis.mandala.core.LayerAnimation
import com.mathis.mandala.core.LayerCache
import com.mathis.mandala.core.MacFile
import com.mathis.mandala.core.CustomPalette
import com.mathis.mandala.core.CustomPaletteStop
import com.mathis.mandala.core.OwnPalettes
import com.mathis.mandala.core.Quote
import com.mathis.mandala.core.Quotes
import com.mathis.mandala.core.TextLayerSettings
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.ProFeature
import com.mathis.mandala.core.ProFeatures
import com.mathis.mandala.core.RenderCancelledException
import com.mathis.mandala.core.RenderOptions
import com.mathis.mandala.core.SettingsJson
import com.mathis.mandala.core.StyleLayer
import com.mathis.mandala.core.VectorExport
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import one.tearoom.unlock.UnlockState
import java.io.File
import java.util.UUID
import kotlin.random.Random

/** The play field while it is open (UX-1 19): its mode and the finger. */
data class PlayState(
    val mode: PlayField.Mode = PlayField.Mode.MAP,
    val touch: PlayField.Touch = PlayField.Touch(),
    /** The picture as it is now was added to the favourites; the star stays filled until it changes. */
    val kept: Boolean = false,
)

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

/** Whether the daily wallpaper is on, and the favourite it set last. */
data class DailyState(val on: Boolean, val lastName: String?, val chosen: Set<String>? = null)

/** The pair sheet's lock and home screen (ADR-0014). */
data class PairPreview(val lock: ImageBitmap? = null, val home: ImageBitmap? = null, val rendering: Boolean = true)

/** How much a new mandala from the menu changes (DM-7): more dots on its die, more change. */
enum class NewKind(@StringRes val label: Int) { VARIATION(R.string.new_kind_variation), LAYERS(R.string.new_kind_layers), EVERYTHING(R.string.new_kind_everything) }

/** A new mandala shown next to the current one in the menu; nothing is replaced until it is used. */
data class Candidate(val kind: NewKind, val params: MandalaParameters, val image: ImageBitmap? = null, val rendering: Boolean = true)

/**
 * The Pro sheet's reason to open (ADR-0016): the Pro features the mandala uses, what the user was
 * about to do with it, and that step, taken again once the features are removed; or a plain `note`
 * (the favourites limit). A request without any of them is the sheet opened from an "About Pro" link.
 */
data class ProRequest(
    val features: Set<ProFeature> = emptySet(),
    val action: String? = null,
    val note: String? = null,
    val then: (() -> Unit)? = null,
)

/** One-off things for the screen to do. */
data class HistoryState(val canGoBack: Boolean, val canGoForward: Boolean)

sealed interface EditorEvent {
    /** `undo` restores what the message reports; changes to the mandala are undone with Back instead. */
    data class Message(val text: String, val undo: (() -> Unit)? = null) : EditorEvent
    data class ShareImage(val uri: Uri, val type: String = "image/png") : EditorEvent
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
    /** The app-wide Pro purchase (ADR-0022); its Play connection stays open with the app. */
    private val pro = Pro.unlock(application)

    /** Whether Pro is unlocked, its price, a pending payment and purchase or restore feedback. */
    val proState: StateFlow<UnlockState> = pro.state

    fun buyPro(activity: android.app.Activity) = pro.purchase(activity)

    /** Asks Google Play again; on every resume (purchases elsewhere, refunds) and from "Try again". */
    fun refreshPro() = pro.refresh()

    /** Asks Google Play for an earlier purchase and reports the answer. */
    fun restorePro() = pro.restore()

    // ── The line between free and Pro (ADR-0016) ──

    private val _proRequest = MutableStateFlow<ProRequest?>(null)

    /** Why the Pro sheet is open; null while it is closed. */
    val proRequest: StateFlow<ProRequest?> = _proRequest.asStateFlow()

    init {
        // Purchase and restore feedback shows inside the Pro sheet while it is open, otherwise as a message.
        viewModelScope.launch {
            combine(pro.state, _proRequest) { state, request -> state.message.takeIf { request == null } }.collect { shown ->
                if (shown != null) {
                    message(string(shown.textRes))
                    pro.clearMessage()
                }
            }
        }
    }

    /** The Pro features the mandala uses that the user does not own: empty with Pro. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val missingPro: StateFlow<Set<ProFeature>> = combine(_params, pro.state) { p, pr -> if (pr.unlocked) emptySet() else ProFeatures.of(p) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Opens the Pro sheet from an "About Pro" link or the mark on the mandala. */
    fun showPro() {
        _proRequest.value = ProRequest(features = missingPro.value)
    }

    fun closePro() {
        // Feedback already read in the sheet is not repeated; the thanks after a purchase is shown.
        if (!pro.state.value.purchased) pro.clearMessage()
        _proRequest.value = null
    }

    /**
     * Pro features may be tried, but saving, sharing or setting as wallpaper a mandala that uses them
     * needs Pro: returns true when `step` may go ahead, otherwise opens the Pro sheet, which can take
     * it after removing them. Favourites are not gated (ADR-0016).
     */
    private fun mayKeep(action: String, step: () -> Unit): Boolean {
        val missing = if (pro.state.value.unlocked) emptySet() else ProFeatures.of(_params.value)
        if (missing.isEmpty()) return true
        _proRequest.value = ProRequest(missing, action, then = step)
        return false
    }

    /** Hides the Pro features (Back brings them back) and takes the step the sheet was opened for, if any. */
    fun continueWithoutPro() {
        val request = _proRequest.value ?: return
        _proRequest.value = null
        _params.update { ProFeatures.removed(it) }
        request.then?.invoke()
    }

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
        val width: Int, val height: Int, val supersample: Boolean, val finish: Finish,
    )
    private var lastExport: Pair<ExportKey, Image8>? = null

    fun setSaveFormat(format: Editing.SaveFormat) = _saveChoice.update { it.withFormat(format) }

    /** "Fill" or "Show whole", free in every format; with Pro it ends placing by hand. */
    fun setFill(fill: Boolean) = _saveChoice.update { it.copy(fit = if (fill) Fit.FILL else Fit.WHOLE) }

    private fun aspectOf(format: Editing.SaveFormat) = Editing.aspect(format, _screenAspect.value)

    /** Moving and zooming the mandala is part of Pro; without it the mandala stays on its preset. */
    fun setFraming(framing: Framing) {
        if (pro.state.value.unlocked) _saveChoice.update { it.copy(framing = framing.clamped(), fit = Fit.HAND, framedByHand = true) }
    }

    /** The quote moving with the mandala, part of "Adjust" and of Pro (ADR-0016). */
    fun setTextFollowsMandala(follows: Boolean) {
        if (pro.state.value.unlocked) _saveChoice.update { it.copy(textFollowsMandala = follows) }
    }

    /** Whether the quote moves with the mandala in exports: as chosen with Pro, never without. */
    private fun effectiveTextFollows(): Boolean = pro.state.value.unlocked && _saveChoice.value.textFollowsMandala

    /** A picture or a video of the layers turning (UX-1 12); anyone may look, keeping the video is Pro. */
    fun setMoving(moving: Boolean) = _saveChoice.update { it.copy(moving = moving) }

    fun setVideoSeconds(seconds: Int) {
        if (seconds in LayerAnimation.SECONDS) _saveChoice.update { it.copy(videoSeconds = seconds) }
    }

    /** The last adjustment of saved images and wallpapers (ADR-0014), part of Pro. */
    fun setFinish(finish: Finish) {
        if (pro.state.value.unlocked) _saveChoice.update { it.copy(finish = finish.clamped()) }
    }

    fun setHomeBrightness(brightness: Double) {
        if (pro.state.value.unlocked) _saveChoice.update { it.copy(homeBrightness = brightness.coerceIn(SaveChoice.MIN_HOME_BRIGHTNESS, 1.0)) }
    }

    /** The finish exports get: the chosen one with Pro, none without. */
    private fun effectiveFinish(): Finish = if (pro.state.value.unlocked) _saveChoice.value.finish else Finish()

    /** Fill zooms measured recently, by mandala and shape; touched only on the main thread. */
    private val fillZooms = LinkedHashMap<Pair<MandalaParameters, Double>, Double>()

    /** The framing the save sheet shows and images are drawn with, for the current mandala. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val shownFraming: StateFlow<Framing> = combine(_saveChoice, _params, _screenAspect, pro.state) { c, p, a, pr -> FramingInput(c, p, Editing.aspect(c.format, a), pr.unlocked) }
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

    /** Previews rendered recently, by their settings; about 1 MB each. */
    private val recentPreviews = RecentImages<MandalaParameters, PreviewState>(RECENT_PREVIEWS)

    private val appearance = Appearance(application)
    private val _fastPreview = MutableStateFlow(appearance.fastPreview)

    /** Whether the preview blurs its glow at a smaller size (quicker, a touch softer; saved images never). */
    val fastPreview: StateFlow<Boolean> = _fastPreview.asStateFlow()

    private val _chipPreviews = MutableStateFlow(appearance.chipPreviews)

    /** Small pictures in the style and emblem lists (Mathis, 27 October 2026: a switch, as they make the lists long). */
    val chipPreviews: StateFlow<Boolean> = _chipPreviews.asStateFlow()

    fun setChipPreviews(on: Boolean) {
        appearance.chipPreviews = on
        _chipPreviews.value = on
    }

    private val _previewSize = MutableStateFlow(appearance.previewSize)

    /** How large the phone preview stands above the controls; kept across starts (UX-1 11). */
    val previewSize: StateFlow<Float> = _previewSize.asStateFlow()

    fun setPreviewSize(size: Float) {
        appearance.previewSize = size
        _previewSize.value = size
    }

    /** The preview without the scene effects, what it was drawn from, and how (UX-1 16). */
    private class Plain(val params: MandalaParameters, val fast: Boolean, val image: ImageBitmap)

    private val _plain = MutableStateFlow<Plain?>(null)
    private var plainJob: Job? = null
    private var plainFor: MandalaParameters? = null

    /** The preview without the scene effects, while it matches the settings; null until drawn. */
    val plainPreview: StateFlow<ImageBitmap?> = combine(_params, _fastPreview, _plain) { p, fast, plain ->
        plain?.takeIf { it.params == p && it.fast == fast }?.image
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Draws the preview without the scene effects once, when a finger starts holding the preview;
     * kept until the settings change. The layer images come from the preview's cache, so only the
     * picture is put together again.
     */
    fun preparePlainPreview() {
        val p = _params.value
        val fast = _fastPreview.value
        if (!p.effectsLayer.isEnabled || plainPreview.value != null || plainJob?.isActive == true && plainFor == p) return
        plainJob?.cancel()
        plainFor = p
        plainJob = viewModelScope.launch {
            try {
                val result = previewLock.withLock {
                    withContext(Dispatchers.Default) {
                        val job = coroutineContext.job
                        MandalaRenderer.render(
                            p.copy(effectsLayer = p.effectsLayer.copy(isEnabled = false)),
                            previewOptions(fast) { !job.isActive },
                        )
                    }
                }
                _plain.value = Plain(p, fast, result.image.toBitmap().asImageBitmap())
            } catch (e: RenderCancelledException) {
                // The settings changed while it was drawn.
            } catch (e: OutOfMemoryError) {
                previewCache.clear()
            }
        }
    }

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
    /** The preview and the one without effects share [previewCache], so they never draw at once. */
    private val previewLock = Mutex()

    /** Save sheet previews rendered recently, by everything they were rendered from. */
    private val recentFrames = RecentImages<FrameRequest, FramePreviewState>(RECENT_FRAMES)

    /** The last frame preview as drawn, before its finish (about 0.4 MB). */
    private var lastFrame: Pair<FrameRequest, Image8>? = null

    private val textRenderer = AndroidTextRenderer()
    private val imageLoader = AndroidImageLoader()

    /** The pair sheet's two small screens, only while it is open. */
    private val _pairOpen = MutableStateFlow(false)
    private val _pairPreview = MutableStateFlow(PairPreview())
    val pairPreview: StateFlow<PairPreview> = _pairPreview.asStateFlow()

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
            // The pair sheet's preview, only while it is open; the finishes follow the sliders at once.
            combine(_params, _saveChoice, _screenAspect, _pairOpen) { p, c, a, open -> if (open) Triple(p, c, a) else null }
                .collectLatest { r -> if (r != null) renderPairPreview(r.first, r.second, r.third) }
        }
        viewModelScope.launch {
            // The save sheet's preview, only while the sheet is open.
            @OptIn(FlowPreview::class)
            combine(_params, _saveChoice, _screenAspect, _saveOpen, pro.state) { p, c, a, open, pr -> if (open) FrameRequest(p, c, a, pr.unlocked) else null }
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

    /** The selected layer keeps its style, palette and sliders and gets a new arrangement. */
    fun reseedSelected() = _params.update { it.reseedLayer(_selected.value, randomSeed()) }

    fun updateBackground(change: (BaseLayerSettings) -> BaseLayerSettings) = _params.update { it.updateBackground(change) }

    fun updateEffects(change: (EffectsLayerSettings) -> EffectsLayerSettings) = _params.update { it.updateEffects(change) }

    fun resetEffects() = updateEffects { it.reset() }

    fun newEffectsPattern() = updateEffects { it.newPattern(::randomSeed) }

    /** A new look of the effects, like the dice of the layers, the emblem and the text. */
    fun randomizeEffects() = updateEffects { it.randomized(Random) }

    fun updateText(change: (TextLayerSettings) -> TextLayerSettings) = _params.update { it.updateText(change) }

    /** A new look for the text; with no words yet, a quote comes in too, as on the Mac. */
    fun randomizeText() {
        if (_params.value.textLayer.text.isBlank()) randomQuote()
        updateText { it.randomized(Random) }
    }

    fun resetText() = updateText { it.reset() }

    fun updateEmblem(change: (EmblemLayerSettings) -> EmblemLayerSettings) = _params.update { it.updateEmblem(change) }

    fun randomizeEmblem() = updateEmblem { it.randomized(Random) }

    fun resetEmblem() = updateEmblem { it.reset() }

    fun newEmblemShape() = updateEmblem { it.newShape(Random) }

    fun useQuote(quote: Quote) = updateText { it.withQuote(quote) }

    /** A quote from the languages switched on in the quote list, or one of the user's own. */
    fun randomQuote() = useQuote((Quotes.collectionsFor(_quoteLanguages.value).flatMap { it.quotes } + _myQuotes.value).random())

    private val quoteLanguageStore = QuoteLanguages(application)
    private val _quoteLanguages = MutableStateFlow(quoteLanguageStore.chosen)

    /** The quote languages switched on, in the order the list shows them (ADR-0019). */
    val quoteLanguages: StateFlow<List<String>> = _quoteLanguages.asStateFlow()

    /** Switches a quote language on or off; the last one stays on. */
    fun toggleQuoteLanguage(language: String) {
        val current = _quoteLanguages.value
        val next = if (language in current) current - language else current + language
        if (next.isEmpty()) return
        _quoteLanguages.value = next
        quoteLanguageStore.chosen = next
    }

    private val myQuotesStore = MyQuotesStore(File(application.filesDir, "my-quotes.json"))
    private val _myQuotes = MutableStateFlow(myQuotesStore.load())

    /** Quotes the user saved, newest first. */
    val myQuotes: StateFlow<List<Quote>> = _myQuotes.asStateFlow()

    /** Keeps the text currently on the image (with its author) among the user's quotes. */
    fun saveTextAsMyQuote() {
        val text = _params.value.textLayer
        changeMyQuotes { MyQuotes.add(it, Quote(text.text, text.customAuthor)) }
        message(string(R.string.message_quote_saved))
    }

    fun removeMyQuote(quote: Quote) = changeMyQuotes { MyQuotes.remove(it, quote) }

    private fun changeMyQuotes(change: (List<Quote>) -> List<Quote>) {
        val next = change(_myQuotes.value)
        _myQuotes.value = next
        viewModelScope.launch(Dispatchers.IO) { runCatching { myQuotesStore.save(next) } }
    }

    private val myPalettesStore = MyPalettesStore(File(application.filesDir, "palettes.json"))
    private val _myPalettes = MutableStateFlow(myPalettesStore.load())

    /** The user's own palettes, in the order made (ADR-0021). */
    val myPalettes: StateFlow<List<CustomPalette>> = _myPalettes.asStateFlow()

    /** A new id in the Mac's form (an upper-case UUID). */
    fun newPaletteId(): String = UUID.randomUUID().toString().uppercase()

    /** Saves an own palette, new or changed; layers and the emblem using it take the new version. */
    fun saveMyPalette(edited: CustomPalette) {
        // The Mac's palettes.json needs an id on every stop.
        val palette = edited.copy(stops = edited.stops.map { if (it.id.isEmpty()) it.copy(id = newPaletteId()) else it })
        changeMyPalettes { MyPalettes.put(it, palette) }
        _params.update { p -> p.changeOwnPalettes { if (it.id == palette.id) palette else it } }
    }

    /** Deletes an own palette; layers using it go back to the built-in palette that looks most like it. */
    fun deleteMyPalette(id: String) {
        changeMyPalettes { MyPalettes.remove(it, id) }
        _params.update { p -> p.changeOwnPalettes { if (it.id == id) null else it } }
    }

    /** Keeps a palette that came with a favourite or a file; a new id if one of the user's has its id. */
    fun keepPalette(palette: CustomPalette) {
        val known = _myPalettes.value.any { it.id == palette.id } || palette.id.isEmpty()
        val kept = (if (known) palette.copy(id = newPaletteId()) else palette)
            .let { p -> p.copy(stops = p.stops.map { if (it.id.isEmpty()) it.copy(id = newPaletteId()) else it }) }
        changeMyPalettes { MyPalettes.put(it, kept) }
        _params.update { p -> p.changeOwnPalettes { if (it == palette) kept else it } }
    }

    /** Colours from the picture as it is shown now, dark to bright; nothing while there is no picture. */
    fun coloursFromPicture(onResult: (List<CustomPaletteStop>) -> Unit) {
        val image = _preview.value.image ?: return
        viewModelScope.launch {
            val stops = withContext(Dispatchers.Default) {
                val pixels = IntArray(image.width * image.height)
                image.readPixels(pixels)
                OwnPalettes.fromPixels(pixels).map { it.copy(id = newPaletteId()) }
            }
            onResult(stops)
        }
    }

    private fun changeMyPalettes(change: (List<CustomPalette>) -> List<CustomPalette>) {
        val next = change(_myPalettes.value)
        _myPalettes.value = next
        viewModelScope.launch(Dispatchers.IO) { runCatching { myPalettesStore.save(next) } }
    }

    /** Uses a picked photo as the background (copied into the app's files). */
    fun useBackgroundImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val url = withContext(Dispatchers.IO) { BackgroundImages.import(getApplication(), uri) }
                updateBackground { it.copy(isEnabled = true, type = BaseLayerType.IMAGE, imageURL = url) }
            } catch (e: Exception) {
                message(string(R.string.message_picture_failed, e.message ?: e.javaClass.simpleName))
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
        _params.update { it.randomAll(Random, surpriseLayers()) }
        _selected.value = 0
    }

    /** Surprise mandalas stay within the free line without Pro, so they can always be kept. */
    private fun surpriseLayers(): Int = if (pro.state.value.unlocked) Randomize.MAX_LAYERS else ProFeatures.FREE_LAYERS

    private val _candidate = MutableStateFlow<Candidate?>(null)

    /** The new mandala the menu shows next to the current one, if any. */
    val candidate: StateFlow<Candidate?> = _candidate.asStateFlow()
    private var candidateJob: Job? = null

    /** Makes a new mandala of this kind from the current one and draws it small; the current one stays. */
    fun tryNew(kind: NewKind) {
        val current = _params.value
        val next = when (kind) {
            NewKind.VARIATION -> current.newVariation(::randomSeed)
            NewKind.LAYERS -> current.randomLayers(Random, surpriseLayers())
            NewKind.EVERYTHING -> current.randomAll(Random, surpriseLayers())
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
        _params.update { it.randomLayers(Random, surpriseLayers()) }
        _selected.value = 0
    }

    fun randomizeSelected() = _params.update { it.randomLayer(_selected.value, Random) }

    /** A new variant of the selected layer in the same style. */
    fun varySelected() = _params.update { it.randomVariant(_selected.value, Random) }

    // ── Positions of layers and the emblem (ADR-0011) ──

    private val _moveTarget = MutableStateFlow<MoveTarget?>(null)

    /** What drags on the canvas move: the selected layer or the emblem; null while they do not. Moved layers are Pro to keep. */
    val moveTarget: StateFlow<MoveTarget?> = _moveTarget.asStateFlow()

    fun setMoveTarget(target: MoveTarget?) {
        _moveTarget.value = target
        if (target != null) _drawTool.value = null
    }

    private val snapX = CentreSnap()
    private val snapY = CentreSnap()

    /**
     * Moves, sizes and turns the move target by a gesture on the canvas (fractions of its width),
     * snapping it onto the centre lines (UX-1 20). The text only moves up and down and does not turn.
     */
    fun moveBy(dx: Double, dy: Double, zoom: Double, turnDegrees: Double) {
        val offsets = -Editing.MAX_OFFSET..Editing.MAX_OFFSET
        when (_moveTarget.value) {
            MoveTarget.LAYER -> updateSelected {
                it.movedBy(0.0, 0.0, zoom, turnDegrees)
                    .copy(offsetX = snapX.next(it.offsetX, dx, 0.0, offsets), offsetY = snapY.next(it.offsetY, dy, 0.0, offsets))
            }
            MoveTarget.EMBLEM -> updateEmblem {
                it.movedBy(0.0, 0.0, zoom, turnDegrees)
                    .copy(offsetX = snapX.next(it.offsetX, dx, 0.0, offsets), offsetY = snapY.next(it.offsetY, dy, 0.0, offsets))
            }
            // The text's height counts up from the bottom.
            MoveTarget.TEXT -> updateText { it.movedBy(0.0, zoom).copy(offsetY = snapY.next(it.offsetY, -dy, 0.5, 0.0..1.0)) }
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

    /** Favourites keep Pro features and open for everyone, like the starters (ADR-0016, 24 October 2026). */
    fun addFavorite() {
        val image = _preview.value.image ?: return
        val favorite = Favorite(
            id = UUID.randomUUID().toString().uppercase(),
            parameters = _params.value,
            thumbnailData = Thumbnails.encode(image),
            createdAt = Favorite.createdNow(System.currentTimeMillis()),
        )
        _favorites.update { Favorites.add(it, favorite) }
        message(string(R.string.message_favourite_added))
    }

    /** Opens a favourite by id, as the home screen widget asks for with a tap (ADR-0020). */
    fun applyFavoriteId(id: String) {
        _favorites.value.firstOrNull { it.id == id }?.let(::applyFavorite)
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
        message(string(R.string.message_favourite_deleted), undo = {
            _favorites.update { list -> if (list.any { it.id == removed.id }) list else list.toMutableList().apply { add(index.coerceAtMost(size), removed) } }
        })
    }

    /** Deletes several favourites at once; one "Undo" brings them all back in their places. */
    fun removeFavorites(ids: Set<String>) {
        val before = _favorites.value
        if (before.none { it.id in ids }) return
        _favorites.update { list -> list.filterNot { it.id in ids } }
        message(string(R.string.message_favourites_deleted, before.count { it.id in ids }), undo = {
            _favorites.update { current ->
                val kept = current.associateBy { it.id }
                // The old order, with favourites added since then kept at the end.
                before.filter { it.id in ids || it.id in kept } + current.filter { c -> before.none { it.id == c.id } }
            }
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
                message(string(R.string.message_file_unreadable))
                return@launch
            }
            when (val file = runCatching { SettingsJson.decodeMacFile(text) }.getOrNull()) {
                is MacFile.Settings -> {
                    if (file.params.layers.isEmpty()) {
                        message(string(R.string.message_file_no_layers))
                    } else {
                        replace(file.params)
                    }
                }
                is MacFile.Favorites -> {
                    var added = 0
                    _favorites.update { current ->
                        val merged = Favorites.merge(current, file.favorites)
                        added = merged.added
                        merged.list
                    }
                    val already = file.favorites.size - added
                    val imported = plural(R.plurals.message_favourites_imported, added)
                    message(if (already > 0) string(R.string.message_favourites_imported_note, imported, plural(R.plurals.message_favourites_already_there, already)) else imported)
                }
                null -> message(string(R.string.message_file_unknown))
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
            message(string(if (ok) R.string.message_settings_saved else R.string.message_settings_failed))
        }
    }

    fun suggestedSettingsName(): String = Editing.fileName(_params.value).removeSuffix(".png") + ".json"

    // ── Play field (UX-1 19, DM-20) ──

    private val _play = MutableStateFlow<PlayState?>(null)
    val play: StateFlow<PlayState?> = _play.asStateFlow()

    /** The mandala when the field opened: its middle, and what Reset goes back to. */
    private var playBase: MandalaParameters? = null
    private var playLand: PlayField.Landscape? = null

    fun openPlay() {
        playBase = _params.value
        _play.value = PlayState()
        newPlayLandscape()
    }

    /** Closes the field; the mandala stays as played, and Back in the editor undoes it. */
    fun closePlay() {
        _play.value = null
        playBase = null
        playLand = null
    }

    fun setPlayMode(mode: PlayField.Mode) {
        val state = _play.value ?: return
        _play.value = state.copy(mode = mode, kept = false)
        applyPlay()
    }

    /** One finger on the field, as fractions of its width and height. */
    fun playTouch(x: Double, y: Double) {
        val state = _play.value ?: return
        _play.value = state.copy(touch = state.touch.copy(x = x.coerceIn(0.0, 1.0), y = y.coerceIn(0.0, 1.0)), kept = false)
        applyPlay()
    }

    /** Back to the middle: the mandala as it was when the field opened. */
    fun resetPlay() {
        val base = playBase ?: return
        _play.update { it?.copy(touch = PlayField.Touch(), kept = false) }
        _params.value = base
    }

    /** Adds the picture as played to the favourites; the sheet's star fills until the picture changes. */
    fun playKeep() {
        if (_play.value == null) return
        addFavorite()
        _play.update { it?.copy(kept = true) }
    }

    /**
     * The landscape: a new one around the same middle; the point stays where it is, so the picture shows
     * the new landscape at once, and hops a step out of the middle, where it would show nothing. The
     * compass: start from here.
     */
    fun playAgain() {
        val state = _play.value ?: return
        if (state.mode == PlayField.Mode.MAP) {
            _play.value = state.copy(touch = PlayField.hopOutOfTheMiddle(state.touch, Random), kept = false)
            newPlayLandscape()
            applyPlay()
        } else {
            playBase = _params.value
            _play.value = state.copy(touch = PlayField.Touch(), kept = false)
        }
    }

    /** A new landscape around the mandala as it is now; it is explored by moving, not shown in advance. */
    private fun newPlayLandscape() {
        val base = playBase ?: return
        playLand = PlayField.Landscape(System.nanoTime(), base, BuiltInPalettes.all.size)
    }

    private fun applyPlay() {
        val base = playBase ?: return
        val land = playLand ?: return
        val state = _play.value ?: return
        _params.value = PlayField.apply(base, state.mode, land, state.touch)
    }

    // ── Images ──

    /**
     * Saves in the chosen format and framing: 1024 px wide, or with Pro (`large`) 2048 px wide, drawn
     * without supersampling so it needs no more memory than the normal export.
     */
    fun saveImage(large: Boolean) {
        if (large && !pro.state.value.unlocked) return
        if (!mayKeep(string(R.string.action_save)) { saveImage(large) }) return
        val choice = _saveChoice.value
        val (w, h) = Editing.exportSize(choice.format, _screenAspect.value, large)
        val params = _params.value
        val framing: suspend () -> Framing = { framingFor(choice, params, aspectOf(choice.format), pro.state.value.unlocked) }
        renderFullSize(string(R.string.export_creating_image), w, h, large, framing, effectiveTextFollows(), finish = effectiveFinish()) { image, fileName ->
            exportStep(string(R.string.export_saving_to_pictures))
            withContext(Dispatchers.IO) { GalleryExport.savePng(getApplication(), image, fileName) }
            message(string(R.string.message_saved_to_pictures, w, h, GalleryExport.FOLDER))
        }
    }

    /**
     * Sets the image as wallpaper in the phone screen format, at the largest size the user may save;
     * an image of this screen format just saved is reused when it is at least as wide as the screen.
     * From the save sheet it takes the framing in the frame; from the menu (`filled`) the mandala
     * fills the screen, which suits a wallpaper best.
     */
    fun setWallpaper(target: WallpaperTarget, filled: Boolean = false) {
        if (!mayKeep(string(R.string.action_set_wallpaper)) { setWallpaper(target, filled) }) return
        val choice = _saveChoice.value
        val large = pro.state.value.unlocked
        val (w, h) = Editing.exportSize(Editing.SaveFormat.SCREEN, _screenAspect.value, large)
        val screen = aspectOf(Editing.SaveFormat.SCREEN)
        val params = _params.value
        // A wallpaper is always in the screen format, even when the sheet was last left on the square.
        val shown = choice.withFormat(Editing.SaveFormat.SCREEN)
        val framing: suspend () -> Framing = { framingFor(if (filled) shown.copy(fit = Fit.FILL) else shown, params, screen, large) }
        val reuse = { k: ExportKey, f: Framing ->
            k.params == params && k.framing == f && k.textFollows == effectiveTextFollows() && k.finish == effectiveFinish() &&
                k.width >= screenShortSide && kotlin.math.abs(k.height.toDouble() / k.width - h.toDouble() / w) < 0.002
        }
        renderFullSize(string(R.string.export_creating_wallpaper), w, h, large, framing, effectiveTextFollows(), reuse, effectiveFinish()) { image, _ ->
            exportStep(string(R.string.export_setting_wallpaper))
            withContext(Dispatchers.IO) { Wallpapers.set(getApplication(), image, target) }
            _export.value = null
            // Android recreates the screen when the wallpaper changes; a message sent right away is lost with it.
            delay(WALLPAPER_MESSAGE_DELAY_MS)
            message(string(R.string.message_wallpaper_set))
        }
    }

    fun shareImage() {
        if (mayKeep(string(R.string.action_share), ::shareImage)) shareSquare()
    }

    private fun shareSquare() = renderFullSize(string(R.string.export_creating_image), Editing.EXPORT_SIZE, Editing.EXPORT_SIZE, large = false, { Framing() }, textFollows = false) { image, fileName ->
        exportStep(string(R.string.export_preparing_share))
        val uri = withContext(Dispatchers.IO) { ImageShare.prepare(getApplication(), image, fileName) }
        _events.send(EditorEvent.ShareImage(uri))
    }

    private var videoJob: Job? = null

    /** True while a video is made; the screen stays on meanwhile. */
    private val _makingVideo = MutableStateFlow(false)
    val makingVideo: StateFlow<Boolean> = _makingVideo.asStateFlow()

    /**
     * Makes a video of the layers turning against each other (UX-1 12, Pro) in the chosen format and
     * framing, 720 or (`large`) 1080 on the shorter side, and saves it to Movies or (`share`) shares it.
     * The layers are drawn once; each frame turns and blends them and runs the scene effects.
     */
    fun saveVideo(large: Boolean, share: Boolean) {
        if (!pro.state.value.unlocked) {
            _proRequest.value = ProRequest(missingPro.value, note = string(R.string.save_video_pro))
            return
        }
        if (_busy.value) return
        val choice = _saveChoice.value
        val (w, h) = Editing.videoSize(choice.format, _screenAspect.value, large)
        val params = _params.value
        val seconds = choice.videoSeconds
        val frames = LayerAnimation.frameCount(seconds)
        val label = string(R.string.export_creating_video)
        _busy.value = true
        _makingVideo.value = true
        _export.value = ExportProgress(label, 0f)
        videoJob = viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                if (!Mp4Writer.supports(w, h, LayerAnimation.FPS)) {
                    message(string(R.string.message_video_size, w, h))
                    return@launch
                }
                val framing = framingFor(choice, params, aspectOf(choice.format), true)
                val fileName = Editing.videoFileName(params, w, h, seconds)
                val file = withContext(Dispatchers.Default) {
                    val job = coroutineContext.job
                    val options = LayerAnimation.options(
                        RenderOptions(
                            width = w, height = h, framing = framing, textFollowsMandala = pro.state.value.unlocked && choice.textFollowsMandala,
                            supersample = false, finish = choice.finish, threads = threads,
                            textRenderer = textRenderer, imageLoader = imageLoader, isCancelled = { !job.isActive },
                        ),
                        LayerCache(),
                    )
                    val file = VideoFiles.workFile(context, fileName)
                    Mp4Writer(file, w, h, LayerAnimation.FPS, threads).use { writer ->
                        val start = SystemClock.elapsedRealtime()
                        var shown = label
                        for (f in 0 until frames) {
                            writer.add(MandalaRenderer.render(LayerAnimation.frame(params, f, frames), options).image)
                            // The first frame draws the layers; from a few frames on, the pace says how long is left.
                            if (f >= TIME_LEFT_AFTER_FRAMES) {
                                val left = (SystemClock.elapsedRealtime() - start) * (frames - f - 1) / (f + 1)
                                shown = if (left < UNDER_A_MINUTE_MS) {
                                    string(R.string.export_video_under_minute)
                                } else {
                                    string(R.string.export_video_minutes, maxOf(1L, (left + 30_000) / 60_000).toInt())
                                }
                            }
                            _export.value = ExportProgress(shown, (f + 1f) / frames)
                        }
                        writer.finish()
                    }
                    file
                }
                if (share) {
                    exportStep(string(R.string.export_preparing_share))
                    _events.send(EditorEvent.ShareImage(VideoFiles.shareUri(context, file), VideoFiles.MIME))
                } else {
                    exportStep(string(R.string.export_saving_to_movies))
                    withContext(Dispatchers.IO) { VideoFiles.saveToMovies(context, file) }
                    message(string(R.string.message_video_saved, w, h, GalleryExport.FOLDER))
                }
            } catch (e: RenderCancelledException) {
                // Cancelled from the sheet.
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                message(string(R.string.message_image_memory))
            } catch (e: Exception) {
                message(string(R.string.message_video_failed, e.message ?: e.javaClass.simpleName))
            } finally {
                _export.value = null
                _busy.value = false
                _makingVideo.value = false
                videoJob = null
            }
        }
    }

    fun cancelVideo() {
        videoJob?.cancel()
    }

    /**
     * Writes the mandala as an SVG line drawing (ADR-0013, Pro) to a file the user picked: the
     * layers' lines and the emblem, square and centred, without the pixel steps.
     */
    fun saveLineDrawing(look: VectorExport.Look, uri: Uri) {
        if (!pro.state.value.unlocked || _busy.value) return
        val snapshot = _params.value
        _busy.value = true
        _export.value = ExportProgress(string(R.string.export_creating_line_drawing))
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt") ?: error("no stream")
                    stream.bufferedWriter().use { VectorExport.svg(snapshot, Editing.EXPORT_SIZE, look, it) }
                }
                message(string(R.string.message_line_drawing_saved))
            } catch (e: OutOfMemoryError) {
                message(string(R.string.message_line_drawing_memory))
            } catch (e: Exception) {
                message(string(R.string.message_line_drawing_failed, e.message ?: e.javaClass.simpleName))
            } finally {
                _export.value = null
                _busy.value = false
            }
        }
    }

    fun suggestedLineDrawingName(look: VectorExport.Look): String =
        Editing.fileName(_params.value).removeSuffix(".png") + (if (look == VectorExport.Look.LINES) "-lines" else "") + ".svg"

    /**
     * The wallpaper pair (ADR-0014, Pro): the mandala filling the screen, as it is on the lock screen
     * and darker by `homeBrightness` on the home screen; drawn once, finished twice.
     */
    fun setWallpaperPair() {
        if (!pro.state.value.unlocked) return
        val choice = _saveChoice.value
        val (w, h) = Editing.exportSize(Editing.SaveFormat.SCREEN, _screenAspect.value, large = true)
        val params = _params.value
        val framing: suspend () -> Framing = { framingFor(choice.copy(fit = Fit.FILL), params, aspectOf(Editing.SaveFormat.SCREEN), pro = true) }
        renderFullSize(string(R.string.export_creating_wallpapers), w, h, large = true, framing, effectiveTextFollows(), finish = effectiveFinish()) { image, _ ->
            exportStep(string(R.string.export_setting_both_wallpapers))
            val home = withContext(Dispatchers.Default) { Finish(brightness = choice.homeBrightness).apply(image, threads) }
            withContext(Dispatchers.IO) { Wallpapers.setPair(getApplication(), image, home) }
            _export.value = null
            delay(WALLPAPER_MESSAGE_DELAY_MS)
            message(string(R.string.message_pair_set))
        }
    }

    /** The daily wallpaper (ADR-0014, Pro): on or off, and the favourite it set last. */
    private val _daily = MutableStateFlow(DailyState(DailyWallpaper.isOn(application), DailyWallpaper.lastName(application), DailyWallpaper.chosen(application)))
    val daily: StateFlow<DailyState> = _daily.asStateFlow()

    init {
        DailyWallpaper.ensureScheduled(application)
    }

    /** Switching it on sets the first one right away, so it shows at once; then every morning. */
    /** Ticks or unticks one favourite for the daily wallpaper (see [DailyWallpaper.toggled]). */
    fun toggleDailyFavourite(id: String) {
        val app = getApplication<Application>()
        val chosen = DailyWallpaper.toggled(_daily.value.chosen, _favorites.value.map { it.id }, id)
        DailyWallpaper.setChosen(app, chosen)
        _daily.update { it.copy(chosen = chosen) }
    }

    fun chooseAllDailyFavourites() {
        DailyWallpaper.setChosen(getApplication(), null)
        _daily.update { it.copy(chosen = null) }
    }

    fun setDaily(on: Boolean) {
        val app = getApplication<Application>()
        if (!on) {
            DailyWallpaper.setOn(app, false)
            _daily.update { it.copy(on = false) }
            return
        }
        if (!pro.state.value.unlocked || _busy.value) return
        if (_favorites.value.isEmpty()) {
            message(string(R.string.message_daily_needs_favourite))
            return
        }
        DailyWallpaper.setOn(app, true)
        _daily.update { it.copy(on = true) }
        _busy.value = true
        _export.value = ExportProgress(string(R.string.export_setting_daily))
        viewModelScope.launch {
            try {
                val name = withContext(Dispatchers.Default) {
                    DailyWallpaper.setNext(app, threads).also { runCatching { MandalaWidget.refresh(app) } }
                }
                _daily.update { it.copy(lastName = DailyWallpaper.lastName(app)) }
                _export.value = null
                delay(WALLPAPER_MESSAGE_DELAY_MS)
                message(if (name.isNullOrEmpty()) string(R.string.message_daily_on) else string(R.string.message_daily_on_today, name))
            } catch (e: OutOfMemoryError) {
                message(string(R.string.message_image_memory))
            } catch (e: Exception) {
                message(string(R.string.message_wallpaper_failed, e.message ?: e.javaClass.simpleName))
            } finally {
                _export.value = null
                _busy.value = false
            }
        }
    }

    /** How the live wallpaper moves (ADR-0014, Pro). */
    private val _live = MutableStateFlow(LiveWallpaper.settings(application))
    val live: StateFlow<LiveSettings> = _live.asStateFlow()

    fun setLiveSettings(settings: LiveSettings) {
        if (!pro.state.value.unlocked) return
        _live.value = settings
        LiveWallpaper.setSettings(getApplication(), settings)
    }

    /**
     * Gives the live wallpaper the current mandala. When it is already on the phone it fades over by
     * itself; otherwise its image is rendered first, with the progress showing, and then `openPicker`
     * opens Android's preview, which shows it at once.
     */
    fun useForLiveWallpaper(openPicker: () -> Unit) {
        if (!pro.state.value.unlocked) return openPicker()
        if (_busy.value) return
        val app = getApplication<Application>()
        val params = _params.value
        LiveWallpaper.setMandala(app, params)
        if (LiveWallpaper.isActive(app)) {
            message(string(R.string.message_live_updating))
            return
        }
        _busy.value = true
        _export.value = ExportProgress(string(R.string.export_preparing_live))
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { LiveWallpaper.prepare(app, params) }
            } catch (e: OutOfMemoryError) {
                // The wallpaper then renders it itself, with less memory taken by the app.
            } finally {
                _export.value = null
                _busy.value = false
            }
            openPicker()
        }
    }

    fun setPairOpen(open: Boolean) {
        _pairOpen.value = open
    }

    private suspend fun renderPairPreview(params: MandalaParameters, choice: SaveChoice, screenAspect: Double) {
        val aspect = Editing.aspect(Editing.SaveFormat.SCREEN, screenAspect)
        val (w, h) = Editing.size(Editing.SaveFormat.SCREEN, screenAspect, PAIR_PREVIEW_WIDTH)
        val key = params to w * 10_000 + h
        _pairPreview.update { it.copy(rendering = true) }
        try {
            val drawn = lastPair?.takeIf { it.first == key }?.second ?: withContext(Dispatchers.Default) {
                val zoom = MandalaRenderer.fillZoom(params, aspect, threads)
                val job = coroutineContext.job
                MandalaRenderer.render(
                    params,
                    RenderOptions(width = w, height = h, framing = Framing(zoom = zoom), threads = threads, textRenderer = textRenderer, imageLoader = imageLoader, isCancelled = { !job.isActive }),
                ).image
            }.also { lastPair = key to it }
            val (lock, home) = withContext(Dispatchers.Default) {
                val lock = choice.finish.apply(drawn, threads)
                lock to Finish(brightness = choice.homeBrightness).apply(lock, threads)
            }
            _pairPreview.value = PairPreview(lock.toBitmap().asImageBitmap(), home.toBitmap().asImageBitmap(), rendering = false)
        } catch (e: RenderCancelledException) {
            // A newer mandala is already being drawn.
        } catch (e: OutOfMemoryError) {
            _pairPreview.update { it.copy(rendering = false) }
        }
    }

    /** The last pair preview as drawn, before its finishes. */
    private var lastPair: Pair<Pair<MandalaParameters, Int>, Image8>? = null

    /** Shows `label` while Android works on the drawn image, for which there is no share to show. */
    private fun exportStep(label: String) {
        _export.value = ExportProgress(label)
    }

    /** Renders the image at full size and hands it to `then`; `label` says what for while it is drawn. */
    private fun renderFullSize(
        label: String, width: Int, height: Int, large: Boolean, framingOf: suspend () -> Framing, textFollows: Boolean,
        reuse: ((ExportKey, Framing) -> Boolean)? = null,
        finish: Finish = Finish(),
        then: suspend (Image8, String) -> Unit,
    ) {
        if (_busy.value) return
        val snapshot = _params.value
        _busy.value = true
        _export.value = ExportProgress(label)
        viewModelScope.launch {
            try {
                val framing = framingOf()
                val key = ExportKey(snapshot, framing, textFollows, width, height, !large, finish)
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
                        finish = finish,
                        threads = threads,
                        textRenderer = textRenderer,
                        imageLoader = imageLoader,
                    )
                    MandalaRenderer.render(snapshot, options).image
                }
                if (_saveOpen.value) lastExport = key to image
                then(image, Editing.fileName(snapshot, width, height))
            } catch (e: OutOfMemoryError) {
                message(string(R.string.message_image_memory))
            } catch (e: Exception) {
                message(string(R.string.message_image_failed, e.message ?: e.javaClass.simpleName))
            } finally {
                _export.value = null
                _busy.value = false
            }
        }
    }

    private fun message(text: String, undo: (() -> Unit)? = null) {
        _events.trySend(EditorEvent.Message(text, undo))
    }

    /** A user-visible text in the device's language (ADR-0019). */
    private fun string(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private fun plural(@PluralsRes id: Int, count: Int): String = getApplication<Application>().resources.getQuantityString(id, count, count)

    private suspend fun renderFramePreview(request: FrameRequest) {
        if (request.choice.moving) return animateFramePreview(request)
        recentFrames[request]?.let {
            _framePreview.value = it
            return
        }
        val (w, h) = Editing.size(request.choice.format, request.aspect, Editing.FRAME_PREVIEW_WIDTH)
        val framing = framingFor(request.choice, request.params, Editing.aspect(request.choice.format, request.aspect), request.pro)
        // The finish is applied to the drawn preview, so its sliders follow the finger without a new render.
        val finish = if (request.pro) request.choice.finish else Finish()
        val unfinished = request.copy(choice = request.choice.copy(finish = Finish()))
        _framePreview.update { it.copy(rendering = true) }
        try {
            val drawn = lastFrame?.takeIf { it.first == unfinished }?.second ?: withContext(Dispatchers.Default) {
                val job = coroutineContext.job
                MandalaRenderer.render(
                    request.params,
                    RenderOptions(
                        width = w, height = h, framing = framing, textFollowsMandala = request.pro && request.choice.textFollowsMandala,
                        threads = threads, textRenderer = textRenderer, imageLoader = imageLoader, isCancelled = { !job.isActive },
                    ),
                ).image
            }.also { lastFrame = unfinished to it }
            val image = withContext(Dispatchers.Default) { finish.apply(drawn, threads) }
            _framePreview.value = FramePreviewState(image.toBitmap().asImageBitmap(), rendering = false, framing = framing, width = w, height = h)
            recentFrames[request] = _framePreview.value
        } catch (e: RenderCancelledException) {
            // A newer framing is already being rendered.
        } catch (e: OutOfMemoryError) {
            _framePreview.update { it.copy(rendering = false) }
        }
    }

    /**
     * The save sheet's frame while "Moving" is on: the layers turning as in the video, drawn again and
     * again, small and with the fast preview's glow, until something changes or the sheet closes. It
     * pauses while a video is made, which needs the processors more.
     */
    private suspend fun animateFramePreview(request: FrameRequest) {
        val (w, h) = Editing.size(request.choice.format, request.aspect, Editing.FRAME_PREVIEW_WIDTH)
        val framing = framingFor(request.choice, request.params, Editing.aspect(request.choice.format, request.aspect), request.pro)
        val finish = if (request.pro) request.choice.finish else Finish()
        val cache = LayerCache()
        val seconds = request.choice.videoSeconds
        val start = SystemClock.elapsedRealtime()
        try {
            withContext(Dispatchers.Default) {
                val job = coroutineContext.job
                val options = LayerAnimation.options(
                    RenderOptions(
                        width = w, height = h, framing = framing, textFollowsMandala = request.pro && request.choice.textFollowsMandala,
                        supersample = false, finish = finish, threads = threads, glowReferenceWidth = Editing.FAST_GLOW_REFERENCE,
                        textRenderer = textRenderer, imageLoader = imageLoader, isCancelled = { !job.isActive },
                    ),
                    cache,
                )
                while (isActive) {
                    if (_makingVideo.value && _framePreview.value.image != null) {
                        delay(MOVING_PAUSE_MS)
                        continue
                    }
                    val t = ((SystemClock.elapsedRealtime() - start) / 1000.0 / seconds).mod(1.0)
                    val image = MandalaRenderer.render(LayerAnimation.at(request.params, t), options).image
                    _framePreview.value = FramePreviewState(image.toBitmap().asImageBitmap(), rendering = false, framing = framing, width = w, height = h)
                    yield()
                }
            }
        } catch (e: RenderCancelledException) {
            // Something changed; the next request starts again.
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

    private fun previewOptions(fast: Boolean, isCancelled: () -> Boolean) = RenderOptions(
        outputSize = Editing.PREVIEW_SIZE, threads = threads, textRenderer = textRenderer, imageLoader = imageLoader,
        isCancelled = isCancelled, layerCache = previewCache,
        glowReferenceWidth = if (fast) Editing.FAST_GLOW_REFERENCE else RenderOptions().glowReferenceWidth,
    )

    private suspend fun renderPreview(params: MandalaParameters, fast: Boolean, redrawn: Int) {
        _preview.update { it.copy(rendering = true, error = null) }
        try {
            val result = previewLock.withLock {
                withContext(Dispatchers.Default) {
                    val job = coroutineContext.job
                    MandalaRenderer.render(params, previewOptions(fast) { !job.isActive })
                }
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
            _preview.update { it.copy(rendering = false, error = string(R.string.message_preview_memory)) }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 150L
        const val SAVE_DELAY_MS = 400L
        const val HISTORY_DELAY_MS = 700L
        const val FRAME_DEBOUNCE_MS = 60L
        /** How often the moving frame looks whether the video is done. */
        const val MOVING_PAUSE_MS = 300L
        /** Frames made before the video's progress says how many minutes are left. */
        const val TIME_LEFT_AFTER_FRAMES = 5
        const val UNDER_A_MINUTE_MS = 50_000L
        const val RECENT_PREVIEWS = 30
        const val RECENT_FRAMES = 6
        const val WALLPAPER_MESSAGE_DELAY_MS = 1500L
        const val FILL_ZOOMS_KEPT = 8
        /** Shorter side of each screen in the pair sheet. */
        const val PAIR_PREVIEW_WIDTH = 240

        /** Until the screen reports its own: a common 20:9 phone. */
        const val DEFAULT_SCREEN_ASPECT = 20.0 / 9.0
        const val STARTER_FAVORITES = "starter-favorites.json"

        fun randomSeed(): ULong = Random.nextLong().toULong()
    }
}
