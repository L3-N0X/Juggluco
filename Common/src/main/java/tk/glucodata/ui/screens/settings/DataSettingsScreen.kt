package tk.glucodata.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** Release builds are compiled without logging; the sizes come back as -1 then. */
private fun logSize(read: () -> Long): Long = try {
    read()
} catch (_: Throwable) {
    -1L
}

private fun formatLogSize(bytes: Long): String = when {
    bytes < 0L -> "unavailable"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

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

    var traceBytes by remember { mutableStateOf(logSize { Natives.getLogfilesize() }) }
    var logcatBytes by remember { mutableStateOf(logSize { Natives.getLogcatfilesize() }) }
    var traceEnabled by remember { mutableStateOf(logSize { if (Natives.islogging()) 1L else 0L } == 1L) }
    var logcatEnabled by remember { mutableStateOf(logSize { if (Natives.islogcat()) 1L else 0L } == 1L) }
    val loggingBuild = traceBytes >= 0L || logcatBytes >= 0L

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

        // DIAGNOSTIC LOGS
        if (loggingBuild) {
            SettingsSection(title = "Diagnostic logs") {
                SettingsSwitchRow(
                    title = "Write debug log",
                    subtitle = "Juggluco's own trace log, kept to ${formatLogSize(4L * 1024L * 1024L)} plus one rotated copy",
                    icon = Icons.Default.BugReport,
                    checked = traceEnabled,
                    onCheckedChange = { enabled ->
                        Natives.dolog(enabled)
                        traceEnabled = enabled
                        traceBytes = logSize { Natives.getLogfilesize() }
                    }
                )
                SettingsDivider()
                SettingsActionRow(
                    title = "trace.log",
                    subtitle = formatLogSize(traceBytes),
                    icon = Icons.Default.Description,
                    trailingContent = {
                        OutlinedButton(
                            onClick = {
                                Natives.zeroLog()
                                traceBytes = logSize { Natives.getLogfilesize() }
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(stringResource(R.string.delete), fontSize = 12.sp)
                        }
                    }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "Capture system log",
                    subtitle = "Runs logcat in the background and rotates it at ${formatLogSize(8L * 1024L * 1024L)}",
                    icon = Icons.Default.BugReport,
                    checked = logcatEnabled,
                    onCheckedChange = { enabled ->
                        Natives.dologcat(enabled)
                        logcatEnabled = enabled
                        logcatBytes = logSize { Natives.getLogcatfilesize() }
                    }
                )
                SettingsDivider()
                SettingsActionRow(
                    title = "logcat.txt",
                    subtitle = formatLogSize(logcatBytes),
                    icon = Icons.Default.Description,
                    trailingContent = {
                        OutlinedButton(
                            onClick = {
                                Natives.zeroLogcat()
                                logcatBytes = logSize { Natives.getLogcatfilesize() }
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(stringResource(R.string.delete), fontSize = 12.sp)
                        }
                    }
                )
            }
        }

        // INFO
        SettingsInfoCard(
            text = "Juggluco stores all sensor data locally in high-performance binary database files on your internal storage. Your health records never leave your phone without your explicit configuration.",
            icon = Icons.Default.Info
        )
    }
}
