package tk.glucodata.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tk.glucodata.Applic
import tk.glucodata.alerts.AlertKind
import tk.glucodata.alerts.AlertOutput
import tk.glucodata.alerts.AlertPlayer
import tk.glucodata.alerts.AlertRule
import tk.glucodata.alerts.AlertRuntime
import tk.glucodata.alerts.AlertSchedule
import tk.glucodata.alerts.AlertSounds
import tk.glucodata.alerts.AlertStore
import tk.glucodata.alerts.VibrationPattern
import tk.glucodata.ui.model.GlucoseUnit
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Everything about one alert. Changes are saved as they are made, like the other
 * settings screens, so there is no separate save step.
 */
@Composable
fun AlertEditorScreen(
    rule: AlertRule,
    runtime: AlertRuntime,
    unit: GlucoseUnit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val active by AlertPlayer.active.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var showSoundPicker by remember { mutableStateOf(false) }
    fun update(transform: (AlertRule) -> AlertRule) = AlertStore.upsert(transform(rule))

    val testing = active?.let { it.isTest && it.rule.id == rule.id } == true

    SettingsDetailScaffold(
        title = rule.name.ifBlank { "Alert" },
        onNavigateBack = onNavigateBack,
        actions = {
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete alert")
            }
        }
    ) {
        // --- General ---------------------------------------------------------------
        SettingsSection {
            NameRow(name = rule.name, onNameChange = { name -> update { it.copy(name = name) } })
            SettingsSwitchRow(
                title = "Alert on",
                subtitle = if (rule.enabled) triggerSummary(rule, unit) else "This alert never fires",
                icon = alertIcon(rule.kind),
                iconTint = alertColors(rule).first,
                iconBackground = alertColors(rule).second,
                checked = rule.enabled,
                onCheckedChange = { enabled -> update { it.copy(enabled = enabled) } }
            )
            if (runtime.snoozedUntil > System.currentTimeMillis()) {
                SettingsActionRow(
                    title = "Snoozed",
                    subtitle = "Until ${formatClock(runtime.snoozedUntil)}",
                    icon = Icons.Default.Snooze,
                    trailingContent = {
                        TextButton(onClick = { AlertStore.snoozeRule(rule.id, 0) }) { Text("Resume") }
                    }
                )
            }
        }

        // --- Trigger ---------------------------------------------------------------
        SettingsSection(title = "Trigger") {
            when (rule.kind) {
                AlertKind.LOW, AlertKind.HIGH -> {
                    LevelRow(
                        title = if (rule.kind == AlertKind.LOW) "Alert at or below" else "Alert at or above",
                        valueMgdl = rule.thresholdMgdl,
                        range = if (rule.kind == AlertKind.LOW) 40f..150f else 110f..400f,
                        unit = unit,
                        onChange = { value -> update { it.copy(thresholdMgdl = value) } }
                    )
                    ChoiceRow(
                        title = "Predictive",
                        subtitle = if (rule.forecastMinutes > 0) {
                            "Also alerts when the trend reaches the level within ${rule.forecastMinutes} min"
                        } else {
                            "Only the current value counts"
                        },
                        icon = Icons.AutoMirrored.Filled.TrendingFlat,
                        options = listOf(0, 10, 15, 20, 30, 45),
                        selected = rule.forecastMinutes,
                        label = { if (it == 0) "Off" else "$it min" },
                        onSelect = { minutes -> update { it.copy(forecastMinutes = minutes) } }
                    )
                    SettingsSwitchRow(
                        title = if (rule.kind == AlertKind.LOW) "Skip while rising" else "Skip while falling",
                        subtitle = "Stay quiet when glucose is already heading back",
                        icon = Icons.Default.Tune,
                        checked = rule.skipWhenRecovering,
                        onCheckedChange = { skip -> update { it.copy(skipWhenRecovering = skip) } }
                    )
                }
                AlertKind.FALLING, AlertKind.RISING -> {
                    RateRow(
                        rateMgdl = rule.rateMgdlPerMin,
                        unit = unit,
                        falling = rule.kind == AlertKind.FALLING,
                        onChange = { rate -> update { it.copy(rateMgdlPerMin = rate) } }
                    )
                    SettingsSwitchRow(
                        title = if (rule.kind == AlertKind.FALLING) "Only below a level" else "Only above a level",
                        subtitle = if (rule.thresholdMgdl > 0f) {
                            "Ignored while glucose is ${if (rule.kind == AlertKind.FALLING) "above" else "below"} ${unit.format(rule.thresholdMgdl)} ${unit.label}"
                        } else {
                            "Alerts at any glucose level"
                        },
                        icon = Icons.Default.Tune,
                        checked = rule.thresholdMgdl > 0f,
                        onCheckedChange = { on ->
                            update { it.copy(thresholdMgdl = if (on) (if (it.kind == AlertKind.FALLING) 150f else 180f) else 0f) }
                        }
                    )
                    if (rule.thresholdMgdl > 0f) {
                        LevelRow(
                            title = if (rule.kind == AlertKind.FALLING) "Below" else "Above",
                            valueMgdl = rule.thresholdMgdl,
                            range = 60f..300f,
                            unit = unit,
                            onChange = { value -> update { it.copy(thresholdMgdl = value) } }
                        )
                    }
                }
                AlertKind.SIGNAL_LOSS -> {
                    ChoiceRow(
                        title = "No reading for",
                        icon = Icons.Default.HourglassBottom,
                        options = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120),
                        selected = rule.lossMinutes,
                        label = { formatDuration(it * 60) },
                        onSelect = { minutes -> update { it.copy(lossMinutes = minutes) } }
                    )
                }
            }
        }

        // --- Schedule --------------------------------------------------------------
        ScheduleSection(schedule = rule.schedule, onChange = { schedule -> update { it.copy(schedule = schedule) } })

        // --- Sound -----------------------------------------------------------------
        SettingsSection(title = "Sound") {
            SettingsSegmentedRow(
                title = "Play on",
                subtitle = outputDescription(rule.output),
                icon = Icons.AutoMirrored.Filled.VolumeUp
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    val outputs = listOf(
                        AlertOutput.ALARM to "Alarm",
                        AlertOutput.NOTIFICATION to "Notification",
                        AlertOutput.MEDIA to "Media",
                        AlertOutput.NONE to "Off"
                    )
                    outputs.forEachIndexed { index, (output, label) ->
                        SegmentedButton(
                            selected = rule.output == output,
                            onClick = { update { it.copy(output = output) } },
                            shape = SegmentedButtonDefaults.itemShape(index, outputs.size),
                            label = { Text(label, maxLines = 1) }
                        )
                    }
                }
            }
            val soundOn = rule.output != AlertOutput.NONE
            SettingsActionRow(
                title = "Sound",
                subtitle = AlertSounds.label(context, rule),
                icon = Icons.Default.MusicNote,
                enabled = soundOn,
                onClick = { showSoundPicker = true }
            )
            SettingsSwitchRow(
                title = "Set volume",
                subtitle = if (rule.volumePercent >= 0) {
                    "Raises the ${outputName(rule.output)} volume to ${rule.volumePercent}% while ringing"
                } else {
                    "Uses the phone's current ${outputName(rule.output)} volume"
                },
                icon = Icons.Default.GraphicEq,
                enabled = soundOn,
                checked = rule.volumePercent >= 0,
                onCheckedChange = { on -> update { it.copy(volumePercent = if (on) 80 else -1) } }
            )
            if (rule.volumePercent >= 0) {
                SettingsSliderRow(
                    title = "Volume",
                    valueText = "${rule.volumePercent}%",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    value = rule.volumePercent.toFloat(),
                    onValueChange = { value -> update { it.copy(volumePercent = (value / 5).roundToInt() * 5) } },
                    valueRange = 5f..100f,
                    enabled = soundOn
                )
            }
            ChoiceRow(
                title = "Fade in",
                subtitle = if (rule.rampUpSec > 0) "Reaches full volume after ${formatDuration(rule.rampUpSec)}" else "Starts at full volume",
                icon = Icons.Default.Speed,
                enabled = soundOn,
                options = listOf(0, 5, 10, 20, 30, 60, 120),
                selected = rule.rampUpSec,
                label = { if (it == 0) "Off" else formatDuration(it) },
                onSelect = { seconds -> update { it.copy(rampUpSec = seconds) } }
            )
            ChoiceRow(
                title = "Sound starts after",
                subtitle = "Time from the alert until the sound starts",
                icon = Icons.Default.Timer,
                enabled = soundOn,
                options = listOf(0, 10, 30, 60, 120, 300),
                selected = rule.soundDelaySec,
                label = { if (it == 0) "At once" else formatDuration(it) },
                onSelect = { seconds -> update { it.copy(soundDelaySec = seconds) } }
            )
            SettingsSwitchRow(
                title = "Override Do Not Disturb",
                subtitle = "Rings and vibrates even in silent mode or Do Not Disturb" +
                    if (!AlertPlayer.hasDndAccess()) " (needs Do Not Disturb access)" else "",
                icon = Icons.Default.DoNotDisturbOn,
                checked = rule.overrideDnd,
                onCheckedChange = { on -> update { it.copy(overrideDnd = on) } }
            )
        }

        // --- Vibration -------------------------------------------------------------
        VibrationSection(rule = rule, onChange = { transform -> update(transform) })

        // --- Behaviour ------------------------------------------------------------
        SettingsSection(title = "Behavior") {
            ChoiceRow(
                title = "Repeat",
                subtitle = if (rule.repeatMinutes > 0) {
                    "Alerts again every ${formatDuration(rule.repeatMinutes * 60)} while the condition lasts"
                } else {
                    "Alerts once until the condition clears"
                },
                icon = Icons.Default.Repeat,
                options = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180),
                selected = rule.repeatMinutes,
                label = { if (it == 0) "Once" else formatDuration(it * 60) },
                onSelect = { minutes -> update { it.copy(repeatMinutes = minutes) } }
            )
            ChoiceRow(
                title = "Ring for",
                subtitle = if (rule.playDurationSec > 0) {
                    "Sound and vibration stop after ${formatDuration(rule.playDurationSec)}"
                } else {
                    "Keeps ringing until dismissed"
                },
                icon = Icons.Default.AccessTime,
                options = listOf(10, 15, 30, 60, 120, 300, 600, 0),
                selected = rule.playDurationSec,
                label = { if (it == 0) "Until dismissed" else formatDuration(it) },
                onSelect = { seconds -> update { it.copy(playDurationSec = seconds) } }
            )
            SettingsSwitchRow(
                title = "Stop when resolved",
                subtitle = "Stops ringing when a new reading no longer meets the condition",
                icon = Icons.Default.CheckCircle,
                checked = rule.stopWhenResolved,
                onCheckedChange = { on -> update { it.copy(stopWhenResolved = on) } }
            )
            SettingsSwitchRow(
                title = "Full-screen alert",
                subtitle = "Shows the value over the lock screen with large snooze and dismiss buttons",
                icon = Icons.Default.Fullscreen,
                checked = rule.fullScreen,
                onCheckedChange = { on -> update { it.copy(fullScreen = on) } }
            )
            SettingsSwitchRow(
                title = "Speak value",
                subtitle = "Reads the glucose value aloud before the sound",
                icon = Icons.Default.RecordVoiceOver,
                checked = rule.announce,
                onCheckedChange = { on -> update { it.copy(announce = on) } }
            )
            if (!Applic.isWearable) {
                SettingsSwitchRow(
                    title = "Flashlight",
                    subtitle = "Blinks the camera flash while ringing",
                    icon = Icons.Default.FlashlightOn,
                    checked = rule.flash,
                    onCheckedChange = { on -> update { it.copy(flash = on) } }
                )
            }
        }

        FilledTonalButton(
            onClick = { if (testing) AlertPlayer.dismiss() else AlertPlayer.test(rule) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
        ) {
            Icon(if (testing) Icons.Default.Stop else Icons.Default.NotificationsActive, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (testing) "Stop test" else "Test this alert")
        }
    }

    if (showSoundPicker) {
        SoundPickerDialog(
            rule = rule,
            onPick = { uri -> update { it.copy(soundUri = uri) } },
            onDismiss = { showSoundPicker = false }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${rule.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (AlertPlayer.active.value?.rule?.id == rule.id) AlertPlayer.dismiss()
                    onNavigateBack()
                    AlertStore.delete(rule.id)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

private fun outputName(output: AlertOutput): String = when (output) {
    AlertOutput.ALARM -> "alarm"
    AlertOutput.NOTIFICATION -> "notification"
    AlertOutput.MEDIA -> "media"
    AlertOutput.NONE -> "media"
}

private fun outputDescription(output: AlertOutput): String = when (output) {
    AlertOutput.ALARM -> "Alarm volume, plays in vibrate mode like an alarm clock"
    AlertOutput.NOTIFICATION -> "Notification volume, muted in silent and vibrate mode"
    AlertOutput.MEDIA -> "Media volume, plays through headphones when connected"
    AlertOutput.NONE -> "No sound, only vibration and the notification"
}

@Composable
private fun NameRow(name: String, onNameChange: (String) -> Unit) {
    // Local text state so typing is not interrupted by the store round trip.
    var text by remember { mutableStateOf(name) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon = Icons.Default.Edit)
        Spacer(Modifier.width(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                if (it.isNotBlank()) onNameChange(it.trim())
            },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Glucose level with a slider for coarse and -/+ buttons for exact adjustment. */
@Composable
private fun LevelRow(
    title: String,
    valueMgdl: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: GlucoseUnit,
    onChange: (Float) -> Unit
) {
    val stepMgdl = if (unit == GlucoseUnit.MMOL_L) unit.toMgDl(0.1f) else 1f
    fun snap(value: Float): Float = if (unit == GlucoseUnit.MMOL_L) {
        unit.toMgDl(((value * unit.factor) * 10).roundToInt() / 10f)
    } else {
        value.roundToInt().toFloat()
    }.coerceIn(range.start, range.endInclusive)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(icon = Icons.Default.Tune)
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            OutlinedIconButton(onClick = { onChange(snap(valueMgdl - stepMgdl)) }) {
                Text("−", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "${unit.format(valueMgdl)} ${unit.label}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            OutlinedIconButton(onClick = { onChange(snap(valueMgdl + stepMgdl)) }) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
        Slider(
            value = valueMgdl.coerceIn(range.start, range.endInclusive),
            onValueChange = { onChange(snap(it)) },
            valueRange = range,
            modifier = Modifier.padding(start = 52.dp, top = 2.dp)
        )
    }
}

@Composable
private fun RateRow(rateMgdl: Float, unit: GlucoseUnit, falling: Boolean, onChange: (Float) -> Unit) {
    SettingsSliderRow(
        title = if (falling) "Falling at least" else "Rising at least",
        valueText = "${unit.formatRate(rateMgdl)} ${unit.label}/min",
        icon = Icons.Default.Speed,
        value = rateMgdl,
        onValueChange = { value -> onChange((value * 4).roundToInt() / 4f) },
        valueRange = 1f..5f,
        steps = 15
    )
}

/** Row with a title and a horizontally scrolling set of mutually exclusive chips. */
@Composable
private fun <T> ChoiceRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    val choices = if (selected in options) options else (options + selected)
    SettingsSegmentedRow(title = title, subtitle = subtitle, icon = icon, enabled = enabled) {
        ChipRow {
            choices.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    enabled = enabled,
                    label = { Text(label(option)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSection(schedule: AlertSchedule, onChange: (AlertSchedule) -> Unit) {
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    SettingsSection(title = "Schedule") {
        SettingsSegmentedRow(
            title = "Active on",
            subtitle = scheduleSummary(schedule.copy(allDay = true)),
            icon = Icons.Default.CalendarMonth
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 68.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DayOfWeek.entries.forEach { day ->
                    val bit = 1 shl (day.value - 1)
                    FilterChip(
                        selected = schedule.days and bit != 0,
                        onClick = { onChange(schedule.copy(days = schedule.days xor bit)) },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
                    )
                }
            }
        }
        SettingsSwitchRow(
            title = "All day",
            subtitle = if (schedule.allDay) "Active around the clock" else {
                "Active ${formatMinuteOfDay(schedule.startMinute)}–${formatMinuteOfDay(schedule.endMinute)}" +
                    if (schedule.endMinute < schedule.startMinute) " (overnight)" else ""
            },
            icon = Icons.Default.Schedule,
            checked = schedule.allDay,
            onCheckedChange = { onChange(schedule.copy(allDay = it)) }
        )
        if (!schedule.allDay) {
            SettingsActionRow(
                title = "From",
                subtitle = formatMinuteOfDay(schedule.startMinute),
                icon = Icons.Default.AccessTime,
                onClick = { editingStart = true }
            )
            SettingsActionRow(
                title = "Until",
                subtitle = formatMinuteOfDay(schedule.endMinute),
                icon = Icons.Default.AccessTime,
                onClick = { editingStart = false }
            )
        }
    }

    editingStart?.let { isStart ->
        val minute = if (isStart) schedule.startMinute else schedule.endMinute
        val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60)
        AlertDialog(
            onDismissRequest = { editingStart = null },
            title = { Text(if (isStart) "Active from" else "Active until") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.hour * 60 + state.minute
                    onChange(if (isStart) schedule.copy(startMinute = picked) else schedule.copy(endMinute = picked))
                    editingStart = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { editingStart = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VibrationSection(rule: AlertRule, onChange: ((AlertRule) -> AlertRule) -> Unit) {
    SettingsSection(title = "Vibration") {
        SettingsSwitchRow(
            title = "Vibrate",
            subtitle = if (rule.vibrate) rule.vibrationPattern.label else "No vibration",
            icon = Icons.Default.Vibration,
            checked = rule.vibrate,
            onCheckedChange = { on -> onChange { it.copy(vibrate = on) } }
        )
        if (rule.vibrate) {
            SettingsSegmentedRow(
                title = "Pattern",
                icon = Icons.Default.GraphicEq
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 68.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VibrationPattern.entries.forEach { pattern ->
                        FilterChip(
                            selected = rule.vibrationPattern == pattern,
                            onClick = {
                                val updated = rule.copy(vibrationPattern = pattern)
                                onChange { it.copy(vibrationPattern = pattern) }
                                if (pattern != VibrationPattern.CUSTOM) AlertPlayer.previewVibration(updated)
                            },
                            label = { Text(pattern.label) }
                        )
                    }
                }
            }
            if (rule.vibrationPattern == VibrationPattern.CUSTOM) {
                CustomPatternRow(rule = rule, onChange = onChange)
            }
            SettingsSliderRow(
                title = "Strength",
                valueText = "${rule.vibrationIntensity}%",
                icon = Icons.Default.Vibration,
                value = rule.vibrationIntensity.toFloat(),
                onValueChange = { value -> onChange { it.copy(vibrationIntensity = ((value / 10).roundToInt() * 10).coerceIn(10, 100)) } },
                valueRange = 10f..100f,
                steps = 8
            )
            ChoiceRow(
                title = "Vibration starts after",
                icon = Icons.Default.Timer,
                options = listOf(0, 10, 30, 60, 120, 300),
                selected = rule.vibrationDelaySec,
                label = { if (it == 0) "At once" else formatDuration(it) },
                onSelect = { seconds -> onChange { it.copy(vibrationDelaySec = seconds) } }
            )
            SettingsActionRow(
                title = "Try pattern",
                subtitle = "Vibrates once with the current pattern and strength",
                icon = Icons.Default.PlayArrow,
                onClick = { AlertPlayer.previewVibration(rule) }
            )
        }
    }
}

@Composable
private fun CustomPatternRow(rule: AlertRule, onChange: ((AlertRule) -> AlertRule) -> Unit) {
    var text by remember { mutableStateOf(rule.customPattern) }
    val valid = VibrationPattern.parseCustom(text) != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 68.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                if (VibrationPattern.parseCustom(it) != null) onChange { rule -> rule.copy(customPattern = it) }
            },
            label = { Text("Pattern in ms: on, off, on, …") },
            placeholder = { Text("500, 200, 500, 1000") },
            isError = text.isNotEmpty() && !valid,
            supportingText = {
                Text(if (text.isNotEmpty() && !valid) "Use numbers up to 10000, separated by commas" else "Vibrate, pause, vibrate, … in milliseconds; the pattern loops")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// --- Sound picker ------------------------------------------------------------------------

/** Plays a sound on the alert's stream so choices can be compared. */
private object SoundPreview {
    private var player: MediaPlayer? = null

    fun play(context: Context, uri: Uri, output: AlertOutput) {
        stop()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            when (output) {
                                AlertOutput.ALARM -> AudioAttributes.USAGE_ALARM
                                AlertOutput.NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION
                                else -> AudioAttributes.USAGE_MEDIA
                            }
                        )
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                prepare()
                setOnCompletionListener { stop() }
                start()
            }
        }.getOrNull()
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
    }
}

@Composable
private fun SoundPickerDialog(rule: AlertRule, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) { onDispose { SoundPreview.stop() } }

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                onPick(uri.toString())
                onDismiss()
            }
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onPick(uri.toString())
            onDismiss()
        }
    }

    val currentUri = AlertSounds.resolve(context, rule).toString()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alert sound") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AlertSounds.builtIns.forEach { sound ->
                    val uri = AlertSounds.uriFor(context, sound)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                SoundPreview.play(context, uri, rule.output)
                                // The alert's own default is stored as "no sound set", so it follows the alert.
                                onPick(if (sound == AlertSounds.defaultFor(rule)) null else uri.toString())
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentUri == uri.toString(), onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (sound == AlertSounds.defaultFor(rule)) "${sound.label} (default)" else sound.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                TextButton(onClick = {
                    SoundPreview.stop()
                    ringtoneLauncher.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentUri))
                    })
                }) { Text("Phone sounds…") }
                TextButton(onClick = {
                    SoundPreview.stop()
                    fileLauncher.launch(arrayOf("audio/*"))
                }) { Text("Audio file…") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
