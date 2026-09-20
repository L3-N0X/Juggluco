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
        SettingsSection(title = "Sensor calibration") {
            SettingsSwitchRow(
                title = stringResource(R.string.calibration_enable),
                subtitle = stringResource(R.string.calibration_enable_desc),
                icon = Icons.Default.Tune,
                checked = displayConfig.calibrationEnabled,
                onCheckedChange = { repository.setCalibrationEnabled(it) }
            )
        }

        if (displayConfig.calibrationEnabled) {
            SettingsSection(title = "Scope & application") {
                SettingsSwitchRow(
                    title = stringResource(R.string.calibration_past),
                    subtitle = stringResource(R.string.calibration_past_desc),
                    icon = Icons.Default.History,
                    checked = displayConfig.calibratePastReadings,
                    onCheckedChange = { repository.setCalibratePastReadings(it) }
                )

                SettingsDivider()

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
            text = "Calibration calculates an offset factor from fingerstick blood glucose entries. Use only stable reference values (no rapid rising or falling arrows) for the most accurate adjustments.",
            icon = Icons.Default.Info
        )
    }
}
