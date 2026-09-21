package tk.glucodata.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

@Composable
fun MirrorConnectionEditScreen(
    repository: GlucoseRepository,
    connectionIndex: Int = -1, // -1 means adding new
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val connections by repository.mirrorConnections.collectAsState()
    val existingConn = connections.find { it.index == connectionIndex }

    val isEditing = existingConn != null

    var label by remember(existingConn) {
        mutableStateOf(existingConn?.label ?: if (connectionIndex < 0) "Local Production App" else "")
    }
    var hostIp by remember(existingConn) {
        mutableStateOf(existingConn?.ips?.firstOrNull() ?: "127.0.0.1")
    }
    var port by remember(existingConn) {
        mutableStateOf(existingConn?.port ?: "17580")
    }
    var isReceiver by remember(existingConn) {
        mutableStateOf(existingConn?.isReceiver ?: true)
    }
    var sendStream by remember(existingConn) {
        mutableStateOf(existingConn?.sendStream ?: true)
    }
    var sendScans by remember(existingConn) {
        mutableStateOf(existingConn?.sendScans ?: true)
    }
    var sendAmounts by remember(existingConn) {
        mutableStateOf(existingConn?.sendAmounts ?: true)
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    val screenTitle = if (isEditing) "Edit mirror connection" else "Add mirror connection"

    SettingsDetailScaffold(
        title = screenTitle,
        onNavigateBack = onNavigateBack
    ) {
        // IDENTIFICATION & HOST
        SettingsSection(title = "Target host & network address") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Connection Label") },
                    placeholder = { Text("e.g. Local Production App or Caregiver Phone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hostIp,
                        onValueChange = { hostIp = it },
                        label = { Text("Target IP / Hostname") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )

                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.9f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick presets for IP
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("127.0.0.1", "192.168.1.", "10.0.2.2").forEach { preset ->
                        FilterChip(
                            selected = hostIp == preset,
                            onClick = { hostIp = preset },
                            label = { Text(preset, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // ROLE & PROTOCOL
        SettingsSection(title = "Device role & synchronized data") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isReceiver,
                        onClick = { isReceiver = true },
                        label = { Text("Receiver (Dev Mode / Secondary)", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = !isReceiver,
                        onClick = { isReceiver = false },
                        label = { Text("Sender (Production / Paired)", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isReceiver)
                        "Receiver connects to production to receive live glucose streams without touching Bluetooth."
                    else
                        "Sender pushes live CGM readings, scans, and amounts to secondary peers or dev builds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsSwitchRow(
                title = "Continuous glucose stream",
                subtitle = "Sync minute-by-minute sensor curve points",
                icon = Icons.Default.Timeline,
                checked = sendStream,
                onCheckedChange = { sendStream = it }
            )

            SettingsSwitchRow(
                title = "Manual NFC scans",
                subtitle = "Sync NFC scan events and fingerstick tests",
                icon = Icons.Default.Nfc,
                checked = sendScans,
                onCheckedChange = { sendScans = it }
            )

            SettingsSwitchRow(
                title = "Insulin & carb amounts",
                subtitle = "Sync logged boluses, basal doses, and meals",
                icon = Icons.Default.Sync,
                checked = sendAmounts,
                onCheckedChange = { sendAmounts = it }
            )
        }

        // ACTIONS
        SettingsSection(title = "Actions") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (showDeleteConfirm) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Are you sure you want to delete this connection?",
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
                                        if (connectionIndex >= 0) {
                                            repository.deleteMirrorConnection(connectionIndex)
                                        }
                                        Toast.makeText(context, "Connection deleted", Toast.LENGTH_SHORT).show()
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
                                val cleanPort = port.trim()
                                val portNum = cleanPort.toIntOrNull()
                                if (portNum == null || portNum !in 1024..65535) {
                                    Toast.makeText(context, "Invalid port (must be between 1024 and 65535)", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                val ok = repository.saveMirrorConnection(
                                    index = if (isEditing) connectionIndex else -1,
                                    ips = listOf(hostIp.trim()),
                                    port = cleanPort,
                                    isReceiver = isReceiver,
                                    label = label.trim(),
                                    sendStream = sendStream,
                                    sendScans = sendScans,
                                    sendAmounts = sendAmounts
                                )

                                if (ok) {
                                    Toast.makeText(context, "Connection saved successfully", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                } else {
                                    Toast.makeText(context, "Failed to save connection", Toast.LENGTH_SHORT).show()
                                }
                            },
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

        // HELP
        SettingsInfoCard(
            text = "For side-by-side local relay testing, set the target IP to 127.0.0.1 and target port to your production app's listen port (default 17580). This dev build will receive all live glucose data without needing a physical sensor connection.",
            icon = Icons.Default.Info
        )
    }
}
