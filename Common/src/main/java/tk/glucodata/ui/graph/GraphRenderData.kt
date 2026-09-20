package tk.glucodata.ui.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import tk.glucodata.ui.model.DisplayConfig
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.LogRecord
import kotlin.math.abs
import kotlin.math.max

/**
 * One drawable layer stored as parallel primitive arrays, sorted by time.
 *
 * Drawing reads it straight from the draw phase: the visible slice is found with a binary search,
 * so panning never filters, copies or allocates - which is what used to make the graph stutter.
 */
@Immutable
class GraphSeries(
    val times: LongArray,
    val values: FloatArray,
    /** Ordinal of [tk.glucodata.ui.model.GlucoseStatus] per point. */
    val statuses: ByteArray
) {
    val size: Int get() = times.size
    val isEmpty: Boolean get() = times.isEmpty()

    /** Index of the first point at or after [time] (may be [size]). */
    fun firstIndexAtOrAfter(time: Long): Int {
        var low = 0
        var high = times.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (times[mid] < time) low = mid + 1 else high = mid
        }
        return low
    }

    /** Index of the last point at or before [time] (may be -1). */
    fun lastIndexAtOrBefore(time: Long): Int = firstIndexAtOrAfter(time + 1) - 1

    fun maxValueBetween(startTime: Long, endTime: Long): Float {
        var result = 0f
        var i = firstIndexAtOrAfter(startTime)
        while (i < times.size && times[i] <= endTime) {
            if (values[i] > result) result = values[i]
            i++
        }
        return result
    }

    fun minValueBetween(startTime: Long, endTime: Long): Float {
        var result = Float.MAX_VALUE
        var i = firstIndexAtOrAfter(startTime)
        while (i < times.size && times[i] <= endTime) {
            if (values[i] < result) result = values[i]
            i++
        }
        return if (result == Float.MAX_VALUE) 0f else result
    }

    companion object {
        val Empty = GraphSeries(LongArray(0), FloatArray(0), ByteArray(0))
    }
}

/** Log entries of the timeline strip, kept sorted with a parallel time array for binary search. */
@Immutable
class GraphEvents(val times: LongArray, val records: List<LogRecord>) {
    val size: Int get() = times.size

    fun firstIndexAtOrAfter(time: Long): Int {
        var low = 0
        var high = times.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (times[mid] < time) low = mid + 1 else high = mid
        }
        return low
    }

    companion object {
        val Empty = GraphEvents(LongArray(0), emptyList())
    }
}

/** Everything the canvas needs, precomputed once per data change instead of once per frame. */
@Immutable
class GraphRenderData(
    val stream: GraphSeries,
    val calibratedStream: GraphSeries,
    val history: GraphSeries,
    val calibratedHistory: GraphSeries,
    val scans: GraphSeries,
    val calibratedScans: GraphSeries,
    val events: GraphEvents,
    val oldestTime: Long,
    val newestTime: Long
) {
    val isEmpty: Boolean
        get() = stream.isEmpty && calibratedStream.isEmpty && history.isEmpty &&
            calibratedHistory.isEmpty && scans.isEmpty && calibratedScans.isEmpty

    /** Highest curve value inside the window, used to scale the Y axis without clipping peaks. */
    fun maxValueBetween(startTime: Long, endTime: Long, config: DisplayConfig): Float {
        var result = 0f
        if (config.showStream) result = max(result, stream.maxValueBetween(startTime, endTime))
        if (config.showCalibratedStream) result = max(result, calibratedStream.maxValueBetween(startTime, endTime))
        if (config.showHistory) result = max(result, history.maxValueBetween(startTime, endTime))
        if (config.showCalibratedHistory) result = max(result, calibratedHistory.maxValueBetween(startTime, endTime))
        if (config.showScans) result = max(result, scans.maxValueBetween(startTime, endTime))
        if (config.showCalibratedScans) result = max(result, calibratedScans.maxValueBetween(startTime, endTime))
        return result
    }

    companion object {
        val Empty = GraphRenderData(
            GraphSeries.Empty, GraphSeries.Empty, GraphSeries.Empty,
            GraphSeries.Empty, GraphSeries.Empty, GraphSeries.Empty,
            GraphEvents.Empty, 0L, 0L
        )

        fun build(readings: List<GlucosePoint>, logs: List<LogRecord>): GraphRenderData {
            if (readings.isEmpty() && logs.isEmpty()) return Empty

            val stream = ArrayList<GlucosePoint>(readings.size)
            val calibratedStream = ArrayList<GlucosePoint>()
            val history = ArrayList<GlucosePoint>()
            val calibratedHistory = ArrayList<GlucosePoint>()
            val scans = ArrayList<GlucosePoint>()
            val calibratedScans = ArrayList<GlucosePoint>()

            for (pt in readings) {
                val bucket = when {
                    pt.isScan && pt.isCalibrated -> calibratedScans
                    pt.isScan -> scans
                    pt.isHistory && pt.isCalibrated -> calibratedHistory
                    pt.isHistory -> history
                    pt.isCalibrated -> calibratedStream
                    else -> stream
                }
                bucket.add(pt)
            }

            val sortedLogs = logs.sortedBy { it.timestamp }
            val eventTimes = LongArray(sortedLogs.size) { sortedLogs[it].timestamp }

            val oldest = minOf(
                readings.firstOrNull()?.timestamp ?: Long.MAX_VALUE,
                sortedLogs.firstOrNull()?.timestamp ?: Long.MAX_VALUE
            ).takeIf { it != Long.MAX_VALUE } ?: 0L
            val newest = maxOf(
                readings.lastOrNull()?.timestamp ?: 0L,
                sortedLogs.lastOrNull()?.timestamp ?: 0L
            )

            return GraphRenderData(
                stream = toSeries(stream),
                calibratedStream = toSeries(calibratedStream),
                history = toSeries(history),
                calibratedHistory = toSeries(calibratedHistory),
                scans = toSeries(scans),
                calibratedScans = toSeries(calibratedScans),
                events = GraphEvents(eventTimes, sortedLogs),
                oldestTime = oldest,
                newestTime = newest
            )
        }

        private fun toSeries(points: List<GlucosePoint>): GraphSeries {
            if (points.isEmpty()) return GraphSeries.Empty
            val sorted = if (isSortedByTime(points)) points else points.sortedBy { it.timestamp }
            val n = sorted.size
            val times = LongArray(n)
            val values = FloatArray(n)
            val statuses = ByteArray(n)
            for (i in 0 until n) {
                val pt = sorted[i]
                times[i] = pt.timestamp
                values[i] = pt.valueMgDl
                statuses[i] = pt.status.ordinal.toByte()
            }
            return GraphSeries(times, values, statuses)
        }

        private fun isSortedByTime(points: List<GlucosePoint>): Boolean {
            for (i in 1 until points.size) {
                if (points[i].timestamp < points[i - 1].timestamp) return false
            }
            return true
        }
    }
}

/**
 * Builds [GraphRenderData] off the main thread, keeping the previous frame's data on screen while
 * the new one is prepared.
 */
@Composable
fun rememberGraphRenderData(
    readings: List<GlucosePoint>,
    logs: List<LogRecord>
): State<GraphRenderData> = produceState(
    initialValue = GraphRenderData.Empty,
    key1 = readings,
    key2 = logs
) {
    value = withContext(Dispatchers.Default) { GraphRenderData.build(readings, logs) }
}

/** An immutable snapshot of the visible window, safe to use as a key for expensive work. */
@Immutable
data class TimeWindow(
    val startMillis: Long,
    val endMillis: Long,
    val isLive: Boolean
) {
    val durationMillis: Long get() = endMillis - startMillis
}

/**
 * Observes the viewport from *outside* composition and republishes it only once the gesture has
 * settled, so stats, labels and logbook filters recompute once per interaction instead of once per
 * frame. While the graph is live, sub-[LIVE_QUANTUM_MILLIS] clock drift is ignored as well.
 */
@Composable
fun rememberSettledWindow(
    viewportState: GraphViewportState,
    debounceMillis: Long = 150L
): TimeWindow {
    var settled by remember {
        mutableStateOf(
            TimeWindow(viewportState.startTimeMillis, viewportState.endTimeMillis, viewportState.isLive)
        )
    }
    LaunchedEffect(viewportState) {
        snapshotFlow {
            TimeWindow(viewportState.startTimeMillis, viewportState.endTimeMillis, viewportState.isLive)
        }
            .distinctUntilChanged()
            .collectLatest { window ->
                val current = settled
                // While live the window creeps forward every second; ignore that drift entirely.
                val threshold = if (window.isLive && current.isLive) LIVE_QUANTUM_MILLIS else 0L
                val movedEnough = abs(window.endMillis - current.endMillis) > threshold ||
                    window.durationMillis != current.durationMillis ||
                    window.isLive != current.isLive
                if (!movedEnough) return@collectLatest
                delay(debounceMillis)
                settled = window
            }
    }
    return settled
}

private const val LIVE_QUANTUM_MILLIS = 30_000L
