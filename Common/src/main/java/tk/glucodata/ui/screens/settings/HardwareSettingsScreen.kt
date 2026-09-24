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
    val nfcActivation = stringResource(R.string.loc_nfc_activation)
    val nfcActivationDesc = stringResource(R.string.loc_nfc_activation_desc)
    val bleHeading = stringResource(R.string.loc_ble_heading)
    val bleDesc = stringResource(R.string.loc_ble_desc)

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_group_hardware_title),
        onNavigateBack = onNavigateBack
    ) {
        // NFC SCANNING OPTIONS
        SettingsSection(title = stringResource(R.string.loc_nfc_scanner_options)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_nfc_sound),
                subtitle = stringResource(R.string.settings_nfc_sound_desc),
                icon = Icons.Default.Nfc,
                checked = hardwareConfig.nfcSound,
                onCheckedChange = { enabled ->
                    repository.setNfcSound(enabled)
                }
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_nfc_launch),
                subtitle = stringResource(R.string.settings_nfc_launch_desc),
                icon = Icons.Default.TouchApp,
                checked = hardwareConfig.globalScanStartsApp,
                onCheckedChange = { enabled ->
                    repository.setNfcLaunchEnabled(enabled)
                }
            )
        }

        // HARDWARE PROTOCOLS & TIPS (COMMENT)
        SettingsInfoCard(icon = Icons.Default.Info) {
            Text(
                text = stringResource(R.string.loc_hardware_protocols),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                        append(nfcActivation)
                    }
                    append(nfcActivationDesc)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                        append(bleHeading)
                    }
                    append(bleDesc)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Text(
                text = stringResource(R.string.loc_nfc_troubleshoot),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )
        }
    }
}
