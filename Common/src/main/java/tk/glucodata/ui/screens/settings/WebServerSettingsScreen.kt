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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.Applic
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

@Composable
fun WebServerSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val exchanges by repository.exchanges.collectAsState()

    val oldSecret = remember { try { Natives.getApiSecret() ?: "" } catch (_: Throwable) { "" } }
    val oldHttpPort = remember {
        try {
            val p = Natives.gethttpport()
            if (p in 1024..65535) p.toString() else "17580"
        } catch (_: Throwable) { "17580" }
    }
    val oldSslPort = remember {
        try {
            val p = Natives.getsslport()
            if (p in 1024..65535) p.toString() else "17581"
        } catch (_: Throwable) { "17581" }
    }
    val oldInterval = remember {
        try {
            val iv = Natives.getinterval()
            if (iv > 0) iv.toString() else "60"
        } catch (_: Throwable) { "60" }
    }

    var apiSecret by remember { mutableStateOf(oldSecret) }
    var httpPort by remember { mutableStateOf(oldHttpPort) }
    var sslPort by remember { mutableStateOf(oldSslPort) }
    var pollInterval by remember { mutableStateOf(oldInterval) }
    var showSecret by remember { mutableStateOf(false) }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_title_web_server),
        subtitle = stringResource(R.string.settings_desc_web_server),
        onNavigateBack = onNavigateBack
    ) {
        // SERVER STATUS & TOGGLE
        SettingsCard(
            title = "REST API Server Status",
            icon = Icons.Default.Code,
            categorySubtitle = "SERVICE CONTROL"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Embedded Web Server",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (exchanges.xdripWebServer) "Active and listening" else "Disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (exchanges.xdripWebServer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = exchanges.xdripWebServer,
                    onCheckedChange = { repository.setXdripWebServer(it) }
                )
            }
        }

        // PORT & AUTH CONFIGURATION
        SettingsCard(
            title = "Network Ports & Authentication",
            categorySubtitle = "HTTP & SECURITY"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = httpPort,
                    onValueChange = { httpPort = it },
                    label = { Text("HTTP Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = sslPort,
                    onValueChange = { sslPort = it },
                    label = { Text("SSL Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = apiSecret,
                onValueChange = { apiSecret = it },
                label = { Text("API Secret (Password)") },
                singleLine = true,
                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showSecret = !showSecret }) {
                        Icon(
                            imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Secret"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = pollInterval,
                onValueChange = { pollInterval = it },
                label = { Text("Update Interval (seconds)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    val hp = httpPort.toIntOrNull()
                    val sp = sslPort.toIntOrNull()
                    val iv = pollInterval.toIntOrNull() ?: 60

                    if (hp == null || hp !in 1024..65535 || sp == null || sp !in 1024..65535) {
                        Toast.makeText(context, "Ports must be valid numbers between 1024 and 65535", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (hp == sp) {
                        Toast.makeText(context, "HTTP and SSL ports cannot be identical", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    try {
                        Natives.sethttpport(hp)
                        Natives.setsslport(sp)
                        Natives.setinterval(iv)
                        Natives.setApiSecret(apiSecret)
                        if (exchanges.xdripWebServer) {
                            repository.setXdripWebServer(false)
                            repository.setXdripWebServer(true)
                        }
                        Toast.makeText(context, "Web server configuration saved", Toast.LENGTH_SHORT).show()
                    } catch (e: Throwable) {
                        Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save & Restart Server", fontWeight = FontWeight.Bold)
            }
        }

        // ENDPOINT REFERENCE CARD
        SettingsCard(
            title = "REST API Endpoints",
            categorySubtitle = "COMPATIBILITY"
        ) {
            val cleanPort = httpPort.ifBlank { "17580" }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Nightscout & xDrip Compatible Endpoints:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Entries: http://127.0.0.1:$cleanPort/api/v1/entries.json\n• Status: http://127.0.0.1:$cleanPort/api/v1/status.json\n• AGP Report: http://127.0.0.1:$cleanPort/x/report",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // INFO CARD
        SettingsInfoCard(
            text = "The local web server allows external applications, Nightscout widgets, emulator environments, and other phone apps to consume glucose streams without Bluetooth pairing.",
            icon = Icons.Default.Info
        )
    }
}
