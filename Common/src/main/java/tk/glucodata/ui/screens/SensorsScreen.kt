package tk.glucodata.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.components.CalibrationDialog
import tk.glucodata.ui.components.StartSensorDialog
import tk.glucodata.ui.components.StopSensorDialog
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.LogType
import tk.glucodata.ui.model.SensorDetail
import tk.glucodata.ui.model.SensorStatus
import tk.glucodata.ui.theme.LocalClinicalColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SensorsScreen(
    repository: GlucoseRepository,
    onTriggerNfcScan: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sensorDetails by repository.sensorDetails.collectAsState()
    val previousSensors by repository.previousSensors.collectAsState()
    val unit by repository.unit.collectAsState()
    val displayConfig by repository.displayConfig.collectAsState()
    val context = LocalContext.current

    val activeSensor = sensorDetails.firstOrNull()

    var showStartSensorDialog by remember { mutableStateOf(false) }
    var showStopSensorDialog by remember { mutableStateOf(false) }
    var showCalibrationDialog by remember { mutableStateOf(false) }
    var showEnableCalibrationPrompt by remember { mutableStateOf(false) }

    ScreenContent(modifier = modifier) {
        // 1. Current Sensor State (Positioned first)
        SectionTitle(text = stringResource(R.string.sensor_current_state))

        if (activeSensor != null) {
            OverhauledSensorCard(
                sensor = activeSensor,
                onWarmupChanged = { minutes -> repository.setWarmupMinutes(activeSensor.sensorPtr, minutes) },
                onHideToggled = { hidden -> repository.setSensorHidden(activeSensor.sensorPtr, hidden) },
                onUseAgain = {
                    repository.useSensorAgain(activeSensor.sensorPtr, context as? Activity)
                    Toast.makeText(context, "Reconnecting to ${activeSensor.name}...", Toast.LENGTH_SHORT).show()
                },
                onForgetAndRescan = {
                    repository.forgetAndRescan(activeSensor.id)
                    Toast.makeText(context, "Scanning for Bluetooth CGM devices...", Toast.LENGTH_SHORT).show()
                },
                onOpenCalibration = { showCalibrationDialog = true },
                onOpenStopSensor = { showStopSensorDialog = true }
            )
        } else {
            NoSensorPairedCard(onScanClick = { showStartSensorDialog = true })
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Start / Scan New Sensor Section (Positioned below the current sensor state)
        SectionTitle(text = stringResource(R.string.sensor_start_new))

        NfcScanBanner(onScanClick = { showStartSensorDialog = true })

        // 3. Previous / Historical Sensors
        if (previousSensors.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))

            SectionTitle(text = "Previous Sensors")

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                previousSensors.forEach { prev ->
                    PreviousSensorItem(sensor = prev)
                }
            }
        }
    }

    // Modal Dialogs
    if (showStartSensorDialog) {
        StartSensorDialog(
            onDismiss = { showStartSensorDialog = false },
            onTriggerNfcScan = onTriggerNfcScan,
            onSensorActivated = { sensorType ->
                Toast.makeText(context, "${sensorType.title} activation initiated!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showStopSensorDialog && activeSensor != null) {
        StopSensorDialog(
            sensor = activeSensor,
            onDismiss = { showStopSensorDialog = false },
            onTemporaryDisconnect = {
                Toast.makeText(context, "Sensor connection paused", Toast.LENGTH_SHORT).show()
            },
            onEndSensorPermanently = {
                repository.forgetAndRescan(activeSensor.id)
                Toast.makeText(context, "Sensor session ended. Ready to start new sensor.", Toast.LENGTH_LONG).show()
            }
        )
    }

    if (showCalibrationDialog && activeSensor != null) {
        CalibrationDialog(
            sensorPtr = activeSensor.sensorPtr,
            unit = unit,
            calibrationEnabled = displayConfig.calibrationEnabled,
            onDismiss = { showCalibrationDialog = false },
            onSaveCalibration = { mgdl ->
                repository.addLogEntry(LogType.BLOOD_GLUCOSE, mgdl, "Calibration reference")
                Toast.makeText(context, "Calibration reference saved: ${unit.format(mgdl)} ${unit.label}", Toast.LENGTH_SHORT).show()
                showCalibrationDialog = false
                if (repository.shouldPromptCalibrationEnable()) {
                    showEnableCalibrationPrompt = true
                }
            }
        )
    }

    if (showEnableCalibrationPrompt) {
        AlertDialog(
            onDismissRequest = {
                showEnableCalibrationPrompt = false
                repository.markCalibrationPromptShown()
            },
            title = { Text(stringResource(R.string.calibration_enable_prompt_title)) },
            text = { Text(stringResource(R.string.calibration_enable_prompt_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    repository.setCalibrationEnabled(true)
                    repository.markCalibrationPromptShown()
                    showEnableCalibrationPrompt = false
                }) {
                    Text(stringResource(R.string.calibration_enable_prompt_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    repository.markCalibrationPromptShown()
                    showEnableCalibrationPrompt = false
                }) {
                    Text(stringResource(R.string.calibration_enable_prompt_dismiss))
                }
            }
        )
    }
}

@Composable
private fun NfcScanBanner(onScanClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Nfc,
                    contentDescription = "NFC",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Scan or Pair New Sensor",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Hold phone directly to sensor or pair via Bluetooth",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onScanClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(imageVector = Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Scan / Start Sensor", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OverhauledSensorCard(
    sensor: SensorDetail,
    onWarmupChanged: (Int) -> Unit,
    onHideToggled: (Boolean) -> Unit,
    onUseAgain: () -> Unit,
    onForgetAndRescan: () -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenStopSensor: () -> Unit
) {
    val clinicalColors = LocalClinicalColors.current
    var showDiagnostics by remember { mutableStateOf(false) }
    var warmupSliderValue by remember(sensor.warmupMinutes) { mutableFloatStateOf(sensor.warmupMinutes.toFloat()) }

    val statusColor = when (sensor.status) {
        SensorStatus.CONNECTED_STREAMING -> clinicalColors.inRange
        SensorStatus.CONNECTED_IDLE -> MaterialTheme.colorScheme.primary
        SensorStatus.CONNECTING -> Color(0xFFD97706)
        SensorStatus.WARMING_UP -> Color(0xFFEA580C)
        SensorStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
        SensorStatus.EXPIRED, SensorStatus.ENDED -> Color.Gray
        SensorStatus.HIDDEN -> Color.DarkGray
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Sensor model & ID
        Text(
            text = sensor.sensorTypeName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "ID: ${sensor.id}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Connection state & last reading (the status label lives here instead of a badge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = "Bluetooth Status",
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        !sensor.isConnected -> sensor.status.label
                        sensor.isStreaming -> "Connected & Streaming"
                        else -> "Bluetooth Connected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (sensor.lastReadingTime > 0L) {
                val elapsedMin = ((System.currentTimeMillis() - sensor.lastReadingTime) / (60 * 1000L)).toInt()
                val lastReadingStr = when {
                    elapsedMin <= 1 -> stringResource(R.string.just_now)
                    elapsedMin < 60 -> stringResource(R.string.min_ago, elapsedMin)
                    else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sensor.lastReadingTime))
                }
                Text(
                    text = "Reading: $lastReadingStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))


        // Warmup Status Banner (if currently warming up)
        if (sensor.isCurrentlyWarmingUp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${stringResource(R.string.sensor_warmup_gauge)}: ${sensor.warmupRemainingMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Prominent Lifespan & Expected End Date Section (Moved out of technical details)
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.sensor_lifetime_gauge),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = formatTimeRemaining(sensor.daysRemaining),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (sensor.daysRemaining > 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Expected End Date (Crucial info clearly highlighted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.sensor_expected_end),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = getDisplayExpectedEnd(sensor),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            val startStr = getDisplayStartTime(sensor)
            if (!startStr.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.sensor_started),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = startStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { 1f - sensor.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = clinicalColors.inRange,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hide from Graph Switch Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (sensor.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Hide from Glucose Graph",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Exclude readings from the active graph view",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Switch(
                checked = sensor.isHidden,
                onCheckedChange = onHideToggled
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons: Full width each so text never wraps
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onUseAgain,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.sensor_reconnect), fontSize = 14.sp)
            }

            OutlinedButton(
                onClick = onForgetAndRescan,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.sensor_forget), fontSize = 14.sp)
            }

            OutlinedButton(
                onClick = onOpenCalibration,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.calibration_title), fontSize = 14.sp)
            }

            OutlinedButton(
                onClick = onOpenStopSensor,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Sensor", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Collapsible Technical Diagnostics Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDiagnostics = !showDiagnostics }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Technical Details & Diagnostics",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Icon(
                imageVector = if (showDiagnostics) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }

        AnimatedVisibility(
            visible = showDiagnostics,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                DiagField(label = "MAC Address", value = sensor.macAddress ?: "Not paired")
                DiagField(label = "Sensor Generation", value = "${sensor.sensorGen}")
                DiagField(label = "Calibration Status", value = if (sensor.hasCalibration) "User Calibrated" else "Factory Calibration")

                Spacer(modifier = Modifier.height(10.dp))

                // Warmup Period Adjuster (Advanced, kept here for parity with Sensors.java)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Warmup Stabilization Time",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${warmupSliderValue.toInt()} min",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = warmupSliderValue,
                        onValueChange = { warmupSliderValue = it },
                        onValueChangeFinished = { onWarmupChanged(warmupSliderValue.toInt()) },
                        valueRange = sensor.minWarmupMinutes.toFloat()..180f,
                        steps = 11
                    )
                    Text(
                        text = "Default for Libre is 60 min. Adjust only if using specialized sensor configurations.",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Processed & Aligned Diagnostic Entries
                val diagItems = remember(sensor.rawDiagnosticText) { parseDiagnosticLog(sensor.rawDiagnosticText) }
                val filteredDiagItems = remember(diagItems, sensor.id, sensor.name) {
                    diagItems.filterNot { item ->
                        item.label.isEmpty() && (item.value.trim() == sensor.id.trim() || item.value.trim() == sensor.name.trim())
                    }
                }

                if (filteredDiagItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.sensor_diagnostic_details),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    filteredDiagItems.forEach { item ->
                        if (item.label.isNotEmpty()) {
                            DiagField(label = item.label, value = item.value)
                        } else {
                            Text(
                                text = item.value,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class DiagnosticItem(
    val label: String,
    val value: String
)

/**
 * Parses raw diagnostic HTML / text from Juggluco native backend,
 * stripping tags, decoding entities like &nbsp; and &nbps;, and aligning tab/colon key-values.
 */
fun parseDiagnosticLog(rawText: String): List<DiagnosticItem> {
    if (rawText.isBlank()) return emptyList()

    // 1. Clean up HTML entities including typo &nbps; and &nbsp;
    val cleanedHtml = rawText
        .replace("&nbps;", " ")
        .replace("&nbsp;", " ")
        .replace("&emsp;", " ")
        .replace("&ensp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#160;", " ")
        .replace("\u00A0", " ")

    // 2. Normalize breaks and paragraph ends to newlines
    val withNewlines = cleanedHtml
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>"), "\n")
        .replace(Regex("(?i)</h1>"), "\n")
        .replace(Regex("(?i)</h2>"), "\n")
        .replace(Regex("(?i)</div>"), "\n")

    // 3. Strip remaining HTML tags
    val textOnly = withNewlines.replace(Regex("<[^>]+>"), "")

    val items = mutableListOf<DiagnosticItem>()
    val lines = textOnly.lines()
    var i = 0
    while (i < lines.size) {
        val rawLine = lines[i].trim()
        i++
        if (rawLine.isBlank()) continue

        // Check if line contains tabs separating label and value (standard Juggluco C++ output: "Label:\t\tValue")
        if (rawLine.contains('\t')) {
            val parts = rawLine.split(Regex("\t+")).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val label = parts[0].removeSuffix(":")
                val value = parts.drop(1).joinToString(" ")
                items.add(DiagnosticItem(label, value))
                continue
            }
        }

        // Check if line contains colon separating label and value (e.g. "Label: Value")
        if (rawLine.contains(':')) {
            val colonIndex = rawLine.indexOf(':')
            val label = rawLine.substring(0, colonIndex).trim()
            val value = rawLine.substring(colonIndex + 1).trim()
            if (value.isNotEmpty()) {
                items.add(DiagnosticItem(label, value))
                continue
            } else if (i < lines.size && lines[i].trim().isNotEmpty() && !lines[i].contains(':')) {
                val nextVal = lines[i].trim()
                i++
                items.add(DiagnosticItem(label, nextVal))
                continue
            }
        }

        // Standalone message or label without separator
        items.add(DiagnosticItem("", rawLine))
    }

    return items
}

private fun getDisplayExpectedEnd(sensor: SensorDetail): String {
    if (sensor.formattedExpectedEnd.isNotEmpty()) {
        return sensor.formattedExpectedEnd
    }
    val diagItems = parseDiagnosticLog(sensor.rawDiagnosticText)
    val endItem = diagItems.firstOrNull {
        it.label.contains("expected", ignoreCase = true) ||
        it.label.contains("end", ignoreCase = true) ||
        it.label.contains("fin", ignoreCase = true) ||
        it.label.contains("ende", ignoreCase = true)
    }
    return endItem?.value ?: "14 Days"
}

private fun getDisplayStartTime(sensor: SensorDetail): String? {
    if (sensor.formattedStartTime.isNotEmpty()) {
        return sensor.formattedStartTime
    }
    val diagItems = parseDiagnosticLog(sensor.rawDiagnosticText)
    val startItem = diagItems.firstOrNull {
        it.label.contains("start", ignoreCase = true) ||
        it.label.contains("début", ignoreCase = true) ||
        it.label.contains("inicio", ignoreCase = true)
    }
    val rawValue = startItem?.value ?: return null
    // If the diagnostic value includes time (e.g. "yyyy-MM-dd HH:mm"), extract just the date portion
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(rawValue.take(10))
        if (parsed != null) {
            SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(parsed)
        } else {
            rawValue.substringBefore(" ")
        }
    } catch (_: Throwable) {
        rawValue.substringBefore(" ")
    }
}

private fun formatTimeRemaining(daysRemaining: Float): String {
    if (daysRemaining <= 0f) return "Expired"
    val days = daysRemaining.toInt()
    val hours = ((daysRemaining - days) * 24).toInt()
    return when {
        days > 1 -> "$days days remaining"
        days == 1 -> if (hours > 0) "1 day, $hours hr remaining" else "1 day remaining"
        hours > 0 -> "$hours hours remaining"
        else -> "Less than 1 hour remaining"
    }
}

@Composable
private fun DiagField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun NoSensorPairedCard(onScanClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Sensors,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No Active CGM Sensor",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Hold phone directly to your sensor to scan via NFC, or pair a compatible Bluetooth transmitter to begin streaming continuous readings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onScanClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan / Start Sensor", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Guidance on compatible hardware for new users
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Supported CGM Hardware",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Juggluco supports direct Bluetooth streaming & NFC scanning with:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            DeviceSupportRow(name = "FreeStyle Libre 2 & 3", note = "Direct BLE streaming & NFC scan")
            DeviceSupportRow(name = "Dexcom G7 / ONE+", note = "Direct Bluetooth transmitter")
            DeviceSupportRow(name = "SiBionics (GS1 / GS3)", note = "Direct Bluetooth streaming")
            DeviceSupportRow(name = "Accu-Chek SmartGuide", note = "Direct Bluetooth streaming")
            DeviceSupportRow(name = "Contour / Accu-Chek Meters", note = "Bluetooth blood glucose check sync")
        }
    }
}

@Composable
private fun PreviousSensorItem(sensor: SensorDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ScreenLayout.cardContainerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenLayout.CardPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = sensor.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ID: ${sensor.id} • ${if (sensor.startTime > 0) SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(sensor.startTime)) else "Past wear"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Text(
                text = sensor.status.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceSupportRow(name: String, note: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
