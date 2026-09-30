package tk.glucodata.ui.screens.settings

import android.text.format.DateFormat
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import tk.glucodata.LibreViewStatus
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.LibreRegion
import tk.glucodata.ui.model.LibreSaveError
import tk.glucodata.ui.screens.ScreenLayout
import java.text.DateFormat as JavaDateFormat
import java.util.Calendar
import java.util.TimeZone

/**
 * LibreView account, upload and resend settings.
 *
 * The password is a draft that lives on this screen only: it is typed here and handed to the
 * repository on save, so it is not kept in a process wide state holder. The account id works the
 * same way when it is written down by hand, because native only stores the number itself.
 */
@Composable
fun LibreViewSettingsScreen(
    repository: GlucoseRepository,
    onOpenTreatments: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val exchanges by repository.exchanges.collectAsState()
    val config by repository.libreView.collectAsState()
    val treatments by repository.libreTreatments.collectAsState()
    val amountsAllowed by repository.libreAmountsAllowed.collectAsState()
    val status by LibreViewStatus.status.collectAsState()

    val storedEmail = remember { try { Natives.getlibreemail() ?: "" } catch (_: Throwable) { "" } }
    val storedPassword = remember { try { Natives.getlibrepass() ?: "" } catch (_: Throwable) { "" } }

    var email by rememberSaveable { mutableStateOf(storedEmail) }
    var password by rememberSaveable { mutableStateOf(storedPassword) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    // A hand written id only exists as a number in native, so it is filled in once.
    var manualAccountIdText by rememberSaveable { mutableStateOf("") }
    // null while the screen has not decided differently from what is stored.
    var manualAccountIdOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var resendFrom by rememberSaveable { mutableLongStateOf(defaultResendMoment()) }
    var showResendDate by rememberSaveable { mutableStateOf(false) }
    var showResendTime by rememberSaveable { mutableStateOf(false) }
    var showResendConfirm by rememberSaveable { mutableStateOf(false) }
    var showAccountIdConfirm by rememberSaveable { mutableStateOf(false) }

    val manualAccountId = manualAccountIdOverride ?: config.manualAccountId

    // Once a hand written id is stored, show it in the field the user can still edit.
    LaunchedEffect(config.manualAccountId, config.accountId) {
        if (config.manualAccountId && manualAccountIdText.isBlank()) {
            manualAccountIdText = config.accountId.toString()
        }
    }

    // A requested account id arrives while the uploader thread runs, so every status line it
    // publishes is a moment to pick up what native knows now.
    LaunchedEffect(status) {
        if (status.isNotBlank()) {
            repository.refreshLibreView()
        }
    }

    val accountIdText = when {
        manualAccountId -> manualAccountIdText
        config.hasAccountId -> config.accountId.toString()
        else -> ""
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_title_libre_view),
        onNavigateBack = onNavigateBack
    ) {
        SettingsSection(title = stringResource(R.string.loc_cloud_sync)) {
            SettingsSwitchRow(
                title = stringResource(R.string.loc_automatic_cloud_upload),
                subtitle = stringResource(R.string.settings_libreview_desc),
                icon = Icons.Default.CloudSync,
                checked = exchanges.libreViewEnabled,
                onCheckedChange = { repository.setLibreViewEnabled(it) }
            )
        }

        SettingsSection(title = stringResource(R.string.loc_account_credentials)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp)
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
                                contentDescription = stringResource(R.string.loc_action_toggle_password)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = accountIdText,
                    onValueChange = { if (manualAccountId) manualAccountIdText = it },
                    label = { Text(stringResource(R.string.loc_account_patient_id)) },
                    singleLine = true,
                    readOnly = !manualAccountId,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            text = when {
                                manualAccountId -> stringResource(R.string.loc_libreview_account_written)
                                config.hasAccountId -> stringResource(R.string.loc_libreview_account_from_server)
                                else -> stringResource(R.string.loc_libreview_account_none)
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SettingsSegmentedRow(
                title = stringResource(R.string.loc_libreview_region),
                subtitle = stringResource(R.string.loc_libreview_region_desc),
                icon = Icons.Default.Public
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = ScreenLayout.CardPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LibreRegion.entries.forEach { region ->
                        FilterChip(
                            selected = config.region == region,
                            onClick = { repository.setLibreViewRegion(region) },
                            label = {
                                Text(
                                    text = stringResource(region.labelRes),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            SettingsSwitchRow(
                title = stringResource(R.string.loc_libreview_account_manual),
                subtitle = stringResource(R.string.loc_libreview_account_manual_desc),
                icon = Icons.Default.Edit,
                checked = manualAccountId,
                onCheckedChange = { manualAccountIdOverride = it }
            )

            SettingsActionRow(
                title = stringResource(R.string.loc_libreview_account_fetch),
                subtitle = stringResource(R.string.loc_libreview_account_fetch_desc),
                icon = Icons.Default.Download,
                enabled = !manualAccountId,
                onClick = { showAccountIdConfirm = true }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp)
            ) {
                Button(
                    onClick = {
                        val accountId = if (manualAccountId) {
                            manualAccountIdText.trim().toLongOrNull() ?: 0L
                        } else {
                            config.accountId
                        }
                        val error = repository.saveLibreViewAccount(
                            email = email.trim(),
                            password = password,
                            region = config.region,
                            accountId = accountId,
                            manualAccountId = manualAccountId,
                            sendAmounts = config.sendAmounts,
                            validateCredentials = exchanges.libreViewEnabled
                        )
                        if (error == LibreSaveError.NONE) {
                            // The stored state is authoritative again from here on.
                            manualAccountIdOverride = null
                        }
                        Toast.makeText(
                            context,
                            context.getString(error.messageRes),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                }
            }
        }

        SettingsSection(title = stringResource(R.string.loc_libreview_upload)) {
            SettingsSwitchRow(
                title = stringResource(R.string.sendamounts),
                subtitle = if (amountsAllowed) {
                    stringResource(R.string.loc_upload_amounts_desc)
                } else {
                    stringResource(R.string.libresetalllabels)
                },
                icon = Icons.Default.CloudUpload,
                checked = config.sendAmounts,
                enabled = amountsAllowed,
                onCheckedChange = { repository.setLibreViewSendAmounts(it) }
            )

            SettingsNavRow(
                title = stringResource(R.string.loc_libreview_treatments),
                subtitle = treatmentSummary(treatments.count { it.kind.isMapped }, treatments.size),
                icon = Icons.Default.Assignment,
                onClick = onOpenTreatments
            )

            SettingsSwitchRow(
                title = stringResource(R.string.loc_libreview_immediate),
                subtitle = stringResource(R.string.loc_libreview_immediate_desc),
                icon = Icons.Default.Bolt,
                checked = config.uploadCurrent,
                onCheckedChange = { repository.setLibreViewUploadCurrent(it) }
            )

            SettingsSwitchRow(
                title = stringResource(R.string.loc_libreview_viewed),
                subtitle = stringResource(R.string.loc_libreview_viewed_desc),
                icon = Icons.Default.History,
                checked = config.uploadViewed,
                onCheckedChange = { repository.setLibreViewUploadViewed(it) }
            )

            SettingsActionRow(
                title = stringResource(R.string.loc_common_upload_now),
                icon = Icons.AutoMirrored.Filled.Send,
                enabled = exchanges.libreViewEnabled,
                onClick = {
                    repository.startLibreViewUpload()
                    Toast.makeText(
                        context,
                        context.getString(R.string.loc_libreview_upload_started),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            SettingsActionRow(
                title = stringResource(R.string.loc_libreview_resend),
                subtitle = stringResource(
                    R.string.loc_libreview_resend_from,
                    formatResendMoment(resendFrom)
                ),
                icon = Icons.Default.Restore,
                onClick = { showResendDate = true }
            )
        }

        SettingsSection(title = stringResource(R.string.loc_libreview_status)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp)
            ) {
                Text(
                    text = status.ifBlank { stringResource(R.string.loc_libreview_status_none) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SettingsInfoCard(
            text = stringResource(R.string.loc_libreview_info),
            icon = Icons.Default.Info
        )
    }

    if (showAccountIdConfirm) {
        AlertDialog(
            onDismissRequest = { showAccountIdConfirm = false },
            title = { Text(stringResource(R.string.getaccountidquestion)) },
            text = { Text(stringResource(R.string.getaccountidmessage)) },
            confirmButton = {
                Button(onClick = {
                    showAccountIdConfirm = false
                    repository.requestLibreViewAccountId()
                    Toast.makeText(
                        context,
                        context.getString(R.string.loc_libreview_account_requested),
                        Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountIdConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showResendDate) {
        LibreResendDatePickerDialog(
            initial = resendFrom,
            onDismiss = { showResendDate = false },
            onConfirm = {
                resendFrom = it
                showResendDate = false
                showResendTime = true
            }
        )
    }

    if (showResendTime) {
        LibreResendTimePickerDialog(
            initial = resendFrom,
            onDismiss = { showResendTime = false },
            onConfirm = {
                resendFrom = it
                showResendTime = false
                showResendConfirm = true
            }
        )
    }

    if (showResendConfirm) {
        AlertDialog(
            onDismissRequest = { showResendConfirm = false },
            title = { Text(stringResource(R.string.resendquestion)) },
            text = { Text(stringResource(R.string.resendmessage)) },
            confirmButton = {
                Button(onClick = {
                    showResendConfirm = false
                    repository.resendLibreViewFrom(resendFrom)
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.loc_libreview_resend_done,
                            formatResendMoment(resendFrom)
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }) {
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
}

/**
 * The resend window opens where the legacy dialog opened it: far enough back to cover a full
 * upload, and never in the future.
 */
private fun defaultResendMoment(): Long =
    (System.currentTimeMillis() - RESEND_DEFAULT_WINDOW_MILLIS).coerceAtLeast(0L)

private fun formatResendMoment(moment: Long): String =
    JavaDateFormat.getDateTimeInstance(JavaDateFormat.DEFAULT, JavaDateFormat.SHORT).format(moment)

/** How many labels carry a treatment kind, as the mapping row's subtitle. */
@Composable
private fun treatmentSummary(mapped: Int, total: Int): String = stringResource(
    R.string.loc_libreview_treatments_progress,
    mapped,
    total,
    stringResource(R.string.loc_libreview_treatments_unmapped)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibreResendDatePickerDialog(
    initial: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val start = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    // The picker works in UTC midnights, so the local calendar day is carried over as such.
    val today = remember { Calendar.getInstance() }
    fun utcMidnight(calendar: Calendar) = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
    val latest = remember { utcMidnight(today) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = utcMidnight(start),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= latest
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
                    val picked = (start.clone() as Calendar).apply {
                        set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH))
                    }
                    onConfirm(picked.timeInMillis.coerceAtLeast(0L))
                }
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibreResendTimePickerDialog(
    initial: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val context = LocalContext.current
    val start = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    val state = rememberTimePickerState(
        initialHour = start.get(Calendar.HOUR_OF_DAY),
        initialMinute = start.get(Calendar.MINUTE),
        is24Hour = DateFormat.is24HourFormat(context)
    )
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        val picked = (start.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, state.hour)
                            set(Calendar.MINUTE, state.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onConfirm(picked.timeInMillis.coerceAtLeast(0L))
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

/** Long enough to cover a full upload, the same window the legacy resend dialog offered. */
private const val RESEND_DEFAULT_WINDOW_MILLIS = 89L * 24L * 60L * 60L * 1000L
