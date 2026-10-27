package com.mathis.mandala.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.mathis.mandala.app.PlayField
import com.mathis.mandala.app.PlayState
import com.mathis.mandala.app.R
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing

/**
 * The play field (UX-1 19, DM-20): the picture, and below it a field where one finger changes many
 * settings at once. "Landscape" lets every setting follow a wave of its own, "Compass" changes
 * light, shape, depth or colour by direction. Done or a swipe down closes it and keeps the mandala
 * as played; Back in the editor undoes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaySheet(
    state: PlayState,
    image: ImageBitmap?,
    onMode: (PlayField.Mode) -> Unit,
    onTouch: (Double, Double) -> Unit,
    onReset: () -> Unit,
    onAgain: () -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalTokens.current
    // Opens fully; the field takes its own touches, so only a swipe outside it closes the sheet.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxHeight().padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryTabRow(
                    selectedTabIndex = state.mode.ordinal, modifier = Modifier.weight(1f),
                    containerColor = BottomSheetDefaults.ContainerColor, divider = {},
                ) {
                    for ((mode, label) in listOf(PlayField.Mode.MAP to R.string.play_map, PlayField.Mode.COMPASS to R.string.play_compass)) {
                        Tab(selected = state.mode == mode, onClick = { onMode(mode) }) {
                            Text(stringResource(label), style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.padding(vertical = Spacing.md))
                        }
                    }
                }
                TextButton(onClick = onReset) { Text(stringResource(R.string.save_reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.play_done)) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(Radius.md)).background(t.canvas)) {
                    image?.let { Image(it, contentDescription = stringResource(R.string.editor_preview), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                }
            }
            PlayPad(state, onTouch)
            Text(
                stringResource(R.string.play_hint), style = MaterialTheme.typography.bodySmall, color = t.textMuted,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(colors = outlinedColors(), onClick = onAgain, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (state.mode == PlayField.Mode.MAP) R.string.play_new_map else R.string.play_from_here), maxLines = 1)
                }
                Button(onClick = onKeep, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.play_keep), maxLines = 1) }
            }
        }
    }
}

/** The field: one finger places the point and drags it. */
@Composable
private fun PlayPad(state: PlayState, onTouch: (Double, Double) -> Unit) {
    val t = LocalTokens.current
    val touch by rememberUpdatedState(onTouch)
    val shape = RoundedCornerShape(Radius.md)
    val description = stringResource(R.string.play_field_description)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(PAD_ASPECT)
            .clip(shape)
            .background(t.surface)
            .border(BorderStroke(OutlinedTextFieldDefaults.UnfocusedBorderThickness, t.outline), shape)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    touch(down.position.x.toDouble() / size.width, down.position.y.toDouble() / size.height)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val p = event.changes.firstOrNull { it.pressed }?.position ?: break
                        touch(p.x.toDouble() / size.width, p.y.toDouble() / size.height)
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        val w = maxWidth
        val h = maxHeight
        if (state.mode == PlayField.Mode.COMPASS) {
            val labels = listOf(
                R.string.play_light to Alignment.TopCenter, R.string.play_shape to Alignment.CenterEnd,
                R.string.play_depth to Alignment.BottomCenter, R.string.play_colour to Alignment.CenterStart,
            )
            for ((label, where) in labels) {
                Text(
                    stringResource(label), style = MaterialTheme.typography.labelMedium, color = t.textMuted,
                    modifier = Modifier.align(where).padding(Spacing.sm),
                )
            }
        }
        // The finger's point.
        Box(
            Modifier
                .offset(w * state.touch.x.toFloat() - Sizes.icon * 0.75f, h * state.touch.y.toFloat() - Sizes.icon * 0.75f)
                .size(Sizes.icon * 1.5f)
                .clip(CircleShape)
                .background(t.accentSoft)
                .border(BorderStroke(Sizes.swatchRing, t.accent), CircleShape),
        )
    }
}

/** Wider than tall, so picture and field both fit on a phone. */
private const val PAD_ASPECT = 1.3f
