package tk.glucodata.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

@Composable
fun AboutSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    SettingsDetailScaffold(
        title = stringResource(R.string.settings_group_about_title),
        onNavigateBack = onNavigateBack
    ) {
        // APP VERSION & IDENTITY
        SettingsSection(title = stringResource(R.string.loc_about_application)) {
            SettingsActionRow(
                title = stringResource(R.string.settings_version_title),
                subtitle = stringResource(R.string.settings_about_details),
                icon = Icons.Default.Info
            )
        }

        // SENSOR HARDWARE ECOSYSTEM
        SettingsSection(title = stringResource(R.string.loc_supported_sensor_hardware)) {
            SettingsActionRow(
                title = "Abbott FreeStyle Libre",
                subtitle = stringResource(R.string.loc_libre_hardware_desc),
                icon = Icons.Default.Sensors
            )

            SettingsActionRow(
                title = "Dexcom CGM",
                subtitle = stringResource(R.string.loc_dexcom_hardware_desc),
                icon = Icons.Default.Sensors
            )

            SettingsActionRow(
                title = "Sibionics CGM",
                subtitle = stringResource(R.string.loc_sibionics_hardware_desc),
                icon = Icons.Default.Sensors
            )
        }

        // PRIVACY & ARCHITECTURE
        SettingsSection(title = stringResource(R.string.loc_privacy_open_source)) {
            SettingsActionRow(
                title = stringResource(R.string.loc_local_first_privacy),
                subtitle = stringResource(R.string.loc_local_first_privacy_desc),
                icon = Icons.Default.Security
            )

            SettingsActionRow(
                title = stringResource(R.string.loc_free_software),
                subtitle = stringResource(R.string.loc_license_desc),
                icon = Icons.Default.Favorite
            )
        }

        // INFO
        SettingsInfoCard(
            text = stringResource(R.string.loc_about_info),
            icon = Icons.Default.Info
        )
    }
}
