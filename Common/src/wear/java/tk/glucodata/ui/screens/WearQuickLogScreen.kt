package tk.glucodata.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
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
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LogType

@Composable
fun WearQuickLogScreen(
    repository: GlucoseRepository,
    onSaved: () -> Unit
) {
    val unit by repository.unit.collectAsState()

    var selectedType by remember { mutableStateOf(LogType.CARBS) }
    var value by remember(selectedType) {
        mutableFloatStateOf(
            when (selectedType) {
                LogType.CARBS -> 20f
                LogType.RAPID_INSULIN -> 2f
                LogType.BASAL_INSULIN -> 10f
                LogType.BLOOD_GLUCOSE -> if (unit == GlucoseUnit.MMOL_L) 5.5f else 100f
                else -> 0f
            }
        )
    }

    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberScalingLazyListState()

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {}
    }

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onRotaryScrollEvent { event ->
                    val step = if (selectedType == LogType.CARBS) 1f else 0.5f
                    if (event.verticalScrollPixels > 0) {
                        value += step
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } else if (event.verticalScrollPixels < 0) {
                        value = (value - step).coerceAtLeast(0f)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    true
                },
            state = listState,
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Category selector (2 rows for comfortable tap targets and readability)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    listOf(
                        Pair("Carbs", LogType.CARBS),
                        Pair("Bolus", LogType.RAPID_INSULIN)
                    ).forEach { (label, type) ->
                        LogCategoryPill(
                            label = label,
                            isSelected = selectedType == type,
                            onClick = {
                                selectedType = type
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    listOf(
                        Pair("Basal", LogType.BASAL_INSULIN),
                        Pair("BG", LogType.BLOOD_GLUCOSE)
                    ).forEach { (label, type) ->
                        LogCategoryPill(
                            label = label,
                            isSelected = selectedType == type,
                            onClick = {
                                selectedType = type
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Value Display
            item {
                val formattedDisplay = when (selectedType) {
                    LogType.CARBS -> "${value.toInt()} g"
                    LogType.RAPID_INSULIN, LogType.BASAL_INSULIN -> String.format(java.util.Locale.US, "%.1f U", value)
                    LogType.BLOOD_GLUCOSE -> if (unit == GlucoseUnit.MMOL_L) {
                        String.format(java.util.Locale.US, "%.1f %s", value, unit.label)
                    } else {
                        "${value.toInt()} ${unit.label}"
                    }
                    else -> "$value"
                }

                Text(
                    text = formattedDisplay,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Stepper buttons with clear font size
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val stepSmall = if (selectedType == LogType.CARBS) 1f else 0.5f
                    val stepLarge = if (selectedType == LogType.CARBS) 5f else 1.0f

                    CompactButton(
                        onClick = {
                            value = (value - stepLarge).coerceAtLeast(0f)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text(
                            text = "-${stepLarge.toInt().coerceAtLeast(1)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    CompactButton(
                        onClick = {
                            value = (value - stepSmall).coerceAtLeast(0f)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text(
                            text = if (stepSmall < 1f) "-0.5" else "-1",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    CompactButton(
                        onClick = {
                            value += stepSmall
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text(
                            text = if (stepSmall < 1f) "+0.5" else "+1",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    CompactButton(
                        onClick = {
                            value += stepLarge
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text(
                            text = "+${stepLarge.toInt().coerceAtLeast(1)}",
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
