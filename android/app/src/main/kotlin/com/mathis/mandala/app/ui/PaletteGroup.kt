package com.mathis.mandala.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathis.mandala.app.Editing
import com.mathis.mandala.app.EditorViewModel
import com.mathis.mandala.app.R
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import com.mathis.mandala.core.BuiltInPalettes
import com.mathis.mandala.core.CustomPalette
import com.mathis.mandala.core.CustomPaletteStop
import com.mathis.mandala.core.OwnPalettes

/** An own palette while it is being made or changed; `before` is what the layer had, for Cancel. */
private data class PaletteDraft(
    val palette: CustomPalette,
    val isNew: Boolean,
    val selected: Int,
    val beforeIndex: Int,
    val beforeOwn: CustomPalette?,
)

/**
 * The palette of a layer or the emblem (DM-15, ADR-0021): the offered built-in palettes, one that came
 * with the picture, the user's own ones with a plus to make one, and the editor for them in place, so
 * the picture above shows every change. `apply` sets index and own palette as they are given.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaletteGroup(
    title: String,
    index: Int,
    own: CustomPalette?,
    vm: EditorViewModel,
    /** Which layer (or the emblem) is shown; an open editor belongs to it. */
    editorKey: Any,
    apply: (index: Int, own: CustomPalette?) -> Unit,
    more: @Composable ColumnScope.() -> Unit = {},
) {
    val t = LocalTokens.current
    val mine by vm.myPalettes.collectAsStateWithLifecycle()
    var editing by remember(editorKey) { mutableStateOf<PaletteDraft?>(null) }
    val shown = own?.takeIf { it.stops.isNotEmpty() }
    val builtIn = BuiltInPalettes.all[index.coerceIn(0, BuiltInPalettes.all.lastIndex)]
    val useOwn: (CustomPalette) -> Unit = { apply(Editing.fallbackPalette(it), it) }
    val defaultName = stringResource(R.string.controls_palette_default_name, mine.size + 1)

    Group(title, summary = (editing?.palette ?: shown)?.name ?: builtIn.name) {
        val draft = editing
        if (draft != null) {
            PaletteEditor(
                draft,
                onChange = { next ->
                    if (next.palette.stops != draft.palette.stops) useOwn(next.palette)
                    editing = next
                },
                onFromPicture = {
                    vm.coloursFromPicture { stops ->
                        editing?.let { d ->
                            val next = d.copy(palette = d.palette.copy(stops = stops), selected = 0)
                            useOwn(next.palette)
                            editing = next
                        }
                    }
                },
                onSave = {
                    vm.saveMyPalette(draft.palette.let { if (it.name.isBlank()) it.copy(name = defaultName) else it.copy(name = it.name.trim()) })
                    editing = null
                },
                onCancel = {
                    apply(draft.beforeIndex, draft.beforeOwn)
                    editing = null
                },
                onDelete = {
                    vm.deleteMyPalette(draft.palette.id)
                    editing = null
                },
                newId = vm::newPaletteId,
            )
            return@Group
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (i in Editing.paletteChoices(if (shown == null) index else -1)) {
                Swatch(BuiltInPalettes.all[i], selected = shown == null && i == index) { apply(i, null) }
            }
        }
        val foreign = shown?.takeIf { it !in mine }
        if (foreign != null) {
            SubHeading(stringResource(R.string.controls_in_this_picture))
            Swatch(foreign.toColorPalette(), selected = true) {}
            NameRow(foreign, stringResource(R.string.controls_palette_keep)) { vm.keepPalette(foreign) }
        }
        SubHeading(stringResource(R.string.controls_my_palettes))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (p in mine) Swatch(p.toColorPalette(), selected = shown == p) { useOwn(p) }
            val newLabel = stringResource(R.string.controls_palette_new)
            Box(
                Modifier.size(Sizes.swatch).padding(Sizes.swatchRing * 2).clip(CircleShape)
                    .border(Sizes.swatchRing / 2, t.outline, CircleShape)
                    .clickable(role = Role.Button) {
                        val start = shown?.stops ?: OwnPalettes.stopsOf(builtIn)
                        val palette = CustomPalette(vm.newPaletteId(), defaultName, start.map { it.copy(id = vm.newPaletteId()) })
                        editing = PaletteDraft(palette, isNew = true, selected = 0, beforeIndex = index, beforeOwn = own)
                        useOwn(palette)
                    }
                    .semantics { contentDescription = newLabel },
                contentAlignment = Alignment.Center,
            ) { Icon(EditorIcons.Plus, contentDescription = null, tint = t.accent) }
        }
        if (shown != null && shown in mine) {
            NameRow(shown, stringResource(R.string.controls_edit)) {
                editing = PaletteDraft(shown, isNew = false, selected = 0, beforeIndex = index, beforeOwn = own)
            }
        }
        more()
    }
}

@Composable
private fun SubHeading(text: String) {
    val t = LocalTokens.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = t.textMuted, modifier = Modifier.padding(end = Spacing.sm))
        HorizontalDivider(color = t.outline, modifier = Modifier.weight(1f))
    }
}

/** The chosen palette's name with one action on the right. */
@Composable
private fun NameRow(palette: CustomPalette, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
        Text(palette.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun PaletteEditor(
    draft: PaletteDraft,
    onChange: (PaletteDraft) -> Unit,
    onFromPicture: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    newId: () -> String,
) {
    val t = LocalTokens.current
    val palette = draft.palette
    val stops = palette.stops
    val selected = draft.selected.coerceIn(0, stops.lastIndex)
    val stop = stops[selected]
    fun withStops(next: List<CustomPaletteStop>, sel: Int = selected) = onChange(draft.copy(palette = palette.copy(stops = next), selected = sel))
    fun withStop(change: (CustomPaletteStop) -> CustomPaletteStop) = withStops(stops.toMutableList().also { it[selected] = change(stop) })

    Text(stringResource(if (draft.isNew) R.string.controls_palette_new else R.string.controls_palette_edit),
        style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = Spacing.sm))
    OutlinedTextField(
        value = palette.name,
        onValueChange = { onChange(draft.copy(palette = palette.copy(name = it))) },
        label = { Text(stringResource(R.string.controls_palette_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Box(
        Modifier.padding(top = Spacing.md).fillMaxWidth().height(Sizes.swatch)
            .clip(RoundedCornerShape(Radius.sm)).background(paletteBar(palette.toColorPalette())),
    )
    StopHandles(stops, selected, onSelect = { onChange(draft.copy(selected = it)) }) { i, position ->
        withStops(stops.toMutableList().also { it[i] = it[i].copy(position = position) }, i)
    }
    val defaults = OwnPalettes.defaultStops()[1]
    ValueSlider(stringResource(R.string.controls_hue), stop.hue, default = defaults.hue) { v -> withStop { it.copy(hue = v) } }
    ValueSlider(stringResource(R.string.save_saturation), stop.saturation, default = defaults.saturation) { v -> withStop { it.copy(saturation = v) } }
    ValueSlider(stringResource(R.string.save_brightness), stop.brightness, default = defaults.brightness) { v -> withStop { it.copy(brightness = v) } }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
        ActionTile(EditorIcons.FromPicture, stringResource(R.string.controls_palette_from_picture), enabled = true, onClick = onFromPicture)
        ActionTile(EditorIcons.Plus, stringResource(R.string.controls_palette_add_colour), enabled = stops.size < OwnPalettes.MAX_STOPS) {
            // Halfway to the next colour on the right (or the left at the end), in the chosen colour.
            val sorted = stops.sortedBy { it.position }
            val at = sorted.indexOf(stop)
            val other = sorted.getOrNull(at + 1) ?: sorted.getOrNull(at - 1)
            val position = if (other == null) 0.5 else (stop.position + other.position) / 2
            withStops(stops + stop.copy(id = newId(), position = position), stops.size)
        }
        ActionTile(EditorIcons.Trash, stringResource(R.string.controls_palette_remove_colour), enabled = stops.size > OwnPalettes.MIN_STOPS) {
            withStops(stops.filterIndexed { i, _ -> i != selected }, (selected - 1).coerceAtLeast(0))
        }
    }
    Text(stringResource(R.string.controls_palette_hint), style = MaterialTheme.typography.bodySmall, color = t.textMuted,
        modifier = Modifier.padding(top = Spacing.sm))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        if (!draft.isNew) TextButton(onClick = onDelete) { Text(stringResource(R.string.controls_delete), color = t.error) }
        Box(Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.controls_cancel)) }
        Button(onClick = onSave, modifier = Modifier.padding(start = Spacing.sm)) { Text(stringResource(R.string.controls_palette_save)) }
    }
}

/** The stops as dots under the bar, at their positions: a tap chooses one, a sideways drag moves it. */
@Composable
private fun StopHandles(
    stops: List<CustomPaletteStop>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onMove: (Int, Double) -> Unit,
) {
    val t = LocalTokens.current
    val current by rememberUpdatedState(stops)
    val move by rememberUpdatedState(onMove)
    val select by rememberUpdatedState(onSelect)
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = Sizes.swatch).padding(top = Spacing.xs)) {
        val track = maxWidth - Sizes.swatchSmall
        for ((i, s) in stops.withIndex()) {
            val label = stringResource(R.string.controls_palette_colour, i + 1)
            Box(
                Modifier
                    .offset(x = track * s.position.toFloat())
                    .size(Sizes.swatchSmall)
                    .border(Sizes.swatchRing, if (i == selected) t.accent else t.outline, CircleShape)
                    .padding(Sizes.swatchRing)
                    .clip(CircleShape)
                    .background(hsbColour(s.hue, s.saturation, s.brightness))
                    .pointerInput(i) {
                        val width = track.toPx()
                        detectHorizontalDragGestures(onDragStart = { select(i) }) { change, dx ->
                            change.consume()
                            if (i in current.indices && width > 0) move(i, (current[i].position + dx / width).coerceIn(0.0, 1.0))
                        }
                    }
                    .clickable(role = Role.Button) { onSelect(i) }
                    .semantics {
                        contentDescription = label
                        this.selected = i == selected
                    },
            )
        }
    }
}
