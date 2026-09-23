package tk.glucodata.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TitleCard
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.SensorState
import tk.glucodata.ui.theme.LocalClinicalColors
import java.util.Locale

@Composable
fun WearSensorsScreen(
    repository: GlucoseRepository,
    onTriggerNfcScan: () -> Unit
) {
    val sensors by repository.sensors.collectAsState()
    val mirrorConnections by repository.mirrorConnections.collectAsState()
    val clinical = LocalClinicalColors.current

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
                    Text("Sensors & Status")
                }
            }

            // NFC Scan Action
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
                        Text("Scan Sensor")
                    }
                )
            }

            // Active sensors section
            item {
                ListSubHeader {
                    Text("Sensors")
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
                            Text("No Active Sensor")
                        }
                    )
                }
            } else {
                items(sensors.size) { index ->
                    val sensor = sensors[index]
                    TitleCard(
                        onClick = {},
                        title = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sensor.name,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = stringResource(sensor.state.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sensor.state == SensorState.ACTIVE) clinical.inRange else clinical.low
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (sensor.daysRemaining > 0f) {
                            Text(
                                text = String.format(Locale.US, "%.1f days remaining", sensor.daysRemaining),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Phone Mirror Sync Info
            item {
                ListSubHeader {
                    Text("Phone Sync")
                }
            }

            item {
                val activeMirrors = mirrorConnections.filter { !it.isDeactivated }
                TitleCard(
                    onClick = {},
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (activeMirrors.isNotEmpty()) "Connected" else "Direct Mode",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (activeMirrors.isNotEmpty()) {
                            "${activeMirrors.size} active connection(s)"
                        } else {
                            "Direct sensor mode"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
