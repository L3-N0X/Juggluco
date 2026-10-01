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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
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
import androidx.wear.compose.material3.SwitchButtonDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TitleCard
import androidx.annotation.StringRes
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.DeltaCalculation
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.RangeLevel
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

/** Steps that land on round numbers on the watch: 1/5 below 200, 5/10 above it. */
private fun rangeSpec(bounds: ClosedFloatingPointRange<Float>, unit: GlucoseUnit): ThresholdSpec {
    val smallStep = if (bounds.endInclusive > 200f) 5f else 1f
    val largeStep = if (bounds.endInclusive > 200f) 10f else 5f
    return mgSpec(bounds.start, bounds.endInclusive, smallStep, largeStep, unit)
}

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
    // A whole width SwitchButton filled with `primary` is a huge, fully saturated block on a small
    // screen, and a custom accent makes it worse. The container pair keeps the row on the muted
    // scale while the filled buttons around it stay the loud, primary coloured elements.
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(title) },
        secondaryLabel = { Text(summary) },
        colors = SwitchButtonDefaults.switchButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            uncheckedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            uncheckedContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

private fun ScalingLazyListScope.wearAlarmToggle(
    @StringRes titleRes: Int,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    item {
        AlarmSwitchRow(stringResource(titleRes), summary, checked, onCheckedChange)
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
    unitLabel: String,
    valueText: (@Composable (Int) -> String)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.wear_ui_label_value, label, valueText?.invoke(currentValue) ?: "$currentValue $unitLabel"),
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
    unitLabel: String,
    onChange: (Int) -> Unit
) {
    item {
        PresetStepperContent(label, currentValue, presets, haptic, onChange, unitLabel)
    }
}

@Composable
fun WearSettingsScreen(
    repository: GlucoseRepository,
    onOpenAlerts: () -> Unit,
    onOpenAppearance: () -> Unit
) {
    val unit by repository.unit.collectAsState()
    val voiceAnnounce by repository.voiceAnnounce.collectAsState()
    val glucoseRange by repository.range.collectAsState()
    val displayConfig by repository.displayConfig.collectAsState()
    val hardwareConfig by repository.hardwareConfig.collectAsState()
    val insulinOnboardOn by repository.insulinOnboardEnabled.collectAsState()
    val colorPreset by WearThemePreferences.colorPreset.collectAsState()

    val haptic = LocalHapticFeedback.current
    // Native refuses to calculate while no label holds an insulin type, and the watch cannot set
    // one itself, so the refusal is the only feedback there is to give.
    var insulinOnboardRefused by remember { mutableStateOf(false) }
    fun tap(action: () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        action()
    }
    // Each stepper is bounded by its neighbours, exactly like the phone sliders, so the four cut
    // points cannot cross no matter which one is turned.
    val rangeBounds = RangeLevel.entries.associateWith { glucoseRange.sliderBoundsFor(it) }
    val veryLowSpec = rangeSpec(rangeBounds.getValue(RangeLevel.VERY_LOW), unit)
    val lowSpec = rangeSpec(rangeBounds.getValue(RangeLevel.LOW), unit)
    val highSpec = rangeSpec(rangeBounds.getValue(RangeLevel.HIGH), unit)
    val veryHighSpec = rangeSpec(rangeBounds.getValue(RangeLevel.VERY_HIGH), unit)
    val targetRangeTitle = stringResource(R.string.loc_target_range)
    val veryLowLabel = stringResource(R.string.status_very_low)
    val lowLabel = stringResource(R.string.status_low)
    val highLabel = stringResource(R.string.status_high)
    val veryHighLabel = stringResource(R.string.status_very_high)
    val onLabel = stringResource(R.string.wear_ui_on)
    val offLabel = stringResource(R.string.wear_ui_off)
    val iobOnLabel = stringResource(R.string.iob_show_on)
    val iobOffLabel = stringResource(R.string.iob_show_off)
    val iobRefusedLabel = stringResource(R.string.iob_needs_type)

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
                    Text(stringResource(R.string.wear_ui_settings))
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
                    label = { Text(stringResource(R.string.wear_ui_alerts)) }
                )
            }

            // Voice output (legacy Talker config equivalent)
            item {
                ListSubHeader {
                    Text(stringResource(R.string.wear_ui_voice))
                }
            }
            wearAlarmToggle(
                titleRes = R.string.wear_ui_speak_readings,
                summary = if (voiceAnnounce) onLabel else offLabel,
                checked = voiceAnnounce,
                onCheckedChange = { enabled -> tap { repository.setVoiceAnnounce(enabled) } }
            )
            // Color preset lives on its own screen to keep this list short.
            item {
                TitleCard(
                    onClick = onOpenAppearance,
                    title = { Text(stringResource(R.string.wear_ui_appearance)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(colorPreset.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Display & device
            item {
                ListSubHeader {
                    Text(stringResource(R.string.wear_ui_display))
                }
            }
            wearAlarmToggle(
                titleRes = R.string.wear_ui_24h_clock,
                summary = if (displayConfig.use24Hour) onLabel else offLabel,
                checked = displayConfig.use24Hour,
                onCheckedChange = { enabled -> tap { repository.setHour24(enabled) } }
            )
            if (hardwareConfig.hasNfc) {
                wearAlarmToggle(
                    titleRes = R.string.wear_ui_nfc_sound,
                    summary = if (hardwareConfig.nfcSound) onLabel else offLabel,
                    checked = hardwareConfig.nfcSound,
                    onCheckedChange = { enabled -> tap { repository.setNfcSound(enabled) } }
                )
            }

            // Glucose Unit Section
            item {
                ListSubHeader {
                    Text(stringResource(R.string.wear_ui_glucose_unit))
                }
            }
            item {
                RadioButton(
                    selected = unit == GlucoseUnit.MG_DL,
                    onSelect = { repository.setUnit(GlucoseUnit.MG_DL) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(GlucoseUnit.MG_DL.labelRes)) }
                )
            }
            item {
                RadioButton(
                    selected = unit == GlucoseUnit.MMOL_L,
                    onSelect = { repository.setUnit(GlucoseUnit.MMOL_L) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(GlucoseUnit.MMOL_L.labelRes)) }
                )
            }

            // Delta Interval Section
            item {
                ListSubHeader {
                    Text(stringResource(R.string.settings_delta_calculation))
                }
            }
            item {
                RadioButton(
                    selected = displayConfig.deltaCalculation == DeltaCalculation.ONE_MINUTE,
                    onSelect = { repository.setDeltaCalculation(DeltaCalculation.ONE_MINUTE) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_delta_1min)) }
                )
            }
            item {
                RadioButton(
                    selected = displayConfig.deltaCalculation == DeltaCalculation.FIVE_MINUTES,
                    onSelect = { repository.setDeltaCalculation(DeltaCalculation.FIVE_MINUTES) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_delta_5min)) }
                )
            }

            // Target Range Section (editable: drives stats and graph colors)
            item {
                ListSubHeader {
                    Text(targetRangeTitle)
                }
            }
            wearThresholdStepper(
                unit.toDisplay(glucoseRange.veryLowMgDl), veryLowSpec, unit, haptic,
                veryLowLabel
            ) { value ->
                tap { repository.setRangeLevel(RangeLevel.VERY_LOW, unit.toMgDl(value)) }
            }
            wearThresholdStepper(
                unit.toDisplay(glucoseRange.lowMgDl), lowSpec, unit, haptic,
                lowLabel
            ) { value ->
                tap { repository.setRangeLevel(RangeLevel.LOW, unit.toMgDl(value)) }
            }
            wearThresholdStepper(
                unit.toDisplay(glucoseRange.highMgDl), highSpec, unit, haptic,
                highLabel
            ) { value ->
                tap { repository.setRangeLevel(RangeLevel.HIGH, unit.toMgDl(value)) }
            }
            wearThresholdStepper(
                unit.toDisplay(glucoseRange.veryHighMgDl), veryHighSpec, unit, haptic,
                veryHighLabel
            ) { value ->
                tap { repository.setRangeLevel(RangeLevel.VERY_HIGH, unit.toMgDl(value)) }
            }

            // Insulin on board. The insulin types themselves come from the phone with the labels,
            // so only the switch is here; native turns the request down while none of them is set,
            // which the summary then says instead of leaving the switch to snap back.
            item {
                ListSubHeader {
                    Text(stringResource(R.string.iob_title))
                }
            }
            wearAlarmToggle(
                titleRes = R.string.iob_show,
                summary = when {
                    insulinOnboardRefused -> iobRefusedLabel
                    insulinOnboardOn -> iobOnLabel
                    else -> iobOffLabel
                },
                checked = insulinOnboardOn,
                onCheckedChange = { turnedOn ->
                    tap { insulinOnboardRefused = !repository.setInsulinOnboard(turnedOn) }
                }
            )

            // Complications Section
            item {
                ListSubHeader {
                    Text(stringResource(R.string.wear_ui_complications))
                }
            }
            item {
                TitleCard(
                    onClick = {},
                    title = { Text(stringResource(R.string.wear_ui_watch_face)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.wear_ui_watch_face_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
