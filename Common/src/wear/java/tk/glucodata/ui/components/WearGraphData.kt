package tk.glucodata.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
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
            // A fingerstick is a spot measurement sitting between two interpolated trend points;
            // mixing it into the curve produces a spike, which is why the phone graph draws scans as
            // their own layer. The watch graph has a single curve, so scans are left out of it -
            // unless there is no trend at all, which is the case for a user who only scans.
            val curve = toSeries(sorted, includeScans = false)
            return if (curve.isEmpty) toSeries(sorted, includeScans = true) else curve
        }

        /**
         * Collapses the history to one point per timestamp, preferring the calibrated reading over
         * the raw one so the curve is not drawn zigzagging between two values at the same x.
         */
        private fun toSeries(sorted: List<GlucosePoint>, includeScans: Boolean): WearSeries {
            val count = sorted.size
            val usable = ArrayList<GlucosePoint>(count)

            var index = 0
            while (index < count) {
                val timestamp = sorted[index].timestamp
                var best = sorted[index]
                index++
                while (index < count && sorted[index].timestamp == timestamp) {
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
 * Builds the series off the main thread, keeping the previous one on screen while the new one is
 * prepared.
 */
@Composable
internal fun rememberWearSeries(readings: List<GlucosePoint>): State<WearSeries> =
    produceState(initialValue = WearSeries.Empty, key1 = readings) {
        value = withContext(Dispatchers.Default) { WearSeries.build(readings) }
    }
