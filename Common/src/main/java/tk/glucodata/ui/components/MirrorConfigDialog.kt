package tk.glucodata.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.Applic
import tk.glucodata.MessageSender
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.SensorBridge
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.MirrorConnection

@Composable
fun MirrorConfigDialog(
    repository: GlucoseRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val connections by repository.mirrorConnections.collectAsState()

    val hostnames = remember {
        try { SensorBridge.getHostnames() } catch (_: Throwable) { arrayOf("null", "192.168.1.10", "null", "OK") }
    }
    val unavailableText = stringResource(R.string.dialog_ip_unavailable)
    val wlanIp = hostnames.getOrNull(1) ?: unavailableText
    val currentListenPort = remember {
        try { Natives.getreceiveport() ?: "17580" } catch (_: Throwable) { "17580" }
    }

    var staticNum by remember {
        mutableStateOf(try { Natives.staticnum() } catch (_: Throwable) { false })
    }

    // Quick Setup state for Side-by-Side testing
    var targetHost by remember { mutableStateOf("127.0.0.1") }
    var targetPort by remember { mutableStateOf("17580") }
    var connLabel by remember { mutableStateOf("Local Production App") }

    // Listen Port state
    var editListenPort by remember { mutableStateOf(currentListenPort) }

    // Delete confirmation state
    var connectionToDelete by remember { mutableStateOf<MirrorConnection?>(null) }

    LaunchedEffect(Unit) {
        repository.refreshMirrorConnections()
    }

    val connAddedMsg = stringResource(R.string.dialog_mirror_conn_added)
    val syncToggledMsg = stringResource(R.string.dialog_sync_toggled)
    val reinitSuccessMsg = stringResource(R.string.dialog_reinit_success)
    val portSavedMsg = stringResource(R.string.dialog_mirror_port_saved)
    val portInvalidMsg = stringResource(R.string.dialog_mirror_port_invalid)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.dialog_mirror_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_mirror_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. QUICK SETUP: SIDE-BY-SIDE LOCAL RELAY (127.0.0.1)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.dialog_mirror_quick_setup_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = stringResource(R.string.dialog_mirror_quick_setup_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = targetHost,
                                onValueChange = { targetHost = it },
                                label = { Text(stringResource(R.string.dialog_mirror_target_host), fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1.3f)
                            )
                            OutlinedTextField(
                                value = targetPort,
                                onValueChange = { targetPort = it },
                                label = { Text(stringResource(R.string.dialog_mirror_target_port), fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = connLabel,
                            onValueChange = { connLabel = it },
                            label = { Text(stringResource(R.string.dialog_mirror_conn_label), fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val ok = repository.addLocalReceiverConnection(targetPort, connLabel)
                                    if (ok) {
                                        Toast.makeText(context, connAddedMsg, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to add receiver", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.dialog_mirror_btn_receiver),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val ok = repository.addLocalSenderConnection(targetPort, connLabel)
                                    if (ok) {
                                        Toast.makeText(context, connAddedMsg, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to add sender", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.dialog_mirror_btn_sender),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // 2. CONFIGURED CONNECTIONS LIST
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dialog_mirror_active_list, connections.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (connections.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.dialog_mirror_no_conns),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        connections.forEach { conn ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (conn.isReceiver)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = conn.label.ifBlank { "Mirror Connection #${conn.index + 1}" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (conn.isReceiver)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.secondary
                                        ) {
                                            Text(
                                                text = if (conn.isReceiver)
                                                    stringResource(R.string.dialog_mirror_role_receiver_tag)
                                                else
                                                    stringResource(R.string.dialog_mirror_role_sender_tag),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    val ipText = if (conn.ips.isNotEmpty()) conn.ips.joinToString(", ") else "127.0.0.1"
                                    Text(
                                        text = "$ipText:${conn.port}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    if (conn.status.isNotBlank()) {
                                        Text(
                                            text = "Status: ${conn.status}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                repository.resetMirrorConnection(conn.index)
                                                Toast.makeText(context, syncToggledMsg, Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Sync,
                                                contentDescription = "Reset Sync",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        IconButton(
                                            onClick = { connectionToDelete = conn },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // 3. THIS DEVICE'S LISTEN PORT & WLAN IP
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.dialog_wlan_ip), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            Text(wlanIp, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.dialog_tcp_port), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                OutlinedTextField(
                                    value = editListenPort,
                                    onValueChange = { editListenPort = it },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    val ok = repository.setMirrorReceivePort(editListenPort)
                                    if (ok) {
                                        Toast.makeText(context, portSavedMsg.format(editListenPort), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, portInvalidMsg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(R.string.dialog_mirror_save_port))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.dialog_mirror_port_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                HorizontalDivider()

                // 4. LOCK LOG AMOUNTS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.dialog_lock_amounts), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.dialog_lock_amounts_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = staticNum,
                        onCheckedChange = {
                            staticNum = it
                            try {
                                Natives.setstaticnum(it)
                            } catch (_: Throwable) {}
                        }
                    )
                }

                HorizontalDivider()

                // 5. ADVANCED MIRROR SETUP (LEGACY UI / QR)
                OutlinedButton(
                    onClick = {
                        if (context is Activity) {
                            repository.openAdvancedMirrorView(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.dialog_mirror_legacy_btn), fontSize = 12.sp)
                }

                // 6. SYNC ACTIONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                Applic.switchSync()
                                repository.refreshMirrorConnections()
                                Toast.makeText(context, syncToggledMsg, Toast.LENGTH_SHORT).show()
                            } catch (e: Throwable) {
                                Toast.makeText(context, "Sync: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.dialog_trigger_sync))
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                MessageSender.reinit()
                                repository.refreshMirrorConnections()
                                Toast.makeText(context, reinitSuccessMsg, Toast.LENGTH_SHORT).show()
                            } catch (e: Throwable) {
                                Toast.makeText(context, "Reinit: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.dialog_reinit))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_done))
            }
        }
    )

    // Delete Confirmation Dialog
    connectionToDelete?.let { conn ->
        val connName = conn.label.ifBlank { "Host #${conn.index + 1}" }
        AlertDialog(
            onDismissRequest = { connectionToDelete = null },
            title = { Text("Delete Connection") },
            text = { Text(stringResource(R.string.dialog_mirror_delete_confirm, connName)) },
            confirmButton = {
                Button(
                    onClick = {
                        repository.deleteMirrorConnection(conn.index)
                        connectionToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { connectionToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
