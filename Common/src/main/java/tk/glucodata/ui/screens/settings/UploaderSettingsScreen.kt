package tk.glucodata.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tk.glucodata.Applic
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.TreatmentMapping
import java.text.DateFormat
import java.util.Date

private const val UPLOADER_STATUS_POLL_MS = 5000L
private const val UPLOADER_MAX_URL_LENGTH = 255
private const val UPLOADER_MAX_SECRET_LENGTH = 79
private const val TREATMENT_KIND_RAPID = 1
private const val TREATMENT_KIND_LONG = 2
private const val TREATMENT_KIND_FOOD = 3
private const val TREATMENT_KIND_NOTE = 4
private const val TREATMENT_KIND_NONE = 5

@Composable
fun UploaderSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uploader by repository.uploader.collectAsState()
    val status by repository.uploaderStatus.collectAsState()
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(uploader.url) }
    var secret by remember { mutableStateOf(repository.uploaderSecret()) }
    var showSecret by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(uploader.active) }
    var v3 by remember { mutableStateOf(uploader.v3) }
    var sendAmounts by remember { mutableStateOf(uploader.postTreatments) }
    var testing by remember { mutableStateOf(false) }
    var showResendConfirm by remember { mutableStateOf(false) }
    var showMapping by remember { mutableStateOf(false) }

    val trimmedUrl = url.trim()
    val urlValid = trimmedUrl.isNotEmpty() &&
        (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) &&
        !trimmedUrl.any { it.isWhitespace() }
    val secretValid = secret.all { it.code in 0x20..0x7E }

    LaunchedEffect(Unit) {
        while (true) {
            repository.refreshUploaderStatus()
            delay(UPLOADER_STATUS_POLL_MS)
        }
    }

    val statusTime = remember(status.timeMillis) {
        if (status.timeMillis > 0L) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(status.timeMillis))
        } else {
            null
        }
    }
    val statusFailed = remember(status.text) { isUploaderFailure(status.text) }
    val testLabel = stringResource(R.string.loc_uploader_test)
    val testingLabel = stringResource(R.string.loc_uploader_testing)
    val resendDoneLabel = context.getString(R.string.loc_uploader_resend_done)

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_title_uploader),
        onNavigateBack = onNavigateBack
    ) {
        // NIGHTSCOUT CONNECTION
        SettingsSection(title = stringResource(R.string.loc_uploader_server)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { if (it.length <= UPLOADER_MAX_URL_LENGTH) url = it },
                    label = { Text(stringResource(R.string.loc_uploader_url_label)) },
                    supportingText = {
                        Text(
                            stringResource(
                                if (url.isEmpty() || urlValid) R.string.loc_uploader_url_help
                                else R.string.loc_uploader_url_invalid
                            )
                        )
                    },
                    isError = url.isNotEmpty() && !urlValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = secret,
                    onValueChange = { if (it.length <= UPLOADER_MAX_SECRET_LENGTH) secret = it },
                    label = { Text(stringResource(R.string.secret)) },
                    supportingText = { Text(stringResource(R.string.loc_uploader_secret_help)) },
                    isError = !secretValid,
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
            }

            SettingsSwitchRow(
                title = stringResource(R.string.active),
                subtitle = stringResource(R.string.loc_uploader_active_desc),
                icon = Icons.Default.CloudUpload,
                checked = active,
                onCheckedChange = { active = it }
            )

            if (!Applic.isWearable) {
                SettingsSwitchRow(
                    title = stringResource(R.string.loc_uploader_v3),
                    subtitle = stringResource(R.string.loc_uploader_v3_desc),
                    icon = Icons.Default.Code,
                    checked = v3,
                    onCheckedChange = { v3 = it }
                )
            }
        }

        // TREATMENTS
        SettingsSection(title = stringResource(R.string.loc_uploader_treatments)) {
            SettingsSwitchRow(
                title = stringResource(R.string.sendamounts),
                subtitle = stringResource(R.string.loc_uploader_treatments_desc),
                icon = Icons.Default.CloudUpload,
                checked = sendAmounts,
                onCheckedChange = { enabled ->
                    if (enabled && !uploader.canSendTreatments) {
                        Toast.makeText(context, R.string.libresetalllabels, Toast.LENGTH_LONG).show()
                        return@SettingsSwitchRow
                    }
                    sendAmounts = enabled
                    repository.setUploaderPostTreatments(enabled)
                }
            )

            SettingsNavRow(
                title = stringResource(R.string.loc_uploader_label_mapping),
                subtitle = stringResource(
                    if (uploader.canSendTreatments) R.string.loc_uploader_label_mapping_desc
                    else R.string.libresetalllabels
                ),
                icon = Icons.Default.Tune,
                onClick = { showMapping = true }
            )
        }

        // ACTIONS
        SettingsSection(title = stringResource(R.string.loc_uploader_actions)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (!urlValid) {
                            Toast.makeText(context, R.string.loc_uploader_url_invalid, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!secretValid) {
                            Toast.makeText(context, R.string.loc_uploader_secret_invalid, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        repository.saveUploaderConfig(trimmedUrl, secret, active, v3)
                        Toast.makeText(context, R.string.loc_uploader_saved, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        if (!urlValid) {
                            Toast.makeText(context, R.string.loc_uploader_url_invalid, Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        testing = true
                        scope.launch {
                            val result = repository.testUploader(trimmedUrl, secret)
                            testing = false
                            val message = if (isUploaderFailure(result)) {
                                val detail = result.lineSequence().firstOrNull().orEmpty()
                                    .ifEmpty { testingLabel }
                                context.getString(R.string.loc_upload_failed, detail)
                            } else {
                                context.getString(R.string.loc_uploader_test_ok)
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (testing) testingLabel else testLabel, fontSize = 12.sp)
                }
            }
        }

        SettingsSection {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { repository.sendUploaderNow() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.loc_uploader_sendnow), fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { showResendConfirm = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.resenddata), fontSize = 12.sp)
                }
            }

            Text(
                text = stringResource(R.string.loc_uploader_resend_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            )
        }

        // LAST UPLOAD STATUS
        SettingsSection(title = stringResource(R.string.loc_uploader_status)) {
            Text(
                text = statusTime?.let { "$it: ${status.text}" } ?: status.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (statusFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }

        SettingsInfoCard(
            text = stringResource(R.string.loc_uploader_info),
            icon = Icons.Default.Info
        )
    }

    if (showResendConfirm) {
        AlertDialog(
            onDismissRequest = { showResendConfirm = false },
            title = { Text(stringResource(R.string.resenddata)) },
            text = { Text(stringResource(R.string.loc_uploader_resend_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResendConfirm = false
                        repository.resendUploaderData()
                        Toast.makeText(context, resendDoneLabel, Toast.LENGTH_SHORT).show()
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

    if (showMapping) {
        TreatmentMappingDialog(
            repository = repository,
            onDismiss = { showMapping = false }
        )
    }
}

private fun isUploaderFailure(text: String): Boolean =
    text.isBlank() ||
        text.contains("failure", ignoreCase = true) ||
        text.contains("failed", ignoreCase = true)

@Composable
private fun TreatmentMappingDialog(
    repository: GlucoseRepository,
    onDismiss: () -> Unit
) {
    var mappings by remember { mutableStateOf(repository.uploaderTreatmentMappings()) }
    var editing by remember { mutableStateOf(-1) }
    val pendingWeights = remember { mutableStateMapOf<Int, String>() }
    val kindNames = listOf(
        stringResource(R.string.rapidinsulin),
        stringResource(R.string.longinsulin),
        stringResource(R.string.carbo),
        stringResource(R.string.comments),
        stringResource(R.string.dontsend)
    )
    val unsetName = stringResource(R.string.loc_uploader_label_unset)

    fun nameForKind(kind: Int): String =
        if (kind in TREATMENT_KIND_RAPID..TREATMENT_KIND_NONE) kindNames[kind - 1] else unsetName

    fun selectKind(mapping: TreatmentMapping, kind: Int) {
        val weight = pendingWeights[mapping.index]?.toFloatOrNull() ?: mapping.weight
        val usedWeight = if (kind == TREATMENT_KIND_FOOD) weight else 0f
        repository.setUploaderTreatmentMapping(mapping.index, kind, usedWeight)
        mappings = mappings.map {
            if (it.index == mapping.index) it.copy(kind = kind, weight = usedWeight) else it
        }
    }

    fun commitPendingWeights() {
        pendingWeights.forEach { (index, text) ->
            val weight = text.toFloatOrNull() ?: return@forEach
            val mapping = mappings.firstOrNull { it.index == index } ?: return@forEach
            if (weight > 0f && weight != mapping.weight) {
                repository.setUploaderTreatmentMapping(index, mapping.kind, weight)
            }
        }
        pendingWeights.clear()
    }

    AlertDialog(
        onDismissRequest = {
            commitPendingWeights()
            onDismiss()
        },
        title = { Text(stringResource(R.string.loc_uploader_label_mapping)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.loc_uploader_label_mapping_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    items(mappings, key = { it.index }) { mapping ->
                        val expanded = editing == mapping.index
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (expanded) {
                                        commitPendingWeights()
                                        editing = -1
                                    } else {
                                        editing = mapping.index
                                    }
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = mapping.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = nameForKind(mapping.kind),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (mapping.kind == 0) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            if (expanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    for (kind in TREATMENT_KIND_RAPID..TREATMENT_KIND_NONE) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectKind(mapping, kind) },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = mapping.kind == kind,
                                                onClick = { selectKind(mapping, kind) }
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = kindNames[kind - 1],
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                    if (mapping.kind == TREATMENT_KIND_FOOD) {
                                        OutlinedTextField(
                                            value = pendingWeights[mapping.index]
                                                ?: mapping.weight.takeIf { it > 0f }?.toString().orEmpty(),
                                            onValueChange = { pendingWeights[mapping.index] = it },
                                            label = { Text(stringResource(R.string.loc_uploader_weight)) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Decimal
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    commitPendingWeights()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.closename))
            }
        }
    )
}
