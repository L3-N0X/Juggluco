package tk.glucodata.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.LibreLabelMapping
import tk.glucodata.ui.model.LibreTreatmentKind
import java.text.DecimalFormat

/**
 * What LibreView should do with the numbers logged under each label. LibreView only knows a
 * rapid acting dose, a long acting dose, a carbohydrate amount and a note, so every label of the
 * logbook is mapped onto one of those; an unmapped label is never sent.
 */
@Composable
fun LibreViewTreatmentsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val treatments by repository.libreTreatments.collectAsState()
    var edited by remember { mutableStateOf<LibreLabelMapping?>(null) }

    SettingsDetailScaffold(
        title = stringResource(R.string.loc_libreview_treatments),
        onNavigateBack = onNavigateBack
    ) {
        if (treatments.isEmpty()) {
            Text(
                text = stringResource(R.string.loc_libreview_treatments_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SettingsSection {
                treatments.forEach { mapping ->
                    SettingsNavRow(
                        title = mapping.label,
                        subtitle = mappingSubtitle(mapping),
                        icon = treatmentIcon(mapping.kind),
                        onClick = { edited = mapping }
                    )
                }
            }
        }

        SettingsInfoCard(
            text = stringResource(R.string.loc_libreview_treatments_info),
            icon = Icons.Default.Info
        )
    }

    edited?.let { mapping ->
        LibreTreatmentKindDialog(
            mapping = mapping,
            onDismiss = { edited = null },
            onConfirm = { kind, weight ->
                repository.setLibreLabelMapping(mapping.index, kind, weight)
                edited = null
            }
        )
    }
}

/** The kind a label is mapped on, with the carbs weight when it is mapped on carbs. */
@Composable
private fun mappingSubtitle(mapping: LibreLabelMapping): String {
    val kind = stringResource(mapping.kind.labelRes)
    return if (mapping.kind == LibreTreatmentKind.CARBS) {
        stringResource(
            R.string.loc_libreview_carb_weight_shown,
            kind,
            formatCarbWeight(mapping.weight)
        )
    } else {
        kind
    }
}

/** Picks what one label becomes in an upload, with the carbs weight when it becomes carbs. */
@Composable
private fun LibreTreatmentKindDialog(
    mapping: LibreLabelMapping,
    onDismiss: () -> Unit,
    onConfirm: (LibreTreatmentKind, Float) -> Unit
) {
    var kind by remember(mapping.index) { mutableStateOf(mapping.kind) }
    var weightText by remember(mapping.index) { mutableStateOf(formatCarbWeight(mapping.weight)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(mapping.label) },
        text = {
            Column {
                LibreTreatmentKind.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = kind == option,
                                onClick = { kind = option }
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = kind == option, onClick = { kind = option })
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                if (kind == LibreTreatmentKind.CARBS) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { typed ->
                            // A decimal separator is a comma in half the world.
                            weightText = typed.filter { it.isDigit() || it == '.' || it == ',' }
                        },
                        label = { Text(stringResource(R.string.loc_libreview_carb_weight)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val weight = weightText.replace(',', '.').toFloatOrNull() ?: 1f
                onConfirm(kind, if (kind == LibreTreatmentKind.CARBS) weight.coerceAtLeast(0.01f) else 1f)
            }) {
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

/** The weight is stored as a plain factor, so it is shown trimmed to what it holds. */
private fun formatCarbWeight(weight: Float): String = DecimalFormat("0.##").format(weight)

/** The kind of number a label carries, as the icon of its row. */
private fun treatmentIcon(kind: LibreTreatmentKind): ImageVector = when (kind) {
    LibreTreatmentKind.RAPID_INSULIN -> Icons.Default.Bolt
    LibreTreatmentKind.LONG_INSULIN -> Icons.Default.Bedtime
    LibreTreatmentKind.CARBS -> Icons.Default.Restaurant
    LibreTreatmentKind.NOTE -> Icons.Default.Notes
    LibreTreatmentKind.UNSET -> Icons.Default.RemoveCircleOutline
}
