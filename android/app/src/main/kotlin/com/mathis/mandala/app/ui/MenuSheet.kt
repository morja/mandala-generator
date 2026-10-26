package com.mathis.mandala.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.mathis.mandala.app.Candidate
import com.mathis.mandala.app.NewKind
import com.mathis.mandala.app.R
import com.mathis.mandala.app.WallpaperTarget
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.MandalaParameters
import com.mathis.mandala.core.VectorExport

/** What the menu sheet can do; the screen wires each to the view model or a system dialog. */
internal class MenuActions(
    val onFavourite: () -> Unit,
    val onShare: () -> Unit,
    val onWallpaper: (WallpaperTarget) -> Unit,
    /** Opens the pair sheet, or the Pro sheet without Pro; `pairLabel` says which. */
    val onPair: () -> Unit,
    val pairLabel: String,
    val onLive: () -> Unit,
    val liveLabel: String,
    /** The daily wallpaper (ADR-0014, Pro): whether it is on, what the line below says, and the switch. */
    val dailyOn: Boolean,
    val dailySubtitle: String,
    val onDaily: (Boolean) -> Unit,
    /** Which favourites take turns (null: all), chosen on a page of their own. */
    val favourites: List<Favorite>,
    val dailyChosen: Set<String>?,
    val onToggleDaily: (String) -> Unit,
    val onChooseAllDaily: () -> Unit,
    /** The line drawing as SVG (ADR-0013, Pro): without Pro its looks open the Pro sheet. */
    val lineDrawingPro: Boolean,
    val hasPointLayers: Boolean,
    val onLineDrawing: (VectorExport.Look) -> Unit,
    val onTryNew: (NewKind) -> Unit,
    val onUseNew: () -> Unit,
    val onBackToMenu: () -> Unit,
    val onOpen: () -> Unit,
    val onSaveSettings: () -> Unit,
    val onFastPreview: (Boolean) -> Unit,
    val onLightTheme: (Boolean) -> Unit,
    val onAbout: () -> Unit,
)

/** The pages of the menu: the main one, and those with room to explain what an entry does. */
private enum class MenuPage { MAIN, LINE_DRAWING, DAILY, DAILY_FAVOURITES, MORE }

/**
 * The menu as a sheet from below (DM-7 B): the mandala on screen with what keeps it, three dice for a
 * new one, then the line drawing, "More", the theme and About. A die shows its new mandala next to the current one
 * in the same sheet; only "Use new" replaces it. The die in the top bar stays the quick way, without
 * asking. The rarer entries (files, fast preview) and the daily wallpaper have pages of their
 * own, each with a few lines on what it does, so the main page stays short.
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
    var page by rememberSaveable { mutableStateOf(MenuPage.MAIN) }
    val back = { page = MenuPage.MAIN }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl)) {
            when {
                candidate != null -> Compare(image, candidate, actions)
                page == MenuPage.LINE_DRAWING -> LineDrawingPage(busy, actions, back)
                page == MenuPage.DAILY -> DailyPage(actions, back, onChoose = { page = MenuPage.DAILY_FAVOURITES })
                page == MenuPage.DAILY_FAVOURITES -> DailyFavouritesPage(actions, onBack = { page = MenuPage.DAILY })
                page == MenuPage.MORE -> MorePage(fastPreview, actions, back)
                else -> {
                    Current(params, image)
                    KeepRow(busy, actions, onDailyPage = { page = MenuPage.DAILY })
                    NewMandala(actions.onTryNew)
                    MainRows(lightTheme, actions, onPage = { page = it })
                }
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
                layers.map { stringResource(it.style.labelRes) }.joinToString(", ").ifEmpty { stringResource(R.string.menu_no_layers) },
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(
                    if (params.emblemLayer.isEnabled) R.plurals.menu_layers_emblem_on else R.plurals.menu_layers_emblem_off,
                    layers.size,
                    layers.size,
                ),
                style = MaterialTheme.typography.bodySmall, color = t.textMuted,
            )
        }
    }
}

@Composable
private fun KeepRow(busy: Boolean, actions: MenuActions, onDailyPage: () -> Unit) {
    Row(Modifier.padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Pill(EditorIcons.StarLine, stringResource(R.string.menu_favourite), onClick = actions.onFavourite)
        Pill(EditorIcons.Share, stringResource(R.string.menu_share), enabled = !busy, onClick = actions.onShare)
        var open by remember { mutableStateOf(false) }
        Box(Modifier.weight(1f)) {
            PillContent(EditorIcons.Wallpaper, stringResource(R.string.menu_wallpaper), !busy, { open = true }, Modifier.fillMaxWidth())
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (target in WallpaperTarget.entries) {
                    DropdownMenuItem(text = { Text(stringResource(target.label)) }, onClick = { open = false; actions.onWallpaper(target) })
                }
                DropdownMenuItem(text = { Text(actions.pairLabel) }, onClick = { open = false; actions.onPair() })
                DropdownMenuItem(text = { Text(actions.liveLabel) }, onClick = { open = false; actions.onLive() })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_daily_wallpaper)) },
                    trailingIcon = { if (actions.dailyOn) Icon(EditorIcons.Check, contentDescription = null, tint = LocalTokens.current.accent) },
                    onClick = { open = false; onDailyPage() },
                )
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

private class DieChoice(val kind: NewKind, val icon: ImageVector, @StringRes val title: Int, @StringRes val detail: Int, val steps: Int)

private val dice = listOf(
    DieChoice(NewKind.VARIATION, EditorIcons.DieOne, R.string.menu_die_variation, R.string.menu_die_variation_detail, 1),
    DieChoice(NewKind.LAYERS, EditorIcons.DieThree, R.string.menu_die_layers, R.string.menu_die_layers_detail, 2),
    DieChoice(NewKind.EVERYTHING, EditorIcons.DieFive, R.string.menu_die_everything, R.string.menu_die_everything_detail, 3),
)

@Composable
private fun NewMandala(onTryNew: (NewKind) -> Unit) {
    val t = LocalTokens.current
    Text(stringResource(R.string.menu_new_mandala), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (die in dice) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(Radius.md)).background(t.surfaceRaised).clickable { onTryNew(die.kind) }
                    .padding(horizontal = Spacing.xs, vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(die.icon, contentDescription = null, tint = t.accent)
                Text(stringResource(die.title), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
                Text(stringResource(die.detail), style = MaterialTheme.typography.bodySmall, color = t.textMuted, maxLines = 1)
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

/** A menu line with an icon, a title, a line below it and a switch; the whole line switches. */
@Composable
private fun SwitchMenuRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = t.textMuted)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.textMuted)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun MainRows(lightTheme: Boolean, actions: MenuActions, onPage: (MenuPage) -> Unit) {
    val t = LocalTokens.current
    Column(Modifier.padding(top = Spacing.md)) {
        HorizontalDivider(color = t.outline)
        MenuRow(EditorIcons.LineArt, stringResource(if (actions.lineDrawingPro) R.string.save_line_drawing else R.string.save_line_drawing_pro),
            onClick = { onPage(MenuPage.LINE_DRAWING) }) { Chevron() }
        HorizontalDivider(color = t.outline)
        MenuRow(EditorIcons.Tune, stringResource(R.string.menu_more), onClick = { onPage(MenuPage.MORE) }) { Chevron() }
        HorizontalDivider(color = t.outline)
        // On the main page, since it is switched often, e.g. outdoors or at night.
        MenuRow(EditorIcons.Moon, stringResource(R.string.menu_theme), onClick = null) {
            ThemeChoice(lightTheme, actions.onLightTheme)
        }
        HorizontalDivider(color = t.outline)
        GuideRow()
        HorizontalDivider(color = t.outline)
        MenuRow(EditorIcons.Info, stringResource(R.string.menu_about), onClick = actions.onAbout) { Chevron() }
    }
}

/** The guide on the website: German pages for German, English for every other language. */
@Composable
private fun GuideRow() {
    val uriHandler = LocalUriHandler.current
    val language = if (LocalConfiguration.current.locales[0].language == "de") "de" else "en"
    MenuRow(EditorIcons.Help, stringResource(R.string.menu_guide), onClick = { uriHandler.openUri("https://www.tearoom.one/$language/apps/dahlia/help") }) { Chevron() }
}

/** A page's top: back to the menu, its title, and a few lines on what it is for. */
@Composable
private fun PageTop(title: String, about: String?, onBack: () -> Unit) {
    BackRow(onBack)
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.sm))
    if (about != null) {
        Text(about, style = MaterialTheme.typography.bodyMedium, color = LocalTokens.current.textMuted, modifier = Modifier.padding(top = Spacing.xs))
    }
}

@Composable
private fun LineDrawingPage(busy: Boolean, actions: MenuActions, onBack: () -> Unit) {
    val t = LocalTokens.current
    val about = stringResource(R.string.save_line_drawing_detail) +
        if (actions.hasPointLayers) " " + stringResource(R.string.save_line_drawing_points) else ""
    PageTop(stringResource(if (actions.lineDrawingPro) R.string.save_line_drawing else R.string.save_line_drawing_pro), about, onBack)
    Column(Modifier.padding(top = Spacing.md)) {
        for ((label, look) in listOf(R.string.save_line_drawing_colour to VectorExport.Look.COLOUR, R.string.save_line_drawing_lines to VectorExport.Look.LINES)) {
            HorizontalDivider(color = t.outline)
            MenuRow(EditorIcons.FileSave, stringResource(label), onClick = if (busy) null else ({ actions.onLineDrawing(look) })) { Chevron() }
        }
    }
}

@Composable
private fun DailyPage(actions: MenuActions, onBack: () -> Unit, onChoose: () -> Unit) {
    val t = LocalTokens.current
    PageTop(stringResource(R.string.menu_daily_wallpaper), stringResource(R.string.menu_daily_about), onBack)
    Column(Modifier.padding(top = Spacing.md)) {
        HorizontalDivider(color = t.outline)
        // The page title names it already; the line says what is set now.
        SwitchMenuRow(EditorIcons.Wallpaper, actions.dailySubtitle, null, actions.dailyOn, actions.onDaily)
        if (actions.favourites.isNotEmpty()) {
            HorizontalDivider(color = t.outline)
            val count = actions.favourites.count { actions.dailyChosen == null || it.id in actions.dailyChosen }
            MenuRow(EditorIcons.StarLine, stringResource(R.string.menu_daily_favourites),
                detail = if (actions.dailyChosen == null) stringResource(R.string.menu_daily_all)
                    else stringResource(R.string.menu_daily_some, count, actions.favourites.size),
                onClick = onChoose) { Chevron() }
        }
        HorizontalDivider(color = t.outline)
        Text(stringResource(R.string.menu_daily_widget_note), style = MaterialTheme.typography.bodySmall, color = t.textMuted,
            modifier = Modifier.padding(top = Spacing.md))
    }
}

/** The favourites as a grid with ticks: only the ticked ones take turns; all are ticked at first. */
@Composable
private fun DailyFavouritesPage(actions: MenuActions, onBack: () -> Unit) {
    PageTop(stringResource(R.string.menu_daily_favourites), stringResource(R.string.menu_daily_pick_about), onBack)
    // Always there, so the grid does not move under the finger when the first tick goes.
    TextButton(onClick = actions.onChooseAllDaily, enabled = actions.dailyChosen != null) { Text(stringResource(R.string.menu_daily_choose_all)) }
    // The menu scrolls as a whole, so plain rows instead of a lazy grid.
    Column(Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (row in actions.favourites.chunked(DAILY_COLUMNS)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                for (fav in row) {
                    Box(Modifier.weight(1f)) {
                        FavoriteTile(fav, selected = actions.dailyChosen == null || fav.id in actions.dailyChosen, selecting = true,
                            onApply = {}, onToggle = { actions.onToggleDaily(fav.id) })
                    }
                }
                repeat(DAILY_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val DAILY_COLUMNS = 3

@Composable
private fun MorePage(fastPreview: Boolean, actions: MenuActions, onBack: () -> Unit) {
    val t = LocalTokens.current
    PageTop(stringResource(R.string.menu_more), null, onBack)
    Column(Modifier.padding(top = Spacing.sm)) {
        HorizontalDivider(color = t.outline)
        MenuRow(EditorIcons.Folder, stringResource(R.string.menu_open_file), stringResource(R.string.menu_open_file_detail), onClick = actions.onOpen) { Chevron() }
        HorizontalDivider(color = t.outline)
        MenuRow(EditorIcons.FileSave, stringResource(R.string.menu_save_settings), stringResource(R.string.menu_save_settings_detail), onClick = actions.onSaveSettings) { Chevron() }
        HorizontalDivider(color = t.outline)
        SwitchMenuRow(EditorIcons.Bolt, stringResource(R.string.menu_fast_preview), stringResource(R.string.menu_fast_preview_detail), fastPreview, actions.onFastPreview)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, detail: String? = null, onClick: (() -> Unit)?, trailing: @Composable () -> Unit) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = t.textMuted)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = t.textMuted)
        }
        trailing()
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
    val t = LocalTokens.current
    Row(
        Modifier.clip(RoundedCornerShape(Radius.sm)).clickable(onClick = onBack).padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EditorIcons.ChevronBack, contentDescription = null, tint = t.textMuted)
        Text(stringResource(R.string.menu_back), style = MaterialTheme.typography.bodyMedium, color = t.textMuted)
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
        for ((label, isLight) in listOf(R.string.menu_theme_dark to false, R.string.menu_theme_light to true)) {
            val on = light == isLight
            Text(
                stringResource(label),
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
    BackRow(actions.onBackToMenu)
    Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Captioned(stringResource(R.string.menu_now), Modifier.weight(1f)) { Thumb(image, Modifier.fillMaxWidth()) }
        Icon(EditorIcons.Arrow, contentDescription = null, tint = t.textMuted)
        Captioned(stringResource(candidate.kind.label), Modifier.weight(1f)) {
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
                label = { Text(stringResource(kind.label), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(colors = outlinedColors(), onClick = { actions.onTryNew(candidate.kind) }, modifier = Modifier.weight(1f)) {
            Icon(EditorIcons.DieThree, contentDescription = null, tint = t.accent, modifier = Modifier.padding(end = Spacing.sm))
            Text(stringResource(R.string.menu_try_another))
        }
        Button(onClick = actions.onUseNew, enabled = candidate.image != null && !candidate.rendering, modifier = Modifier.weight(1f)) {
            Icon(EditorIcons.Check, contentDescription = null, modifier = Modifier.padding(end = Spacing.sm))
            Text(stringResource(R.string.menu_use_new))
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
