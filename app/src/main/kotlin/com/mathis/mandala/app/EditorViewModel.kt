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
import com.mathis.mandala.app.Randomize.randomLayers
import com.mathis.mandala.app.Editing.addStroke
import com.mathis.mandala.app.Editing.clearStrokes
import com.mathis.mandala.app.Editing.removeLastStroke
import com.mathis.mandala.app.Editing.strokeCount
import com.mathis.mandala.app.Editing.updateDrawing
import com.mathis.mandala.app.Editing.updateGraffiti
import com.mathis.mandala.app.Editing.duplicateLayer
import com.mathis.mandala.app.Editing.moveLayer
import com.mathis.mandala.app.Editing.newVariation
import com.mathis.mandala.app.Editing.removeLayer
import com.mathis.mandala.app.Editing.newPattern
import com.mathis.mandala.app.Editing.updateBackground
import com.mathis.mandala.app.Editing.updateEffects
import com.mathis.mandala.app.Editing.updateLayer
import com.mathis.mandala.app.Editing.updateText
import com.mathis.mandala.app.Editing.withQuote
import com.mathis.mandala.core.BaseLayerSettings
import com.mathis.mandala.core.BaseLayerType
import com.mathis.mandala.core.DrawingLayerSettings
import com.mathis.mandala.core.EffectsLayerSettings
import com.mathis.mandala.core.GraffitiLayerSettings
import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.Image8
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
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

/** One-off things for the screen to do. */
data class HistoryState(val canGoBack: Boolean, val canGoForward: Boolean)

sealed interface EditorEvent {
    data class Message(val text: String, val canUndo: Boolean = false) : EditorEvent
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

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    /** Settings before the last replace (favourite or file), for "Undo". */
    private var beforeReplace: MandalaParameters? = null

    private val historyStore = HistoryStore(File(application.filesDir, "history.json"))
    private val history = History<MandalaParameters>().apply { restore(historyStore.load()) }

    private val _historyState = MutableStateFlow(HistoryState(history.canGoBack, history.canGoForward))
    val historyState: StateFlow<HistoryState> = _historyState.asStateFlow()

    private val threads = Runtime.getRuntime().availableProcessors()
    private val textRenderer = AndroidTextRenderer()
    private val imageLoader = AndroidImageLoader()

    init {
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _params.debounce(DEBOUNCE_MS).collectLatest { renderPreview(it) }
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
    }

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

    fun useQuote(quote: Quote) = updateText { it.withQuote(quote) }

    fun randomQuote() = useQuote(Quotes.all.random())

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

    /** New layers, like "Randomize Layers" on the Mac. */
    fun randomLayers() {
        _params.update { it.randomLayers(Random) }
        _selected.value = 0
    }

    fun randomizeSelected() = _params.update { it.randomLayer(_selected.value, Random) }

    // ── Drawing by hand ──

    private val _drawTool = MutableStateFlow<DrawTool?>(null)

    /** The tool that turns drags on the canvas into strokes; null while the canvas is not for drawing. */
    val drawTool: StateFlow<DrawTool?> = _drawTool.asStateFlow()

    fun setDrawTool(tool: DrawTool?) {
        _drawTool.value = tool
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
        replace(_params.value.clearStrokes(tool), keepSelection = true)
        message("${tool.label} cleared", canUndo = true)
    }

    /** Applies a layer operation and selects the layer it returns. */
    private fun applyLayerOperation(operation: (MandalaParameters) -> Pair<MandalaParameters, Int>) {
        val (next, index) = operation(_params.value)
        _params.value = next
        _selected.value = index
    }

    /** Replaces all settings (favourite or file); the previous ones can be restored with [undo]. */
    private fun replace(params: MandalaParameters, keepSelection: Boolean = false) {
        beforeReplace = _params.value
        _params.value = params
        if (!keepSelection) _selected.value = 0
    }

    fun undo() {
        val previous = beforeReplace ?: return
        beforeReplace = null
        _params.value = previous
        _selected.value = _selected.value.coerceIn(0, previous.layers.lastIndex)
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
        replace(favorite.parameters)
        message("Favourite applied", canUndo = true)
    }

    fun removeFavorite(id: String) = _favorites.update { Favorites.remove(it, id) }

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
                        message("Settings loaded", canUndo = true)
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

    fun exportToGallery() = renderFullSize { image, fileName ->
        withContext(Dispatchers.IO) { GalleryExport.savePng(getApplication(), image, fileName) }
        message("Saved to Pictures/${GalleryExport.FOLDER} as $fileName")
    }

    fun shareImage() = renderFullSize { image, fileName ->
        val uri = withContext(Dispatchers.IO) { ImageShare.prepare(getApplication(), image, fileName) }
        _events.send(EditorEvent.ShareImage(uri))
    }

    private fun renderFullSize(then: suspend (Image8, String) -> Unit) {
        if (_busy.value) return
        val snapshot = _params.value
        _busy.value = true
        viewModelScope.launch {
            try {
                val image = withContext(Dispatchers.Default) {
                    MandalaRenderer.render(snapshot, RenderOptions(outputSize = Editing.EXPORT_SIZE, threads = threads, textRenderer = textRenderer, imageLoader = imageLoader)).image
                }
                then(image, Editing.fileName(snapshot))
            } catch (e: OutOfMemoryError) {
                message("Not enough memory for this image.")
            } catch (e: Exception) {
                message("Could not create the image: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    private fun message(text: String, canUndo: Boolean = false) {
        _events.trySend(EditorEvent.Message(text, canUndo))
    }

    private suspend fun renderPreview(params: MandalaParameters) {
        _preview.update { it.copy(rendering = true, error = null) }
        try {
            val result = withContext(Dispatchers.Default) {
                val job = coroutineContext.job
                MandalaRenderer.render(
                    params,
                    RenderOptions(outputSize = Editing.PREVIEW_SIZE, threads = threads, textRenderer = textRenderer, imageLoader = imageLoader, isCancelled = { !job.isActive }),
                )
            }
            val bitmap = result.image.toBitmap().asImageBitmap()
            _preview.value = PreviewState(image = bitmap, rendering = false, millis = result.millis, warnings = result.warnings)
        } catch (e: RenderCancelledException) {
            // A newer change is already being rendered.
        } catch (e: OutOfMemoryError) {
            _preview.update { it.copy(rendering = false, error = "Not enough memory for the preview.") }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 150L
        const val SAVE_DELAY_MS = 400L
        const val HISTORY_DELAY_MS = 700L
        const val STARTER_FAVORITES = "starter-favorites.json"

        fun randomSeed(): ULong = Random.nextLong().toULong()
    }
}
