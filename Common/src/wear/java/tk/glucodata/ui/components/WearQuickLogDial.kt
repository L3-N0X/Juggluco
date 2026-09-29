package tk.glucodata.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Circular dial for Wear Quick log, drawn along the edge of the screen.
 *
 * - Crown (rotary encoder) adjusts [value] by [step].
 * - Touch: spin the ring with a circular drag gesture that starts on the ring.
 * - Visual: progress ring with a knob, open at the bottom ([sweepAngle] < 360) so an edge
 *   button fits in the gap. Everything else on the screen goes in [content], inside the ring.
 */
@Composable
fun WearQuickLogDial(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onValueChange: (Float) -> Unit,
    stateDescription: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    sweepAngle: Float = 270f,
    content: @Composable BoxScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {
        }
    }

    // Always use the latest callbacks inside gesture scopes.
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)

    fun quantize(raw: Float): Float {
        if (step <= 0f) return raw.coerceIn(range.start, range.endInclusive)
        val steps = ((raw - range.start) / step).roundToInt()
        return (range.start + steps * step).coerceIn(range.start, range.endInclusive)
    }

    fun bump(direction: Int) {
        if (direction == 0) return
        val next = quantize(latestValue + direction * step)
        if (next != latestValue) {
            latestOnChange(next)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val totalSteps = ((range.endInclusive - range.start) / step).coerceAtLeast(1f)
    // Aim for roughly 3 full revolutions across the whole range so touch spin
    // feels fast without being twitchy. Clamped to a sensible per-step angle.
    val degreesPerStep = (3f * 360f / totalSteps).coerceIn(4f, 25f)

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val knobInner = MaterialTheme.colorScheme.surfaceContainerLow
    val startAngle = 90f + (360f - sweepAngle) / 2f

    val density = LocalDensity.current
    val strokePx = with(density) { 8.dp.toPx() }
    val knobRadiusPx = with(density) { 9.dp.toPx() }
    // The knob must stay on screen, so the ring sits just inside the edge by the knob's radius.
    val ringInsetPx = knobRadiusPx + with(density) { 1.dp.toPx() }
    // Touches this far inside the ring still grab it; anything further in belongs to [content].
    val grabBandPx = with(density) { 22.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateDescription
            }
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                if (event.verticalScrollPixels > 0f) {
                    bump(1)
                } else if (event.verticalScrollPixels < 0f) {
                    bump(-1)
                }
                true
            },
        contentAlignment = Alignment.Center
    ) {
        val centerPx = with(density) {
            Offset(
                x = maxWidth.toPx() / 2f,
                y = maxHeight.toPx() / 2f
            )
        }
        val ringRadiusPx = with(density) { minOf(maxWidth, maxHeight).toPx() } / 2f - ringInsetPx

        fun angleOf(position: Offset): Float {
            val dx = position.x - centerPx.x
            val dy = position.y - centerPx.y
            return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(step, range, centerPx, ringRadiusPx, degreesPerStep) {
                    val touchSlopPx = with(density) { 8.dp.toPx() }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downRadiusPx = kotlin.math.hypot(
                            down.position.x - centerPx.x,
                            down.position.y - centerPx.y
                        )
                        val startedOnRing = downRadiusPx >= ringRadiusPx - grabBandPx
                        if (!startedOnRing) return@awaitEachGesture
                        focusRequester.requestFocus()
                        var totalDistance = 0f
                        var totalAngle = 0f
                        var isAdjusting = false
                        var accAngle = 0f
                        var previousPosition = down.position

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            val current = angleOf(change.position)
                            var angleDelta = current - angleOf(previousPosition)
                            if (angleDelta > 180f) angleDelta -= 360f
                            if (angleDelta < -180f) angleDelta += 360f

                            totalDistance += kotlin.math.hypot(dx, dy)
                            totalAngle += angleDelta
                            previousPosition = change.position

                            val angularDistance = Math.toRadians(kotlin.math.abs(totalAngle).toDouble())
                                .toFloat() * ringRadiusPx
                            if (!isAdjusting &&
                                totalDistance > touchSlopPx &&
                                angularDistance > totalDistance * 0.6f
                            ) {
                                isAdjusting = true
                                accAngle = totalAngle
                            } else if (isAdjusting) {
                                accAngle += angleDelta
                            }

                            if (isAdjusting) {
                                val steps = (accAngle / degreesPerStep).toInt()
                                if (steps != 0) {
                                    accAngle -= steps * degreesPerStep
                                    val next = quantize(latestValue + steps * step)
                                    if (next != latestValue) {
                                        latestOnChange(next)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                change.consume()
                            }
                        } while (change.pressed)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val fraction = ((value - range.start) / (range.endInclusive - range.start))
                .coerceIn(0f, 1f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val arcTopLeft = Offset(
                    center.x - ringRadiusPx,
                    center.y - ringRadiusPx
                )
                val arcSize = Size(ringRadiusPx * 2f, ringRadiusPx * 2f)

                // Track
                drawArc(
                    color = trackColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                // Progress
                if (fraction > 0f) {
                    drawArc(
                        color = accentColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle * fraction,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
                // Knob at the tip of the progress arc
                val knobAngleRad = Math.toRadians((startAngle + sweepAngle * fraction).toDouble())
                val knobCenter = Offset(
                    x = center.x + ringRadiusPx * cos(knobAngleRad).toFloat(),
                    y = center.y + ringRadiusPx * sin(knobAngleRad).toFloat()
                )
                drawCircle(
                    color = accentColor,
                    radius = knobRadiusPx,
                    center = knobCenter
                )
                drawCircle(
                    color = knobInner,
                    radius = 3.5.dp.toPx(),
                    center = knobCenter
                )
            }

            content()
        }
    }
}
