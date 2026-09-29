package tk.glucodata.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tk.glucodata.R
import tk.glucodata.ui.data.SensorActivationState

enum class SupportedSensorType(
    @androidx.annotation.StringRes val titleRes: Int,
    @androidx.annotation.StringRes val subtitleRes: Int,
    @androidx.annotation.StringRes val pairingMethodRes: Int,
    val icon: ImageVector,
    val warmupMinutes: Int,
    val usesNfc: Boolean
) {
    LIBRE_3(R.string.sensor_name_libre_3, R.string.sensor_supported_type_ble, R.string.sensor_pairing_nfc, Icons.Default.Nfc, 60, true),
    LIBRE_2(R.string.sensor_name_libre_2, R.string.sensor_supported_type_alarms, R.string.sensor_pairing_nfc, Icons.Default.Nfc, 60, true),
    DEXCOM_G7(R.string.sensor_name_dexcom_g7, R.string.sensor_supported_type_direct, R.string.sensor_pairing_bluetooth, Icons.Default.Bluetooth, 30, false),
    SIBIONICS(R.string.sensor_name_sibionics, R.string.sensor_supported_type_continuous, R.string.sensor_pairing_qr_ble, Icons.Default.QrCodeScanner, 60, false)
}

@Composable
fun StartSensorDialog(
    activationState: SensorActivationState,
    onStartScan: (SupportedSensorType) -> Unit,
    onRetry: (SupportedSensorType) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onAddToCalendar: (String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSensor by remember { mutableStateOf(SupportedSensorType.LIBRE_3) }
    var showInstructions by remember { mutableStateOf(false) }
    var addToCalendar by remember(activationState) {
        mutableStateOf(activationState is SensorActivationState.Success && activationState.canAddToCalendar)
    }

    val isBusy = activationState is SensorActivationState.Waiting ||
        activationState is SensorActivationState.Reading ||
        activationState is SensorActivationState.Activating ||
        activationState is SensorActivationState.AwaitingSecondScan ||
        activationState is SensorActivationState.Verifying
    val isFinished = activationState is SensorActivationState.Success ||
        activationState is SensorActivationState.Failure ||
        activationState is SensorActivationState.Cancelled

    AlertDialog(
        onDismissRequest = {
            if (isBusy) onCancel() else onDismiss()
        },
        title = {
            Text(
                text = when (activationState) {
                    SensorActivationState.Idle -> if (showInstructions) {
                        stringResource(R.string.sensor_activate_title, stringResource(selectedSensor.titleRes))
                    } else {
                        stringResource(R.string.sensor_start_new_title)
                    }
                    SensorActivationState.Waiting -> stringResource(R.string.sensor_activation_waiting_title)
                    SensorActivationState.Reading -> stringResource(R.string.sensor_activation_reading_title)
                    SensorActivationState.Activating -> stringResource(R.string.sensor_activation_activating_title)
                    SensorActivationState.AwaitingSecondScan -> stringResource(R.string.sensor_activation_second_scan_title)
                    SensorActivationState.Verifying -> stringResource(R.string.sensor_activation_verifying_title)
                    is SensorActivationState.Success -> stringResource(R.string.sensor_started_success)
                    is SensorActivationState.Failure -> stringResource(R.string.sensor_activation_failed_title)
                    SensorActivationState.Cancelled -> stringResource(R.string.sensor_activation_cancelled_title)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (activationState) {
                    SensorActivationState.Idle -> {
                        if (showInstructions) {
                            InstructionStep(
                                number = "1",
                                title = stringResource(R.string.sensor_step_clean_title),
                                desc = stringResource(R.string.sensor_step_clean_desc)
                            )
                            InstructionStep(
                                number = "2",
                                title = stringResource(R.string.sensor_step_position_title),
                                desc = stringResource(R.string.sensor_step_position_desc)
                            )
                            InstructionStep(
                                number = "3",
                                title = stringResource(R.string.sensor_step_streaming_title),
                                desc = stringResource(R.string.sensor_step_streaming_desc)
                            )
                            Text(
                                text = stringResource(R.string.sensor_nfc_active_ready),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.sensor_select_model_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            SupportedSensorType.entries.forEach { sensorType ->
                                SensorTypeItem(
                                    sensorType = sensorType,
                                    selected = sensorType == selectedSensor,
                                    onClick = { selectedSensor = sensorType }
                                )
                            }
                        }
                    }
                    SensorActivationState.Waiting,
                    SensorActivationState.Reading,
                    SensorActivationState.Activating,
                    SensorActivationState.AwaitingSecondScan,
                    SensorActivationState.Verifying -> {
                        ActivationProgress(
                            icon = selectedSensor.icon,
                            title = when (activationState) {
                                SensorActivationState.Waiting -> stringResource(R.string.sensor_activation_waiting_desc)
                                SensorActivationState.Reading -> stringResource(R.string.sensor_activation_reading_desc)
                                SensorActivationState.Activating -> stringResource(R.string.sensor_activation_activating_desc)
                                SensorActivationState.AwaitingSecondScan -> stringResource(R.string.sensor_activation_second_scan_desc)
                                else -> stringResource(R.string.sensor_activation_verifying_desc)
                            },
                            showProgress = true
                        )
                    }
                    is SensorActivationState.Success -> {
                        val activatedSensorName = activationState.sensorTypeName?.takeIf { it.isNotBlank() }
                            ?: activationState.sensorName
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = stringResource(
                                    R.string.sensor_started_warmup_desc,
                                    activatedSensorName,
                                    activationState.warmupMinutes
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (activationState.canAddToCalendar) {
                                ListItem(
                                    headlineContent = {
                                        Text(stringResource(R.string.addsensorenddate))
                                    },
                                    supportingContent = {
                                        Text(formatExpiry(activationState.endTime))
                                    },
                                    leadingContent = {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                                    },
                                    trailingContent = {
                                        Switch(
                                            checked = addToCalendar,
                                            onCheckedChange = { addToCalendar = it }
                                        )
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                )
                            }
                        }
                    }
                    is SensorActivationState.Failure -> {
                        StatusMessage(
                            icon = Icons.Default.ErrorOutline,
                            title = stringResource(R.string.sensor_activation_failed_desc),
                            body = stringResource(R.string.sensor_activation_failed_retry_desc),
                            isError = true
                        )
                    }
                    SensorActivationState.Cancelled -> {
                        StatusMessage(
                            icon = Icons.Default.Sensors,
                            title = stringResource(R.string.sensor_activation_cancelled_desc),
                            body = stringResource(R.string.sensor_activation_cancelled_retry_desc)
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                activationState is SensorActivationState.Success -> {
                    Button(
                        onClick = {
                            if (addToCalendar && activationState.canAddToCalendar) {
                                onAddToCalendar(activationState.sensorName, activationState.endTime)
                            }
                            onClose()
                        }
                    ) {
                        Text(stringResource(R.string.dialog_done))
                    }
                }
                activationState is SensorActivationState.Failure || activationState is SensorActivationState.Cancelled -> {
                    Button(onClick = { onRetry(selectedSensor) }) {
                        Text(stringResource(R.string.sensor_activation_retry))
                    }
                }
                activationState is SensorActivationState.Idle && showInstructions -> {
                    Button(onClick = { onStartScan(selectedSensor) }) {
                        Text(
                            stringResource(
                                if (selectedSensor.usesNfc) R.string.sensor_scan_nfc_now else R.string.sensor_pairing_start
                            )
                        )
                    }
                }
                activationState is SensorActivationState.Idle -> {
                    Button(onClick = { showInstructions = true }) {
                        Text(stringResource(R.string.next))
                    }
                }
            }
        },
        dismissButton = {
            when {
                isBusy -> {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                activationState is SensorActivationState.Success -> Unit
                activationState is SensorActivationState.Failure || activationState is SensorActivationState.Cancelled -> {
                    TextButton(onClick = onClose) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                activationState is SensorActivationState.Idle && showInstructions -> {
                    TextButton(onClick = { showInstructions = false }) {
                        Text(stringResource(R.string.back))
                    }
                }
                activationState is SensorActivationState.Idle -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    )
}

@Composable
private fun SensorTypeItem(
    sensorType: SupportedSensorType,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = stringResource(sensorType.titleRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(stringResource(sensorType.subtitleRes))
        },
        leadingContent = {
            Icon(sensorType.icon, contentDescription = null)
        },
        trailingContent = {
            RadioButton(selected = selected, onClick = onClick)
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    )
}

@Composable
private fun InstructionStep(number: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = number,
                modifier = Modifier.padding(6.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActivationProgress(
    icon: ImageVector,
    title: String,
    showProgress: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        if (showProgress) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun StatusMessage(
    icon: ImageVector,
    title: String,
    body: String,
    isError: Boolean = false
) {
    val tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatExpiry(endTime: Long): String {
    if (endTime <= 0L) return ""
    return java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM,
        java.text.DateFormat.SHORT,
        java.util.Locale.getDefault()
    ).format(java.util.Date(endTime))
}
