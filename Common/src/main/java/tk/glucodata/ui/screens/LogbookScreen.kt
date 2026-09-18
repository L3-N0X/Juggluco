package tk.glucodata.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.model.LogType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogbookScreen(
    repository: GlucoseRepository,
    onOpenAddEntry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logs by repository.logs.collectAsState()
    val unit by repository.unit.collectAsState()
    val context = LocalContext.current

    var selectedFilter by remember { mutableStateOf<LogType?>(null) }

    val filteredLogs = remember(logs, selectedFilter) {
        if (selectedFilter == null) logs else logs.filter { it.type == selectedFilter }
    }

    // Calculate today's totals
    val now = System.currentTimeMillis()
    val dayStart = now - (now % (24 * 3600 * 1000L))
    val todayLogs = logs.filter { it.timestamp >= dayStart }
    val todayBolus = todayLogs.filter { it.type == LogType.RAPID_INSULIN }.sumOf { it.value.toDouble() }
    val todayBasal = todayLogs.filter { it.type == LogType.BASAL_INSULIN }.sumOf { it.value.toDouble() }
    val todayCarbs = todayLogs.filter { it.type == LogType.CARBS || it.type == LogType.MEAL }.sumOf { it.value.toDouble() }
    val todayChecks = todayLogs.count { it.type == LogType.BLOOD_GLUCOSE }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Diabetes Logbook",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Track insulin doses, carbs, fingerpricks and notes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Daily Summary KPI Row
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DailyTotalPill(label = "Bolus", value = "${String.format(Locale.US, "%.1f", todayBolus)} U", color = Color(0xFF2563EB))
                    DailyTotalPill(label = "Basal", value = "${String.format(Locale.US, "%.1f", todayBasal)} U", color = Color(0xFF4F46E5))
                    DailyTotalPill(label = "Carbs", value = "${todayCarbs.toInt()} g", color = Color(0xFFD97706))
                    DailyTotalPill(label = "BG Checks", value = "$todayChecks", color = Color(0xFFDC2626))
                }
            }

            // Filter Chips Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("All", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == LogType.RAPID_INSULIN,
                    onClick = { selectedFilter = if (selectedFilter == LogType.RAPID_INSULIN) null else LogType.RAPID_INSULIN },
                    label = { Text("Bolus", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == LogType.BASAL_INSULIN,
                    onClick = { selectedFilter = if (selectedFilter == LogType.BASAL_INSULIN) null else LogType.BASAL_INSULIN },
                    label = { Text("Basal", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == LogType.CARBS,
                    onClick = { selectedFilter = if (selectedFilter == LogType.CARBS) null else LogType.CARBS },
                    label = { Text("Carbs", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == LogType.BLOOD_GLUCOSE,
                    onClick = { selectedFilter = if (selectedFilter == LogType.BLOOD_GLUCOSE) null else LogType.BLOOD_GLUCOSE },
                    label = { Text("BG", fontSize = 12.sp) }
                )
            }

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No log records found\nTap + to log insulin, carbs, meals or glucose checks",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { item ->
                        LogItemCard(
                            record = item,
                            unit = unit,
                            onDelete = {
                                repository.deleteLogEntry(item)
                                Toast.makeText(context, "Log entry deleted", Toast.LENGTH_SHORT).show()
                            }
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
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Log")
        }
    }
}

@Composable
private fun DailyTotalPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun LogItemCard(
    record: LogRecord,
    unit: GlucoseUnit,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    val (icon, iconColor, bgColor) = when (record.type) {
        LogType.RAPID_INSULIN -> Triple(Icons.Default.Medication, Color(0xFF2563EB), Color(0xFFDBEAFE))
        LogType.BASAL_INSULIN -> Triple(Icons.Default.Vaccines, Color(0xFF4F46E5), Color(0xFFE0E7FF))
        LogType.CARBS, LogType.MEAL -> Triple(Icons.Default.Fastfood, Color(0xFFD97706), Color(0xFFFEF3C7))
        LogType.BLOOD_GLUCOSE -> Triple(Icons.Default.Bloodtype, Color(0xFFDC2626), Color(0xFFFEE2E2))
        LogType.NOTE -> Triple(Icons.AutoMirrored.Filled.Notes, Color(0xFF7C3AED), Color(0xFFEDE9FE))
    }

    val valueDisplay = when (record.type) {
        LogType.RAPID_INSULIN, LogType.BASAL_INSULIN -> "${String.format(Locale.US, "%.1f", record.value)} U"
        LogType.CARBS, LogType.MEAL -> "${record.value.toInt()} g"
        LogType.BLOOD_GLUCOSE -> "${unit.format(record.value)} ${unit.label}"
        LogType.NOTE -> if (record.value > 0) "${record.value}" else ""
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(bgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = record.type.label,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.type.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = valueDisplay,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = iconColor
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${dateFormat.format(Date(record.timestamp))} at ${timeFormat.format(Date(record.timestamp))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!record.note.isNullOrEmpty()) {
                        Text(
                            text = record.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
