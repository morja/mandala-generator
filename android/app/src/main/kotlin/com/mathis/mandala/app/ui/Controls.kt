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
import androidx.compose.material3.OutlinedIconButton
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
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mathis.mandala.app.R
import com.mathis.mandala.core.LayerBlendMode
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
import com.mathis.mandala.app.Editing.withEmblem
import com.mathis.mandala.app.Editing.withText
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

private enum class ControlTab(@StringRes val label: Int) {
    LAYERS(R.string.controls_tab_layers), SCENE(R.string.controls_tab_scene), EMBLEM(R.string.controls_tab_emblem),
    TEXT(R.string.controls_tab_text), DRAW(R.string.controls_tab_draw),
}

/** What the open tab is about, in two short lines (beside the folded image on phones). */
internal data class ControlHeader(val title: String, val detail: String)

@Composable
private fun header(tab: ControlTab, params: MandalaParameters, selected: Int): ControlHeader {
    val title = stringResource(tab.label)
    return when (tab) {
        ControlTab.LAYERS -> params.layers.getOrNull(selected)?.let { l ->
            ControlHeader("${selected + 1} · ${stringResource(l.style.labelRes)}", blendSummary(l.blendMode, l.opacity))
        } ?: ControlHeader(title, stringResource(R.string.controls_header_no_layers))
        ControlTab.SCENE -> ControlHeader(title,
            if (params.baseLayer.isEnabled) backgroundName(params.baseLayer.type) else stringResource(R.string.controls_header_no_background))
        ControlTab.EMBLEM -> ControlHeader(title,
            if (params.emblemLayer.isEnabled) stringResource(params.emblemLayer.motif.labelRes) else stringResource(R.string.controls_off))
        ControlTab.TEXT -> ControlHeader(title,
            if (params.textLayer.isEnabled) params.textLayer.customAuthor.ifEmpty { stringResource(R.string.controls_on) } else stringResource(R.string.controls_off))
        ControlTab.DRAW -> {
            val lines = params.drawingLayer.strokes.size
            val spray = params.graffitiLayer.strokes.size
            ControlHeader(title, stringResource(R.string.controls_header_strokes,
                pluralStringResource(R.plurals.controls_header_lines, lines, lines), pluralStringResource(R.plurals.controls_header_spray, spray, spray)))
        }
    }
}

/** "Screen · 80 %": blend mode and opacity, as in the layer rows and the blending groups. */
@Composable
private fun blendSummary(mode: LayerBlendMode, opacity: Double): String =
    stringResource(R.string.controls_blend_summary, stringResource(mode.labelRes), (opacity * 100).roundToInt())

/** A label with the Pro mark, for controls that need Pro to keep. */
@Composable
private fun withPro(label: String): String = stringResource(R.string.controls_with_pro, label)

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
                    Text(stringResource(option.label), style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.padding(vertical = Spacing.md))
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
    val proMark = !position.proOwned && shown >= ProFeatures.FREE_LAYERS
    if (!position.proOwned && shown > ProFeatures.FREE_LAYERS) {
        ProNote(stringResource(R.string.controls_pro_note_layers), position.showPro)
    }
    // Actions on the selected (highlighted) layer; its settings follow below.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = vm::addLayer, enabled = layers.size < Editing.MAX_LAYERS) { Text(stringResource(R.string.controls_layers_add).let { if (proMark) withPro(it) else it }) }
        TextButton(onClick = vm::duplicateSelected, enabled = layers.size < Editing.MAX_LAYERS) { Text(stringResource(R.string.controls_layers_duplicate).let { if (proMark) withPro(it) else it }) }
        TextButton(onClick = vm::removeSelected, enabled = layers.size > 1) { Text(stringResource(R.string.controls_delete)) }
        Box(Modifier.weight(1f))
        IconButton(onClick = vm::randomizeSelected) { Icon(EditorIcons.DieOne, contentDescription = stringResource(R.string.controls_layers_randomize, selected + 1), tint = LocalTokens.current.accent) }
    }
    LayerControls(layers[selected], vm::updateSelected, position, onVary = vm::varySelected, onReseed = vm::reseedSelected)
}

/** A muted line saying what comes with Pro, with a link to the Pro sheet. */
@Composable
private fun ProNote(text: String, showPro: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = LocalTokens.current.textMuted, modifier = Modifier.weight(1f))
        TextButton(onClick = showPro) { Text(stringResource(R.string.controls_about_pro)) }
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
    Group(stringResource(R.string.controls_position), summary = when {
        centred -> stringResource(R.string.controls_position_centred)
        input.proOwned -> stringResource(R.string.controls_position_moved)
        else -> withPro(stringResource(R.string.controls_position_moved))
    }) {
        if (!input.proOwned) ProNote(stringResource(R.string.controls_pro_note_position), input.showPro)
        val range = (-Editing.MAX_OFFSET).toFloat()..Editing.MAX_OFFSET.toFloat()
        if (hidden) {
            Text(stringResource(R.string.controls_position_hidden_note), style = MaterialTheme.typography.bodySmall, color = t.textMuted)
        }
        ValueSlider(stringResource(R.string.controls_across), x, range, default = 0.0) { v -> onChange(v, y) }
        ValueSlider(stringResource(R.string.controls_up_and_down), y, range, default = 0.0) { v -> onChange(x, v) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange(0.0, 0.0) }, enabled = !centred) { Text(stringResource(R.string.controls_centre)) }
            Box(Modifier.weight(1f))
            if (input.moveTarget == target) {
                FilledTonalButton(onClick = { input.setMoveTarget(null) }) { Text(stringResource(R.string.controls_done)) }
            } else {
                Button(onClick = { input.setMoveTarget(target) }, enabled = !hidden) { Text(stringResource(R.string.controls_move_on_image)) }
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
            Text("$number · ${stringResource(layer.style.labelRes)}", style = MaterialTheme.typography.bodyMedium,
                color = if (layer.isEnabled) t.text else t.textMuted, maxLines = 1)
            Text(blendSummary(layer.blendMode, layer.opacity),
                style = MaterialTheme.typography.bodySmall, color = t.textMuted, maxLines = 1)
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (layer.isEnabled) EditorIcons.Visible else EditorIcons.Hidden,
                contentDescription = stringResource(if (layer.isEnabled) R.string.controls_layer_hide else R.string.controls_layer_show, number),
                tint = if (layer.isEnabled) t.text else t.textMuted,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(EditorIcons.MoveUp, contentDescription = stringResource(R.string.controls_layer_move_up, number)) }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(EditorIcons.MoveDown, contentDescription = stringResource(R.string.controls_layer_move_down, number)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerControls(layer: StyleLayer, update: ((StyleLayer) -> StyleLayer) -> Unit, position: PositionInput, onVary: () -> Unit, onReseed: () -> Unit) {
    val t = LocalTokens.current
    Group(stringResource(R.string.controls_style), summary = stringResource(layer.style.labelRes)) {
        RandomActionRow(stringResource(R.string.menu_die_variation), stringResource(R.string.controls_variation_detail), stringResource(R.string.controls_variation_description), onVary)
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
                    label = { Text(stringResource(style.labelRes)) },
                )
            }
        }
    }
    val palette = BuiltInPalettes.all[layer.paletteIndex.coerceIn(0, BuiltInPalettes.all.lastIndex)]
    Group(stringResource(R.string.controls_palette), summary = palette.name) {
        PaletteSwatches(layer.paletteIndex) { index -> update { it.copy(paletteIndex = index) } }
    }
    Group(stringResource(R.string.controls_blending), summary = blendSummary(layer.blendMode, layer.opacity)) {
        BlendChips(layer.blendMode) { m -> update { it.copy(blendMode = m) } }
        ValueSlider(stringResource(R.string.controls_opacity), layer.opacity, default = StyleLayer().opacity) { v -> update { it.copy(opacity = v) } }
    }
    Group(stringResource(R.string.controls_shape), initiallyOpen = true) {
        RandomActionRow(stringResource(R.string.controls_seed_detail), null, stringResource(R.string.controls_seed_description), onReseed)
        SteppedSlider(stringResource(R.string.controls_symmetry), layer.symmetry, 1..8, default = StyleLayer().symmetry) { v -> update { it.copy(symmetry = v) } }
        ValueSlider(stringResource(R.string.controls_scale), layer.scale, Editing.Ranges.layerScale.floats(), default = StyleLayer().scale) { v -> update { it.copy(scale = v) } }
        ValueSlider(stringResource(R.string.controls_complexity), layer.complexity, Editing.Ranges.layerComplexity(layer.style).floats(), default = StyleLayer().complexity) { v -> update { it.copy(complexity = v) } }
        if (layer.style == MandalaStyle.RADIAL_MESH) {
            ValueSlider(stringResource(R.string.controls_spokes), layer.spokes, default = StyleLayer().spokes) { v -> update { it.copy(spokes = v) } }
        }
        // Only where the style uses them; hidden values stay as they are.
        if (layer.style in Editing.densityStyles) {
            ValueSlider(stringResource(R.string.controls_density), layer.density, default = StyleLayer().density) { v -> update { it.copy(density = v) } }
        }
        if (layer.style in Editing.rippleStyles) {
            ValueSlider(stringResource(R.string.controls_ripple), layer.ripple, default = StyleLayer().ripple) { v -> update { it.copy(ripple = v) } }
        }
    }
    PositionGroup(layer.offsetX, layer.offsetY, MoveTarget.LAYER, position, hidden = !layer.isEnabled) { x, y -> update { it.copy(offsetX = x, offsetY = y) } }
    Group(stringResource(R.string.controls_light_and_colour)) {
        ValueSlider(stringResource(R.string.controls_glow), layer.glowIntensity, default = StyleLayer().glowIntensity) { v -> update { it.copy(glowIntensity = v) } }
        ValueSlider(stringResource(R.string.controls_colour_drift), layer.colorDrift, default = StyleLayer().colorDrift) { v -> update { it.copy(colorDrift = v) } }
        ValueSlider(stringResource(R.string.controls_painted), layer.abstractLevel, Editing.Ranges.layerPainted.floats(), default = StyleLayer().abstractLevel) { v -> update { it.copy(abstractLevel = v) } }
        ValueSlider(stringResource(R.string.controls_saturation), layer.saturation, default = StyleLayer().saturation) { v -> update { it.copy(saturation = v) } }
        ValueSlider(stringResource(R.string.controls_brightness), layer.brightness, Editing.Ranges.layerBrightness.floats(), default = StyleLayer().brightness) { v -> update { it.copy(brightness = v) } }
    }
}

// ── Scene: background and effects ──

@Composable
private fun SceneTab(params: MandalaParameters, vm: EditorViewModel) {
    // Effects are used far more than the background, so they come first and open.
    val base = params.baseLayer
    EffectsGroup(params.effectsLayer, vm::updateEffects, vm::newEffectsPattern, vm::resetEffects)
    Group(stringResource(R.string.controls_background), summary = if (base.isEnabled) backgroundName(base.type) else stringResource(R.string.controls_off)) {
        BackgroundControls(base, vm::updateBackground, vm::useBackgroundImage)
    }
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
        FilterChip(selected = !base.isEnabled, onClick = { update { it.copy(isEnabled = false) } }, label = { Text(stringResource(R.string.controls_off)) })
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
            Text(stringResource(R.string.controls_background_auto_note), style = MaterialTheme.typography.bodySmall,
                color = LocalTokens.current.textMuted)
            return
        }
        BaseLayerType.IMAGE -> {
            OutlinedButton(colors = outlinedColors(), onClick = ::choose) { Text(stringResource(R.string.controls_choose_picture)) }
            ValueSlider(stringResource(R.string.controls_strength), base.imageBlend, default = BaseLayerSettings().imageBlend) { v -> update { it.copy(imageBlend = v) } }
            ValueSlider(stringResource(R.string.controls_opacity), base.opacity, default = BaseLayerSettings().opacity) { v -> update { it.copy(opacity = v) } }
            return
        }
        else -> Unit
    }
    val twoColours = base.type != BaseLayerType.COLOR && base.type != BaseLayerType.GRAIN
    val brightnessRange = Editing.Ranges.backgroundBrightness.floats()
    ColourGroup(if (twoColours) stringResource(R.string.controls_colour_1) else stringResource(R.string.controls_colour), base.hue, base.saturation, base.brightness,
        BaseLayerSettings().let { Triple(it.hue, it.saturation, it.brightness) }, brightnessRange = brightnessRange) { h, s, b ->
        update { it.copy(hue = h, saturation = s, brightness = b) }
    }
    if (twoColours) {
        ColourGroup(stringResource(R.string.controls_colour_2), base.hue2, base.saturation2, base.brightness2,
            BaseLayerSettings().let { Triple(it.hue2, it.saturation2, it.brightness2) }, brightnessRange = brightnessRange) { h, s, b ->
            update { it.copy(hue2 = h, saturation2 = s, brightness2 = b) }
        }
    }
    when (base.type) {
        BaseLayerType.GRADIENT -> {
            SwitchRow(stringResource(R.string.controls_radial), base.isRadial) { r -> update { it.copy(isRadial = r) } }
            if (!base.isRadial) ValueSlider(stringResource(R.string.controls_angle), base.gradientAngle, default = BaseLayerSettings().gradientAngle) { v -> update { it.copy(gradientAngle = v) } }
        }
        BaseLayerType.SUNBURST -> {
            ValueSlider(stringResource(R.string.controls_rays), base.patternScale, default = BaseLayerSettings().patternScale) { v -> update { it.copy(patternScale = v) } }
            ValueSlider(stringResource(R.string.controls_sharpness), base.patternSharpness, default = BaseLayerSettings().patternSharpness) { v -> update { it.copy(patternSharpness = v) } }
        }
        BaseLayerType.GRAIN -> {
            ValueSlider(stringResource(R.string.controls_grain), base.grainAmount, default = BaseLayerSettings().grainAmount) { v -> update { it.copy(grainAmount = v) } }
            SwitchRow(stringResource(R.string.controls_coloured_grain), base.grainColored) { c -> update { it.copy(grainColored = c) } }
        }
        else -> Unit
    }
    ValueSlider(stringResource(R.string.controls_opacity), base.opacity, default = BaseLayerSettings().opacity) { v -> update { it.copy(opacity = v) } }
}

@Composable
private fun backgroundName(type: BaseLayerType) = stringResource(when (type) {
    BaseLayerType.AUTO -> R.string.controls_background_auto
    BaseLayerType.IMAGE -> R.string.controls_background_picture
    BaseLayerType.COLOR -> R.string.controls_background_colour
    BaseLayerType.GRADIENT -> R.string.controls_background_gradient
    BaseLayerType.SUNBURST -> R.string.controls_background_sunburst
    BaseLayerType.GRAIN -> R.string.controls_background_grain
})

/**
 * Scene effects over the mandala, by default also over the emblem and behind the text (ADR-0018), switched on inside the section; the quick, reliable sliders in
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
    Group(stringResource(R.string.controls_effects), summary = if (fx.isEnabled) stringResource(R.string.controls_on) else stringResource(R.string.controls_off), initiallyOpen = true) {
        // Keep actions inside the section so its closed header is as compact as every other one.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.controls_show_effects), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (fx.isEnabled) {
                IconButton(onClick = newPattern) { Icon(EditorIcons.DieOne,
                    contentDescription = stringResource(R.string.controls_effects_new_pattern), tint = t.accent) }
                TextButton(onClick = resetAll) { Text(stringResource(R.string.save_reset)) }
            }
            Switch(checked = fx.isEnabled, onCheckedChange = { on -> update { it.copy(isEnabled = on) } })
        }
        if (!fx.isEnabled) {
            Text(stringResource(R.string.controls_effects_off_note), style = MaterialTheme.typography.bodySmall,
                color = LocalTokens.current.textMuted)
            return@Group
        }
        Group(stringResource(R.string.glow_light), inset = true, initiallyOpen = true) {
            ValueSlider(stringResource(R.string.save_brightness), fx.brightness, default = EffectsLayerSettings().brightness) { v -> update { it.copy(brightness = v) } }
            ValueSlider(stringResource(R.string.save_contrast), fx.contrast, default = EffectsLayerSettings().contrast) { v -> update { it.copy(contrast = v) } }
            ValueSlider(stringResource(R.string.controls_vignette), fx.vignette, Editing.Ranges.effectsVignette.floats(), default = EffectsLayerSettings().vignette) { v -> update { it.copy(vignette = v) } }
            ValueSlider(stringResource(R.string.controls_bloom), fx.bloom, Editing.Ranges.effectsBloom.floats(), default = EffectsLayerSettings().bloom) { v -> update { it.copy(bloom = v) } }
        }
        Group(stringResource(R.string.controls_tone), inset = true) {
            ValueSlider(stringResource(R.string.controls_sepia), fx.sepia, default = EffectsLayerSettings().sepia) { v -> update { it.copy(sepia = v) } }
            ValueSlider(stringResource(R.string.controls_fade), fx.wash, default = EffectsLayerSettings().wash) { v -> update { it.copy(wash = v) } }
        }
        Group(stringResource(R.string.controls_texture), inset = true) {
            ValueSlider(stringResource(R.string.controls_clarity), fx.localContrast, default = EffectsLayerSettings().localContrast) { v -> update { it.copy(localContrast = v) } }
            ValueSlider(stringResource(R.string.controls_background_grain), fx.grain, Editing.Ranges.effectsGrain.floats(), default = EffectsLayerSettings().grain) { v -> update { it.copy(grain = v) } }
            ValueSlider(stringResource(R.string.controls_smoother), fx.smoother, default = EffectsLayerSettings().smoother) { v -> update { it.copy(smoother = v) } }
        }
        Group(stringResource(R.string.controls_sparkle), inset = true) {
            ValueSlider(stringResource(R.string.controls_stars), fx.stars, default = EffectsLayerSettings().stars) { v -> update { it.copy(stars = v) } }
            ValueSlider(stringResource(R.string.controls_glitter), fx.glitter, Editing.Ranges.effectsGlitter.floats(), default = EffectsLayerSettings().glitter) { v -> update { it.copy(glitter = v) } }
        }
        Group(stringResource(R.string.editor_more), inset = true) {
            ValueSlider(stringResource(R.string.controls_highlights), fx.highlights, default = EffectsLayerSettings().highlights) { v -> update { it.copy(highlights = v) } }
            ValueSlider(stringResource(R.string.controls_dimming), fx.dimming, default = EffectsLayerSettings().dimming) { v -> update { it.copy(dimming = v) } }
            ValueSlider(stringResource(R.string.controls_erasure), fx.erasure, default = EffectsLayerSettings().erasure) { v -> update { it.copy(erasure = v) } }
            ValueSlider(stringResource(R.string.controls_relief), fx.relief, default = EffectsLayerSettings().relief) { v -> update { it.copy(relief = v) } }
            if (fx.relief > 0) ValueSlider(stringResource(R.string.controls_relief_angle), fx.reliefAngle, default = EffectsLayerSettings().reliefAngle) { v -> update { it.copy(reliefAngle = v) } }
            ValueSlider(stringResource(R.string.controls_chromatic), fx.chromatic, default = EffectsLayerSettings().chromatic) { v -> update { it.copy(chromatic = v) } }
            if (showOldFade) ValueSlider(stringResource(R.string.controls_old_fade), fx.fade, default = EffectsLayerSettings().fade) { v -> update { it.copy(fade = v) } }
        }
        // Rarely changed, so last and closed; the summary says what the effects reach.
        val emblem = stringResource(R.string.controls_tab_emblem)
        val text = stringResource(R.string.controls_tab_text)
        val reach = listOfNotNull(stringResource(R.string.controls_reach_mandala), emblem.takeIf { fx.affectsEmblem }, text.takeIf { fx.affectsText })
        Group(stringResource(R.string.controls_applies_to), summary = reach.joinToString(", "), inset = true) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SwitchRow(emblem, fx.affectsEmblem) { on -> update { it.withEmblem(on) } }
                SwitchRow(text, fx.affectsText) { on -> update { it.withText(on) } }
            }
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
            stringResource(R.string.controls_emblem_show).let { if (position.proOwned) it else withPro(it) }, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).toggleable(value = em.isEnabled, role = Role.Switch) { on -> update { it.switched(on) } },
        )
        if (em.isEnabled) {
            IconButton(onClick = vm::randomizeEmblem) { Icon(EditorIcons.DieOne, contentDescription = stringResource(R.string.controls_random_emblem), tint = t.accent) }
            TextButton(onClick = vm::resetEmblem) { Text(stringResource(R.string.save_reset)) }
        }
        Switch(checked = em.isEnabled, onCheckedChange = { on -> update { it.switched(on) } })
    }
    if (!em.isEnabled) {
        Text(stringResource(R.string.controls_emblem_off_note),
            style = MaterialTheme.typography.bodySmall, color = t.textMuted)
        return
    }
    if (!position.proOwned) ProNote(stringResource(R.string.controls_pro_note_emblem), position.showPro)
    Group(stringResource(R.string.controls_motif), summary = stringResource(em.motif.labelRes), initiallyOpen = true) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (motif in EmblemMotif.entries) {
                FilterChip(selected = em.motif == motif, onClick = { update { it.copy(motif = motif) } }, label = { Text(stringResource(motif.labelRes)) })
            }
        }
        when (em.motif) {
            EmblemMotif.FLOWER_OF_LIFE -> ChipLine(stringResource(R.string.controls_variant)) {
                for (v in EmblemFlowerVariant.entries) {
                    FilterChip(selected = em.flowerVariant == v, onClick = { update { it.copy(flowerVariant = v) } }, label = { Text(stringResource(v.labelRes)) })
                }
            }
            EmblemMotif.LOTUS -> ChipLine(stringResource(R.string.controls_petals)) {
                for (n in Editing.lotusPetals) {
                    FilterChip(selected = em.petals == n, onClick = { update { it.copy(petals = n) } }, label = { Text("$n") })
                }
            }
            else -> Unit
        }
        if (em.motif.isOrganic) ValueSlider(stringResource(R.string.controls_organic), em.organic, default = defaults.organic) { v -> update { it.copy(organic = v) } }
        if (em.motif == EmblemMotif.TREE_OF_LIFE) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.controls_emblem_shape_seed, em.seed.toString()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::newEmblemShape) { Text(stringResource(R.string.controls_new_shape)) }
            }
            ValueSlider(stringResource(R.string.controls_branches), em.branching, default = defaults.branching) { v -> update { it.copy(branching = v) } }
            SwitchRow(stringResource(R.string.controls_roots), em.roots) { r -> update { it.copy(roots = r) } }
        }
    }
    Group(stringResource(R.string.controls_style_and_frame), summary = "${stringResource(em.rendering.labelRes)} · ${stringResource(em.frame.labelRes)}") {
        ChipLine(stringResource(R.string.controls_style)) {
            for (r in EmblemRendering.entries) {
                FilterChip(selected = em.rendering == r, onClick = { update { it.copy(rendering = r) } }, label = { Text(stringResource(r.labelRes)) })
            }
        }
        ChipLine(stringResource(R.string.controls_frame)) {
            for (f in EmblemFrame.entries) {
                FilterChip(selected = em.frame == f, onClick = { update { it.copy(frame = f) } }, label = { Text(stringResource(f.labelRes)) })
            }
        }
    }
    Group(stringResource(R.string.controls_shape)) {
        ValueSlider(stringResource(R.string.controls_size), em.size, Editing.Ranges.emblemSize.floats(), default = defaults.size) { v -> update { it.copy(size = v) } }
        ValueSlider(stringResource(R.string.controls_weight), em.weight, Editing.Ranges.emblemWeight.floats(), default = defaults.weight) { v -> update { it.copy(weight = v) } }
        ValueSlider(stringResource(R.string.controls_rotate), em.rotation, -180f..180f, default = defaults.rotation, display = { "${it.roundToInt()}°" }) { v ->
            update { it.copy(rotation = v) }
        }
    }
    PositionGroup(em.offsetX, em.offsetY, MoveTarget.EMBLEM, position) { x, y -> update { it.copy(offsetX = x, offsetY = y) } }
    val palette = BuiltInPalettes.all[em.paletteIndex.coerceIn(0, BuiltInPalettes.all.lastIndex)]
    Group(stringResource(R.string.controls_colour), summary = palette.name) {
        PaletteSwatches(em.paletteIndex) { index -> update { it.copy(paletteIndex = index) } }
        ValueSlider(stringResource(R.string.controls_position), em.colorPosition, default = defaults.colorPosition) { v -> update { it.copy(colorPosition = v) } }
        ValueSlider(stringResource(R.string.controls_spread), em.colorSpread, default = defaults.colorSpread) { v -> update { it.copy(colorSpread = v) } }
        ValueSlider(stringResource(R.string.controls_glow), em.glow, default = defaults.glow) { v -> update { it.copy(glow = v) } }
        ChipLine(stringResource(R.string.controls_glow)) {
            for (m in EmblemGlowMode.entries) {
                FilterChip(selected = em.glowMode == m, onClick = { update { it.copy(glowMode = m) } }, label = { Text(stringResource(m.labelRes)) })
            }
        }
    }
    Group(stringResource(R.string.controls_blending), summary = blendSummary(em.blendMode, em.opacity)) {
        BlendChips(em.blendMode) { m -> update { it.copy(blendMode = m) } }
        ValueSlider(stringResource(R.string.controls_opacity), em.opacity, default = defaults.opacity) { v -> update { it.copy(opacity = v) } }
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
    val quoteLanguages by vm.quoteLanguages.collectAsStateWithLifecycle()
    val switch: (Boolean) -> Unit = { on -> if (on && text.text.isEmpty()) vm.randomQuote() else update { it.copy(isEnabled = on) } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        // The label switches too; the die and reset beside it keep their own taps (as in the emblem tab).
        Text(
            stringResource(R.string.controls_text_show).let { if (position.proOwned) it else withPro(it) }, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).toggleable(value = text.isEnabled, role = Role.Switch, onValueChange = switch),
        )
        if (text.isEnabled) {
            IconButton(onClick = vm::randomizeText) { Icon(EditorIcons.DieOne, contentDescription = stringResource(R.string.controls_random_text_look), tint = LocalTokens.current.accent) }
            TextButton(onClick = vm::resetText) { Text(stringResource(R.string.save_reset)) }
        }
        Switch(checked = text.isEnabled, onCheckedChange = switch)
    }
    if (choosing) {
        QuoteDialog(
            myQuotes = myQuotes,
            languages = quoteLanguages,
            onToggleLanguage = vm::toggleQuoteLanguage,
            onPick = { vm.useQuote(it); choosing = false },
            onRemove = vm::removeMyQuote,
            onDismiss = { choosing = false },
        )
    }
    if (!text.isEnabled) {
        Text(stringResource(R.string.controls_text_off_note),
            style = MaterialTheme.typography.bodySmall, color = LocalTokens.current.textMuted)
        return
    }
    if (!position.proOwned) ProNote(stringResource(R.string.controls_pro_note_text), position.showPro)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(colors = outlinedColors(), onClick = vm::randomQuote) { Text(stringResource(R.string.controls_random_quote)) }
        OutlinedButton(colors = outlinedColors(), onClick = { choosing = true }) { Text(stringResource(R.string.controls_choose_quote)) }
    }
    OutlinedTextField(
        value = text.text,
        onValueChange = { v -> update { it.copy(text = v) } },
        label = { Text(stringResource(R.string.controls_text_field)) },
        maxLines = 4,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
    )
    OutlinedTextField(
        value = text.customAuthor,
        onValueChange = { v -> update { it.copy(customAuthor = v) } },
        label = { Text(stringResource(R.string.controls_author)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val saved = myQuotes.any { it.text == text.text.trim() && it.author == text.customAuthor.trim() }
    TextButton(onClick = vm::saveTextAsMyQuote, enabled = text.text.isNotBlank() && !saved) {
        Text(stringResource(if (saved) R.string.controls_text_in_my_quotes else R.string.controls_text_save_to_my_quotes))
    }
    SwitchRow(stringResource(R.string.controls_show_author), text.showAuthor) { a -> update { it.copy(showAuthor = a) } }
    Group(stringResource(R.string.controls_font), summary = Quotes.fonts.firstOrNull { it.first == text.fontName }?.second ?: text.fontName) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for ((name, display) in Quotes.fonts) {
                FilterChip(selected = text.fontName == name, onClick = { update { it.copy(fontName = name) } }, label = { Text(display) })
            }
        }
    }
    Group(stringResource(R.string.controls_size_and_place)) {
        ValueSlider(stringResource(R.string.controls_size), text.fontSize, Editing.Ranges.textSize.floats(), default = TextLayerSettings().fontSize) { v -> update { it.copy(fontSize = v) } }
        ValueSlider(stringResource(R.string.controls_width), text.fontWidth, Editing.Ranges.textWidth.floats(), default = TextLayerSettings().fontWidth) { v -> update { it.copy(fontWidth = v) } }
        ValueSlider(stringResource(R.string.controls_tracking), text.tracking, Editing.Ranges.textTracking.floats(), default = TextLayerSettings().tracking) { v -> update { it.copy(tracking = v) } }
        ValueSlider(stringResource(R.string.controls_height), text.offsetY, default = TextLayerSettings().offsetY) { v -> update { it.copy(offsetY = v) } }
    }
    Group(stringResource(R.string.controls_light)) {
        ValueSlider(stringResource(R.string.controls_glow), text.glow, Editing.Ranges.textGlow.floats(), default = TextLayerSettings().glow) { v -> update { it.copy(glow = v) } }
        ValueSlider(stringResource(R.string.controls_cloud), text.cloudOpacity, default = TextLayerSettings().cloudOpacity) { v -> update { it.copy(cloudOpacity = v) } }
        ValueSlider(stringResource(R.string.controls_cloud_size), text.cloudScale, Editing.Ranges.textCloudSize.floats(), default = TextLayerSettings().cloudScale) { v -> update { it.copy(cloudScale = v) } }
        // The colours only where the cloud or the shadow shows; the default of both is black.
        if (text.cloudOpacity > 0) {
            ColourGroup(stringResource(R.string.controls_cloud_colour), text.cloudHue, text.cloudSaturation, text.cloudBrightness,
                defaults = Triple(TextLayerSettings().cloudHue, TextLayerSettings().cloudSaturation, TextLayerSettings().cloudBrightness), inset = true,
            ) { h, sa, b -> update { it.copy(cloudHue = h, cloudSaturation = sa, cloudBrightness = b) } }
        }
        ValueSlider(stringResource(R.string.controls_shadow), text.shadowOpacity, default = TextLayerSettings().shadowOpacity) { v -> update { it.copy(shadowOpacity = v) } }
        if (text.shadowOpacity > 0) {
            ColourGroup(stringResource(R.string.controls_shadow_colour), text.shadowHue, text.shadowSaturation, text.shadowBrightness,
                defaults = Triple(TextLayerSettings().shadowHue, TextLayerSettings().shadowSaturation, TextLayerSettings().shadowBrightness), inset = true,
            ) { h, sa, b -> update { it.copy(shadowHue = h, shadowSaturation = sa, shadowBrightness = b) } }
        }
    }
    Group(stringResource(R.string.controls_colour), trailing = { ColourDot(hsbColour(text.hue, text.saturation, text.brightness.coerceAtMost(1.0))) }) {
        ValueSlider(stringResource(R.string.controls_hue), text.hue, default = TextLayerSettings().hue) { v -> update { it.copy(hue = v) } }
        ValueSlider(stringResource(R.string.controls_saturation), text.saturation, default = TextLayerSettings().saturation) { v -> update { it.copy(saturation = v) } }
        ValueSlider(stringResource(R.string.controls_brightness), text.brightness, Editing.Ranges.textBrightness.floats(), default = TextLayerSettings().brightness) { v -> update { it.copy(brightness = v) } }
    }
}

/**
 * Language chips (only with more than one collection), then your own quotes (with a remove button), then
 * the built-in public-domain collections of the languages switched on (ADR-0019).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuoteDialog(
    myQuotes: List<Quote>,
    languages: List<String>,
    onToggleLanguage: (String) -> Unit,
    onPick: (Quote) -> Unit,
    onRemove: (Quote) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalTokens.current
    val shown = Quotes.collectionsFor(languages)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.controls_close)) } },
        title = { Text(stringResource(R.string.controls_quotes)) },
        text = {
            LazyColumn {
                if (Quotes.collections.size > 1) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(bottom = Spacing.sm)) {
                            for (c in Quotes.collections) {
                                FilterChip(selected = c.language in languages, onClick = { onToggleLanguage(c.language) }, label = { Text(c.name) })
                            }
                        }
                    }
                }
                if (myQuotes.isNotEmpty()) {
                    item { Text(stringResource(R.string.controls_your_quotes), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = Spacing.xs)) }
                    items(myQuotes) { q -> QuoteRow(q, onPick) { IconButton(onClick = { onRemove(q) }) { Icon(EditorIcons.Close, stringResource(R.string.controls_remove_quote)) } } }
                }
                for ((i, c) in shown.withIndex()) {
                    if (myQuotes.isNotEmpty() || shown.size > 1) {
                        item {
                            Text(if (shown.size > 1) c.name else stringResource(R.string.controls_quotes_collection), style = MaterialTheme.typography.titleSmall,
                                modifier = if (i > 0 || myQuotes.isNotEmpty()) Modifier.padding(top = Spacing.md, bottom = Spacing.xs) else Modifier.padding(bottom = Spacing.xs))
                        }
                    }
                    items(c.quotes) { q -> QuoteRow(q, onPick, null) }
                }
                item {
                    Text(stringResource(R.string.controls_quotes_public_domain), style = MaterialTheme.typography.bodySmall,
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
@OptIn(ExperimentalLayoutApi::class)
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
                label = { Text("${stringResource(option.label)} (${params.strokeCount(option)})") },
            )
        }
    }
    // One wide action and two small icon buttons, so the row fits in every language without wrapping.
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = Spacing.sm),
    ) {
        if (active == null) {
            Button(onClick = { vm.setDrawTool(tool) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.controls_draw_on_image), maxLines = 1) }
        } else {
            FilledTonalButton(onClick = { vm.setDrawTool(null) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.controls_done), maxLines = 1) }
        }
        OutlinedIconButton(colors = outlinedIconColors(), onClick = { vm.removeLastStroke(tool) }, enabled = count > 0) {
            Icon(EditorIcons.Back, contentDescription = stringResource(R.string.controls_undo))
        }
        OutlinedIconButton(colors = outlinedIconColors(), onClick = { vm.clearStrokes(tool) }, enabled = count > 0) {
            Icon(EditorIcons.Trash, contentDescription = stringResource(R.string.controls_clear))
        }
    }
    if (count > 0) {
        val shown = if (tool == DrawTool.LINES) lines.isEnabled else spray.isEnabled
        SwitchRow(stringResource(if (tool == DrawTool.LINES) R.string.controls_draw_show_lines else R.string.controls_draw_show_spray), shown) { on ->
            if (tool == DrawTool.LINES) vm.updateDrawing { it.copy(isEnabled = on) } else vm.updateGraffiti { it.copy(isEnabled = on) }
        }
    }
    when (tool) {
        DrawTool.LINES -> {
            SteppedSlider(stringResource(R.string.controls_symmetry), lines.symmetry.coerceIn(1, 16), 1..16, default = DrawingLayerSettings().symmetry) { v -> vm.updateDrawing { it.copy(symmetry = v) } }
            ColourGroup(stringResource(R.string.controls_line_colour), lines.currentHue, lines.currentSaturation, lines.currentBrightness,
                DrawingLayerSettings().let { Triple(it.currentHue, it.currentSaturation, it.currentBrightness) }, initiallyOpen = true) { h, s, b ->
                vm.updateDrawing { it.copy(currentHue = h, currentSaturation = s, currentBrightness = b) }
            }
            Group(stringResource(R.string.controls_line_and_light)) {
                ValueSlider(stringResource(R.string.controls_weight), lines.strokeWeight, default = DrawingLayerSettings().strokeWeight) { v -> vm.updateDrawing { it.copy(strokeWeight = v) } }
                ValueSlider(stringResource(R.string.controls_glow), lines.glowIntensity, default = DrawingLayerSettings().glowIntensity) { v -> vm.updateDrawing { it.copy(glowIntensity = v) } }
                ValueSlider(stringResource(R.string.controls_opacity), lines.opacity, default = DrawingLayerSettings().opacity) { v -> vm.updateDrawing { it.copy(opacity = v) } }
                BlendChips(lines.blendMode) { m -> vm.updateDrawing { it.copy(blendMode = m) } }
            }
        }
        DrawTool.SPRAY -> {
            SteppedSlider(stringResource(R.string.controls_symmetry), spray.symmetry.coerceIn(1, 16), 1..16, default = GraffitiLayerSettings().symmetry) { v -> vm.updateGraffiti { it.copy(symmetry = v) } }
            ValueSlider(stringResource(R.string.controls_brush_size), spray.currentBrushSize, 0.01f..0.2f, default = GraffitiLayerSettings().currentBrushSize) { v -> vm.updateGraffiti { it.copy(currentBrushSize = v) } }
            ValueSlider(stringResource(R.string.controls_paint), spray.currentOpacity, default = GraffitiLayerSettings().currentOpacity) { v -> vm.updateGraffiti { it.copy(currentOpacity = v) } }
            ColourGroup(stringResource(R.string.controls_spray_colour), spray.currentHue, spray.currentSaturation, spray.currentBrightness,
                GraffitiLayerSettings().let { Triple(it.currentHue, it.currentSaturation, it.currentBrightness) }, initiallyOpen = true) { h, s, b ->
                vm.updateGraffiti { it.copy(currentHue = h, currentSaturation = s, currentBrightness = b) }
            }
            Group(stringResource(R.string.controls_softness_and_blending)) {
                ValueSlider(stringResource(R.string.controls_softness), spray.softness, default = GraffitiLayerSettings().softness) { v -> vm.updateGraffiti { it.copy(softness = v) } }
                ValueSlider(stringResource(R.string.controls_opacity), spray.opacity, default = GraffitiLayerSettings().opacity) { v -> vm.updateGraffiti { it.copy(opacity = v) } }
                BlendChips(spray.blendMode) { m -> vm.updateGraffiti { it.copy(blendMode = m) } }
            }
        }
    }
}
