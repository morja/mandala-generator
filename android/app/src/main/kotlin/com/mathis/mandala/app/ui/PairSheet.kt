package com.mathis.mandala.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.mathis.mandala.app.ExportProgress
import com.mathis.mandala.app.R
import com.mathis.mandala.app.PairPreview
import com.mathis.mandala.app.SaveChoice
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * The wallpaper pair (ADR-0014, Pro): the lock and the home screen side by side as they will look,
 * the mandala filling both, the home screen darker so its icons stay readable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PairSheet(
    preview: PairPreview,
    screenAspect: Double,
    homeBrightness: Double,
    busy: Boolean,
    export: ExportProgress?,
    onHomeBrightness: (Double) -> Unit,
    onSet: () -> Unit,
    onDismiss: () -> Unit,
    snackbar: SnackbarHostState,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Box {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(stringResource(R.string.pair_title), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    Screen(stringResource(R.string.pair_lock_screen), preview.lock, preview.rendering, screenAspect, Modifier.weight(1f))
                    Screen(stringResource(R.string.pair_home_screen), preview.home, preview.rendering, screenAspect, Modifier.weight(1f))
                }
                val percent = stringResource(R.string.editor_percent)
                ValueSlider(
                    stringResource(R.string.pair_home_screen),
                    homeBrightness,
                    SaveChoice.MIN_HOME_BRIGHTNESS.toFloat()..1f,
                    default = SaveChoice.DEFAULT_HOME_BRIGHTNESS,
                    display = { percent.format((it * 100).roundToInt()) },
                ) { if (!busy) onHomeBrightness(it) }
                Text(
                    stringResource(R.string.pair_lock_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalTokens.current.textMuted,
                )
                Button(onClick = onSet, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pair_set_both)) }
            }
            if (export != null) ExportNote(export, Modifier.align(Alignment.TopCenter)) else MessageHost(snackbar, Modifier.align(Alignment.TopCenter))
        }
    }
}

/** One phone screen of the pair, with its name below. */
@Composable
private fun Screen(label: String, image: ImageBitmap?, rendering: Boolean, aspect: Double, modifier: Modifier) {
    val t = LocalTokens.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(Modifier.fillMaxWidth().aspectRatio((1 / aspect).toFloat()).clip(RoundedCornerShape(Radius.md)).background(t.canvas)) {
            if (image != null) Image(image, contentDescription = stringResource(R.string.pair_screen_preview, label), contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            if (rendering) LinearProgressIndicator(Modifier.fillMaxWidth().height(Sizes.progress).align(Alignment.BottomCenter))
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = t.textMuted)
    }
}
