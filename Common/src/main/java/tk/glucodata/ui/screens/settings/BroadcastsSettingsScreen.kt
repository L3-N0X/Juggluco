package tk.glucodata.ui.screens.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        subtitle = stringResource(R.string.settings_cat_integrations),
        onNavigateBack = onNavigateBack
    ) {
        // LOCAL INTER-APP BROADCASTS
        SettingsCard(
            title = "Local App Broadcasts",
            icon = Icons.Default.CloudSync,
            categorySubtitle = "INTER-APP INTENTS"
        ) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_glucodata_broadcast),
                subtitle = stringResource(R.string.settings_glucodata_broadcast_desc),
                checked = exchanges.glucodataBroadcast,
                onCheckedChange = { repository.setGlucodataBroadcast(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            SettingsToggleRow(
                title = stringResource(R.string.settings_xdrip_broadcast),
                subtitle = stringResource(R.string.settings_xdrip_broadcast_desc),
                checked = exchanges.xdripBroadcast,
                onCheckedChange = { repository.setXdripBroadcast(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            SettingsToggleRow(
                title = stringResource(R.string.settings_librelink_broadcast),
                subtitle = stringResource(R.string.settings_librelink_broadcast_desc),
                checked = exchanges.librelinkBroadcast,
                onCheckedChange = { repository.setLibrelinkBroadcast(it) }
            )
        }

        // HEALTH CLOUD & SERVICES
        SettingsCard(
            title = "Cloud & System Health",
            categorySubtitle = "HEALTH ECOSYSTEM"
        ) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_health_connect),
                subtitle = stringResource(R.string.settings_health_connect_desc),
                checked = exchanges.healthConnect,
                onCheckedChange = { repository.setHealthConnect(it, context as? Activity) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_libreview),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_libreview_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = exchanges.libreViewEnabled,
                        onCheckedChange = { repository.setLibreViewEnabled(it) }
                    )
                    if (exchanges.libreViewEnabled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(onClick = onOpenLibreViewConfig) {
                            Text(stringResource(R.string.settings_btn_config), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // LOCAL REST WEB SERVER
        SettingsCard(
            title = stringResource(R.string.settings_xdrip_server),
            icon = Icons.Default.Code,
            categorySubtitle = "EMBEDDED WEB SERVER"
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_xdrip_server),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_xdrip_server_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = exchanges.xdripWebServer,
                        onCheckedChange = { repository.setXdripWebServer(it) }
                    )
                    if (exchanges.xdripWebServer) {
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(onClick = onOpenWebServerConfig) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.settings_btn_config), fontSize = 11.sp)
                        }
                    }
                }
            }

            if (exchanges.xdripWebServer) {
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "URL: http://127.0.0.1:${exchanges.webServerPort}/",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onOpenWebServerConfig) {
                            Text("Endpoints", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // INFO
        SettingsInfoCard(
            text = "Broadcasts allow local companion apps such as xDrip+, AndroidAPS, and smartwatch faces to receive real-time glucose values as soon as Juggluco receives them.",
            icon = Icons.Default.Info
        )
    }
}
