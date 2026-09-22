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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import tk.glucodata.ui.theme.DarkClinicalColors
import tk.glucodata.ui.theme.LocalClinicalColors
import kotlin.math.roundToInt

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
    val scope = rememberCoroutineScope()

    var showWebServerActivationDialog by remember { mutableStateOf(false) }
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }

    fun openWebReport() {
        try {
            if (Applic.Nativesloaded) {
                try {
                    Natives.analysedays(selectedPeriod.days, useHistory)
                } catch (_: Throwable) {}
            }
            var endtime = try {
                Natives.percentileEndtime(selectedPeriod.days)
            } catch (_: Throwable) { 0L }
            if (endtime < 1577829600L) {
                endtime = System.currentTimeMillis() / 1000L
            }
            val key = try { Natives.getApiSecret() ?: "" } catch (_: Throwable) { "" }
            val addkey = if (key.isNotEmpty()) "$key/" else ""
            val effectiveHistory = if (Applic.Nativesloaded) {
                try { Natives.getAnalysehistory() } catch (_: Throwable) { useHistory }
            } else useHistory
            val type = (if (Natives.getDoCalibrate()) (if (Natives.getCalibratePast()) "&pastvalues" else "") + "&calibrated" else "&") + if (effectiveHistory) "history" else "stream"
            val rawPort = try { Natives.gethttpport() } catch (_: Throwable) { 17580 }
            val port = if (rawPort > 0) rawPort else 17580
            val url = "http://127.0.0.1:$port/$addkey" + "x/report?amounts&days=${selectedPeriod.days}&endtime=$endtime$type&hl=${Applic.curlang}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            Toast.makeText(context, "Report error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    ScreenContent(modifier = modifier) {
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

        // 2. Clinical KPI Metrics Grid
        ClinicalKpiCards(
            stats = stats,
            unit = unit,
            onShowInfo = { title, text ->
                infoDialogTitle = title
                infoDialogText = text
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // 3. Time in Range Breakdown
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // 4. Ambulatory Glucose Profile (AGP)
        Column(modifier = Modifier.fillMaxWidth()) {
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
                val agpTitle = stringResource(R.string.help_agp_title)
                val agpDesc = stringResource(R.string.help_agp_desc)
                IconButton(
                    onClick = {
                        infoDialogTitle = agpTitle
                        infoDialogText = agpDesc
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.QueryStats,
                        contentDescription = agpTitle,
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // 5. Actions: Web Report & Export
        Column(modifier = Modifier.fillMaxWidth()) {
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

    if (showWebServerActivationDialog) {
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
                        scope.launch {
                            val rawPort = try { Natives.gethttpport() } catch (_: Throwable) { 17580 }
                            val port = if (rawPort > 0) rawPort else 17580
                            withContext(Dispatchers.IO) {
                                var ready = false
                                val start = System.currentTimeMillis()
                                while (!ready && System.currentTimeMillis() - start < 2500) {
                                    try {
                                        java.net.Socket("127.0.0.1", port).use { ready = true }
                                    } catch (_: Throwable) {
                                        delay(100)
                                    }
                                }
                            }
                            openWebReport()
                        }
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

    // Values that miss their clinical target are tinted instead of carrying a badge
    val onTarget = MaterialTheme.colorScheme.onSurface
    val avgColor = when {
        !hasData || stats.averageMgDl in 70f..154f -> onTarget
        stats.averageMgDl > 154f -> clinicalColors.high
        else -> clinicalColors.low
    }
    val gmiColor = if (!hasData || stats.estimatedA1c <= 0f || stats.estimatedA1c in 4.0f..7.0f) onTarget else clinicalColors.high
    val cvColor = if (!hasData || stats.cvPercent <= 0f || stats.cvPercent < 36f) onTarget else clinicalColors.veryHigh
    val activeColor = if (!hasData || stats.activeTimePercent >= 70f) onTarget else clinicalColors.high

    val gmiTitle = stringResource(R.string.help_gmi_title)
    val gmiDesc = stringResource(R.string.help_gmi_desc)
    val cvTitle = stringResource(R.string.help_cv_title)
    val cvDesc = stringResource(R.string.help_cv_desc)
    val avgTitle = stringResource(R.string.help_avg_title)
    val avgDesc = stringResource(R.string.help_avg_desc)
    val activeTitle = stringResource(R.string.help_active_title)
    val activeDesc = stringResource(R.string.help_active_desc)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            KpiCard(
                title = stringResource(R.string.kpi_average_glucose),
                value = if (hasData) unit.format(stats.averageMgDl) else "—",
                subtitle = stringResource(R.string.target_average_sub, unit.format(154f)),
                valueColor = avgColor,
                onClick = { onShowInfo(avgTitle, avgDesc) },
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = stringResource(R.string.kpi_gmi),
                value = if (hasData && stats.estimatedA1c > 0) "${String.format(java.util.Locale.US, "%.1f", stats.estimatedA1c)}%" else "—",
                subtitle = stringResource(R.string.target_gmi_sub),
                valueColor = gmiColor,
                onClick = { onShowInfo(gmiTitle, gmiDesc) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            KpiCard(
                title = stringResource(R.string.kpi_cv),
                value = if (hasData && stats.cvPercent > 0) "${String.format(java.util.Locale.US, "%.1f", stats.cvPercent)}%" else "—",
                subtitle = stringResource(R.string.target_cv_sub),
                valueColor = cvColor,
                onClick = { onShowInfo(cvTitle, cvDesc) },
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = stringResource(R.string.kpi_active_time),
                value = if (hasData) "${String.format(java.util.Locale.US, "%.1f", stats.activeTimePercent)}%" else "—",
                subtitle = stringResource(R.string.target_active_sub),
                valueColor = activeColor,
                onClick = { onShowInfo(activeTitle, activeDesc) },
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
    valueColor: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1
        )
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

    Column(modifier = Modifier.fillMaxWidth()) {
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
                    contentDescription = tirTitle,
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

            // 5 Clinical Ranges Breakdown Rows (Vertical descending hierarchy: Top/Very High -> Bottom/Very Low)
            TirRow(
                icon = Icons.Default.KeyboardDoubleArrowUp,
                label = stringResource(R.string.status_very_high),
                rangeDesc = stringResource(R.string.tir_range_greater_than, unit.format(250f)),
                percent = stats.timeVeryHighPercent,
                target = stringResource(R.string.tir_target_very_high),
                color = clinicalColors.veryHigh
            )
            TirRow(
                icon = Icons.Default.ArrowUpward,
                label = stringResource(R.string.status_high),
                rangeDesc = stringResource(R.string.tir_range_between, unit.format(targetHigh + 1f), unit.format(250f)),
                percent = stats.timeAbovePercent,
                target = stringResource(R.string.tir_target_high),
                color = clinicalColors.high
            )
            TirRow(
                icon = Icons.Default.Check,
                label = stringResource(R.string.status_in_range),
                rangeDesc = stringResource(R.string.tir_range_between, unit.format(targetLow), unit.format(targetHigh)),
                percent = stats.timeInRangePercent,
                target = stringResource(R.string.tir_target_in_range),
                color = clinicalColors.inRange,
                isPrimary = true
            )
            TirRow(
                icon = Icons.Default.ArrowDownward,
                label = stringResource(R.string.status_low),
                rangeDesc = stringResource(R.string.tir_range_between, unit.format(54f), unit.format(targetLow - 1f)),
                percent = stats.timeBelowPercent,
                target = stringResource(R.string.tir_target_low),
                color = clinicalColors.low
            )
            TirRow(
                icon = Icons.Default.KeyboardDoubleArrowDown,
                label = stringResource(R.string.status_very_low),
                rangeDesc = stringResource(R.string.tir_range_less_than, unit.format(54f)),
                percent = stats.timeVeryLowPercent,
                target = stringResource(R.string.tir_target_very_low),
                color = clinicalColors.veryLow
            )
        }
    }
}

@Composable
private fun TirRow(
    icon: ImageVector,
    label: String,
    rangeDesc: String,
    percent: Int,
    target: String,
    color: Color,
    isPrimary: Boolean = false
) {
    val totalMinutes = ((percent / 100f) * 24f * 60f).roundToInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.tir_range_with_target, rangeDesc, target),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.tir_percent, percent),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (isPrimary) color else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.tir_duration_format, hours, minutes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun AgpLegend() {
    val isDark = LocalClinicalColors.current == DarkClinicalColors
    val outerRangeColor = if (isDark) Color(0xFF2563EB) else Color(0xFF60A5FA)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        LegendItem(color = Color(0xFF1D4ED8), label = stringResource(R.string.agp_median))
        LegendItem(color = Color(0xFF3B82F6), label = stringResource(R.string.agp_iqr))
        LegendItem(color = outerRangeColor, label = stringResource(R.string.agp_outer_range))
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
