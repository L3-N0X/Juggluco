package tk.glucodata.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
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
    isDarkTheme: Boolean = false,
    onDarkThemeChanged: (Boolean?) -> Unit = {},
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 96.dp)
    ) {
        // Page Header
        Text(
            text = "Settings & Config",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Thresholds, alarms, broadcasts, appearance and hardware",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION 1: GLUCOSE TARGETS & UNITS
        SettingsCategoryHeader(title = "GLUCOSE TARGETS & UNITS")

        SettingsSectionCard(
            title = "Target Range & Units",
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
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
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
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
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
                        text = unit.format(currentLowSlider),
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
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
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
                        text = unit.format(currentHighSlider),
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

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 2: ALARMS & SOUNDS
        SettingsCategoryHeader(title = "ALARMS & NOTIFICATIONS")

        SettingsSectionCard(
            title = "Glucose Alarms & Chimes",
            icon = Icons.Default.NotificationsActive
        ) {
            SettingToggleRow(
                title = "Low Glucose Alarm",
                subtitle = "Alert when glucose drops below ${unit.format(alarms.lowThreshold)}",
                checked = alarms.lowAlarmEnabled,
                onCheckedChange = { repository.updateAlarms(alarms.copy(lowAlarmEnabled = it)) }
            )

            if (alarms.lowAlarmEnabled) {
                Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)) {
                    Text(
                        text = "Threshold: ${unit.format(alarms.lowThreshold)} • Snooze: ${alarms.lowSnoozeMinutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = alarms.lowThreshold,
                        onValueChange = { repository.updateAlarms(alarms.copy(lowThreshold = it)) },
                        valueRange = 55f..95f
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Snooze Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5, 10, 15, 20, 30, 45, 60).forEach { mins ->
                            FilterChip(
                                selected = alarms.lowSnoozeMinutes == mins,
                                onClick = { repository.updateAlarms(alarms.copy(lowSnoozeMinutes = mins)) },
                                label = { Text("${mins}m", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = "High Glucose Alarm",
                subtitle = "Alert when glucose rises above ${unit.format(alarms.highThreshold)}",
                checked = alarms.highAlarmEnabled,
                onCheckedChange = { repository.updateAlarms(alarms.copy(highAlarmEnabled = it)) }
            )

            if (alarms.highAlarmEnabled) {
                Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)) {
                    Text(
                        text = "Threshold: ${unit.format(alarms.highThreshold)} • Snooze: ${alarms.highSnoozeMinutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = alarms.highThreshold,
                        onValueChange = { repository.updateAlarms(alarms.copy(highThreshold = it)) },
                        valueRange = 140f..250f
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Snooze Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(15, 30, 45, 60, 90, 120).forEach { mins ->
                            FilterChip(
                                selected = alarms.highSnoozeMinutes == mins,
                                onClick = { repository.updateAlarms(alarms.copy(highSnoozeMinutes = mins)) },
                                label = { Text("${mins}m", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = "Loss of Signal Alarm",
                subtitle = "Alert if CGM disconnects for more than ${alarms.lossWaitMinutes} minutes",
                checked = alarms.lossAlarmEnabled,
                onCheckedChange = { repository.updateAlarms(alarms.copy(lossAlarmEnabled = it)) }
            )

            if (alarms.lossAlarmEnabled) {
                Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)) {
                    Text(text = "Wait Duration Before Alarm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(10, 15, 20, 30, 45, 60).forEach { mins ->
                            FilterChip(
                                selected = alarms.lossWaitMinutes == mins,
                                onClick = { repository.updateAlarms(alarms.copy(lossWaitMinutes = mins)) },
                                label = { Text("${mins}m", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = "Value Available Notification",
                subtitle = "Chime when fresh sensor reading arrives",
                checked = alarms.valueAvailableNotification,
                onCheckedChange = { repository.updateAlarms(alarms.copy(valueAvailableNotification = it)) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            // Alarm Stream Selector (Compact, Non-overflowing chips)
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "Alarm Audio Stream",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Active channel: ${alarms.soundStream.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlarmSoundStream.entries.forEach { stream ->
                        val isSelected = stream == alarms.soundStream
                        val shortLabel = when (stream) {
                            AlarmSoundStream.ALARM -> "Alarm Stream"
                            AlarmSoundStream.NOTIFICATION -> "Notification"
                            AlarmSoundStream.MEDIA -> "Media"
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { repository.updateAlarms(alarms.copy(soundStream = stream)) },
                            label = { Text(shortLabel, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 3: BROADCASTS & SYNC
        SettingsCategoryHeader(title = "INTEGRATIONS & BROADCASTS")

        SettingsSectionCard(
            title = "Exchanges & Local Sync",
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
                title = "Local xDrip / Nightscout Server",
                subtitle = "Serve local REST API on port 17580 for watchfaces",
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
                        text = "Sync readings and alarms with other devices over Wi-Fi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { showMirrorConfigDialog = true }) {
                    Text("Config")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 4: DISPLAY & THEME
        SettingsCategoryHeader(title = "DISPLAY & THEME")

        SettingsSectionCard(
            title = "Appearance & Overlays",
            icon = Icons.Default.Palette
        ) {
            // Theme Mode Selector
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "Theme Preference",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Select dark, light, or follow system theme",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isDarkTheme,
                        onClick = { onDarkThemeChanged(true) },
                        label = { Text("Dark Theme", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    FilterChip(
                        selected = !isDarkTheme,
                        onClick = { onDarkThemeChanged(false) },
                        label = { Text("Light Theme", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onDarkThemeChanged(null) },
                        label = { Text("System Default", fontSize = 12.sp) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            SettingToggleRow(
                title = "Floating Glucose Widget",
                subtitle = "Show movable floating glucose overlay above other apps",
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = stringResource(R.string.minimalist_units),
                subtitle = stringResource(R.string.minimalist_units_desc),
                checked = displayConfig.minimalistUnits,
                onCheckedChange = { repository.setMinimalistUnits(it) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 5: NFC & HARDWARE
        SettingsCategoryHeader(title = "NFC & HARDWARE")

        SettingsSectionCard(
            title = "Scanning & Sound Options",
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

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 6: DATA & LEGACY
        SettingsCategoryHeader(title = "DATA MANAGEMENT & TOOLS")

        SettingsSectionCard(
            title = "Export & Legacy View",
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

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 7: ABOUT
        SettingsCategoryHeader(title = "ABOUT")

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
                text = "Modern Jetpack Compose Material 3 Edition\nOpen Source Software licensed under GPLv3\nCreated by Jaap Korthals Altes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Export Dialog Modal
    if (showExportDialog) {
        ExportDataDialog(
            onDismiss = { showExportDialog = false },
            onExecuteExport = { typeIndex, isCalibrated, days ->
                showExportDialog = false
                val activity = context as? tk.glucodata.MainActivity
                if (activity != null) {
                    tk.glucodata.Dialogs.runExport(activity, typeIndex, isCalibrated, days)
                } else {
                    onExportData()
                }
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

@Composable
private fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun ExportDataDialog(
    onDismiss: () -> Unit,
    onExecuteExport: (typeIndex: Int, isCalibrated: Boolean, days: Float) -> Unit
) {
    val exportOptions = listOf(
        Pair(stringResource(R.string.streamname), 2),
        Pair(stringResource(R.string.amountsname), 0),
        Pair(stringResource(R.string.scansname), 1),
        Pair(stringResource(R.string.historyname), 3),
        Pair(stringResource(R.string.mealsname), 4),
        Pair(stringResource(R.string.libreviewname), 5)
    )

    var selectedTypeIndex by remember { mutableIntStateOf(2) }
    var isCalibrated by remember {
        mutableStateOf(try { tk.glucodata.Natives.getDoCalibrate() } catch (_: Throwable) { false })
    }
    val defaultDays = remember {
        try {
            val hour24 = 1000 * 60 * 60 * 24L
            val endtime = tk.glucodata.Natives.getendtime()
            val daysnr = (endtime - tk.glucodata.Natives.oldestdatatime() + hour24 - 1) / hour24
            daysnr.toInt().coerceAtLeast(1).toString()
        } catch (_: Throwable) {
            "14"
        }
    }
    var daysText by remember { mutableStateOf(defaultDays) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_glucose_data), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.export_select_stream),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                exportOptions.forEach { (label, typeIndex) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTypeIndex = typeIndex },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTypeIndex == typeIndex,
                            onClick = { selectedTypeIndex = typeIndex }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selectedTypeIndex == typeIndex) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                if (selectedTypeIndex != 4) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.export_include_calibrated),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = isCalibrated,
                            onCheckedChange = { isCalibrated = it }
                        )
                    }
                }

                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text(stringResource(R.string.export_days_count)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = daysText.toFloatOrNull() ?: 14f
                    onExecuteExport(selectedTypeIndex, isCalibrated, days)
                }
            ) {
                Text(stringResource(R.string.export_start_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
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
