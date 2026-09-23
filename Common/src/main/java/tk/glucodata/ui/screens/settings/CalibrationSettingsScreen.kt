package tk.glucodata.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

@Composable
fun CalibrationSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val displayConfig by repository.displayConfig.collectAsState()

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
