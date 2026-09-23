package tk.glucodata.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DIAL_START_ANGLE = 135f
private const val DIAL_SWEEP_TOTAL = 270f

/**
 * Spotify-style circular dial for Wear Quick log.
 *
 * - Crown (rotary encoder) adjusts [value] by [step].
 * - Touch: spin the ring with a circular drag gesture.
 * - Visual: 270-degree progress ring with a knob, themed with Material3 tokens.
 */
@Composable
fun WearQuickLogDial(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onValueChange: (Float) -> Unit,
    displayText: String,
    labelText: String,
    modifier: Modifier = Modifier,
    contentDescription: String = labelText
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
    val progressColor = MaterialTheme.colorScheme.primary
    val knobOuter = MaterialTheme.colorScheme.primary
    val knobInner = MaterialTheme.colorScheme.onPrimary
    val valueColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val density = LocalDensity.current
    val strokePx = with(density) { 10.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .size(168.dp)
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = displayText
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

        fun angleOf(position: Offset): Float {
            val dx = position.x - centerPx.x
            val dy = position.y - centerPx.y
            return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()).toFloat().toDouble()).toFloat()
        }

        Box(
            modifier = Modifier
                .size(168.dp)
                .pointerInput(step, range, centerPx, degreesPerStep) {
                    var accAngle = 0f
                    var prevAngle = Float.NaN
                    detectDragGestures(
                        onDragStart = { offset ->
                            prevAngle = angleOf(offset)
                            accAngle = 0f
                        },
                        onDragEnd = {
                            prevAngle = Float.NaN
                            accAngle = 0f
                        },
                        onDragCancel = {
                            prevAngle = Float.NaN
                            accAngle = 0f
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (prevAngle.isNaN()) {
                                prevAngle = angleOf(change.position)
                                return@detectDragGestures
                            }
                            val current = angleOf(change.position)
                            var delta = current - prevAngle
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            prevAngle = current
                            accAngle += delta
                            val steps = (accAngle / degreesPerStep).toInt()
                            if (steps != 0) {
                                accAngle -= steps * degreesPerStep
                                val next = quantize(latestValue + steps * step)
                                if (next != latestValue) {
                                    latestOnChange(next)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val fraction = ((value - range.start) / (range.endInclusive - range.start))
                .coerceIn(0f, 1f)

            Canvas(modifier = Modifier.size(168.dp)) {
                val diameter = size.minDimension
                val ringRadius = diameter / 2f - strokePx / 2f - 8.dp.toPx()
                val arcTopLeft = Offset(
                    center.x - ringRadius,
                    center.y - ringRadius
                )
                val arcSize = androidx.compose.ui.geometry.Size(ringRadius * 2f, ringRadius * 2f)

                // Track
                drawArc(
                    color = trackColor,
                    startAngle = DIAL_START_ANGLE,
                    sweepAngle = DIAL_SWEEP_TOTAL,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                // Progress
                if (fraction > 0f) {
                    drawArc(
                        color = progressColor,
                        startAngle = DIAL_START_ANGLE,
                        sweepAngle = DIAL_SWEEP_TOTAL * fraction,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
                // Knob at the tip of the progress arc
                val knobAngleDeg = DIAL_START_ANGLE + DIAL_SWEEP_TOTAL * fraction
                val knobAngleRad = Math.toRadians(knobAngleDeg.toDouble())
                val knobCenter = Offset(
                    x = center.x + ringRadius * cos(knobAngleRad).toFloat(),
                    y = center.y + ringRadius * sin(knobAngleRad).toFloat()
                )
                drawCircle(
                    color = knobOuter,
                    radius = 9.dp.toPx(),
                    center = knobCenter
                )
                drawCircle(
                    color = knobInner,
                    radius = 3.5.dp.toPx(),
                    center = knobCenter
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = displayText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = valueColor,
                    maxLines = 1
                )
                Text(
                    text = labelText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = labelColor,
                    maxLines = 1
                )
            }
        }
    }
}
