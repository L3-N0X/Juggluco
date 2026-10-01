package tk.glucodata.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import tk.glucodata.ui.screens.ScreenLayout
import java.io.File

/** Names the native SSL listener reads from the app's file directory. */
private const val PRIVATE_KEY_FILE = "privkey.pem"
private const val FULL_CHAIN_FILE = "fullchain.pem"

/** A PEM header alone is longer than this, anything smaller is not a usable certificate. */
private const val MIN_CERTIFICATE_BYTES = 50L
private const val MAX_CERTIFICATE_BYTES = 512L * 1024L

/** Why a picked document did not become an installed certificate. */
private enum class CertificateImport {
    OK,
    UNREADABLE,
    TOO_SMALL,
    TOO_LARGE,
    WRITE_FAILED
}

/** The persisted HTTPS state, native settings keep it across restarts. */
private fun httpsEnabled(): Boolean = try { Natives.getuseSSL() } catch (_: Throwable) { false }

/** Size of an installed certificate, zero when there is none. */
private fun certificateSize(context: Context, name: String): Long = try {
    val file = File(context.filesDir, name)
    if (file.isFile) file.length() else 0L
} catch (_: Throwable) {
    0L
}

private fun writeCertificate(context: Context, uri: Uri, target: File): CertificateImport = try {
    val input = context.contentResolver.openInputStream(uri) ?: return CertificateImport.UNREADABLE
    input.use { stream ->
        target.outputStream().use { output ->
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_CERTIFICATE_BYTES) return CertificateImport.TOO_LARGE
                output.write(buffer, 0, read)
            }
            if (total < MIN_CERTIFICATE_BYTES) return CertificateImport.TOO_SMALL
            output.flush()
            output.fd.sync()
        }
    }
    CertificateImport.OK
} catch (_: Throwable) {
    CertificateImport.WRITE_FAILED
}

/**
 * Install a picked document as one of the certificate files. The copy is staged next to
 * the target and only swapped in when it is complete, so a failed or truncated import
 * never destroys a certificate that is already working.
 */
private fun importCertificate(context: Context, uri: Uri, name: String): CertificateImport {
    val target = File(context.filesDir, name)
    val staged = File(context.filesDir, "$name.import")
    val result = writeCertificate(context, uri, staged)
    if (result != CertificateImport.OK) {
        staged.delete()
        return result
    }
    if (target.exists() && !target.delete()) {
        staged.delete()
        return CertificateImport.WRITE_FAILED
    }
    if (!staged.renameTo(target)) {
        staged.delete()
        return CertificateImport.WRITE_FAILED
    }
    return CertificateImport.OK
}

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
            if (p in Natives.WEBSERVER_MIN_PORT..Natives.WEBSERVER_MAX_PORT) p.toString() else "17580"
        } catch (_: Throwable) { "17580" }
    }
    val oldSslPort = remember {
        try {
            val p = Natives.getsslport()
            if (p in Natives.WEBSERVER_MIN_PORT..Natives.WEBSERVER_MAX_PORT) p.toString() else "17581"
        } catch (_: Throwable) { "17581" }
    }
    val oldInterval = remember {
        try {
            val iv = Natives.getinterval()
            if (iv in 1..Natives.WEBSERVER_MAX_INTERVAL) iv.toString() else "60"
        } catch (_: Throwable) { "60" }
    }
    //Read when saving, the mirror can connect and change its port while this screen is open.
    fun mirrorListenPort(): Int =
        try { Natives.getreceiveport()?.trim()?.toIntOrNull() ?: 0 } catch (_: Throwable) { 0 }

    var apiSecret by remember { mutableStateOf(oldSecret) }
    var httpPort by remember { mutableStateOf(oldHttpPort) }
    var sslPort by remember { mutableStateOf(oldSslPort) }
    var pollInterval by remember { mutableStateOf(oldInterval) }
    var showSecret by remember { mutableStateOf(false) }

    var httpsOn by remember { mutableStateOf(httpsEnabled()) }
    var privateKeySize by remember { mutableStateOf(certificateSize(context, PRIVATE_KEY_FILE)) }
    var chainSize by remember { mutableStateOf(certificateSize(context, FULL_CHAIN_FILE)) }
    var pickedCertificate by remember { mutableStateOf(PRIVATE_KEY_FILE) }

    /**
     * Hand the wanted state to native and mirror what it actually stored. When a
     * certificate is rejected the stored flag would still claim HTTPS is on while its
     * listener is down, so it is cleared and the error is handed back to the caller.
     * The plain HTTP server is never touched here, it stays usable.
     */
    fun applyHttps(enabled: Boolean): String? {
        val error = try {
            Natives.setuseSSL(enabled)
        } catch (e: Throwable) {
            e.message ?: e.javaClass.simpleName
        }
        if (!error.isNullOrEmpty()) {
            try { Natives.setuseSSL(false) } catch (_: Throwable) {}
            httpsOn = false
            return error
        }
        httpsOn = enabled
        return null
    }

    fun refreshCertificates() {
        privateKeySize = certificateSize(context, PRIVATE_KEY_FILE)
        chainSize = certificateSize(context, FULL_CHAIN_FILE)
    }

    val certificateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val name = pickedCertificate
        if (uri == null) return@rememberLauncherForActivityResult
        when (importCertificate(context, uri, name)) {
            CertificateImport.OK -> {
                refreshCertificates()
                // Replacing a file under a running listener only takes effect after a restart.
                val error = if (httpsOn) applyHttps(true) else null
                Toast.makeText(
                    context,
                    if (error != null) context.getString(R.string.loc_https_start_failed, error)
                    else context.getString(R.string.loc_cert_imported, name),
                    Toast.LENGTH_LONG
                ).show()
            }
            CertificateImport.UNREADABLE, CertificateImport.WRITE_FAILED ->
                Toast.makeText(context, context.getString(R.string.loc_cert_import_failed), Toast.LENGTH_LONG).show()
            CertificateImport.TOO_SMALL ->
                Toast.makeText(context, context.getString(R.string.loc_cert_too_small), Toast.LENGTH_LONG).show()
            CertificateImport.TOO_LARGE ->
                Toast.makeText(context, context.getString(R.string.loc_cert_too_large), Toast.LENGTH_LONG).show()
        }
    }

    val secretTooLong = apiSecret.length > Natives.WEBSERVER_MAX_APISECRET
    val secretCharset = apiSecret.any { it.code !in 0x20..0x7E }

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
                    isError = secretTooLong || secretCharset,
                    supportingText = if (secretTooLong) {
                        { Text(stringResource(R.string.loc_secret_too_long, Natives.WEBSERVER_MAX_APISECRET)) }
                    } else if (secretCharset) {
                        { Text(stringResource(R.string.loc_secret_charset)) }
                    } else {
                        null
                    },
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
                        val iv = pollInterval.toIntOrNull()

                        if (hp == null || hp !in Natives.WEBSERVER_MIN_PORT..Natives.WEBSERVER_MAX_PORT ||
                            sp == null || sp !in Natives.WEBSERVER_MIN_PORT..Natives.WEBSERVER_MAX_PORT
                        ) {
                            Toast.makeText(context, context.getString(R.string.loc_ports_range), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (hp == sp) {
                            Toast.makeText(context, context.getString(R.string.loc_ports_identical), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (iv == null || iv !in 1..Natives.WEBSERVER_MAX_INTERVAL) {
                            Toast.makeText(context, context.getString(R.string.loc_interval_range, Natives.WEBSERVER_MAX_INTERVAL), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (secretTooLong) {
                            Toast.makeText(context, context.getString(R.string.loc_secret_too_long, Natives.WEBSERVER_MAX_APISECRET), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (secretCharset) {
                            Toast.makeText(context, context.getString(R.string.loc_secret_charset), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val mirrorPort = mirrorListenPort()
                        if (mirrorPort != 0 && (hp == mirrorPort || sp == mirrorPort)) {
                            Toast.makeText(context, context.getString(R.string.loc_ports_mirror_collision, mirrorPort.toString()), Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        try {
                            val status = Natives.setWebServerConfig(hp, sp, iv, apiSecret)
                            if (status != Natives.WEBSERVERCONFIG_OK) {
                                val message = when (status) {
                                    Natives.WEBSERVERCONFIG_HTTPPORT, Natives.WEBSERVERCONFIG_SSLPORT -> context.getString(R.string.loc_ports_range)
                                    Natives.WEBSERVERCONFIG_IDENTICAL -> context.getString(R.string.loc_ports_identical)
                                    Natives.WEBSERVERCONFIG_INTERVAL -> context.getString(R.string.loc_interval_range, Natives.WEBSERVER_MAX_INTERVAL)
                                    Natives.WEBSERVERCONFIG_SECRETLONG -> context.getString(R.string.loc_secret_too_long, Natives.WEBSERVER_MAX_APISECRET)
                                    Natives.WEBSERVERCONFIG_SECRETTYPE -> context.getString(R.string.loc_secret_charset)
                                    Natives.WEBSERVERCONFIG_MIRRORPORT -> {
                                        val live = mirrorListenPort()
                                        context.getString(R.string.loc_ports_mirror_collision, if (live != 0) live.toString() else "")
                                    }
                                    else -> context.getString(R.string.loc_error_saving, status.toString())
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (exchanges.xdripWebServer) {
                                repository.restartXdripWebServer()
                            }
                            //A running SSL listener keeps its old port, rebind it when it moved.
                            if (httpsOn && sp != oldSslPort.toIntOrNull()) {
                                val error = applyHttps(true)
                                if (error != null) {
                                    Toast.makeText(context, context.getString(R.string.loc_https_start_failed, error), Toast.LENGTH_LONG).show()
                                    return@Button
                                }
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

        // HTTPS & CERTIFICATES
        SettingsSection(title = stringResource(R.string.loc_https_certificates)) {
            SettingsSwitchRow(
                title = stringResource(R.string.loc_https_server),
                subtitle = if (httpsOn) {
                    stringResource(R.string.loc_https_active, sslPort.ifBlank { oldSslPort })
                } else {
                    stringResource(R.string.loc_https_inactive)
                },
                icon = Icons.Default.Lock,
                checked = httpsOn,
                onCheckedChange = { wanted ->
                    val error = applyHttps(wanted)
                    if (error != null) {
                        Toast.makeText(context, context.getString(R.string.loc_https_start_failed, error), Toast.LENGTH_LONG).show()
                    } else if (wanted && !exchanges.xdripWebServer) {
                        repository.setXdripWebServer(true)
                    }
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = ScreenLayout.CardPadding),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            SettingsActionRow(
                title = stringResource(R.string.loc_https_private_key),
                subtitle = if (privateKeySize > 0) {
                    stringResource(
                        R.string.loc_cert_installed,
                        PRIVATE_KEY_FILE,
                        Formatter.formatFileSize(context, privateKeySize)
                    )
                } else {
                    stringResource(R.string.loc_cert_not_installed, PRIVATE_KEY_FILE)
                },
                icon = Icons.Default.Key,
                onClick = {
                    pickedCertificate = PRIVATE_KEY_FILE
                    certificateLauncher.launch(arrayOf("*/*"))
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = ScreenLayout.CardPadding),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            SettingsActionRow(
                title = stringResource(R.string.loc_https_full_chain),
                subtitle = if (chainSize > 0) {
                    stringResource(
                        R.string.loc_cert_installed,
                        FULL_CHAIN_FILE,
                        Formatter.formatFileSize(context, chainSize)
                    )
                } else {
                    stringResource(R.string.loc_cert_not_installed, FULL_CHAIN_FILE)
                },
                icon = Icons.Default.VerifiedUser,
                onClick = {
                    pickedCertificate = FULL_CHAIN_FILE
                    certificateLauncher.launch(arrayOf("*/*"))
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = ScreenLayout.CardPadding),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            Text(
                text = stringResource(R.string.loc_https_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = ScreenLayout.CardPadding, vertical = 12.dp)
            )
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
