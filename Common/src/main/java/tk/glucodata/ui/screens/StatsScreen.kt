package tk.glucodata.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.Applic
import tk.glucodata.Natives
import tk.glucodata.R
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
    modifier: Modifier = Modifier,
    onExportData: () -> Unit = {},
    onOpenWebServerSettings: () -> Unit = {}
) {
    val stats by repository.stats.collectAsState()
    val agpProfile by repository.agpProfile.collectAsState()
    val selectedPeriod by repository.statsPeriod.collectAsState()
    val useHistory by repository.statsUseHistory.collectAsState()
    val unit by repository.unit.collectAsState()
    val targetLow by repository.targetLow.collectAsState()
    val targetHigh by repository.targetHigh.collectAsState()
    val context = LocalContext.current

    var showWebServerActivationDialog by remember { mutableStateOf(false) }
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }

    ScreenContent(modifier = modifier, spacing = 12.dp) {
        var showCustomPeriodDialog by remember { mutableStateOf(false) }

        // 1. Period Selector Pills (Horizontally scrollable to prevent wrapping on small screens)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatsPeriod.PRESETS.forEach { period ->
                val isSelected = period == selectedPeriod
                FilterChip(
                    selected = isSelected,
                    onClick = { repository.setStatsPeriod(period) },
                    label = {
                        Text(
                            text = if (period.labelRes != null) stringResource(period.labelRes) else period.label,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            // Custom period selector chip
            val isCustomPeriod = selectedPeriod.isCustom && StatsPeriod.PRESETS.none { it.days == selectedPeriod.days }
            FilterChip(
                selected = isCustomPeriod,
                onClick = { showCustomPeriodDialog = true },
                label = {
                    Text(
                        text = if (isCustomPeriod) {
                            "${selectedPeriod.days} ${stringResource(R.string.days)}"
                        } else {
                            stringResource(R.string.timerange_custom)
                        },
                        fontSize = 12.sp,
                        fontWeight = if (isCustomPeriod) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.custom_range),
                        modifier = Modifier.size(14.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }

        if (showCustomPeriodDialog) {
            CustomStatsPeriodDialog(
                currentDays = selectedPeriod.days,
                onDismiss = { showCustomPeriodDialog = false },
                onApply = { days ->
                    showCustomPeriodDialog = false
                    repository.setStatsPeriod(StatsPeriod.fromDays(days))
                }
            )
        }

        // Stream vs History Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.data_source),
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
                        text = stringResource(R.string.realtime_stream),
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
                        text = stringResource(R.string.sensor_history),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (useHistory) FontWeight.Bold else FontWeight.Normal,
                        color = if (useHistory) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 2. Clinical KPI Metrics Grid
        ClinicalKpiCards(
            stats = stats,
            unit = unit,
            onShowInfo = { title, text ->
                infoDialogTitle = title
                infoDialogText = text
            }
        )

        // 3. Time in Range Breakdown Card
        TimeInRangeBreakdownCard(
            stats = stats,
            unit = unit,
            targetLow = targetLow,
            targetHigh = targetHigh,
            onShowInfo = { title, text ->
                infoDialogTitle = title
                infoDialogText = text
            }
        )

        // 4. Ambulatory Glucose Profile (AGP) Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(ScreenLayout.CardPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.agp_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.agp_card_subtitle, if (selectedPeriod.labelRes != null) stringResource(selectedPeriod.labelRes!!) else selectedPeriod.label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            infoDialogTitle = "Ambulatory Glucose Profile (AGP)"
                            infoDialogText = "AGP collapses multiple days of continuous glucose data into a single 24-hour composite day. The dark blue median curve represents your typical trend, the 25–75% interquartile band shows frequent fluctuations, and the 10–90% band captures outer glycemic excursions."
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueryStats,
                            contentDescription = "AGP Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                AgpGraph(
                    profile = agpProfile,
                    unit = unit,
                    targetLow = targetLow,
                    targetHigh = targetHigh
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Legend
                AgpLegend()
            }
        }

        // 5. Actions Card: Web Report & Export
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(ScreenLayout.CardPadding)) {
                Text(
                    text = stringResource(R.string.reports_export_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.reports_export_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                fun openWebReport() {
                    try {
                        val endtime = Natives.percentileEndtime(selectedPeriod.days)
                        val key = Natives.getApiSecret() ?: ""
                        val addkey = if (key.isNotEmpty()) "$key/" else ""
                        val type = (if (Natives.getDoCalibrate()) (if (Natives.getCalibratePast()) "&pastvalues" else "") + "&calibrated" else "&") + if (useHistory) "history" else "stream"
                        val url = "http://127.0.0.1:${Natives.gethttpport()}/$addkey" + "x/report?amounts&days=${selectedPeriod.days}&endtime=$endtime$type&hl=${Applic.curlang}"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Throwable) {
                        Toast.makeText(context, "Report error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (Applic.Nativesloaded && Natives.getusexdripwebserver()) {
                                openWebReport()
                            } else {
                                showWebServerActivationDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.btn_web_report),
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onExportData,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.btn_export_data),
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    if (showWebServerActivationDialog) {
        fun launchReportAfterServerEnable() {
            try {
                val endtime = Natives.percentileEndtime(selectedPeriod.days)
                val key = Natives.getApiSecret() ?: ""
                val addkey = if (key.isNotEmpty()) "$key/" else ""
                val type = (if (Natives.getDoCalibrate()) (if (Natives.getCalibratePast()) "&pastvalues" else "") + "&calibrated" else "&") + if (useHistory) "history" else "stream"
                val url = "http://127.0.0.1:${Natives.gethttpport()}/$addkey" + "x/report?amounts&days=${selectedPeriod.days}&endtime=$endtime$type&hl=${Applic.curlang}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Throwable) {
                Toast.makeText(context, "Report error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog(
            onDismissRequest = { showWebServerActivationDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.web_report_server_required_title),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.web_report_server_required_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWebServerActivationDialog = false
                        repository.setXdripWebServer(true)
                        try {
                            Natives.setusexdripwebserver(true)
                        } catch (_: Throwable) {}
                        launchReportAfterServerEnable()
                    }
                ) {
                    Text(stringResource(R.string.web_report_activate_and_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWebServerActivationDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (infoDialogTitle != null && infoDialogText != null) {
        AlertDialog(
            onDismissRequest = {
                infoDialogTitle = null
                infoDialogText = null
            },
            title = {
                Text(text = infoDialogTitle!!, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = infoDialogText!!, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = {
                    infoDialogTitle = null
                    infoDialogText = null
                }) {
                    Text(stringResource(R.string.closename))
                }
            }
        )
    }
}

@Composable
private fun ClinicalKpiCards(
    stats: GlucoseStats,
    unit: GlucoseUnit,
    onShowInfo: (String, String) -> Unit = { _, _ -> }
) {
    val clinicalColors = LocalClinicalColors.current
    val hasData = stats.readingsCount > 0 && stats.averageMgDl > 0f

    val avgBadge = when {
        !hasData -> ""
        stats.averageMgDl in 70f..154f -> stringResource(R.string.clinical_target_reached)
        stats.averageMgDl > 154f -> stringResource(R.string.status_high)
        else -> stringResource(R.string.status_low)
    }
    val avgBadgeColor = if (stats.averageMgDl in 70f..154f) clinicalColors.inRange else clinicalColors.high

    val gmiBadge = when {
        !hasData || stats.estimatedA1c <= 0f -> ""
        stats.estimatedA1c in 4.0f..7.0f -> stringResource(R.string.clinical_target_reached)
        else -> stringResource(R.string.status_high)
    }
    val gmiBadgeColor = if (stats.estimatedA1c in 4.0f..7.0f) clinicalColors.inRange else clinicalColors.high

    val cvBadge = when {
        !hasData || stats.cvPercent <= 0f -> ""
        stats.cvPercent < 36f -> stringResource(R.string.clinical_stable_cv)
        else -> stringResource(R.string.clinical_high_cv)
    }
    val cvBadgeColor = if (stats.cvPercent < 36f) clinicalColors.inRange else clinicalColors.veryHigh

    val activeBadge = when {
        !hasData -> ""
        stats.activeTimePercent >= 70f -> stringResource(R.string.clinical_target_reached)
        else -> stringResource(R.string.clinical_target_unmet)
    }
    val activeBadgeColor = if (stats.activeTimePercent >= 70f) clinicalColors.inRange else clinicalColors.high

    val gmiTitle = stringResource(R.string.help_gmi_title)
    val gmiDesc = stringResource(R.string.help_gmi_desc)
    val cvTitle = stringResource(R.string.help_cv_title)
    val cvDesc = stringResource(R.string.help_cv_desc)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                title = stringResource(R.string.kpi_average_glucose),
                value = if (hasData) unit.format(stats.averageMgDl) else "—",
                subtitle = stringResource(R.string.target_average_sub, unit.format(154f)),
                badge = avgBadge,
                badgeColor = avgBadgeColor,
                onClick = {
                    onShowInfo(
                        "Average Glucose",
                        "Average glucose over the selected period. Clinical consensus targets an average of < 154 mg/dL (8.5 mmol/L), which corresponds roughly to an estimated HbA1c of < 7.0%."
                    )
                },
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = stringResource(R.string.kpi_gmi),
                value = if (hasData && stats.estimatedA1c > 0) "${String.format(java.util.Locale.US, "%.1f", stats.estimatedA1c)}%" else "—",
                subtitle = stringResource(R.string.target_gmi_sub),
                badge = gmiBadge,
                badgeColor = gmiBadgeColor,
                onClick = { onShowInfo(gmiTitle, gmiDesc) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                title = stringResource(R.string.kpi_cv),
                value = if (hasData && stats.cvPercent > 0) "${String.format(java.util.Locale.US, "%.1f", stats.cvPercent)}%" else "—",
                subtitle = stringResource(R.string.target_cv_sub),
                badge = cvBadge,
                badgeColor = cvBadgeColor,
                onClick = { onShowInfo(cvTitle, cvDesc) },
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = stringResource(R.string.kpi_active_time),
                value = if (hasData) "${String.format(java.util.Locale.US, "%.1f", stats.activeTimePercent)}%" else "—",
                subtitle = stringResource(R.string.target_active_sub),
                badge = activeBadge,
                badgeColor = activeBadgeColor,
                onClick = {
                    onShowInfo(
                        "Active CGM Time",
                        "Percentage of time sensor data was actively recorded. International consensus requires at least 70% wear time over 14 days for a statistically robust and clinically valid Ambulatory Glucose Profile."
                    )
                },
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
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(ScreenLayout.CardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (badge.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TimeInRangeBreakdownCard(
    stats: GlucoseStats,
    unit: GlucoseUnit,
    targetLow: Float,
    targetHigh: Float,
    onShowInfo: (String, String) -> Unit = { _, _ -> }
) {
    val clinicalColors = LocalClinicalColors.current
    val tirTitle = stringResource(R.string.help_tir_title)
    val tirDesc = stringResource(R.string.help_tir_desc)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(ScreenLayout.CardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tir_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.tir_guidelines),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onShowInfo(tirTitle, tirDesc) }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "TIR Info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val hasData = stats.readingsCount > 0 && stats.averageMgDl > 0f

            if (!hasData) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.tir_no_readings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Multi-segment horizontal stacked bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
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
                    label = stringResource(R.string.status_very_high),
                    rangeDesc = "> ${unit.format(250f)}",
                    percent = stats.timeVeryHighPercent,
                    target = "< 5%",
                    color = clinicalColors.veryHigh,
                    isTargetMet = stats.timeVeryHighPercent < 5
                )
                TirRow(
                    label = stringResource(R.string.status_high),
                    rangeDesc = "${unit.format(targetHigh + 1f)} – ${unit.format(250f)}",
                    percent = stats.timeAbovePercent,
                    target = "< 25%",
                    color = clinicalColors.high,
                    isTargetMet = stats.timeAbovePercent < 25
                )
                TirRow(
                    label = stringResource(R.string.status_in_range),
                    rangeDesc = "${unit.format(targetLow)} – ${unit.format(targetHigh)}",
                    percent = stats.timeInRangePercent,
                    target = "> 70%",
                    color = clinicalColors.inRange,
                    isPrimary = true,
                    isTargetMet = stats.timeInRangePercent >= 70
                )
                TirRow(
                    label = stringResource(R.string.status_low),
                    rangeDesc = "${unit.format(54f)} – ${unit.format(targetLow - 1f)}",
                    percent = stats.timeBelowPercent,
                    target = "< 4%",
                    color = clinicalColors.low,
                    isTargetMet = stats.timeBelowPercent < 4
                )
                TirRow(
                    label = stringResource(R.string.status_very_low),
                    rangeDesc = "< ${unit.format(54f)}",
                    percent = stats.timeVeryLowPercent,
                    target = "< 1%",
                    color = clinicalColors.veryLow,
                    isTargetMet = stats.timeVeryLowPercent < 1
                )
            }
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
    isPrimary: Boolean = false,
    isTargetMet: Boolean = false
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$percent% (${hours}h ${minutes}m)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPrimary) color else MaterialTheme.colorScheme.onSurface
                )
                if (isTargetMet) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LocalClinicalColors.current.inRange
                    )
                }
            }
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
        LegendItem(color = Color(0xFF1D4ED8), label = stringResource(R.string.agp_median))
        LegendItem(color = Color(0xFF3B82F6), label = stringResource(R.string.agp_iqr))
        LegendItem(color = Color(0xFF60A5FA), label = stringResource(R.string.agp_outer_range))
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

@Composable
fun CustomStatsPeriodDialog(
    currentDays: Int,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit
) {
    var daysSlider by remember { mutableFloatStateOf(currentDays.coerceIn(1, 180).toFloat()) }
    val quickPresets = listOf(3, 7, 10, 14, 21, 30, 60, 90, 180)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Custom Stats Period",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select time interval for clinical stats, TIR and AGP profile analysis",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = stringResource(R.string.quick_presets),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPresets.forEach { d ->
                        val isSelected = daysSlider.toInt() == d
                        FilterChip(
                            selected = isSelected,
                            onClick = { daysSlider = d.toFloat() },
                            label = { Text("${d}d", fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Days Analyzed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${daysSlider.toInt()} days",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = daysSlider,
                    onValueChange = { daysSlider = it },
                    valueRange = 1f..180f,
                    steps = 178
                )
            }
        },
        confirmButton = {
            Button(onClick = { onApply(daysSlider.toInt()) }) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
