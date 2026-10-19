package com.mathis.mandala.app.ui

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.mathis.mandala.app.Editing.layer
import com.mathis.mandala.app.ExportState
import com.mathis.mandala.app.GalleryExport
import com.mathis.mandala.app.PreviewState
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.ColorPalette
import com.mathis.mandala.core.StyleLayer
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel) {
    val params by vm.params.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val export by vm.export.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(export) {
        val message = when (val e = export) {
            is ExportState.Saved -> "Saved to Pictures/${GalleryExport.FOLDER} as ${e.fileName}"
            is ExportState.Failed -> "Could not save: ${e.message}"
            else -> null
        }
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.exportMessageShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mandala") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val layer = params.layer
            val actions: @Composable () -> Unit = {
                Actions(saving = export == ExportState.Saving, onVariation = vm::newVariation, onSave = vm::exportToGallery)
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
                        Controls(layer, vm::updateLayer)
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
                        Controls(layer, vm::updateLayer)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(layer: StyleLayer, update: ((StyleLayer) -> StyleLayer) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle("Style")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (style in Editing.styles) {
                FilterChip(
                    selected = layer.style == style,
                    onClick = { update { it.copy(style = style) } },
                    label = { Text(style.displayName) },
                )
            }
        }

        SectionTitle("Palette")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            BuiltInPalettes.all.forEachIndexed { index, palette ->
                Swatch(palette, selected = layer.paletteIndex == index) { update { it.copy(paletteIndex = index) } }
            }
        }

        SectionTitle("Shape")
        SteppedSlider("Symmetry", layer.symmetry, 1..8) { v -> update { it.copy(symmetry = v) } }
        ValueSlider("Scale", layer.scale, 0.1f..1.2f) { v -> update { it.copy(scale = v) } }
        ValueSlider("Complexity", layer.complexity) { v -> update { it.copy(complexity = v) } }
        ValueSlider("Density", layer.density) { v -> update { it.copy(density = v) } }
        ValueSlider("Ripple", layer.ripple) { v -> update { it.copy(ripple = v) } }

        SectionTitle("Light and colour")
        ValueSlider("Glow", layer.glowIntensity) { v -> update { it.copy(glowIntensity = v) } }
        ValueSlider("Colour drift", layer.colorDrift) { v -> update { it.copy(colorDrift = v) } }
        ValueSlider("Painted", layer.abstractLevel) { v -> update { it.copy(abstractLevel = v) } }
        ValueSlider("Saturation", layer.saturation) { v -> update { it.copy(saturation = v) } }
        ValueSlider("Brightness", layer.brightness) { v -> update { it.copy(brightness = v) } }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs))
}

@Composable
private fun Swatch(palette: ColorPalette, selected: Boolean, onClick: () -> Unit) {
    val t = LocalTokens.current
    val colors = palette.stops.map { Color(it.color.r.toFloat(), it.color.g.toFloat(), it.color.b.toFloat(), 1f, ColorSpaces.DisplayP3) }
    Box(
        Modifier
            .size(Sizes.swatch)
            .border(Sizes.swatchRing, if (selected) t.accent else Color.Transparent, CircleShape)
            .padding(Sizes.swatchRing * 2)
            .clip(CircleShape)
            .background(Brush.sweepGradient(colors))
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

private fun seconds(millis: Long): String = String.format(Locale.US, "%.1f", millis / 1000.0)
