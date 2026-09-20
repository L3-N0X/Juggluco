package tk.glucodata.ui.graph

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.model.TimeRange

@Composable
fun TimeRangeSelector(
    selectedRange: TimeRange?,
    onRangeSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Standard Presets
        TimeRange.PRESETS.forEach { range ->
            val isSelected = range == selectedRange
            FilterChip(
                selected = isSelected,
                onClick = { onRangeSelected(range) },
                label = {
                    Text(
                        text = if (range.labelRes != null) stringResource(range.labelRes) else range.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 12.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }

        // Custom range chip
        val isCustomRange = selectedRange?.isCustom == true && TimeRange.PRESETS.none { it.durationMillis == selectedRange.durationMillis }
        FilterChip(
            selected = isCustomRange,
            onClick = { showCustomDialog = true },
            label = {
                Text(
                    text = if (isCustomRange) selectedRange.label else stringResource(R.string.timerange_custom),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isCustomRange) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = stringResource(R.string.custom_range),
                    modifier = Modifier.size(14.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }

    if (showCustomDialog) {
        CustomTimeRangeDialog(
            currentRange = selectedRange ?: TimeRange.SIX_HOURS,
            onDismiss = { showCustomDialog = false },
            onApply = { customRange ->
                showCustomDialog = false
                onRangeSelected(customRange)
            }
        )
    }
}

@Composable
fun CustomTimeRangeDialog(
    currentRange: TimeRange,
    onDismiss: () -> Unit,
    onApply: (TimeRange) -> Unit
) {
    var isDaysMode by remember {
        mutableStateOf(currentRange.durationMillis >= 24 * 3600 * 1000L && currentRange.durationMillis % (24 * 3600 * 1000L) == 0L)
    }

    val initialHours = (currentRange.durationMillis / (3600 * 1000L)).toInt().coerceIn(1, 72)
    val initialDays = (currentRange.durationMillis / (24 * 3600 * 1000L)).toInt().coerceIn(1, 90)

    var hoursSlider by remember { mutableFloatStateOf(initialHours.toFloat()) }
    var daysSlider by remember { mutableFloatStateOf(initialDays.toFloat()) }

    val quickHourIntervals = listOf(2, 4, 8, 16, 18, 36, 48)
    val quickDayIntervals = listOf(2, 3, 5, 10, 14, 21, 30)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.custom_time_range),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mode Toggle: Hours vs Days
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (!isDaysMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { isDaysMode = false }
                    ) {
                        Text(
                            text = "Hours",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (!isDaysMode) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isDaysMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDaysMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { isDaysMode = true }
                    ) {
                        Text(
                            text = "Days",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isDaysMode) FontWeight.Bold else FontWeight.Normal,
                            color = if (isDaysMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }

                HorizontalDivider()

                // Quick presets row
                Text(
                    text = stringResource(R.string.quick_presets),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!isDaysMode) {
                        quickHourIntervals.forEach { h ->
                            val isCurrent = hoursSlider.toInt() == h
                            FilterChip(
                                selected = isCurrent,
                                onClick = { hoursSlider = h.toFloat() },
                                label = { Text("${h}h", fontSize = 11.sp) }
                            )
                        }
                    } else {
                        quickDayIntervals.forEach { d ->
                            val isCurrent = daysSlider.toInt() == d
                            FilterChip(
                                selected = isCurrent,
                                onClick = { daysSlider = d.toFloat() },
                                label = { Text("${d}d", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Slider & Numeric Display
                if (!isDaysMode) {
                    val currentH = hoursSlider.toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Duration",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$currentH hours",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = hoursSlider,
                        onValueChange = { hoursSlider = it },
                        valueRange = 1f..72f,
                        steps = 70
                    )
                } else {
                    val currentD = daysSlider.toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Duration",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$currentD days",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = daysSlider,
                        onValueChange = { daysSlider = it },
                        valueRange = 1f..90f,
                        steps = 88
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val resultRange = if (!isDaysMode) {
                        TimeRange.fromHours(hoursSlider.toInt())
                    } else {
                        TimeRange.fromDays(daysSlider.toInt())
                    }
                    onApply(resultRange)
                }
            ) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
