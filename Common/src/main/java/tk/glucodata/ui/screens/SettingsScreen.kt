package tk.glucodata.ui.screens

import android.app.Activity
import android.widget.Toast
import tk.glucodata.Natives
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Sync
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
    darkThemeOverride: Boolean? = null,
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
    val mirrorConnections by repository.mirrorConnections.collectAsState()
    val context = LocalContext.current

    var currentLowSlider by remember(targetLow) { mutableFloatStateOf(targetLow) }
    var currentHighSlider by remember(targetHigh) { mutableFloatStateOf(targetHigh) }

    var showExportDialog by remember { mutableStateOf(false) }
    var showTalkerDialog by remember { mutableStateOf(false) }
    var showFloatingConfigDialog by remember { mutableStateOf(false) }
    var showMirrorConfigDialog by remember { mutableStateOf(false) }
    var showDevGuideDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 96.dp)
    ) {
        // Page Header
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION 1: GLUCOSE TARGETS & UNITS
        SettingsSectionCard(
            categorySubtitle = stringResource(R.string.settings_cat_glucose),
            title = stringResource(R.string.settings_card_target_range),
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
                        text = stringResource(R.string.settings_unit_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (unit == GlucoseUnit.MG_DL) stringResource(R.string.settings_unit_mgdl_desc) else stringResource(R.string.settings_unit_mmoll_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.mgdL),
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
                        text = stringResource(R.string.mmolL),
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
                        text = stringResource(R.string.settings_target_low),
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
                        text = stringResource(R.string.settings_target_high),
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
        SettingsSectionCard(
            categorySubtitle = stringResource(R.string.settings_cat_alarms),
            title = stringResource(R.string.settings_card_alarms),
            icon = Icons.Default.NotificationsActive
        ) {
            SettingToggleRow(
                title = stringResource(R.string.settings_alarm_low),
                subtitle = stringResource(R.string.settings_alarm_low_desc, unit.format(alarms.lowThreshold)),
                checked = alarms.lowAlarmEnabled,
                onCheckedChange = { repository.updateAlarms(alarms.copy(lowAlarmEnabled = it)) }
            )

            if (alarms.lowAlarmEnabled) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_alarm_threshold_snooze, unit.format(alarms.lowThreshold), alarms.lowSnoozeMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = alarms.lowThreshold,
                        onValueChange = { repository.updateAlarms(alarms.copy(lowThreshold = it)) },
                        valueRange = 55f..95f
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stringResource(R.string.settings_alarm_snooze_duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                title = stringResource(R.string.settings_alarm_high),
                subtitle = stringResource(R.string.settings_alarm_high_desc, unit.format(alarms.highThreshold)),
                checked = alarms.highAlarmEnabled,
                onCheckedChange = { repository.updateAlarms(alarms.copy(highAlarmEnabled = it)) }
            )

            if (alarms.highAlarmEnabled) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_alarm_threshold_snooze, unit.format(alarms.highThreshold), alarms.highSnoozeMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = alarms.highThreshold,
                        onValueChange = { repository.updateAlarms(alarms.copy(highThreshold = it)) },
                        valueRange = 140f..250f
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = stringResource(R.string.settings_alarm_snooze_duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                title = stringResource(R.string.settings_alarm_loss),
                subtitle = stringResource(R.string.settings_alarm_loss_desc, alarms.lossWaitMinutes),
                checked = alarms.lossAlarmEnabled,
                onCheckedChange = { repository.updateAlarms(alarms.copy(lossAlarmEnabled = it)) }
            )

            if (alarms.lossAlarmEnabled) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                    Text(text = stringResource(R.string.settings_alarm_loss_wait), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                title = stringResource(R.string.settings_alarm_value_available),
                subtitle = stringResource(R.string.settings_alarm_value_available_desc),
                checked = alarms.valueAvailableNotification,
                onCheckedChange = { repository.updateAlarms(alarms.copy(valueAvailableNotification = it)) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            // Alarm Stream Selector (Compact, Non-overflowing chips)
            val currentStreamLabel = when (alarms.soundStream) {
                AlarmSoundStream.ALARM -> stringResource(R.string.settings_stream_alarm)
                AlarmSoundStream.NOTIFICATION -> stringResource(R.string.settings_stream_notification)
                AlarmSoundStream.MEDIA -> stringResource(R.string.settings_stream_media)
            }
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = stringResource(R.string.settings_alarm_stream),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.settings_alarm_active_channel, currentStreamLabel),
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
                            AlarmSoundStream.ALARM -> stringResource(R.string.settings_stream_alarm)
                            AlarmSoundStream.NOTIFICATION -> stringResource(R.string.settings_stream_notification)
                            AlarmSoundStream.MEDIA -> stringResource(R.string.settings_stream_media)
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

        // SECTION: DEVELOPMENT & TESTING STRATEGIES (SIDE-BY-SIDE INSTALL)
        SettingsSectionCard(
            categorySubtitle = stringResource(R.string.settings_cat_dev_testing),
            title = stringResource(R.string.settings_card_dev_testing),
            icon = Icons.Default.Devices
        ) {
            Text(
                text = stringResource(R.string.settings_dev_testing_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Status row: Active Role & Configured Connections
            val isReceiverActive = mirrorConnections.any { it.isReceiver }
            val isSenderActive = mirrorConnections.any { !it.isReceiver }
            val roleText = when {
                isReceiverActive -> stringResource(R.string.settings_dev_role_receiver)
                isSenderActive -> stringResource(R.string.settings_dev_role_sender)
                else -> stringResource(R.string.settings_dev_role_standalone)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isReceiverActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = roleText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isReceiverActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                val listenPort = remember { try { Natives.getreceiveport() ?: "17580" } catch (_: Throwable) { "17580" } }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Port: $listenPort",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showMirrorConfigDialog = true },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_dev_btn_setup_relay), fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { showDevGuideDialog = true },
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_dev_btn_guide), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 3: BROADCASTS & SYNC
        SettingsSectionCard(
            categorySubtitle = stringResource(R.string.settings_cat_integrations),
            title = stringResource(R.string.settings_card_exchanges),
            icon = Icons.Default.CloudSync
        ) {
            SettingToggleRow(
                title = stringResource(R.string.settings_glucodata_broadcast),
                subtitle = stringResource(R.string.settings_glucodata_broadcast_desc),
                checked = exchanges.glucodataBroadcast,
                onCheckedChange = { repository.setGlucodataBroadcast(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = stringResource(R.string.settings_xdrip_broadcast),
                subtitle = stringResource(R.string.settings_xdrip_broadcast_desc),
                checked = exchanges.xdripBroadcast,
                onCheckedChange = { repository.setXdripBroadcast(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = stringResource(R.string.settings_librelink_broadcast),
                subtitle = stringResource(R.string.settings_librelink_broadcast_desc),
                checked = exchanges.librelinkBroadcast,
                onCheckedChange = { repository.setLibrelinkBroadcast(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = stringResource(R.string.settings_health_connect),
                subtitle = stringResource(R.string.settings_health_connect_desc),
                checked = exchanges.healthConnect,
                onCheckedChange = { repository.setHealthConnect(it, context as? Activity) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = stringResource(R.string.settings_libreview),
                subtitle = stringResource(R.string.settings_libreview_desc),
                checked = exchanges.libreViewEnabled,
                onCheckedChange = { repository.setLibreViewEnabled(it) }
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
                        text = stringResource(R.string.settings_xdrip_server),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_xdrip_server_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = exchanges.xdripWebServer,
                        onCheckedChange = { repository.setXdripWebServer(it) }
                    )
                    if (exchanges.xdripWebServer) {
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedButton(
                            onClick = {
                                if (context is Activity) {
                                    repository.openWebServerConfig(context)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.settings_btn_config), fontSize = 11.sp)
                        }
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
                        text = stringResource(R.string.settings_mirror_sync),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_mirror_sync_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { showMirrorConfigDialog = true }) {
                    Text(stringResource(R.string.settings_btn_config))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 4: DISPLAY & THEME
        SettingsSectionCard(
            categorySubtitle = stringResource(R.string.settings_cat_display),
            title = stringResource(R.string.settings_card_display),
            icon = Icons.Default.Palette
        ) {
            // Theme Mode Selector
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = stringResource(R.string.settings_theme_pref),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.settings_theme_pref_desc),
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
                        selected = darkThemeOverride == true,
                        onClick = { onDarkThemeChanged(true) },
                        label = { Text(stringResource(R.string.settings_theme_dark), fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    FilterChip(
                        selected = darkThemeOverride == false,
                        onClick = { onDarkThemeChanged(false) },
                        label = { Text(stringResource(R.string.settings_theme_light), fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    FilterChip(
                        selected = darkThemeOverride == null,
                        onClick = { onDarkThemeChanged(null) },
                        label = { Text(stringResource(R.string.settings_theme_system), fontSize = 12.sp) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            SettingToggleRow(
                title = stringResource(R.string.settings_floating_widget),
                subtitle = stringResource(R.string.settings_floating_widget_desc),
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
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showFloatingConfigDialog = true }) {
                        Text(stringResource(R.string.settings_btn_configure_appearance))
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
                        text = stringResource(R.string.settings_voice_speech),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_voice_speech_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { showTalkerDialog = true }) {
                    Text(stringResource(R.string.settings_btn_configure))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = stringResource(R.string.settings_status_bar),
                subtitle = stringResource(R.string.settings_status_bar_desc),
                checked = displayConfig.statusBarNotification,
                onCheckedChange = { repository.setStatusBarNotification(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = stringResource(R.string.settings_fullscreen),
                subtitle = stringResource(R.string.settings_fullscreen_desc),
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
        SettingsSectionCard(
            categorySubtitle = stringResource(R.string.settings_cat_hardware),
            title = stringResource(R.string.settings_card_hardware),
            icon = Icons.Default.Nfc
        ) {
            SettingToggleRow(
                title = stringResource(R.string.settings_nfc_sound),
                subtitle = stringResource(R.string.settings_nfc_sound_desc),
                checked = hardwareConfig.nfcSound,
                onCheckedChange = {
                    // Update NFC sound
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            SettingToggleRow(
                title = stringResource(R.string.settings_nfc_launch),
                subtitle = stringResource(R.string.settings_nfc_launch_desc),
                checked = hardwareConfig.globalScanStartsApp,
                onCheckedChange = {
                    // Update global scan
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 6: DATA & LEGACY
        SettingsSectionCard(
            categorySubtitle = stringResource(R.string.settings_cat_data),
            title = stringResource(R.string.settings_card_data),
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
                        text = stringResource(R.string.settings_export_sensor_data),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_export_sensor_data_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onExportData) {
                    Text(stringResource(R.string.export))
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
                        text = stringResource(R.string.settings_legacy_canvas),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_legacy_canvas_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onOpenLegacyView) {
                    Text(stringResource(R.string.settings_btn_switch))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 7: ABOUT
        SettingsSectionCard(
            categorySubtitle = stringResource(R.string.settings_cat_about),
            title = stringResource(R.string.settings_card_about),
            icon = Icons.Default.Info
        ) {
            Text(
                text = stringResource(R.string.settings_version_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_about_details),
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
            repository = repository,
            onDismiss = { showMirrorConfigDialog = false }
        )
    }

    // Side-by-Side Dev Testing Guide Dialog
    if (showDevGuideDialog) {
        AlertDialog(
            onDismissRequest = { showDevGuideDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_dev_guide_title), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                val guideHtml = stringResource(R.string.settings_dev_guide_body)
                val spanned = remember(guideHtml) {
                    androidx.core.text.HtmlCompat.fromHtml(guideHtml, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = spanned.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showDevGuideDialog = false }) {
                    Text(stringResource(R.string.dialog_done))
                }
            }
        )
    }
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
    categorySubtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (categorySubtitle != null) {
                Text(
                    text = categorySubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
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
