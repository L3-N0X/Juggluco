package tk.glucodata.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TitleCard
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.AlarmBehavior
import tk.glucodata.ui.model.AlarmConfig
import tk.glucodata.ui.model.AlarmSoundStream
import tk.glucodata.ui.model.DeltaCalculation
import tk.glucodata.ui.model.GlucoseUnit
import kotlin.math.roundToInt

/** Alarm kinds with an adjustable glucose threshold. Ranges defined in mg/dL. */
private enum class AlarmKind {
    LOW, URGENT_LOW, HIGH, VERY_HIGH, PRE_LOW, PRE_HIGH
}

private data class ThresholdSpec(
    val min: Float,
    val max: Float,
    val smallStep: Float,
    val largeStep: Float,
    val decimals: Int
)

/** Native thresholds are stored per display unit, so specs are converted for mmol/L. */
private fun mgSpec(min: Float, max: Float, smallStep: Float, largeStep: Float, unit: GlucoseUnit): ThresholdSpec {
    return if (unit == GlucoseUnit.MMOL_L) {
        ThresholdSpec(
            min = round1(min / 18.0182f),
            max = round1(max / 18.0182f),
            smallStep = round1(smallStep / 18.0182f).coerceAtLeast(0.1f),
            largeStep = round1(largeStep / 18.0182f),
            decimals = 1
        )
    } else {
        ThresholdSpec(min, max, smallStep, largeStep, 0)
    }
}

private fun specFor(kind: AlarmKind, unit: GlucoseUnit): ThresholdSpec {
    return when (kind) {
        AlarmKind.LOW -> mgSpec(55f, 95f, 1f, 5f, unit)
        AlarmKind.URGENT_LOW -> mgSpec(40f, 70f, 1f, 5f, unit)
        AlarmKind.HIGH -> mgSpec(140f, 250f, 5f, 10f, unit)
        AlarmKind.VERY_HIGH -> mgSpec(200f, 350f, 5f, 10f, unit)
        AlarmKind.PRE_LOW -> mgSpec(60f, 110f, 1f, 5f, unit)
        AlarmKind.PRE_HIGH -> mgSpec(130f, 220f, 5f, 10f, unit)
    }
}

private fun round1(value: Float): Float = (value * 10).roundToInt() / 10f

private fun adjustThreshold(current: Float, delta: Float, spec: ThresholdSpec): Float {
    val stepped = (current + delta).coerceIn(spec.min, spec.max)
    return if (spec.decimals == 1) round1(stepped).coerceIn(spec.min, spec.max)
    else stepped.roundToInt().toFloat().coerceIn(spec.min, spec.max)
}

private fun formatThreshold(value: Float, spec: ThresholdSpec, unit: GlucoseUnit): String {
    val number = if (spec.decimals == 1) {
        String.format(java.util.Locale.US, "%.1f", value)
    } else {
        value.roundToInt().toString()
    }
    return "$number ${unit.label}"
}

private fun formatStep(step: Float, spec: ThresholdSpec): String {
    return if (spec.decimals == 1) {
        String.format(java.util.Locale.US, "%.1f", step).trimEnd('0').trimEnd('.')
            .let { if (it.startsWith(".")) "0$it" else it }
    } else {
        step.roundToInt().toString()
    }
}

private val lowSnoozePresets = listOf(5, 10, 15, 20, 30, 45, 60)
private val highSnoozePresets = listOf(15, 30, 45, 60, 90, 120)
private val genericSnoozePresets = listOf(5, 10, 15, 20, 30, 45, 60)
private val lossWaitPresets = listOf(10, 15, 20, 30, 45, 60)
private val alarmDurationPresets = listOf(10, 15, 30, 60, 120, 180, 300)

private fun stepPreset(current: Int, presets: List<Int>, direction: Int): Int {
    val index = presets.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: (presets.size - 1)
    return presets[(index + direction).coerceIn(0, presets.size - 1)]
}

@Composable
private fun AlarmSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(title) },
        secondaryLabel = { Text(summary) }
    )
}

private fun ScalingLazyListScope.wearAlarmToggle(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    item {
        AlarmSwitchRow(title, summary, checked, onCheckedChange)
    }
}

@Composable
private fun ThresholdStepperContent(
    current: Float,
    spec: ThresholdSpec,
    unit: GlucoseUnit,
    haptic: HapticFeedback,
    onChange: (Float) -> Unit,
    caption: String? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatThreshold(current, spec, unit),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(adjustThreshold(current, -spec.largeStep, spec))
            }) {
                Text(text = "-${formatStep(spec.largeStep, spec)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            CompactButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(adjustThreshold(current, -spec.smallStep, spec))
            }) {
                Text(text = "-${formatStep(spec.smallStep, spec)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            CompactButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(adjustThreshold(current, spec.smallStep, spec))
            }) {
                Text(text = "+${formatStep(spec.smallStep, spec)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            CompactButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onChange(adjustThreshold(current, spec.largeStep, spec))
            }) {
                Text(text = "+${formatStep(spec.largeStep, spec)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun ScalingLazyListScope.wearThresholdStepper(
    current: Float,
    spec: ThresholdSpec,
    unit: GlucoseUnit,
    haptic: HapticFeedback,
    caption: String? = null,
    onChange: (Float) -> Unit
) {
    item {
        ThresholdStepperContent(current, spec, unit, haptic, onChange, caption)
    }
}

@Composable
private fun PresetStepperContent(
    label: String,
    currentValue: Int,
    presets: List<Int>,
    haptic: HapticFeedback,
    onChange: (Int) -> Unit,
    unitLabel: String = "min"
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$label: $currentValue $unitLabel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onChange(stepPreset(currentValue, presets, -1))
                }) {
                    Text(text = "-", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                CompactButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onChange(stepPreset(currentValue, presets, 1))
                }) {
                    Text(text = "+", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
        }
    }
}

private fun ScalingLazyListScope.wearPresetStepper(
    label: String,
    currentValue: Int,
    presets: List<Int>,
    haptic: HapticFeedback,
    unitLabel: String = "min",
    onChange: (Int) -> Unit
) {
    item {
        PresetStepperContent(label, currentValue, presets, haptic, onChange, unitLabel)
    }
}

@Composable
fun WearSettingsScreen(
    repository: GlucoseRepository
) {
    val unit by repository.unit.collectAsState()
    val alarms by repository.alarms.collectAsState()
    val behaviors by repository.alarmBehavior.collectAsState()
    val voiceAnnounce by repository.voiceAnnounce.collectAsState()
    val speakAlarms by repository.speakAlarms.collectAsState()
    val targetLow by repository.targetLow.collectAsState()
    val targetHigh by repository.targetHigh.collectAsState()
    val displayConfig by repository.displayConfig.collectAsState()
    val hardwareConfig by repository.hardwareConfig.collectAsState()

    val haptic = LocalHapticFeedback.current
    fun tap(action: () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        action()
    }
    fun push(update: (AlarmConfig) -> AlarmConfig) {
        tap { repository.updateAlarms(update(alarms)) }
    }
    fun pushBehavior(kind: Int, update: (AlarmBehavior) -> AlarmBehavior) {
        val current = behaviors.find { it.kind == kind } ?: AlarmBehavior(kind)
        tap { repository.updateAlarmBehavior(update(current)) }
    }

    val lowSpec = specFor(AlarmKind.LOW, unit)
    val urgentLowSpec = specFor(AlarmKind.URGENT_LOW, unit)
    val highSpec = specFor(AlarmKind.HIGH, unit)
    val veryHighSpec = specFor(AlarmKind.VERY_HIGH, unit)
    val preLowSpec = specFor(AlarmKind.PRE_LOW, unit)
    val preHighSpec = specFor(AlarmKind.PRE_HIGH, unit)
    val targetLowSpec = mgSpec(60f, 110f, 1f, 5f, unit)
    val targetHighSpec = mgSpec(140f, 250f, 5f, 10f, unit)

    // Alarm kinds with sound behavior controls, shown only while enabled.
    val behaviorRows = listOf(
        Triple(0, "Low", alarms.lowAlarmEnabled),
        Triple(5, "Urgent low", alarms.urgentLowEnabled),
        Triple(1, "High", alarms.highAlarmEnabled),
        Triple(6, "Very high", alarms.veryHighEnabled),
        Triple(7, "Low coming", alarms.preLowEnabled),
        Triple(8, "High coming", alarms.preHighEnabled),
        Triple(4, "Signal loss", alarms.lossAlarmEnabled),
        Triple(2, "Value chime", alarms.valueAvailableNotification)
    ).filter { it.third }

    val listState = rememberScalingLazyListState()

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ListHeader {
                    Text("Settings")
                }
            }

            // Alarms Section: every sound-producing alarm can be toggled here,
            // all are evaluated locally on the watch from its own stored settings.
            item {
                ListSubHeader {
                    Text("Alarms")
                }
            }
            item {
                val active = alarms.activeAlarmCount()
                Text(
                    text = if (active == 0) "All alarms off" else "$active alarm${if (active == 1) "" else "s"} on",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (active == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            wearAlarmToggle(
                title = "Low glucose",
                summary = if (alarms.lowAlarmEnabled) formatThreshold(alarms.lowThreshold, lowSpec, unit) else "Off",
                checked = alarms.lowAlarmEnabled,
                onCheckedChange = { enabled -> push { cfg -> cfg.copy(lowAlarmEnabled = enabled) } }
            )
            if (alarms.lowAlarmEnabled) {
                wearThresholdStepper(alarms.lowThreshold, lowSpec, unit, haptic) { value ->
                    push { cfg -> cfg.copy(lowThreshold = value) }
                }
                wearPresetStepper("Snooze", alarms.lowSnoozeMinutes, lowSnoozePresets, haptic) { mins ->
                    push { cfg -> cfg.copy(lowSnoozeMinutes = mins) }
                }
            }

            wearAlarmToggle(
                title = "Urgent low",
                summary = if (alarms.urgentLowEnabled) formatThreshold(alarms.urgentLowThreshold, urgentLowSpec, unit) else "Off",
                checked = alarms.urgentLowEnabled,
                onCheckedChange = { enabled -> push { cfg -> cfg.copy(urgentLowEnabled = enabled) } }
            )
            if (alarms.urgentLowEnabled) {
                wearThresholdStepper(alarms.urgentLowThreshold, urgentLowSpec, unit, haptic) { value ->
                    push { cfg -> cfg.copy(urgentLowThreshold = value) }
                }
                wearPresetStepper("Snooze", alarms.urgentLowSnoozeMinutes, genericSnoozePresets, haptic) { mins ->
                    push { cfg -> cfg.copy(urgentLowSnoozeMinutes = mins) }
                }
            }

            wearAlarmToggle(
                title = "High glucose",
                summary = if (alarms.highAlarmEnabled) formatThreshold(alarms.highThreshold, highSpec, unit) else "Off",
                checked = alarms.highAlarmEnabled,
                onCheckedChange = { enabled -> push { cfg -> cfg.copy(highAlarmEnabled = enabled) } }
            )
            if (alarms.highAlarmEnabled) {
                wearThresholdStepper(alarms.highThreshold, highSpec, unit, haptic) { value ->
                    push { cfg -> cfg.copy(highThreshold = value) }
                }
                wearPresetStepper("Snooze", alarms.highSnoozeMinutes, highSnoozePresets, haptic) { mins ->
                    push { cfg -> cfg.copy(highSnoozeMinutes = mins) }
                }
            }

            wearAlarmToggle(
                title = "Very high",
                summary = if (alarms.veryHighEnabled) formatThreshold(alarms.veryHighThreshold, veryHighSpec, unit) else "Off",
                checked = alarms.veryHighEnabled,
                onCheckedChange = { enabled -> push { cfg -> cfg.copy(veryHighEnabled = enabled) } }
            )
            if (alarms.veryHighEnabled) {
                wearThresholdStepper(alarms.veryHighThreshold, veryHighSpec, unit, haptic) { value ->
                    push { cfg -> cfg.copy(veryHighThreshold = value) }
                }
                wearPresetStepper("Snooze", alarms.veryHighSnoozeMinutes, genericSnoozePresets, haptic) { mins ->
                    push { cfg -> cfg.copy(veryHighSnoozeMinutes = mins) }
                }
            }

            wearAlarmToggle(
                title = "Low coming",
                summary = if (alarms.preLowEnabled) formatThreshold(alarms.preLowThreshold, preLowSpec, unit) else "Off",
                checked = alarms.preLowEnabled,
                onCheckedChange = { enabled -> push { cfg -> cfg.copy(preLowEnabled = enabled) } }
            )
            if (alarms.preLowEnabled) {
                wearThresholdStepper(alarms.preLowThreshold, preLowSpec, unit, haptic) { value ->
                    push { cfg -> cfg.copy(preLowThreshold = value) }
                }
                wearPresetStepper("Snooze", alarms.preLowSnoozeMinutes, genericSnoozePresets, haptic) { mins ->
                    push { cfg -> cfg.copy(preLowSnoozeMinutes = mins) }
                }
            }

            wearAlarmToggle(
                title = "High coming",
                summary = if (alarms.preHighEnabled) formatThreshold(alarms.preHighThreshold, preHighSpec, unit) else "Off",
                checked = alarms.preHighEnabled,
                onCheckedChange = { enabled -> push { cfg -> cfg.copy(preHighEnabled = enabled) } }
            )
            if (alarms.preHighEnabled) {
                wearThresholdStepper(alarms.preHighThreshold, preHighSpec, unit, haptic) { value ->
                    push { cfg -> cfg.copy(preHighThreshold = value) }
                }
                wearPresetStepper("Snooze", alarms.preHighSnoozeMinutes, genericSnoozePresets, haptic) { mins ->
                    push { cfg -> cfg.copy(preHighSnoozeMinutes = mins) }
                }
            }

            wearAlarmToggle(
                title = "Signal loss",
                summary = if (alarms.lossAlarmEnabled) "After ${alarms.lossWaitMinutes} min" else "Off",
                checked = alarms.lossAlarmEnabled,
                onCheckedChange = { enabled -> push { cfg -> cfg.copy(lossAlarmEnabled = enabled) } }
            )
            if (alarms.lossAlarmEnabled) {
                wearPresetStepper("Wait", alarms.lossWaitMinutes, lossWaitPresets, haptic) { mins ->
                    push { cfg -> cfg.copy(lossWaitMinutes = mins) }
                }
            }

            wearAlarmToggle(
                title = "Value chime",
                summary = if (alarms.valueAvailableNotification) "On" else "Off",
                checked = alarms.valueAvailableNotification,
                onCheckedChange = { enabled -> push { cfg -> cfg.copy(valueAvailableNotification = enabled) } }
            )

            // Alarm sound output
            item {
                ListSubHeader {
                    Text("Alarm sound")
                }
            }
            AlarmSoundStream.entries.forEach { stream ->
                item {
                    RadioButton(
                        selected = alarms.soundStream == stream,
                        onSelect = { push { cfg -> cfg.copy(soundStream = stream) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                when (stream) {
                                    AlarmSoundStream.ALARM -> "Alarm"
                                    AlarmSoundStream.NOTIFICATION -> "Notification"
                                    AlarmSoundStream.MEDIA -> "Media"
                                }
                            )
                        }
                    )
                }
            }
            item {
                Text(
                    text = "Alarm sounds even in silent mode. Notification and Media follow system volume.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Per-alarm sound behavior (legacy RingTones equivalent): sound,
            // vibration and duration for every currently enabled alarm.
            if (behaviorRows.isNotEmpty()) {
                item {
                    ListSubHeader {
                        Text("Alarm behavior")
                    }
                }
                behaviorRows.forEach { (kind, title, _) ->
                    val behavior = behaviors.find { it.kind == kind } ?: AlarmBehavior(kind)
                    wearAlarmToggle(
                        title = "$title sound",
                        summary = if (behavior.sound) "On" else "Off",
                        checked = behavior.sound,
                        onCheckedChange = { enabled -> pushBehavior(kind) { it.copy(sound = enabled) } }
                    )
                    wearAlarmToggle(
                        title = "$title vibration",
                        summary = if (behavior.vibration) "On" else "Off",
                        checked = behavior.vibration,
                        onCheckedChange = { enabled -> pushBehavior(kind) { it.copy(vibration = enabled) } }
                    )
                    wearPresetStepper(
                        "$title duration", behavior.durationSecs, alarmDurationPresets, haptic, "sec"
                    ) { secs -> pushBehavior(kind) { it.copy(durationSecs = secs) } }
                }
            }

            // Voice output (legacy Talker config equivalent)
            item {
                ListSubHeader {
                    Text("Voice")
                }
            }
            wearAlarmToggle(
                title = "Speak readings",
                summary = if (voiceAnnounce) "On" else "Off",
                checked = voiceAnnounce,
                onCheckedChange = { enabled -> tap { repository.setVoiceAnnounce(enabled) } }
            )
            wearAlarmToggle(
                title = "Speak alarms",
                summary = if (speakAlarms) "On" else "Off",
                checked = speakAlarms,
                onCheckedChange = { enabled -> tap { repository.setSpeakAlarms(enabled) } }
            )

            // Display & device
            item {
                ListSubHeader {
                    Text("Display")
                }
            }
            wearAlarmToggle(
                title = "24-hour clock",
                summary = if (displayConfig.use24Hour) "On" else "Off",
                checked = displayConfig.use24Hour,
                onCheckedChange = { enabled -> tap { repository.setHour24(enabled) } }
            )
            if (hardwareConfig.hasNfc) {
                wearAlarmToggle(
                    title = "NFC sound",
                    summary = if (hardwareConfig.nfcSound) "On" else "Off",
                    checked = hardwareConfig.nfcSound,
                    onCheckedChange = { enabled -> tap { repository.setNfcSound(enabled) } }
                )
            }

            // Glucose Unit Section
            item {
                ListSubHeader {
                    Text("Glucose Unit")
                }
            }
            item {
                RadioButton(
                    selected = unit == GlucoseUnit.MG_DL,
                    onSelect = { repository.setUnit(GlucoseUnit.MG_DL) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("mg/dL") }
                )
            }
            item {
                RadioButton(
                    selected = unit == GlucoseUnit.MMOL_L,
                    onSelect = { repository.setUnit(GlucoseUnit.MMOL_L) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("mmol/L") }
                )
            }

            // Delta Interval Section
            item {
                ListSubHeader {
                    Text("Delta Interval")
                }
            }
            item {
                RadioButton(
                    selected = displayConfig.deltaCalculation == DeltaCalculation.ONE_MINUTE,
                    onSelect = { repository.setDeltaCalculation(DeltaCalculation.ONE_MINUTE) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("1 Minute") }
                )
            }
            item {
                RadioButton(
                    selected = displayConfig.deltaCalculation == DeltaCalculation.FIVE_MINUTES,
                    onSelect = { repository.setDeltaCalculation(DeltaCalculation.FIVE_MINUTES) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("5 Minutes") }
                )
            }

            // Target Range Section (editable: drives stats and graph colors)
            item {
                ListSubHeader {
                    Text("Target Range")
                }
            }
            wearThresholdStepper(targetLow, targetLowSpec, unit, haptic, "Low target") { value ->
                tap { repository.setTargetRange(value, targetHigh) }
            }
            wearThresholdStepper(targetHigh, targetHighSpec, unit, haptic, "High target") { value ->
                tap { repository.setTargetRange(targetLow, value) }
            }

            // Complications Section
            item {
                ListSubHeader {
                    Text("Complications")
                }
            }
            item {
                TitleCard(
                    onClick = {},
                    title = { Text("Watch Face") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Add glucose complications via your watch face customization menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
