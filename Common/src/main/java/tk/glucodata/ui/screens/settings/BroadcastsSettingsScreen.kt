package tk.glucodata.ui.screens.settings

import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

@Composable
fun BroadcastsSettingsScreen(
    repository: GlucoseRepository,
    onOpenWebServerConfig: () -> Unit = {},
    onOpenLibreViewConfig: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val exchanges by repository.exchanges.collectAsState()
    val context = LocalContext.current

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_group_broadcasts_title),
        onNavigateBack = onNavigateBack
    ) {
        // LOCAL INTER-APP BROADCASTS
        SettingsSection(title = "Local app broadcasts") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_glucodata_broadcast),
                subtitle = stringResource(R.string.settings_glucodata_broadcast_desc),
                icon = Icons.Default.CloudSync,
                checked = exchanges.glucodataBroadcast,
                onCheckedChange = { repository.setGlucodataBroadcast(it) }
            )

            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.settings_xdrip_broadcast),
                subtitle = stringResource(R.string.settings_xdrip_broadcast_desc),
                icon = Icons.Default.CloudSync,
                checked = exchanges.xdripBroadcast,
                onCheckedChange = { repository.setXdripBroadcast(it) }
            )

            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.settings_librelink_broadcast),
                subtitle = stringResource(R.string.settings_librelink_broadcast_desc),
                icon = Icons.Default.CloudSync,
                checked = exchanges.librelinkBroadcast,
                onCheckedChange = { repository.setLibrelinkBroadcast(it) }
            )
        }

        // HEALTH CLOUD & LOCAL SERVERS
        SettingsSection(title = "Cloud & local servers") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_health_connect),
                subtitle = stringResource(R.string.settings_health_connect_desc),
                icon = Icons.Default.CloudUpload,
                checked = exchanges.healthConnect,
                onCheckedChange = { repository.setHealthConnect(it, context as? Activity) }
            )

            SettingsDivider()

            SettingsNavRow(
                title = stringResource(R.string.settings_libreview),
                subtitle = stringResource(R.string.settings_libreview_desc),
                icon = Icons.Default.CloudUpload,
                checked = exchanges.libreViewEnabled,
                onCheckedChange = { repository.setLibreViewEnabled(it) },
                onClick = onOpenLibreViewConfig
            )

            SettingsDivider()

            SettingsNavRow(
                title = stringResource(R.string.settings_xdrip_server),
                subtitle = "Local REST API on port ${exchanges.webServerPort}",
                icon = Icons.Default.Code,
                checked = exchanges.xdripWebServer,
                onCheckedChange = { repository.setXdripWebServer(it) },
                onClick = onOpenWebServerConfig
            )
        }

        // INFO
        SettingsInfoCard(
            text = "Broadcasts allow local companion apps such as xDrip+, AndroidAPS, and smartwatch faces to receive real-time glucose values as soon as Juggluco receives them.",
            icon = Icons.Default.Info
        )
    }
}
