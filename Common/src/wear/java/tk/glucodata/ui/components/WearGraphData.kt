package tk.glucodata.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseStatus

/**
 * The wear graph's trend curve, stored as parallel primitive arrays and sorted by time.
 *
 * Two things make this worth having.
 *
 * **Correctness.** The native history contains the *same* reading twice: once raw and once
 * calibrated. Plotting that list as-is made the curve zigzag vertically between two different values
 * at the same x for every single minute, and pulled the value axis up to whatever the uncalibrated
 * estimate happened to be. This collapses each timestamp to one point, preferring the calibrated
 * value.
 *
 * **Cost.** Because it is sorted, the visible window is found with a binary search and the draw
 * phase can index primitive arrays directly. Nothing filters, copies or boxes while drawing, and
 * building the arrays happens once per data change on a background dispatcher rather than once per
 * frame on the main thread.
 */
@Immutable
internal class WearSeries(
    val times: LongArray,
    val values: FloatArray,
    /** Ordinal of [GlucoseStatus] per point. */
    val statuses: ByteArray
) {
    val size: Int get() = times.size
    val isEmpty: Boolean get() = times.isEmpty()
    val firstTime: Long get() = if (isEmpty) 0L else times[0]
    val lastTime: Long get() = if (isEmpty) 0L else times[size - 1]

    /** Index of the first point at or after [time]; may be [size]. */
    fun firstIndexAtOrAfter(time: Long): Int {
        var low = 0
        var high = size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (times[mid] < time) low = mid + 1 else high = mid
        }
        return low
    }

    /** Index of the last point at or before [time]; may be -1. */
    fun lastIndexAtOrBefore(time: Long): Int = firstIndexAtOrAfter(time + 1) - 1

    /** Index of the point closest in time to [time]; -1 when empty. */
    fun nearestIndexTo(time: Long): Int {
        if (isEmpty) return -1
        val at = firstIndexAtOrAfter(time)
        if (at == 0) return 0
        if (at >= size) return size - 1
        val before = at - 1
        return if (time - times[before] <= times[at] - time) before else at
    }

    /** Highest value inside the window, used to scale the value axis. */
    fun maxValueBetween(startTime: Long, endTime: Long): Float {
        var result = 0f
        var i = firstIndexAtOrAfter(startTime)
        while (i < size && times[i] <= endTime) {
            if (values[i] > result) result = values[i]
            i++
        }
        return result
    }

    fun statusAt(index: Int): GlucoseStatus = STATUS_BY_ORDINAL[statuses[index].toInt()]

    /**
     * Rebuilds a [GlucosePoint] for a single index. The graph only needs the time, the value and the
     * range it fell in, so there is no reason to keep a list of objects alive for the inspector.
     */
    fun pointAt(index: Int): GlucosePoint = GlucosePoint(
        timestamp = times[index],
        valueMgDl = values[index],
        status = statusAt(index)
    )

    companion object {
        val Empty = WearSeries(LongArray(0), FloatArray(0), ByteArray(0))

        private val STATUS_BY_ORDINAL: Array<GlucoseStatus> = GlucoseStatus.entries.toTypedArray()

        fun build(readings: List<GlucosePoint>): WearSeries {
            if (readings.isEmpty()) return Empty
            val sorted = if (isSortedByTime(readings)) readings else readings.sortedBy { it.timestamp }
            return curveFrom(sorted, 0, sorted.size)
        }

        /**
         * Builds only the newest [windowMillis] of history.
         *
         * The published history is six figures of points - a full native walk of every raw,
         * calibrated and scan record of every sensor - and the home screen sparkline draws two hours
         * of it, which is roughly thirty points. Collapsing all of it to draw thirty was the reason
         * the sparkline went blank and popped back in seconds later: the build was big enough to
         * miss a frame budget, and it was paid again on every scroll back into the viewport.
         *
         * The repository hands over a sorted list, so the window is a suffix and is found by walking
         * backwards off the newest point until it falls out of range - no sort, no copy, no
         * allocation outside the arrays actually returned. A list that is not sorted takes the
         * ordinary [build] path, which is the same work this always did.
         */
        fun buildTail(readings: List<GlucosePoint>, windowMillis: Long): WearSeries {
            if (readings.isEmpty()) return Empty
            if (!isSortedByTime(readings)) return build(readings)
            val newest = readings[readings.size - 1].timestamp
            val floorTime = newest - windowMillis
            var start = readings.size
            while (start > 0 && readings[start - 1].timestamp >= floorTime) start--
            if (start >= readings.size) return Empty
            return curveFrom(readings, start, readings.size)
        }

        /**
         * A fingerstick is a spot measurement sitting between two interpolated trend points; mixing
         * it into the curve produces a spike, which is why the phone graph draws scans as their own
         * layer. The watch graph has a single curve, so scans are left out of it - unless there is no
         * trend in the range at all, which is the case for a user who only scans.
         */
        private fun curveFrom(sorted: List<GlucosePoint>, from: Int, to: Int): WearSeries {
            val curve = toSeries(sorted, from, to, includeScans = false)
            return if (curve.isEmpty) toSeries(sorted, from, to, includeScans = true) else curve
        }

        /**
         * Collapses `sorted[from, to)` to one point per timestamp, preferring the calibrated reading
         * over the raw one so the curve is not drawn zigzagging between two values at the same x.
         */
        private fun toSeries(sorted: List<GlucosePoint>, from: Int, to: Int, includeScans: Boolean): WearSeries {
            val usable = ArrayList<GlucosePoint>(to - from)

            var index = from
            while (index < to) {
                val timestamp = sorted[index].timestamp
                var best = sorted[index]
                index++
                while (index < to && sorted[index].timestamp == timestamp) {
                    val candidate = sorted[index]
                    if (candidate.isCalibrated && !best.isCalibrated) best = candidate
                    index++
                }
                if (!best.isHistory && (includeScans || !best.isScan)) usable.add(best)
            }

            if (usable.isEmpty()) return Empty
            val n = usable.size
            val times = LongArray(n)
            val values = FloatArray(n)
            val statuses = ByteArray(n)
            for (i in 0 until n) {
                val point = usable[i]
                times[i] = point.timestamp
                values[i] = point.valueMgDl
                statuses[i] = point.status.ordinal.toByte()
            }
            return WearSeries(times, values, statuses)
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
 * A built series, tagged with the exact readings list and window it came from.
 *
 * Keyed on identity rather than equality on purpose: the repository publishes a fresh list only
 * when a reading genuinely changed, so identity is both a correct key and a field comparison.
 */
private class CachedSeries(
    val source: List<GlucosePoint>,
    val windowMillis: Long,
    val series: WearSeries
) {
    fun matches(readings: List<GlucosePoint>, windowMillis: Long): Boolean =
        source === readings && this.windowMillis == windowMillis
}

/** Stands in for "the whole history", which [WearSeries.build] builds and [WearSeries.buildTail] does not. */
private const val FULL_HISTORY = 0L

/**
 * Process-wide memo, so a series is built once per data change instead of once per composition.
 *
 * This is what makes the sparkline reappear the instant it scrolls back into view. The home screen
 * is a `ScalingLazyColumn`, which disposes items that leave the viewport, so every scroll back in
 * used to create a brand new `produceState`, throw the prepared arrays away and start the collapse
 * over on a background dispatcher - which meant a blank card for as long as that took, over and
 * over, for data that had not changed at all. Now the second composition of the same data is a
 * field read, and the arrays outlive the item that drew them.
 *
 * One slot per shape rather than a map: the full graph needs the whole history, the sparkline needs
 * a window, and nothing else builds a series.
 */
private object WearSeriesCache {
    @Volatile
    private var full: CachedSeries? = null

    @Volatile
    private var tail: CachedSeries? = null

    fun get(readings: List<GlucosePoint>, windowMillis: Long): WearSeries? {
        val entry = if (windowMillis == FULL_HISTORY) full else tail
        return entry?.takeIf { it.matches(readings, windowMillis) }?.series
    }

    fun put(readings: List<GlucosePoint>, windowMillis: Long, series: WearSeries) {
        val entry = CachedSeries(readings, windowMillis, series)
        if (windowMillis == FULL_HISTORY) full = entry else tail = entry
    }
}

/**
 * The [State] both entry points below hand back.
 *
 * Two publication paths, because they cost different things. A cache hit is published into a plain
 * field that the composition already running reads back on the same pass, so scrolling a graph back
 * into view costs no recomposition at all. A background build is published through the snapshot, so
 * the canvas redraws when it lands - and the previous series stays on screen until then, rather than
 * resetting to empty and blanking the card on every new reading.
 */
private class WearSeriesBox : State<WearSeries> {
    private val snapshot = mutableStateOf(WearSeries.Empty)
    private var immediate: WearSeries? = null

    override val value: WearSeries get() = immediate ?: snapshot.value

    fun publishImmediate(series: WearSeries) {
        immediate = series
    }

    fun publish(series: WearSeries) {
        immediate = null
        snapshot.value = series
    }
}

/**
 * Builds the whole history off the main thread, keeping the previous series on screen while the new
 * one is prepared. Used by the full graph, which pans back through days of data.
 */
@Composable
internal fun rememberWearSeries(readings: List<GlucosePoint>): State<WearSeries> =
    rememberWearSeries(readings, FULL_HISTORY)

/**
 * Same series, but prepared from only the newest [hoursToShow] of readings. Used by the sparkline.
 */
@Composable
internal fun rememberWearSeriesTail(readings: List<GlucosePoint>, hoursToShow: Int): State<WearSeries> =
    rememberWearSeries(readings, hoursToShow.coerceAtLeast(1) * 3_600_000L)

@Composable
private fun rememberWearSeries(readings: List<GlucosePoint>, windowMillis: Long): State<WearSeries> {
    val box = remember { WearSeriesBox() }
    val cached = remember(readings, windowMillis) { WearSeriesCache.get(readings, windowMillis) }
    if (cached != null) {
        box.publishImmediate(cached)
    } else {
        LaunchedEffect(readings, windowMillis) {
            val built = withContext(Dispatchers.Default) {
                if (windowMillis == FULL_HISTORY) WearSeries.build(readings)
                else WearSeries.buildTail(readings, windowMillis)
            }
            WearSeriesCache.put(readings, windowMillis, built)
            box.publish(built)
        }
    }
    return box
}
