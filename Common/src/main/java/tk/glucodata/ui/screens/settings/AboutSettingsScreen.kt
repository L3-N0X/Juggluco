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
        SettingsSection(title = "Application") {
            SettingsActionRow(
                title = stringResource(R.string.settings_version_title),
                subtitle = stringResource(R.string.settings_about_details),
                icon = Icons.Default.Info
            )
        }

        // SENSOR HARDWARE ECOSYSTEM
        SettingsSection(title = "Supported sensor hardware") {
            SettingsActionRow(
                title = "Abbott FreeStyle Libre",
                subtitle = "Libre 1, Libre 2 (European & US), and Libre 3 with direct BLE streaming & NFC scans",
                icon = Icons.Default.Sensors
            )

            SettingsDivider()

            SettingsActionRow(
                title = "Dexcom CGM",
                subtitle = "Dexcom G7 and Dexcom ONE+ direct Bluetooth Low Energy connectivity",
                icon = Icons.Default.Sensors
            )

            SettingsDivider()

            SettingsActionRow(
                title = "Sibionics CGM",
                subtitle = "Continuous sensor readings and Bluetooth telemetry",
                icon = Icons.Default.Sensors
            )
        }

        // PRIVACY & ARCHITECTURE
        SettingsSection(title = "Privacy & open source") {
            SettingsActionRow(
                title = "Local-first privacy philosophy",
                subtitle = "100% offline-capable. Medical records never leave your phone without explicit third-party service setup.",
                icon = Icons.Default.Security
            )

            SettingsDivider()

            SettingsActionRow(
                title = "Free software (GPLv3)",
                subtitle = "Licensed under GNU General Public License v3. Created by Jaap Korthals Altes.",
                icon = Icons.Default.Favorite
            )
        }

        // INFO
        SettingsInfoCard(
            text = "Juggluco gives you full control and ownership of your continuous glucose monitor data.",
            icon = Icons.Default.Info
        )
    }
}
