package tk.glucodata.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
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

@Composable
fun MirrorSettingsScreen(
    repository: GlucoseRepository,
    onOpenConnectionEdit: (Int) -> Unit = {},
    onOpenTurnServerConfig: () -> Unit = {},
    onNavigateBack: () -> Unit
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

    // Quick Setup state
    var targetHost by remember { mutableStateOf("127.0.0.1") }
    var targetPort by remember { mutableStateOf("17580") }
    var connLabel by remember { mutableStateOf("Local Production App") }

    // Listen Port state
    var editListenPort by remember { mutableStateOf(currentListenPort) }

    // Inline deletion confirmation state
    var pendingDeleteIndex by remember { mutableIntStateOf(-1) }

    // Dev guide expanded state
    var showGuideExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.refreshMirrorConnections()
    }

    val connAddedMsg = stringResource(R.string.dialog_mirror_conn_added)
    val syncToggledMsg = stringResource(R.string.dialog_sync_toggled)
    val reinitSuccessMsg = stringResource(R.string.dialog_reinit_success)
    val portSavedMsg = stringResource(R.string.dialog_mirror_port_saved)
    val portInvalidMsg = stringResource(R.string.dialog_mirror_port_invalid)

    val isReceiverActive = connections.any { it.isReceiver }
    val isSenderActive = connections.any { !it.isReceiver }
    val roleText = when {
        isReceiverActive -> stringResource(R.string.settings_dev_role_receiver)
        isSenderActive -> stringResource(R.string.settings_dev_role_sender)
        else -> stringResource(R.string.settings_dev_role_standalone)
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_group_mirror_title),
        onNavigateBack = onNavigateBack
    ) {
        // RELAY & PROXY SERVERS
        SettingsSection(title = "Relay servers") {
            SettingsNavRow(
                title = stringResource(R.string.settings_title_turn_server),
                subtitle = stringResource(R.string.settings_desc_turn_server),
                icon = Icons.Default.Router,
                onClick = onOpenTurnServerConfig
            )
        }

        // DEVICE ROLE & NETWORK STATUS
        SettingsSection(title = "Device network role") {
            SettingsActionRow(
                title = "Synchronization role",
                subtitle = "$roleText • Listen port: $currentListenPort",
                icon = Icons.Default.Devices
            )

            SettingsDivider()

            SettingsActionRow(
                title = stringResource(R.string.dialog_wlan_ip),
                subtitle = wlanIp,
                icon = Icons.Default.Wifi
            )

            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.dialog_lock_amounts),
                subtitle = stringResource(R.string.dialog_lock_amounts_desc),
                icon = Icons.Default.Lock,
                checked = staticNum,
                onCheckedChange = {
                    staticNum = it
                    try {
                        Natives.setstaticnum(it)
                    } catch (_: Throwable) {}
                }
            )
        }

        // LISTEN PORT
        SettingsSection(title = "TCP socket listen port") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_mirror_port_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editListenPort,
                        onValueChange = { editListenPort = it },
                        label = { Text("Listen Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val ok = repository.setMirrorReceivePort(editListenPort)
                            if (ok) {
                                Toast.makeText(context, portSavedMsg.format(editListenPort), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, portInvalidMsg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.dialog_mirror_save_port))
                    }
                }
            }
        }

        // CONFIGURED CONNECTIONS
        SettingsSection(title = "Peer connections (${connections.size})") {
            SettingsActionRow(
                title = "Add custom connection",
                subtitle = "Configure new sender or receiver peer address",
                icon = Icons.Default.Add,
                onClick = { onOpenConnectionEdit(-1) }
            )

            if (connections.isEmpty()) {
                SettingsDivider()
                Text(
                    text = stringResource(R.string.dialog_mirror_no_conns),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            } else {
                connections.forEach { conn ->
                    SettingsDivider()
                    val ipText = if (conn.ips.isNotEmpty()) conn.ips.joinToString(", ") else "127.0.0.1"
                    val roleLabel = if (conn.isReceiver) "Receiver" else "Sender"

                    SettingsNavRow(
                        title = conn.label.ifBlank { "Connection #${conn.index + 1}" },
                        subtitle = "$ipText:${conn.port} • $roleLabel ${if (conn.status.isNotBlank()) "(${conn.status})" else ""}",
                        icon = Icons.Default.Devices,
                        onClick = { onOpenConnectionEdit(conn.index) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
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
                        Text(stringResource(R.string.dialog_trigger_sync), fontSize = 12.sp)
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
                        Text(stringResource(R.string.dialog_reinit), fontSize = 12.sp)
                    }
                }
            }
        }

        // QUICK SETUP: SIDE-BY-SIDE LOCAL RELAY
        SettingsSection(title = "Quick setup (127.0.0.1 local relay)") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_mirror_quick_setup_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

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

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = connLabel,
                    onValueChange = { connLabel = it },
                    label = { Text(stringResource(R.string.dialog_mirror_conn_label), fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

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
                        shape = RoundedCornerShape(10.dp)
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
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_mirror_btn_sender),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // DEV TESTING GUIDE (EXPANDABLE)
        SettingsSection(title = "Developer testing instructions") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showGuideExpanded = !showGuideExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIcon(icon = Icons.Default.Info)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "How to relay live data side-by-side",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(onClick = { showGuideExpanded = !showGuideExpanded }) {
                        Icon(
                            imageVector = if (showGuideExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Guide"
                        )
                    }
                }

                AnimatedVisibility(visible = showGuideExpanded) {
                    val guideHtml = stringResource(R.string.settings_dev_guide_body)
                    val spanned = remember(guideHtml) {
                        androidx.core.text.HtmlCompat.fromHtml(guideHtml, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT)
                    }
                    Text(
                        text = spanned.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
    }
}
