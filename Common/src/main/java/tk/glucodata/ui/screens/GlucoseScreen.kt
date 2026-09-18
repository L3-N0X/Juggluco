package tk.glucodata.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.ui.components.CurrentGlucoseHeroCard
import tk.glucodata.ui.components.GlucoseStatsCard
import tk.glucodata.ui.components.SearchGlucoseDialog
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.graph.GlucoseGraph
import tk.glucodata.ui.graph.TimeRangeSelector
import tk.glucodata.ui.model.GlucosePoint
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseScreen(
    repository: GlucoseRepository,
    onOpenAddEntry: () -> Unit,
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
    val sensorDetails by repository.sensorDetails.collectAsState()
    val displayConfig by repository.displayConfig.collectAsState()
    val context = LocalContext.current

    val sensorName = sensorDetails.firstOrNull()?.name ?: "CGM Sensor"
    val previousReading = if (readings.size >= 2) readings[readings.size - 2] else null

    var inspectedPoint by remember { mutableStateOf<GlucosePoint?>(null) }
    val displayReading = inspectedPoint ?: currentReading

    var showLayersSheet by remember { mutableStateOf(false) }
    val layersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showSearchDialog by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchSummaryText by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight && maxWidth > 550.dp

        if (isLandscape) {
            // Landscape layout: side-by-side
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                // Left pane: Hero & Stats
                Column(
                    modifier = Modifier
                        .weight(0.42f)
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
                        targetHigh = targetHigh
                    )
                    GlucoseStatsCard(
                        stats = stats,
                        unit = unit,
                        timeRangeLabel = selectedRange.label
                    )
                }

                // Right pane: Graph & Controls
                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .padding(start = 6.dp)
                ) {
                    TimeRangeSelector(
                        selectedRange = selectedRange,
                        onRangeSelected = { repository.setTimeRange(it) }
                    )

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
                        onPointInspected = { inspectedPoint = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    GraphNavigationToolbar(
                        onJumpToNow = { repository.jumpToNow() },
                        onNavigateDay = { delta -> repository.navigateDays(delta) },
                        onNavigateWeek = { delta -> repository.navigateDays(delta * 7) },
                        onShowLastScan = { repository.showLastScan() },
                        onOpenLayers = { showLayersSheet = true },
                        onOpenSearch = { showSearchDialog = true },
                        onOpenDatePicker = { showDatePicker = true }
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
                    targetHigh = targetHigh
                )

                // 2. Time Range Selector pills
                TimeRangeSelector(
                    selectedRange = selectedRange,
                    onRangeSelected = { repository.setTimeRange(it) }
                )

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
                    onPointInspected = { inspectedPoint = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp)
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 4. Quick Graph Navigation Controls (Parity with landscape OpenGL curve)
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    GraphNavigationToolbar(
                        onJumpToNow = {
                            repository.jumpToNow()
                            Toast.makeText(context, "Jumped to latest reading", Toast.LENGTH_SHORT).show()
                        },
                        onNavigateDay = { delta -> repository.navigateDays(delta) },
                        onNavigateWeek = { delta -> repository.navigateDays(delta * 7) },
                        onShowLastScan = {
                            repository.showLastScan()
                            Toast.makeText(context, "Showing last scan", Toast.LENGTH_SHORT).show()
                        },
                        onOpenLayers = { showLayersSheet = true },
                        onOpenSearch = { showSearchDialog = true },
                        onOpenDatePicker = { showDatePicker = true }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Glucose Stats & Time in Range Card
                GlucoseStatsCard(
                    stats = stats,
                    unit = unit,
                    timeRangeLabel = selectedRange.label
                )
            }
        }

        // Quick Log FAB (Bottom right)
        FloatingActionButton(
            onClick = onOpenAddEntry,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Log Record")
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
                        text = "Graph Display Layers",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Customize data layers rendered on the glucose curve",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    LayerToggleItem(
                        title = "Real-time Stream",
                        subtitle = "Continuous 1-minute Bluetooth stream values",
                        checked = displayConfig.showStream,
                        onCheckedChange = { repository.toggleGraphLayer("stream", it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LayerToggleItem(
                        title = "NFC Scans",
                        subtitle = "Manual scan readings from sensor memory",
                        checked = displayConfig.showScans,
                        onCheckedChange = { repository.toggleGraphLayer("scans", it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LayerToggleItem(
                        title = "Sensor History",
                        subtitle = "Historic 15-minute sensor backup values",
                        checked = displayConfig.showHistory,
                        onCheckedChange = { repository.toggleGraphLayer("history", it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LayerToggleItem(
                        title = "Calibrated Stream",
                        subtitle = "Calibrated glucose values when active",
                        checked = displayConfig.showCalibratedStream,
                        onCheckedChange = { repository.toggleGraphLayer("calibrated", it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LayerToggleItem(
                        title = "Insulin & Amounts",
                        subtitle = "Show logged insulin doses & numbers on curve",
                        checked = displayConfig.showAmounts,
                        onCheckedChange = { repository.toggleGraphLayer("amounts", it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LayerToggleItem(
                        title = "Meals & Food",
                        subtitle = "Display meal carbs and food indicators",
                        checked = displayConfig.showMeals,
                        onCheckedChange = { repository.toggleGraphLayer("meals", it) }
                    )
                }
            }
        }

        // 7. Search Glucose & Logs Dialog
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

        // 8. Date Picker Dialog
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
                        Text("Jump to Date")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
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
                    text = summaryText.ifEmpty { "Search Active" },
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
    onJumpToNow: () -> Unit,
    onNavigateDay: (Int) -> Unit,
    onNavigateWeek: (Int) -> Unit,
    onShowLastScan: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDatePicker: () -> Unit
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
                    Icon(imageVector = Icons.Default.FastRewind, contentDescription = "Week Back", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onNavigateDay(-1) }, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Day Back", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Center Actions: "Now", Date, Search, Last Scan, Layers
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onJumpToNow,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onOpenDatePicker, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = "Pick Date", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(onClick = onOpenSearch, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(onClick = onShowLastScan, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.Default.Nfc, contentDescription = "Last Scan", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(onClick = onOpenLayers, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.Default.Layers, contentDescription = "Layers", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Day Forward & Week Forward
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onNavigateDay(1) }, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Day Forward", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onNavigateWeek(1) }, modifier = Modifier.size(34.dp)) {
                    Icon(imageVector = Icons.Default.FastForward, contentDescription = "Week Forward", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
