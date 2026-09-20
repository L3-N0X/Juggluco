package tk.glucodata.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        subtitle = stringResource(R.string.settings_cat_hardware),
        onNavigateBack = onNavigateBack
    ) {
        // NFC SCANNING OPTIONS
        SettingsCard(
            title = stringResource(R.string.settings_card_hardware),
            icon = Icons.Default.Nfc,
            categorySubtitle = "NFC SCANNER"
        ) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_nfc_sound),
                subtitle = stringResource(R.string.settings_nfc_sound_desc),
                checked = hardwareConfig.nfcSound,
                onCheckedChange = {
                    // Update NFC sound
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            SettingsToggleRow(
                title = stringResource(R.string.settings_nfc_launch),
                subtitle = stringResource(R.string.settings_nfc_launch_desc),
                checked = hardwareConfig.globalScanStartsApp,
                onCheckedChange = {
                    // Update global scan
                }
            )
        }

        // BLUETOOTH LE & HARDWARE SCANNING INFO
        SettingsCard(
            title = "Hardware Sensor Interfacing",
            icon = Icons.Default.Bluetooth,
            categorySubtitle = "RADIO PROTOCOLS"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "NFC Sensor Activation",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Holding the upper-back area of your phone against your sensor starts the warm-up period and transfers encryption keys. Once paired, readings stream continuously via Bluetooth Low Energy without needing further NFC scans.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Bluetooth Low Energy (BLE)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Continuous readings are decrypted directly by Juggluco's native C++ engine every minute. Bluetooth must remain enabled on your phone at all times.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // TIPS
        SettingsInfoCard(
            text = "If NFC scans fail to register, verify that your phone's NFC toggle is switched on in Android System Settings and remove thick metal cases that might shield the internal NFC antenna.",
            icon = Icons.Default.Info
        )
    }
}
