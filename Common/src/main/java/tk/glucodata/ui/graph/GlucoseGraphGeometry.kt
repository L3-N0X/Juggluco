package tk.glucodata.ui.graph

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** Plot rectangle. The right gutter holds the value axis; grid lines run into its labels. */
internal class ChartMetrics(val width: Float, val height: Float, density: Density) {
    val gutterWidth = with(density) { 42.dp.toPx() }
    val chartLeft = with(density) { 8.dp.toPx() }
    val chartTop = with(density) { 10.dp.toPx() }
    val chartRight = width - gutterWidth
    val chartBottom = height - with(density) { 26.dp.toPx() }
    val chartWidth get() = (chartRight - chartLeft).coerceAtLeast(1f)
    val chartHeight get() = (chartBottom - chartTop).coerceAtLeast(1f)
    val labelAnchorX = width - with(density) { 6.dp.toPx() }
    val labelGap = with(density) { 4.dp.toPx() }
}

/** Maps time and glucose values onto the plot; carries the density for dp-correct stroke sizes. */
internal class ChartTransform(
    val metrics: ChartMetrics,
    val startTime: Long,
    val endTime: Long,
    val minValue: Float,
    val maxValue: Float,
    val scope: DrawScope
) {
    val spanMillis = (endTime - startTime).coerceAtLeast(1L)

    fun x(time: Long): Float =
        metrics.chartLeft + ((time - startTime).toFloat() / spanMillis) * metrics.chartWidth

    fun xClamped(time: Long): Float = x(time).coerceIn(metrics.chartLeft, metrics.chartRight)

    fun y(value: Float): Float {
        val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        return metrics.chartBottom - fraction * metrics.chartHeight
    }

    fun timeAt(x: Float): Long =
        startTime + (((x - metrics.chartLeft) / metrics.chartWidth).coerceIn(0f, 1f) * spanMillis).toLong()

    fun px(dp: Float): Float = with(scope) { dp.dp.toPx() }
}
