package tk.glucodata.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.model.GlucoseStats
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.screens.ScreenLayout
import tk.glucodata.ui.theme.LocalClinicalColors

@Composable
fun GlucoseStatsCard(
    stats: GlucoseStats,
    unit: GlucoseUnit,
    timeRangeLabel: String = "",
    minimalistUnits: Boolean = true,
    modifier: Modifier = Modifier
) {
    val clinicalColors = LocalClinicalColors.current
    val hasData = stats.readingsCount > 0 && stats.averageMgDl > 0f
    val glucoseUnitLabel = if (!minimalistUnits) unit.label else ""

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(ScreenLayout.CardPadding)
        ) {
            // Header: Title & Time in target
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tir_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (hasData) {
                    Text(
                        text = "${stats.timeInRangePercent}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = clinicalColors.inRange
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!hasData) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.tir_no_readings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Multi-segment Time in Range Bar
                TimeInRangeBar(stats = stats)

                Spacer(modifier = Modifier.height(10.dp))

                // Concise Legend / Breakdown Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TirLegendItem(
                        label = stringResource(R.string.status_very_low),
                        percent = stats.timeVeryLowPercent,
                        color = clinicalColors.veryLow
                    )
                    TirLegendItem(
                        label = stringResource(R.string.status_low),
                        percent = (stats.timeBelowPercent - stats.timeVeryLowPercent).coerceAtLeast(0),
                        color = clinicalColors.low
                    )
                    TirLegendItem(
                        label = stringResource(R.string.status_in_range),
                        percent = stats.timeInRangePercent,
                        color = clinicalColors.inRange
                    )
                    TirLegendItem(
                        label = stringResource(R.string.status_high),
                        percent = (stats.timeAbovePercent - stats.timeVeryHighPercent).coerceAtLeast(0),
                        color = clinicalColors.high
                    )
                    TirLegendItem(
                        label = stringResource(R.string.status_very_high),
                        percent = stats.timeVeryHighPercent,
                        color = clinicalColors.veryHigh
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4 Equal-Height Stat Tiles Grid: Average, GMI, Min, Max
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatTile(
                        title = stringResource(R.string.stat_average),
                        value = unit.format(stats.averageMgDl),
                        unit = glucoseUnitLabel,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        title = stringResource(R.string.stat_gmi),
                        value = if (stats.estimatedA1c > 0) String.format(java.util.Locale.US, "%.1f", stats.estimatedA1c) else "—",
                        unit = if (stats.estimatedA1c > 0) "%" else "",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        title = stringResource(R.string.stat_min),
                        value = unit.format(stats.minMgDl),
                        unit = glucoseUnitLabel,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        title = stringResource(R.string.stat_max),
                        value = unit.format(stats.maxMgDl),
                        unit = glucoseUnitLabel,
                        modifier = Modifier.weight(1f)
                    )
                }
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
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp))
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
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
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
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(1.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            }
        }
    }
}
