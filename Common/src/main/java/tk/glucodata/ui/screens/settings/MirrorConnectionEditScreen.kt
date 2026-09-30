package tk.glucodata.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tk.glucodata.Applic
import tk.glucodata.BleMirror
import tk.glucodata.QRmake
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.MirrorConnectionDraft
import tk.glucodata.ui.model.MirrorDataStart
import tk.glucodata.ui.model.MirrorSaveResult
import tk.glucodata.ui.screens.ScreenLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_ADDRESSES = 4

/**
 * How many addresses the native side can store: the label and a detected
 * address each take one of the four slots.
 */
private fun maxMirrorAddresses(label: String, detectIp: Boolean): Int =
    MAX_ADDRESSES - (if (label.isBlank()) 0 else 1) - (if (detectIp) 1 else 0)

private data class MirrorEditorState(
    val draft: MirrorConnectionDraft,
    /** A Wear OS peer is the only reason a phone may offer the Messages transport. */
    val peerIsWearOs: Boolean
)

/**
 * Editor for one mirror connection.
 *
 * The connection method comes first because it decides which of the remaining
 * options exist at all: addresses and ports only mean something for the network
 * transports, a password only for direct Bluetooth, and a relay label only for
 * a network connection that goes through ICE. Everything the native side can
 * store is reachable here, so no setting forces a trip back to the old screens.
 */
@Composable
fun MirrorConnectionEditScreen(
    repository: GlucoseRepository,
    connectionIndex: Int = -1, // -1 means adding new
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEditing = connectionIndex >= 0
    val connections by repository.mirrorConnections.collectAsState()

    var editor by remember(connectionIndex) { mutableStateOf<MirrorEditorState?>(null) }
    var loadFailed by remember(connectionIndex) { mutableStateOf(false) }
    var saving by remember(connectionIndex) { mutableStateOf(false) }
    var showDeleteConfirm by remember(connectionIndex) { mutableStateOf(false) }
    var showResendConfirm by remember(connectionIndex) { mutableStateOf(false) }
    var passwordVisible by remember(connectionIndex) { mutableStateOf(false) }
    var shownCode by remember(connectionIndex) { mutableStateOf<String?>(null) }

    val screenTitle = stringResource(
        if (isEditing) R.string.loc_mirror_edit_title else R.string.loc_mirror_edit_add
    )
    val codesSupported = remember { repository.mirrorCodesSupported() }
    val restoreSupported = remember { repository.mirrorRestoreSupported() }

    LaunchedEffect(Unit) {
        repository.refreshMirrorConnections()
    }

    LaunchedEffect(connectionIndex) {
        if (!isEditing) {
            editor = MirrorEditorState(newMirrorDraft(), false)
            return@LaunchedEffect
        }
        val draft = repository.mirrorConnectionDraft(connectionIndex)
        val stored = repository.mirrorHostEditState(connectionIndex)
        if (draft == null || stored == null) {
            loadFailed = true
            return@LaunchedEffect
        }
        editor = MirrorEditorState(draft, stored.wearOs)
    }

    val state = editor
    val draft = state?.draft
    val snapshotStatus = connections
        .firstOrNull { it.index == connectionIndex }
        ?.status
        .orEmpty()

    SettingsDetailScaffold(
        title = screenTitle,
        onNavigateBack = onNavigateBack
    ) {
        if (state == null || draft == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (loadFailed) {
                    Text(
                        text = stringResource(R.string.loc_failed_load_connection),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
            return@SettingsDetailScaffold
        }

        // --- Connection method, the choice every other option depends on ---
        SettingsSection(title = stringResource(R.string.loc_mirror_method)) {
            val methods = mirrorTransportOptions(state)
            methods.forEach { option ->
                MirrorChoiceRow(
                    title = stringResource(option.titleRes),
                    subtitle = stringResource(option.subtitleRes),
                    icon = option.icon,
                    selected = draft.transport == option.transport,
                    onClick = {
                        editor = state.copy(
                            draft = draft.applyTransport(option.transport)
                        )
                    }
                )
            }
        }

        // --- Label, and the password that direct Bluetooth needs ---
        SettingsSection(title = stringResource(R.string.loc_mirror_identity)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft.label,
                    onValueChange = { updated ->
                        // A label takes one of the four address slots, so a name that
                        // no longer fits must not leave a connection that cannot save.
                        editor = state.copy(
                            draft = draft.copy(
                                label = updated,
                                addresses = draft.addresses.take(
                                    maxMirrorAddresses(updated, draft.detectIp)
                                )
                            )
                        )
                    },
                    label = { Text(stringResource(R.string.loc_connection_label)) },
                    placeholder = { Text(stringResource(R.string.loc_connection_label_example)) },
                    isError = draft.needsLabel && draft.label.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (draft.needsLabel) {
                    MirrorSupportingText(stringResource(R.string.loc_mirror_label_required))
                }
            }

            if (draft.transport == BleMirror.TRANSPORT_BLUETOOTH) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draft.password,
                        onValueChange = {
                            editor = state.copy(
                                draft = draft.copy(
                                    password = it,
                                    // An emptied field means no password, which direct
                                    // Bluetooth refuses to save.
                                    usePassword = it.isNotEmpty()
                                )
                            )
                        },
                        label = { Text(stringResource(R.string.loc_mirror_password)) },
                        isError = draft.password.isEmpty(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = stringResource(R.string.loc_mirror_show_password)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    MirrorSupportingText(stringResource(R.string.loc_mirror_password_desc))
                }
            }
        }

        // --- Addresses, only for the transports that use a network ---
        if (draft.isNetworkTransport && !draft.ice) {
            SettingsSection(title = stringResource(R.string.loc_target_host_network)) {
                OutlinedTextField(
                    value = draft.port,
                    onValueChange = { editor = state.copy(draft = draft.copy(port = it)) },
                    label = { Text(stringResource(R.string.port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !draft.passiveOnly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val addresses = draft.addresses.ifEmpty { listOf("") }
                    val editableAddresses = if (draft.useHostname) addresses.take(1) else addresses
                    // The native side keeps one slot for the label and one for a
                    // detected address, so the editor offers no more than it can store.
                    val maxFields = maxMirrorAddresses(
                        draft.label,
                        draft.detectIp && !draft.useHostname && !draft.activeOnly
                    )
                    editableAddresses.forEachIndexed { position, address ->
                        OutlinedTextField(
                            value = address,
                            onValueChange = { updated ->
                                val next = editableAddresses.toMutableList()
                                next[position] = updated
                                editor = state.copy(draft = draft.copy(addresses = next))
                            },
                            label = {
                                Text(
                                    if (draft.useHostname) {
                                        stringResource(R.string.hostname)
                                    } else {
                                        stringResource(R.string.loc_target_ip_hostname)
                                    }
                                )
                            },
                            placeholder = { Text(stringResource(R.string.loc_hostname_example)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            trailingIcon = {
                                if (editableAddresses.size > 1) {
                                    IconButton(
                                        onClick = {
                                            val next = editableAddresses
                                                .toMutableList()
                                                .also { it.removeAt(position) }
                                            editor = state.copy(draft = draft.copy(addresses = next))
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = stringResource(
                                                R.string.loc_mirror_remove_address,
                                                position + 1
                                            )
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (!draft.useHostname && editableAddresses.size < maxFields) {
                        TextButton(
                            onClick = {
                                editor = state.copy(
                                    draft = draft.copy(addresses = editableAddresses + "")
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.loc_mirror_add_address))
                        }
                        MirrorSupportingText(stringResource(R.string.loc_mirror_address_hint))
                    }
                }

                SettingsSwitchRow(
                    title = stringResource(R.string.loc_mirror_use_hostname),
                    subtitle = stringResource(R.string.loc_mirror_use_hostname_desc),
                    icon = Icons.Default.Dns,
                    checked = draft.useHostname,
                    onCheckedChange = { enabled ->
                        editor = state.copy(
                            draft = draft.copy(
                                useHostname = enabled,
                                detectIp = if (enabled) false else draft.detectIp,
                                addresses = if (enabled) listOf(draft.addresses.firstOrNull().orEmpty()) else draft.addresses
                            )
                        )
                    }
                )

                if (!draft.useHostname) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.loc_mirror_detect_ip),
                        subtitle = stringResource(R.string.loc_mirror_detect_ip_desc),
                        icon = Icons.Default.Search,
                        checked = draft.detectIp,
                        onCheckedChange = { enabled ->
                            editor = state.copy(
                                draft = draft.copy(
                                    detectIp = enabled,
                                    // A detected peer is never dialled with one fixed address.
                                    activeOnly = if (enabled) false else draft.activeOnly
                                )
                            )
                        }
                    )
                }

                SettingsSwitchRow(
                    title = stringResource(R.string.loc_mirror_test_ip),
                    subtitle = stringResource(R.string.loc_mirror_test_ip_desc),
                    icon = Icons.Default.NetworkCheck,
                    checked = draft.testIp,
                    onCheckedChange = { editor = state.copy(draft = draft.copy(testIp = it)) }
                )
            }

            SettingsSection(title = stringResource(R.string.loc_mirror_direction)) {
                MirrorChoiceRow(
                    title = stringResource(R.string.loc_mirror_direction_both),
                    subtitle = stringResource(R.string.loc_mirror_direction_both_desc),
                    icon = Icons.Default.SwapHoriz,
                    selected = !draft.activeOnly && !draft.passiveOnly,
                    onClick = {
                        editor = state.copy(
                            draft = draft.copy(activeOnly = false, passiveOnly = false)
                        )
                    }
                )
                MirrorChoiceRow(
                    title = stringResource(R.string.loc_mirror_direction_active),
                    subtitle = stringResource(R.string.loc_mirror_direction_active_desc),
                    icon = Icons.Default.NorthEast,
                    selected = draft.activeOnly,
                    onClick = {
                        editor = state.copy(
                            draft = draft.copy(activeOnly = true, passiveOnly = false, detectIp = false)
                        )
                    }
                )
                MirrorChoiceRow(
                    title = stringResource(R.string.loc_mirror_direction_passive),
                    subtitle = stringResource(R.string.loc_mirror_direction_passive_desc),
                    icon = Icons.Default.SouthWest,
                    selected = draft.passiveOnly,
                    onClick = {
                        editor = state.copy(
                            draft = draft.copy(activeOnly = false, passiveOnly = true)
                        )
                    }
                )
            }
        }

        // --- Relay, a network connection without a direct route ---
        if (draft.isNetworkTransport) {
            SettingsSection(title = stringResource(R.string.loc_mirror_ice_title)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.loc_mirror_ice),
                    subtitle = stringResource(R.string.loc_mirror_ice_desc),
                    icon = Icons.Default.Cloud,
                    checked = draft.ice,
                    onCheckedChange = { editor = state.copy(draft = draft.copy(ice = it)) }
                )
                if (draft.ice) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = draft.iceLabel,
                            onValueChange = { editor = state.copy(draft = draft.copy(iceLabel = it)) },
                            label = { Text(stringResource(R.string.loc_mirror_ice_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        MirrorSupportingText(stringResource(R.string.loc_mirror_ice_label_hint))
                        Text(
                            text = stringResource(R.string.loc_mirror_ice_side),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(false, true).forEach { side ->
                                MirrorSmallChoice(
                                    text = stringResource(if (side) R.string.one else R.string.zero),
                                    selected = draft.iceSide == side,
                                    onClick = { editor = state.copy(draft = draft.copy(iceSide = side)) }
                                )
                            }
                        }
                        MirrorSupportingText(stringResource(R.string.loc_mirror_ice_side_desc))
                    }
                }
            }
        }

        // --- What both devices exchange ---
        SettingsSection(title = stringResource(R.string.loc_mirror_shared_data)) {
            SettingsSwitchRow(
                title = stringResource(R.string.loc_mirror_receive_from),
                subtitle = stringResource(R.string.loc_mirror_receive_from_desc),
                icon = Icons.Default.Download,
                checked = draft.receiveFrom,
                onCheckedChange = { editor = state.copy(draft = draft.copy(receiveFrom = it)) }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.loc_mirror_send_stream),
                subtitle = stringResource(R.string.loc_continuous_stream_desc),
                icon = Icons.Default.Timeline,
                checked = draft.sendStream,
                onCheckedChange = { editor = state.copy(draft = draft.copy(sendStream = it)) }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.loc_mirror_send_scans),
                subtitle = stringResource(R.string.loc_manual_nfc_scans_desc),
                icon = Icons.Default.Nfc,
                checked = draft.sendScans,
                onCheckedChange = { editor = state.copy(draft = draft.copy(sendScans = it)) }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.loc_mirror_send_amounts),
                subtitle = stringResource(R.string.loc_insulin_carb_amounts_desc),
                icon = Icons.Default.Sync,
                checked = draft.sendAmounts,
                onCheckedChange = { editor = state.copy(draft = draft.copy(sendAmounts = it)) }
            )
            if (restoreSupported) {
                SettingsSwitchRow(
                    title = stringResource(R.string.loc_mirror_restore),
                    subtitle = stringResource(R.string.loc_mirror_restore_desc),
                    icon = Icons.Default.Restore,
                    checked = draft.restore,
                    onCheckedChange = { editor = state.copy(draft = draft.copy(restore = it)) }
                )
            }
            // Combinations the native side refuses, told before saving is pressed.
            when {
                draft.receiveFrom && draft.sendAmounts && draft.sendStream && draft.sendScans ->
                    MirrorWarningText(stringResource(R.string.allsentnoreceive))
                !draft.receiveFrom && !draft.sendsAnything ->
                    MirrorWarningText(stringResource(R.string.specifyreceiveordata))
            }
            if (draft.sendsAnything && !(draft.sendAmounts && draft.sendStream && draft.sendScans)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.loc_mirror_data_start),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    MirrorDataStart.entries.forEach { start ->
                        MirrorSmallChoice(
                            text = stringResource(start.labelRes),
                            selected = draft.dataStart == start,
                            onClick = { editor = state.copy(draft = draft.copy(dataStart = start)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (draft.dataStart == MirrorDataStart.SCREEN_POSITION && draft.startTime > 0L) {
                        MirrorSupportingText(
                            stringResource(
                                R.string.loc_mirror_data_start_from,
                                formatMirrorTime(draft.startTime)
                            )
                        )
                    }
                }
            }
        }

        if (isEditing && codesSupported) {
            SettingsSection(title = stringResource(R.string.loc_mirror_code_section)) {
                SettingsActionRow(
                    title = stringResource(R.string.loc_mirror_show_code),
                    subtitle = stringResource(R.string.loc_mirror_show_code_desc),
                    icon = Icons.Default.QrCode2,
                    onClick = {
                        scope.launch {
                            shownCode = repository.mirrorConnectionCode(connectionIndex)
                                ?: context.getString(R.string.loc_mirror_no_code)
                        }
                    }
                )
                if (draft.sendsAnything) {
                    SettingsActionRow(
                        title = stringResource(R.string.loc_mirror_resend),
                        subtitle = stringResource(R.string.loc_mirror_resend_desc),
                        icon = Icons.Default.Upload,
                        onClick = { showResendConfirm = true }
                    )
                }
            }
        }

        if (snapshotStatus.isNotBlank()) {
            SettingsSection(title = stringResource(R.string.loc_connection_diagnostics)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp)
                ) {
                    Text(
                        text = remember(snapshotStatus) { htmlToAnnotatedString(snapshotStatus) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        SettingsSection(title = stringResource(R.string.loc_common_actions)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp)
            ) {
                if (showDeleteConfirm) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.loc_delete_connection_confirm),
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
                                        repository.deleteMirrorConnection(connectionIndex)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.loc_connection_deleted),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onNavigateBack()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
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
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.delete),
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (saving) return@Button
                                saving = true
                                scope.launch {
                                    val result = repository.saveMirrorConnectionDraft(
                                        index = if (isEditing) connectionIndex else -1,
                                        draft = draft
                                    )
                                    saving = false
                                    if (result.ok) {
                                        onMirrorSaved(context, result)
                                        onNavigateBack()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(result.error.messageRes),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.save),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        SettingsInfoCard(
            text = stringResource(R.string.loc_mirror_testing_info),
            icon = Icons.Default.Info
        )
    }

    if (showResendConfirm) {
        AlertDialog(
            onDismissRequest = { showResendConfirm = false },
            title = { Text(stringResource(R.string.loc_mirror_resend)) },
            text = { Text(stringResource(R.string.loc_mirror_resend_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        showResendConfirm = false
                        repository.resetMirrorConnection(connectionIndex)
                        Toast.makeText(
                            context,
                            context.getString(R.string.loc_mirror_resend_done),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResendConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    shownCode?.let { code ->
        MirrorCodeDialog(
            code = code,
            onDismiss = { shownCode = null }
        )
    }
}

/** A connection method offered by this device, with the wording the user reads. */
private data class MirrorTransportOption(
    val transport: Int,
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector
)

/**
 * The methods a phone or watch can offer. Messages only appear for a Wear OS
 * peer, because on any other peer there is nothing on the other end to talk to.
 */
private fun mirrorTransportOptions(state: MirrorEditorState): List<MirrorTransportOption> {
    val messagesAllowed = Applic.isWearable || state.peerIsWearOs ||
            state.draft.transport == BleMirror.TRANSPORT_MESSAGES
    return buildList {
        add(
            MirrorTransportOption(
                transport = BleMirror.TRANSPORT_AUTOMATIC,
                titleRes = R.string.transport_automatic,
                subtitleRes = R.string.loc_transport_automatic_desc,
                icon = Icons.Default.Wifi
            )
        )
        add(
            MirrorTransportOption(
                transport = BleMirror.TRANSPORT_TCP,
                titleRes = R.string.transport_tcp,
                subtitleRes = R.string.loc_transport_tcp_desc,
                icon = Icons.Default.Lan
            )
        )
        if (messagesAllowed) {
            add(
                MirrorTransportOption(
                    transport = BleMirror.TRANSPORT_MESSAGES,
                    titleRes = R.string.loc_transport_wear_os,
                    subtitleRes = R.string.loc_transport_wear_os_desc,
                    icon = Icons.Default.Watch
                )
            )
        }
        add(
            MirrorTransportOption(
                transport = BleMirror.TRANSPORT_BLUETOOTH,
                titleRes = R.string.transport_bluetooth,
                subtitleRes = R.string.loc_transport_bluetooth_desc,
                icon = Icons.Default.Bluetooth
            )
        )
    }
}

/** Defaults for a connection that does not exist yet. */
private fun newMirrorDraft(): MirrorConnectionDraft = MirrorConnectionDraft(
    transport = BleMirror.TRANSPORT_AUTOMATIC,
    label = "",
    addresses = listOf(""),
    port = "17580",
    detectIp = false,
    testIp = true,
    receiveFrom = true,
    sendAmounts = false,
    sendStream = false,
    sendScans = false
)

/**
 * Keeps the draft consistent when the method changes: Bluetooth and Messages
 * identify a peer by label, so a password is asked for right away, and the
 * network only options are not left switched on behind a transport that ignores
 * them.
 */
private fun MirrorConnectionDraft.applyTransport(transport: Int): MirrorConnectionDraft {
    if (transport == this.transport) return this
    val needsPassword = transport == BleMirror.TRANSPORT_BLUETOOTH
    return copy(
        transport = transport,
        // The label travels over Bluetooth and Messages, so a peer that was named
        // for the network keeps its name and stays recognisable.
        label = label,
        usePassword = if (needsPassword) true else usePassword,
        addresses = addresses,
        detectIp = if (needsPassword || transport == BleMirror.TRANSPORT_MESSAGES) {
            false
        } else {
            detectIp
        }
    )
}

/** After a successful save: ask for Bluetooth and say the one thing worth knowing. */
private fun onMirrorSaved(context: Context, result: MirrorSaveResult) {
    if (result.needsBluetoothPermission) {
        (context as? Activity)?.let { Applic.requestBluetoothPermissions(it) }
    }
    val warning = result.blocker?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.loc_mirror_data_not_all).takeIf { result.partialData }
    warning?.let {
        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
    }
    Toast.makeText(
        context,
        context.getString(R.string.loc_connection_saved),
        Toast.LENGTH_SHORT
    ).show()
}

/** One of the mutually exclusive options, e.g. the connection method. */
@Composable
private fun MirrorChoiceRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(
            icon = icon,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        RadioButton(selected = selected, onClick = null)
    }
}

/** A compact selectable chip, used for the smaller multi-choice groups. */
@Composable
private fun MirrorSmallChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun MirrorSupportingText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Says out loud what still stops this connection from being saved. */
@Composable
private fun MirrorWarningText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = ScreenLayout.CardPadding, vertical = 10.dp)
    )
}

/** Renders a connection code as a QR image, with the plain text as a fallback. */
@Composable
fun MirrorCodeDialog(code: String, onDismiss: () -> Unit) {
    val bitmap: ImageBitmap? = remember(code) {
        QRmake.qrbitmap(code, 720)?.asImageBitmap()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.loc_mirror_show_code)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = stringResource(R.string.loc_mirror_show_code),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                }
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.closename))
            }
        }
    )
}

/** Shows the moment a "from the position this device shows" send would start at. */
private fun formatMirrorTime(epochSeconds: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000L))
