package tk.glucodata.ui.components

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.Natives
import tk.glucodata.ui.model.GlucoseUnit

@Composable
fun CalibrationDialog(
    sensorPtr: Long,
    unit: GlucoseUnit,
    onDismiss: () -> Unit,
    onSaveCalibration: (Float) -> Unit
) {
    var isCalibrateActive by remember {
        mutableStateOf(try { Natives.getDoCalibrate() } catch (_: Throwable) { false })
    }
    var calibratePast by remember {
        mutableStateOf(try { Natives.getCalibratePast() } catch (_: Throwable) { false })
    }
    var allValues by remember {
        mutableStateOf(try { Natives.getAllValues() } catch (_: Throwable) { false })
    }

    var bloodGlucoseText by remember { mutableStateOf("") }
    var showAddPoint by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sensor Calibration", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Calibrate sensor readings against fingerprick reference values:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Calibration Active Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Enable Calibration", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Apply calibration curve to glucose stream", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = isCalibrateActive,
                        onCheckedChange = {
                            isCalibrateActive = it
                            try {
                                Natives.setDoCalibrate(it)
                                Natives.setshowcalibratedstream(it)
                            } catch (_: Throwable) {}
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // Calibrate Past Values Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Calibrate Past Readings", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Recalibrate earlier sensor history values", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = calibratePast,
                        onCheckedChange = {
                            calibratePast = it
                            try {
                                Natives.setCalibratePast(it)
                            } catch (_: Throwable) {}
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // Use All Values Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Use All Blood Glucose Checks", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Automatically incorporate fingerprick logs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = allValues,
                        onCheckedChange = {
                            allValues = it
                            try {
                                Natives.setAllValues(it)
                            } catch (_: Throwable) {}
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Add Calibration Point Section
                if (!showAddPoint) {
                    Button(
                        onClick = { showAddPoint = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Calibration Reference")
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("New Reference Check", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = bloodGlucoseText,
                                onValueChange = { bloodGlucoseText = it },
                                label = { Text("Fingerprick BG (${unit.label})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showAddPoint = false }) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        val raw = bloodGlucoseText.replace(',', '.').toFloatOrNull()
                                        if (raw != null && raw > 0) {
                                            val mgdl = if (unit == GlucoseUnit.MMOL_L) GlucoseUnit.MMOL_L.toMgDl(raw) else raw
                                            onSaveCalibration(mgdl)
                                            showAddPoint = false
                                            bloodGlucoseText = ""
                                        }
                                    }
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
