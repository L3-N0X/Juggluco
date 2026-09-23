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
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Restore default alerts") },
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
                    title = if (activeAlert.ringing) "${activeAlert.rule.name} is ringing" else activeAlert.rule.name,
                    subtitle = AlertPlayer.detail(activeAlert),
                    icon = Icons.Default.NotificationsActive,
                    iconTint = MaterialTheme.colorScheme.error,
                    iconBackground = MaterialTheme.colorScheme.errorContainer,
                    trailingContent = {
                        TextButton(onClick = { AlertPlayer.dismiss() }) { Text("Dismiss") }
                    }
                )
            }
        }

        // General switches
        SettingsSection {
            val enabledCount = rules.count { it.enabled }
            SettingsSwitchRow(
                title = "Alerts",
                subtitle = if (settings.enabled) "$enabledCount of ${rules.size} alerts on" else "All alerts are off",
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
            SettingsSection(title = "Glucose levels") {
                levelRules.forEach { rule ->
                    AlertRuleRow(rule, runtime[rule.id], unit, now, onClick = { editingId = rule.id })
                }
            }
        }
        val trendRules = rules.filter { it.kind.isRate }
        if (trendRules.isNotEmpty()) {
            SettingsSection(title = "Trends") {
                trendRules.forEach { rule ->
                    AlertRuleRow(rule, runtime[rule.id], unit, now, onClick = { editingId = rule.id })
                }
            }
        }
        val lossRules = rules.filter { it.kind == AlertKind.SIGNAL_LOSS }
        if (lossRules.isNotEmpty()) {
            SettingsSection(title = "Connection") {
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
            Text("Add alert")
        }

        SettingsSection(title = "Alert buttons") {
            SettingsSegmentedRow(
                title = "Snooze choices",
                subtitle = "Offered on the notification and the full-screen alert (up to 3)",
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

        SettingsSection(title = "Watch") {
            val watchConnected = AlertSync.hasWearPeer()
            SettingsActionRow(
                title = "Send alerts to watch",
                subtitle = if (watchConnected) {
                    "The watch app uses these alerts unless it keeps its own. Changes are sent automatically."
                } else {
                    "No watch with Juggluco is connected right now"
                },
                icon = Icons.Default.Watch,
                enabled = watchConnected,
                onClick = { AlertSync.pushConfig() },
                trailingContent = if (watchConnected) {
                    { TextButton(onClick = { AlertSync.pushConfig() }) { Text("Send") } }
                } else null
            )
            SettingsSwitchRow(
                title = "Ring for watch alerts",
                subtitle = "Rings here when the watch raises an alert. Dismissing or snoozing on either device stops both.",
                icon = Icons.Default.SyncAlt,
                checked = settings.mirrorAlerts,
                onCheckedChange = { on -> AlertStore.updateSettings { it.copy(mirrorAlerts = on) } }
            )
            SettingsSwitchRow(
                title = "Sound on this phone",
                subtitle = if (settings.soundOnThisDevice) "Alerts play their sound here" else "Alerts only vibrate on this phone",
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                checked = settings.soundOnThisDevice,
                onCheckedChange = { on -> AlertStore.updateSettings { it.copy(soundOnThisDevice = on) } }
            )
        }

        SettingsSection(title = "Other sounds") {
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
            title = { Text("Restore default alerts?") },
            text = { Text("Your alerts are replaced by the starter set. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    AlertPlayer.dismiss()
                    AlertStore.resetToDefaults()
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SnoozeAllRow(snoozeUntil: Long, now: Long, enabled: Boolean) {
    var showChoices by remember { mutableStateOf(false) }
    val snoozed = snoozeUntil > now
    SettingsActionRow(
        title = if (snoozed) "All alerts snoozed" else "Snooze all alerts",
        subtitle = if (snoozed) "Until ${formatClock(snoozeUntil)}" else "Pause every alert for a while",
        icon = Icons.Default.Snooze,
        enabled = enabled,
        onClick = { if (!snoozed) showChoices = true },
        trailingContent = if (snoozed) {
            { TextButton(onClick = { AlertStore.snoozeAll(0) }) { Text("Resume") } }
        } else null
    )
    if (showChoices) {
        AlertDialog(
            onDismissRequest = { showChoices = false },
            title = { Text("Snooze all alerts") },
            text = {
                Column {
                    listOf(30, 60, 120, 240, 480).forEach { minutes ->
                        Text(
                            text = "For ${formatDuration(minutes * 60)}",
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
                TextButton(onClick = { showChoices = false }) { Text("Cancel") }
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

    SettingsSection(title = "Needs your attention") {
        if (notificationsOff) {
            SettingsActionRow(
                title = "Notifications are blocked",
                subtitle = "Alerts cannot show until notifications are allowed",
                icon = Icons.Default.NotificationsOff,
                iconTint = MaterialTheme.colorScheme.error,
                iconBackground = MaterialTheme.colorScheme.errorContainer,
                onClick = { openAppNotificationSettings(context) }
            )
        }
        if (needsDnd) {
            SettingsActionRow(
                title = "Allow Do Not Disturb access",
                subtitle = "Needed for alerts that ring through Do Not Disturb and silent mode",
                icon = Icons.Default.DoNotDisturbOn,
                iconTint = MaterialTheme.colorScheme.error,
                iconBackground = MaterialTheme.colorScheme.errorContainer,
                onClick = { launchSettings(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }
            )
        }
        if (needsFullScreen) {
            SettingsActionRow(
                title = "Allow full-screen alerts",
                subtitle = "Needed to show alerts over the lock screen",
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
    val snoozedUntil = runtime?.snoozedUntil ?: 0L
    val subtitle = buildString {
        append(triggerSummary(rule, unit))
        append(" · ")
        append(deliverySummary(rule))
        if (!rule.schedule.isAlways) append(" · ").append(scheduleSummary(rule.schedule))
        if (snoozedUntil > now) append(" · snoozed until ").append(formatClock(snoozedUntil))
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
        AlertKind.LOW to ("Low glucose" to "When glucose drops below a level"),
        AlertKind.HIGH to ("High glucose" to "When glucose rises above a level"),
        AlertKind.FALLING to ("Falling fast" to "When glucose drops quickly"),
        AlertKind.RISING to ("Rising fast" to "When glucose rises quickly"),
        AlertKind.SIGNAL_LOSS to ("Signal loss" to "When no reading arrives for a while")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add alert") },
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
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

internal fun triggerSummary(rule: AlertRule, unit: GlucoseUnit): String {
    val level = "${unit.format(rule.thresholdMgdl)} ${unit.label}"
    val forecast = if (rule.forecastMinutes > 0) " in ${rule.forecastMinutes} min" else ""
    return when (rule.kind) {
        AlertKind.LOW -> "Below $level$forecast"
        AlertKind.HIGH -> "Above $level$forecast"
        AlertKind.FALLING -> "Falling ${unit.formatRate(rule.rateMgdlPerMin)}/min" +
            if (rule.thresholdMgdl > 0f) " under $level" else ""
        AlertKind.RISING -> "Rising ${unit.formatRate(rule.rateMgdlPerMin)}/min" +
            if (rule.thresholdMgdl > 0f) " over $level" else ""
        AlertKind.SIGNAL_LOSS -> "No reading for ${formatDuration(rule.lossMinutes * 60)}"
    }
}

internal fun deliverySummary(rule: AlertRule): String {
    val sound = when (rule.output) {
        AlertOutput.ALARM -> "Alarm"
        AlertOutput.NOTIFICATION -> "Notification"
        AlertOutput.MEDIA -> "Media"
        AlertOutput.NONE -> if (rule.vibrate) "Vibrate only" else "Silent"
    }
    val repeat = if (rule.repeatMinutes > 0) "every ${formatDuration(rule.repeatMinutes * 60)}" else "once"
    return "$sound · $repeat"
}

internal fun scheduleSummary(schedule: tk.glucodata.alerts.AlertSchedule): String {
    val days = when (schedule.days) {
        tk.glucodata.alerts.AlertSchedule.ALL_DAYS -> "Every day"
        tk.glucodata.alerts.AlertSchedule.WEEKDAYS -> "Weekdays"
        tk.glucodata.alerts.AlertSchedule.WEEKEND -> "Weekends"
        0 -> "Never"
        else -> java.time.DayOfWeek.entries
            .filter { schedule.isActiveOn(it) }
            .joinToString(" ") { it.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }
    }
    if (schedule.allDay) return days
    return "$days ${formatMinuteOfDay(schedule.startMinute)}–${formatMinuteOfDay(schedule.endMinute)}"
}

internal fun formatMinuteOfDay(minute: Int): String {
    val time = java.time.LocalTime.of(minute / 60, minute % 60)
    return time.format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
}

internal fun formatClock(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

internal fun formatDuration(seconds: Int): String = when {
    seconds <= 0 -> "0 s"
    seconds < 60 -> "$seconds s"
    seconds % 3600 == 0 -> "${seconds / 3600} h"
    seconds > 3600 -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} s"
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
