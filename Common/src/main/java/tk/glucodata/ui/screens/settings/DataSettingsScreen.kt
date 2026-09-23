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
import androidx.compose.ui.platform.LocalContext
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

private fun formatLogSize(context: android.content.Context, bytes: Long): String = when {
    bytes < 0L -> context.getString(R.string.dialog_ip_unavailable)
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
    val context = LocalContext.current
    val dateRangeInfo = remember {
        try {
            val oldest = Natives.oldestdatatime()
            val newest = Natives.getendtime()
            if (oldest > 0L && newest > 0L) {
                val pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "yMMMd")
                val fmt = SimpleDateFormat(pattern, Locale.getDefault())
                "${fmt.format(Date(oldest))} — ${fmt.format(Date(newest))}"
            } else {
                context.getString(R.string.loc_active_sessions_recorded)
            }
        } catch (_: Throwable) {
            context.getString(R.string.loc_active_sessions_recorded)
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
        SettingsSection(title = stringResource(R.string.loc_data_records_backup)) {
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
                        Text(stringResource(R.string.btn_export_data), fontSize = 12.sp)
                    }
                }
            )
        }

        // LEGACY OPENGL CANVAS
        SettingsSection(title = stringResource(R.string.loc_classic_interface)) {
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
        SettingsSection(title = stringResource(R.string.loc_local_database_storage)) {
            SettingsActionRow(
                title = stringResource(R.string.loc_recorded_data_span),
                subtitle = dateRangeInfo,
                icon = Icons.Default.Storage
            )
        }

        // DIAGNOSTIC LOGS
        if (loggingBuild) {
            SettingsSection(title = stringResource(R.string.loc_diagnostic_logs)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.loc_write_debug_log),
                    subtitle = stringResource(R.string.loc_write_debug_log_desc, formatLogSize(context, 4L * 1024L * 1024L)),
                    icon = Icons.Default.BugReport,
                    checked = traceEnabled,
                    onCheckedChange = { enabled ->
                        Natives.dolog(enabled)
                        traceEnabled = enabled
                        traceBytes = logSize { Natives.getLogfilesize() }
                    }
                )
                SettingsActionRow(
                    title = "trace.log",
                    subtitle = formatLogSize(context, traceBytes),
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
                SettingsSwitchRow(
                    title = stringResource(R.string.loc_capture_system_log),
                    subtitle = stringResource(R.string.loc_capture_system_log_desc, formatLogSize(context, 8L * 1024L * 1024L)),
                    icon = Icons.Default.BugReport,
                    checked = logcatEnabled,
                    onCheckedChange = { enabled ->
                        Natives.dologcat(enabled)
                        logcatEnabled = enabled
                        logcatBytes = logSize { Natives.getLogcatfilesize() }
                    }
                )
                SettingsActionRow(
                    title = "logcat.txt",
                    subtitle = formatLogSize(context, logcatBytes),
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
            text = stringResource(R.string.loc_data_privacy_info),
            icon = Icons.Default.Info
        )
    }
}
