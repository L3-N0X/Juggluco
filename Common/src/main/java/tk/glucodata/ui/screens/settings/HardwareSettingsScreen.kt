package tk.glucodata.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

@Composable
fun HardwareSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val hardwareConfig by repository.hardwareConfig.collectAsState()

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_group_hardware_title),
        onNavigateBack = onNavigateBack
    ) {
        // NFC SCANNING OPTIONS
        SettingsSection(title = "NFC scanner options") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_nfc_sound),
                subtitle = stringResource(R.string.settings_nfc_sound_desc),
                icon = Icons.Default.Nfc,
                checked = hardwareConfig.nfcSound,
                onCheckedChange = {
                    // Update NFC sound
                }
            )

            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.settings_nfc_launch),
                subtitle = stringResource(R.string.settings_nfc_launch_desc),
                icon = Icons.Default.TouchApp,
                checked = hardwareConfig.globalScanStartsApp,
                onCheckedChange = {
                    // Update global scan
                }
            )
        }

        // RADIO & HARDWARE PROTOCOLS
        SettingsSection(title = "Hardware interfacing protocols") {
            SettingsActionRow(
                title = "NFC sensor activation",
                subtitle = "Tap phone against sensor to begin warm-up and transfer encryption keys",
                icon = Icons.Default.Nfc
            )

            SettingsDivider()

            SettingsActionRow(
                title = "Bluetooth Low Energy (BLE)",
                subtitle = "Continuous readings are decrypted directly by Juggluco every minute without re-scanning",
                icon = Icons.Default.Bluetooth
            )
        }

        // TIPS
        SettingsInfoCard(
            text = "If NFC scans fail to register, verify that your phone's NFC toggle is switched on in Android System Settings and remove thick metal cases that might shield the internal antenna.",
            icon = Icons.Default.Info
        )
    }
}
