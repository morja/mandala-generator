package com.mathis.mandala.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.mathis.mandala.app.ExportProgress
import com.mathis.mandala.app.R
import com.mathis.mandala.app.ui.theme.Spacing

/** Shows the editor's messages; a message can be swiped away to either side. */
@Composable
internal fun MessageHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        // A fresh swipe state for each message, so the next one does not start swiped away.
        key(data) {
            SwipeToDismissBox(
                state = rememberSwipeToDismissBoxState(),
                backgroundContent = {},
                onDismiss = { data.dismiss() },
            ) { Snackbar(data) }
        }
    }
}

/**
 * What a running export does and how far it is, where messages show and looking like one: the
 * step with its percentage, and a bar below that moves on its own while the share is not known.
 */
@Composable
internal fun ExportNote(progress: ExportProgress, modifier: Modifier = Modifier) {
    val content = SnackbarDefaults.contentColor
    Surface(
        modifier.padding(Spacing.md).fillMaxWidth(),
        shape = SnackbarDefaults.shape,
        color = SnackbarDefaults.color,
        contentColor = content,
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row {
                // Read out when the step changes, not at every percent.
                Text(
                    stringResource(R.string.editor_progress_step, progress.label),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
                progress.share?.let { Text(stringResource(R.string.editor_percent, (it * 100).toInt()), style = MaterialTheme.typography.bodyMedium) }
            }
            val bar = MaterialTheme.colorScheme.inversePrimary
            val track = content.copy(alpha = 0.24f)
            val share = progress.share
            if (share != null) {
                LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth(), color = bar, trackColor = track)
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = bar, trackColor = track)
            }
        }
    }
}
