package tk.glucodata.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.glucodata.ui.graph.rememberSettledWindow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.alerts.AlertStore
import tk.glucodata.ui.components.CurrentGlucoseHeroCard
import tk.glucodata.ui.components.EditEntryDialog
import tk.glucodata.ui.components.GlucoseStatsCard
import tk.glucodata.ui.components.SearchGlucoseDialog
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.graph.GlucoseGraph
import tk.glucodata.ui.graph.GraphViewportState
import tk.glucodata.ui.graph.TimeRangeSelector
import tk.glucodata.ui.graph.rememberGraphViewportState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.ui.graphics.Color
import tk.glucodata.ui.model.DeltaCalculation
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.model.LogType
import tk.glucodata.ui.screens.DailyTotalPill
import tk.glucodata.ui.screens.LogItemCard
import tk.glucodata.ui.theme.LocalLogbookColors
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

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
    val screenStats by repository.screenStats.collectAsState()
    val sensorDetails by repository.sensorDetails.collectAsState()
    val displayConfig by repository.displayConfig.collectAsState()
    remember { AlertStore.ensureLoaded() }
    val alertEvents by AlertStore.events.collectAsState()
    val context = LocalContext.current

    val sensorName = sensorDetails.firstOrNull()?.name
    val previousReading = remember(currentReading, readings, displayConfig.deltaCalculation) {
        DeltaCalculation.findDeltaReading(currentReading, readings, displayConfig.deltaCalculation)
    }

    // 1. Unified Graph Viewport State
    val viewportState = rememberGraphViewportState(initialDurationMillis = selectedRange?.durationMillis ?: tk.glucodata.ui.model.TimeRange.SIX_HOURS.durationMillis)

    // Sync viewport duration when time range pill changes from external controls
    LaunchedEffect(selectedRange) {
        val range = selectedRange ?: return@LaunchedEffect
        val currentDuration = viewportState.durationMillis
        val tolerance = (range.durationMillis * 0.15).toLong()
        if (abs(currentDuration - range.durationMillis) > tolerance) {
            viewportState.setDuration(range.durationMillis)
        }
    }

    // Settled window for off-thread stats calculation and logbook filtering without recomposition churn
    val settledWindow = rememberSettledWindow(viewportState)

    // Sync selected range badge when the graph viewport duration settles after gesture (pinch zoom)
    LaunchedEffect(settledWindow.durationMillis) {
        val currentDuration = settledWindow.durationMillis
        val matched = tk.glucodata.ui.model.TimeRange.matchPreset(currentDuration)
        if (matched != null) {
            if (selectedRange != matched) {
                repository.setTimeRange(matched)
            }
        } else if (selectedRange?.isCustom == true) {
            val tolerance = (selectedRange!!.durationMillis * 0.15).toLong()
            if (abs(currentDuration - selectedRange!!.durationMillis) > tolerance) {
                repository.setTimeRange(null)
            }
        } else if (selectedRange != null) {
            repository.setTimeRange(null)
        }
    }

    // Dynamic stats calculated asynchronously on Dispatchers.Default for the settled window
    val visibleStats by produceState(
        initialValue = tk.glucodata.ui.model.GlucoseStats(),
        readings,
        settledWindow,
        displayConfig,
        targetLow,
        targetHigh
    ) {
        value = withContext(Dispatchers.Default) {
            if (readings.isEmpty()) return@withContext tk.glucodata.ui.model.GlucoseStats()
            val range = findIndexRange(readings, settledWindow.startMillis, settledWindow.endMillis)
            if (range.isEmpty()) return@withContext tk.glucodata.ui.model.GlucoseStats()

            val filtered = ArrayList<GlucosePoint>(range.last - range.first + 1)
            for (i in range) {
                val pt = readings[i]
                val include = when {
                    pt.isScan -> displayConfig.showScans || (pt.isCalibrated && displayConfig.showCalibratedScans)
                    pt.isHistory -> displayConfig.showHistory || (pt.isCalibrated && displayConfig.showCalibratedHistory)
                    pt.isCalibrated -> displayConfig.showCalibratedStream
                    else -> displayConfig.showStream
                }
                if (include) filtered.add(pt)
            }
            val pointsToUse = if (filtered.isNotEmpty()) filtered else {
                readings.subList(range.first, range.last + 1)
            }
            tk.glucodata.ui.model.GlucoseStats.calculate(pointsToUse, targetLow, targetHigh)
        }
    }

    val statsTimeRangeLabel = remember(settledWindow.isLive, selectedRange, settledWindow.startMillis, settledWindow.endMillis, settledWindow.durationMillis) {
        if (settledWindow.isLive) {
            val range = selectedRange
            if (range != null) {
                if (range.labelRes != null) {
                    context.getString(range.labelRes)
                } else {
                    formatDurationLabel(context, range.durationMillis)
                }
            } else {
                formatDurationLabel(context, settledWindow.durationMillis)
            }
        } else {
            val dateFmt = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd, Hm"), Locale.getDefault())
            "${dateFmt.format(Date(settledWindow.startMillis))} - ${dateFmt.format(Date(settledWindow.endMillis))}"
        }
    }

    // Handle back button when in fullscreen mode: exit fullscreen instead of closing app
    BackHandler(enabled = isFullscreen) {
        onToggleFullscreen()
    }

    var showLayersSheet by remember { mutableStateOf(false) }
    val layersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showHelpSheet by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedLogForDetail by remember { mutableStateOf<LogRecord?>(null) }

    var showSearchDialog by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchMatches by remember { mutableStateOf<List<Long>>(emptyList()) }
    var searchMatchIndex by remember { mutableIntStateOf(0) }
    var searchSummaryText by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())

    var showFullLogbookSheet by remember { mutableStateOf(false) }
    val fullLogbookSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight && maxWidth > 550.dp

        if (isFullscreen) {
            // Fullscreen Graph Mode (With systemBarsPadding to prevent overlapping Android navigation & status bar)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
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
                        onRangeSelected = {
                            repository.setTimeRange(it)
                            viewportState.setDuration(it.durationMillis)
                        },
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

                if (isSearchActive && searchMatches.isNotEmpty()) {
                    SearchActiveBar(
                        summaryText = stringResource(
                            R.string.search_match_position,
                            searchMatchIndex + 1,
                            searchMatches.size,
                            searchSummaryText
                        ),
                        onPrev = {
                            if (searchMatches.isNotEmpty()) {
                                searchMatchIndex = (searchMatchIndex - 1 + searchMatches.size) % searchMatches.size
                                viewportState.jumpTo(searchMatches[searchMatchIndex] + (viewportState.durationMillis / 2))
                            }
                        },
                        onNext = {
                            if (searchMatches.isNotEmpty()) {
                                searchMatchIndex = (searchMatchIndex + 1) % searchMatches.size
                                viewportState.jumpTo(searchMatches[searchMatchIndex] + (viewportState.durationMillis / 2))
                            }
                        },
                        onClose = {
                            isSearchActive = false
                            searchMatches = emptyList()
                            repository.stopSearch()
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                GlucoseGraph(
                    readings = readings,
                    logs = logs,
                    viewportState = viewportState,
                    unit = unit,
                    targetLow = targetLow,
                    targetHigh = targetHigh,
                    displayConfig = displayConfig,
                    alertEvents = alertEvents,
                    onLogEntryClicked = { selectedLogForDetail = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                GraphNavigationToolbar(
                    isFullscreen = false,
                    onToggleFullscreen = onToggleFullscreen,
                    onNavigateDay = { delta ->
                        viewportState.navigateDays(delta)
                    },
                    canNavigateForward = !viewportState.isLive,
                    onShowLastScan = {
                        handleShowLastScan(readings, viewportState, repository, context)
                    },
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
                    .padding(ScreenLayout.Gutter)
            ) {
                // Left pane: Hero & Stats
                Column(
                    modifier = Modifier
                        .weight(0.40f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // Always show current real-time sensor reading
                    CurrentGlucoseHeroCard(
                        currentReading = currentReading,
                        previousReading = previousReading,
                        unit = unit,
                        sensorName = sensorName,
                        targetLow = targetLow,
                        targetHigh = targetHigh,
                        minimalistUnits = displayConfig.minimalistUnits,
                        deltaCalculation = displayConfig.deltaCalculation
                    )
                    GlucoseStatsCard(
                        stats = visibleStats,
                        unit = unit,
                        timeRangeLabel = statsTimeRangeLabel,
                        minimalistUnits = displayConfig.minimalistUnits
                    )
                    LogbookSection(
                        repository = repository,
                        onOpenAddEntry = onOpenAddEntry,
                        onViewAll = { showFullLogbookSheet = true },
                        unit = unit,
                        minimalistUnits = displayConfig.minimalistUnits,
                        viewStartTime = settledWindow.startMillis,
                        viewEndTime = settledWindow.endMillis
                    )
                }

                // Right pane: Graph & Controls
                Column(
                    modifier = Modifier
                        .weight(0.60f)
                        .fillMaxHeight()
                        .padding(start = 8.dp)
                ) {
                    TimeRangeSelector(
                        selectedRange = selectedRange,
                        onRangeSelected = {
                            repository.setTimeRange(it)
                            viewportState.setDuration(it.durationMillis)
                        }
                    )

                    if (isSearchActive && searchMatches.isNotEmpty()) {
                        SearchActiveBar(
                            summaryText = stringResource(
                            R.string.search_match_position,
                            searchMatchIndex + 1,
                            searchMatches.size,
                            searchSummaryText
                        ),
                            onPrev = {
                                if (searchMatches.isNotEmpty()) {
                                    searchMatchIndex = (searchMatchIndex - 1 + searchMatches.size) % searchMatches.size
                                    viewportState.jumpTo(searchMatches[searchMatchIndex] + (viewportState.durationMillis / 2))
                                }
                            },
                            onNext = {
                                if (searchMatches.isNotEmpty()) {
                                    searchMatchIndex = (searchMatchIndex + 1) % searchMatches.size
                                    viewportState.jumpTo(searchMatches[searchMatchIndex] + (viewportState.durationMillis / 2))
                                }
                            },
                            onClose = {
                                isSearchActive = false
                                searchMatches = emptyList()
                                repository.stopSearch()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    GlucoseGraph(
                        readings = readings,
                        logs = logs,
                        viewportState = viewportState,
                        unit = unit,
                        targetLow = targetLow,
                        targetHigh = targetHigh,
                        displayConfig = displayConfig,
                        alertEvents = alertEvents,
                        onLogEntryClicked = { selectedLogForDetail = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    GraphNavigationToolbar(
                        isFullscreen = false,
                        onToggleFullscreen = onToggleFullscreen,
                        onNavigateDay = { delta ->
                            viewportState.navigateDays(delta)
                        },
                        canNavigateForward = !viewportState.isLive,
                        onShowLastScan = {
                            handleShowLastScan(readings, viewportState, repository, context)
                        },
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
                    .padding(
                        start = ScreenLayout.Gutter,
                        end = ScreenLayout.Gutter,
                        top = ScreenLayout.TopPadding,
                        bottom = ScreenLayout.BottomPadding
                    )
            ) {
                // 1. Current Glucose Reading at top (ALWAYS real-time reading)
                CurrentGlucoseHeroCard(
                    currentReading = currentReading,
                    previousReading = previousReading,
                    unit = unit,
                    sensorName = sensorName,
                    targetLow = targetLow,
                    targetHigh = targetHigh,
                    minimalistUnits = displayConfig.minimalistUnits,
                    deltaCalculation = displayConfig.deltaCalculation
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 2. Time Range Selector pills
                TimeRangeSelector(
                    selectedRange = selectedRange,
                    onRangeSelected = {
                        repository.setTimeRange(it)
                        viewportState.setDuration(it.durationMillis)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Search Active Banner
                if (isSearchActive && searchMatches.isNotEmpty()) {
                    SearchActiveBar(
                        summaryText = stringResource(
                            R.string.search_match_position,
                            searchMatchIndex + 1,
                            searchMatches.size,
                            searchSummaryText
                        ),
                        onPrev = {
                            if (searchMatches.isNotEmpty()) {
                                searchMatchIndex = (searchMatchIndex - 1 + searchMatches.size) % searchMatches.size
                                viewportState.jumpTo(searchMatches[searchMatchIndex] + (viewportState.durationMillis / 2))
                            }
                        },
                        onNext = {
                            if (searchMatches.isNotEmpty()) {
                                searchMatchIndex = (searchMatchIndex + 1) % searchMatches.size
                                viewportState.jumpTo(searchMatches[searchMatchIndex] + (viewportState.durationMillis / 2))
                            }
                        },
                        onClose = {
                            isSearchActive = false
                            searchMatches = emptyList()
                            repository.stopSearch()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 3. Modern Interactive Compose Glucose Graph
                GlucoseGraph(
                    readings = readings,
                    logs = logs,
                    viewportState = viewportState,
                    unit = unit,
                    targetLow = targetLow,
                    targetHigh = targetHigh,
                    displayConfig = displayConfig,
                    alertEvents = alertEvents,
                    onLogEntryClicked = { selectedLogForDetail = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Quick Graph Navigation Controls
                GraphNavigationToolbar(
                    isFullscreen = false,
                    onToggleFullscreen = onToggleFullscreen,
                    onNavigateDay = { delta ->
                        viewportState.navigateDays(delta)
                    },
                    canNavigateForward = !viewportState.isLive,
                    onShowLastScan = {
                        handleShowLastScan(readings, viewportState, repository, context)
                    },
                    onOpenLayers = { showLayersSheet = true },
                    onOpenSearch = { showSearchDialog = true },
                    onOpenDatePicker = { showDatePicker = true },
                    onOpenHelp = { showHelpSheet = true }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 5. Glucose Stats & Time in Range
                GlucoseStatsCard(
                    stats = visibleStats,
                    unit = unit,
                    timeRangeLabel = statsTimeRangeLabel,
                    minimalistUnits = displayConfig.minimalistUnits
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 6. Recent Logbook Entries Section
                LogbookSection(
                    repository = repository,
                    onOpenAddEntry = onOpenAddEntry,
                    onViewAll = { showFullLogbookSheet = true },
                    unit = unit,
                    minimalistUnits = displayConfig.minimalistUnits,
                    viewStartTime = settledWindow.startMillis,
                    viewEndTime = settledWindow.endMillis
                )
            }
        }

        // 6. Layers & Display Options Bottom Sheet
        if (showLayersSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLayersSheet = false },
                sheetState = layersSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ScreenLayout.Gutter)
                        .padding(bottom = 36.dp)
                ) {
                    Text(
                        text = stringResource(R.string.graph_layers),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.graph_layers_description_short),
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

                    if (displayConfig.calibrationEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        LayerToggleItem(
                            title = stringResource(R.string.layer_calibrated_stream),
                            subtitle = stringResource(R.string.layer_calibrated_stream_desc),
                            checked = displayConfig.showCalibratedStream,
                            onCheckedChange = { repository.toggleGraphLayer("calibratedstream", it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LayerToggleItem(
                        title = stringResource(R.string.layer_scans),
                        subtitle = stringResource(R.string.layer_scans_desc),
                        checked = displayConfig.showScans,
                        onCheckedChange = { repository.toggleGraphLayer("scans", it) }
                    )

                    if (displayConfig.calibrationEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        LayerToggleItem(
                            title = stringResource(R.string.layer_calibrated_scans),
                            subtitle = stringResource(R.string.layer_calibrated_scans_desc),
                            checked = displayConfig.showCalibratedScans,
                            onCheckedChange = { repository.toggleGraphLayer("calibratedscans", it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LayerToggleItem(
                        title = stringResource(R.string.layer_history),
                        subtitle = stringResource(R.string.layer_history_desc),
                        checked = displayConfig.showHistory,
                        onCheckedChange = { repository.toggleGraphLayer("history", it) }
                    )

                    if (displayConfig.calibrationEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        LayerToggleItem(
                            title = stringResource(R.string.layer_calibrated_history),
                            subtitle = stringResource(R.string.layer_calibrated_history_desc),
                            checked = displayConfig.showCalibratedHistory,
                            onCheckedChange = { repository.toggleGraphLayer("calibratedhistory", it) }
                        )
                    }

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
                        .padding(horizontal = ScreenLayout.Gutter)
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
            val timeFmt = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "yMMMd, Hm"), Locale.getDefault())
            AlertDialog(
                onDismissRequest = { selectedLogForDetail = null },
                title = {
                    Text(
                        text = stringResource(
                            R.string.log_detail_title,
                            stringResource(log.type.labelRes),
                            log.value.toString()
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.log_detail_time, timeFmt.format(Date(log.timestamp))),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!log.note.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.log_detail_note, log.note),
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
                            Toast.makeText(context, context.getString(R.string.log_entry_deleted), Toast.LENGTH_SHORT).show()
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
                    val matches = repository.searchGlucose(
                        label,
                        under,
                        above,
                        keyword,
                        LogType.entries.associateWith { context.getString(it.labelRes) }
                    )
                    if (matches.isNotEmpty()) {
                        searchMatches = matches
                        searchMatchIndex = 0
                        isSearchActive = true
                        searchSummaryText = when {
                            under > 0f -> context.getString(R.string.tir_range_less_than, unit.format(under))
                            above > 0f -> context.getString(R.string.tir_range_greater_than, unit.format(above))
                            keyword.isNotEmpty() -> "\"$keyword\""
                            else -> context.getString(R.string.search_results)
                        }
                        viewportState.jumpTo(matches[0] + (viewportState.durationMillis / 2))
                        Toast.makeText(
                            context,
                            context.resources.getQuantityString(R.plurals.search_matches_found, matches.size, matches.size),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.search_no_matches), Toast.LENGTH_SHORT).show()
                    }
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
                                viewportState.jumpToDate(selectedMillis)

                                val cal = Calendar.getInstance()
                                cal.timeInMillis = selectedMillis
                                val year = cal.get(Calendar.YEAR)
                                val month = cal.get(Calendar.MONTH) + 1
                                val day = cal.get(Calendar.DAY_OF_MONTH)
                                repository.moveToDate(year, month, day)

                                val dateFmt = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "yMMMd"), Locale.getDefault())
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.date_navigated, dateFmt.format(Date(viewportState.startTimeMillis))),
                                    Toast.LENGTH_SHORT
                                ).show()
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

        // 11. Full Logbook Bottom Sheet
        if (showFullLogbookSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFullLogbookSheet = false },
                sheetState = fullLogbookSheetState
            ) {
                LogbookScreen(
                    repository = repository,
                    onOpenAddEntry = onOpenAddEntry,
                    onClose = { showFullLogbookSheet = false },
                    modifier = Modifier.fillMaxHeight(0.85f)
                )
            }
        }
    }
}

private fun handleShowLastScan(
    readings: List<GlucosePoint>,
    viewportState: GraphViewportState,
    repository: GlucoseRepository,
    context: android.content.Context
) {
    val lastScan = readings.filter { it.isScan }.maxByOrNull { it.timestamp }
    if (lastScan != null) {
        viewportState.jumpTo(lastScan.timestamp + (viewportState.durationMillis / 3))
        val timeFmt = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd, Hm"), Locale.getDefault())
        Toast.makeText(
            context,
            context.getString(R.string.jumped_to_last_scan, timeFmt.format(Date(lastScan.timestamp))),
            Toast.LENGTH_SHORT
        ).show()
    } else {
        Toast.makeText(context, context.getString(R.string.no_nfc_scans_recorded), Toast.LENGTH_SHORT).show()
    }
    repository.showLastScan()
}

@Composable
private fun SearchActiveBar(
    summaryText: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    // Flat indicator placed directly on the page (M3): no Card container, the
    // primary accent carries the active state instead of a tonal surface.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
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
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.previous_match),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.next_match),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close_search),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GraphNavigationToolbar(
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onNavigateDay: (Int) -> Unit,
    canNavigateForward: Boolean = true,
    onShowLastScan: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onOpenHelp: () -> Unit
) {
    // Flat toolbar placed directly on the page (M3): icon buttons with no Card
    // container, background or elevation, edge to edge with the screen gutter.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Day Back
        IconButton(onClick = { onNavigateDay(-1) }, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.day_back),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Center Actions: Date, Search, Last Scan, Layers, Fullscreen, Help
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenDatePicker, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.date), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }

            IconButton(onClick = onOpenSearch, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.Search, contentDescription = stringResource(R.string.search), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }

            IconButton(onClick = onShowLastScan, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.Nfc, contentDescription = stringResource(R.string.last_scan), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }

            IconButton(onClick = onOpenLayers, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.Layers, contentDescription = stringResource(R.string.graph_layers), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }

            IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = stringResource(if (isFullscreen) R.string.exit_fullscreen else R.string.fullscreen_graph),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onOpenHelp, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.HelpOutline, contentDescription = stringResource(R.string.helpname), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Day Forward
        IconButton(
            onClick = { onNavigateDay(1) },
            enabled = canNavigateForward,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.day_later),
                modifier = Modifier.size(18.dp),
                tint = if (canNavigateForward) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
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
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ClinicalHelpSection(
    title: String,
    description: String
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun LogbookSection(
    repository: GlucoseRepository,
    onOpenAddEntry: () -> Unit,
    onViewAll: () -> Unit,
    unit: tk.glucodata.ui.model.GlucoseUnit,
    minimalistUnits: Boolean,
    viewStartTime: Long = 0L,
    viewEndTime: Long = 0L,
    modifier: Modifier = Modifier
) {
    val logs by repository.logs.collectAsState()
    val context = LocalContext.current
    var selectedTypeFilter by remember { mutableStateOf<LogType?>(null) }
    var editingEntry by remember { mutableStateOf<LogRecord?>(null) }

    val now = System.currentTimeMillis()
    val isViewingPast = viewStartTime > 0L && viewEndTime > 0L && abs(now - viewEndTime) >= 60_000L

    val activePeriodLogs = remember(logs, isViewingPast, viewStartTime, viewEndTime, now) {
        if (isViewingPast) {
            val pastLogs = logs.filter { it.timestamp in viewStartTime..viewEndTime }
            if (pastLogs.isNotEmpty()) pastLogs else logs.filter { it.timestamp in (viewStartTime - 12 * 3600 * 1000L)..(viewEndTime + 12 * 3600 * 1000L) }
        } else {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis
            logs.filter { it.timestamp >= dayStart }
        }
    }

    val todayBolus = remember(activePeriodLogs) {
        activePeriodLogs.filter { it.type == LogType.RAPID_INSULIN }.sumOf { it.value.toDouble() }
    }
    val todayBasal = remember(activePeriodLogs) {
        activePeriodLogs.filter { it.type == LogType.BASAL_INSULIN }.sumOf { it.value.toDouble() }
    }
    val todayCarbs = remember(activePeriodLogs) {
        activePeriodLogs.filter { it.type == LogType.CARBS || it.type == LogType.MEAL }.sumOf { it.value.toDouble() }
    }
    val todayChecks = remember(activePeriodLogs) {
        activePeriodLogs.count { it.type == LogType.BLOOD_GLUCOSE }
    }

    val displayLogs = remember(logs, activePeriodLogs, selectedTypeFilter) {
        val sourceList = if (activePeriodLogs.isNotEmpty()) activePeriodLogs else logs.take(10)
        val filtered = if (selectedTypeFilter != null) {
            sourceList.filter { it.type == selectedTypeFilter }
        } else {
            sourceList
        }
        filtered.take(6)
    }

    // Rendered as a flat section rather than a card wrapping cards, so the log
    // rows keep the same indent as every other card on the screen.
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ListAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                SectionTitle(text = stringResource(R.string.tab_logbook))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onViewAll) {
                    Text(
                        text = stringResource(R.string.logbook_view_all),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onOpenAddEntry,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.new_amount),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val logbookColors = LocalLogbookColors.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DailyTotalPill(
                label = stringResource(R.string.log_short_bolus),
                value = stringResource(R.string.log_value_insulin, String.format(Locale.getDefault(), "%.1f", todayBolus)),
                color = logbookColors.bolus.primary
            )
            DailyTotalPill(
                label = stringResource(R.string.log_short_basal),
                value = stringResource(R.string.log_value_insulin, String.format(Locale.getDefault(), "%.1f", todayBasal)),
                color = logbookColors.basal.primary
            )
            DailyTotalPill(
                label = stringResource(R.string.log_short_carbs),
                value = stringResource(R.string.log_value_carbs, todayCarbs.toInt().toString()),
                color = logbookColors.carbs.primary
            )
            DailyTotalPill(
                label = stringResource(R.string.log_type_finger_prick),
                value = todayChecks.toString(),
                color = logbookColors.bloodGlucose.primary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedTypeFilter == null,
                onClick = { selectedTypeFilter = null },
                label = { Text(stringResource(R.string.log_all), fontSize = 11.sp) }
            )
            FilterChip(
                selected = selectedTypeFilter == LogType.RAPID_INSULIN,
                onClick = { selectedTypeFilter = if (selectedTypeFilter == LogType.RAPID_INSULIN) null else LogType.RAPID_INSULIN },
                label = { Text(stringResource(R.string.log_short_bolus), fontSize = 11.sp) }
            )
            FilterChip(
                selected = selectedTypeFilter == LogType.BASAL_INSULIN,
                onClick = { selectedTypeFilter = if (selectedTypeFilter == LogType.BASAL_INSULIN) null else LogType.BASAL_INSULIN },
                label = { Text(stringResource(R.string.log_short_basal), fontSize = 11.sp) }
            )
            FilterChip(
                selected = selectedTypeFilter == LogType.CARBS,
                onClick = { selectedTypeFilter = if (selectedTypeFilter == LogType.CARBS) null else LogType.CARBS },
                label = { Text(stringResource(R.string.log_short_carbs), fontSize = 11.sp) }
            )
            FilterChip(
                selected = selectedTypeFilter == LogType.BLOOD_GLUCOSE,
                onClick = { selectedTypeFilter = if (selectedTypeFilter == LogType.BLOOD_GLUCOSE) null else LogType.BLOOD_GLUCOSE },
                label = { Text(stringResource(R.string.log_short_bg), fontSize = 11.sp) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (displayLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.logbook_no_entries_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Flat M3 list (M3): rows sit directly on the page, separated by
            // dividers instead of Card containers.
            Column {
                displayLogs.forEachIndexed { index, logItem ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    LogItemCard(
                        record = logItem,
                        unit = unit,
                        minimalistUnits = minimalistUnits,
                        onEdit = if (logItem.nativeSource != null) ({ editingEntry = logItem }) else null,
                        onDelete = {
                            repository.deleteLogEntry(logItem)
                            Toast.makeText(context, context.getString(R.string.log_entry_deleted), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    editingEntry?.let { entry ->
        EditEntryDialog(
            entry = entry,
            unit = unit,
            onDismiss = { editingEntry = null },
            onSave = { value, note ->
                repository.updateLogEntry(entry, entry.type, value, note = note)
            }
        )
    }
}

private fun findIndexRange(readings: List<GlucosePoint>, startTime: Long, endTime: Long): IntRange {
    if (readings.isEmpty()) return IntRange.EMPTY
    var low = 0
    var high = readings.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (readings[mid].timestamp < startTime) low = mid + 1 else high = mid
    }
    val startIdx = low

    low = startIdx
    high = readings.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (readings[mid].timestamp <= endTime) low = mid + 1 else high = mid
    }
    val endIdx = low
    return if (startIdx < endIdx) startIdx until endIdx else IntRange.EMPTY
}

private fun formatDurationLabel(context: android.content.Context, durationMillis: Long): String {
    val hours = (durationMillis / (3600 * 1000L)).toInt()
    val days = hours / 24
    val remainingHours = hours % 24
    return when {
        days > 0 && remainingHours == 0 -> context.resources.getQuantityString(R.plurals.day_count, days, days)
        days > 0 -> context.getString(R.string.duration_days_hours, days, remainingHours)
        hours > 0 -> context.resources.getQuantityString(R.plurals.hour_count, hours, hours)
        else -> context.resources.getQuantityString(
            R.plurals.minute_count,
            (durationMillis / 60_000L).toInt(),
            (durationMillis / 60_000L).toInt()
        )
    }
}
