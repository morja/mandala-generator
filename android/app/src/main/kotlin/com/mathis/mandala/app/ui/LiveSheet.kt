package com.mathis.mandala.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mathis.mandala.app.LiveSettings
import com.mathis.mandala.app.R
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Spacing

/**
 * The live wallpaper (ADR-0014, Pro): the mandala turns slowly, as a whole or with its layers on their
 * own; breathing, colour drift and the palettes in turn can be added. Android shows a preview before it is set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveSheet(settings: LiveSettings, onSettings: (LiveSettings) -> Unit, onSet: () -> Unit, onDismiss: () -> Unit) {
    val t = LocalTokens.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.live_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.live_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = t.textMuted,
            )
            SwitchRow(stringResource(R.string.live_layered), settings.layered) { onSettings(settings.copy(layered = it)) }
            if (settings.layered) {
                Text(stringResource(R.string.live_layered_note), style = MaterialTheme.typography.bodySmall, color = t.textMuted)
            }
            SwitchRow(stringResource(R.string.live_breathing), settings.breathing) { onSettings(settings.copy(breathing = it)) }
            SwitchRow(stringResource(R.string.live_colour_drift), settings.colourDrift) { onSettings(settings.copy(colourDrift = it)) }
            SwitchRow(stringResource(R.string.live_palettes_in_turn), settings.palettesInTurn) { onSettings(settings.copy(palettesInTurn = it)) }
            Text(
                stringResource(R.string.live_daily_note),
                style = MaterialTheme.typography.bodySmall,
                color = t.textMuted,
            )
            Button(onClick = onSet, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.live_set)) }
        }
    }
}
