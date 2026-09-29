package tk.glucodata.ui.graph

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tk.glucodata.ui.model.TimeRange
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/**
 * Visible time window of the glucose graph.
 *
 * The window is kept in plain snapshot state so that the graph can read it from the *draw* phase
 * only: panning and zooming then invalidate drawing without ever recomposing the screen around it.
 * Nothing in here does list work - callers that need the settled window for expensive calculations
 * should observe it through [tk.glucodata.ui.graph.rememberSettledWindow].
 */
@Stable
class GraphViewportState(
    private val scope: CoroutineScope,
    initialDurationMillis: Long = TimeRange.SIX_HOURS.durationMillis,
    initialEndTimeMillis: Long = System.currentTimeMillis()
) {
    var durationMillis by mutableLongStateOf(
        initialDurationMillis.coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS)
    )
        private set

    var endTimeMillis by mutableLongStateOf(initialEndTimeMillis.coerceAtMost(System.currentTimeMillis()))
        private set

    /**
     * Oldest timestamp present in the dataset. Used to stop panning into empty prehistory.
     * `0` disables the lower bound.
     */
    var oldestDataMillis by mutableLongStateOf(0L)

    /**
     * True while the window tracks the wall clock. Any manual pan into the past clears it, panning
     * back to the right edge (or tapping "Now") restores it.
     */
    var isLive by mutableStateOf(true)
        private set

    val startTimeMillis: Long
        get() = endTimeMillis - durationMillis

    /** Day-level identity of the window, so UI can recompose only when the shown day changes. */
    private val dayKeyState = derivedStateOf {
        val start = startTimeMillis
        val end = endTimeMillis
        dayIndex(start) * 100_000L + dayIndex(end)
    }
    val dayKey: Long
        get() = dayKeyState.value

    private var animationJob: Job? = null

    fun stopAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    private fun minEndTime(duration: Long): Long {
        val oldest = oldestDataMillis
        return if (oldest <= 0L) Long.MIN_VALUE / 4 else oldest + (duration / 5)
    }

    private fun applyWindow(newEnd: Long, newDuration: Long = durationMillis) {
        val duration = newDuration.coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS)
        val now = System.currentTimeMillis()
        val clamped = newEnd.coerceIn(minEndTime(duration), now)
        if (duration != durationMillis) durationMillis = duration
        if (clamped != endTimeMillis) endTimeMillis = clamped
        isLive = (now - clamped) < LIVE_TOLERANCE_MILLIS
    }

    fun panBy(deltaMillis: Long) {
        if (deltaMillis == 0L) return
        applyWindow(endTimeMillis + deltaMillis)
    }

    /** Pans by a horizontal drag distance in pixels across a chart [chartWidthPx] wide. */
    fun panByPixels(deltaPx: Float, chartWidthPx: Float) {
        if (chartWidthPx <= 0f || deltaPx == 0f) return
        panBy((-(deltaPx / chartWidthPx) * durationMillis).toLong())
    }

    fun zoomBy(scaleFactor: Float, focalTimeMillis: Long) {
        if (scaleFactor <= 0f || scaleFactor == 1.0f) return
        val currentDuration = durationMillis
        val newDuration = (currentDuration / scaleFactor).toLong()
            .coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS)
        if (newDuration == currentDuration) return

        // Keep the pinch focal point anchored to the same instant
        val focalFraction = ((focalTimeMillis - startTimeMillis).toDouble() / currentDuration.toDouble())
            .coerceIn(0.0, 1.0)
        val newStart = (focalTimeMillis - (focalFraction * newDuration)).toLong()
        applyWindow(newStart + newDuration, newDuration)
    }

    /** Momentum scroll after a horizontal drag. */
    fun fling(velocityXPxPerSec: Float, chartWidthPx: Float, decaySpec: DecayAnimationSpec<Float>) {
        stopAnimation()
        if (chartWidthPx <= 0f || abs(velocityXPxPerSec) < MIN_FLING_VELOCITY) return
        animationJob = scope.launch {
            var lastValue = 0f
            AnimationState(initialValue = 0f, initialVelocity = -velocityXPxPerSec)
                .animateDecay(decaySpec) {
                    val delta = value - lastValue
                    lastValue = value
                    val before = endTimeMillis
                    panBy((delta / chartWidthPx * durationMillis).toLong())
                    // Stop cleanly once we hit either edge of the data
                    if (endTimeMillis == before && abs(velocity) > 1f && delta != 0f) cancelAnimation()
                }
        }
    }

    fun setDuration(newDurationMillis: Long, animate: Boolean = true) {
        val clamped = newDurationMillis.coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS)
        if (clamped == durationMillis) return
        val now = System.currentTimeMillis()
        val targetEnd = if (isLive) {
            now
        } else {
            // Zoom around the centre of what is on screen
            val centre = startTimeMillis + (durationMillis / 2)
            centre + (clamped / 2)
        }
        animateWindow(targetEnd, clamped, animate)
    }

    fun jumpTo(targetEndTime: Long, newDuration: Long? = null, animate: Boolean = true) {
        animateWindow(targetEndTime, newDuration ?: durationMillis, animate)
    }

    fun jumpToNow(animate: Boolean = true) {
        animateWindow(System.currentTimeMillis(), durationMillis, animate)
    }

    fun navigateDays(days: Int, animate: Boolean = true) {
        animateWindow(endTimeMillis + days * DAY_MILLIS, durationMillis, animate)
    }

    fun navigateWeeks(weeks: Int, animate: Boolean = true) {
        navigateDays(weeks * 7, animate)
    }

    /** Centres the window on midday of the local date encoded in [dateUtcMillis] (date picker). */
    fun jumpToDate(dateUtcMillis: Long, animate: Boolean = true) {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = dateUtcMillis
        }
        val localCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        animateWindow(localCal.timeInMillis + (durationMillis / 2), durationMillis, animate)
    }

    private fun animateWindow(targetEnd: Long, targetDuration: Long, animate: Boolean) {
        stopAnimation()
        val duration = targetDuration.coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS)
        val clampedEnd = targetEnd.coerceIn(minEndTime(duration), System.currentTimeMillis())
        if (!animate) {
            applyWindow(clampedEnd, duration)
            return
        }

        val fromEnd = endTimeMillis
        val fromDuration = durationMillis
        val endDelta = clampedEnd - fromEnd
        val durationDelta = duration - fromDuration
        if (endDelta == 0L && durationDelta == 0L) return

        animationJob = scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = JUMP_ANIMATION_MILLIS, easing = FastOutSlowInEasing)
            ) { fraction, _ ->
                applyWindow(
                    fromEnd + (endDelta * fraction).toLong(),
                    fromDuration + (durationDelta * fraction).toLong()
                )
            }
            // Land exactly on the requested window (and re-clamp against a clock that moved on)
            applyWindow(if (clampedEnd >= System.currentTimeMillis() - 1000L) System.currentTimeMillis() else clampedEnd, duration)
        }
    }

    /** Called by the live ticker while [isLive]; keeps the right edge pinned to the wall clock. */
    internal fun advanceToNow() {
        if (!isLive) return
        endTimeMillis = System.currentTimeMillis()
    }

    private fun dayIndex(timeMillis: Long): Long {
        val cal = sharedCalendar
        cal.timeInMillis = timeMillis
        return cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        const val MIN_DURATION_MILLIS = 30 * 60 * 1000L // 30 minutes
        const val MAX_DURATION_MILLIS = 14 * 24 * 3600 * 1000L // 14 days
        private const val DAY_MILLIS = 24 * 3600 * 1000L
        private const val LIVE_TOLERANCE_MILLIS = 60_000L
        private const val MIN_FLING_VELOCITY = 80f
        private const val JUMP_ANIMATION_MILLIS = 220

        private val sharedCalendar: Calendar = Calendar.getInstance()
    }
}

@Composable
fun rememberGraphViewportState(
    initialDurationMillis: Long = TimeRange.SIX_HOURS.durationMillis,
    initialEndTimeMillis: Long = System.currentTimeMillis()
): GraphViewportState {
    val scope = rememberCoroutineScope()
    val state = remember { GraphViewportState(scope, initialDurationMillis, initialEndTimeMillis) }

    // Keep the live window pinned to the clock. Only the draw phase observes the window, so this
    // costs one canvas redraw per second and never a recomposition.
    LaunchedEffect(state) {
        while (isActive) {
            delay(1_000L)
            state.advanceToNow()
        }
    }
    return state
}
