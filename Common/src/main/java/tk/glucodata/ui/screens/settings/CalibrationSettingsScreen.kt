package tk.glucodata.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val displayConfig by repository.displayConfig.collectAsState()
    val bloodLabels by repository.bloodLabels.collectAsState()

    SettingsDetailScaffold(
        title = stringResource(R.string.calibration_title),
        onNavigateBack = onNavigateBack
    ) {
        SettingsSection(title = stringResource(R.string.loc_calibration_sensor)) {
            SettingsSwitchRow(
                title = stringResource(R.string.calibration_enable),
                subtitle = stringResource(R.string.calibration_enable_desc),
                icon = Icons.Default.Tune,
                checked = displayConfig.calibrationEnabled,
                onCheckedChange = { repository.setCalibrationEnabled(it) }
            )

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
                    SettingsIcon(icon = Icons.Default.Bloodtype)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.bloodvar),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = bloodLabels.getOrNull(displayConfig.bloodLabelIndex)
                                ?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.specifyblood),
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
                    bloodLabels.forEachIndexed { index, label ->
                        if (label.isNotBlank()) {
                            DropdownMenuItem(
                                enabled = repository.canSelectBloodLabel(index),
                                text = { Text(label) },
                                trailingIcon = if (displayConfig.bloodLabelIndex == index) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else null,
                                onClick = {
                                    repository.setBloodLabelIndex(index)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (displayConfig.calibrationEnabled) {
            SettingsSection(title = stringResource(R.string.loc_calibration_scope)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.calibration_past),
                    subtitle = stringResource(R.string.calibration_past_desc),
                    icon = Icons.Default.History,
                    checked = displayConfig.calibratePastReadings,
                    onCheckedChange = { repository.setCalibratePastReadings(it) }
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.calibration_all_values),
                    subtitle = stringResource(R.string.calibration_all_values_desc),
                    icon = Icons.Default.AllInclusive,
                    checked = displayConfig.calibrateAllValues,
                    onCheckedChange = { repository.setCalibrateAllValues(it) }
                )
            }
        }

        SettingsInfoCard(
            text = stringResource(R.string.loc_calibration_info),
            icon = Icons.Default.Info
        )
    }
}
