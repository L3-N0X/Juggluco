package tk.glucodata.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tk.glucodata.ui.model.GlucoseStats
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.theme.LocalClinicalColors

@Composable
fun GlucoseStatsCard(
    stats: GlucoseStats,
    unit: GlucoseUnit,
    timeRangeLabel: String = "6h",
    modifier: Modifier = Modifier
) {
    val clinicalColors = LocalClinicalColors.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            // Header: Title & Time window
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Time in Range ($timeRangeLabel)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${stats.timeInRangePercent}% in target",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = clinicalColors.inRange
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Multi-segment Time in Range Bar
            TimeInRangeBar(stats = stats)

            Spacer(modifier = Modifier.height(14.dp))

            // Legend / breakdown Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TirLegendItem(
                    label = "Very Low",
                    percent = stats.timeVeryLowPercent,
                    color = clinicalColors.veryLow
                )
                TirLegendItem(
                    label = "Low",
                    percent = stats.timeBelowPercent - stats.timeVeryLowPercent,
                    color = clinicalColors.low
                )
                TirLegendItem(
                    label = "In Target",
                    percent = stats.timeInRangePercent,
                    color = clinicalColors.inRange
                )
                TirLegendItem(
                    label = "High",
                    percent = stats.timeAbovePercent - stats.timeVeryHighPercent,
                    color = clinicalColors.high
                )
                TirLegendItem(
                    label = "Very High",
                    percent = stats.timeVeryHighPercent,
                    color = clinicalColors.veryHigh
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4 Stat Tiles Grid: Average, GMI, Min, Max
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatTile(
                    title = "Average",
                    value = if (stats.averageMgDl > 0) unit.format(stats.averageMgDl) else "—",
                    unit = unit.label,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    title = "Est. A1C (GMI)",
                    value = if (stats.estimatedA1c > 0) String.format(java.util.Locale.US, "%.1f", stats.estimatedA1c) else "—",
                    unit = "%",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    title = "Min",
                    value = if (stats.minMgDl > 0) unit.format(stats.minMgDl) else "—",
                    unit = unit.label,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    title = "Max",
                    value = if (stats.maxMgDl > 0) unit.format(stats.maxMgDl) else "—",
                    unit = unit.label,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TimeInRangeBar(stats: GlucoseStats) {
    val clinicalColors = LocalClinicalColors.current

    val vLow = stats.timeVeryLowPercent.coerceAtLeast(0)
    val low = (stats.timeBelowPercent - stats.timeVeryLowPercent).coerceAtLeast(0)
    val inRange = stats.timeInRangePercent.coerceAtLeast(0)
    val high = (stats.timeAbovePercent - stats.timeVeryHighPercent).coerceAtLeast(0)
    val vHigh = stats.timeVeryHighPercent.coerceAtLeast(0)

    val total = (vLow + low + inRange + high + vHigh).coerceAtLeast(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (vLow > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(vLow.toFloat() / total)
                    .background(clinicalColors.veryLow)
            )
        }
        if (low > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(low.toFloat() / total)
                    .background(clinicalColors.low)
            )
        }
        if (inRange > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(inRange.toFloat() / total)
                    .background(clinicalColors.inRange)
            )
        }
        if (high > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(high.toFloat() / total)
                    .background(clinicalColors.high)
            )
        }
        if (vHigh > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(vHigh.toFloat() / total)
                    .background(clinicalColors.veryHigh)
            )
        }
    }
}

@Composable
private fun TirLegendItem(
    label: String,
    percent: Int,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${percent.coerceAtLeast(0)}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatTile(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
