package tk.glucodata.ui.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import tk.glucodata.ui.model.TimeRange
import java.util.Calendar
import java.util.TimeZone

/**
 * Manages the visible time window of the glucose graph.
 *
 * Provides smooth panning, pinch-to-zoom scaling, and external jumps (Now, DatePicker, Day/Week steps, Search, Last Scan).
 * Clamped strictly at current time (cannot scroll into the future).
 */
class GraphViewportState(
    initialDurationMillis: Long = TimeRange.SIX_HOURS.durationMillis,
    initialEndTimeMillis: Long = System.currentTimeMillis()
) {
    var durationMillis by mutableLongStateOf(initialDurationMillis.coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS))
        private set

    var endTimeMillis by mutableLongStateOf(initialEndTimeMillis.coerceAtMost(System.currentTimeMillis()))
        private set

    val startTimeMillis: Long
        get() = endTimeMillis - durationMillis

    /**
     * True when the graph window is tracking live readings (within 45 seconds of current wall clock).
     */
    val isLive: Boolean
        get() = (System.currentTimeMillis() - endTimeMillis) < 45_000L

    fun panBy(deltaMillis: Long) {
        val now = System.currentTimeMillis()
        val newEnd = endTimeMillis + deltaMillis
        // Prevent scrolling into the future - stop firmly at now
        endTimeMillis = newEnd.coerceAtMost(now)
    }

    fun zoomBy(scaleFactor: Float, focalTimeMillis: Long) {
        if (scaleFactor <= 0f || scaleFactor == 1.0f) return
        val currentDuration = durationMillis
        val newDuration = (currentDuration / scaleFactor).toLong().coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS)
        if (newDuration == currentDuration) return

        // Keep focal point stationary in time
        val focalFraction = ((focalTimeMillis - startTimeMillis).toDouble() / currentDuration.toDouble()).coerceIn(0.0, 1.0)
        val newStartTime = (focalTimeMillis - (focalFraction * newDuration)).toLong()
        val now = System.currentTimeMillis()
        val newEndTime = (newStartTime + newDuration).coerceAtMost(now)

        durationMillis = newDuration
        endTimeMillis = newEndTime
    }

    fun setDuration(newDurationMillis: Long) {
        val clamped = newDurationMillis.coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS)
        val now = System.currentTimeMillis()
        if (isLive) {
            durationMillis = clamped
            endTimeMillis = now
        } else {
            val centerTime = startTimeMillis + (durationMillis / 2)
            durationMillis = clamped
            endTimeMillis = (centerTime + (clamped / 2)).coerceAtMost(now)
        }
    }

    fun jumpTo(targetEndTime: Long, newDuration: Long? = null) {
        val now = System.currentTimeMillis()
        if (newDuration != null) {
            durationMillis = newDuration.coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS)
        }
        endTimeMillis = targetEndTime.coerceAtMost(now)
    }

    fun jumpToNow() {
        endTimeMillis = System.currentTimeMillis()
    }

    fun navigateDays(days: Int) {
        panBy(days * 24 * 3600 * 1000L)
    }

    fun navigateWeeks(weeks: Int) {
        panBy(weeks * 7 * 24 * 3600 * 1000L)
    }

    fun jumpToDate(dateUtcMillis: Long) {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = dateUtcMillis
        }
        val year = utcCal.get(Calendar.YEAR)
        val month = utcCal.get(Calendar.MONTH)
        val day = utcCal.get(Calendar.DAY_OF_MONTH)

        // Center on midday of the selected local date
        val localCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetEnd = localCal.timeInMillis + (durationMillis / 2)
        jumpTo(targetEnd)
    }

    companion object {
        const val MIN_DURATION_MILLIS = 30 * 60 * 1000L // 30 minutes
        const val MAX_DURATION_MILLIS = 14 * 24 * 3600 * 1000L // 14 days
    }
}

@Composable
fun rememberGraphViewportState(
    initialDurationMillis: Long = TimeRange.SIX_HOURS.durationMillis,
    initialEndTimeMillis: Long = System.currentTimeMillis()
): GraphViewportState {
    return remember {
        GraphViewportState(initialDurationMillis, initialEndTimeMillis)
    }
}
