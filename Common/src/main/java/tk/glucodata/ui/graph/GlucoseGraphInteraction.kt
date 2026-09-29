package tk.glucodata.ui.graph

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import tk.glucodata.ui.model.DisplayConfig
import tk.glucodata.ui.model.LogRecord
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

@Immutable
internal data class InspectedReading(
    val timestamp: Long,
    val valueMgDl: Float,
    val statusOrdinal: Int,
    val previousTimestamp: Long,
    val previousValueMgDl: Float
) {
    val hasDelta: Boolean get() = previousTimestamp > 0L
    val deltaMinutes: Long get() = max(1L, (timestamp - previousTimestamp) / 60_000L)
    val ratePerMinute: Float
        get() = if (!hasDelta) 0f else (valueMgDl - previousValueMgDl) / deltaMinutes.toFloat()
}

internal fun handleTap(
    touchX: Float,
    touchY: Float,
    metrics: ChartMetrics,
    viewportState: GraphViewportState,
    data: GraphRenderData,
    config: DisplayConfig,
    currentSelection: InspectedReading?,
    onLogTapped: (LogRecord) -> Unit,
    onSelectionChanged: (InspectedReading?) -> Unit
) {
    val fraction = ((touchX - metrics.chartLeft) / metrics.chartWidth).coerceIn(0f, 1f)
    val start = viewportState.startTimeMillis
    val span = (viewportState.endTimeMillis - start).coerceAtLeast(1L)
    val touchTime = start + (fraction * span).toLong()

    if (config.showAmounts && touchY > metrics.chartBottom - metrics.chartHeight * 0.14f) {
        val log = findNearestEvent(data.events, touchTime, (span * 0.04f).toLong())
        if (log != null) {
            onLogTapped(log)
            return
        }
    }

    val nearest = findNearestReading(data, config, touchTime, (span * 0.05f).toLong())
    onSelectionChanged(
        if (nearest != null && nearest.timestamp == currentSelection?.timestamp) null else nearest
    )
}

private fun findNearestEvent(events: GraphEvents, targetTime: Long, tolerance: Long): LogRecord? {
    if (events.size == 0) return null
    val index = events.firstIndexAtOrAfter(targetTime)
    var best: LogRecord? = null
    var bestDistance = Long.MAX_VALUE
    for (i in (index - 1)..index) {
        if (i < 0 || i >= events.size) continue
        val distance = abs(events.times[i] - targetTime)
        if (distance < bestDistance) {
            bestDistance = distance
            best = events.records[i]
        }
    }
    return if (bestDistance <= tolerance) best else null
}

private fun findNearestReading(
    data: GraphRenderData,
    config: DisplayConfig,
    targetTime: Long,
    tolerance: Long
): InspectedReading? {
    var best: InspectedReading? = null
    var bestDistance = Long.MAX_VALUE

    fun consider(series: GraphSeries, enabled: Boolean) {
        if (!enabled || series.isEmpty) return
        val index = series.firstIndexAtOrAfter(targetTime)
        for (i in (index - 1)..index) {
            if (i < 0 || i >= series.size) continue
            val distance = abs(series.times[i] - targetTime)
            if (distance < bestDistance) {
                bestDistance = distance
                best = InspectedReading(
                    timestamp = series.times[i],
                    valueMgDl = series.values[i],
                    statusOrdinal = series.statuses[i].toInt(),
                    previousTimestamp = if (i > 0) series.times[i - 1] else 0L,
                    previousValueMgDl = if (i > 0) series.values[i - 1] else 0f
                )
            }
        }
    }

    consider(data.stream, config.showStream)
    consider(data.calibratedStream, config.showCalibratedStream)
    consider(data.history, config.showHistory)
    consider(data.calibratedHistory, config.showCalibratedHistory)
    consider(data.scans, config.showScans)
    consider(data.calibratedScans, config.showCalibratedScans)

    return if (bestDistance <= tolerance) best else null
}

internal fun distance(p1: Offset, p2: Offset): Float {
    val dx = p1.x - p2.x
    val dy = p1.y - p2.y
    return sqrt(dx * dx + dy * dy)
}
