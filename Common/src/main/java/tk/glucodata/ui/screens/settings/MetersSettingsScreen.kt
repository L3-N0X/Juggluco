package tk.glucodata.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.meters.MeterFound
import tk.glucodata.ui.meters.MeterInfo
import tk.glucodata.ui.meters.MeterRuntime
import tk.glucodata.ui.meters.MeterScanStatus
import java.text.DateFormat
import java.util.Calendar
import java.util.TimeZone

/**
 * The glucose meters that read over Bluetooth. This is a secondary feature for the few people who
 * still take finger-prick readings on a meter, so it is switched off until someone asks for it and
 * the rest of the screen stays away until then.
 */
@Composable
fun MetersSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val enabled by repository.metersEnabled.collectAsState()
    val meters by repository.meters.collectAsState()
    val runtimes by repository.meterRuntimes.collectAsState()
    val bloodLabels by repository.bloodLabels.collectAsState()
    val displayConfig by repository.displayConfig.collectAsState()

    // A static build keeps its amounts, so it cannot take in readings from a meter.
    val staticBuild = remember { try { Natives.staticnum() } catch (_: Throwable) { false } }

    var scanning by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MeterInfo?>(null) }
    var justFound by remember { mutableStateOf<MeterFound?>(null) }
    var confirmDelete by remember { mutableStateOf<MeterInfo?>(null) }

    // The connection state moves on its own, so it is reread while the list is on screen.
    LaunchedEffect(enabled) {
        while (enabled) {
            delay(STATUS_REFRESH_MILLIS)
            repository.refreshMeters()
        }
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.meterlist),
        onNavigateBack = onNavigateBack
    ) {
        SettingsSection(title = stringResource(R.string.meters_section_feature)) {
            SettingsSwitchRow(
                title = stringResource(R.string.meters_enable),
                subtitle = stringResource(R.string.meters_enable_desc),
                icon = Icons.Default.Bloodtype,
                checked = enabled,
                onCheckedChange = { repository.setMetersEnabled(it) }
            )
        }

        if (!enabled) {
            SettingsInfoCard(icon = Icons.Default.Info) {
                Text(
                    text = stringResource(R.string.meters_off_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@SettingsDetailScaffold
        }

        if (staticBuild) {
            SettingsInfoCard(icon = Icons.Default.Info) {
                Text(
                    text = stringResource(R.string.meters_static_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@SettingsDetailScaffold
        }

        SettingsSection(title = stringResource(R.string.meters_section_add)) {
            SettingsActionRow(
                title = stringResource(R.string.finddevices),
                subtitle = stringResource(R.string.meters_find_desc),
                icon = Icons.Default.Bluetooth,
                onClick = {
                    scanning = true
                    repository.startMeterScan()
                }
            )
        }

        if (meters.isEmpty()) {
            SettingsInfoCard(icon = Icons.Default.SearchOff) {
                Text(
                    text = stringResource(R.string.meters_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            SettingsSection(title = stringResource(R.string.meters_section_yours)) {
                meters.forEach { meter ->
                    MeterRow(
                        meter = meter,
                        runtime = runtimes.firstOrNull { it.index == meter.index },
                        onClick = {
                            justFound = null
                            editing = meter
                        },
                        onActiveChange = { repository.setMeterActive(meter, it) }
                    )
                }
            }
        }
    }

    if (scanning && enabled) {
        MeterDiscoveryDialog(
            repository = repository,
            onDismiss = {
                scanning = false
                repository.stopMeterScan()
            },
            onPicked = { found ->
                val meter = repository.addMeter(found.name, found.address)
                scanning = false
                repository.stopMeterScan()
                justFound = found
                editing = meter
            }
        )
    }

    if (enabled) editing?.let { meter ->
        MeterEditorDialog(
            meter = meter,
            bloodLabels = bloodLabels,
            selectedBloodLabel = displayConfig.bloodLabelIndex,
            canSelectBloodLabel = repository::canSelectBloodLabel,
            onSelectBloodLabel = repository::setBloodLabelIndex,
            onSave = { bloodAfter ->
                repository.saveMeter(meter, bloodAfter, justFound)
                justFound = null
                editing = null
            },
            onDelete = {
                editing = null
                confirmDelete = meter
            },
            onDismiss = {
                justFound = null
                editing = null
            }
        )
    }

    if (enabled) confirmDelete?.let { meter ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = {
                Text(
                    stringResource(R.string.deletemeter) + "\n\n" + meter.name
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.removeMeter(meter.index)
                    confirmDelete = null
                }) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/** How often the connection state of the meters is reread while the list is on screen. */
private const val STATUS_REFRESH_MILLIS = 2_000L

/**
 * One stored meter: its name, what is known about it and whether it is used. Tapping the row opens
 * its settings, the switch on the right only decides whether the phone talks to it.
 */
@Composable
private fun MeterRow(
    meter: MeterInfo,
    runtime: MeterRuntime?,
    onClick: () -> Unit,
    onActiveChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon = Icons.Default.Bloodtype)

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = meter.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (meter.address.isNotEmpty()) {
                Text(
                    text = meter.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (meter.lastReadingTime > 0L) {
                Text(
                    text = stringResource(R.string.last) + formatMeterTime(meter.lastReadingTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // A meter that is not used is not talked to, so there is no connection to report.
            if (meter.active && runtime != null) {
                val connected = runtime.connected
                val since = if (connected) runtime.connectedTime else runtime.disconnectedTime
                if (since > 0L) {
                    Text(
                        text = stringResource(if (connected) R.string.isconnected else R.string.isdisconnected) +
                            ": " + formatMeterTime(since),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (runtime.receivedTime == 0L) {
                        Text(
                            text = stringResource(
                                when {
                                    connected && runtime.bonded -> R.string.meters_bonded
                                    connected -> R.string.meters_not_bonded
                                    runtime.bonded -> R.string.meters_was_bonded
                                    else -> R.string.meters_was_not_bonded
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(
                                if (runtime.hasNewValues) R.string.newdata else R.string.nonewdata
                            ) + ": " + formatMeterTime(runtime.receivedTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.meters_not_connected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(checked = meter.active, onCheckedChange = onActiveChange)
    }
}

/**
 * The settings of one meter: which readings of it are taken, which label they are stored under,
 * and how to forget it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeterEditorDialog(
    meter: MeterInfo,
    bloodLabels: List<String>,
    selectedBloodLabel: Int,
    canSelectBloodLabel: (Int) -> Boolean,
    onSelectBloodLabel: (Int) -> Unit,
    onSave: (Long) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    // A meter that never gave a reading has no cutoff yet, which means everything it has.
    val initial = if (meter.lastReadingTime > 0L) meter.lastReadingTime else 0L
    var bloodAfter by remember { mutableStateOf(initial) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    // Readings are stored under the label for blood glucose, so without one the meter would
    // collect nothing at all and the user would have no idea why.
    val bloodLabelSet = selectedBloodLabel >= 0 && !bloodLabels.getOrNull(selectedBloodLabel).isNullOrBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(meter.name) },
        text = {
            Column {
                if (meter.address.isNotEmpty()) {
                    Text(
                        text = meter.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (!bloodLabelSet) {
                    Text(
                        text = stringResource(R.string.specifyblood),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = stringResource(R.string.bloodafter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pickingDate = true }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsIcon(icon = Icons.Default.Schedule)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (bloodAfter > 0L) {
                            DateFormat.getDateInstance(DateFormat.SHORT).format(bloodAfter)
                        } else {
                            stringResource(R.string.meters_all_readings)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (bloodAfter > 0L) {
                        TextButton(onClick = { pickingTime = true }) {
                            Text(
                                DateFormat.getTimeInstance(DateFormat.SHORT)
                                    .format(Calendar.getInstance().apply { timeInMillis = bloodAfter }.time)
                            )
                        }
                    } else {
                        TextButton(onClick = { pickingDate = true }) {
                            Text(stringResource(R.string.meters_pick_date))
                        }
                    }
                }

                var labelsOpen by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = labelsOpen,
                    onExpandedChange = { labelsOpen = it }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .clickable { labelsOpen = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIcon(icon = Icons.Default.Bloodtype)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.bloodvar),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = bloodLabels.getOrNull(selectedBloodLabel)
                                    ?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.specifyblood),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = labelsOpen)
                    }

                    ExposedDropdownMenu(
                        expanded = labelsOpen,
                        onDismissRequest = { labelsOpen = false }
                    ) {
                        bloodLabels.forEachIndexed { index, label ->
                            if (label.isNotBlank()) {
                                DropdownMenuItem(
                                    enabled = canSelectBloodLabel(index),
                                    text = { Text(label) },
                                    trailingIcon = if (selectedBloodLabel == index) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        onSelectBloodLabel(index)
                                        labelsOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = bloodLabelSet,
                onClick = { onSave(bloodAfter) }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )

    if (pickingDate) {
        MeterDatePickerDialog(
            initial = if (bloodAfter > 0L) bloodAfter else startOfToday(),
            onDismiss = { pickingDate = false },
            onConfirm = {
                bloodAfter = it
                pickingDate = false
            }
        )
    }

    if (pickingTime) {
        MeterTimePickerDialog(
            initial = bloodAfter,
            onDismiss = { pickingTime = false },
            onConfirm = {
                bloodAfter = it
                pickingTime = false
            }
        )
    }
}

/**
 * Picks the day the readings are taken from. Future days are refused, since nothing has been
 * measured yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeterDatePickerDialog(
    initial: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val start = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    // The picker works in UTC midnights, so the local calendar day is carried over as such.
    fun utcMidnight(calendar: Calendar) = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
    val today = remember { Calendar.getInstance() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = utcMidnight(start),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= utcMidnight(today)
            override fun isSelectableYear(year: Int) = year <= today.get(Calendar.YEAR)
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    val selected = state.selectedDateMillis ?: return@TextButton
                    val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selected }
                    onConfirm((start.clone() as Calendar).apply {
                        set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH))
                    }.timeInMillis)
                }
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    ) {
        DatePicker(state = state)
    }
}

/** Picks the time of day the readings are taken from, on the day that is already set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeterTimePickerDialog(
    initial: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val context = LocalContext.current
    val start = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    val state = rememberTimePickerState(
        initialHour = start.get(Calendar.HOUR_OF_DAY),
        initialMinute = start.get(Calendar.MINUTE),
        is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    )
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        onConfirm((start.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, state.hour)
                            set(Calendar.MINUTE, state.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis)
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

/**
 * Searches for meters nearby and lets one of them be added. The search runs until the dialog goes
 * away, because meters are only discoverable while they are awake.
 */
@Composable
private fun MeterDiscoveryDialog(
    repository: GlucoseRepository,
    onDismiss: () -> Unit,
    onPicked: (MeterFound) -> Unit
) {
    val scan by repository.meterScan.collectAsState()

    DisposableEffect(Unit) {
        onDispose { repository.stopMeterScan() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.finddevices),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.meters_find_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (scan.status) {
                        MeterScanStatus.UNSUPPORTED -> {
                            Text(stringResource(R.string.meters_unsupported))
                        }

                        MeterScanStatus.UNAVAILABLE -> {
                            Text(stringResource(R.string.turn_on_nearby_devices_permission))
                        }

                        MeterScanStatus.IDLE -> {
                            Text(
                                text = stringResource(R.string.meters_search_stopped),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        MeterScanStatus.SCANNING -> {
                            if (scan.found.isEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.height(18.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(stringResource(R.string.meters_searching))
                                }
                            } else {
                                Column {
                                    scan.found.forEachIndexed { position, found ->
                                        if (position > 0) HorizontalDivider()
                                        MeterFoundRow(found = found, onClick = { onPicked(found) })
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.closename)) }
                    if (scan.status != MeterScanStatus.SCANNING) {
                        TextButton(onClick = { repository.startMeterScan() }) {
                            Text(stringResource(R.string.meters_search_again))
                        }
                    }
                }
            }
        }
    }
}

/** One meter the search turned up. */
@Composable
private fun MeterFoundRow(found: MeterFound, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon = Icons.Default.Bluetooth)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = found.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            val subtitle = found.address.orEmpty()
            Text(
                text = if (found.alreadyAdded) subtitle else subtitle + "  " + stringResource(R.string.newname),
                style = MaterialTheme.typography.bodySmall,
                color = if (found.alreadyAdded) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

/** The first moment of today, the starting point when a cutoff is set for the first time. */
private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** A moment in the meter's own history, in the format the rest of the app uses. */
private fun formatMeterTime(millis: Long): String = try {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(millis)
} catch (_: Throwable) {
    ""
}
