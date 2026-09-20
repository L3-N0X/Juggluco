package tk.glucodata.ui.screens.settings

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

@Composable
fun TurnServerSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    val hasServer = remember {
        try { Natives.TurnServerNR() > 0 } catch (_: Throwable) { false }
    }

    var hostname by remember {
        mutableStateOf(try { if (hasServer) Natives.getTurnHost(0) ?: "" else "" } catch (_: Throwable) { "" })
    }
    var port by remember {
        mutableStateOf(try { if (hasServer) (Natives.getTurnPort(0).takeIf { it > 0 }?.toString() ?: "3478") else "3478" } catch (_: Throwable) { "3478" })
    }
    var username by remember {
        mutableStateOf(try { if (hasServer) Natives.getTurnUser(0) ?: "" else "" } catch (_: Throwable) { "" })
    }
    var password by remember {
        mutableStateOf(try { if (hasServer) Natives.getTurnPassword(0) ?: "" else "" } catch (_: Throwable) { "" })
    }
    var showPassword by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_title_turn_server),
        subtitle = stringResource(R.string.settings_desc_turn_server),
        onNavigateBack = onNavigateBack
    ) {
        // TURN SERVER ENDPOINT
        SettingsCard(
            title = "Relay Server Endpoint",
            icon = Icons.Default.Router,
            categorySubtitle = "STUN / TURN PROTOCOL"
        ) {
            OutlinedTextField(
                value = hostname,
                onValueChange = { hostname = it },
                label = { Text("Server Hostname / IP") },
                placeholder = { Text("e.g. turn.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("TURN Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // TURN CREDENTIALS
        SettingsCard(
            title = "TURN Authentication",
            categorySubtitle = "ACCESS CREDENTIALS"
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (showDeleteConfirm) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Delete configured TURN server?",
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
                                    try {
                                        Natives.deleteTurnServer(0)
                                        Toast.makeText(context, "TURN server removed", Toast.LENGTH_SHORT).show()
                                        onNavigateBack()
                                    } catch (e: Throwable) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
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
                    if (hasServer) {
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
                            val portNum = port.trim().toIntOrNull()
                            if (portNum == null || portNum !in 1..65535) {
                                Toast.makeText(context, "Invalid port number", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (hostname.isBlank()) {
                                Toast.makeText(context, "Please specify a hostname", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            try {
                                Natives.setTurnHost(0, hostname.trim())
                                Natives.setTurnPort(0, portNum)
                                Natives.setTurnUser(0, username.trim())
                                Natives.setTurnPassword(0, password)
                                Toast.makeText(context, "TURN server saved", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            } catch (e: Throwable) {
                                Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
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

        // INFO
        SettingsInfoCard(
            text = "TURN (Traversal Using Relays around NAT) servers relay packets when devices are on separate networks behind restrictive NATs or cellular firewalls, enabling remote mirroring without port forwarding.",
            icon = Icons.Default.Info
        )
    }
}
