package tk.glucodata.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.theme.LocalClinicalColors

@Composable
fun GlucoseTargetsSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val unit by repository.unit.collectAsState()
    val targetLow by repository.targetLow.collectAsState()
    val targetHigh by repository.targetHigh.collectAsState()

    var currentLowSlider by remember(targetLow) { mutableFloatStateOf(targetLow) }
    var currentHighSlider by remember(targetHigh) { mutableFloatStateOf(targetHigh) }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_card_target_range),
        subtitle = stringResource(R.string.settings_cat_glucose),
        onNavigateBack = onNavigateBack
    ) {
        // UNIT SELECTION CARD
        SettingsCard(
            title = stringResource(R.string.settings_unit_label),
            icon = Icons.Default.Straighten,
            categorySubtitle = stringResource(R.string.settings_cat_glucose)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = if (unit == GlucoseUnit.MG_DL) stringResource(R.string.mgdL) else stringResource(R.string.mmolL),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (unit == GlucoseUnit.MG_DL) stringResource(R.string.settings_unit_mgdl_desc) else stringResource(R.string.settings_unit_mmoll_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.mgdL),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (unit == GlucoseUnit.MG_DL) FontWeight.Bold else FontWeight.Normal,
                        color = if (unit == GlucoseUnit.MG_DL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = unit == GlucoseUnit.MMOL_L,
                        onCheckedChange = { isMmol ->
                            repository.setUnit(if (isMmol) GlucoseUnit.MMOL_L else GlucoseUnit.MG_DL)
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.mmolL),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (unit == GlucoseUnit.MMOL_L) FontWeight.Bold else FontWeight.Normal,
                        color = if (unit == GlucoseUnit.MMOL_L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // TARGET THRESHOLDS CARD
        SettingsCard(
            title = stringResource(R.string.settings_card_target_range),
            categorySubtitle = "BOUNDARIES"
        ) {
            // Visual range bar preview
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(
                    text = "Range Preview: ${unit.format(currentLowSlider)} - ${unit.format(currentHighSlider)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                val clinicalColors = LocalClinicalColors.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                ) {
                    // Low / Hypo zone
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(clinicalColors.low)
                    )
                    // Target / In-range zone
                    Box(
                        modifier = Modifier
                            .weight(2.5f)
                            .background(clinicalColors.inRange)
                    )
                    // High / Hyper zone
                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .background(clinicalColors.high)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Low (< ${unit.format(currentLowSlider)})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text("In Target Range", style = MaterialTheme.typography.labelSmall, color = clinicalColors.inRange, fontWeight = FontWeight.Bold)
                    Text("High (> ${unit.format(currentHighSlider)})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Target Low Slider
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_target_low),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Standard guideline: 70 mg/dL (3.9 mmol/L)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = unit.format(currentLowSlider),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = currentLowSlider,
                    onValueChange = { currentLowSlider = it },
                    onValueChangeFinished = { repository.setTargetRange(currentLowSlider, currentHighSlider) },
                    valueRange = 55f..100f
                )
                // Quick presets for low
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(65f, 70f, 75f, 80f).forEach { preset ->
                        FilterChip(
                            selected = kotlin.math.abs(currentLowSlider - preset) < 0.5f,
                            onClick = {
                                currentLowSlider = preset
                                repository.setTargetRange(preset, currentHighSlider)
                            },
                            label = { Text(unit.format(preset), fontSize = 11.sp) }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            // Target High Slider
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_target_high),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Standard guideline: 180 mg/dL (10.0 mmol/L)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = unit.format(currentHighSlider),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = currentHighSlider,
                    onValueChange = { currentHighSlider = it },
                    onValueChangeFinished = { repository.setTargetRange(currentLowSlider, currentHighSlider) },
                    valueRange = 140f..250f
                )
                // Quick presets for high
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(140f, 160f, 180f, 200f, 220f).forEach { preset ->
                        FilterChip(
                            selected = kotlin.math.abs(currentHighSlider - preset) < 0.5f,
                            onClick = {
                                currentHighSlider = preset
                                repository.setTargetRange(currentLowSlider, preset)
                            },
                            label = { Text(unit.format(preset), fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // CLINICAL GUIDANCE CARD
        SettingsInfoCard(
            text = "Target range thresholds determine the Time in Range (TIR) percentages displayed on the Stats and Glucose graphs. The International Consensus on Time in Range recommends a target range of 70–180 mg/dL (3.9–10.0 mmol/L) with a goal of >70% time in range for most individuals.",
            icon = Icons.Default.Info
        )
    }
}
