package tk.glucodata.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GlucoseRange
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.RangeLevel
import tk.glucodata.ui.theme.LocalClinicalColors
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseTargetsSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val unit by repository.unit.collectAsState()
    val range by repository.range.collectAsState()
    val clinicalColors = LocalClinicalColors.current

    // Local mirror so the thumb tracks the finger; the repository is only written on release.
    var pendingVeryLow by remember(range) { mutableFloatStateOf(range.veryLowMgDl) }
    var pendingLow by remember(range) { mutableFloatStateOf(range.lowMgDl) }
    var pendingHigh by remember(range) { mutableFloatStateOf(range.highMgDl) }
    var pendingVeryHigh by remember(range) { mutableFloatStateOf(range.veryHighMgDl) }

    // Each slider's window is set by its neighbours, so the bounds are derived from the range the
    // other three currently propose. Normalizing the proposal keeps the windows meaningful even in
    // the one frame between a drag and the write that resolves it.
    val preview = remember(pendingVeryLow, pendingLow, pendingHigh, pendingVeryHigh) {
        GlucoseRange(
            veryLowMgDl = pendingVeryLow,
            lowMgDl = pendingLow,
            highMgDl = pendingHigh,
            veryHighMgDl = pendingVeryHigh
        ).normalized()
    }
    val bounds = remember(preview) { RangeLevel.entries.associateWith { preview.sliderBoundsFor(it) } }

    fun commit(level: RangeLevel, value: Float) {
        val next = preview.withLevel(level, value)
        pendingVeryLow = next.veryLowMgDl
        pendingLow = next.lowMgDl
        pendingHigh = next.highMgDl
        pendingVeryHigh = next.veryHighMgDl
        repository.setGlucoseRange(next)
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_card_target_range),
        onNavigateBack = onNavigateBack
    ) {
        // UNIT SELECTION
        SettingsSection(title = stringResource(R.string.loc_common_unit)) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(unit.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.loc_example_value, unit.format(100f)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = stringResource(R.string.mgdL),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.loc_example_value, GlucoseUnit.MG_DL.format(100f)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        trailingIcon = if (unit == GlucoseUnit.MG_DL) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null,
                        onClick = {
                            repository.setUnit(GlucoseUnit.MG_DL)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = stringResource(R.string.mmolL),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.loc_example_value, GlucoseUnit.MMOL_L.format(100f)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        trailingIcon = if (unit == GlucoseUnit.MMOL_L) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null,
                        onClick = {
                            repository.setUnit(GlucoseUnit.MMOL_L)
                            expanded = false
                        }
                    )
                }
            }
        }

        // THE FOUR CUT POINTS
        SettingsSection(title = stringResource(R.string.loc_target_range)) {
            RangeSlider(
                label = stringResource(R.string.status_very_low),
                description = stringResource(
                    R.string.loc_range_desc,
                    stringResource(R.string.tir_range_less_than, unit.format(range.veryLowMgDl))
                ),
                value = pendingVeryLow,
                bounds = bounds.getValue(RangeLevel.VERY_LOW),
                defaultValue = GlucoseRange.DEFAULT_VERY_LOW,
                unit = unit,
                accent = clinicalColors.veryLow,
                onValueChange = { pendingVeryLow = it },
                onValueChangeFinished = { commit(RangeLevel.VERY_LOW, pendingVeryLow) },
                onReset = { commit(RangeLevel.VERY_LOW, GlucoseRange.DEFAULT_VERY_LOW) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            RangeSlider(
                label = stringResource(R.string.status_low),
                description = stringResource(
                    R.string.loc_range_desc,
                    stringResource(
                        R.string.tir_range_between,
                        unit.format(range.veryLowMgDl),
                        unit.format(range.lowMgDl - 1f)
                    )
                ),
                value = pendingLow,
                bounds = bounds.getValue(RangeLevel.LOW),
                defaultValue = GlucoseRange.DEFAULT_LOW,
                unit = unit,
                accent = clinicalColors.low,
                onValueChange = { pendingLow = it },
                onValueChangeFinished = { commit(RangeLevel.LOW, pendingLow) },
                onReset = { commit(RangeLevel.LOW, GlucoseRange.DEFAULT_LOW) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            RangeSlider(
                label = stringResource(R.string.status_high),
                description = stringResource(
                    R.string.loc_range_desc,
                    stringResource(
                        R.string.tir_range_between,
                        unit.format(range.highMgDl + 1f),
                        unit.format(range.veryHighMgDl)
                    )
                ),
                value = pendingHigh,
                bounds = bounds.getValue(RangeLevel.HIGH),
                defaultValue = GlucoseRange.DEFAULT_HIGH,
                unit = unit,
                accent = clinicalColors.high,
                onValueChange = { pendingHigh = it },
                onValueChangeFinished = { commit(RangeLevel.HIGH, pendingHigh) },
                onReset = { commit(RangeLevel.HIGH, GlucoseRange.DEFAULT_HIGH) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            RangeSlider(
                label = stringResource(R.string.status_very_high),
                description = stringResource(
                    R.string.loc_range_desc,
                    stringResource(R.string.tir_range_greater_than, unit.format(range.veryHighMgDl))
                ),
                value = pendingVeryHigh,
                bounds = bounds.getValue(RangeLevel.VERY_HIGH),
                defaultValue = GlucoseRange.DEFAULT_VERY_HIGH,
                unit = unit,
                accent = clinicalColors.veryHigh,
                onValueChange = { pendingVeryHigh = it },
                onValueChangeFinished = { commit(RangeLevel.VERY_HIGH, pendingVeryHigh) },
                onReset = { commit(RangeLevel.VERY_HIGH, GlucoseRange.DEFAULT_VERY_HIGH) }
            )
        }

        // CLINICAL GUIDANCE
        SettingsInfoCard(
            text = stringResource(
                R.string.target_range_consensus,
                unit.format(GlucoseRange.DEFAULT_LOW),
                unit.format(GlucoseRange.DEFAULT_HIGH),
                stringResource(unit.labelRes)
            ),
            icon = Icons.Default.Info
        )
    }
}

/**
 * One band edge: its name, the slice of values it owns, a slider tinted with that band's colour and
 * a way back to the consensus value. The value sits in the thumb, so there is no separate readout
 * and no pill next to the title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSlider(
    label: String,
    description: String,
    value: Float,
    bounds: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    unit: GlucoseUnit,
    accent: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (abs(value - defaultValue) > 0.5f) {
                Text(
                    text = stringResource(R.string.loc_reset),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clickable(onClick = onReset)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = value.coerceIn(bounds.start, bounds.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = bounds,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
            ),
            modifier = Modifier.fillMaxWidth(),
            thumb = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accent,
                    shadowElevation = 2.dp
                ) {
                    Text(
                        text = unit.format(value),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        )
    }
}
