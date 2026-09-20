package tk.glucodata.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
        subtitle = stringResource(R.string.settings_cat_alarms),
        onNavigateBack = onNavigateBack
    ) {
        // LOW GLUCOSE ALARM
        SettingsCard(
            title = stringResource(R.string.settings_alarm_low),
            icon = Icons.Default.NotificationsActive,
            categorySubtitle = "CRITICAL ALERTS"
        ) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_alarm_low),
                subtitle = stringResource(R.string.settings_alarm_low_desc, unit.format(alarms.lowThreshold)),
                checked = alarms.lowAlarmEnabled,
                onCheckedChange = { repository.updateAlarms(alarms.copy(lowAlarmEnabled = it)) }
            )

            if (alarms.lowAlarmEnabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Low Alert Threshold",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
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
                        valueRange = 55f..95f
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stringResource(R.string.settings_alarm_snooze_duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
        }

        // HIGH GLUCOSE ALARM
        SettingsCard(
            title = stringResource(R.string.settings_alarm_high),
            icon = Icons.Default.NotificationsActive,
            categorySubtitle = "THRESHOLD ALERTS"
        ) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_alarm_high),
                subtitle = stringResource(R.string.settings_alarm_high_desc, unit.format(alarms.highThreshold)),
                checked = alarms.highAlarmEnabled,
                onCheckedChange = { repository.updateAlarms(alarms.copy(highAlarmEnabled = it)) }
            )

            if (alarms.highAlarmEnabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "High Alert Threshold",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
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
                        valueRange = 140f..250f
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stringResource(R.string.settings_alarm_snooze_duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
        }

        // SIGNAL LOSS & CHIMES
        SettingsCard(
            title = "Connection & Chimes",
            categorySubtitle = "CONNECTION MONITOR"
        ) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_alarm_loss),
                subtitle = stringResource(R.string.settings_alarm_loss_desc, alarms.lossWaitMinutes),
                checked = alarms.lossAlarmEnabled,
                onCheckedChange = { repository.updateAlarms(alarms.copy(lossAlarmEnabled = it)) }
            )

            if (alarms.lossAlarmEnabled) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_alarm_loss_wait),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            SettingsToggleRow(
                title = stringResource(R.string.settings_alarm_value_available),
                subtitle = stringResource(R.string.settings_alarm_value_available_desc),
                checked = alarms.valueAvailableNotification,
                onCheckedChange = { repository.updateAlarms(alarms.copy(valueAvailableNotification = it)) }
            )
        }

        // AUDIO OUTPUT STREAM
        SettingsCard(
            title = stringResource(R.string.settings_alarm_stream),
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            categorySubtitle = "AUDIO HARDWARE ROUTING"
        ) {
            val currentStreamLabel = when (alarms.soundStream) {
                AlarmSoundStream.ALARM -> stringResource(R.string.settings_stream_alarm)
                AlarmSoundStream.NOTIFICATION -> stringResource(R.string.settings_stream_notification)
                AlarmSoundStream.MEDIA -> stringResource(R.string.settings_stream_media)
            }

            Text(
                text = stringResource(R.string.settings_alarm_active_channel, currentStreamLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

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

        // AUDIO ROUTING INFO
        SettingsInfoCard(
            text = "Alarm Stream overrides phone silent and vibrate modes to ensure urgent alarms sound during sleep. Notification and Media streams follow system volume toggles.",
            icon = Icons.Default.Info
        )
    }
}
