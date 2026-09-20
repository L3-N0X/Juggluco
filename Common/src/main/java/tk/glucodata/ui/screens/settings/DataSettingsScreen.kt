package tk.glucodata.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DataSettingsScreen(
    repository: GlucoseRepository,
    onOpenLegacyView: () -> Unit,
    onExportData: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val dateRangeInfo = remember {
        try {
            val oldest = Natives.oldestdatatime()
            val newest = Natives.getendtime()
            if (oldest > 0L && newest > 0L) {
                val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                "${fmt.format(Date(oldest))} — ${fmt.format(Date(newest))}"
            } else {
                "Active sensor sessions recorded"
            }
        } catch (_: Throwable) {
            "Active sensor sessions recorded"
        }
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_group_data_title),
        onNavigateBack = onNavigateBack
    ) {
        // EXPORT GLUCOSE DATA
        SettingsSection(title = "Records & backup") {
            SettingsActionRow(
                title = stringResource(R.string.settings_export_sensor_data),
                subtitle = stringResource(R.string.settings_export_sensor_data_desc),
                icon = Icons.Default.FileUpload,
                onClick = onExportData,
                trailingContent = {
                    Button(
                        onClick = onExportData,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text("Export", fontSize = 12.sp)
                    }
                }
            )
        }

        // LEGACY OPENGL CANVAS
        SettingsSection(title = "Classic interface") {
            SettingsActionRow(
                title = stringResource(R.string.settings_legacy_canvas),
                subtitle = stringResource(R.string.settings_legacy_canvas_desc),
                icon = Icons.Default.Timeline,
                onClick = onOpenLegacyView,
                trailingContent = {
                    OutlinedButton(
                        onClick = onOpenLegacyView,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(stringResource(R.string.settings_btn_switch), fontSize = 12.sp)
                    }
                }
            )
        }

        // STORAGE STATUS
        SettingsSection(title = "Local database storage") {
            SettingsActionRow(
                title = "Recorded data span",
                subtitle = dateRangeInfo,
                icon = Icons.Default.Storage
            )
        }

        // INFO
        SettingsInfoCard(
            text = "Juggluco stores all sensor data locally in high-performance binary database files on your internal storage. Your health records never leave your phone without your explicit configuration.",
            icon = Icons.Default.Info
        )
    }
}
