package com.mathis.mandala.app

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mathis.mandala.app.Editing.newVariation
import com.mathis.mandala.app.Editing.updateLayer
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.MandalaRenderer
import com.mathis.mandala.core.RenderCancelledException
import com.mathis.mandala.core.RenderOptions
import com.mathis.mandala.core.StyleLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class PreviewState(
    val image: ImageBitmap? = null,
    val rendering: Boolean = true,
    val millis: Long? = null,
    val error: String? = null,
)

sealed interface ExportState {
    data object Idle : ExportState
    data object Saving : ExportState
    data class Saved(val fileName: String) : ExportState
    data class Failed(val message: String) : ExportState
}

/** Holds the settings, renders the preview after each change and exports to the gallery. */
class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val _params = MutableStateFlow(Editing.initial(Random.nextLong().toULong()))
    val params: StateFlow<MandalaParameters> = _params.asStateFlow()

    private val _preview = MutableStateFlow(PreviewState())
    val preview: StateFlow<PreviewState> = _preview.asStateFlow()

    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = _export.asStateFlow()

    private val threads = Runtime.getRuntime().availableProcessors()

    init {
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _params.debounce(DEBOUNCE_MS).collectLatest { renderPreview(it) }
        }
    }

    fun updateLayer(change: (StyleLayer) -> StyleLayer) = _params.update { it.updateLayer(change) }

    fun newVariation() = _params.update { it.newVariation(Random.nextLong().toULong()) }

    fun exportToGallery() {
        if (_export.value == ExportState.Saving) return
        val snapshot = _params.value
        _export.value = ExportState.Saving
        viewModelScope.launch {
            _export.value = try {
                val fileName = Editing.fileName(snapshot)
                val image = withContext(Dispatchers.Default) {
                    MandalaRenderer.render(snapshot, RenderOptions(outputSize = Editing.EXPORT_SIZE, threads = threads)).image
                }
                withContext(Dispatchers.IO) { GalleryExport.savePng(getApplication(), image, fileName) }
                ExportState.Saved(fileName)
            } catch (e: OutOfMemoryError) {
                ExportState.Failed("Not enough memory for this export.")
            } catch (e: Exception) {
                ExportState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun exportMessageShown() {
        if (_export.value !is ExportState.Saving) _export.value = ExportState.Idle
    }

    private suspend fun renderPreview(params: MandalaParameters) {
        _preview.update { it.copy(rendering = true, error = null) }
        try {
            val result = withContext(Dispatchers.Default) {
                val job = coroutineContext.job
                MandalaRenderer.render(
                    params,
                    RenderOptions(outputSize = Editing.PREVIEW_SIZE, threads = threads, isCancelled = { !job.isActive }),
                )
            }
            val bitmap = result.image.toBitmap().asImageBitmap()
            _preview.value = PreviewState(image = bitmap, rendering = false, millis = result.millis)
        } catch (e: RenderCancelledException) {
            // A newer change is already being rendered.
        } catch (e: OutOfMemoryError) {
            _preview.update { it.copy(rendering = false, error = "Not enough memory for the preview.") }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 150L
    }
}
