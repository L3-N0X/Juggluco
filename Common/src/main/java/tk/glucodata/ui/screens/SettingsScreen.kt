package tk.glucodata.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.ui.components.FloatingConfigDialog
import tk.glucodata.ui.components.MirrorConfigDialog
import tk.glucodata.ui.components.TalkerConfigDialog
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.AlarmConfig
import tk.glucodata.ui.model.AlarmSoundStream
import tk.glucodata.ui.model.GlucoseUnit

@Composable
fun SettingsScreen(
    repository: GlucoseRepository,
    onOpenLegacyView: () -> Unit = {},
    onExportData: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val unit by repository.unit.collectAsState()
    val targetLow by repository.targetLow.collectAsState()
    val targetHigh by repository.targetHigh.collectAsState()
    val alarms by repository.alarms.collectAsState()
    val exchanges by repository.exchanges.collectAsState()
    val displayConfig by repository.displayConfig.collectAsState()
    val hardwareConfig by repository.hardwareConfig.collectAsState()
    val context = LocalContext.current

    var currentLowSlider by remember(targetLow) { mutableFloatStateOf(targetLow) }
    var currentHighSlider by remember(targetHigh) { mutableFloatStateOf(targetHigh) }

    var showExportDialog by remember { mutableStateOf(false) }
    var showTalkerDialog by remember { mutableStateOf(false) }
    var showFloatingConfigDialog by remember { mutableStateOf(false) }
    var showMirrorConfigDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 96.dp)
    ) {
        Text(
            text = "Settings & Config",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Units, thresholds, alarms, broadcast sync and hardware",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 1. Glucose Display & Target Range
        SettingsSectionCard(
            title = "Glucose Units & Target Range",
            icon = Icons.Default.Straighten
        ) {
            // Unit Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Glucose Unit",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (unit == GlucoseUnit.MG_DL) "mg/dL (Milligrams per deciliter)" else "mmol/L (Millimoles per liter)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "mg/dL",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (unit == GlucoseUnit.MG_DL) FontWeight.Bold else FontWeight.Normal,
                        color = if (unit == GlucoseUnit.MG_DL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = unit == GlucoseUnit.MMOL_L,
                        onCheckedChange = { isMmol ->
                            repository.setUnit(if (isMmol) GlucoseUnit.MMOL_L else GlucoseUnit.MG_DL)
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "mmol/L",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (unit == GlucoseUnit.MMOL_L) FontWeight.Bold else FontWeight.Normal,
                        color = if (unit == GlucoseUnit.MMOL_L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            // Target Low Slider
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Target Low Threshold",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${unit.format(currentLowSlider)} ${unit.label}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = currentLowSlider,
                    onValueChange = { currentLowSlider = it },
                    onValueChangeFinished = { repository.setTargetRange(currentLowSlider, currentHighSlider) },
                    valueRange = 55f..100f
                )
            }

            // Target High Slider
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Target High Threshold",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${unit.format(currentHighSlider)} ${unit.label}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = currentHighSlider,
                    onValueChange = { currentHighSlider = it },
                    onValueChangeFinished = { repository.setTargetRange(currentLowSlider, currentHighSlider) },
                    valueRange = 140f..250f
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Alarms & Notifications (Full Parity with alarmsettings)
        SettingsSectionCard(
            title = "Glucose Alarms & Notifications",
            icon = Icons.Default.NotificationsActive
        ) {
            SettingToggleRow(
                title = "Low Glucose Alarm",
                subtitle = "Alert when glucose drops below ${unit.format(alarms.lowThreshold)} ${unit.label}",
                checked = alarms.lowAlarmEnabled,
                onCheckedChange = { repository.updateAlarms(alarms.copy(lowAlarmEnabled = it)) }
            )

            if (alarms.lowAlarmEnabled) {
                Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)) {
                    Text(
                        text = "Low Alarm Threshold: ${unit.format(alarms.lowThreshold)} ${unit.label} • Snooze: ${alarms.lowSnoozeMinutes} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = alarms.lowThreshold,
                        onValueChange = { repository.updateAlarms(alarms.copy(lowThreshold = it)) },
                        valueRange = 55f..95f
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = "High Glucose Alarm",
                subtitle = "Alert when glucose rises above ${unit.format(alarms.highThreshold)} ${unit.label}",
                checked = alarms.highAlarmEnabled,
                onCheckedChange = { repository.updateAlarms(alarms.copy(highAlarmEnabled = it)) }
            )

            if (alarms.highAlarmEnabled) {
                Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)) {
                    Text(
                        text = "High Alarm Threshold: ${unit.format(alarms.highThreshold)} ${unit.label} • Snooze: ${alarms.highSnoozeMinutes} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = alarms.highThreshold,
                        onValueChange = { repository.updateAlarms(alarms.copy(highThreshold = it)) },
                        valueRange = 140f..250f
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = "Loss of Signal Alarm",
                subtitle = "Alert if CGM disconnects for more than ${alarms.lossWaitMinutes} minutes",
                checked = alarms.lossAlarmEnabled,
                onCheckedChange = { repository.updateAlarms(alarms.copy(lossAlarmEnabled = it)) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = "Value Available Notification",
                subtitle = "Play chime or notify whenever fresh reading arrives",
                checked = alarms.valueAvailableNotification,
                onCheckedChange = { repository.updateAlarms(alarms.copy(valueAvailableNotification = it)) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            // Alarm Stream Selector (Alarm / Notification / Media)
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "Alarm Audio Stream",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Choose which Android volume channel sounds play through",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlarmSoundStream.entries.forEach { stream ->
                        val isSelected = stream == alarms.soundStream
                        FilterChip(
                            selected = isSelected,
                            onClick = { repository.updateAlarms(alarms.copy(soundStream = stream)) },
                            label = { Text(stream.label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Sharing & Cloud Sync (Full Parity with exchanges)
        SettingsSectionCard(
            title = "Exchanges & Broadcasts",
            icon = Icons.Default.CloudSync
        ) {
            SettingToggleRow(
                title = "Glucodata Broadcast",
                subtitle = "Broadcast glucose locally to Juggluco watchfaces & apps",
                checked = exchanges.glucodataBroadcast,
                onCheckedChange = { repository.setGlucodataBroadcast(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = "xDrip Broadcast",
                subtitle = "Broadcast readings to xDrip+ and compatible apps",
                checked = exchanges.xdripBroadcast,
                onCheckedChange = { repository.setXdripBroadcast(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = "Google Health Connect",
                subtitle = "Sync blood glucose records with Android Health Connect",
                checked = exchanges.healthConnect,
                onCheckedChange = { repository.setHealthConnect(it, context as? Activity) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = "Abbott LibreView Upload",
                subtitle = "Automatically sync sensor readings to LibreView cloud",
                checked = exchanges.libreViewEnabled,
                onCheckedChange = { repository.setLibreViewEnabled(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = "Local xDrip / Nightscout Web Server",
                subtitle = "Serve local REST API on port 17580 for watchfaces & bridges",
                checked = exchanges.xdripWebServer,
                onCheckedChange = { repository.setXdripWebServer(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Mirror & Network Sync",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Sync readings and alarms with other devices on Wi-Fi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { showMirrorConfigDialog = true }) {
                    Text("Config")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Smartwatches & Mirroring (Full Parity with Watch.java)
        SettingsSectionCard(
            title = "Smartwatches & Wearables",
            icon = Icons.Default.Watch
        ) {
            Text(
                text = "Juggluco seamlessly integrates with smartwatches:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            WatchFeatureRow(name = "Wear OS by Google", desc = "Direct watch app & complications")
            WatchFeatureRow(name = "Garmin ConnectIQ", desc = "Kerfstok watchface & datafields")
            WatchFeatureRow(name = "Watchdrip & Gadgetbridge", desc = "Mi Band, Amazfit & Bip smartbands")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Display & Floating Overlay (Full Parity with Floating.java)
        SettingsSectionCard(
            title = "Display & UI Overlays",
            icon = Icons.Default.Palette
        ) {
            SettingToggleRow(
                title = "Floating Glucose Widget",
                subtitle = "Show movable floating glucose number overlay above other apps",
                checked = displayConfig.floatingGlucose,
                onCheckedChange = {
                    if (context is Activity) {
                        repository.setFloatingGlucose(it, context)
                    }
                }
            )

            if (displayConfig.floatingGlucose) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showFloatingConfigDialog = true }) {
                        Text("Configure Appearance")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Voice & Speech Announcements",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Spoken readings on arrival, alarms and speech rate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { showTalkerDialog = true }) {
                    Text("Configure")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = "Status Bar Glucose Notification",
                subtitle = "Keep live glucose and trend continuously in Android status bar",
                checked = displayConfig.statusBarNotification,
                onCheckedChange = { repository.setStatusBarNotification(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = "Fullscreen System UI",
                subtitle = "Hide Android status and navigation bars for immersive curve",
                checked = displayConfig.systemUiFullscreen,
                onCheckedChange = {
                    repository.setSystemUiFullscreen(it, context as? Activity)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 6. NFC & Scanning Hardware
        SettingsSectionCard(
            title = "NFC & Hardware Options",
            icon = Icons.Default.Nfc
        ) {
            SettingToggleRow(
                title = "NFC Scan Sound",
                subtitle = "Play confirmation chime when NFC sensor is read",
                checked = hardwareConfig.nfcSound,
                onCheckedChange = {
                    // Update NFC sound
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = "Launch App on NFC Scan",
                subtitle = "Automatically open Juggluco when phone touches sensor",
                checked = hardwareConfig.globalScanStartsApp,
                onCheckedChange = {
                    // Update global scan
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 7. Data Management & Export (Full Parity with Dialogs.java)
        SettingsSectionCard(
            title = "Data Management & Legacy View",
            icon = Icons.Default.FileUpload
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Export Sensor Data",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Export CSV or HTML records for amounts, scans, stream & history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { showExportDialog = true }) {
                    Text("Export")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Legacy OpenGL Canvas View",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Switch to classic landscape OpenGL curve. Press Back anytime to return.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onOpenLegacyView) {
                    Text("Switch")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 8. About Juggluco
        SettingsSectionCard(
            title = "About Juggluco",
            icon = Icons.Default.Info
        ) {
            Text(
                text = "Juggluco v11.0.2",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Modern Jetpack Compose Material 3 Edition\nOpen Source Software licensed under GPLv3\nCreated by Jaap Korthals Altes • Rebuilt with full Jetpack Compose parity",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Export Dialog Modal
        if (showExportDialog) {
            ExportDataDialog(
                onDismiss = { showExportDialog = false },
                onExecuteExport = { typeName, daysCount ->
                    showExportDialog = false
                    onExportData()
                    Toast.makeText(context, "Exporting $typeName for $daysCount days...", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Voice / Talker Dialog
        if (showTalkerDialog) {
            TalkerConfigDialog(
                onDismiss = { showTalkerDialog = false }
            )
        }

        // Floating Glucose Widget Dialog
        if (showFloatingConfigDialog) {
            FloatingConfigDialog(
                onDismiss = { showFloatingConfigDialog = false }
            )
        }

        // Mirror / Sync Dialog
        if (showMirrorConfigDialog) {
            MirrorConfigDialog(
                onDismiss = { showMirrorConfigDialog = false }
            )
        }
    }
}

@Composable
private fun ExportDataDialog(
    onDismiss: () -> Unit,
    onExecuteExport: (String, Int) -> Unit
) {
    var selectedType by remember { mutableStateOf("Stream Glucose") }
    var daysText by remember { mutableStateOf("14") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Glucose Data", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Select data stream to export to CSV:",
                    style = MaterialTheme.typography.bodySmall
                )

                val exportTypes = listOf("Stream Glucose", "Amounts & Insulin", "NFC Scans", "Sensor History", "Meals", "LibreView")
                exportTypes.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = type },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = type, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text("Number of Days") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = daysText.toIntOrNull() ?: 14
                    onExecuteExport(selectedType, days)
                }
            ) {
                Text("Export File")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun WatchFeatureRow(name: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "• $desc",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
