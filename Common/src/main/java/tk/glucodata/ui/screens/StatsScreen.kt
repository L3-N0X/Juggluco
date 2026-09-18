package tk.glucodata.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.Applic
import tk.glucodata.Natives
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.graph.AgpGraph
import tk.glucodata.ui.model.GlucoseStats
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.HourlyPercentiles
import tk.glucodata.ui.model.StatsPeriod
import tk.glucodata.ui.theme.LocalClinicalColors

@Composable
fun StatsScreen(
    repository: GlucoseRepository,
    onExportData: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val stats by repository.stats.collectAsState()
    val agpProfile by repository.agpProfile.collectAsState()
    val selectedPeriod by repository.statsPeriod.collectAsState()
    val useHistory by repository.statsUseHistory.collectAsState()
    val unit by repository.unit.collectAsState()
    val targetLow by repository.targetLow.collectAsState()
    val targetHigh by repository.targetHigh.collectAsState()
    val context = LocalContext.current

    var inspectedHour by remember { mutableStateOf<HourlyPercentiles?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 96.dp)
    ) {
        Text(
            text = "Statistics & AGP",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Clinical metrics and Ambulatory Glucose Profile",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Period Selector Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsPeriod.entries.forEach { period ->
                val isSelected = period == selectedPeriod
                FilterChip(
                    selected = isSelected,
                    onClick = { repository.setStatsPeriod(period) },
                    label = { Text(period.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Stream vs History Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Data Source",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (!useHistory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { repository.setStatsUseHistory(false) }
                ) {
                    Text(
                        text = "Real-time Stream",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (!useHistory) FontWeight.Bold else FontWeight.Normal,
                        color = if (!useHistory) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (useHistory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { repository.setStatsUseHistory(true) }
                ) {
                    Text(
                        text = "Sensor History",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (useHistory) FontWeight.Bold else FontWeight.Normal,
                        color = if (useHistory) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Clinical KPI Metrics Grid
        ClinicalKpiCards(stats = stats, unit = unit)

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Time in Range Breakdown Card
        TimeInRangeBreakdownCard(stats = stats, unit = unit, targetLow = targetLow, targetHigh = targetHigh)

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Ambulatory Glucose Profile (AGP) Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Ambulatory Glucose Profile (AGP)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "24-hour composite curve across ${selectedPeriod.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.QueryStats,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Inspected hour display banner
                if (inspectedHour != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "Time: %02d:00", inspectedHour!!.hour),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Median: ${unit.format(inspectedHour!!.p50)} ${unit.label} • 50% range: ${unit.format(inspectedHour!!.p25)}-${unit.format(inspectedHour!!.p75)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                AgpGraph(
                    profile = agpProfile,
                    unit = unit,
                    targetLow = targetLow,
                    targetHigh = targetHigh,
                    onHourInspected = { inspectedHour = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Legend
                AgpLegend()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Actions Card: Web Report & Export
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Reports & Export",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Generate clinical summary or export raw records",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                if (Applic.Nativesloaded && Natives.getusexdripwebserver()) {
                                    val endtime = Natives.percentileEndtime(selectedPeriod.days)
                                    val key = Natives.getApiSecret() ?: ""
                                    val addkey = if (key.isNotEmpty()) "$key/" else ""
                                    val type = (if (Natives.getDoCalibrate()) (if (Natives.getCalibratePast()) "&pastvalues" else "") + "&calibrated" else "&") + if (useHistory) "history" else "stream"
                                    val url = "http://127.0.0.1:${Natives.gethttpport()}/$addkey" + "x/report?amounts&days=${selectedPeriod.days}&endtime=$endtime$type&hl=${Applic.curlang}"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(context, "Local webserver is offline. Enable it in Settings > Exchanges.", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Throwable) {
                                Toast.makeText(context, "Report error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Web Report", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onExportData,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export CSV", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClinicalKpiCards(stats: GlucoseStats, unit: GlucoseUnit) {
    val clinicalColors = LocalClinicalColors.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                title = "Average Glucose",
                value = if (stats.averageMgDl > 0) "${unit.format(stats.averageMgDl)} ${unit.label}" else "--",
                subtitle = "Goal: < ${unit.format(154f)}",
                badge = if (stats.averageMgDl in 70f..154f) "In Target" else if (stats.averageMgDl > 154f) "Above" else "Low",
                badgeColor = if (stats.averageMgDl in 70f..154f) clinicalColors.inRange else clinicalColors.high,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "GMI (est. HbA1c)",
                value = if (stats.estimatedA1c > 0) "${String.format(java.util.Locale.US, "%.1f", stats.estimatedA1c)}%" else "--",
                subtitle = "Clinical target: < 7.0%",
                badge = if (stats.estimatedA1c in 4.0f..7.0f) "Normal" else "Elevated",
                badgeColor = if (stats.estimatedA1c in 4.0f..7.0f) clinicalColors.inRange else clinicalColors.high,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                title = "Variability (CV)",
                value = "${String.format(java.util.Locale.US, "%.1f", stats.cvPercent)}%",
                subtitle = "Target: < 36.0%",
                badge = if (stats.cvPercent < 36f) "Stable" else "Variable",
                badgeColor = if (stats.cvPercent < 36f) clinicalColors.inRange else clinicalColors.veryHigh,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Active CGM Time",
                value = "${String.format(java.util.Locale.US, "%.1f", stats.activeTimePercent)}%",
                subtitle = "Target: > 70.0%",
                badge = if (stats.activeTimePercent >= 70f) "Good" else "Low",
                badgeColor = if (stats.activeTimePercent >= 70f) clinicalColors.inRange else clinicalColors.high,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun TimeInRangeBreakdownCard(
    stats: GlucoseStats,
    unit: GlucoseUnit,
    targetLow: Float,
    targetHigh: Float
) {
    val clinicalColors = LocalClinicalColors.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Time In Range (TIR)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "International Consensus Guidelines",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-segment horizontal stacked bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (stats.timeVeryLowPercent > 0) {
                    Box(modifier = Modifier.weight(stats.timeVeryLowPercent.coerceAtLeast(1).toFloat()).fillMaxSize().background(clinicalColors.veryLow))
                }
                if (stats.timeBelowPercent > 0) {
                    Box(modifier = Modifier.weight(stats.timeBelowPercent.coerceAtLeast(1).toFloat()).fillMaxSize().background(clinicalColors.low))
                }
                if (stats.timeInRangePercent > 0) {
                    Box(modifier = Modifier.weight(stats.timeInRangePercent.coerceAtLeast(1).toFloat()).fillMaxSize().background(clinicalColors.inRange))
                }
                if (stats.timeAbovePercent > 0) {
                    Box(modifier = Modifier.weight(stats.timeAbovePercent.coerceAtLeast(1).toFloat()).fillMaxSize().background(clinicalColors.high))
                }
                if (stats.timeVeryHighPercent > 0) {
                    Box(modifier = Modifier.weight(stats.timeVeryHighPercent.coerceAtLeast(1).toFloat()).fillMaxSize().background(clinicalColors.veryHigh))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5 Clinical Ranges Breakdown Rows
            TirRow(
                label = "Very High",
                rangeDesc = "> ${unit.format(250f)} ${unit.label}",
                percent = stats.timeVeryHighPercent,
                target = "< 5%",
                color = clinicalColors.veryHigh
            )
            TirRow(
                label = "High",
                rangeDesc = "${unit.format(targetHigh + 1f)} - ${unit.format(250f)} ${unit.label}",
                percent = stats.timeAbovePercent,
                target = "< 25%",
                color = clinicalColors.high
            )
            TirRow(
                label = "In Target Range",
                rangeDesc = "${unit.format(targetLow)} - ${unit.format(targetHigh)} ${unit.label}",
                percent = stats.timeInRangePercent,
                target = "> 70%",
                color = clinicalColors.inRange,
                isPrimary = true
            )
            TirRow(
                label = "Low",
                rangeDesc = "${unit.format(54f)} - ${unit.format(targetLow - 1f)} ${unit.label}",
                percent = stats.timeBelowPercent,
                target = "< 4%",
                color = clinicalColors.low
            )
            TirRow(
                label = "Very Low",
                rangeDesc = "< ${unit.format(54f)} ${unit.label}",
                percent = stats.timeVeryLowPercent,
                target = "< 1%",
                color = clinicalColors.veryLow
            )
        }
    }
}

@Composable
private fun TirRow(
    label: String,
    rangeDesc: String,
    percent: Int,
    target: String,
    color: Color,
    isPrimary: Boolean = false
) {
    val hoursPerDay = (percent / 100f) * 24f
    val hours = hoursPerDay.toInt()
    val minutes = ((hoursPerDay - hours) * 60).toInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = rangeDesc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$percent% (${hours}h ${minutes}m)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isPrimary) color else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Target: $target",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun AgpLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        LegendItem(color = Color(0xFF1D4ED8), label = "50% Median")
        LegendItem(color = Color(0xFF3B82F6), label = "25-75% Range")
        LegendItem(color = Color(0xFF60A5FA), label = "10-90% Range")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
