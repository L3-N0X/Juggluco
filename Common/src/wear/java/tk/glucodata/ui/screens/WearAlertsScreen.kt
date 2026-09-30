package tk.glucodata.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TitleCard
import tk.glucodata.R
import tk.glucodata.alerts.AlertKind
import tk.glucodata.alerts.AlertOutput
import tk.glucodata.alerts.AlertPlayer
import tk.glucodata.alerts.AlertRule
import tk.glucodata.alerts.AlertStatus
import tk.glucodata.alerts.AlertStore
import tk.glucodata.alerts.AlertSync
import tk.glucodata.alerts.VibrationPattern
import tk.glucodata.alerts.rememberAlertIndicatorState
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.screens.settings.formatClock
import tk.glucodata.ui.screens.settings.formatDuration
import tk.glucodata.ui.screens.settings.triggerSummary

/**
 * Alerts on the watch. With "Use phone's alerts" on, the list mirrors the phone and
 * is read-only here; otherwise the watch keeps its own list with a compact editor.
 */
@Composable
fun WearAlertsScreen(
    repository: GlucoseRepository,
    onEditRule: (String) -> Unit
) {
    remember { AlertStore.ensureLoaded() }
    val unit by repository.unit.collectAsState()
    val legacyAlarms by repository.alarms.collectAsState()
    val rules by AlertStore.rules.collectAsState()
    val settings by AlertStore.settings.collectAsState()
    val indicator = rememberAlertIndicatorState()
    val haptic = LocalHapticFeedback.current
    val onLabel = stringResource(R.string.wear_ui_on)
    val offLabel = stringResource(R.string.wear_ui_off)
    val snoozeLabels = mapOf(
        30 to stringResource(R.string.wear_ui_minutes_short, 30),
        60 to stringResource(R.string.wear_ui_hours_short, 1),
        120 to stringResource(R.string.wear_ui_hours_short, 2)
    )
    fun tap(action: () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        action()
    }
    LaunchedEffect(settings.syncFromPhone) { AlertSync.requestConfig() }

    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { ListHeader { Text(stringResource(R.string.wear_ui_alerts)) } }

            if (indicator.status == AlertStatus.RINGING) {
                item {
                    Button(
                        onClick = { tap { AlertPlayer.dismiss() } },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        label = { Text(stringResource(R.string.wear_ui_dismiss)) },
                        secondaryLabel = {
                            Text(indicator.ringingName ?: stringResource(R.string.wear_ui_alert))
                        }
                    )
                }
            }
            item {
                AlarmSwitchRow(
                    title = stringResource(R.string.wear_ui_alerts_on_watch),
                    summary = when (indicator.status) {
                        AlertStatus.OFF -> offLabel
                        AlertStatus.SNOOZED ->
                            stringResource(R.string.wear_ui_snoozed_until, formatClock(indicator.snoozedUntil))
                        else -> stringResource(R.string.wear_ui_alerts_n_on, rules.count { it.enabled })
                    },
                    checked = settings.enabled,
                    onCheckedChange = { on -> tap { AlertStore.updateSettings { it.copy(enabled = on) } } }
                )
            }
            if (settings.enabled) {
                if (indicator.status == AlertStatus.SNOOZED) {
                    item {
                        FilledTonalButton(
                            onClick = { tap { AlertStore.snoozeAll(0) } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.wear_ui_resume_alerts)) }
                        )
                    }
                } else {
                    item {
                        Text(
                            text = stringResource(R.string.wear_ui_snooze_all),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            snoozeLabels.forEach { (minutes, label) ->
                                CompactButton(onClick = { tap { AlertStore.snoozeAll(minutes) } }) {
                                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            item { ListSubHeader { Text(stringResource(R.string.wear_ui_alert_list)) } }
            item {
                AlarmSwitchRow(
                    title = stringResource(R.string.wear_ui_use_phone_alerts),
                    summary = if (settings.syncFromPhone) {
                        stringResource(R.string.wear_ui_edit_on_phone)
                    } else {
                        stringResource(R.string.wear_ui_watch_keeps_own)
                    },
                    checked = settings.syncFromPhone,
                    onCheckedChange = { on -> tap { AlertStore.updateSettings { it.copy(syncFromPhone = on) } } }
                )
            }
            if (settings.syncFromPhone) {
                item {
                    FilledTonalButton(
                        onClick = { tap { AlertSync.requestConfig() } },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize)) },
                        label = { Text(stringResource(R.string.wear_ui_sync_now)) }
                    )
                }
            }
            rules.forEach { rule ->
                item {
                    TitleCard(
                        onClick = { if (!settings.syncFromPhone) onEditRule(rule.id) },
                        title = { Text(rule.name, maxLines = 1) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (rule.enabled) triggerSummary(rule, unit) else offLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (rule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (!settings.syncFromPhone) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            AlertKind.LOW to stringResource(R.string.wear_ui_kind_low),
                            AlertKind.HIGH to stringResource(R.string.wear_ui_kind_high),
                            AlertKind.SIGNAL_LOSS to stringResource(R.string.wear_ui_kind_loss)
                        ).forEach { (kind, label) ->
                            CompactButton(
                                onClick = {
                                    tap {
                                        val rule = AlertRule.template(kind)
                                        AlertStore.upsert(rule)
                                        onEditRule(rule.id)
                                    }
                                },
                                icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            ) {
                                Text(label, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            item { ListSubHeader { Text(stringResource(R.string.wear_ui_on_this_watch)) } }
            item {
                AlarmSwitchRow(
                    title = stringResource(R.string.wear_ui_sound),
                    summary = if (settings.soundOnThisDevice) {
                        stringResource(R.string.wear_ui_alerts_ring)
                    } else {
                        stringResource(R.string.wear_ui_vibrate_only)
                    },
                    checked = settings.soundOnThisDevice,
                    onCheckedChange = { on -> tap { AlertStore.updateSettings { it.copy(soundOnThisDevice = on) } } }
                )
            }
            item {
                AlarmSwitchRow(
                    title = stringResource(R.string.wear_ui_phone_alerts),
                    summary = if (settings.mirrorAlerts) {
                        stringResource(R.string.wear_ui_also_ring_here)
                    } else {
                        stringResource(R.string.wear_ui_phone_only)
                    },
                    checked = settings.mirrorAlerts,
                    onCheckedChange = { on -> tap { AlertStore.updateSettings { it.copy(mirrorAlerts = on) } } }
                )
            }
            item {
                AlarmSwitchRow(
                    title = stringResource(R.string.wear_ui_full_screen_in_use),
                    summary = if (settings.fullScreenOnActiveScreen) {
                        stringResource(R.string.wear_ui_cover_watch_screen)
                    } else {
                        stringResource(R.string.wear_ui_only_lock_screen)
                    },
                    checked = settings.fullScreenOnActiveScreen,
                    onCheckedChange = { on -> tap { AlertStore.updateSettings { it.copy(fullScreenOnActiveScreen = on) } } }
                )
            }
            item {
                AlarmSwitchRow(
                    title = stringResource(R.string.wear_ui_value_chime),
                    summary = if (legacyAlarms.valueAvailableNotification) onLabel else offLabel,
                    checked = legacyAlarms.valueAvailableNotification,
                    onCheckedChange = { on -> tap { repository.updateAlarms(legacyAlarms.copy(valueAvailableNotification = on)) } }
                )
            }
        }
    }
}

/** Compact editor for a watch-local alert: level, timing, sound and vibration. */
@Composable
fun WearAlertEditScreen(
    ruleId: String,
    unit: GlucoseUnit,
    onBack: () -> Unit
) {
    val rules by AlertStore.rules.collectAsState()
    val rule = rules.firstOrNull { it.id == ruleId }
    LaunchedEffect(rule == null) { if (rule == null) onBack() }
    if (rule == null) return
    val haptic = LocalHapticFeedback.current
    val onLabel = stringResource(R.string.wear_ui_on)
    val offLabel = stringResource(R.string.wear_ui_off)
    val minutesUnit = stringResource(R.string.wear_ui_minutes_unit)
    fun update(transform: (AlertRule) -> AlertRule) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        AlertStore.upsert(transform(rule))
    }

    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { ListHeader { Text(rule.name) } }
            item {
                AlarmSwitchRow(
                    title = stringResource(R.string.wear_ui_alert_on),
                    summary = triggerSummary(rule, unit),
                    checked = rule.enabled,
                    onCheckedChange = { on -> update { it.copy(enabled = on) } }
                )
            }

            when (rule.kind) {
                AlertKind.LOW, AlertKind.HIGH -> item {
                    val spec = if (rule.kind == AlertKind.LOW) mgSpec(40f, 150f, 1f, 5f, unit) else mgSpec(110f, 400f, 5f, 10f, unit)
                    ThresholdStepperContent(
                        current = toDisplay(rule.thresholdMgdl, unit),
                        spec = spec,
                        unit = unit,
                        haptic = haptic,
                        onChange = { value -> AlertStore.upsert(rule.copy(thresholdMgdl = unit.toMgDl(value))) },
                        caption = if (rule.kind == AlertKind.LOW) {
                            stringResource(R.string.wear_ui_at_or_below)
                        } else {
                            stringResource(R.string.wear_ui_at_or_above)
                        }
                    )
                }
                AlertKind.FALLING, AlertKind.RISING -> item {
                    PresetStepperContent(
                        label = stringResource(R.string.wear_ui_rate),
                        currentValue = (rule.rateMgdlPerMin * 10).toInt(),
                        presets = listOf(10, 15, 20, 25, 30, 40, 50),
                        haptic = haptic,
                        onChange = { tenths -> AlertStore.upsert(rule.copy(rateMgdlPerMin = tenths / 10f)) },
                        unitLabel = minutesUnit,
                        valueText = { tenths -> stringResource(R.string.wear_ui_per_min, unit.formatRate(tenths / 10f)) }
                    )
                }
                AlertKind.SIGNAL_LOSS -> item {
                    PresetStepperContent(
                        label = stringResource(R.string.wear_ui_no_reading_for),
                        currentValue = rule.lossMinutes,
                        presets = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120),
                        haptic = haptic,
                        onChange = { minutes -> AlertStore.upsert(rule.copy(lossMinutes = minutes)) },
                        unitLabel = minutesUnit,
                        valueText = { formatDuration(it * 60) }
                    )
                }
            }

            item {
                PresetStepperContent(
                    label = stringResource(R.string.wear_ui_repeat),
                    currentValue = rule.repeatMinutes,
                    presets = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120),
                    haptic = haptic,
                    onChange = { minutes -> AlertStore.upsert(rule.copy(repeatMinutes = minutes)) },
                    unitLabel = minutesUnit,
                    valueText = { if (it == 0) stringResource(R.string.loc_common_once) else formatDuration(it * 60) }
                )
            }
            item {
                PresetStepperContent(
                    label = stringResource(R.string.wear_ui_ring_for),
                    currentValue = rule.playDurationSec,
                    presets = listOf(0, 10, 15, 30, 60, 120, 300, 600),
                    haptic = haptic,
                    onChange = { seconds -> AlertStore.upsert(rule.copy(playDurationSec = seconds)) },
                    unitLabel = minutesUnit,
                    valueText = { if (it == 0) stringResource(R.string.wear_ui_until_dismissed) else formatDuration(it) }
                )
            }
            item {
                AlarmSwitchRow(
                    title = stringResource(R.string.wear_ui_sound),
                    summary = if (rule.output == AlertOutput.NONE) {
                        stringResource(R.string.off)
                    } else {
                        stringResource(R.string.wear_ui_alarm_volume, stringResource(rule.output.labelRes))
                    },
                    checked = rule.output != AlertOutput.NONE,
                    onCheckedChange = { on -> update { it.copy(output = if (on) AlertOutput.ALARM else AlertOutput.NONE) } }
                )
            }
            item {
                AlarmSwitchRow(
                    title = stringResource(R.string.wear_ui_vibrate),
                    summary = if (rule.vibrate) stringResource(rule.vibrationPattern.labelRes) else stringResource(R.string.loc_common_off),
                    checked = rule.vibrate,
                    onCheckedChange = { on -> update { it.copy(vibrate = on) } }
                )
            }
            if (rule.vibrate) {
                item {
                    FilledTonalButton(
                        onClick = {
                            // Cycles through the presets and plays each one once.
                            val presets = VibrationPattern.entries.filter { it != VibrationPattern.CUSTOM }
                            val next = presets[(presets.indexOf(rule.vibrationPattern) + 1) % presets.size]
                            val updated = rule.copy(vibrationPattern = next)
                            AlertStore.upsert(updated)
                            AlertPlayer.previewVibration(updated)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize)) },
                        label = { Text(stringResource(R.string.wear_ui_pattern)) },
                        secondaryLabel = { Text(stringResource(rule.vibrationPattern.labelRes)) }
                    )
                }
            }
            item {
                AlarmSwitchRow(
                    title = stringResource(R.string.wear_ui_override_dnd),
                    summary = if (rule.overrideDnd) onLabel else offLabel,
                    checked = rule.overrideDnd,
                    onCheckedChange = { on -> update { it.copy(overrideDnd = on) } }
                )
            }
            item {
                FilledTonalButton(
                    onClick = { AlertPlayer.test(rule) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize)) },
                    label = { Text(stringResource(R.string.test)) }
                )
            }
            item {
                Button(
                    onClick = {
                        AlertStore.delete(rule.id)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    icon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize)) },
                    label = { Text(stringResource(R.string.delete)) }
                )
            }
            item {
                Text(
                    text = stringResource(R.string.wear_ui_more_options_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun toDisplay(mgdl: Float, unit: GlucoseUnit): Float = unit.toDisplay(mgdl)
