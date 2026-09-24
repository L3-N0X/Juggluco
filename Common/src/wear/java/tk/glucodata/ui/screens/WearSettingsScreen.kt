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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
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
import tk.glucodata.ui.model.DeltaCalculation
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.theme.WearColorPreset
import tk.glucodata.ui.theme.WearThemePreferences
import kotlin.math.roundToInt

internal data class ThresholdSpec(
    val min: Float,
    val max: Float,
    val smallStep: Float,
    val largeStep: Float,
    val decimals: Int
)

/** Native thresholds are stored per display unit, so specs are converted for mmol/L. */
internal fun mgSpec(min: Float, max: Float, smallStep: Float, largeStep: Float, unit: GlucoseUnit): ThresholdSpec {
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

private fun round1(value: Float): Float = (value * 10).roundToInt() / 10f

private fun adjustThreshold(current: Float, delta: Float, spec: ThresholdSpec): Float {
    val stepped = (current + delta).coerceIn(spec.min, spec.max)
    return if (spec.decimals == 1) round1(stepped).coerceIn(spec.min, spec.max)
    else stepped.roundToInt().toFloat().coerceIn(spec.min, spec.max)
}

internal fun formatThreshold(value: Float, spec: ThresholdSpec, unit: GlucoseUnit): String {
    val number = if (spec.decimals == 1) {
        String.format(java.util.Locale.getDefault(), "%.1f", value)
    } else {
        value.roundToInt().toString()
    }
    return "$number ${unit.symbol}"
}

private fun formatStep(step: Float, spec: ThresholdSpec): String {
    return if (spec.decimals == 1) {
        String.format(java.util.Locale.getDefault(), "%.1f", step).trimEnd('0').trimEnd('.')
            .let { if (it.startsWith(".")) "0$it" else it }
    } else {
        step.roundToInt().toString()
    }
}

private fun stepPreset(current: Int, presets: List<Int>, direction: Int): Int {
    val index = presets.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: (presets.size - 1)
    return presets[(index + direction).coerceIn(0, presets.size - 1)]
}

@Composable
internal fun AlarmSwitchRow(
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
internal fun ThresholdStepperContent(
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
internal fun PresetStepperContent(
    label: String,
    currentValue: Int,
    presets: List<Int>,
    haptic: HapticFeedback,
    onChange: (Int) -> Unit,
    unitLabel: String = "min",
    valueText: ((Int) -> String)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$label: ${valueText?.invoke(currentValue) ?: "$currentValue $unitLabel"}",
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
    repository: GlucoseRepository,
    onOpenAlerts: () -> Unit
) {
    val unit by repository.unit.collectAsState()
    val voiceAnnounce by repository.voiceAnnounce.collectAsState()
    val targetLow by repository.targetLow.collectAsState()
    val targetHigh by repository.targetHigh.collectAsState()
    val displayConfig by repository.displayConfig.collectAsState()
    val hardwareConfig by repository.hardwareConfig.collectAsState()
    val colorPreset by WearThemePreferences.colorPreset.collectAsState()

    val haptic = LocalHapticFeedback.current
    fun tap(action: () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        action()
    }
    val targetLowSpec = mgSpec(60f, 110f, 1f, 5f, unit)
    val targetHighSpec = mgSpec(140f, 250f, 5f, 10f, unit)

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

            // Alerts live on their own screen, shared with the phone when synced.
            item {
                FilledTonalButton(
                    onClick = onOpenAlerts,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                    },
                    label = { Text("Alerts") }
                )
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
            item {
                ListSubHeader {
                    Text("Appearance")
                }
            }
            WearColorPreset.values().forEach { preset ->
                item {
                    RadioButton(
                        selected = colorPreset == preset,
                        onSelect = { tap { WearThemePreferences.setColorPreset(preset) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(preset.label) }
                    )
                }
            }
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
