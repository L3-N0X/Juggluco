package tk.glucodata.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.AlarmSoundStream

@Composable
fun AlarmsSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val unit by repository.unit.collectAsState()
    val alarms by repository.alarms.collectAsState()

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_group_alarms_title),
        onNavigateBack = onNavigateBack
    ) {
        // GLUCOSE ALERTS
        SettingsSection(title = "Glucose alerts") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_alarm_low),
                subtitle = stringResource(R.string.settings_alarm_low_desc, unit.format(alarms.lowThreshold)),
                icon = Icons.Default.NotificationsActive,
                checked = alarms.lowAlarmEnabled,
                onCheckedChange = { repository.updateAlarms(alarms.copy(lowAlarmEnabled = it)) }
            )

            if (alarms.lowAlarmEnabled) {
                SettingsDivider()

                // Low Threshold Slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIcon(icon = Icons.AutoMirrored.Filled.TrendingDown)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Low alert threshold",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = unit.format(alarms.lowThreshold),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Slider(
                        value = alarms.lowThreshold,
                        onValueChange = { repository.updateAlarms(alarms.copy(lowThreshold = it)) },
                        valueRange = 55f..95f,
                        modifier = Modifier.padding(start = 52.dp, top = 2.dp)
                    )
                }

                SettingsDivider()

                // Snooze selection
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIcon(icon = Icons.Default.Snooze)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.settings_alarm_snooze_duration),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 52.dp)
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

            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.settings_alarm_high),
                subtitle = stringResource(R.string.settings_alarm_high_desc, unit.format(alarms.highThreshold)),
                icon = Icons.Default.NotificationsActive,
                checked = alarms.highAlarmEnabled,
                onCheckedChange = { repository.updateAlarms(alarms.copy(highAlarmEnabled = it)) }
            )

            if (alarms.highAlarmEnabled) {
                SettingsDivider()

                // High Threshold Slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIcon(icon = Icons.AutoMirrored.Filled.TrendingUp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "High alert threshold",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = unit.format(alarms.highThreshold),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = alarms.highThreshold,
                        onValueChange = { repository.updateAlarms(alarms.copy(highThreshold = it)) },
                        valueRange = 140f..250f,
                        modifier = Modifier.padding(start = 52.dp, top = 2.dp)
                    )
                }

                SettingsDivider()

                // Snooze selection
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIcon(icon = Icons.Default.Snooze)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.settings_alarm_snooze_duration),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 52.dp)
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
        }

        // SIGNAL LOSS & CHIMES
        SettingsSection(title = "Connection & chimes") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_alarm_loss),
                subtitle = stringResource(R.string.settings_alarm_loss_desc, alarms.lossWaitMinutes),
                icon = Icons.Default.WifiOff,
                checked = alarms.lossAlarmEnabled,
                onCheckedChange = { repository.updateAlarms(alarms.copy(lossAlarmEnabled = it)) }
            )

            if (alarms.lossAlarmEnabled) {
                SettingsDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsIcon(icon = Icons.Default.Timer)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.settings_alarm_loss_wait),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 52.dp)
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

            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.settings_alarm_value_available),
                subtitle = stringResource(R.string.settings_alarm_value_available_desc),
                icon = Icons.Default.MusicNote,
                checked = alarms.valueAvailableNotification,
                onCheckedChange = { repository.updateAlarms(alarms.copy(valueAvailableNotification = it)) }
            )
        }

        // AUDIO OUTPUT STREAM
        SettingsSection(title = "Audio hardware routing") {
            val currentStreamLabel = when (alarms.soundStream) {
                AlarmSoundStream.ALARM -> stringResource(R.string.settings_stream_alarm)
                AlarmSoundStream.NOTIFICATION -> stringResource(R.string.settings_stream_notification)
                AlarmSoundStream.MEDIA -> stringResource(R.string.settings_stream_media)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsIcon(icon = Icons.AutoMirrored.Filled.VolumeUp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_alarm_stream),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_alarm_active_channel, currentStreamLabel),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 52.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            label = { Text(shortLabel, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }

        // AUDIO ROUTING INFO
        SettingsInfoCard(
            text = "Alarm Stream overrides phone silent and vibrate modes to ensure urgent alarms sound during sleep. Notification and Media streams follow system volume toggles.",
            icon = Icons.Default.Info
        )
    }
}
