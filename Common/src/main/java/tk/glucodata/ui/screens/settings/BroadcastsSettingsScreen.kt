package tk.glucodata.ui.screens.settings

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.BroadcastReceiverApp

@Composable
fun BroadcastsSettingsScreen(
    repository: GlucoseRepository,
    onOpenWebServerConfig: () -> Unit = {},
    onOpenLibreViewConfig: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val exchanges by repository.exchanges.collectAsState()
    val xdripReceiverApps by repository.xdripReceiverApps.collectAsState()
    val xdripReceiverAppsLoading by repository.xdripReceiverAppsLoading.collectAsState()
    val glucodataReceiverApps by repository.glucodataReceiverApps.collectAsState()
    val glucodataReceiverAppsLoading by repository.glucodataReceiverAppsLoading.collectAsState()
    val context = LocalContext.current
    var showXdripReceiverDialog by remember { mutableStateOf(false) }
    var showGlucodataReceiverDialog by remember { mutableStateOf(false) }
    val selectedXdripReceiverPackages = remember(exchanges.xdripReceiverPackages) {
        exchanges.xdripReceiverPackages.toSet()
    }
    val selectedGlucodataReceiverPackages = remember(exchanges.glucodataReceiverPackages) {
        exchanges.glucodataReceiverPackages.toSet()
    }

    LaunchedEffect(showXdripReceiverDialog) {
        if (showXdripReceiverDialog) {
            repository.refreshXdripReceiverApps()
        }
    }
    LaunchedEffect(showGlucodataReceiverDialog) {
        if (showGlucodataReceiverDialog) {
            repository.refreshGlucodataReceiverApps()
        }
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_group_broadcasts_title),
        onNavigateBack = onNavigateBack
    ) {
        // LOCAL INTER-APP BROADCASTS
        SettingsSection(title = stringResource(R.string.loc_local_app_broadcasts)) {
            SettingsNavRow(
                title = stringResource(R.string.settings_glucodata_broadcast),
                subtitle = stringResource(R.string.settings_glucodata_broadcast_desc),
                icon = Icons.Default.CloudSync,
                checked = exchanges.glucodataBroadcast,
                onCheckedChange = { showGlucodataReceiverDialog = true },
                onClick = { showGlucodataReceiverDialog = true }
            )

            SettingsNavRow(
                title = stringResource(R.string.settings_xdrip_broadcast),
                subtitle = stringResource(R.string.settings_xdrip_broadcast_desc),
                icon = Icons.Default.CloudSync,
                checked = exchanges.xdripBroadcast,
                onCheckedChange = { showXdripReceiverDialog = true },
                onClick = { showXdripReceiverDialog = true }
            )

            SettingsSwitchRow(
                title = stringResource(R.string.settings_librelink_broadcast),
                subtitle = stringResource(R.string.settings_librelink_broadcast_desc),
                icon = Icons.Default.CloudSync,
                checked = exchanges.librelinkBroadcast,
                onCheckedChange = { repository.setLibrelinkBroadcast(it) }
            )
        }

        // HEALTH CLOUD & LOCAL SERVERS
        SettingsSection(title = stringResource(R.string.loc_cloud_local_servers)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_health_connect),
                subtitle = stringResource(R.string.settings_health_connect_desc),
                icon = Icons.Default.CloudUpload,
                checked = exchanges.healthConnect,
                onCheckedChange = { repository.setHealthConnect(it, context as? Activity) }
            )

            SettingsNavRow(
                title = stringResource(R.string.settings_libreview),
                subtitle = stringResource(R.string.settings_libreview_desc),
                icon = Icons.Default.CloudUpload,
                checked = exchanges.libreViewEnabled,
                onCheckedChange = { repository.setLibreViewEnabled(it) },
                onClick = onOpenLibreViewConfig
            )

            SettingsNavRow(
                title = stringResource(R.string.settings_xdrip_server),
                subtitle = stringResource(R.string.loc_rest_api_port, exchanges.webServerPort),
                icon = Icons.Default.Code,
                checked = exchanges.xdripWebServer,
                onCheckedChange = { repository.setXdripWebServer(it) },
                onClick = onOpenWebServerConfig
            )
        }

        // INFO
        SettingsInfoCard(
            text = stringResource(R.string.loc_broadcast_info),
            icon = Icons.Default.Info
        )
    }

    if (showGlucodataReceiverDialog) {
        BroadcastReceiverDialog(
            title = stringResource(R.string.loc_glucodata_receiver_dialog_title),
            emptyMessage = stringResource(R.string.loc_no_glucodata_receivers),
            receiverApps = glucodataReceiverApps,
            selectedPackages = selectedGlucodataReceiverPackages,
            isLoading = glucodataReceiverAppsLoading,
            onSave = { packages ->
                repository.setGlucodataReceivers(packages)
                showGlucodataReceiverDialog = false
            },
            onDismiss = { showGlucodataReceiverDialog = false }
        )
    }

    if (showXdripReceiverDialog) {
        BroadcastReceiverDialog(
            title = stringResource(R.string.loc_xdrip_receiver_dialog_title),
            emptyMessage = stringResource(R.string.loc_no_xdrip_receivers),
            receiverApps = xdripReceiverApps,
            selectedPackages = selectedXdripReceiverPackages,
            isLoading = xdripReceiverAppsLoading,
            onSave = { packages ->
                repository.setXdripReceivers(packages)
                showXdripReceiverDialog = false
            },
            onDismiss = { showXdripReceiverDialog = false }
        )
    }
}

@Composable
private fun BroadcastReceiverDialog(
    title: String,
    emptyMessage: String,
    receiverApps: List<BroadcastReceiverApp>,
    selectedPackages: Set<String>,
    isLoading: Boolean,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedReceiverPackages by remember(selectedPackages, receiverApps) {
        mutableStateOf(selectedPackages)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                receiverApps.isEmpty() -> {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    )
                }

                else -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(receiverApps, key = { it.packageName }) { receiverApp ->
                                val isSelected = receiverApp.packageName in selectedReceiverPackages
                                val selectionLimitReached = selectedReceiverPackages.size >= 10 && !isSelected
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(enabled = !selectionLimitReached) {
                                            if (isSelected) {
                                                selectedReceiverPackages -= receiverApp.packageName
                                            } else {
                                                selectedReceiverPackages += receiverApp.packageName
                                            }
                                        },
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    selectedReceiverPackages += receiverApp.packageName
                                                } else {
                                                    selectedReceiverPackages -= receiverApp.packageName
                                                }
                                            },
                                            enabled = !selectionLimitReached
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = receiverApp.label,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            if (receiverApp.label != receiverApp.packageName || !receiverApp.installed) {
                                                Text(
                                                    text = if (receiverApp.installed) {
                                                        receiverApp.packageName
                                                    } else {
                                                        stringResource(R.string.loc_receiver_not_installed, receiverApp.packageName)
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.loc_receiver_limit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val orderedPackages = receiverApps
                        .map { it.packageName }
                        .filter { it in selectedReceiverPackages }
                    onSave(orderedPackages)
                },
                enabled = receiverApps.isNotEmpty() && !isLoading
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
