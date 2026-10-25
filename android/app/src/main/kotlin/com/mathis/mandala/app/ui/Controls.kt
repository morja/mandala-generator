package com.mathis.mandala.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathis.mandala.app.DrawTool
import com.mathis.mandala.app.MoveTarget
import com.mathis.mandala.app.Editing
import com.mathis.mandala.app.Editing.strokeCount
import com.mathis.mandala.app.Editing.switched
import com.mathis.mandala.app.Editing.switchedTo
import com.mathis.mandala.app.EditorViewModel
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import com.mathis.mandala.core.BaseLayerSettings
import com.mathis.mandala.core.BaseLayerType
import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.DrawingLayerSettings
import com.mathis.mandala.core.EffectsLayerSettings
import com.mathis.mandala.core.EmblemFlowerVariant
import com.mathis.mandala.core.EmblemFrame
import com.mathis.mandala.core.EmblemGlowMode
import com.mathis.mandala.core.EmblemLayerSettings
import com.mathis.mandala.core.EmblemMotif
import com.mathis.mandala.core.EmblemRendering
import com.mathis.mandala.core.GraffitiLayerSettings
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.Quote
import com.mathis.mandala.core.Quotes
import com.mathis.mandala.core.ProFeatures
import com.mathis.mandala.core.MandalaStyle
import com.mathis.mandala.core.StyleLayer
import com.mathis.mandala.core.TextLayerSettings
import kotlin.math.roundToInt

private enum class ControlTab(val label: String) { LAYERS("Layers"), SCENE("Scene"), EMBLEM("Emblem"), TEXT("Text"), DRAW("Draw") }

/** What the open tab is about, in two short lines (beside the folded image on phones). */
internal data class ControlHeader(val title: String, val detail: String)

private fun header(tab: ControlTab, params: MandalaParameters, selected: Int): ControlHeader = when (tab) {
    ControlTab.LAYERS -> params.layers.getOrNull(selected)?.let { l ->
        ControlHeader("${selected + 1} · ${l.style.displayName}", "${l.blendMode.displayName} · ${(l.opacity * 100).roundToInt()} %")
    } ?: ControlHeader(tab.label, "No layers")
    ControlTab.SCENE -> ControlHeader(tab.label, if (params.baseLayer.isEnabled) backgroundName(params.baseLayer.type) else "No background")
    ControlTab.EMBLEM -> ControlHeader(tab.label, if (params.emblemLayer.isEnabled) params.emblemLayer.motif.displayName else "Off")
    ControlTab.TEXT -> ControlHeader(tab.label, if (params.textLayer.isEnabled) params.textLayer.customAuthor.ifEmpty { "On" } else "Off")
    ControlTab.DRAW -> ControlHeader(tab.label, "${params.drawingLayer.strokes.size} lines · ${params.graffitiLayer.strokes.size} spray")
}

/**
 * The controls below (or beside) the canvas, in five tabs so only one area is on screen at a time.
 * Each tab keeps its scroll position and open groups while another one is shown.
 */
@Composable
internal fun ControlTabs(
    params: MandalaParameters,
    selected: Int,
    vm: EditorViewModel,
    modifier: Modifier = Modifier,
    proOwned: Boolean = false,
    onShowPro: () -> Unit = {},
    onHeader: (ControlHeader) -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(ControlTab.LAYERS) }
    val header = header(tab, params, selected)
    LaunchedEffect(header) { onHeader(header) }
    val tabs = rememberSaveableStateHolder()
    val moveTarget by vm.moveTarget.collectAsStateWithLifecycle()
    // Drawing on the canvas only while the Draw tab is shown; moving only while its tab is.
    LaunchedEffect(tab) {
        if (tab != ControlTab.DRAW) vm.setDrawTool(null)
        if (tab != ControlTab.LAYERS && moveTarget == MoveTarget.LAYER) vm.setMoveTarget(null)
        if (tab != ControlTab.EMBLEM && moveTarget == MoveTarget.EMBLEM) vm.setMoveTarget(null)
    }
    val position = PositionInput(proOwned, moveTarget, vm::setMoveTarget, onShowPro)
    Column(modifier) {
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            for (option in ControlTab.entries) {
                // Content without the text tab's side padding, so five labels fit on a narrow phone.
                Tab(selected = tab == option, onClick = { tab = option }) {
                    Text(option.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.padding(vertical = Spacing.md))
                }
            }
        }
        tabs.SaveableStateProvider(tab) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = Spacing.xs, bottom = Spacing.lg)) {
                when (tab) {
                    ControlTab.LAYERS -> LayersTab(params, selected.coerceIn(0, params.layers.lastIndex), vm, position)
                    ControlTab.SCENE -> SceneTab(params, vm)
                    ControlTab.EMBLEM -> EmblemTab(params.emblemLayer, vm, position)
                    ControlTab.TEXT -> TextTab(params.textLayer, vm, position)
                    ControlTab.DRAW -> DrawTab(params, vm)
                }
            }
        }
    }
}

// ── Layers ──

@Composable
private fun LayersTab(params: MandalaParameters, selected: Int, vm: EditorViewModel, position: PositionInput) {
    val layers = params.layers
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
    // Without Pro a fourth visible layer can be tried; keeping the mandala then needs Pro (ADR-0016).
    val shown = layers.count { it.isEnabled }
    val proMark = if (!position.proOwned && shown >= ProFeatures.FREE_LAYERS) " · Pro" else ""
    if (!position.proOwned && shown > ProFeatures.FREE_LAYERS) {
        ProNote("More than three layers come with Pro. Try them as you like; keeping the mandala needs Pro.", position.showPro)
    }
    // Actions on the selected (highlighted) layer; its settings follow below.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = vm::addLayer, enabled = layers.size < Editing.MAX_LAYERS) { Text("Add$proMark") }
        TextButton(onClick = vm::duplicateSelected, enabled = layers.size < Editing.MAX_LAYERS) { Text("Duplicate$proMark") }
        TextButton(onClick = vm::removeSelected, enabled = layers.size > 1) { Text("Delete") }
        Box(Modifier.weight(1f))
        IconButton(onClick = vm::randomizeSelected) { Icon(EditorIcons.DieOne, contentDescription = "Randomize layer ${selected + 1}", tint = LocalTokens.current.accent) }
    }
    LayerControls(layers[selected], vm::updateSelected, position, onVary = vm::varySelected, onReseed = vm::reseedSelected)
}

/** A muted line saying what comes with Pro, with a link to the Pro sheet. */
@Composable
private fun ProNote(text: String, showPro: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = LocalTokens.current.textMuted, modifier = Modifier.weight(1f))
        TextButton(onClick = showPro) { Text("About Pro") }
    }
}

/** What the Pro-marked controls need: whether Pro is owned, the move mode, and the Pro sheet. */
private class PositionInput(
    val proOwned: Boolean,
    val moveTarget: MoveTarget?,
    val setMoveTarget: (MoveTarget?) -> Unit,
    val showPro: () -> Unit,
)

/**
 * Where a layer or the emblem sits (ADR-0011): X and Y as fractions of the image from its centre,
 * "Centre", and moving it by hand on the image. Pro; without it a note opens the Pro sheet.
 */
@Composable
private fun PositionGroup(
    x: Double, y: Double, target: MoveTarget, input: PositionInput, hidden: Boolean = false, onChange: (Double, Double) -> Unit,
) {
    val t = LocalTokens.current
    val centred = x == 0.0 && y == 0.0
    Group("Position", summary = when { centred -> "Centred"; input.proOwned -> "Moved"; else -> "Moved · Pro" }) {
        if (!input.proOwned) ProNote("Moving layers comes with Pro. Try it as you like; keeping the mandala needs Pro.", input.showPro)
        val range = (-Editing.MAX_OFFSET).toFloat()..Editing.MAX_OFFSET.toFloat()
        if (hidden) {
            Text("This layer is hidden. Show it to see where it moves.", style = MaterialTheme.typography.bodySmall, color = t.textMuted)
        }
        ValueSlider("Across", x, range, default = 0.0) { v -> onChange(v, y) }
        ValueSlider("Up and down", y, range, default = 0.0) { v -> onChange(x, v) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange(0.0, 0.0) }, enabled = !centred) { Text("Centre") }
            Box(Modifier.weight(1f))
            if (input.moveTarget == target) {
                FilledTonalButton(onClick = { input.setMoveTarget(null) }) { Text("Done") }
            } else {
                Button(onClick = { input.setMoveTarget(target) }, enabled = !hidden) { Text("Move on image") }
            }
        }
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
        Column(Modifier.weight(1f).padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
            Text("$number · ${layer.style.displayName}", style = MaterialTheme.typography.bodyMedium,
                color = if (layer.isEnabled) t.text else t.textMuted, maxLines = 1)
            Text("${layer.blendMode.displayName} · ${(layer.opacity * 100).roundToInt()} %",
                style = MaterialTheme.typography.bodySmall, color = t.textMuted, maxLines = 1)
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (layer.isEnabled) EditorIcons.Visible else EditorIcons.Hidden,
                contentDescription = if (layer.isEnabled) "Hide layer $number" else "Show layer $number",
                tint = if (layer.isEnabled) t.text else t.textMuted,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(EditorIcons.MoveUp, contentDescription = "Move layer $number up") }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(EditorIcons.MoveDown, contentDescription = "Move layer $number down") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerControls(layer: StyleLayer, update: ((StyleLayer) -> StyleLayer) -> Unit, position: PositionInput, onVary: () -> Unit, onReseed: () -> Unit) {
    val t = LocalTokens.current
    Group("Style", summary = layer.style.displayName) {
        RandomActionRow("Variation", "Colour & settings", "New colours and settings, same style", onVary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (style in Editing.stylesByName) {
                FilterChip(
                    selected = layer.style == style,
                    onClick = { update { it.copy(style = style) } },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = t.accentSoft,
                        selectedLabelColor = t.accent,
                    ),
                    border = BorderStroke(
                        if (layer.style == style) Sizes.swatchRing else Sizes.swatchRing / 2,
                        if (layer.style == style) t.accent else t.outline,
                    ),
                    leadingIcon = {
                        Image(painterResource(stylePreviewResource(style)), contentDescription = null,
                            modifier = Modifier.size(Sizes.swatchSmall).clip(RoundedCornerShape(Radius.sm)))
                    },
                    label = { Text(style.displayName) },
                )
            }
        }
    }
    val palette = BuiltInPalettes.all[layer.paletteIndex.coerceIn(0, BuiltInPalettes.all.lastIndex)]
    Group("Palette", summary = palette.name) {
        PaletteSwatches(layer.paletteIndex) { index -> update { it.copy(paletteIndex = index) } }
    }
    Group("Blending", summary = "${layer.blendMode.displayName} · ${(layer.opacity * 100).roundToInt()} %") {
        BlendChips(layer.blendMode) { m -> update { it.copy(blendMode = m) } }
        ValueSlider("Opacity", layer.opacity, default = StyleLayer().opacity) { v -> update { it.copy(opacity = v) } }
    }
    Group("Shape", initiallyOpen = true) {
        RandomActionRow("Seed", "Arrangement", "New arrangement, same style, palette and settings", onReseed)
        SteppedSlider("Symmetry", layer.symmetry, 1..8, default = StyleLayer().symmetry) { v -> update { it.copy(symmetry = v) } }
        ValueSlider("Scale", layer.scale, Editing.Ranges.layerScale.floats(), default = StyleLayer().scale) { v -> update { it.copy(scale = v) } }
        ValueSlider("Complexity", layer.complexity, Editing.Ranges.layerComplexity(layer.style).floats(), default = StyleLayer().complexity) { v -> update { it.copy(complexity = v) } }
        if (layer.style == MandalaStyle.RADIAL_MESH) {
            ValueSlider("Spokes", layer.spokes, default = StyleLayer().spokes) { v -> update { it.copy(spokes = v) } }
        }
        // Only where the style uses them; hidden values stay as they are.
        if (layer.style in Editing.densityStyles) {
            ValueSlider("Density", layer.density, default = StyleLayer().density) { v -> update { it.copy(density = v) } }
        }
        if (layer.style in Editing.rippleStyles) {
            ValueSlider("Ripple", layer.ripple, default = StyleLayer().ripple) { v -> update { it.copy(ripple = v) } }
        }
    }
    PositionGroup(layer.offsetX, layer.offsetY, MoveTarget.LAYER, position, hidden = !layer.isEnabled) { x, y -> update { it.copy(offsetX = x, offsetY = y) } }
    Group("Light and colour") {
        ValueSlider("Glow", layer.glowIntensity, default = StyleLayer().glowIntensity) { v -> update { it.copy(glowIntensity = v) } }
        ValueSlider("Colour drift", layer.colorDrift, default = StyleLayer().colorDrift) { v -> update { it.copy(colorDrift = v) } }
        ValueSlider("Painted", layer.abstractLevel, Editing.Ranges.layerPainted.floats(), default = StyleLayer().abstractLevel) { v -> update { it.copy(abstractLevel = v) } }
        ValueSlider("Saturation", layer.saturation, default = StyleLayer().saturation) { v -> update { it.copy(saturation = v) } }
        ValueSlider("Brightness", layer.brightness, Editing.Ranges.layerBrightness.floats(), default = StyleLayer().brightness) { v -> update { it.copy(brightness = v) } }
    }
}

// ── Scene: background and effects ──

@Composable
private fun SceneTab(params: MandalaParameters, vm: EditorViewModel) {
    val base = params.baseLayer
    Group("Background", summary = if (base.isEnabled) backgroundName(base.type) else "Off") {
        BackgroundControls(base, vm::updateBackground, vm::useBackgroundImage)
    }
    EffectsGroup(params.effectsLayer, vm::updateEffects, vm::newEffectsPattern, vm::resetEffects)
}

/** Background behind all layers: off or one of the macOS types, with the fitting sliders. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundControls(
    base: BaseLayerSettings,
    update: ((BaseLayerSettings) -> BaseLayerSettings) -> Unit,
    useImage: (Uri) -> Unit,
) {
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(useImage) }
    fun choose() = pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChip(selected = !base.isEnabled, onClick = { update { it.copy(isEnabled = false) } }, label = { Text("Off") })
        for (type in Editing.backgroundChoices(base)) {
            FilterChip(
                selected = base.isEnabled && base.type == type,
                onClick = {
                    if (type == BaseLayerType.IMAGE && base.imageURL == null) choose()
                    else update { it.switchedTo(type) }
                },
                label = { Text(backgroundName(type)) },
            )
        }
    }
    if (!base.isEnabled || base.type !in Editing.backgrounds) return
    when (base.type) {
        BaseLayerType.AUTO -> {
            Text("A dark glow and fine fibres in the colours of the bottom layer.", style = MaterialTheme.typography.bodySmall,
                color = LocalTokens.current.textMuted)
            return
        }
        BaseLayerType.IMAGE -> {
            OutlinedButton(onClick = ::choose) { Text("Choose picture") }
            ValueSlider("Strength", base.imageBlend, default = BaseLayerSettings().imageBlend) { v -> update { it.copy(imageBlend = v) } }
            ValueSlider("Opacity", base.opacity, default = BaseLayerSettings().opacity) { v -> update { it.copy(opacity = v) } }
            return
        }
        else -> Unit
    }
    val twoColours = base.type != BaseLayerType.COLOR && base.type != BaseLayerType.GRAIN
    val brightnessRange = Editing.Ranges.backgroundBrightness.floats()
    ColourGroup(if (twoColours) "Colour 1" else "Colour", base.hue, base.saturation, base.brightness,
        BaseLayerSettings().let { Triple(it.hue, it.saturation, it.brightness) }, brightnessRange = brightnessRange) { h, s, b ->
        update { it.copy(hue = h, saturation = s, brightness = b) }
    }
    if (twoColours) {
        ColourGroup("Colour 2", base.hue2, base.saturation2, base.brightness2,
            BaseLayerSettings().let { Triple(it.hue2, it.saturation2, it.brightness2) }, brightnessRange = brightnessRange) { h, s, b ->
            update { it.copy(hue2 = h, saturation2 = s, brightness2 = b) }
        }
    }
    when (base.type) {
        BaseLayerType.GRADIENT -> {
            SwitchRow("Radial", base.isRadial) { r -> update { it.copy(isRadial = r) } }
            if (!base.isRadial) ValueSlider("Angle", base.gradientAngle, default = BaseLayerSettings().gradientAngle) { v -> update { it.copy(gradientAngle = v) } }
        }
        BaseLayerType.SUNBURST -> {
            ValueSlider("Rays", base.patternScale, default = BaseLayerSettings().patternScale) { v -> update { it.copy(patternScale = v) } }
            ValueSlider("Sharpness", base.patternSharpness, default = BaseLayerSettings().patternSharpness) { v -> update { it.copy(patternSharpness = v) } }
        }
        BaseLayerType.GRAIN -> {
            ValueSlider("Grain", base.grainAmount, default = BaseLayerSettings().grainAmount) { v -> update { it.copy(grainAmount = v) } }
            SwitchRow("Coloured grain", base.grainColored) { c -> update { it.copy(grainColored = c) } }
        }
        else -> Unit
    }
    ValueSlider("Opacity", base.opacity, default = BaseLayerSettings().opacity) { v -> update { it.copy(opacity = v) } }
}

private fun backgroundName(type: BaseLayerType) = when (type) {
    BaseLayerType.AUTO -> "Auto"
    BaseLayerType.IMAGE -> "Picture"
    BaseLayerType.COLOR -> "Colour"
    BaseLayerType.GRADIENT -> "Gradient"
    BaseLayerType.SUNBURST -> "Sunburst"
    BaseLayerType.GRAIN -> "Grain"
}

/**
 * Scene effects behind the text, switched on inside the section; the quick, reliable sliders in
 * four groups and the rarer ones under "More" (TUNE-1). "Fade" sets the `wash` field; the old `fade`
 * field does almost the same and gets its own slider ("Old fade") only in settings that use it.
 * The die in the heading moves highlights, dimming, erasure, stars and glitter to new places, as the
 * one on the Mac's card does; it used to sit under "Sparkle", where it read as a glitter-only button.
 */
@Composable
private fun EffectsGroup(
    fx: EffectsLayerSettings,
    update: ((EffectsLayerSettings) -> EffectsLayerSettings) -> Unit,
    newPattern: () -> Unit,
    resetAll: () -> Unit,
) {
    // Once shown, the old fade slider stays while this screen is open, so it does not vanish at 0 mid-drag.
    var showOldFade by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(fx.fade > 0) { if (fx.fade > 0) showOldFade = true }
    val t = LocalTokens.current
    Group("Effects", summary = if (fx.isEnabled) "On" else "Off") {
        // Keep actions inside the section so its closed header is as compact as every other one.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Show effects", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (fx.isEnabled) {
                IconButton(onClick = newPattern) { Icon(EditorIcons.DieOne,
                    contentDescription = "New pattern of highlights, dimming, stars and glitter", tint = t.accent) }
                TextButton(onClick = resetAll) { Text("Reset") }
            }
            Switch(checked = fx.isEnabled, onCheckedChange = { on -> update { it.copy(isEnabled = on) } })
        }
        if (!fx.isEnabled) {
            Text("Switch on to use vignette, bloom, grain, stars and more.", style = MaterialTheme.typography.bodySmall,
                color = LocalTokens.current.textMuted)
            return@Group
        }
        Group("Light", inset = true, initiallyOpen = true) {
            ValueSlider("Brightness", fx.brightness, default = EffectsLayerSettings().brightness) { v -> update { it.copy(brightness = v) } }
            ValueSlider("Contrast", fx.contrast, default = EffectsLayerSettings().contrast) { v -> update { it.copy(contrast = v) } }
            ValueSlider("Vignette", fx.vignette, Editing.Ranges.effectsVignette.floats(), default = EffectsLayerSettings().vignette) { v -> update { it.copy(vignette = v) } }
            ValueSlider("Bloom", fx.bloom, Editing.Ranges.effectsBloom.floats(), default = EffectsLayerSettings().bloom) { v -> update { it.copy(bloom = v) } }
        }
        Group("Tone", inset = true) {
            ValueSlider("Sepia", fx.sepia, default = EffectsLayerSettings().sepia) { v -> update { it.copy(sepia = v) } }
            ValueSlider("Fade", fx.wash, default = EffectsLayerSettings().wash) { v -> update { it.copy(wash = v) } }
        }
        Group("Texture", inset = true) {
            ValueSlider("Clarity", fx.localContrast, default = EffectsLayerSettings().localContrast) { v -> update { it.copy(localContrast = v) } }
            ValueSlider("Grain", fx.grain, Editing.Ranges.effectsGrain.floats(), default = EffectsLayerSettings().grain) { v -> update { it.copy(grain = v) } }
            ValueSlider("Smoother", fx.smoother, default = EffectsLayerSettings().smoother) { v -> update { it.copy(smoother = v) } }
        }
        Group("Sparkle", inset = true) {
            ValueSlider("Stars", fx.stars, default = EffectsLayerSettings().stars) { v -> update { it.copy(stars = v) } }
            ValueSlider("Glitter", fx.glitter, Editing.Ranges.effectsGlitter.floats(), default = EffectsLayerSettings().glitter) { v -> update { it.copy(glitter = v) } }
        }
        Group("More", inset = true) {
            ValueSlider("Highlights", fx.highlights, default = EffectsLayerSettings().highlights) { v -> update { it.copy(highlights = v) } }
            ValueSlider("Dimming", fx.dimming, default = EffectsLayerSettings().dimming) { v -> update { it.copy(dimming = v) } }
            ValueSlider("Erasure", fx.erasure, default = EffectsLayerSettings().erasure) { v -> update { it.copy(erasure = v) } }
            ValueSlider("Relief", fx.relief, default = EffectsLayerSettings().relief) { v -> update { it.copy(relief = v) } }
            if (fx.relief > 0) ValueSlider("Relief angle", fx.reliefAngle, default = EffectsLayerSettings().reliefAngle) { v -> update { it.copy(reliefAngle = v) } }
            ValueSlider("Chromatic", fx.chromatic, default = EffectsLayerSettings().chromatic) { v -> update { it.copy(chromatic = v) } }
            if (showOldFade) ValueSlider("Old fade", fx.fade, default = EffectsLayerSettings().fade) { v -> update { it.copy(fade = v) } }
        }
    }
}

// ── Emblem ──

/** One motif large in the centre, with the controls of the macOS emblem card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmblemTab(em: EmblemLayerSettings, vm: EditorViewModel, position: PositionInput) {
    val update = vm::updateEmblem
    val defaults = EmblemLayerSettings()
    val t = LocalTokens.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        // The label switches too; the dice and reset buttons beside it keep their own taps.
        Text(
            if (position.proOwned) "Show emblem" else "Show emblem · Pro", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).toggleable(value = em.isEnabled, role = Role.Switch) { on -> update { it.switched(on) } },
        )
        if (em.isEnabled) {
            IconButton(onClick = vm::randomizeEmblem) { Icon(EditorIcons.DieOne, contentDescription = "Random emblem", tint = t.accent) }
            TextButton(onClick = vm::resetEmblem) { Text("Reset") }
        }
        Switch(checked = em.isEnabled, onCheckedChange = { on -> update { it.switched(on) } })
    }
    if (!em.isEnabled) {
        Text("One motif drawn large in the centre: Om, Tree of Life, Flower of Life, Lotus and more.",
            style = MaterialTheme.typography.bodySmall, color = t.textMuted)
        return
    }
    if (!position.proOwned) ProNote("The emblem comes with Pro. Try it as you like; keeping the mandala needs Pro.", position.showPro)
    Group("Motif", summary = em.motif.displayName, initiallyOpen = true) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (motif in EmblemMotif.entries) {
                FilterChip(selected = em.motif == motif, onClick = { update { it.copy(motif = motif) } }, label = { Text(motif.displayName) })
            }
        }
        when (em.motif) {
            EmblemMotif.FLOWER_OF_LIFE -> ChipLine("Variant") {
                for (v in EmblemFlowerVariant.entries) {
                    FilterChip(selected = em.flowerVariant == v, onClick = { update { it.copy(flowerVariant = v) } }, label = { Text(v.displayName) })
                }
            }
            EmblemMotif.LOTUS -> ChipLine("Petals") {
                for (n in Editing.lotusPetals) {
                    FilterChip(selected = em.petals == n, onClick = { update { it.copy(petals = n) } }, label = { Text("$n") })
                }
            }
            else -> Unit
        }
        if (em.motif.isOrganic) ValueSlider("Organic", em.organic, default = defaults.organic) { v -> update { it.copy(organic = v) } }
        if (em.motif == EmblemMotif.TREE_OF_LIFE) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Shape ${em.seed}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::newEmblemShape) { Text("New shape") }
            }
            ValueSlider("Branches", em.branching, default = defaults.branching) { v -> update { it.copy(branching = v) } }
            SwitchRow("Roots", em.roots) { r -> update { it.copy(roots = r) } }
        }
    }
    Group("Style and frame", summary = "${em.rendering.displayName} · ${em.frame.displayName}") {
        ChipLine("Style") {
            for (r in EmblemRendering.entries) {
                FilterChip(selected = em.rendering == r, onClick = { update { it.copy(rendering = r) } }, label = { Text(r.displayName) })
            }
        }
        ChipLine("Frame") {
            for (f in EmblemFrame.entries) {
                FilterChip(selected = em.frame == f, onClick = { update { it.copy(frame = f) } }, label = { Text(f.displayName) })
            }
        }
    }
    Group("Shape") {
        ValueSlider("Size", em.size, Editing.Ranges.emblemSize.floats(), default = defaults.size) { v -> update { it.copy(size = v) } }
        ValueSlider("Weight", em.weight, Editing.Ranges.emblemWeight.floats(), default = defaults.weight) { v -> update { it.copy(weight = v) } }
        ValueSlider("Rotate", em.rotation, -180f..180f, default = defaults.rotation, display = { "${it.roundToInt()}°" }) { v ->
            update { it.copy(rotation = v) }
        }
    }
    PositionGroup(em.offsetX, em.offsetY, MoveTarget.EMBLEM, position) { x, y -> update { it.copy(offsetX = x, offsetY = y) } }
    val palette = BuiltInPalettes.all[em.paletteIndex.coerceIn(0, BuiltInPalettes.all.lastIndex)]
    Group("Colour", summary = palette.name) {
        PaletteSwatches(em.paletteIndex) { index -> update { it.copy(paletteIndex = index) } }
        ValueSlider("Position", em.colorPosition, default = defaults.colorPosition) { v -> update { it.copy(colorPosition = v) } }
        ValueSlider("Spread", em.colorSpread, default = defaults.colorSpread) { v -> update { it.copy(colorSpread = v) } }
        ValueSlider("Glow", em.glow, default = defaults.glow) { v -> update { it.copy(glow = v) } }
        ChipLine("Glow") {
            for (m in EmblemGlowMode.entries) {
                FilterChip(selected = em.glowMode == m, onClick = { update { it.copy(glowMode = m) } }, label = { Text(m.displayName) })
            }
        }
    }
    Group("Blending", summary = "${em.blendMode.displayName} · ${(em.opacity * 100).roundToInt()} %") {
        BlendChips(em.blendMode) { m -> update { it.copy(blendMode = m) } }
        ValueSlider("Opacity", em.opacity, default = defaults.opacity) { v -> update { it.copy(opacity = v) } }
    }
}

/** A small caption above a wrapping row of chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipLine(caption: String, chips: @Composable () -> Unit) {
    Text(caption, style = MaterialTheme.typography.bodySmall, color = LocalTokens.current.textMuted, modifier = Modifier.padding(top = Spacing.xs))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { chips() }
}

// ── Text ──

/** Text over the mandala: quotes from the macOS app's collection or own text, with font and light. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextTab(text: TextLayerSettings, vm: EditorViewModel, position: PositionInput) {
    val update = vm::updateText
    var choosing by remember { mutableStateOf(false) }
    val myQuotes by vm.myQuotes.collectAsStateWithLifecycle()
    val switch: (Boolean) -> Unit = { on -> if (on && text.text.isEmpty()) vm.randomQuote() else update { it.copy(isEnabled = on) } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        // The label switches too; the die and reset beside it keep their own taps (as in the emblem tab).
        Text(
            if (position.proOwned) "Show text" else "Show text · Pro", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).toggleable(value = text.isEnabled, role = Role.Switch, onValueChange = switch),
        )
        if (text.isEnabled) {
            IconButton(onClick = vm::randomizeText) { Icon(EditorIcons.DieOne, contentDescription = "Random text look", tint = LocalTokens.current.accent) }
            TextButton(onClick = vm::resetText) { Text("Reset") }
        }
        Switch(checked = text.isEnabled, onCheckedChange = switch)
    }
    if (choosing) {
        QuoteDialog(
            myQuotes = myQuotes,
            onPick = { vm.useQuote(it); choosing = false },
            onRemove = vm::removeMyQuote,
            onDismiss = { choosing = false },
        )
    }
    if (!text.isEnabled) {
        Text("A quote or your own words below the mandala, with the author if you like.",
            style = MaterialTheme.typography.bodySmall, color = LocalTokens.current.textMuted)
        return
    }
    if (!position.proOwned) ProNote("Text and quotes come with Pro. Try them as you like; keeping the mandala needs Pro.", position.showPro)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(onClick = vm::randomQuote) { Text("Random quote") }
        OutlinedButton(onClick = { choosing = true }) { Text("Choose quote") }
    }
    OutlinedTextField(
        value = text.text,
        onValueChange = { v -> update { it.copy(text = v) } },
        label = { Text("Text") },
        maxLines = 4,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
    )
    OutlinedTextField(
        value = text.customAuthor,
        onValueChange = { v -> update { it.copy(customAuthor = v) } },
        label = { Text("Author") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val saved = myQuotes.any { it.text == text.text.trim() && it.author == text.customAuthor.trim() }
    TextButton(onClick = vm::saveTextAsMyQuote, enabled = text.text.isNotBlank() && !saved) {
        Text(if (saved) "In your quotes" else "Save to my quotes")
    }
    SwitchRow("Show author", text.showAuthor) { a -> update { it.copy(showAuthor = a) } }
    Group("Font", summary = Quotes.fonts.firstOrNull { it.first == text.fontName }?.second ?: text.fontName) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for ((name, display) in Quotes.fonts) {
                FilterChip(selected = text.fontName == name, onClick = { update { it.copy(fontName = name) } }, label = { Text(display) })
            }
        }
    }
    Group("Size and place") {
        ValueSlider("Size", text.fontSize, Editing.Ranges.textSize.floats(), default = TextLayerSettings().fontSize) { v -> update { it.copy(fontSize = v) } }
        ValueSlider("Width", text.fontWidth, Editing.Ranges.textWidth.floats(), default = TextLayerSettings().fontWidth) { v -> update { it.copy(fontWidth = v) } }
        ValueSlider("Tracking", text.tracking, Editing.Ranges.textTracking.floats(), default = TextLayerSettings().tracking) { v -> update { it.copy(tracking = v) } }
        ValueSlider("Height", text.offsetY, default = TextLayerSettings().offsetY) { v -> update { it.copy(offsetY = v) } }
    }
    Group("Light") {
        ValueSlider("Glow", text.glow, Editing.Ranges.textGlow.floats(), default = TextLayerSettings().glow) { v -> update { it.copy(glow = v) } }
        ValueSlider("Cloud", text.cloudOpacity, default = TextLayerSettings().cloudOpacity) { v -> update { it.copy(cloudOpacity = v) } }
        ValueSlider("Cloud size", text.cloudScale, Editing.Ranges.textCloudSize.floats(), default = TextLayerSettings().cloudScale) { v -> update { it.copy(cloudScale = v) } }
        // The colours only where the cloud or the shadow shows; the default of both is black.
        if (text.cloudOpacity > 0) {
            ColourGroup("Cloud colour", text.cloudHue, text.cloudSaturation, text.cloudBrightness,
                defaults = Triple(TextLayerSettings().cloudHue, TextLayerSettings().cloudSaturation, TextLayerSettings().cloudBrightness), inset = true,
            ) { h, sa, b -> update { it.copy(cloudHue = h, cloudSaturation = sa, cloudBrightness = b) } }
        }
        ValueSlider("Shadow", text.shadowOpacity, default = TextLayerSettings().shadowOpacity) { v -> update { it.copy(shadowOpacity = v) } }
        if (text.shadowOpacity > 0) {
            ColourGroup("Shadow colour", text.shadowHue, text.shadowSaturation, text.shadowBrightness,
                defaults = Triple(TextLayerSettings().shadowHue, TextLayerSettings().shadowSaturation, TextLayerSettings().shadowBrightness), inset = true,
            ) { h, sa, b -> update { it.copy(shadowHue = h, shadowSaturation = sa, shadowBrightness = b) } }
        }
    }
    Group("Colour", trailing = { ColourDot(hsbColour(text.hue, text.saturation, text.brightness.coerceAtMost(1.0))) }) {
        ValueSlider("Hue", text.hue, default = TextLayerSettings().hue) { v -> update { it.copy(hue = v) } }
        ValueSlider("Saturation", text.saturation, default = TextLayerSettings().saturation) { v -> update { it.copy(saturation = v) } }
        ValueSlider("Brightness", text.brightness, Editing.Ranges.textBrightness.floats(), default = TextLayerSettings().brightness) { v -> update { it.copy(brightness = v) } }
    }
}

/** Your own quotes first (with a remove button), then the built-in public-domain collection. */
@Composable
private fun QuoteDialog(myQuotes: List<Quote>, onPick: (Quote) -> Unit, onRemove: (Quote) -> Unit, onDismiss: () -> Unit) {
    val t = LocalTokens.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Quotes") },
        text = {
            LazyColumn {
                if (myQuotes.isNotEmpty()) {
                    item { Text("Your quotes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = Spacing.xs)) }
                    items(myQuotes) { q -> QuoteRow(q, onPick) { IconButton(onClick = { onRemove(q) }) { Icon(EditorIcons.Close, "Remove quote") } } }
                    item { Text("Collection", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs)) }
                }
                items(Quotes.all) { q -> QuoteRow(q, onPick, null) }
                item {
                    Text("All quotes in the collection are in the public domain.", style = MaterialTheme.typography.bodySmall,
                        color = t.textMuted, modifier = Modifier.padding(top = Spacing.md))
                }
            }
        },
    )
}

@Composable
private fun QuoteRow(q: Quote, onPick: (Quote) -> Unit, trailing: (@Composable () -> Unit)?) {
    val t = LocalTokens.current
    Row(Modifier.fillMaxWidth().clickable { onPick(q) }.padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(q.text, style = MaterialTheme.typography.bodyMedium)
            if (q.author.isNotEmpty()) Text(q.author, style = MaterialTheme.typography.bodySmall, color = t.textMuted)
        }
        trailing?.invoke()
    }
}

// ── Draw ──

/**
 * Drawing by hand, as on the Mac: glowing lines (drawing layer) or soft spray paint (graffiti layer),
 * each repeated around the centre. "Draw on image" turns drags on the canvas into strokes.
 */
@Composable
private fun DrawTab(params: MandalaParameters, vm: EditorViewModel) {
    val active by vm.drawTool.collectAsStateWithLifecycle()
    var tool by rememberSaveable { mutableStateOf(DrawTool.LINES) }
    val count = params.strokeCount(tool)
    val lines = params.drawingLayer
    val spray = params.graffitiLayer

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(bottom = Spacing.sm)) {
        if (active == null) {
            Button(onClick = { vm.setDrawTool(tool) }) { Text("Draw on image") }
        } else {
            FilledTonalButton(onClick = { vm.setDrawTool(null) }) { Text("Done") }
        }
        OutlinedButton(onClick = { vm.removeLastStroke(tool) }, enabled = count > 0) { Text("Undo") }
        OutlinedButton(onClick = { vm.clearStrokes(tool) }, enabled = count > 0) { Text("Clear") }
    }
    if (count > 0) {
        val shown = if (tool == DrawTool.LINES) lines.isEnabled else spray.isEnabled
        SwitchRow("Show ${tool.label.lowercase()}", shown) { on ->
            if (tool == DrawTool.LINES) vm.updateDrawing { it.copy(isEnabled = on) } else vm.updateGraffiti { it.copy(isEnabled = on) }
        }
    }
    when (tool) {
        DrawTool.LINES -> {
            SteppedSlider("Symmetry", lines.symmetry.coerceIn(1, 16), 1..16, default = DrawingLayerSettings().symmetry) { v -> vm.updateDrawing { it.copy(symmetry = v) } }
            ColourGroup("Line colour", lines.currentHue, lines.currentSaturation, lines.currentBrightness,
                DrawingLayerSettings().let { Triple(it.currentHue, it.currentSaturation, it.currentBrightness) }, initiallyOpen = true) { h, s, b ->
                vm.updateDrawing { it.copy(currentHue = h, currentSaturation = s, currentBrightness = b) }
            }
            Group("Line and light") {
                ValueSlider("Weight", lines.strokeWeight, default = DrawingLayerSettings().strokeWeight) { v -> vm.updateDrawing { it.copy(strokeWeight = v) } }
                ValueSlider("Glow", lines.glowIntensity, default = DrawingLayerSettings().glowIntensity) { v -> vm.updateDrawing { it.copy(glowIntensity = v) } }
                ValueSlider("Opacity", lines.opacity, default = DrawingLayerSettings().opacity) { v -> vm.updateDrawing { it.copy(opacity = v) } }
                BlendChips(lines.blendMode) { m -> vm.updateDrawing { it.copy(blendMode = m) } }
            }
        }
        DrawTool.SPRAY -> {
            SteppedSlider("Symmetry", spray.symmetry.coerceIn(1, 16), 1..16, default = GraffitiLayerSettings().symmetry) { v -> vm.updateGraffiti { it.copy(symmetry = v) } }
            ValueSlider("Brush size", spray.currentBrushSize, 0.01f..0.2f, default = GraffitiLayerSettings().currentBrushSize) { v -> vm.updateGraffiti { it.copy(currentBrushSize = v) } }
            ValueSlider("Paint", spray.currentOpacity, default = GraffitiLayerSettings().currentOpacity) { v -> vm.updateGraffiti { it.copy(currentOpacity = v) } }
            ColourGroup("Spray colour", spray.currentHue, spray.currentSaturation, spray.currentBrightness,
                GraffitiLayerSettings().let { Triple(it.currentHue, it.currentSaturation, it.currentBrightness) }, initiallyOpen = true) { h, s, b ->
                vm.updateGraffiti { it.copy(currentHue = h, currentSaturation = s, currentBrightness = b) }
            }
            Group("Softness and blending") {
                ValueSlider("Softness", spray.softness, default = GraffitiLayerSettings().softness) { v -> vm.updateGraffiti { it.copy(softness = v) } }
                ValueSlider("Opacity", spray.opacity, default = GraffitiLayerSettings().opacity) { v -> vm.updateGraffiti { it.copy(opacity = v) } }
                BlendChips(spray.blendMode) { m -> vm.updateGraffiti { it.copy(blendMode = m) } }
            }
        }
    }
}
