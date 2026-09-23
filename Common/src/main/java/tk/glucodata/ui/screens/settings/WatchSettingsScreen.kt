package tk.glucodata.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.WearWatchDevice
import tk.glucodata.ui.screens.ScreenLayout

@Composable
fun WatchSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit,
    onOpenMirrorConfig: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val watchConfig by repository.watchConfig.collectAsState()
    val wearDevices by repository.wearDevices.collectAsState()
    val wearDiagnosticInfo by repository.wearDiagnosticInfo.collectAsState()

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_group_watch_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) {
        // Section 1: Wear OS Integration
        SettingsSection(title = stringResource(R.string.loc_wear_integration)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_wearos_service),
                subtitle = stringResource(R.string.settings_wearos_service_desc),
                icon = Icons.Default.Watch,
                checked = watchConfig.wearOsEnabled,
                onCheckedChange = { enabled ->
                    repository.setWearOsEnabled(context, enabled)
                    Toast.makeText(
                        context,
                        context.getString(if (enabled) R.string.loc_wear_service_enabled else R.string.loc_wear_service_disabled),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            if (watchConfig.wearOsEnabled) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_search_watches),
                    subtitle = stringResource(R.string.loc_wear_scan_desc),
                    icon = Icons.Default.Refresh,
                    onClick = {
                        repository.scanForWatches()
                        Toast.makeText(context, context.getString(R.string.loc_scanning_watches), Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Section 2: Discovered Wear OS Devices
        if (watchConfig.wearOsEnabled) {
            if (wearDevices.isNotEmpty()) {
                Text(
                    text = pluralStringResource(R.plurals.loc_connected_watches, wearDevices.size, wearDevices.size),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = ScreenLayout.CardPadding, top = 4.dp, bottom = 2.dp)
                )

                wearDevices.forEach { device ->
                    WearDeviceCard(
                        device = device,
                        onDirectSensorChanged = { direct ->
                            repository.setWatchDirectSensor(device.id, direct, device.isGalaxy, device.isEnterNumsOnWatch)
                        },
                        onEnterNumsChanged = { watchNums ->
                            repository.setWatchEnterNums(device.id, watchNums, device.isDirectSensor, device.isGalaxy)
                        },
                        onInitWatch = {
                            repository.initWatchApp(device.id, device.isGalaxy)
                            Toast.makeText(context, context.getString(R.string.loc_init_watch_app), Toast.LENGTH_SHORT).show()
                        },
                        onSync = {
                            repository.syncWatch(device.id)
                            Toast.makeText(context, context.getString(R.string.loc_trigger_watch_sync), Toast.LENGTH_SHORT).show()
                        },
                        onResetDefaults = {
                            repository.resetWatchDefaults(device.id, device.isGalaxy, context)
                            Toast.makeText(context, context.getString(R.string.loc_reset_watch_defaults), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } else {
                SettingsInfoCard(icon = Icons.Default.Info) {
                    Text(
                        text = stringResource(R.string.settings_no_watches_found),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.loc_wear_connect_instructions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Section 3: Compatibility & Diagnostics
        SettingsSection(title = stringResource(R.string.loc_app_ids_diagnostics)) {
            val isMismatchRisk = wearDiagnosticInfo.phoneAppId.endsWith(".dub") ||
                    wearDiagnosticInfo.phoneAppId.endsWith(".debug")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.loc_phone_app_id),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = wearDiagnosticInfo.phoneAppId.ifBlank { "tk.glucodata" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.loc_sync_mirror_port),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = wearDiagnosticInfo.mirrorPort.ifBlank { "8795" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.loc_wearable_receiver_state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(if (wearDiagnosticInfo.isReceiverServiceEnabled) R.string.loc_state_active else R.string.loc_state_inactive),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (wearDiagnosticInfo.isReceiverServiceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.loc_app_id_matching_rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }

        // Section 4: Other Smartwatches
        SettingsSection(title = stringResource(R.string.settings_other_watches)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_garmin_title),
                subtitle = stringResource(R.string.settings_garmin_desc),
                icon = Icons.Default.WatchLater,
                checked = watchConfig.garminEnabled,
                onCheckedChange = { enabled ->
                    repository.setGarminEnabled(enabled)
                }
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_watchdrip_title),
                subtitle = stringResource(R.string.settings_watchdrip_desc),
                icon = Icons.Default.Watch,
                checked = watchConfig.watchdripEnabled,
                onCheckedChange = { enabled ->
                    repository.setWatchdripEnabled(enabled)
                }
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_gadgetbridge_title),
                subtitle = stringResource(R.string.settings_gadgetbridge_desc),
                icon = Icons.Default.Bluetooth,
                checked = watchConfig.gadgetbridgeEnabled,
                onCheckedChange = { enabled ->
                    repository.setGadgetbridgeEnabled(enabled)
                }
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_watch_forward_alarm),
                subtitle = stringResource(R.string.settings_watch_forward_alarm_desc),
                icon = Icons.Default.Notifications,
                checked = watchConfig.notifyWatch,
                onCheckedChange = { enabled ->
                    repository.setNotifyWatch(enabled)
                }
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_watch_separate_alarm),
                subtitle = stringResource(R.string.settings_watch_separate_alarm_desc),
                icon = Icons.Default.NotificationsActive,
                checked = watchConfig.separateAlerts,
                onCheckedChange = { enabled ->
                    repository.setSeparateAlerts(enabled)
                }
            )
        }

        // Section 5: Manual Wi-Fi Mirror Fallback
        SettingsSection(title = stringResource(R.string.loc_manual_wifi_fallback)) {
            SettingsNavRow(
                title = stringResource(R.string.settings_group_mirror_title),
                subtitle = stringResource(R.string.loc_manual_wifi_desc),
                icon = Icons.Default.Sync,
                onClick = onOpenMirrorConfig
            )
        }
    }
}

@Composable
private fun WearDeviceCard(
    device: WearWatchDevice,
    onDirectSensorChanged: (Boolean) -> Unit,
    onEnterNumsChanged: (Boolean) -> Unit,
    onInitWatch: () -> Unit,
    onSync: () -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ScreenLayout.cardContainerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ScreenLayout.CardPadding)
        ) {
            // Header: Display name, status, ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.sensor_id, device.id),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Text(
                    text = stringResource(if (device.isConnected) R.string.loc_connected else R.string.loc_registered),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (device.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }

            if (device.mirrorIps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.loc_ip_address, device.mirrorIps.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Setting 1: Direct Sensor Connection
            SettingsSwitchRow(
                title = stringResource(R.string.settings_direct_sensor_title),
                subtitle = if (device.isDirectSensor) stringResource(R.string.loc_watch_direct_sensor_desc) else stringResource(R.string.loc_phone_sensor_stream_desc),
                icon = Icons.Default.Sensors,
                checked = device.isDirectSensor,
                onCheckedChange = onDirectSensorChanged,
                modifier = Modifier.padding(vertical = 0.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Setting 2: Enter Nums on Watch
            SettingsSwitchRow(
                title = stringResource(R.string.settings_enter_nums_title),
                subtitle = if (device.isEnterNumsOnWatch) stringResource(R.string.loc_enter_insulin_meals_watch) else stringResource(R.string.loc_enter_amounts_phone),
                icon = Icons.Default.WatchLater,
                checked = device.isEnterNumsOnWatch,
                onCheckedChange = onEnterNumsChanged,
                modifier = Modifier.padding(vertical = 0.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSync,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.sync), fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onInitWatch,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.loc_init_app), fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onResetDefaults,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.loc_defaults), fontSize = 12.sp)
                }
            }
        }
    }
}
