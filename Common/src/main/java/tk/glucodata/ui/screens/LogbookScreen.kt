package tk.glucodata.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.components.LogEntryEditor
import tk.glucodata.ui.components.icon
import tk.glucodata.ui.components.shortLabelRes
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.model.LogType
import tk.glucodata.ui.theme.LocalLogbookColors
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogbookTimeFilter(@androidx.annotation.StringRes val labelRes: Int, val days: Int) {
    TODAY(R.string.logbook_time_today, 1),
    SEVEN_DAYS(R.string.logbook_time_7d, 7),
    FOURTEEN_DAYS(R.string.logbook_time_14d, 14),
    THIRTY_DAYS(R.string.logbook_time_30d, 30),
    ALL(R.string.logbook_time_all, 0)
}

@Composable
fun LogbookScreen(
    repository: GlucoseRepository,
    onOpenAddEntry: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val logs by repository.logs.collectAsState()
    val unit by repository.unit.collectAsState()
    val displayConfig by repository.displayConfig.collectAsState()

    var selectedTypeFilter by remember { mutableStateOf<LogType?>(null) }
    var selectedTimeFilter by remember { mutableStateOf(LogbookTimeFilter.TODAY) }
    var customDays by remember { mutableStateOf<Int?>(null) }
    var showCustomRangeDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<LogRecord?>(null) }

    val logbookColors = LocalLogbookColors.current

    val now = System.currentTimeMillis()
    val timeFilteredLogs = remember(logs, selectedTimeFilter, customDays) {
        if (customDays != null) {
            val cutoff = now - customDays!! * 24 * 3600 * 1000L
            logs.filter { it.timestamp >= cutoff }
        } else {
            when (selectedTimeFilter) {
                LogbookTimeFilter.TODAY -> {
                    val dayStart = now - (now % (24 * 3600 * 1000L))
                    logs.filter { it.timestamp >= dayStart }
                }
                LogbookTimeFilter.SEVEN_DAYS -> {
                    val cutoff = now - 7 * 24 * 3600 * 1000L
                    logs.filter { it.timestamp >= cutoff }
                }
                LogbookTimeFilter.FOURTEEN_DAYS -> {
                    val cutoff = now - 14 * 24 * 3600 * 1000L
                    logs.filter { it.timestamp >= cutoff }
                }
                LogbookTimeFilter.THIRTY_DAYS -> {
                    val cutoff = now - 30 * 24 * 3600 * 1000L
                    logs.filter { it.timestamp >= cutoff }
                }
                LogbookTimeFilter.ALL -> logs
            }
        }
    }

    val finalFilteredLogs = remember(timeFilteredLogs, selectedTypeFilter) {
        if (selectedTypeFilter == null) timeFilteredLogs else timeFilteredLogs.filter { it.type == selectedTypeFilter }
    }

    // Totals for selected time window. One pass, and remembered: this used to filter the whole
    // window three times over and allocate a boxed Double per entry, on every recomposition.
    val (windowBolus, windowBasal, windowCarbs, windowChecks) =
        remember(timeFilteredLogs) {
            var bolus = 0.0
            var basal = 0.0
            var carbs = 0.0
            var checks = 0
            for (entry in timeFilteredLogs) {
                when (entry.type) {
                    LogType.RAPID_INSULIN -> bolus += entry.value
                    LogType.BASAL_INSULIN -> basal += entry.value
                    LogType.CARBS, LogType.MEAL -> carbs += entry.value
                    LogType.BLOOD_GLUCOSE -> checks++
                    LogType.NOTE -> Unit
                }
            }
            WindowTotals(bolus, basal, carbs, checks)
        }

    val totalsTitle = when {
        customDays != null -> stringResource(
            R.string.logbook_totals_custom_days,
            pluralStringResource(R.plurals.day_count, customDays!!, customDays!!)
        )
        selectedTimeFilter == LogbookTimeFilter.TODAY -> stringResource(R.string.logbook_totals_today)
        selectedTimeFilter == LogbookTimeFilter.SEVEN_DAYS -> stringResource(R.string.logbook_totals_7d)
        selectedTimeFilter == LogbookTimeFilter.FOURTEEN_DAYS -> stringResource(R.string.logbook_totals_14d)
        selectedTimeFilter == LogbookTimeFilter.THIRTY_DAYS -> stringResource(R.string.logbook_totals_30d)
        else -> stringResource(R.string.logbook_totals_all)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenLayout.Gutter, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tab_logbook),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.closename)
                        )
                    }
                }
            }

            // 1. Time Interval Selector Pills (Customizable time intervals)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = ScreenLayout.Gutter, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LogbookTimeFilter.entries.forEach { filter ->
                    val isSelected = customDays == null && filter == selectedTimeFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            customDays = null
                            selectedTimeFilter = filter
                        },
                        label = { Text(stringResource(filter.labelRes), fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                // Custom interval chip
                FilterChip(
                    selected = customDays != null,
                    onClick = { showCustomRangeDialog = true },
                    label = {
                        Text(
                            text = if (customDays != null) {
                                pluralStringResource(R.plurals.day_count, customDays!!, customDays!!)
                            } else {
                                stringResource(R.string.timerange_custom)
                            },
                            fontWeight = if (customDays != null) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = stringResource(R.string.custom_range), modifier = Modifier.size(14.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            // 2. Summary KPI Row for selected time window
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenLayout.Gutter, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(ScreenLayout.CardPadding)) {
                    Text(
                        text = totalsTitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DailyTotalPill(
                            label = stringResource(R.string.log_short_bolus),
                            value = stringResource(R.string.log_value_insulin, String.format(Locale.getDefault(), "%.1f", windowBolus)),
                            color = logbookColors.bolus.primary
                        )
                        DailyTotalPill(
                            label = stringResource(R.string.log_short_basal),
                            value = stringResource(R.string.log_value_insulin, String.format(Locale.getDefault(), "%.1f", windowBasal)),
                            color = logbookColors.basal.primary
                        )
                        DailyTotalPill(
                            label = stringResource(R.string.log_short_carbs),
                            value = stringResource(R.string.log_value_carbs, windowCarbs.toInt().toString()),
                            color = logbookColors.carbs.primary
                        )
                        DailyTotalPill(
                            label = stringResource(R.string.log_type_finger_prick),
                            value = windowChecks.toString(),
                            color = logbookColors.bloodGlucose.primary
                        )
                    }
                }
            }

            // 3. Filter Chips Header (Category filter: Bolus, Basal, Carbs, BG)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = ScreenLayout.Gutter, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTypeFilter == null,
                    onClick = { selectedTypeFilter = null },
                    label = { Text(stringResource(R.string.log_all), fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedTypeFilter == LogType.RAPID_INSULIN,
                    onClick = { selectedTypeFilter = if (selectedTypeFilter == LogType.RAPID_INSULIN) null else LogType.RAPID_INSULIN },
                    label = { Text(stringResource(R.string.log_short_bolus), fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedTypeFilter == LogType.BASAL_INSULIN,
                    onClick = { selectedTypeFilter = if (selectedTypeFilter == LogType.BASAL_INSULIN) null else LogType.BASAL_INSULIN },
                    label = { Text(stringResource(R.string.log_short_basal), fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedTypeFilter == LogType.CARBS,
                    onClick = { selectedTypeFilter = if (selectedTypeFilter == LogType.CARBS) null else LogType.CARBS },
                    label = { Text(stringResource(R.string.log_short_carbs), fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedTypeFilter == LogType.BLOOD_GLUCOSE,
                    onClick = { selectedTypeFilter = if (selectedTypeFilter == LogType.BLOOD_GLUCOSE) null else LogType.BLOOD_GLUCOSE },
                    label = { Text(stringResource(R.string.log_short_bg), fontSize = 12.sp) }
                )
            }

            if (finalFilteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.logbook_no_records_range),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = ScreenLayout.Gutter,
                        end = ScreenLayout.Gutter,
                        top = 6.dp,
                        bottom = ScreenLayout.BottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(finalFilteredLogs, key = { index, item -> "${item.id}_${item.timestamp}_$index" }) { index, item ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                        LogItemCard(
                            record = item,
                            unit = unit,
                            minimalistUnits = displayConfig.minimalistUnits,
                            onClick = if (item.nativeSource != null) ({ editingEntry = item }) else null
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onOpenAddEntry,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.logbook_add_description))
        }
    }

    if (showCustomRangeDialog) {
        CustomLogbookRangeDialog(
            currentDays = customDays ?: 14,
            onDismiss = { showCustomRangeDialog = false },
            onApply = { days ->
                showCustomRangeDialog = false
                customDays = days
            }
        )
    }

    editingEntry?.let { entry ->
        LogEntryEditor(
            repository = repository,
            entry = entry,
            onDismiss = { editingEntry = null }
        )
    }
}

@Composable
fun DailyTotalPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

/**
 * One logbook row. Tapping it opens the entry in the editor, which is also where it is deleted,
 * so a stray tap in the list can never remove anything.
 */
@Composable
fun LogItemCard(
    record: LogRecord,
    unit: GlucoseUnit,
    minimalistUnits: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val locale = Locale.getDefault()
    val timeFormat: DateFormat = remember(locale) { DateFormat.getTimeInstance(DateFormat.SHORT, locale) }
    val dateFormat = remember(locale) { SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd"), locale) }
    val itemColors = LocalLogbookColors.current.forType(record.type)

    val valueDisplay = when (record.type) {
        LogType.RAPID_INSULIN, LogType.BASAL_INSULIN -> stringResource(
            R.string.log_value_insulin,
            String.format(locale, "%.1f", record.value)
        )
        LogType.CARBS, LogType.MEAL -> stringResource(R.string.log_value_carbs, record.value.toInt().toString())
        LogType.BLOOD_GLUCOSE -> if (minimalistUnits) {
            unit.format(record.value)
        } else {
            stringResource(R.string.log_glucose_value, unit.format(record.value), stringResource(unit.labelRes))
        }
        LogType.NOTE -> if (record.value > 0) record.value.toString() else ""
    }

    val time = timeFormat.format(Date(record.timestamp))
    val isToday = remember(record.timestamp) { DateUtils.isToday(record.timestamp) }
    val whenText = if (isToday) time else stringResource(
        R.string.logbook_date_at_time,
        dateFormat.format(Date(record.timestamp)),
        time
    )
    val subtitle = if (record.note.isBlank()) whenText else "$whenText · ${record.note}"

    // Flat M3 list row placed directly on the page: no Card container, no
    // elevation. Parents separate rows with HorizontalDivider.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(itemColors.container, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = record.type.icon,
                contentDescription = null,
                tint = itemColors.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(record.type.shortLabelRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = valueDisplay,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = itemColors.primary
        )
    }
}

@Composable
fun CustomLogbookRangeDialog(
    currentDays: Int,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit
) {
    var daysSlider by remember { mutableFloatStateOf(currentDays.coerceIn(1, 90).toFloat()) }
    val quickDays = listOf(3, 7, 14, 21, 30, 60, 90)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                                text = stringResource(R.string.custom_logbook_range_title),
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
                    text = stringResource(R.string.custom_logbook_range_desc),
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
                    quickDays.forEach { d ->
                        val isSelected = daysSlider.toInt() == d
                        FilterChip(
                            selected = isSelected,
                            onClick = { daysSlider = d.toFloat() },
                            label = { Text(pluralStringResource(R.plurals.day_count, d, d), fontSize = 11.sp) }
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
                        text = stringResource(R.string.logbook_days_included),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = pluralStringResource(R.plurals.day_count, daysSlider.toInt(), daysSlider.toInt()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = daysSlider,
                    onValueChange = { daysSlider = it },
                    valueRange = 1f..90f,
                    steps = 88
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

/** Totals for the currently selected time window, computed in a single pass over the entries. */
private data class WindowTotals(
    val bolus: Double,
    val basal: Double,
    val carbs: Double,
    val checks: Int
)
