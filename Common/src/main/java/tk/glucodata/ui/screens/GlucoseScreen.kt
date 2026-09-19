package tk.glucodata.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.components.CurrentGlucoseHeroCard
import tk.glucodata.ui.components.GlucoseStatsCard
import tk.glucodata.ui.components.SearchGlucoseDialog
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.graph.GlucoseGraph
import tk.glucodata.ui.graph.TimeRangeSelector
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.LogRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseScreen(
    repository: GlucoseRepository,
    onOpenAddEntry: () -> Unit,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentReading by repository.currentReading.collectAsState()
    val readings by repository.readings.collectAsState()
    val logs by repository.logs.collectAsState()
    val unit by repository.unit.collectAsState()
    val targetLow by repository.targetLow.collectAsState()
    val targetHigh by repository.targetHigh.collectAsState()
    val selectedRange by repository.selectedTimeRange.collectAsState()
    val stats by repository.stats.collectAsState()
    val screenStats by repository.screenStats.collectAsState()
    val sensorDetails by repository.sensorDetails.collectAsState()
    val displayConfig by repository.displayConfig.collectAsState()
    val context = LocalContext.current

    val sensorName = sensorDetails.firstOrNull()?.name
    val previousReading = if (readings.size >= 2) readings[readings.size - 2] else null

    var inspectedPoint by remember { mutableStateOf<GlucosePoint?>(null) }
    val displayReading = inspectedPoint ?: currentReading

    var showLayersSheet by remember { mutableStateOf(false) }
    val layersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showHelpSheet by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedLogForDetail by remember { mutableStateOf<LogRecord?>(null) }

    var showSearchDialog by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchSummaryText by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())

    var isViewingPastData by remember { mutableStateOf(false) }
    var pastWindowStart by remember { mutableLongStateOf(0L) }
    var pastWindowEnd by remember { mutableLongStateOf(0L) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight && maxWidth > 550.dp

        if (isFullscreen) {
            // Fullscreen Graph Mode (Classic landscape OpenGL parity with modern compose visuals)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Top control bar in fullscreen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeRangeSelector(
                        selectedRange = selectedRange,
                        onRangeSelected = { repository.setTimeRange(it) },
                        modifier = Modifier.weight(1f)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = stringResource(R.string.exit_fullscreen),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (isViewingPastData) {
                    PastDataBanner(
                        startTime = pastWindowStart,
                        endTime = pastWindowEnd,
                        onJumpToNow = { repository.jumpToNow() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                GlucoseGraph(
                    readings = readings,
                    logs = logs,
                    timeRange = selectedRange,
                    unit = unit,
                    targetLow = targetLow,
                    targetHigh = targetHigh,
                    displayConfig = displayConfig,
                    onPointInspected = { inspectedPoint = it },
                    onLogEntryClicked = { selectedLogForDetail = it },
                    onWindowChanged = { start, end, isPast ->
                        isViewingPastData = isPast
                        pastWindowStart = start
                        pastWindowEnd = end
                    },
                    onNavigateDays = { delta -> repository.navigateDays(delta) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                GraphNavigationToolbar(
                    isFullscreen = true,
                    onToggleFullscreen = onToggleFullscreen,
                    onJumpToNow = { repository.jumpToNow() },
                    onNavigateDay = { delta -> repository.navigateDays(delta) },
                    onNavigateWeek = { delta -> repository.navigateDays(delta * 7) },
                    onShowLastScan = { repository.showLastScan() },
                    onOpenLayers = { showLayersSheet = true },
                    onOpenSearch = { showSearchDialog = true },
                    onOpenDatePicker = { showDatePicker = true },
                    onOpenHelp = { showHelpSheet = true }
                )
            }
        } else if (isLandscape) {
            // Landscape layout: side-by-side
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                // Left pane: Hero & Stats
                Column(
                    modifier = Modifier
                        .weight(0.40f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 6.dp)
                ) {
                    CurrentGlucoseHeroCard(
                        currentReading = displayReading,
                        previousReading = previousReading,
                        unit = unit,
                        sensorName = sensorName,
                        targetLow = targetLow,
                        targetHigh = targetHigh,
                        minimalistUnits = displayConfig.minimalistUnits
                    )
                    GlucoseStatsCard(
                        stats = screenStats,
                        unit = unit,
                        timeRangeLabel = selectedRange.label,
                        minimalistUnits = displayConfig.minimalistUnits
                    )
                }

                // Right pane: Graph & Controls
                Column(
                    modifier = Modifier
                        .weight(0.60f)
                        .fillMaxHeight()
                        .padding(start = 6.dp)
                ) {
                    TimeRangeSelector(
                        selectedRange = selectedRange,
                        onRangeSelected = { repository.setTimeRange(it) }
                    )

                    if (isViewingPastData) {
                        PastDataBanner(
                            startTime = pastWindowStart,
                            endTime = pastWindowEnd,
                            onJumpToNow = { repository.jumpToNow() }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (isSearchActive) {
                        SearchActiveBar(
                            summaryText = searchSummaryText,
                            onPrev = { repository.prevSearchMatch() },
                            onNext = { repository.nextSearchMatch() },
                            onClose = {
                                isSearchActive = false
                                repository.stopSearch()
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    GlucoseGraph(
                        readings = readings,
                        logs = logs,
                        timeRange = selectedRange,
                        unit = unit,
                        targetLow = targetLow,
                        targetHigh = targetHigh,
                        displayConfig = displayConfig,
                        onPointInspected = { inspectedPoint = it },
                        onLogEntryClicked = { selectedLogForDetail = it },
                        onWindowChanged = { start, end, isPast ->
                            isViewingPastData = isPast
                            pastWindowStart = start
                            pastWindowEnd = end
                        },
                        onNavigateDays = { delta -> repository.navigateDays(delta) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    GraphNavigationToolbar(
                        isFullscreen = false,
                        onToggleFullscreen = onToggleFullscreen,
                        onJumpToNow = { repository.jumpToNow() },
                        onNavigateDay = { delta -> repository.navigateDays(delta) },
                        onNavigateWeek = { delta -> repository.navigateDays(delta * 7) },
                        onShowLastScan = { repository.showLastScan() },
                        onOpenLayers = { showLayersSheet = true },
                        onOpenSearch = { showSearchDialog = true },
                        onOpenDatePicker = { showDatePicker = true },
                        onOpenHelp = { showHelpSheet = true }
                    )
                }
            }
        } else {
            // Portrait layout: vertical scroll
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 96.dp)
            ) {
                // 1. Hero Card at top
                CurrentGlucoseHeroCard(
                    currentReading = displayReading,
                    previousReading = previousReading,
                    unit = unit,
                    sensorName = sensorName,
                    targetLow = targetLow,
                    targetHigh = targetHigh,
                    minimalistUnits = displayConfig.minimalistUnits
                )

                // 2. Time Range Selector pills
                TimeRangeSelector(
                    selectedRange = selectedRange,
                    onRangeSelected = { repository.setTimeRange(it) }
                )

                // Past Data banner if user navigated into history
                if (isViewingPastData) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        PastDataBanner(
                            startTime = pastWindowStart,
                            endTime = pastWindowEnd,
                            onJumpToNow = { repository.jumpToNow() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Search Active Banner (if searching)
                if (isSearchActive) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SearchActiveBar(
                            summaryText = searchSummaryText,
                            onPrev = { repository.prevSearchMatch() },
                            onNext = { repository.nextSearchMatch() },
                            onClose = {
                                isSearchActive = false
                                repository.stopSearch()
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 3. Interactive Jetpack Compose Canvas Glucose Graph
                GlucoseGraph(
                    readings = readings,
                    logs = logs,
                    timeRange = selectedRange,
                    unit = unit,
                    targetLow = targetLow,
                    targetHigh = targetHigh,
                    displayConfig = displayConfig,
                    onPointInspected = { inspectedPoint = it },
                    onLogEntryClicked = { selectedLogForDetail = it },
                    onWindowChanged = { start, end, isPast ->
                        isViewingPastData = isPast
                        pastWindowStart = start
                        pastWindowEnd = end
                    },
                    onNavigateDays = { delta -> repository.navigateDays(delta) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 4. Quick Graph Navigation Controls (Parity with landscape OpenGL curve)
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    GraphNavigationToolbar(
                        isFullscreen = false,
                        onToggleFullscreen = onToggleFullscreen,
                        onJumpToNow = {
                            repository.jumpToNow()
                            Toast.makeText(context, context.getString(R.string.now), Toast.LENGTH_SHORT).show()
                        },
                        onNavigateDay = { delta -> repository.navigateDays(delta) },
                        onNavigateWeek = { delta -> repository.navigateDays(delta * 7) },
                        onShowLastScan = {
                            repository.showLastScan()
                            Toast.makeText(context, context.getString(R.string.last_scan), Toast.LENGTH_SHORT).show()
                        },
                        onOpenLayers = { showLayersSheet = true },
                        onOpenSearch = { showSearchDialog = true },
                        onOpenDatePicker = { showDatePicker = true },
                        onOpenHelp = { showHelpSheet = true }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Glucose Stats & Time in Range Card
                GlucoseStatsCard(
                    stats = screenStats,
                    unit = unit,
                    timeRangeLabel = selectedRange.label,
                    minimalistUnits = displayConfig.minimalistUnits
                )
            }
        }

        // Quick Log FAB (Bottom right)
        if (!isFullscreen) {
            FloatingActionButton(
                onClick = onOpenAddEntry,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.new_amount))
            }
        }

        // 6. Graph Layers Bottom Sheet (Scans, Stream, History, Amounts, Meals)
        if (showLayersSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLayersSheet = false },
                sheetState = layersSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp)
                ) {
                    Text(
                        text = stringResource(R.string.graph_layers),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.graph_layers_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    LayerToggleItem(
                        title = stringResource(R.string.layer_stream),
                        subtitle = stringResource(R.string.layer_stream_desc),
                        checked = displayConfig.showStream,
                        onCheckedChange = { repository.toggleGraphLayer("stream", it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LayerToggleItem(
                        title = stringResource(R.string.layer_calibrated_stream),
                        subtitle = stringResource(R.string.layer_calibrated_stream_desc),
                        checked = displayConfig.showCalibratedStream,
                        onCheckedChange = { repository.toggleGraphLayer("calibratedstream", it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LayerToggleItem(
                        title = stringResource(R.string.layer_scans),
                        subtitle = stringResource(R.string.layer_scans_desc),
                        checked = displayConfig.showScans,
                        onCheckedChange = { repository.toggleGraphLayer("scans", it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LayerToggleItem(
                        title = stringResource(R.string.layer_calibrated_scans),
                        subtitle = stringResource(R.string.layer_calibrated_scans_desc),
                        checked = displayConfig.showCalibratedScans,
                        onCheckedChange = { repository.toggleGraphLayer("calibratedscans", it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LayerToggleItem(
                        title = stringResource(R.string.layer_history),
                        subtitle = stringResource(R.string.layer_history_desc),
                        checked = displayConfig.showHistory,
                        onCheckedChange = { repository.toggleGraphLayer("history", it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LayerToggleItem(
                        title = stringResource(R.string.layer_calibrated_history),
                        subtitle = stringResource(R.string.layer_calibrated_history_desc),
                        checked = displayConfig.showCalibratedHistory,
                        onCheckedChange = { repository.toggleGraphLayer("calibratedhistory", it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LayerToggleItem(
                        title = stringResource(R.string.layer_amounts),
                        subtitle = stringResource(R.string.layer_amounts_desc),
                        checked = displayConfig.showAmounts,
                        onCheckedChange = { repository.toggleGraphLayer("amounts", it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LayerToggleItem(
                        title = stringResource(R.string.layer_meals),
                        subtitle = stringResource(R.string.layer_meals_desc),
                        checked = displayConfig.showMeals,
                        onCheckedChange = { repository.toggleGraphLayer("meals", it) }
                    )
                }
            }
        }

        // 7. Clinical Explanations & Help Bottom Sheet
        if (showHelpSheet) {
            ModalBottomSheet(
                onDismissRequest = { showHelpSheet = false },
                sheetState = helpSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 40.dp)
                ) {
                    Text(
                        text = stringResource(R.string.help_and_explanations),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    ClinicalHelpSection(
                        title = stringResource(R.string.help_tir_title),
                        description = stringResource(R.string.help_tir_desc)
                    )
                    ClinicalHelpSection(
                        title = stringResource(R.string.help_agp_title),
                        description = stringResource(R.string.help_agp_desc)
                    )
                    ClinicalHelpSection(
                        title = stringResource(R.string.help_gmi_title),
                        description = stringResource(R.string.help_gmi_desc)
                    )
                    ClinicalHelpSection(
                        title = stringResource(R.string.help_cv_title),
                        description = stringResource(R.string.help_cv_desc)
                    )
                    ClinicalHelpSection(
                        title = stringResource(R.string.help_data_sources_title),
                        description = stringResource(R.string.help_data_sources_desc)
                    )
                    ClinicalHelpSection(
                        title = stringResource(R.string.help_calibration_title),
                        description = stringResource(R.string.help_calibration_desc)
                    )
                    ClinicalHelpSection(
                        title = stringResource(R.string.help_exchanges_title),
                        description = stringResource(R.string.help_exchanges_desc)
                    )
                }
            }
        }

        // 8. Amount Log Entry Detail Dialog (Inspection / Deletion)
        if (selectedLogForDetail != null) {
            val log = selectedLogForDetail!!
            val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            AlertDialog(
                onDismissRequest = { selectedLogForDetail = null },
                title = {
                    Text(
                        text = "${log.type.label}: ${log.value}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Time: ${timeFmt.format(Date(log.timestamp))}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!log.note.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Note: ${log.note}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedLogForDetail = null }) {
                        Text(stringResource(R.string.closename))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            repository.deleteLogEntry(log)
                            selectedLogForDetail = null
                            Toast.makeText(context, "Log entry deleted", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }

        // 9. Search Glucose & Logs Dialog
        if (showSearchDialog) {
            SearchGlucoseDialog(
                unit = unit,
                onDismiss = { showSearchDialog = false },
                onExecuteSearch = { under, above, label, keyword ->
                    showSearchDialog = false
                    val count = repository.searchGlucose(label, under, above, keyword)
                    isSearchActive = true
                    searchSummaryText = when {
                        under > 0f -> "Lows (< ${unit.format(under)})"
                        above > 0f -> "Highs (> ${unit.format(above)})"
                        keyword.isNotEmpty() -> "\"$keyword\""
                        else -> "Search Results"
                    }
                    Toast.makeText(context, "Search active: $count matches found", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 10. Date Picker Dialog
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDatePicker = false
                            val selectedMillis = datePickerState.selectedDateMillis
                            if (selectedMillis != null) {
                                val cal = Calendar.getInstance()
                                cal.timeInMillis = selectedMillis
                                val year = cal.get(Calendar.YEAR)
                                val month = cal.get(Calendar.MONTH) + 1
                                val day = cal.get(Calendar.DAY_OF_MONTH)
                                repository.moveToDate(year, month, day)
                                Toast.makeText(context, "Navigated to $year-$month-$day", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.date))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
private fun PastDataBanner(
    startTime: Long,
    endTime: Long,
    onJumpToNow: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${stringResource(R.string.viewing_past_data)}: ${dateFmt.format(Date(startTime))} - ${dateFmt.format(Date(endTime))}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            OutlinedButton(
                onClick = onJumpToNow,
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(stringResource(R.string.now), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SearchActiveBar(
    summaryText: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = summaryText.ifEmpty { stringResource(R.string.search) },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Prev Match",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Match",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Search",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun GraphNavigationToolbar(
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onJumpToNow: () -> Unit,
    onNavigateDay: (Int) -> Unit,
    onNavigateWeek: (Int) -> Unit,
    onShowLastScan: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onOpenHelp: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Week Back & Day Back
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onNavigateWeek(-1) }, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.Default.FastRewind, contentDescription = stringResource(R.string.week_back), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onNavigateDay(-1) }, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.day_back), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Center Actions: "Now", Date, Search, Last Scan, Layers, Fullscreen, Help
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onJumpToNow,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.now), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onOpenDatePicker, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.date), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(onClick = onOpenSearch, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = stringResource(R.string.search), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(onClick = onShowLastScan, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.Default.Nfc, contentDescription = stringResource(R.string.last_scan), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(onClick = onOpenLayers, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.Default.Layers, contentDescription = stringResource(R.string.graph_layers), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = stringResource(if (isFullscreen) R.string.exit_fullscreen else R.string.fullscreen_graph),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onOpenHelp, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.Default.HelpOutline, contentDescription = stringResource(R.string.helpname), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Day Forward & Week Forward
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onNavigateDay(1) }, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.day_later), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onNavigateWeek(1) }, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.Default.FastForward, contentDescription = stringResource(R.string.week_later), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LayerToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ClinicalHelpSection(
    title: String,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}
