package tk.glucodata.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import tk.glucodata.R
import tk.glucodata.ui.components.WearQuickLogDial
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LogType
import kotlin.math.roundToInt

private data class QuickLogSpec(
    val min: Float,
    val max: Float,
    val smallStep: Float,
    val largeStep: Float,
    val initial: Float
)

private fun specFor(type: LogType, unit: GlucoseUnit): QuickLogSpec {
    return when (type) {
        LogType.CARBS -> QuickLogSpec(min = 0f, max = 150f, smallStep = 1f, largeStep = 5f, initial = 20f)
        LogType.RAPID_INSULIN -> QuickLogSpec(min = 0f, max = 30f, smallStep = 0.5f, largeStep = 1f, initial = 2f)
        LogType.BASAL_INSULIN -> QuickLogSpec(min = 0f, max = 60f, smallStep = 0.5f, largeStep = 1f, initial = 10f)
        LogType.BLOOD_GLUCOSE -> if (unit == GlucoseUnit.MMOL_L) {
            QuickLogSpec(min = 1.1f, max = 22.2f, smallStep = 0.1f, largeStep = 1f, initial = 5.5f)
        } else {
            QuickLogSpec(min = 20f, max = 400f, smallStep = 1f, largeStep = 10f, initial = 100f)
        }
        else -> QuickLogSpec(min = 0f, max = 100f, smallStep = 1f, largeStep = 5f, initial = 0f)
    }
}

@Composable
fun WearQuickLogScreen(
    repository: GlucoseRepository,
    onSaved: () -> Unit
) {
    val unit by repository.unit.collectAsState()

    var selectedType by remember { mutableStateOf(LogType.CARBS) }
    val spec = remember(selectedType, unit) { specFor(selectedType, unit) }
    var value by remember(spec) { mutableFloatStateOf(spec.initial) }

    fun adjust(delta: Float) {
        val steps = ((value + delta - spec.min) / spec.smallStep).roundToInt()
        value = (spec.min + steps * spec.smallStep).coerceIn(spec.min, spec.max)
    }

    val (displayText, labelText) = when (selectedType) {
        LogType.CARBS -> "${value.roundToInt()} g" to stringResource(R.string.log_short_carbs)
        LogType.RAPID_INSULIN -> String.format(java.util.Locale.getDefault(), "%.1f U", value) to stringResource(R.string.log_short_bolus)
        LogType.BASAL_INSULIN -> String.format(java.util.Locale.getDefault(), "%.1f U", value) to stringResource(R.string.log_short_basal)
        LogType.BLOOD_GLUCOSE -> if (unit == GlucoseUnit.MMOL_L) {
            String.format(java.util.Locale.getDefault(), "%.1f", value) to stringResource(unit.labelRes)
        } else {
            "${value.roundToInt()}" to stringResource(unit.labelRes)
        }
        else -> "$value" to ""
    }

    val haptic = LocalHapticFeedback.current
    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = 0,
        initialCenterItemScrollOffset = 0
    )

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            anchorType = ScalingLazyListAnchorType.ItemStart,
            autoCentering = null,
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    listOf(
                        listOf("Carbs" to LogType.CARBS, "Bolus" to LogType.RAPID_INSULIN),
                        listOf("Basal" to LogType.BASAL_INSULIN, "BG" to LogType.BLOOD_GLUCOSE)
                    ).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { (label, type) ->
                                LogCategoryPill(
                                    label = label,
                                    isSelected = selectedType == type,
                                    onClick = {
                                        selectedType = type
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Circular rotary dial: crown + spin-the-ring to adjust
            item {
                WearQuickLogDial(
                    value = value,
                    range = spec.min..spec.max,
                    step = spec.smallStep,
                    onValueChange = { value = it },
                    displayText = displayText,
                    labelText = labelText,
                    contentDescription = when (selectedType) {
                        LogType.CARBS -> "Carbohydrates in grams"
                        LogType.RAPID_INSULIN -> "Rapid insulin in units"
                        LogType.BASAL_INSULIN -> "Basal insulin in units"
                        LogType.BLOOD_GLUCOSE -> "Blood glucose"
                        else -> "Value"
                    }
                )
            }

            item {
                Text(
                    text = "Turn crown or spin ring",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Stepper buttons with clear font size
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactButton(
                        onClick = {
                            adjust(-spec.largeStep)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text(
                            text = "-${formatStep(spec.largeStep)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    CompactButton(
                        onClick = {
                            adjust(-spec.smallStep)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text(
                            text = "-${formatStep(spec.smallStep)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    CompactButton(
                        onClick = {
                            adjust(spec.smallStep)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text(
                            text = "+${formatStep(spec.smallStep)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    CompactButton(
                        onClick = {
                            adjust(spec.largeStep)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text(
                            text = "+${formatStep(spec.largeStep)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Save Action (standard Wear M3 Button)
            item {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val rawValue = if (selectedType == LogType.BLOOD_GLUCOSE && unit == GlucoseUnit.MMOL_L) {
                            unit.toMgDl(value)
                        } else {
                            value
                        }
                        repository.addLogEntry(selectedType, rawValue, "")
                        onSaved()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                    },
                    label = {
                        Text("Save")
                    }
                )
            }
        }
    }
}

private fun formatStep(step: Float): String {
    return if (step < 1f) {
        String.format(java.util.Locale.US, "%.1f", step).trimEnd('0').trimEnd('.').let {
            if (it.startsWith("0")) it.substring(1) else it
        }.let { if (it.startsWith(".")) "0$it" else it }
    } else {
        step.roundToInt().toString()
    }
}

@Composable
private fun LogCategoryPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompactButton(
        onClick = onClick,
        modifier = modifier,
        colors = if (isSelected) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        }
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
