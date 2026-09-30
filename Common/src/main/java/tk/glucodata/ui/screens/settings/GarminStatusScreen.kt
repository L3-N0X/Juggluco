package tk.glucodata.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GarminLibre3Result
import tk.glucodata.ui.model.GarminStatus
import tk.glucodata.ui.model.GarminTransport
import tk.glucodata.ui.model.GarminWatchState
import tk.glucodata.util

/** The native Garmin status screen refreshed once a second, off the main thread. */
private const val GARMIN_STATUS_POLL_MS = 1_000L

/**
 * Status and per-watch configuration for Garmin watches.
 *
 * The transport reports what it knows at the moment it is asked and offers no
 * callback, so the screen reads on a timer instead of reacting to events. Only
 * the watch in [selectedPeerId] is configured here; the others are picked from
 * the same list the native screen showed in a spinner.
 */
@Composable
fun GarminStatusScreen(
    repository: GlucoseRepository,
    selectedPeerId: Long,
    onSelectPeer: (Long) -> Unit,
    onOpenConfig: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by repository.garminStatus.collectAsState()
    val watchConfig by repository.watchConfig.collectAsState()
    var watchMenuExpanded by remember { mutableStateOf(false) }
    var numbersChangeTo by remember { mutableStateOf<GarminWatchState?>(null) }
    var helpHtml by remember { mutableStateOf<String?>(null) }

    // Paired devices are asked for once on entry, because pairing happens in the
    // Android Bluetooth settings and a watch paired since start-up is otherwise
    // unknown. Every later tick only reads.
    LaunchedEffect(Unit) {
        repository.discoverGarminDevices()
        while (isActive) {
            delay(GARMIN_STATUS_POLL_MS)
            repository.refreshGarminStatus()
        }
    }

    val watch = status.watches.picked(selectedPeerId)
    LaunchedEffect(watch?.id) {
        if (watch != null && watch.id != selectedPeerId) onSelectPeer(watch.id)
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.loc_garmin_status_title),
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

        if (!watchConfig.garminEnabled) {
            SettingsInfoCard(icon = Icons.Default.Info) {
                Text(
                    text = stringResource(R.string.settings_garmin_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.settings_garmin_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- The watch in hand
        SettingsSection(title = stringResource(R.string.loc_garmin_watch_section)) {
            if (status.watches.isEmpty()) {
                SettingsInfoCard(icon = Icons.Default.Search) {
                    Text(
                        text = stringResource(R.string.loc_garmin_no_watches),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.loc_garmin_pair_instructions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                GarminWatchPicker(
                    watches = status.watches,
                    selected = watch,
                    expanded = watchMenuExpanded,
                    onExpandedChange = { watchMenuExpanded = it },
                    onSelect = {
                        watchMenuExpanded = false
                        onSelectPeer(it)
                    }
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.active),
                    icon = Icons.Default.Watch,
                    checked = watch?.active == true,
                    enabled = watch != null,
                    onCheckedChange = { enabled ->
                        watch?.let { repository.setGarminActive(it.id, enabled) }
                    }
                )

                // Glucose is a user preference, not a connection state, so it stays
                // reachable for any known watch and can be switched on before the
                // watch ever connects. The queued or latest value is sent once it
                // does. The native screen hid the row until a watch could be
                // reached, which hid the only way to set it up beforehand.
                SettingsSwitchRow(
                    title = stringResource(R.string.glucose),
                    subtitle = stringResource(R.string.loc_garmin_glucose_desc),
                    icon = Icons.Default.Bluetooth,
                    checked = watch?.glucose == true,
                    enabled = watch != null && status.sendGlucoseAvailable,
                    onCheckedChange = { enabled ->
                        watch?.let { repository.setGarminGlucose(it.id, enabled) }
                    }
                )

                SettingsSwitchRow(
                    title = stringResource(R.string.use_for_numbers),
                    subtitle = stringResource(R.string.loc_garmin_numbers_desc),
                    icon = Icons.Default.Bolt,
                    checked = watch?.numbers == true,
                    enabled = watch != null,
                    onCheckedChange = { enabled ->
                        val target = watch ?: return@SettingsSwitchRow
                        // Dropping the numbers role is a plain switch. Taking it
                        // replaces the stored numbers on the watch, and leaves the
                        // previous watch unable to enter any, so that is confirmed.
                        if (!enabled) {
                            repository.setGarminNumbersDevice(target.id, false)
                        } else if (!target.numbers) {
                            numbersChangeTo = target
                        }
                    }
                )

                if (watch?.libre3Installed == true) {
                    GarminLibre3Row(
                        watch = watch,
                        onChange = { direct ->
                            scope.launch {
                                val result = repository.requestGarminLibre3Direct(watch.id, direct)
                                if (result != GarminLibre3Result.APPLIED) {
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            if (result == GarminLibre3Result.NO_SENSOR) {
                                                R.string.garmin_libre3_no_active_sensor
                                            } else {
                                                R.string.garmin_select_device
                                            }
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                }
            }
        }

        // --- How the phone reaches the watch
        SettingsSection(title = stringResource(R.string.loc_garmin_actions_section)) {
            GarminTransportRow(
                mode = status.transportMode,
                enabled = watch != null,
                onChange = { repository.setGarminTransportMode(it) }
            )

            SettingsActionRow(
                title = stringResource(R.string.sync),
                subtitle = stringResource(
                    R.string.loc_garmin_numbers_device,
                    status.numbersDeviceName.orNone(context)
                ),
                icon = Icons.Default.Sync,
                enabled = watch != null && watch.active && watch.numbers,
                onClick = { watch?.let { repository.syncGarmin(it.id) } }
            )

            if (watch != null && watch.active && watch.numbers && watch.waiting && !status.sending) {
                SettingsActionRow(
                    title = stringResource(R.string.sendqueue),
                    icon = Icons.Default.Publish,
                    onClick = { repository.sendNextGarminMessage(watch.id) }
                )
            }

            SettingsActionRow(
                title = stringResource(R.string.reinit),
                icon = Icons.Default.RestartAlt,
                enabled = watch != null,
                onClick = { watch?.let { repository.reinitGarmin(it.id) } }
            )

            SettingsActionRow(
                title = stringResource(R.string.loc_garmin_search_watches),
                subtitle = stringResource(R.string.loc_garmin_search_watches_desc),
                icon = Icons.Default.Refresh,
                onClick = { repository.discoverGarminDevices() }
            )

            SettingsActionRow(
                title = stringResource(R.string.config),
                icon = Icons.Default.Settings,
                onClick = onOpenConfig
            )

            val help = remember { repository.garminHelpHtml("kerfstok") }
            if (help != null) {
                SettingsActionRow(
                    title = stringResource(R.string.helpname),
                    icon = Icons.Default.Info,
                    onClick = { helpHtml = help }
                )
            }
        }

        // --- What the transport last reported about the watch in hand
        watch?.let {
            SettingsSection(title = stringResource(R.string.loc_garmin_details_section)) {
                GarminDetails(watch = it)
            }
        }
    }

    numbersChangeTo?.let { target ->
        AlertDialog(
            onDismissRequest = { numbersChangeTo = null },
            title = { Text(stringResource(R.string.garmin_change_numbers_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.garmin_change_numbers_message,
                        status.numbersDeviceName.orNone(context),
                        target.name
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.setGarminNumbersDevice(target.id, true)
                    numbersChangeTo = null
                }) { Text(stringResource(R.string.garmin_change)) }
            },
            dismissButton = {
                TextButton(onClick = { numbersChangeTo = null }) {
                    Text(stringResource(R.string.cancel))
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
 * The watch whose settings and actions apply. The numbers watch is the one the
 * native screen opened on, because that is the watch a stored number goes to.
 */
private fun List<GarminWatchState>.picked(selectedPeerId: Long): GarminWatchState? =
    firstOrNull { it.id == selectedPeerId }
        ?: firstOrNull { it.numbers }
        ?: firstOrNull()

private fun String.orNone(context: Context): String =
    ifBlank { context.getString(R.string.garmin_none) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GarminWatchPicker(
    watches: List<GarminWatchState>,
    selected: GarminWatchState?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Long) -> Unit
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(icon = Icons.Default.WatchLater)
            Text(
                text = selected?.name.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            watches.forEach { watch ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.garmin_device_row,
                                watch.name,
                                stringResource(
                                    if (watch.connected) R.string.isconnected else R.string.isdisconnected
                                )
                            )
                        )
                    },
                    trailingIcon = if (watch.id == selected?.id) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    onClick = { onSelect(watch.id) }
                )
            }
        }
    }
}

@Composable
private fun GarminTransportRow(
    mode: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit
) {
    val options = listOf(
        GarminTransport.AUTOMATIC to R.string.transport_automatic,
        GarminTransport.CONNECT to R.string.via_garmin,
        GarminTransport.DIRECT to R.string.transport_bluetooth
    )
    SettingsSegmentedRow(
        title = stringResource(R.string.loc_garmin_transport_title),
        subtitle = stringResource(R.string.loc_garmin_transport_desc),
        icon = Icons.Default.Sync,
        enabled = enabled
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = mode == value,
                    enabled = enabled,
                    onClick = { if (mode != value) onChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(stringResource(label), maxLines = 1) }
                )
            }
        }
    }
}

@Composable
private fun GarminLibre3Row(
    watch: GarminWatchState,
    onChange: (Boolean) -> Unit
) {
    // The watch only reads the sensor itself once it is active and the Libre 3
    // application really is on it; before that the choice means nothing.
    val editable = watch.active
    SettingsSegmentedRow(
        title = stringResource(R.string.loc_garmin_libre3_connection),
        subtitle = stringResource(
            if (watch.libre3Direct) R.string.loc_garmin_libre3_watch_desc
            else R.string.loc_garmin_libre3_phone_desc
        ),
        icon = Icons.Default.Bluetooth,
        enabled = editable
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            SegmentedButton(
                selected = !watch.libre3Direct,
                enabled = editable,
                onClick = { if (watch.libre3Direct) onChange(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                label = { Text(stringResource(R.string.phone), maxLines = 1) }
            )
            SegmentedButton(
                selected = watch.libre3Direct,
                enabled = editable,
                onClick = { if (!watch.libre3Direct) onChange(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                label = { Text(stringResource(R.string.watch), maxLines = 1) }
            )
        }
    }
}

/**
 * What the transport reported, one line per fact, the way the native status
 * screen listed it. The native state names are lookup keys, not text.
 */
@Composable
private fun GarminDetails(watch: GarminWatchState) {
    val none = stringResource(R.string.garmin_none)
    val facts = mutableListOf<String>()
    facts += stringResource(
        R.string.loc_garmin_connected_via,
        stringResource(if (watch.direct) R.string.transport_bluetooth else R.string.garmin_connect)
    )
    facts += stringResource(
        R.string.garmin_kerfstok_status,
        stringResource(garminCommunicationStatusRes(watch.communicationStatus))
    )
    facts += stringResource(R.string.garmin_last_confirmed_reply, watch.lastAcknowledged.at(none))
    facts += stringResource(R.string.garmin_glucose_confirmed, watch.lastGlucoseAcknowledged.at(none))
    if (watch.lastGlucoseAcknowledged != 0L) {
        facts += stringResource(
            R.string.garmin_glucose_sample,
            util.timestring(watch.acknowledgedGlucoseTime * 1000L)
        )
    }
    facts += stringResource(
        R.string.garmin_glucose_ack,
        stringResource(
            if (watch.timestampedGlucoseAck) R.string.garmin_ack_timestamped else R.string.garmin_ack_legacy
        )
    )
    facts += stringResource(R.string.garmin_last_sent, watch.lastSend.at(none))
    facts += stringResource(R.string.garmin_last_received, watch.lastReceived.at(none))
    // A result older than the last message says nothing about what is happening
    // now, so it is only worth a line while it is the newest word.
    if (watch.transportResult != null && watch.lastStatusTime >= watch.lastSend) {
        facts += stringResource(
            R.string.garmin_transport_result,
            stringResource(garminTransportResultRes(watch.transportResult))
        )
    }
    watch.lastError?.let { error ->
        val text = when (error) {
            "No Kerfstok START acknowledgement" -> stringResource(R.string.garmin_no_start_ack)
            "No glucose acknowledgement" -> stringResource(R.string.garmin_no_glucose_ack)
            // Everything else is a diagnostic the transport wrote itself.
            else -> error
        }
        facts += stringResource(R.string.garmin_last_error, text)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        facts.forEach { fact ->
            Text(
                text = fact,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Long.at(none: String): String = if (this == 0L) none else util.timestring(this)

/**
 * The native transport reports its state as English words so the values stay
 * stable across versions and languages. They are keys here, never shown.
 */
@StringRes
private fun garminCommunicationStatusRes(state: String): Int = when (state) {
    "Kerfstok closed" -> R.string.garmin_status_closed
    "Recovering communication" -> R.string.garmin_status_recovering
    "Communication error" -> R.string.garmin_status_error
    "Connecting" -> R.string.garmin_status_connecting
    "Not connected" -> R.string.isdisconnected
    "Waiting for Kerfstok START" -> R.string.garmin_status_waiting_start
    "Connected; Kerfstok not yet ready" -> R.string.garmin_status_not_ready
    "Waiting for glucose acknowledgement" -> R.string.garmin_status_waiting_glucose
    "Waiting for history acknowledgement" -> R.string.garmin_status_waiting_history
    "Two-way communication confirmed" -> R.string.garmin_status_confirmed
    "Receiving; replies not yet confirmed" -> R.string.garmin_status_receiving
    "Sending; replies not yet confirmed" -> R.string.garmin_status_sending
    "Connected; communication not yet confirmed" -> R.string.garmin_status_unconfirmed
    else -> R.string.garmin_status_unknown
}

@StringRes
private fun garminTransportResultRes(result: String): Int = when (result) {
    "SUCCESS" -> R.string.success
    "FAILURE_DEVICE_NOT_CONNECTED" -> R.string.isdisconnected
    "FAILURE_DURING_TRANSFER" -> R.string.garmin_transfer_failed
    "FAILURE_INVALID_DEVICE" -> R.string.garmin_invalid_device
    "FAILURE_INVALID_FORMAT" -> R.string.garmin_invalid_format
    "FAILURE_MESSAGE_TOO_LARGE" -> R.string.garmin_message_too_large
    "FAILURE_UNSUPPORTED_TYPE" -> R.string.garmin_unsupported_type
    else -> R.string.garmin_unknown_failure
}
