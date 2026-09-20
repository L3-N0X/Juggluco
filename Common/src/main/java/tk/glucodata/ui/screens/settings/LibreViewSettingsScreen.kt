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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.Libreview
import tk.glucodata.MainActivity
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

@Composable
fun LibreViewSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val exchanges by repository.exchanges.collectAsState()

    val initialEmail = remember { try { Natives.getlibreemail() ?: "" } catch (_: Throwable) { "" } }
    val initialPass = remember { try { Natives.getlibrepass() ?: "" } catch (_: Throwable) { "" } }
    val initialAccountId = remember {
        try {
            val id = Natives.getlibreAccountIDnumber()
            if (id > 0) id.toString() else ""
        } catch (_: Throwable) { "" }
    }
    val initialSendAmounts = remember { try { Natives.getSendNumbers() } catch (_: Throwable) { true } }

    var email by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf(initialPass) }
    var accountId by remember { mutableStateOf(initialAccountId) }
    var sendAmounts by remember { mutableStateOf(initialSendAmounts) }
    var showPassword by remember { mutableStateOf(false) }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_title_libre_view),
        subtitle = stringResource(R.string.settings_desc_libre_view),
        onNavigateBack = onNavigateBack
    ) {
        // UPLOAD SERVICE TOGGLE
        SettingsCard(
            title = stringResource(R.string.settings_libreview),
            icon = Icons.Default.CloudSync,
            categorySubtitle = "CLOUD SYNCHRONIZATION"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Automatic Cloud Upload",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.settings_libreview_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = exchanges.libreViewEnabled,
                    onCheckedChange = { repository.setLibreViewEnabled(it) }
                )
            }
        }

        // ACCOUNT CREDENTIALS
        SettingsCard(
            title = "LibreView Account Credentials",
            icon = Icons.Default.CloudUpload,
            categorySubtitle = "AUTHENTICATION"
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
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

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = accountId,
                onValueChange = { accountId = it },
                label = { Text("Account / Patient ID (Optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SettingsToggleRow(
                title = stringResource(R.string.sendamounts),
                subtitle = "Upload logged carbs and insulin doses along with glucose stream",
                checked = sendAmounts,
                onCheckedChange = {
                    sendAmounts = it
                    try {
                        Natives.setSendNumbers(it)
                    } catch (_: Throwable) {}
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            Natives.setlibreemail(email.trim())
                            Natives.setlibrepass(password)
                            if (accountId.isNotBlank()) {
                                accountId.toLongOrNull()?.let { Natives.setlibreAccountIDnumber(it) }
                            }
                            Natives.setSendNumbers(sendAmounts)
                            Toast.makeText(context, "LibreView credentials saved", Toast.LENGTH_SHORT).show()
                        } catch (e: Throwable) {
                            Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Credentials", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        try {
                            Natives.wakelibreview(0)
                            Toast.makeText(context, "LibreView sync triggered", Toast.LENGTH_SHORT).show()
                        } catch (e: Throwable) {
                            Toast.makeText(context, "Upload: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.sendnow), fontSize = 12.sp)
                }
            }
        }

        // INFO
        SettingsInfoCard(
            text = "LibreView is Abbott's cloud platform. When enabled, readings from your Freestyle Libre sensors are formatted and synced directly to your physician's clinic account.",
            icon = Icons.Default.Info
        )
    }
}
