package tk.glucodata.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
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

        // HARDWARE PROTOCOLS & TIPS (COMMENT)
        SettingsInfoCard(icon = Icons.Default.Info) {
            Text(
                text = "Hardware interfacing protocols",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                        append("NFC sensor activation: ")
                    }
                    append("Tap phone against sensor to begin warm-up and transfer encryption keys.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                        append("Bluetooth Low Energy (BLE): ")
                    }
                    append("Continuous readings are decrypted directly by Juggluco every minute without re-scanning.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Text(
                text = "If NFC scans fail to register, verify that your phone's NFC toggle is switched on in Android System Settings and remove thick metal cases that might shield the internal antenna.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )
        }
    }
}
