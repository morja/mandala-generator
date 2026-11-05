package com.mathis.mandala.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import com.mathis.mandala.app.R
import com.mathis.mandala.app.Thumbnails
import com.mathis.mandala.app.WidgetChoice
import com.mathis.mandala.app.WidgetLook
import com.mathis.mandala.app.WidgetSettings
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import com.mathis.mandala.core.Favorite
import com.mathis.mandala.core.Quote

/** What the settings page of one widget shows; `favourites` is null while they are read. */
internal class WidgetPage(
    val size: String,
    val wide: Boolean,
    val favourites: List<Favorite>?,
    val dailyTurn: Favorite?,
    val pro: Boolean,
    val appLight: Boolean,
    val settings: WidgetSettings,
)

/**
 * One widget's settings (ADR-0020, 5 November 2026): a sketch of the widget, its colours, and its
 * mandala, the daily turn or one favourite from a grid. Every change applies at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetSettingsScreen(page: WidgetPage, words: (Favorite) -> Quote, onSettings: (WidgetSettings) -> Unit, onClose: () -> Unit) {
    val t = LocalTokens.current
    // A fixed favourite that was deleted counts as the daily turn, as on the widget.
    val fixed = page.settings.favouriteId?.takeIf { id -> page.favourites?.any { it.id == id } == true }
    val shown = fixed?.let { id -> page.favourites?.firstOrNull { it.id == id } } ?: page.dailyTurn
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_settings_title, page.size)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(EditorIcons.Close, contentDescription = stringResource(R.string.controls_close)) }
                },
            )
        },
    ) { padding ->
        // No wider than a small tablet, so the grid does not grow huge.
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = Spacing.twoPaneMinWidth).verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
            ) {
                WidgetSketch(shown, page.wide, light = WidgetChoice.isLight(page.settings.look, page.appLight), words)

                Heading(stringResource(R.string.widget_appearance))
                val looks = listOf(WidgetLook.APP to R.string.widget_look_app, WidgetLook.LIGHT to R.string.widget_look_light, WidgetLook.DARK to R.string.widget_look_dark)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    for ((i, choice) in looks.withIndex()) {
                        val (look, label) = choice
                        SegmentedButton(
                            selected = page.settings.look == look,
                            onClick = { onSettings(page.settings.copy(look = look)) },
                            shape = SegmentedButtonDefaults.itemShape(i, looks.size),
                            icon = {},
                        ) { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }

                Heading(stringResource(R.string.widget_mandala))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm))
                        .selectable(selected = fixed == null, role = Role.RadioButton) { onSettings(page.settings.copy(favouriteId = null)) }
                        .padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    RadioButton(selected = fixed == null, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.widget_daily), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(if (page.pro) R.string.widget_daily_pro else R.string.widget_daily_free),
                            style = MaterialTheme.typography.bodySmall, color = t.textMuted)
                    }
                }

                val favourites = page.favourites
                if (favourites != null && favourites.isEmpty()) {
                    Text(stringResource(R.string.widget_empty), style = MaterialTheme.typography.bodyMedium, color = t.textMuted,
                        modifier = Modifier.padding(top = Spacing.md))
                } else if (favourites != null) {
                    Text(stringResource(R.string.widget_fixed), style = MaterialTheme.typography.bodyMedium, color = t.textMuted,
                        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm))
                    FavouriteGrid(favourites, fixed) { onSettings(page.settings.copy(favouriteId = it)) }
                }
            }
        }
    }
}

/** A section title: more room above than below, so it belongs to what follows. */
@Composable
private fun Heading(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm))

/**
 * The widget in small, in its colours from `widget.xml`: the picture alone, or the picture with the
 * words beside it when the widget is wide. Made from the favourite's thumbnail.
 */
@Composable
private fun WidgetSketch(favourite: Favorite?, wide: Boolean, light: Boolean, words: (Favorite) -> Quote) {
    val surface = colorResource(if (light) R.color.widget_surface_light else R.color.widget_surface_dark)
    val text = colorResource(if (light) R.color.widget_text_light else R.color.widget_text_dark)
    val muted = colorResource(if (light) R.color.widget_muted_light else R.color.widget_muted_dark)
    val thumb: ImageBitmap? = remember(favourite?.id) { favourite?.let { Thumbnails.decode(it.thumbnailData) } }
    val shape = RoundedCornerShape(Radius.md)
    // A square widget is the picture alone; a wide one is as wide as the page.
    Row(
        Modifier.padding(top = Spacing.sm).then(if (wide) Modifier.fillMaxWidth() else Modifier).height(Sizes.favourite)
            .clip(shape).background(surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.fillMaxHeight().aspectRatio(1f)) {
            thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (favourite == null) {
                Text(stringResource(R.string.widget_empty), style = MaterialTheme.typography.labelSmall, color = muted,
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.sm))
            }
        }
        if (wide && favourite != null) {
            val quote = remember(favourite.id) { words(favourite) }
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(quote.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Serif, color = text,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (quote.author.isNotBlank()) {
                    Text("— " + quote.author, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The favourites in three columns with their names; tapping one fixes the widget on it. */
@Composable
private fun FavouriteGrid(favourites: List<Favorite>, fixed: String?, onPick: (String) -> Unit) {
    val t = LocalTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        for (row in favourites.chunked(COLUMNS)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                for (fav in row) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        FavoriteTile(fav, selected = fav.id == fixed, selecting = true, onApply = {}, onToggle = { onPick(fav.id) })
                        Text(favouriteName(fav), style = MaterialTheme.typography.bodySmall, color = if (fav.id == fixed) t.text else t.textMuted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A favourite's name; an unnamed one is called by its styles, as the menu calls the mandala. */
@Composable
internal fun favouriteName(fav: Favorite): String =
    fav.name.ifEmpty { fav.parameters.layers.filter { it.isEnabled }.map { stringResource(it.style.labelRes) }.joinToString(", ") }

private const val COLUMNS = 3
