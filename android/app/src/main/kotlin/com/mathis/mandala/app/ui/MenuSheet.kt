package com.mathis.mandala.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.mathis.mandala.app.Candidate
import com.mathis.mandala.app.NewKind
import com.mathis.mandala.app.WallpaperTarget
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import com.mathis.mandala.core.MandalaParameters

/** What the menu sheet can do; the screen wires each to the view model or a system dialog. */
internal class MenuActions(
    val onFavourite: () -> Unit,
    val onShare: () -> Unit,
    val onWallpaper: (WallpaperTarget) -> Unit,
    /** Opens the pair sheet, or the Pro sheet without Pro; `pairLabel` says which. */
    val onPair: () -> Unit,
    val pairLabel: String,
    val onTryNew: (NewKind) -> Unit,
    val onUseNew: () -> Unit,
    val onBackToMenu: () -> Unit,
    val onOpen: () -> Unit,
    val onSaveSettings: () -> Unit,
    val onFastPreview: (Boolean) -> Unit,
    val onLightTheme: (Boolean) -> Unit,
    val onAbout: () -> Unit,
)

/**
 * The menu as a sheet from below (DM-7 B): the mandala on screen with what keeps it, three dice for a
 * new one, then files and settings. A die shows its new mandala next to the current one in the same
 * sheet; only "Use new" replaces it. The die in the top bar stays the quick way, without asking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MenuSheet(
    params: MandalaParameters,
    image: ImageBitmap?,
    candidate: Candidate?,
    busy: Boolean,
    fastPreview: Boolean,
    lightTheme: Boolean,
    actions: MenuActions,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl)) {
            if (candidate == null) {
                Current(params, image)
                KeepRow(busy, actions)
                NewMandala(actions.onTryNew)
                Settings(fastPreview, lightTheme, actions)
            } else {
                Compare(image, candidate, actions)
            }
        }
    }
}

@Composable
private fun Current(params: MandalaParameters, image: ImageBitmap?) {
    val t = LocalTokens.current
    val layers = params.layers.filter { it.isEnabled }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Thumb(image, Modifier.size(Sizes.favourite / 2))
        Column(Modifier.weight(1f)) {
            Text(
                layers.joinToString(", ") { it.style.displayName }.ifEmpty { "No layers" },
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${layers.size} ${if (layers.size == 1) "layer" else "layers"} · emblem ${if (params.emblemLayer.isEnabled) "on" else "off"}",
                style = MaterialTheme.typography.bodySmall, color = t.textMuted,
            )
        }
    }
}

@Composable
private fun KeepRow(busy: Boolean, actions: MenuActions) {
    Row(Modifier.padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Pill(EditorIcons.StarLine, "Favourite", onClick = actions.onFavourite)
        Pill(EditorIcons.Share, "Share", enabled = !busy, onClick = actions.onShare)
        var open by remember { mutableStateOf(false) }
        Box(Modifier.weight(1f)) {
            PillContent(EditorIcons.Wallpaper, "Wallpaper", !busy, { open = true }, Modifier.fillMaxWidth())
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (target in WallpaperTarget.entries) {
                    DropdownMenuItem(text = { Text(target.label) }, onClick = { open = false; actions.onWallpaper(target) })
                }
                DropdownMenuItem(text = { Text(actions.pairLabel) }, onClick = { open = false; actions.onPair() })
            }
        }
    }
}

@Composable
private fun RowScope.Pill(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) =
    PillContent(icon, label, enabled, onClick, Modifier.weight(1f))

@Composable
private fun PillContent(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val t = LocalTokens.current
    Column(
        modifier.clip(RoundedCornerShape(Radius.md)).background(t.surfaceRaised).clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) t.accent else t.textMuted)
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) t.text else t.textMuted)
    }
}

private class DieChoice(val kind: NewKind, val icon: ImageVector, val title: String, val detail: String, val steps: Int)

private val dice = listOf(
    DieChoice(NewKind.VARIATION, EditorIcons.DieOne, "Variation", "new details", 1),
    DieChoice(NewKind.LAYERS, EditorIcons.DieThree, "New layers", "new styles", 2),
    DieChoice(NewKind.EVERYTHING, EditorIcons.DieFive, "Surprise me", "everything", 3),
)

@Composable
private fun NewMandala(onTryNew: (NewKind) -> Unit) {
    val t = LocalTokens.current
    Text("New mandala", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (die in dice) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(Radius.md)).background(t.surfaceRaised).clickable { onTryNew(die.kind) }
                    .padding(horizontal = Spacing.xs, vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(die.icon, contentDescription = null, tint = t.accent)
                Text(die.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
                Text(die.detail, style = MaterialTheme.typography.bodySmall, color = t.textMuted, maxLines = 1)
                // How much changes: one, two or three of three steps.
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    repeat(3) { i ->
                        Box(
                            Modifier.width(Spacing.md).height(Sizes.progress).clip(RoundedCornerShape(Radius.sm))
                                .background(if (i < die.steps) t.accent else t.outline),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Settings(fastPreview: Boolean, lightTheme: Boolean, actions: MenuActions) {
    val t = LocalTokens.current
    Column(Modifier.padding(top = Spacing.md)) {
        HorizontalDivider(color = t.outline)
        MenuRow(EditorIcons.Folder, "Open file", onClick = actions.onOpen) { Chevron() }
        HorizontalDivider(color = t.outline)
        MenuRow(EditorIcons.FileSave, "Save settings", onClick = actions.onSaveSettings) { Chevron() }
        HorizontalDivider(color = t.outline)
        Row(
            Modifier.fillMaxWidth().toggleable(value = fastPreview, role = Role.Switch, onValueChange = actions.onFastPreview)
                .padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(EditorIcons.Bolt, contentDescription = null, tint = t.textMuted)
            Column(Modifier.weight(1f)) {
                Text("Fast preview", style = MaterialTheme.typography.bodyLarge)
                Text("Saved images always get the full glow", style = MaterialTheme.typography.bodySmall, color = t.textMuted)
            }
            Switch(checked = fastPreview, onCheckedChange = null)
        }
        HorizontalDivider(color = t.outline)
        MenuRow(EditorIcons.Moon, "Theme", onClick = null) {
            ThemeChoice(lightTheme, actions.onLightTheme)
        }
        HorizontalDivider(color = t.outline)
        MenuRow(EditorIcons.Info, "About", onClick = actions.onAbout) { Chevron() }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: (() -> Unit)?, trailing: @Composable () -> Unit) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = t.textMuted)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Chevron() = Icon(EditorIcons.Chevron, contentDescription = null, tint = LocalTokens.current.textMuted)

/** Dark or light, as two halves of one rounded control. */
@Composable
private fun ThemeChoice(light: Boolean, onLight: (Boolean) -> Unit) {
    val t = LocalTokens.current
    val pill = RoundedCornerShape(percent = 50)
    Row(Modifier.clip(pill).background(t.surfaceRaised).padding(Spacing.xs)) {
        for ((label, isLight) in listOf("Dark" to false, "Light" to true)) {
            val on = light == isLight
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (on) t.text else t.textMuted,
                modifier = Modifier.clip(pill).background(if (on) t.surface else t.surfaceRaised)
                    .toggleable(value = on, role = Role.RadioButton) { onLight(isLight) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
    }
}

/** Second step: the current mandala and the new one side by side; nothing changes before "Use new". */
@Composable
private fun Compare(image: ImageBitmap?, candidate: Candidate, actions: MenuActions) {
    val t = LocalTokens.current
    Row(
        Modifier.clip(RoundedCornerShape(Radius.sm)).clickable(onClick = actions.onBackToMenu).padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EditorIcons.ChevronBack, contentDescription = null, tint = t.textMuted)
        Text("Menu", style = MaterialTheme.typography.bodyMedium, color = t.textMuted)
    }
    Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Captioned("Now", Modifier.weight(1f)) { Thumb(image, Modifier.fillMaxWidth()) }
        Icon(EditorIcons.Arrow, contentDescription = null, tint = t.textMuted)
        Captioned(candidate.kind.label, Modifier.weight(1f)) {
            Box {
                Thumb(candidate.image, Modifier.fillMaxWidth())
                if (candidate.rendering) {
                    if (candidate.image == null) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center).size(Sizes.icon), strokeWidth = Sizes.progress)
                    } else {
                        LinearProgressIndicator(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(Sizes.progress))
                    }
                }
            }
        }
    }
    Row(Modifier.padding(vertical = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (kind in NewKind.entries) {
            FilterChip(
                selected = kind == candidate.kind,
                onClick = { actions.onTryNew(kind) },
                label = { Text(kind.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(onClick = { actions.onTryNew(candidate.kind) }, modifier = Modifier.weight(1f)) {
            Icon(EditorIcons.DieThree, contentDescription = null, tint = t.accent, modifier = Modifier.padding(end = Spacing.sm))
            Text("Try another")
        }
        Button(onClick = actions.onUseNew, enabled = candidate.image != null && !candidate.rendering, modifier = Modifier.weight(1f)) {
            Icon(EditorIcons.Check, contentDescription = null, modifier = Modifier.padding(end = Spacing.sm))
            Text("Use new")
        }
    }
}

@Composable
private fun Captioned(caption: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        content()
        Text(caption, style = MaterialTheme.typography.bodySmall, color = LocalTokens.current.textMuted)
    }
}

@Composable
private fun Thumb(image: ImageBitmap?, modifier: Modifier) {
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(Radius.md)).background(LocalTokens.current.canvas)) {
        if (image != null) Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}
