package tk.glucodata.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tk.glucodata.BleMirror
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.MirrorHostEditState
import tk.glucodata.ui.screens.ScreenLayout

private data class MirrorConnectionEditor(
    val snapshot: MirrorHostEditState?,
    val initialLabel: String,
    val initialHost: String,
    val initialPort: String,
    val initialIsReceiver: Boolean,
    val initialSendStream: Boolean,
    val initialSendScans: Boolean,
    val initialSendAmounts: Boolean,
    val label: String = initialLabel,
    val hostIp: String = initialHost,
    val port: String = initialPort,
    val isReceiver: Boolean = initialIsReceiver,
    val sendStream: Boolean = initialSendStream,
    val sendScans: Boolean = initialSendScans,
    val sendAmounts: Boolean = initialSendAmounts
) {
    val hasSnapshot: Boolean get() = snapshot != null
    val addressEditable: Boolean
        get() {
            val state = snapshot ?: return true
            if (state.isIce) return false
            return state.transport == BleMirror.TRANSPORT_AUTOMATIC ||
                    state.transport == BleMirror.TRANSPORT_TCP
        }

    val changedFields: Int
        get() {
            var mask = 0
            if (label.trim() != initialLabel) mask = mask or Natives.MIRRORFIELD_LABEL
            if (hostIp.trim() != initialHost) mask = mask or Natives.MIRRORFIELD_IPS
            if (port.trim() != initialPort) mask = mask or Natives.MIRRORFIELD_PORT
            if (isReceiver != initialIsReceiver) mask = mask or Natives.MIRRORFIELD_RECEIVEFROM
            if (sendStream != initialSendStream) mask = mask or Natives.MIRRORFIELD_SENDSTREAM
            if (sendScans != initialSendScans) mask = mask or Natives.MIRRORFIELD_SENDSCANS
            if (sendAmounts != initialSendAmounts) mask = mask or Natives.MIRRORFIELD_SENDNUMS
            return mask
        }
}

@Composable
fun MirrorConnectionEditScreen(
    repository: GlucoseRepository,
    connectionIndex: Int = -1, // -1 means adding new
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val connections by repository.mirrorConnections.collectAsState()
    val existingConn = connections.find { it.index == connectionIndex }

    val isEditing = connectionIndex >= 0
    val localProductionLabel = stringResource(R.string.loc_local_production_app)
    val screenTitle = stringResource(
        if (isEditing) R.string.loc_mirror_edit_title else R.string.loc_mirror_edit_add
    )
    val scope = rememberCoroutineScope()
    var editor by remember(connectionIndex) { mutableStateOf<MirrorConnectionEditor?>(null) }
    var loadFailed by remember(connectionIndex) { mutableStateOf(false) }
    var showDeleteConfirm by remember(connectionIndex) { mutableStateOf(false) }
    var saving by remember(connectionIndex) { mutableStateOf(false) }

    LaunchedEffect(connectionIndex) {
        val snapshot = if (isEditing) repository.mirrorHostEditState(connectionIndex) else null
        if (isEditing && snapshot == null) {
            loadFailed = true
            return@LaunchedEffect
        }
        val storedLabel = snapshot?.label?.trim() ?: ""
        val storedHost = snapshot?.ips?.firstOrNull { it.isNotBlank() } ?: "127.0.0.1"
        val storedPort = snapshot?.port?.takeIf { it.isNotBlank() } ?: "17580"
        editor = MirrorConnectionEditor(
            snapshot = snapshot,
            initialLabel = storedLabel.ifEmpty {
                if (isEditing) "" else localProductionLabel
            },
            initialHost = storedHost,
            initialPort = storedPort,
            initialIsReceiver = snapshot?.isReceiver ?: true,
            initialSendStream = snapshot?.sendStream ?: true,
            initialSendScans = snapshot?.sendScans ?: true,
            initialSendAmounts = snapshot?.sendAmounts ?: true
        )
    }

    val state = editor
    SettingsDetailScaffold(
        title = screenTitle,
        onNavigateBack = onNavigateBack
    ) {
        if (state == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (loadFailed) {
                    Text(
                        text = stringResource(R.string.loc_failed_load_connection),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
            return@SettingsDetailScaffold
        }

        SettingsSection(title = stringResource(R.string.loc_target_host_network)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp)
            ) {
                OutlinedTextField(
                    value = state.label,
                    onValueChange = { editor = state.copy(label = it) },
                    label = { Text(stringResource(R.string.loc_connection_label)) },
                    placeholder = { Text(stringResource(R.string.loc_connection_label_example)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.hostIp,
                        onValueChange = { editor = state.copy(hostIp = it) },
                        enabled = state.addressEditable,
                        label = { Text(stringResource(R.string.loc_target_ip_hostname)) },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )

                    OutlinedTextField(
                        value = state.port,
                        onValueChange = { editor = state.copy(port = it) },
                        enabled = state.addressEditable,
                        label = { Text(stringResource(R.string.port)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.9f)
                    )
                }

                if (state.addressEditable) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("127.0.0.1", "192.168.1.", "10.0.2.2").forEach { preset ->
                            FilterChip(
                                selected = state.hostIp == preset,
                                onClick = { editor = state.copy(hostIp = preset) },
                                label = { Text(preset, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        SettingsSection(title = stringResource(R.string.loc_device_role_data)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.isReceiver,
                        onClick = { editor = state.copy(isReceiver = true) },
                        label = { Text(stringResource(R.string.loc_receiver_role), fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = !state.isReceiver,
                        onClick = { editor = state.copy(isReceiver = false) },
                        label = { Text(stringResource(R.string.loc_sender_role), fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(
                        if (state.isReceiver) R.string.loc_receiver_role_desc else R.string.loc_sender_role_desc
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsSwitchRow(
                title = stringResource(R.string.loc_continuous_stream),
                subtitle = stringResource(R.string.loc_continuous_stream_desc),
                icon = Icons.Default.Timeline,
                checked = state.sendStream,
                onCheckedChange = { editor = state.copy(sendStream = it) }
            )

            SettingsSwitchRow(
                title = stringResource(R.string.loc_manual_nfc_scans),
                subtitle = stringResource(R.string.loc_manual_nfc_scans_desc),
                icon = Icons.Default.Nfc,
                checked = state.sendScans,
                onCheckedChange = { editor = state.copy(sendScans = it) }
            )

            SettingsSwitchRow(
                title = stringResource(R.string.loc_insulin_carb_amounts),
                subtitle = stringResource(R.string.loc_insulin_carb_amounts_desc),
                icon = Icons.Default.Sync,
                checked = state.sendAmounts,
                onCheckedChange = { editor = state.copy(sendAmounts = it) }
            )
        }

        if (existingConn != null && existingConn.status.isNotBlank()) {
            SettingsSection(title = stringResource(R.string.loc_connection_diagnostics)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp)
                ) {
                    val annotated = remember(existingConn.status) {
                        htmlToAnnotatedString(existingConn.status)
                    }
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        SettingsSection(title = stringResource(R.string.loc_common_actions)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp)
            ) {
                if (showDeleteConfirm) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.loc_delete_connection_confirm),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        if (isEditing) {
                                            repository.deleteMirrorConnection(connectionIndex)
                                        }
                                        Toast.makeText(context, context.getString(R.string.loc_connection_deleted), Toast.LENGTH_SHORT).show()
                                        onNavigateBack()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isEditing) {
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.weight(0.8f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                if (saving) return@Button
                                val cleanPort = state.port.trim()
                                if (!isEditing || state.addressEditable) {
                                    val portNum = cleanPort.toIntOrNull()
                                    if (portNum == null || portNum !in 1024..65535) {
                                        Toast.makeText(context, context.getString(R.string.loc_invalid_mirror_port), Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                }
                                if (isEditing && !state.hasSnapshot) {
                                    Toast.makeText(context, context.getString(R.string.loc_failed_load_connection), Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (!state.isReceiver && !state.sendStream && !state.sendScans && !state.sendAmounts) {
                                    Toast.makeText(context, context.getString(R.string.specifyreceiveordata), Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                saving = true
                                scope.launch {
                                    val ok = repository.saveMirrorConnection(
                                        index = if (isEditing) connectionIndex else -1,
                                        ips = listOf(state.hostIp.trim()),
                                        port = cleanPort,
                                        isReceiver = state.isReceiver,
                                        label = state.label.trim(),
                                        sendStream = state.sendStream,
                                        sendScans = state.sendScans,
                                        sendAmounts = state.sendAmounts,
                                        changedFields = if (isEditing) state.changedFields else 0
                                    )
                                    saving = false
                                    if (ok) {
                                        Toast.makeText(context, context.getString(R.string.loc_connection_saved), Toast.LENGTH_SHORT).show()
                                        onNavigateBack()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.loc_failed_save_connection), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        SettingsInfoCard(
            text = stringResource(R.string.loc_mirror_testing_info),
            icon = Icons.Default.Info
        )
    }
}
