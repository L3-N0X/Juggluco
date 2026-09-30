package tk.glucodata.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GarminStatus

private const val KERFSTOK_STORE_URL =
    "https://apps.garmin.com/en-US/apps/b6348ccc-86d8-4780-8013-d9e19fed5260"

private const val GARMIN_APP_ID_LENGTH = 32

/**
 * The settings that belong to the watch application rather than to one watch:
 * getting Kerfstok onto the watch, which application id the phone talks to, the
 * amounts the watch offers on its own, and how it draws its display.
 */
@Composable
fun GarminConfigScreen(
    repository: GlucoseRepository,
    peerId: Long,
    onOpenShortcuts: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by repository.garminStatus.collectAsState()
    var editingAppId by remember { mutableStateOf(false) }
    var helpHtml by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { repository.refreshGarminStatus() }

    val watch = status.watches.firstOrNull { it.id == peerId }

    SettingsDetailScaffold(
        title = stringResource(R.string.loc_garmin_config_title),
        onNavigateBack = onNavigateBack
    ) {
        if (!status.supported) {
            SettingsInfoCard(icon = Icons.Default.Info) {
                Text(
                    text = stringResource(R.string.loc_garmin_not_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@SettingsDetailScaffold
        }

        SettingsSection {
            SettingsActionRow(
                title = stringResource(
                    if (status.appInstalled) R.string.watchappinstalled else R.string.getkerfstok
                ),
                icon = Icons.Default.Download,
                onClick = { openKerfstokStore(context) }
            )

            SettingsActionRow(
                title = stringResource(R.string.garmin_watch_app_id),
                subtitle = stringResource(R.string.loc_garmin_app_id_dialog_desc),
                icon = Icons.Default.Fingerprint,
                onClick = { editingAppId = true }
            )

            val help = remember { repository.garminHelpHtml("garminconfig") }
            if (help != null) {
                SettingsActionRow(
                    title = stringResource(R.string.helpname),
                    icon = Icons.Default.Info,
                    onClick = { helpHtml = help }
                )
            }
        }

        SettingsSection {
            SettingsActionRow(
                title = stringResource(R.string.shutcuts),
                subtitle = stringResource(R.string.loc_garmin_shortcuts_desc),
                icon = Icons.Default.Bolt,
                onClick = onOpenShortcuts
            )

            SettingsSwitchRow(
                title = stringResource(R.string.darkmode),
                subtitle = watch?.name,
                icon = Icons.Default.DarkMode,
                checked = watch?.darkMode == true,
                enabled = watch != null,
                onCheckedChange = { black ->
                    watch?.let { repository.setGarminDarkMode(it.id, black) }
                }
            )
        }
    }

    if (editingAppId) {
        GarminAppIdDialog(
            status = status,
            onDismiss = { editingAppId = false },
            onSave = { id ->
                scope.launch {
                    val saved = repository.saveGarminAppId(id)
                    editingAppId = false
                    if (saved) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.loc_garmin_app_id_saved),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.garmin_id_change_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    helpHtml?.let { html ->
        AlertDialog(
            onDismissRequest = { helpHtml = null },
            title = { Text(stringResource(R.string.loc_garmin_help_title)) },
            text = { Text(htmlToAnnotatedString(html)) },
            confirmButton = {
                TextButton(onClick = { helpHtml = null }) { Text(stringResource(R.string.ok)) }
            }
        )
    }
}

/**
 * The watch application id the phone talks to. The id is 32 hex digits, and the
 * default one is what a stock install uses, so it stays a choice instead of an
 * empty field the user has to restore by hand.
 */
@Composable
private fun GarminAppIdDialog(
    status: GarminStatus,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    var useDefault by remember { mutableStateOf(status.appIdIsDefault) }
    var id by remember { mutableStateOf(status.appId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.loc_garmin_app_id_dialog_title)) },
        text = {
            GarminAppIdField(
                id = id,
                onIdChange = { id = it },
                useDefault = useDefault,
                onUseDefaultChange = {
                    useDefault = it
                    if (it) id = status.defaultAppId
                }
            )
        },
        confirmButton = {
            TextButton(
                enabled = useDefault || id.length == GARMIN_APP_ID_LENGTH,
                onClick = { onSave(if (useDefault) null else id) }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun GarminAppIdField(
    id: String,
    onIdChange: (String) -> Unit,
    useDefault: Boolean,
    onUseDefaultChange: (Boolean) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.loc_garmin_app_id_dialog_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.defaultname),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = useDefault, onCheckedChange = onUseDefaultChange)
        }
        OutlinedTextField(
            value = id,
            onValueChange = { onIdChange(it.uppercaseHex()) },
            enabled = !useDefault,
            singleLine = true,
            isError = !useDefault && id.length != GARMIN_APP_ID_LENGTH,
            supportingText = if (useDefault || id.length == GARMIN_APP_ID_LENGTH) {
                null
            } else {
                { Text(stringResource(R.string.garmin_id_length, id.length)) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
    }
}

/** The id is 32 hex digits; anything else cannot be an application id. */
private fun String.uppercaseHex(): String =
    filter { it.digitToIntOrNull(16) != null }.uppercase()

private fun openKerfstokStore(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(KERFSTOK_STORE_URL))
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}
