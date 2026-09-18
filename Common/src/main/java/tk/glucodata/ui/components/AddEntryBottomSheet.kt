package tk.glucodata.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LogType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryBottomSheet(
    sheetState: SheetState,
    unit: GlucoseUnit,
    onDismiss: () -> Unit,
    onSave: (LogType, Float, String) -> Unit
) {
    var selectedType by remember { mutableStateOf(LogType.RAPID_INSULIN) }
    var valueText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "Log Entry",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Type selector chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(LogType.RAPID_INSULIN, LogType.CARBS, LogType.BLOOD_GLUCOSE, LogType.BASAL_INSULIN).forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = {
                            Text(
                                text = when (type) {
                                    LogType.RAPID_INSULIN -> "Bolus"
                                    LogType.CARBS -> "Carbs"
                                    LogType.BLOOD_GLUCOSE -> "Finger Prick"
                                    LogType.BASAL_INSULIN -> "Basal"
                                    else -> type.label
                                }
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Value Input Field
            val valueLabel = when (selectedType) {
                LogType.RAPID_INSULIN, LogType.BASAL_INSULIN -> "Insulin Units (U)"
                LogType.CARBS, LogType.MEAL -> "Carbohydrates (grams)"
                LogType.BLOOD_GLUCOSE -> "Blood Glucose (${unit.label})"
                LogType.NOTE -> "Amount"
            }

            OutlinedTextField(
                value = valueText,
                onValueChange = { valueText = it },
                label = { Text(valueLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick preset buttons
            val presets = when (selectedType) {
                LogType.RAPID_INSULIN -> listOf(1f, 2f, 3f, 4f, 6f, 8f)
                LogType.CARBS -> listOf(15f, 30f, 45f, 60f, 75f)
                LogType.BASAL_INSULIN -> listOf(10f, 14f, 18f, 22f)
                LogType.BLOOD_GLUCOSE -> if (unit == GlucoseUnit.MMOL_L) listOf(5.5f, 7.0f, 9.0f) else listOf(100f, 130f, 160f)
                else -> emptyList()
            }

            if (presets.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { presetVal ->
                        val presetLabel = if (selectedType == LogType.CARBS) "${presetVal.toInt()}g"
                        else if (selectedType == LogType.RAPID_INSULIN || selectedType == LogType.BASAL_INSULIN) "${presetVal.toInt()}U"
                        else presetVal.toString()

                        OutlinedButton(
                            onClick = {
                                valueText = if (presetVal % 1 == 0f) presetVal.toInt().toString() else presetVal.toString()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = presetLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Note Input Field
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Note (Optional, e.g. Pizza, Workout)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save & Cancel Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        val num = valueText.replace(',', '.').toFloatOrNull() ?: 0f
                        onSave(selectedType, num, noteText)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Record")
                }
            }
        }
    }
}
