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
import com.mathis.mandala.app.LiveSettings
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Spacing

/**
 * The live wallpaper (ADR-0014, Pro): the mandala turns slowly; breathing, colour drift and the
 * palettes in turn can be added. Android shows a preview before it is set.
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
            Text("Live wallpaper", style = MaterialTheme.typography.titleMedium)
            Text(
                "This mandala fills the screen and turns slowly, one turn in four minutes. It only moves while you see it.",
                style = MaterialTheme.typography.bodyMedium,
                color = t.textMuted,
            )
            SwitchRow("Breathing: brightness rises and falls", settings.breathing) { onSettings(settings.copy(breathing = it)) }
            SwitchRow("Colour drift: the colours turn slowly", settings.colourDrift) { onSettings(settings.copy(colourDrift = it)) }
            SwitchRow("Palettes in turn: the next one fades in every ten minutes", settings.palettesInTurn) { onSettings(settings.copy(palettesInTurn = it)) }
            Text(
                "With the daily wallpaper on, the live wallpaper fades over to the next favourite every morning.",
                style = MaterialTheme.typography.bodySmall,
                color = t.textMuted,
            )
            Button(onClick = onSet, modifier = Modifier.fillMaxWidth()) { Text("Set live wallpaper") }
        }
    }
}
