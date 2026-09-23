package tk.glucodata.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tk.glucodata.Applic
import tk.glucodata.R
import tk.glucodata.alerts.AlertKind
import tk.glucodata.alerts.AlertOutput
import tk.glucodata.alerts.AlertPlayer
import tk.glucodata.alerts.AlertRule
import tk.glucodata.alerts.AlertRuntime
import tk.glucodata.alerts.AlertStore
import tk.glucodata.alerts.AlertSync
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.theme.LocalClinicalColors
import java.text.DateFormat
import java.util.Date

/**
 * Alert overview: every configured alert as one row, glucose levels ordered from the
 * highest level to the lowest, followed by trend and signal alerts. Tapping a row
 * opens [AlertEditorScreen].
 */
@Composable
fun AlarmsSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    remember { AlertStore.ensureLoaded(context) }
    val unit by repository.unit.collectAsState()
    val legacyAlarms by repository.alarms.collectAsState()
    val rules by AlertStore.rules.collectAsState()
    val settings by AlertStore.settings.collectAsState()
    val runtime by AlertStore.runtime.collectAsState()
    val active by AlertPlayer.active.collectAsState()

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    // Snooze end times are compared against a clock that ticks while the screen is open.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }

    val editing = editingId?.let { id -> rules.firstOrNull { it.id == id } }
    if (editing != null) {
        AlertEditorScreen(
            rule = editing,
            runtime = runtime[editing.id] ?: AlertRuntime(),
            unit = unit,
            onNavigateBack = { editingId = null }
        )
        return
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_group_alarms_title),
        onNavigateBack = onNavigateBack,
        actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.loc_action_more))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.loc_alarm_restore_default)) },
                    onClick = {
                        showMenu = false
                        confirmReset = true
                    }
                )
            }
        }
    ) {
        val activeAlert = active
        if (activeAlert != null) {
            SettingsSection {
                SettingsActionRow(
                    title = if (activeAlert.ringing) stringResource(R.string.loc_alarm_is_ringing, activeAlert.rule.name) else activeAlert.rule.name,
                    subtitle = AlertPlayer.detail(activeAlert),
                    icon = Icons.Default.NotificationsActive,
                    iconTint = MaterialTheme.colorScheme.error,
                    iconBackground = MaterialTheme.colorScheme.errorContainer,
                    trailingContent = {
                        TextButton(onClick = { AlertPlayer.dismiss() }) { Text(stringResource(R.string.dismiss)) }
                    }
                )
            }
        }

        // General switches
        SettingsSection {
            val enabledCount = rules.count { it.enabled }
            SettingsSwitchRow(
                title = stringResource(R.string.loc_alerts),
                subtitle = if (settings.enabled) stringResource(R.string.loc_alerts_enabled_count, enabledCount, rules.size) else stringResource(R.string.loc_all_alerts_off),
                icon = if (settings.enabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    AlertStore.updateSettings { it.copy(enabled = enabled) }
                    if (!enabled) AlertPlayer.dismiss()
                }
            )
            SnoozeAllRow(snoozeUntil = settings.snoozeAllUntil, now = now, enabled = settings.enabled)
        }

        PermissionRows(context = context, rules = rules)

        val levelRules = rules.filter { it.kind.isGlucoseLevel }.sortedByDescending { it.thresholdMgdl }
        if (levelRules.isNotEmpty()) {
            SettingsSection(title = stringResource(R.string.loc_glucose_levels)) {
                levelRules.forEach { rule ->
                    AlertRuleRow(rule, runtime[rule.id], unit, now, onClick = { editingId = rule.id })
                }
            }
        }
        val trendRules = rules.filter { it.kind.isRate }
        if (trendRules.isNotEmpty()) {
            SettingsSection(title = stringResource(R.string.loc_trends)) {
                trendRules.forEach { rule ->
                    AlertRuleRow(rule, runtime[rule.id], unit, now, onClick = { editingId = rule.id })
                }
            }
        }
        val lossRules = rules.filter { it.kind == AlertKind.SIGNAL_LOSS }
        if (lossRules.isNotEmpty()) {
            SettingsSection(title = stringResource(R.string.loc_connection)) {
                lossRules.forEach { rule ->
                    AlertRuleRow(rule, runtime[rule.id], unit, now, onClick = { editingId = rule.id })
                }
            }
        }

        OutlinedButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.loc_add_alert))
        }

        SettingsSection(title = stringResource(R.string.loc_alert_buttons)) {
            SettingsSegmentedRow(
                title = stringResource(R.string.loc_snooze_choices),
                subtitle = stringResource(R.string.loc_snooze_choices_desc),
                icon = Icons.Default.Snooze
            ) {
                ChipRow {
                    listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180).forEach { minutes ->
                        val selected = minutes in settings.snoozeOptions
                        FilterChip(
                            selected = selected,
                            onClick = {
                                AlertStore.updateSettings { current ->
                                    val options = if (selected) {
                                        (current.snoozeOptions - minutes).ifEmpty { current.snoozeOptions }
                                    } else {
                                        (current.snoozeOptions + minutes).sorted().takeLast(3)
                                    }
                                    current.copy(snoozeOptions = options.sorted())
                                }
                            },
                            label = { Text(formatDuration(minutes * 60)) }
                        )
                    }
                }
            }
        }

        SettingsSection(title = stringResource(R.string.loc_watch_section)) {
            val watchConnected = AlertSync.hasWearPeer()
            SettingsActionRow(
                title = stringResource(R.string.loc_send_alerts_watch),
                subtitle = if (watchConnected) {
                    stringResource(R.string.loc_watch_uses_alerts)
                } else {
                    stringResource(R.string.loc_no_watch_connected)
                },
                icon = Icons.Default.Watch,
                enabled = watchConnected,
                onClick = { AlertSync.pushConfig() },
                trailingContent = if (watchConnected) {
                    { TextButton(onClick = { AlertSync.pushConfig() }) { Text(stringResource(R.string.loc_common_send)) } }
                } else null
            )
            SettingsSwitchRow(
                title = stringResource(R.string.loc_ring_watch_alerts),
                subtitle = stringResource(R.string.loc_ring_watch_alerts_desc),
                icon = Icons.Default.SyncAlt,
                checked = settings.mirrorAlerts,
                onCheckedChange = { on -> AlertStore.updateSettings { it.copy(mirrorAlerts = on) } }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.loc_sound_on_phone),
                subtitle = if (settings.soundOnThisDevice) stringResource(R.string.loc_alerts_play_here) else stringResource(R.string.loc_alerts_vibrate_here),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                checked = settings.soundOnThisDevice,
                onCheckedChange = { on -> AlertStore.updateSettings { it.copy(soundOnThisDevice = on) } }
            )
        }

        SettingsSection(title = stringResource(R.string.loc_other_sounds)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_alarm_value_available),
                subtitle = stringResource(R.string.settings_alarm_value_available_desc),
                icon = Icons.Default.MusicNote,
                checked = legacyAlarms.valueAvailableNotification,
                onCheckedChange = { repository.updateAlarms(legacyAlarms.copy(valueAvailableNotification = it)) }
            )
        }
    }

    if (showAddDialog) {
        AddAlertDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { kind ->
                showAddDialog = false
                val rule = AlertRule.template(kind)
                AlertStore.upsert(rule)
                editingId = rule.id
            }
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.loc_restore_alerts_title)) },
            text = { Text(stringResource(R.string.loc_restore_alerts_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    AlertPlayer.dismiss()
                    AlertStore.resetToDefaults()
                }) { Text(stringResource(R.string.loc_common_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun SnoozeAllRow(snoozeUntil: Long, now: Long, enabled: Boolean) {
    var showChoices by remember { mutableStateOf(false) }
    val snoozed = snoozeUntil > now
    SettingsActionRow(
        title = if (snoozed) stringResource(R.string.loc_all_alerts_snoozed) else stringResource(R.string.loc_snooze_all_alerts),
        subtitle = if (snoozed) stringResource(R.string.loc_until_time, formatClock(snoozeUntil)) else stringResource(R.string.loc_pause_all_alerts),
        icon = Icons.Default.Snooze,
        enabled = enabled,
        onClick = { if (!snoozed) showChoices = true },
        trailingContent = if (snoozed) {
            { TextButton(onClick = { AlertStore.snoozeAll(0) }) { Text(stringResource(R.string.loc_common_resume)) } }
        } else null
    )
    if (showChoices) {
        AlertDialog(
            onDismissRequest = { showChoices = false },
            title = { Text(stringResource(R.string.loc_snooze_all_alerts)) },
            text = {
                Column {
                    listOf(30, 60, 120, 240, 480).forEach { minutes ->
                        Text(
                            text = stringResource(R.string.loc_snooze_for_duration, formatDuration(minutes * 60)),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    AlertStore.snoozeAll(minutes)
                                    AlertPlayer.snooze(minutes)
                                    showChoices = false
                                }
                                .padding(vertical = 14.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChoices = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** Rows asking for the system permissions the configured alerts depend on. */
@Composable
private fun PermissionRows(context: Context, rules: List<AlertRule>) {
    // Re-checked whenever the screen recomposes, e.g. after returning from system settings.
    val needsDnd = rules.any { it.enabled && it.overrideDnd } && !AlertPlayer.hasDndAccess()
    val needsFullScreen = rules.any { it.enabled && it.fullScreen } && !AlertPlayer.canUseFullScreen()
    val notificationsOff = !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    if (!needsDnd && !needsFullScreen && !notificationsOff) return

    SettingsSection(title = stringResource(R.string.loc_needs_attention)) {
        if (notificationsOff) {
            SettingsActionRow(
                title = stringResource(R.string.loc_notifications_blocked),
                subtitle = stringResource(R.string.loc_notifications_blocked_desc),
                icon = Icons.Default.NotificationsOff,
                iconTint = MaterialTheme.colorScheme.error,
                iconBackground = MaterialTheme.colorScheme.errorContainer,
                onClick = { openAppNotificationSettings(context) }
            )
        }
        if (needsDnd) {
            SettingsActionRow(
                title = stringResource(R.string.loc_allow_dnd_access),
                subtitle = stringResource(R.string.loc_allow_dnd_access_desc),
                icon = Icons.Default.DoNotDisturbOn,
                iconTint = MaterialTheme.colorScheme.error,
                iconBackground = MaterialTheme.colorScheme.errorContainer,
                onClick = { launchSettings(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }
            )
        }
        if (needsFullScreen) {
            SettingsActionRow(
                title = stringResource(R.string.loc_allow_full_screen),
                subtitle = stringResource(R.string.loc_allow_full_screen_desc),
                icon = Icons.Default.Fullscreen,
                iconTint = MaterialTheme.colorScheme.error,
                iconBackground = MaterialTheme.colorScheme.errorContainer,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 34) {
                        launchSettings(
                            context,
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}")
                        )
                    }
                }
            )
        }
    }
}

private fun launchSettings(context: Context, action: String, data: Uri? = null) {
    try {
        context.startActivity(Intent(action).apply {
            if (data != null) setData(data)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: ActivityNotFoundException) {
        launchSettings(context, Settings.ACTION_SETTINGS)
    }
}

private fun openAppNotificationSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
    launchSettings(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}

@Composable
private fun AlertRuleRow(
    rule: AlertRule,
    runtime: AlertRuntime?,
    unit: GlucoseUnit,
    now: Long,
    onClick: () -> Unit
) {
    val (tint, background) = alertColors(rule)
    val context = LocalContext.current
    val snoozedUntil = runtime?.snoozedUntil ?: 0L
    val subtitle = buildString {
        append(triggerSummary(context, rule, unit))
        append(" · ")
        append(deliverySummary(rule))
        if (!rule.schedule.isAlways) append(" · ").append(scheduleSummary(rule.schedule))
        if (snoozedUntil > now) {
            append(" · ")
            append(context.getString(R.string.loc_until_time, formatClock(snoozedUntil)))
        }
    }
    SettingsNavRow(
        title = rule.name,
        subtitle = subtitle,
        icon = alertIcon(rule.kind),
        iconTint = tint,
        iconBackground = background,
        onClick = onClick,
        checked = rule.enabled,
        onCheckedChange = { AlertStore.setEnabled(rule.id, it) }
    )
}

@Composable
private fun AddAlertDialog(onDismiss: () -> Unit, onAdd: (AlertKind) -> Unit) {
    val choices = listOf(
        AlertKind.LOW to (stringResource(R.string.loc_alert_choice_low) to stringResource(R.string.loc_alert_choice_low_desc)),
        AlertKind.HIGH to (stringResource(R.string.loc_alert_choice_high) to stringResource(R.string.loc_alert_choice_high_desc)),
        AlertKind.FALLING to (stringResource(R.string.loc_alert_choice_falling) to stringResource(R.string.loc_alert_choice_falling_desc)),
        AlertKind.RISING to (stringResource(R.string.loc_alert_choice_rising) to stringResource(R.string.loc_alert_choice_rising_desc)),
        AlertKind.SIGNAL_LOSS to (stringResource(R.string.loc_alert_choice_loss) to stringResource(R.string.loc_alert_choice_loss_desc))
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.loc_add_alert)) },
        text = {
            Column {
                choices.forEach { (kind, labels) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAdd(kind) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val (tint, background) = alertColors(AlertRule.template(kind))
                        SettingsIcon(icon = alertIcon(kind), tint = tint, backgroundColor = background)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(labels.first, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                labels.second,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// --- Shared helpers for the alert screens ---------------------------------------------

internal fun alertIcon(kind: AlertKind): ImageVector = when (kind) {
    AlertKind.LOW -> Icons.AutoMirrored.Filled.TrendingDown
    AlertKind.HIGH -> Icons.AutoMirrored.Filled.TrendingUp
    AlertKind.FALLING -> Icons.Default.KeyboardDoubleArrowDown
    AlertKind.RISING -> Icons.Default.KeyboardDoubleArrowUp
    AlertKind.SIGNAL_LOSS -> Icons.Default.WifiOff
}

/** Icon tint and background for an alert, following the glucose range colors. */
@Composable
internal fun alertColors(rule: AlertRule): Pair<Color, Color> {
    val clinical = LocalClinicalColors.current
    return when (rule.kind) {
        AlertKind.LOW -> if (rule.thresholdMgdl < 60f && rule.forecastMinutes == 0) {
            clinical.veryLow to clinical.veryLowContainer
        } else {
            clinical.low to clinical.lowContainer
        }
        AlertKind.FALLING -> clinical.low to clinical.lowContainer
        AlertKind.HIGH -> if (rule.thresholdMgdl >= 240f && rule.forecastMinutes == 0) {
            clinical.veryHigh to clinical.veryHighContainer
        } else {
            clinical.high to clinical.highContainer
        }
        AlertKind.RISING -> clinical.high to clinical.highContainer
        AlertKind.SIGNAL_LOSS -> MaterialTheme.colorScheme.onSurfaceVariant to
            MaterialTheme.colorScheme.surfaceVariant
    }
}

internal fun triggerSummary(rule: AlertRule, unit: GlucoseUnit): String = triggerSummary(Applic.getContext(), rule, unit)

internal fun triggerSummary(context: Context, rule: AlertRule, unit: GlucoseUnit): String {
    val level = context.getString(R.string.log_glucose_value, unit.format(rule.thresholdMgdl), context.getString(unit.labelRes))
    val forecast = if (rule.forecastMinutes > 0) {
        context.getString(R.string.alert_forecast, rule.forecastMinutes)
    } else {
        ""
    }
    return when (rule.kind) {
        AlertKind.LOW -> context.getString(R.string.alert_trigger_below, level) + forecast
        AlertKind.HIGH -> context.getString(R.string.alert_trigger_above, level) + forecast
        AlertKind.FALLING -> context.getString(
            R.string.alert_trigger_falling,
            unit.formatRate(rule.rateMgdlPerMin),
            context.getString(unit.labelRes)
        ) + if (rule.thresholdMgdl > 0f) context.getString(R.string.alert_trigger_under, level) else ""
        AlertKind.RISING -> context.getString(
            R.string.alert_trigger_rising,
            unit.formatRate(rule.rateMgdlPerMin),
            context.getString(unit.labelRes)
        ) + if (rule.thresholdMgdl > 0f) context.getString(R.string.alert_trigger_over, level) else ""
        AlertKind.SIGNAL_LOSS -> context.getString(R.string.alert_trigger_signal_loss, formatDuration(rule.lossMinutes * 60))
    }
}

internal fun deliverySummary(rule: AlertRule): String {
    val context = Applic.getContext()
    val sound = when (rule.output) {
        AlertOutput.ALARM -> context.getString(R.string.loc_alarm_stream)
        AlertOutput.NOTIFICATION -> context.getString(R.string.loc_alarm_notification_stream)
        AlertOutput.MEDIA -> context.getString(R.string.loc_alarm_media_stream)
        AlertOutput.NONE -> context.getString(if (rule.vibrate) R.string.loc_delivery_vibrate_only else R.string.loc_delivery_silent)
    }
    return if (rule.repeatMinutes > 0) {
        context.getString(R.string.loc_delivery_repeat, sound, formatDuration(rule.repeatMinutes * 60))
    } else {
        context.getString(R.string.loc_delivery_once, sound)
    }
}

internal fun scheduleSummary(schedule: tk.glucodata.alerts.AlertSchedule): String {
    val context = Applic.getContext()
    val days = when (schedule.days) {
        tk.glucodata.alerts.AlertSchedule.ALL_DAYS -> context.getString(R.string.loc_schedule_every_day)
        tk.glucodata.alerts.AlertSchedule.WEEKDAYS -> context.getString(R.string.loc_schedule_weekdays)
        tk.glucodata.alerts.AlertSchedule.WEEKEND -> context.getString(R.string.loc_schedule_weekends)
        0 -> context.getString(R.string.loc_schedule_never)
        else -> java.time.DayOfWeek.entries
            .filter { schedule.isActiveOn(it) }
            .joinToString(" ") { it.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }
    }
    if (schedule.allDay) return days
    return "$days ${formatMinuteOfDay(schedule.startMinute)}–${formatMinuteOfDay(schedule.endMinute)}"
}

internal fun formatMinuteOfDay(minute: Int): String {
    val locale = java.util.Locale.getDefault()
    val formatter = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(locale)
    return java.time.LocalTime.of(minute / 60, minute % 60).format(formatter)
}

internal fun formatClock(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT, java.util.Locale.getDefault()).format(Date(millis))

internal fun formatDuration(seconds: Int): String {
    val context = Applic.getContext()
    return when {
        seconds <= 0 -> context.getString(R.string.loc_duration_zero)
        seconds < 60 -> context.getString(R.string.loc_duration_seconds, seconds)
        seconds % 3600 == 0 -> context.getString(R.string.loc_duration_hours, seconds / 3600)
        seconds > 3600 -> context.getString(R.string.loc_duration_hours_minutes, seconds / 3600, (seconds % 3600) / 60)
        seconds % 60 == 0 -> context.getString(R.string.loc_duration_minutes, seconds / 60)
        else -> context.getString(R.string.loc_duration_minutes_seconds, seconds / 60, seconds % 60)
    }
}

/** Horizontally scrolling chip row aligned with the text of a settings row. */
@Composable
internal fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 68.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}
