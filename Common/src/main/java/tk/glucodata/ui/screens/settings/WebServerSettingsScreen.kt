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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        onNavigateBack = onNavigateBack
    ) {
        // SERVER STATUS & TOGGLE
        SettingsSection(title = stringResource(R.string.loc_service_status)) {
            SettingsSwitchRow(
                title = stringResource(R.string.loc_embedded_rest_server),
                subtitle = if (exchanges.xdripWebServer) stringResource(R.string.loc_server_listening, httpPort) else stringResource(R.string.loc_server_disabled),
                icon = Icons.Default.Code,
                checked = exchanges.xdripWebServer,
                onCheckedChange = { repository.setXdripWebServer(it) }
            )
        }

        // PORT & AUTH CONFIGURATION
        SettingsSection(title = stringResource(R.string.loc_network_ports_auth)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = httpPort,
                        onValueChange = { httpPort = it },
                        label = { Text(stringResource(R.string.loc_http_port)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = sslPort,
                        onValueChange = { sslPort = it },
                        label = { Text(stringResource(R.string.loc_ssl_port)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = { apiSecret = it },
                    label = { Text(stringResource(R.string.loc_api_secret)) },
                    singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.loc_action_toggle_secret)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = pollInterval,
                    onValueChange = { pollInterval = it },
                    label = { Text(stringResource(R.string.loc_update_interval)) },
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
                            Toast.makeText(context, context.getString(R.string.loc_ports_range), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (hp == sp) {
                            Toast.makeText(context, context.getString(R.string.loc_ports_identical), Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, context.getString(R.string.loc_web_server_saved), Toast.LENGTH_SHORT).show()
                        } catch (e: Throwable) {
                            Toast.makeText(context, context.getString(R.string.loc_error_saving, e.message), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.loc_save_restart_server), fontWeight = FontWeight.Bold)
                }
            }
        }

        // ENDPOINT REFERENCE CARD
        SettingsSection(title = stringResource(R.string.loc_rest_compatibility)) {
            val cleanPort = httpPort.ifBlank { "17580" }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.loc_rest_endpoints_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.loc_rest_endpoints, cleanPort),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                )
            }
        }

        // INFO CARD
        SettingsInfoCard(
            text = stringResource(R.string.loc_web_server_info),
            icon = Icons.Default.Info
        )
    }
}
