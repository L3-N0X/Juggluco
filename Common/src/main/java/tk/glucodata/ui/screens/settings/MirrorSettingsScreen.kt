package tk.glucodata.ui.screens.settings

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tk.glucodata.Applic
import tk.glucodata.BleMirror
import tk.glucodata.MainActivity
import tk.glucodata.MessageSender
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.SensorBridge
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.MirrorConnection
import tk.glucodata.ui.model.MirrorImportPreview
import tk.glucodata.ui.model.MirrorQuickCode
import tk.glucodata.ui.screens.ScreenLayout

fun getMirrorConnectionStatusSummary(conn: MirrorConnection): String {
    val context = Applic.getContext()
    if (conn.isDeactivated) return context.getString(R.string.loc_mirror_status_disabled)
    val raw = conn.status
    if (raw.isBlank()) return if (conn.isActive) context.getString(R.string.loc_state_active) else ""

    val errorMatch = Regex("""\w{3}\s+\w{3}\s+\d+\s+[\d:]+\s+\d{4}:\s*(.+)""").find(raw)
    if (errorMatch != null) {
        val err = errorMatch.groupValues[1].trim().removeSuffix(":")
        if (err.isNotBlank()) return context.getString(R.string.loc_mirror_status_error, err)
    }

    val isTcpLive = raw.contains("live socket: true", ignoreCase = true) ||
            raw.contains("TCP/IP live socket</b>: true", ignoreCase = true)
    val isBleLive = raw.contains("Direct Bluetooth (BLE GATT)=true", ignoreCase = true)
    val isWearLive = raw.contains("Messages (Wear OS MessageClient)=true", ignoreCase = true)

    if (isTcpLive || isBleLive || isWearLive) {
        return context.getString(R.string.loc_connected)
    }

    if (raw.contains("running=true", ignoreCase = true) ||
        raw.contains("wait for commands: true", ignoreCase = true) ||
        raw.contains("wait for commands:true", ignoreCase = true)
    ) {
        return context.getString(if (conn.isReceiver) R.string.loc_mirror_status_listening else R.string.loc_mirror_status_connecting)
    }

    if (raw.contains("running=false", ignoreCase = true) ||
        raw.contains("live socket: false", ignoreCase = true) ||
        raw.contains("No active carrier", ignoreCase = true)
    ) {
        return context.getString(R.string.loc_mirror_status_disconnected)
    }

    return if (conn.isActive) context.getString(R.string.loc_state_active) else ""
}

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
    val localProductionLabel = stringResource(R.string.loc_local_production_app)
    var connLabel by remember(localProductionLabel) { mutableStateOf(localProductionLabel) }

    // Listen Port state
    var editListenPort by remember { mutableStateOf(currentListenPort) }

    // Connection code import and quick code creation
    val codesSupported = repository.mirrorCodesSupported()
    var showImportDialog by remember { mutableStateOf(false) }
    var showQuickCodeDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importWorking by remember { mutableStateOf(false) }
    var pendingOverwrite by remember { mutableStateOf<MirrorImportPreview?>(null) }
    var shownCode by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // A scan started from this screen comes back here instead of pairing a sensor.
    DisposableEffect(showImportDialog) {
        if (showImportDialog) {
            MainActivity.setQrCodeListener { code -> importText = code }
        }
        onDispose { MainActivity.setQrCodeListener(null) }
    }

    fun importCode(preview: MirrorImportPreview) {
        importWorking = true
        scope.launch {
            val result = repository.importMirrorCode(preview)
            importWorking = false
            showImportDialog = false
            pendingOverwrite = null
            if (result.ok) {
                if (result.needsBluetoothPermission) {
                    (context as? Activity)?.let { Applic.requestBluetoothPermissions(it) }
                }
                val warning = result.blocker?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.loc_mirror_data_not_all)
                        .takeIf { result.partialData }
                warning?.let {
                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.loc_connection_saved),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    context.getString(result.error.messageRes),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

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
        SettingsSection(title = stringResource(R.string.loc_relay_servers)) {
            SettingsNavRow(
                title = stringResource(R.string.settings_title_turn_server),
                subtitle = stringResource(R.string.settings_desc_turn_server),
                icon = Icons.Default.Router,
                onClick = onOpenTurnServerConfig
            )
        }

        // DEVICE ROLE & NETWORK STATUS
        SettingsSection(title = stringResource(R.string.loc_device_network_role)) {
            SettingsActionRow(
                title = stringResource(R.string.loc_sync_role),
                subtitle = stringResource(R.string.loc_role_listen_port, roleText, currentListenPort),
                icon = Icons.Default.Devices
            )

            SettingsActionRow(
                title = stringResource(R.string.dialog_wlan_ip),
                subtitle = wlanIp,
                icon = Icons.Default.Wifi
            )

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
        SettingsSection(title = stringResource(R.string.loc_tcp_listen_port)) {
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
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editListenPort,
                        onValueChange = { editListenPort = it },
                        label = { Text(stringResource(R.string.dialog_tcp_port)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.dialog_mirror_save_port))
                    }
                }
            }
        }

        // CONFIGURED CONNECTIONS
        SettingsSection(title = stringResource(R.string.loc_peer_connections, connections.size)) {
            SettingsActionRow(
                title = stringResource(R.string.loc_add_custom_connection),
                subtitle = stringResource(R.string.loc_add_custom_connection_desc),
                icon = Icons.Default.Add,
                onClick = { onOpenConnectionEdit(-1) }
            )

            if (codesSupported) {
                SettingsActionRow(
                    title = stringResource(R.string.loc_mirror_import),
                    subtitle = stringResource(R.string.loc_mirror_import_desc),
                    icon = Icons.Default.QrCodeScanner,
                    onClick = {
                        importText = ""
                        showImportDialog = true
                    }
                )
                SettingsActionRow(
                    title = stringResource(R.string.loc_mirror_code_title),
                    subtitle = stringResource(R.string.loc_mirror_code_desc),
                    icon = Icons.Default.QrCode2,
                    onClick = { showQuickCodeDialog = true }
                )
            }

            if (connections.isEmpty()) {
                Text(
                    text = stringResource(R.string.dialog_mirror_no_conns),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp)
                )
            } else {
                connections.forEach { conn ->
                    val transportText = mirrorTransportName(conn)
                    val addressText = if (conn.isIce) {
                        context.getString(R.string.loc_mirror_ice)
                    } else if (conn.ips.isNotEmpty()) {
                        conn.ips.joinToString(", ")
                    } else {
                        context.getString(R.string.detect)
                    }
                    val roleLabel = stringResource(
                        if (conn.isReceiver) R.string.loc_mirror_receive_from else R.string.loc_mirror_send_data
                    )
                    val statusSummary = getMirrorConnectionStatusSummary(conn)
                    val statusSuffix = if (statusSummary.isNotBlank()) " • $statusSummary" else ""

                    SettingsNavRow(
                        title = conn.label.ifBlank {
                            stringResource(R.string.loc_connection_default_name, conn.index + 1)
                        },
                        subtitle = stringResource(
                            R.string.loc_mirror_connection_summary,
                            transportText,
                            if (conn.isReceiver) addressText else "$addressText:${conn.port}",
                            roleLabel,
                            statusSuffix
                        ),
                        icon = mirrorTransportIcon(conn),
                        onClick = { onOpenConnectionEdit(conn.index) },
                        checked = !conn.isDeactivated,
                        onCheckedChange = { enabled ->
                            repository.setMirrorConnectionDeactivated(conn.index, !enabled)
                        }
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
                                Toast.makeText(context, context.getString(R.string.loc_sync_error_with_detail, e.message), Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(context, context.getString(R.string.loc_reinit_error_with_detail, e.message), Toast.LENGTH_SHORT).show()
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
        SettingsSection(title = stringResource(R.string.loc_quick_setup_local_relay)) {
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
                                Toast.makeText(context, context.getString(R.string.loc_failed_add_receiver), Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(context, context.getString(R.string.loc_failed_add_sender), Toast.LENGTH_SHORT).show()
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
        SettingsSection(title = stringResource(R.string.loc_developer_testing)) {
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
                            text = stringResource(R.string.loc_how_to_relay),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(onClick = { showGuideExpanded = !showGuideExpanded }) {
                        Icon(
                            imageVector = if (showGuideExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = stringResource(R.string.loc_action_toggle_guide)
                        )
                    }
                }

                AnimatedVisibility(visible = showGuideExpanded) {
                    val guideHtml = stringResource(R.string.settings_dev_guide_body)
                    val annotated = remember(guideHtml) {
                        htmlToAnnotatedString(guideHtml)
                    }
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.loc_mirror_import)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.loc_mirror_import_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text(stringResource(R.string.loc_mirror_import)) },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                importText = readClipboardText(context).orEmpty()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.loc_mirror_import_paste), fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { (context as? MainActivity)?.let { MainActivity.scanQrCode(it) } }
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.loc_mirror_import_scan), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = importText.isNotBlank() && !importWorking,
                    onClick = {
                        val payload = importText
                        importWorking = true
                        scope.launch {
                            val preview = repository.previewMirrorImport(payload)
                            importWorking = false
                            when {
                                preview == null -> Toast.makeText(
                                    context,
                                    context.getString(R.string.loc_mirror_import_empty),
                                    Toast.LENGTH_SHORT
                                ).show()
                                // Only a receiving connection replaces the data the other
                                // device already holds, so that is the only case to confirm.
                                preview.overwritesData && preview.draft.receiveFrom ->
                                    pendingOverwrite = preview
                                else -> importCode(preview)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.loc_mirror_import_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingOverwrite?.let { preview ->
        AlertDialog(
            onDismissRequest = { pendingOverwrite = null },
            title = { Text(stringResource(R.string.overwrite)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            R.string.loc_mirror_import_overwrite,
                            presentMirrorDataTypes(context, preview)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.loc_mirror_import_overwrite_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { importCode(preview) }) {
                    Text(stringResource(R.string.overwrite))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOverwrite = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showQuickCodeDialog) {
        AlertDialog(
            onDismissRequest = { showQuickCodeDialog = false },
            title = { Text(stringResource(R.string.loc_mirror_code_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.loc_mirror_code_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MirrorQuickCode.entries.forEach { kind ->
                        SettingsActionRow(
                            title = stringResource(kind.titleRes),
                            subtitle = stringResource(kind.subtitleRes),
                            icon = Icons.Default.QrCode2,
                            onClick = {
                                showQuickCodeDialog = false
                                scope.launch {
                                    val result = repository.createMirrorQuickCode(kind)
                                    val code = result.code
                                    if (result.ok && !code.isNullOrBlank()) {
                                        shownCode = code
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                if (result.ok) R.string.loc_mirror_no_code
                                                else R.string.loc_mirror_code_failed
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickCodeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    shownCode?.let { code ->
        MirrorCodeDialog(code = code, onDismiss = { shownCode = null })
    }
}

/** The connection method a stored connection uses, in the wording of the editor. */
@Composable
private fun mirrorTransportName(conn: MirrorConnection): String = when {
    conn.isIce -> stringResource(R.string.loc_mirror_ice)
    conn.transport == BleMirror.TRANSPORT_BLUETOOTH -> stringResource(R.string.transport_bluetooth)
    conn.transport == BleMirror.TRANSPORT_MESSAGES -> stringResource(R.string.loc_transport_wear_os)
    conn.transport == BleMirror.TRANSPORT_TCP -> stringResource(R.string.transport_tcp)
    else -> stringResource(R.string.transport_automatic)
}

private fun mirrorTransportIcon(conn: MirrorConnection): ImageVector = when {
    conn.isIce -> Icons.Default.Cloud
    conn.transport == BleMirror.TRANSPORT_BLUETOOTH -> Icons.Default.Bluetooth
    conn.transport == BleMirror.TRANSPORT_MESSAGES -> Icons.Default.Watch
    conn.transport == BleMirror.TRANSPORT_TCP -> Icons.Default.Lan
    else -> Icons.Default.Wifi
}

/** Names the data this device already holds that a received connection would replace. */
private fun presentMirrorDataTypes(
    context: Context,
    preview: MirrorImportPreview
): String {
    val context = context
    val types = buildList {
        if (preview.presentAmounts) add(context.getString(R.string.amountsname))
        if (preview.presentScans) add(context.getString(R.string.scansname))
        if (preview.presentStream) add(context.getString(R.string.streamname))
    }
    return types.joinToString(", ")
}

private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = clipboard?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}
