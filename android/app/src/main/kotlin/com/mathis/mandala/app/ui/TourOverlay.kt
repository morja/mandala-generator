package com.mathis.mandala.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.toSize
import com.mathis.mandala.app.R
import com.mathis.mandala.app.TourLayout
import com.mathis.mandala.app.TourState
import com.mathis.mandala.app.TourStep
import com.mathis.mandala.app.TourTarget
import com.mathis.mandala.app.ui.theme.LocalTokens
import com.mathis.mandala.app.ui.theme.Radius
import com.mathis.mandala.app.ui.theme.Sizes
import com.mathis.mandala.app.ui.theme.Spacing
import com.mathis.mandala.core.MandalaStyle

/**
 * Where the tour's targets are on screen, reported by the composables themselves (`tourTarget`), so
 * the rings fit every screen size, font scale and language. Also carries the running step to the
 * controls, which open the step's tab and scroll its row into place, and then mark the step settled.
 */
@Stable
internal class TourTargets {
    /** In root coordinates; only filled while a tour runs. */
    val bounds = mutableStateMapOf<TourTarget, Rect>()

    /** The running step; null while no tour runs. */
    var step by mutableStateOf<TourStep?>(null)

    /** The step whose tab and scroll position are in place; its rings show only then. */
    var settled by mutableStateOf<TourStep?>(null)
}

internal val LocalTourTargets = compositionLocalOf<TourTargets?> { null }

/** Reports this composable's place on screen as `target` while a tour runs. */
internal fun Modifier.tourTarget(target: TourTarget): Modifier = composed {
    val tour = LocalTourTargets.current
    if (tour?.step == null) return@composed Modifier
    DisposableEffect(tour, target) { onDispose { tour.bounds.remove(target) } }
    Modifier.onGloballyPositioned { c ->
        val r = Rect(c.positionInRoot(), c.size.toSize())
        if (tour.bounds[target] != r) tour.bounds[target] = r
    }
}

/** How dark the dimming is; the dark theme needs more to set the card apart (DM-22). */
private const val SCRIM_ALPHA_LIGHT = 0.42f
private const val SCRIM_ALPHA_DARK = 0.62f

/** Icons get a round ring; rows and tabs follow their own shape. */
private val roundTargets = setOf(TourTarget.PLAY, TourTarget.FAVOURITES, TourTarget.SAVE, TourTarget.MORE, TourTarget.TOP_DIE, TourTarget.LAYER_DIE)

/** A ring in the overlay's coordinates. */
private class Ring(val rect: Rect, val round: Boolean, val line: Boolean)

/**
 * The tour over the whole editor (DM-22, card a): everything but the picture and the step's targets
 * is dimmed, a ring marks each target and a thin line leads from it to the card docked at the bottom.
 * Touches outside the card do nothing; Back on the phone ends the tour like Skip.
 */
@Composable
internal fun TourOverlay(
    state: TourState,
    tour: TourTargets,
    wide: Boolean,
    darkTheme: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val t = LocalTokens.current
    val density = LocalDensity.current
    BackHandler(onBack = onSkip)

    // The overlay's own place in the root, so the reported bounds can be moved into its coordinates.
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Written while placing the card, read when drawing the lines.
    var cardTop by remember { mutableFloatStateOf(Float.NaN) }
    var cardBottom by remember { mutableFloatStateOf(Float.NaN) }
    var cardLeft by remember { mutableFloatStateOf(0f) }
    var cardRight by remember { mutableFloatStateOf(0f) }
    // Kept while the controls scroll to the next row, so the card does not jump with it.
    val lastTop = remember { floatArrayOf(Float.NaN) }

    val step = state.step
    val settled = tour.settled == step
    val iconRing = with(density) { (Sizes.icon / 2 + Spacing.sm).toPx() }
    val rowInset = with(density) { Sizes.swatchRing.toPx() }
    fun local(r: Rect) = r.translate(-origin)
    val rings = if (!settled) emptyList() else step.marks.mapNotNull { mark ->
        tour.bounds[mark.target]?.let { b ->
            val r = local(b)
            val round = mark.target in roundTargets
            Ring(if (round) Rect(r.center, iconRing) else r.inflate(rowInset), round, mark.line)
        }
    }
    val open = if (!settled) emptyList() else step.open.mapNotNull { tour.bounds[it]?.let(::local) }
    val picture = tour.bounds[TourTarget.PICTURE]?.let(::local)

    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = if (darkTheme) SCRIM_ALPHA_DARK else SCRIM_ALPHA_LIGHT)
    val paneTitle = stringResource(R.string.tour_pane)

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            // Takes every touch outside the card, so nothing behind it changes during the tour.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .semantics { this.paneTitle = paneTitle },
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            val corner = CornerRadius(Radius.md.toPx())
            drawRect(scrim)

            // Holes: the picture, the targets and what the step leaves open.
            picture?.let { drawRoundRect(Color.Black, it.topLeft, it.size, corner, blendMode = BlendMode.Clear) }
            for (r in open) drawRoundRect(Color.Black, r.topLeft, r.size, corner, blendMode = BlendMode.Clear)
            for (ring in rings) {
                if (ring.round) drawCircle(Color.Black, ring.rect.width / 2, ring.rect.center, blendMode = BlendMode.Clear)
                else drawRoundRect(Color.Black, ring.rect.topLeft, ring.rect.size, corner, blendMode = BlendMode.Clear)
            }

            // Rings and the lines from them to the card.
            val width = Sizes.swatchRing.toPx()
            for (ring in rings) {
                if (ring.round) drawCircle(t.accent, ring.rect.width / 2 - width / 2, ring.rect.center, style = Stroke(width))
                else drawRoundRect(t.accent, ring.rect.topLeft, ring.rect.size, corner, style = Stroke(width))
                if (!ring.line || cardTop.isNaN()) continue
                val x = ring.rect.center.x
                if (x < cardLeft || x > cardRight) continue
                val (from, to) = if (cardTop >= ring.rect.bottom) ring.rect.bottom to cardTop else cardBottom to ring.rect.top
                if (to - from >= Spacing.lg.toPx()) drawLine(t.accent, Offset(x, from), Offset(x, to), strokeWidth = width)
            }
        }

        val insets = WindowInsets.safeDrawing
        Layout(
            content = { TourCard(state, onNext, onBack, onSkip) },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val margin = Spacing.md.roundToPx()
            val full = constraints.maxWidth - 2 * margin
            // On wide screens the card stays the width of the controls, at their side.
            val width = if (wide) minOf(full, Spacing.controlsWidth.roundToPx()) else full
            val card = measurables.first().measure(Constraints.fixedWidth(width.coerceAtLeast(0)))
            val top = (insets.getTop(this) + margin).toFloat()
            val bottom = (constraints.maxHeight - insets.getBottom(this) - margin).toFloat()
            val y = if (settled || lastTop[0].isNaN()) {
                val spans = rings.map { TourLayout.Span(it.rect.top, it.rect.bottom) }
                TourLayout.cardTop(spans, card.height.toFloat(), top, bottom, Spacing.sm.toPx()).also { lastTop[0] = it }
            } else {
                lastTop[0]
            }
            val x = if (wide) constraints.maxWidth - margin - width else margin
            cardTop = y
            cardBottom = y + card.height
            cardLeft = x.toFloat()
            cardRight = (x + width).toFloat()
            layout(constraints.maxWidth, constraints.maxHeight) { card.place(x, y.toInt()) }
        }
    }
}

/** The card: title and step counter, one or two sentences, and the buttons. */
@Composable
private fun TourCard(state: TourState, onNext: () -> Unit, onBack: () -> Unit, onSkip: () -> Unit) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(Radius.md)
    val step = state.step
    Column(
        Modifier
            .clip(shape)
            .background(t.surfaceRaised)
            .border(Sizes.swatchRing / 2, t.outline, shape)
            // Swallows taps between the buttons, so they do not reach the dimming behind.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.sm)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row {
            Text(stringResource(step.title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).alignByBaseline())
            Text(
                stringResource(R.string.tour_count, state.number, TourState.count),
                style = MaterialTheme.typography.bodySmall,
                color = t.textMuted,
                maxLines = 1,
                modifier = Modifier.padding(start = Spacing.sm).alignByBaseline(),
            )
        }
        Text(bodyText(step), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.xs))
        if (step == TourStep.DIE) DiePlaces()
        TourButtons(
            skip = if (state.isLast) null else ({
                TextButton(onClick = onSkip, colors = ButtonDefaults.textButtonColors(contentColor = t.textMuted)) {
                    Text(stringResource(R.string.tour_skip), maxLines = 1)
                }
            }),
        ) {
            if (!state.isFirst) {
                TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = t.textMuted)) {
                    Text(stringResource(R.string.tour_back), maxLines = 1)
                }
            }
            Button(onClick = onNext, modifier = Modifier.padding(start = Spacing.xs)) {
                Text(stringResource(if (state.isLast) R.string.tour_done else R.string.tour_next), maxLines = 1)
            }
        }
    }
}

/** The step's sentences; the style step names two real styles in the app's language. */
@Composable
private fun bodyText(step: TourStep): String = when (step) {
    TourStep.STYLES -> stringResource(step.body, stringResource(MandalaStyle.SPIROGRAPH.labelRes), stringResource(MandalaStyle.ROSE_CURVES.labelRes))
    else -> stringResource(step.body)
}

/** The other places with a die, as the die each shows with its name; the two in view carry rings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiePlaces() {
    val t = LocalTokens.current
    val places: List<Pair<ImageVector, String>> = listOf(
        EditorIcons.DieOne to stringResource(R.string.tour_place_effects),
        EditorIcons.DieOne to stringResource(R.string.controls_tab_emblem),
        EditorIcons.DieOne to stringResource(R.string.controls_tab_text),
        EditorIcons.DieThree to stringResource(R.string.tour_place_menu),
        EditorIcons.DieOne to stringResource(R.string.tour_place_play),
    )
    FlowRow(
        Modifier.padding(top = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        for ((icon, label) in places) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.padding(horizontal = Spacing.xs)) {
                Icon(icon, contentDescription = null, tint = t.accent, modifier = Modifier.size(Sizes.icon))
                Text(label, style = MaterialTheme.typography.bodySmall, color = t.textMuted, maxLines = 1)
            }
        }
    }
}

/**
 * Skip on the left, Back and Next on the right, in one line when they fit. Otherwise (long words at a
 * large font) Back and Next keep the line and Skip goes below them, so no label wraps or shrinks.
 * Skip moves left by the text button's padding, so its word lines up with the text above.
 */
@Composable
private fun TourButtons(skip: (@Composable () -> Unit)?, actions: @Composable () -> Unit) {
    Layout(
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
            skip?.invoke()
        },
        modifier = Modifier.padding(top = Spacing.sm),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val row = measurables[0].measure(loose)
        val skipButton = measurables.getOrNull(1)?.measure(loose)
        val shift = Spacing.md.roundToPx()
        val width = constraints.maxWidth
        val oneLine = skipButton == null || skipButton.width - shift + row.width <= width
        val height = if (oneLine) maxOf(row.height, skipButton?.height ?: 0) else row.height + skipButton!!.height
        layout(width, height) {
            row.place(width - row.width, (height - row.height).let { if (oneLine) it / 2 else 0 })
            skipButton?.place(-shift, if (oneLine) (height - skipButton.height) / 2 else row.height)
        }
    }
}
