package tk.glucodata.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.glucodata.R
import tk.glucodata.ui.components.icon
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.InsulinType
import tk.glucodata.ui.model.LogType
import tk.glucodata.ui.theme.LocalLogbookColors

/**
 * Insulin on board: whether it is calculated at all, and which insulin each logbook label holds.
 *
 * The switch comes first because it is what the feature is for, and the types below it because the
 * calculation needs one: native turns the request down while every label is still "Not", so the
 * refusal is spelled out under the switch instead of leaving the switch to snap back silently.
 */
@Composable
fun InsulinOnboardSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val labels by repository.labelConfig.collectAsState()
    val enabled by repository.insulinOnboardEnabled.collectAsState()
    val logbookColors = LocalLogbookColors.current

    var types by remember { mutableStateOf(emptyMap<Int, InsulinType>()) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var refused by remember { mutableStateOf(false) }

    // One native lookup per label, so it is read once per label set rather than on every draw.
    LaunchedEffect(labels.labels) {
        repository.refreshInsulinOnboard()
        val indices = labels.labels.map { it.index }
        types = withContext(Dispatchers.IO) {
            indices.associateWith { repository.insulinTypeOf(it) }
        }
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.iob_title),
        onNavigateBack = onNavigateBack
    ) {
        if (!labels.editable) {
            SettingsInfoCard(text = stringResource(R.string.iob_read_only), icon = Icons.Default.Lock)
        }

        SettingsSection {
            SettingsSwitchRow(
                title = stringResource(R.string.iob_show),
                subtitle = stringResource(if (enabled) R.string.iob_show_on else R.string.iob_show_off),
                icon = LogType.RAPID_INSULIN.icon,
                iconTint = logbookColors.rapidInsulin,
                iconBackground = logbookColors.rapidInsulinContainer,
                checked = enabled,
                onCheckedChange = { turnedOn ->
                    refused = !repository.setInsulinOnboard(turnedOn)
                }
            )
            if (refused) {
                Text(
                    text = stringResource(R.string.iob_needs_type),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        SettingsSection(title = stringResource(R.string.iob_section_types)) {
            // Only named labels: an unnamed one cannot be picked as a bolus label either, and two
            // rows both reading "Custom" would say nothing about which is which.
            labels.labels.filter { it.name.isNotBlank() }.forEach { label ->
                val type = types[label.index] ?: InsulinType.NONE
                val colors = logbookColors.forType(label.type)
                SettingsNavRow(
                    title = label.name,
                    subtitle = stringResource(type.labelRes),
                    icon = label.type.icon,
                    iconTint = colors.primary,
                    iconBackground = colors.container,
                    enabled = labels.editable,
                    onClick = { picking = label.index }
                )
            }
        }

        SettingsInfoCard {
            Text(
                text = stringResource(R.string.iob_types_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.iob_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    picking?.let { index ->
        val label = labels.labels.firstOrNull { it.index == index }
        InsulinTypeDialog(
            current = types[index] ?: InsulinType.NONE,
            onDismiss = { picking = null },
            onPick = { type ->
                repository.setInsulinType(index, type)
                types = types + (index to type)
                refused = false
                picking = null
            },
            title = stringResource(R.string.iob_type_title, label?.name.orEmpty())
        )
    }
}

/** Picks the insulin a label holds: "Not", or one of the seven with a curve of its own. */
@Composable
private fun InsulinTypeDialog(
    current: InsulinType,
    title: String,
    onDismiss: () -> Unit,
    onPick: (InsulinType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                InsulinType.entries.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = type == current,
                                onClick = { onPick(type) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = type == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(type.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
