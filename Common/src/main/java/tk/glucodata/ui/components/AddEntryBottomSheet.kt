package tk.glucodata.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.model.LogType
import tk.glucodata.ui.model.toStoredLogValue
import java.util.Locale

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
                text = stringResource(R.string.new_amount),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Type selector chips (Horizontally scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(LogType.RAPID_INSULIN, LogType.CARBS, LogType.BLOOD_GLUCOSE, LogType.BASAL_INSULIN).forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = {
                            Text(
                                text = when (type) {
                                    LogType.RAPID_INSULIN -> stringResource(R.string.log_short_bolus)
                                    LogType.CARBS -> stringResource(R.string.log_short_carbs)
                                    LogType.BLOOD_GLUCOSE -> stringResource(R.string.log_type_finger_prick)
                                    LogType.BASAL_INSULIN -> stringResource(R.string.log_short_basal)
                                    else -> stringResource(type.labelRes)
                                }
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Value Input Field
            val valueLabel = when (selectedType) {
                LogType.RAPID_INSULIN, LogType.BASAL_INSULIN -> stringResource(R.string.log_value_insulin_units)
                LogType.CARBS, LogType.MEAL -> stringResource(R.string.log_value_carbohydrates_grams)
                LogType.BLOOD_GLUCOSE -> stringResource(R.string.log_value_blood_glucose, stringResource(unit.labelRes))
                LogType.NOTE -> stringResource(R.string.log_value_amount)
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

            // Quick increment & preset buttons (Horizontally scrollable)
            val presets = when (selectedType) {
                LogType.RAPID_INSULIN -> listOf(1f, 2f, 3f, 4f, 6f, 8f, 10f)
                LogType.CARBS -> listOf(15f, 30f, 45f, 60f, 75f)
                LogType.BASAL_INSULIN -> listOf(10f, 14f, 18f, 22f)
                LogType.BLOOD_GLUCOSE -> if (unit == GlucoseUnit.MMOL_L) listOf(5.5f, 7.0f, 9.0f) else listOf(100f, 130f, 160f)
                else -> emptyList()
            }

            if (presets.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { presetVal ->
                        val presetLabel = if (selectedType == LogType.CARBS) stringResource(R.string.log_value_carbs, presetVal.toInt().toString())
                        else if (selectedType == LogType.RAPID_INSULIN || selectedType == LogType.BASAL_INSULIN) stringResource(R.string.log_value_insulin, presetVal.toInt().toString())
                        else String.format(Locale.getDefault(), "%.1f", presetVal)

                        OutlinedButton(
                            onClick = {
                                valueText = if (presetVal % 1 == 0f) presetVal.toInt().toString() else String.format(Locale.getDefault(), "%.1f", presetVal)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = presetLabel, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Quick Meal Category Shortcuts
            if (selectedType == LogType.CARBS || selectedType == LogType.MEAL || selectedType == LogType.RAPID_INSULIN) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        stringResource(R.string.meal_breakfast),
                        stringResource(R.string.meal_lunch),
                        stringResource(R.string.meal_dinner),
                        stringResource(R.string.meal_snack)
                    ).forEach { mealName ->
                        SuggestionChip(
                            onClick = {
                                noteText = if (noteText.isEmpty()) mealName else "$noteText, $mealName"
                            },
                            label = { Text(mealName, fontSize = 12.sp) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Note Input Field
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text(stringResource(R.string.log_note_example)) },
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
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        val rawValue = valueText.replace(',', '.').toFloatOrNull() ?: 0f
                        val value = toStoredLogValue(selectedType, rawValue, unit)
                        onSave(selectedType, value, noteText)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
fun EditEntryDialog(
    entry: LogRecord,
    unit: GlucoseUnit,
    onDismiss: () -> Unit,
    onSave: (Float, String) -> Unit
) {
    val displayValue = if (entry.type == LogType.BLOOD_GLUCOSE) unit.toDisplay(entry.value) else entry.value
    var valueText by remember(entry.id, unit) {
        mutableStateOf(
            if (displayValue % 1f == 0f) displayValue.toInt().toString()
            else String.format(Locale.getDefault(), "%.1f", displayValue)
        )
    }
    var noteText by remember(entry.id) { mutableStateOf(entry.note) }
    val parsedValue = valueText.replace(',', '.').toFloatOrNull()
    val valueLabel = when (entry.type) {
        LogType.RAPID_INSULIN, LogType.BASAL_INSULIN -> stringResource(R.string.log_value_insulin_units)
        LogType.CARBS, LogType.MEAL -> stringResource(R.string.log_value_carbohydrates_grams)
        LogType.BLOOD_GLUCOSE -> stringResource(R.string.log_value_blood_glucose, stringResource(unit.labelRes))
        LogType.NOTE -> stringResource(R.string.log_value_amount)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text(valueLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.log_note_example)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val displayValue = parsedValue ?: return@Button
                    onSave(toStoredLogValue(entry.type, displayValue, unit), noteText)
                    onDismiss()
                },
                enabled = parsedValue != null && parsedValue > 0f
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
