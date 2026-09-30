package tk.glucodata.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GarminShortcut
import tk.glucodata.ui.model.GarminShortcutError
import tk.glucodata.ui.model.GarminShortcutField

/**
 * The amounts a Garmin watch offers on its own, so a bolus or a meal can be
 * logged without reaching for the phone.
 *
 * The list is edited in place and written in one go, because the native store
 * only takes a whole list and then pushes it to the watch. A partly written list
 * would leave the two sides disagreeing about what the watch can log.
 */
@Composable
fun GarminShortcutsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stored by repository.garminShortcuts.collectAsState()
    var edited by remember { mutableStateOf<List<GarminShortcut>?>(null) }
    var editing by remember { mutableStateOf<ShortcutEdit?>(null) }

    LaunchedEffect(Unit) { repository.refreshGarminShortcuts() }
    val shortcuts = edited ?: stored

    SettingsDetailScaffold(
        title = stringResource(R.string.loc_garmin_shortcuts_title),
        onNavigateBack = onNavigateBack
    ) {
        Text(
            text = stringResource(R.string.loc_garmin_shortcuts_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingsSection {
            shortcuts.forEachIndexed { index, shortcut ->
                GarminShortcutRow(
                    shortcut = shortcut,
                    onEdit = { editing = ShortcutEdit(index, shortcut.label, shortcut.value) },
                    onDelete = { edited = shortcuts.filterIndexed { i, _ -> i != index } }
                )
            }

            SettingsActionRow(
                title = stringResource(R.string.newname),
                icon = Icons.Default.Add,
                onClick = { editing = ShortcutEdit(-1, "", "") }
            )

            SettingsActionRow(
                title = stringResource(R.string.save),
                icon = Icons.Default.Save,
                onClick = {
                    scope.launch {
                        val error = repository.saveGarminShortcuts(shortcuts)
                        edited = null
                        Toast.makeText(
                            context,
                            context.shortcutSaveMessage(error, shortcuts),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }

    editing?.let { current ->
        GarminShortcutDialog(
            edit = current,
            onDismiss = { editing = null },
            onConfirm = { label, value ->
                val next = shortcuts.toMutableList()
                if (current.index < 0) {
                    next.add(GarminShortcut(label, value))
                } else {
                    next[current.index] = GarminShortcut(label, value)
                }
                edited = next
                editing = null
            },
            onDelete = if (current.index < 0) {
                null
            } else {
                {
                    edited = shortcuts.filterIndexed { i, _ -> i != current.index }
                    editing = null
                }
            }
        )
    }
}

/** Which shortcut is being edited; an index below zero means a new one. */
private data class ShortcutEdit(val index: Int, val label: String, val value: String)

@Composable
private fun GarminShortcutRow(
    shortcut: GarminShortcut,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shortcut.label.ifBlank { stringResource(R.string.loc_garmin_unnamed_shortcut) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = shortcut.value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.loc_action_delete),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun GarminShortcutDialog(
    edit: ShortcutEdit,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var label by remember(edit) { mutableStateOf(edit.label) }
    var value by remember(edit) { mutableStateOf(edit.value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shortcut)) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.shortcut)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.value)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(label, value) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

/**
 * The native store fixes the size of both halves and names the one that did not
 * fit, so the message is the same one the native shortcut screen gave.
 */
private fun Context.shortcutSaveMessage(
    error: GarminShortcutError?,
    shortcuts: List<GarminShortcut>
): String = when {
    error == null -> getString(R.string.loc_garmin_shortcuts_saved)
    error.field == null -> getString(R.string.index_too_large, error.index)
    error.field == GarminShortcutField.LABEL ->
        getString(R.string.label_too_long, shortcuts.getOrNull(error.index)?.label.orEmpty())
    else -> getString(R.string.label_too_long, shortcuts.getOrNull(error.index)?.value.orEmpty())
}
