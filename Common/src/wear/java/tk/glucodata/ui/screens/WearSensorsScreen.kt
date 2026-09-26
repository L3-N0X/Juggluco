package tk.glucodata.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import kotlinx.coroutines.delay
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.MirrorConnection
import tk.glucodata.ui.model.SensorState
import tk.glucodata.ui.theme.LocalClinicalColors

@Composable
fun WearSensorsScreen(
    repository: GlucoseRepository,
    onTriggerNfcScan: () -> Unit,
    onSyncPhone: () -> Unit
) {
    val sensors by repository.sensors.collectAsState()
    val mirrorConnections by repository.mirrorConnections.collectAsState()
    val clinical = LocalClinicalColors.current
    var syncRequested by remember { mutableStateOf(false) }

    val activeMirrors = mirrorConnections.filterNot { it.isDeactivated }
    val livePhoneMirrors = activeMirrors.filter { it.hasLivePhoneCarrier() }
    val phoneStatus = when {
        livePhoneMirrors.isNotEmpty() -> stringResource(R.string.wear_phone_receiving)
        activeMirrors.isNotEmpty() -> stringResource(R.string.wear_phone_unavailable)
        else -> stringResource(R.string.wear_phone_no_link)
    }

    LaunchedEffect(Unit) {
        repository.refreshMirrorConnections()
    }
    LaunchedEffect(syncRequested) {
        if (syncRequested) {
            delay(2_000L)
            syncRequested = false
        }
    }

    val listState = rememberScalingLazyListState()

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ListHeader {
                    Text(stringResource(R.string.wear_sensors_status_title))
                }
            }

            item {
                Button(
                    onClick = onTriggerNfcScan,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Nfc,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                    },
                    label = {
                        Text(stringResource(R.string.wear_scan_sensor))
                    }
                )
            }

            item {
                ListSubHeader {
                    Text(stringResource(R.string.sensors))
                }
            }

            if (sensors.isEmpty()) {
                item {
                    FilledTonalButton(
                        onClick = onTriggerNfcScan,
                        modifier = Modifier.fillMaxWidth(),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize)
                            )
                        },
                        label = {
                            Text(stringResource(R.string.wear_scan_or_pair_sensor))
                        },
                        secondaryLabel = {
                            Text(stringResource(R.string.wear_no_active_sensor))
                        }
                    )
                }
            } else {
                items(sensors.size) { index ->
                    val sensor = sensors[index]
                    val stateLabel = stringResource(sensor.state.labelRes)
                    val remainingLabel = if (sensor.daysRemaining > 0f) {
                        stringResource(R.string.wear_sensor_days_remaining, sensor.daysRemaining)
                    } else {
                        null
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = sensor.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stateLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (sensor.state == SensorState.ACTIVE) clinical.inRange else clinical.low
                                    )
                                    if (remainingLabel != null) {
                                        Text(
                                            text = " • $remainingLabel",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                ListSubHeader {
                    Text(stringResource(R.string.wear_phone_connection))
                }
            }

            item {
                FilledTonalButton(
                    onClick = {
                        syncRequested = true
                        onSyncPhone()
                    },
                    enabled = !syncRequested,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.wear_sync_with_phone),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = if (syncRequested) {
                                stringResource(R.string.wear_sync_requested)
                            } else {
                                phoneStatus
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

private fun MirrorConnection.hasLivePhoneCarrier(): Boolean {
    val normalized = status.replace(Regex("<[^>]+>"), "")
    return (normalized.contains("TCP/IP live socket: true", ignoreCase = true) &&
        normalized.contains("receive=true", ignoreCase = true)) ||
        normalized.contains("Direct Bluetooth (BLE GATT)=true", ignoreCase = true) ||
        normalized.contains("Messages (Wear OS MessageClient)=true", ignoreCase = true)
}
