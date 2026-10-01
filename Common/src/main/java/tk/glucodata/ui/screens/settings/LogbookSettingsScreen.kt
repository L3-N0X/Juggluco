package tk.glucodata.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.glucodata.R
import tk.glucodata.ui.components.decimalInput
import tk.glucodata.ui.components.icon
import tk.glucodata.ui.components.parseDecimal
import tk.glucodata.ui.components.plainAmount
import tk.glucodata.ui.components.shortLabelRes
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.data.MealStore
import tk.glucodata.ui.model.LabelConfig
import tk.glucodata.ui.model.LabelSaveError
import tk.glucodata.ui.model.LogLabel
import tk.glucodata.ui.model.LogType
import tk.glucodata.ui.theme.LocalLogbookColors

/**
 * The labels of the logbook, for those who use more than carbs, insulin and finger-pricks.
 *
 * At the top: which label each entry type of the editor is saved under. Below: every native
 * label, renamed, weighted, added or (the last one) removed here, and meals, the ingredient
 * catalog and the rounding of meal totals. On a device that mirrors another one's labels it is
 * read only, like the classic label screen.
 */
@Composable
fun LogbookSettingsScreen(
    repository: GlucoseRepository,
    onOpenIngredients: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val labels by repository.labelConfig.collectAsState()
    val logbookColors = LocalLogbookColors.current
    var pickingType by remember { mutableStateOf<LogType?>(null) }
    var editingLabel by remember { mutableStateOf<LabelEdit?>(null) }
    var editingRounding by remember { mutableStateOf(false) }
    val mealsAvailable = MealStore.available
    var ingredientCount by remember { mutableIntStateOf(0) }
    var roundTo by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        repository.refreshLabels()
        if (mealsAvailable) {
            val (count, step) = withContext(Dispatchers.IO) { MealStore.ingredients().size to MealStore.roundTo() }
            ingredientCount = count
            roundTo = step
        }
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_group_logbook_title),
        onNavigateBack = onNavigateBack
    ) {
        if (!labels.editable) {
            SettingsInfoCard(text = stringResource(R.string.labels_read_only), icon = Icons.Default.Lock)
        }

        SettingsSection(title = stringResource(R.string.labels_section_types)) {
            LabelConfig.ROLE_TYPES.forEach { type ->
                val colors = logbookColors.forType(type)
                SettingsNavRow(
                    title = stringResource(type.shortLabelRes),
                    subtitle = labels.nameOf(labels.labelFor(type)).ifBlank { stringResource(R.string.labels_none) },
                    icon = type.icon,
                    iconTint = colors.primary,
                    iconBackground = colors.container,
                    enabled = labels.labels.isNotEmpty() && (labels.editable || type == LogType.BLOOD_GLUCOSE),
                    onClick = { pickingType = type }
                )
            }
        }

        SettingsSection(title = stringResource(R.string.labels_section_labels)) {
            labels.labels.forEach { label ->
                val colors = logbookColors.forType(label.type)
                SettingsNavRow(
                    title = label.name.ifBlank { stringResource(R.string.log_type_custom) },
                    subtitle = labelSubtitle(label),
                    icon = label.type.icon,
                    iconTint = colors.primary,
                    iconBackground = colors.container,
                    enabled = labels.editable,
                    onClick = { editingLabel = LabelEdit(label) }
                )
            }
            if (labels.canAdd) {
                SettingsActionRow(
                    title = stringResource(R.string.labels_add),
                    icon = Icons.Default.Add,
                    onClick = { editingLabel = LabelEdit(null) }
                )
            }
        }

        if (mealsAvailable) {
            SettingsSection(title = stringResource(R.string.meals_section)) {
                SettingsNavRow(
                    title = stringResource(R.string.meals_ingredients),
                    subtitle = pluralStringResource(R.plurals.ingredient_count, ingredientCount, ingredientCount),
                    icon = Icons.Default.Restaurant,
                    onClick = onOpenIngredients
                )
                SettingsNavRow(
                    title = stringResource(R.string.meals_round),
                    subtitle = if (roundTo > 0f) {
                        stringResource(R.string.log_value_carbs, plainAmount(roundTo, 2))
                    } else {
                        stringResource(R.string.meals_round_off)
                    },
                    icon = Icons.Default.Straighten,
                    enabled = labels.editable,
                    onClick = { editingRounding = true }
                )
            }
        }

        SettingsInfoCard(text = stringResource(R.string.labels_info), icon = Icons.Default.Info)
    }

    pickingType?.let { type ->
        LabelPickerDialog(
            type = type,
            labels = labels,
            onDismiss = { pickingType = null },
            onPick = { index ->
                repository.setLabelForType(type, index)
                pickingType = null
            }
        )
    }

    editingLabel?.let { edit ->
        LabelEditorDialog(
            label = edit.label,
            labels = labels,
            onDismiss = { editingLabel = null },
            onSave = { name, weight, roundStep ->
                repository.saveLabel(edit.label?.index ?: -1, name, weight, roundStep)
                    .also { if (it == null) editingLabel = null }
            },
            onDelete = {
                repository.deleteLastLabel()
                editingLabel = null
            }
        )
    }

    if (editingRounding) {
        MealRoundingDialog(
            current = roundTo,
            onDismiss = { editingRounding = false },
            onSave = { step ->
                MealStore.setRoundTo(step)
                roundTo = step
                editingRounding = false
            }
        )
    }
}

/** Which label is being edited; null for a new one. */
private data class LabelEdit(val label: LogLabel?)

/** What a label is used for, and its classic graph weight when it has one. */
@Composable
private fun labelSubtitle(label: LogLabel): String? {
    val parts = mutableListOf<String>()
    if (label.type != LogType.CUSTOM) parts += stringResource(label.type.shortLabelRes)
    if (label.weight > 0f) parts += stringResource(R.string.labels_weight_shown, plainAmount(label.weight, 2))
    return parts.joinToString(" · ").ifEmpty { null }
}

/** Picks the label an entry type is saved under; labels another type uses can't be picked. */
@Composable
private fun LabelPickerDialog(
    type: LogType,
    labels: LabelConfig,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    val typeName = stringResource(type.shortLabelRes)
    val current = labels.labelFor(type)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.labels_pick_title, typeName)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                labels.labels.forEach { label ->
                    val usedBy = labels.roleOf(label.index, except = type)
                    val enabled = usedBy == null && label.name.isNotBlank()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = label.index == current,
                                enabled = enabled,
                                onClick = { onPick(label.index) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = label.index == current, onClick = null, enabled = enabled)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = label.name.ifBlank { stringResource(R.string.log_type_custom) },
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                            if (usedBy != null) {
                                Text(
                                    text = stringResource(R.string.labels_used_by, stringResource(usedBy.shortLabelRes)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/**
 * Names a label and sets its classic graph weight and Garmin rounding. The name is stored as up
 * to 11 bytes of UTF-8, so the counter counts bytes: an accented letter takes two.
 */
@Composable
private fun LabelEditorDialog(
    label: LogLabel?,
    labels: LabelConfig,
    onDismiss: () -> Unit,
    onSave: (name: String, weight: Float, roundTo: Float) -> LabelSaveError?,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(label?.name.orEmpty()) }
    var weightText by remember { mutableStateOf(label?.weight?.let { plainAmount(it, 3) } ?: "0") }
    var roundText by remember { mutableStateOf(label?.roundTo?.let { plainAmount(it, 3) } ?: "1") }
    var error by remember { mutableStateOf<LabelSaveError?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val bytes = name.trim().toByteArray(Charsets.UTF_8).size
    val weight = parseDecimal(weightText)
    val roundTo = parseDecimal(roundText)
    val usedBy = label?.let { labels.roleOf(it.index) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (label == null) R.string.labels_new_title else R.string.labels_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.ingredient_name)) },
                    supportingText = { Text("$bytes/${LabelConfig.MAX_NAME_BYTES}") },
                    isError = bytes > LabelConfig.MAX_NAME_BYTES || error != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = decimalInput(it) },
                    label = { Text(stringResource(R.string.labels_weight)) },
                    supportingText = { Text(stringResource(R.string.labels_weight_hint)) },
                    isError = weight == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (labels.hasGarmin) {
                    OutlinedTextField(
                        value = roundText,
                        onValueChange = { roundText = decimalInput(it) },
                        label = { Text(stringResource(R.string.labels_round_to)) },
                        isError = roundTo == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val deleteHint = when {
                    label == null -> null
                    usedBy != null && label.index == labels.labels.lastIndex ->
                        stringResource(R.string.labels_delete_in_use, stringResource(usedBy.shortLabelRes))
                    label.index != labels.labels.lastIndex -> stringResource(R.string.labels_delete_not_last)
                    else -> null
                }
                deleteHint?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                error?.let {
                    Text(
                        text = stringResource(labelErrorRes(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = weight != null && (!labels.hasGarmin || roundTo != null),
                onClick = {
                    error = onSave(name, weight ?: 0f, if (labels.hasGarmin) roundTo ?: 0f else label?.roundTo ?: 0f)
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (label != null && labels.canDelete(label.index)) {
                    TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )

    if (confirmDelete && label != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.labels_delete_question, label.name)) },
            text = { Text(stringResource(R.string.labels_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

private fun labelErrorRes(error: LabelSaveError): Int = when (error) {
    LabelSaveError.EMPTY -> R.string.ingredient_error_empty
    LabelSaveError.TOO_LONG -> R.string.labels_error_too_long
    LabelSaveError.TOO_MANY -> R.string.labels_error_too_many
    LabelSaveError.READ_ONLY -> R.string.labels_read_only
}

/** The step a meal's carbs total is rounded to before it becomes the carbs amount. */
@Composable
private fun MealRoundingDialog(
    current: Float,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit
) {
    var text by remember { mutableStateOf(plainAmount(current, 2)) }
    val step = parseDecimal(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.meals_round)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = decimalInput(it) },
                suffix = { Text(stringResource(R.string.unit_carbs_short)) },
                supportingText = { Text(stringResource(R.string.meals_round_hint)) },
                isError = step == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = step != null && step >= 0f, onClick = { step?.let(onSave) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
