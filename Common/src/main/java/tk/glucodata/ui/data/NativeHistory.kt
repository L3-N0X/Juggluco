package tk.glucodata.ui.data

import tk.glucodata.Natives
import kotlin.math.max

/**
 * Reads the tail of the native sensor streams into parallel arrays, for the surfaces that only draw
 * a short stretch of history (widgets, alert screens) and so must not walk the whole store the way
 * [GlucoseRepository] does.
 */
object NativeHistory {
    private const val MINUTE = 60_000L

    private class SensorRun(val times: LongArray, val values: FloatArray)

    /**
     * Readings from [from] (epoch milliseconds) to now as sorted (times, mg/dL). Call off the main
     * thread. Where two sensors overlap only the newer one is kept, so a curve never zigzags between them.
     *
     * Walks only the tail of each sensor's stream. Stream positions are roughly one per minute, so
     * starting [from] minutes before the end is enough, and sensors that ended before [from] are
     * skipped after a single probe.
     */
    fun read(from: Long): Pair<LongArray, FloatArray> {
        val fromSec = from / 1000L
        val spanPositions = ((System.currentTimeMillis() - from) / MINUTE).toInt() + 90
        val ptrs = (try { Natives.allSensorPtrs() } catch (_: Throwable) { null })
            ?.takeIf { it.isNotEmpty() }
            ?: Natives.activeSensorPtrs()
            ?: LongArray(0)
        val runs = ArrayList<SensorRun>()
        for (ptr in ptrs) {
            if (ptr == 0L) continue
            // Past the end the native call returns the stream length in the position field.
            val length = ((Natives.streamfromSensorptr(ptr, Int.MAX_VALUE) ushr 48) and 0xFFFF).toInt()
            if (length == 0) continue
            val probe = Natives.streamfromSensorptr(ptr, max(0, length - 5))
            val probeTime = probe and 0xFFFFFFFFL
            if (probeTime != 0L && probeTime < fromSec) continue
            val runTimes = ArrayList<Long>()
            val runValues = ArrayList<Float>()
            var pos = max(0, length - spanPositions)
            var guard = spanPositions + 10
            while (guard-- > 0) {
                val res = Natives.streamfromSensorptr(ptr, pos)
                val time = res and 0xFFFFFFFFL
                val next = ((res ushr 48) and 0xFFFF).toInt()
                if (time == 0L || next <= pos) break
                val mgDl = ((res ushr 32) and 0xFFFF).toInt()
                if (time >= fromSec && mgDl in 20..600) {
                    runTimes.add(time * 1000L)
                    runValues.add(mgDl.toFloat())
                }
                pos = next
            }
            if (runTimes.isNotEmpty()) runs.add(SensorRun(runTimes.toLongArray(), runValues.toFloatArray()))
        }
        if (runs.isEmpty()) return LongArray(0) to FloatArray(0)
        runs.sortByDescending { it.times.last() }
        val mergedTimes = ArrayList<Long>()
        val mergedValues = ArrayList<Float>()
        var cutoff = Long.MAX_VALUE
        for (run in runs) {
            for (i in run.times.indices) {
                if (run.times[i] < cutoff - 30_000L) {
                    mergedTimes.add(run.times[i])
                    mergedValues.add(run.values[i])
                }
            }
            cutoff = minOf(cutoff, run.times.first())
        }
        val order = mergedTimes.indices.sortedBy { mergedTimes[it] }
        return LongArray(order.size) { mergedTimes[order[it]] } to FloatArray(order.size) { mergedValues[order[it]] }
    }
}
