package tk.glucodata.ui.screens.settings

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import tk.glucodata.ui.model.MirrorConnection

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Router

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
        subtitle = stringResource(R.string.settings_cat_dev_testing),
        onNavigateBack = onNavigateBack
    ) {
        // STATUS OVERVIEW CARD
        SettingsCard(
            title = "Device Role & Sync State",
            icon = Icons.Default.Devices,
            categorySubtitle = "NETWORK STATE"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isReceiverActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = roleText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isReceiverActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Listen Port: $currentListenPort",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dialog_wlan_ip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = wlanIp,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // LISTEN PORT CONFIGURATION CARD
        SettingsCard(
            title = "TCP Listen Port",
            categorySubtitle = "SOCKET CONFIGURATION"
        ) {
            Text(
                text = stringResource(R.string.dialog_mirror_port_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.dialog_mirror_save_port))
                }
            }
        }

        // QUICK SETUP: SIDE-BY-SIDE LOCAL RELAY
        SettingsCard(
            title = stringResource(R.string.dialog_mirror_quick_setup_title),
            icon = Icons.Default.Sync,
            categorySubtitle = "LOCAL RELAY (127.0.0.1)"
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

        // CONFIGURED CONNECTIONS LIST
        SettingsCard(
            title = stringResource(R.string.dialog_mirror_active_list, connections.size),
            categorySubtitle = "PEER CONNECTIONS"
        ) {
            if (connections.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.dialog_mirror_no_conns),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    connections.forEach { conn ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenConnectionEdit(conn.index) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (conn.isReceiver)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = conn.label.ifBlank { "Connection #${conn.index + 1}" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (conn.isReceiver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                    ) {
                                        Text(
                                            text = if (conn.isReceiver) stringResource(R.string.dialog_mirror_role_receiver_tag) else stringResource(R.string.dialog_mirror_role_sender_tag),
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

                                Spacer(modifier = Modifier.height(6.dp))

                                // Inline Deletion Prompt if active
                                if (pendingDeleteIndex == conn.index) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Confirm delete?",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(onClick = { pendingDeleteIndex = -1 }) {
                                                    Text(stringResource(R.string.cancel))
                                                }
                                                Button(
                                                    onClick = {
                                                        repository.deleteMirrorConnection(conn.index)
                                                        pendingDeleteIndex = -1
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
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { onOpenConnectionEdit(conn.index) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Connection",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

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
                                            onClick = { pendingDeleteIndex = conn.index },
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sync Control Buttons
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

        // LOCK LOG AMOUNTS & LEGACY TOOLS
        SettingsCard(
            title = "Security & Legacy Setup",
            categorySubtitle = "DATA INTEGRITY"
        ) {
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            OutlinedButton(
                onClick = { onOpenConnectionEdit(-1) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Custom Connection", fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenTurnServerConfig,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Router, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.settings_title_turn_server), fontSize = 12.sp)
            }
        }

        // DEV TESTING GUIDE (EXPANDABLE)
        SettingsCard(
            title = stringResource(R.string.settings_dev_guide_title),
            icon = Icons.Default.Info,
            categorySubtitle = "DEVELOPER INSTRUCTIONS"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "How to relay live data side-by-side",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
