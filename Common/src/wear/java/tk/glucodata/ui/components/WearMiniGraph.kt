package tk.glucodata.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.theme.LocalClinicalColors

@Composable
fun WearMiniGraph(
    readings: List<GlucosePoint>,
    targetLow: Float = 70f,
    targetHigh: Float = 180f,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(72.dp),
    hoursToShow: Int = 2
) {
    val clinical = LocalClinicalColors.current

    val now = remember(readings) {
        readings.lastOrNull()?.timestamp ?: System.currentTimeMillis()
    }
    val windowStart = now - (hoursToShow * 3600 * 1000L)

    val visibleReadings = remember(readings, windowStart) {
        readings.filter { it.timestamp >= windowStart }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val minGl = 40f
        val maxGl = 260f
        val rangeGl = maxGl - minGl

        fun yFor(gl: Float): Float {
            val clamped = gl.coerceIn(minGl, maxGl)
            return height - ((clamped - minGl) / rangeGl) * height
        }

        fun xFor(timestamp: Long): Float {
            val progress = ((timestamp - windowStart).toFloat() / (now - windowStart).coerceAtLeast(1L)).coerceIn(0f, 1f)
            return progress * width
        }

        // 1. Shaded target range band
        val yTargetLow = yFor(targetLow)
        val yTargetHigh = yFor(targetHigh)
        val bandTop = yTargetHigh.coerceAtLeast(0f)
        val bandBottom = yTargetLow.coerceAtMost(height)

        drawRect(
            color = clinical.targetRangeShade,
            topLeft = Offset(0f, bandTop),
            size = Size(width, (bandBottom - bandTop).coerceAtLeast(0f))
        )

        // 2. Target guideline boundaries
        drawLine(
            color = clinical.graphGrid,
            start = Offset(0f, yTargetHigh),
            end = Offset(width, yTargetHigh),
            strokeWidth = 1f
        )
        drawLine(
            color = clinical.graphGrid,
            start = Offset(0f, yTargetLow),
            end = Offset(width, yTargetLow),
            strokeWidth = 1f
        )

        if (visibleReadings.isEmpty()) return@Canvas

        // 3. Connect line
        val path = Path()
        visibleReadings.forEachIndexed { index, point ->
            val px = xFor(point.timestamp)
            val py = yFor(point.valueMgDl)
            if (index == 0) {
                path.moveTo(px, py)
            } else {
                path.lineTo(px, py)
            }
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    clinical.inRange.copy(alpha = 0.5f),
                    clinical.inRange
                )
            ),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // 4. Highlight the latest reading with a distinctive dot
        val lastPoint = visibleReadings.last()
        val lx = xFor(lastPoint.timestamp)
        val ly = yFor(lastPoint.valueMgDl)
        val dotColor = when (lastPoint.status) {
            GlucoseStatus.IN_RANGE -> clinical.inRange
            GlucoseStatus.LOW, GlucoseStatus.HIGH -> clinical.low
            GlucoseStatus.VERY_LOW, GlucoseStatus.VERY_HIGH -> clinical.veryLow
        }

        // Outer glow
        drawCircle(
            color = dotColor.copy(alpha = 0.35f),
            radius = 6.dp.toPx(),
            center = Offset(lx, ly)
        )
        // Inner core
        drawCircle(
            color = dotColor,
            radius = 3.5.dp.toPx(),
            center = Offset(lx, ly)
        )
    }
}
