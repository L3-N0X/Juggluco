package tk.glucodata.ui.screens.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
        subtitle = stringResource(R.string.settings_cat_data),
        onNavigateBack = onNavigateBack
    ) {
        // EXPORT GLUCOSE DATA CARD
        SettingsCard(
            title = stringResource(R.string.settings_export_sensor_data),
            icon = Icons.Default.FileUpload,
            categorySubtitle = "RECORDS & BACKUP"
        ) {
            Text(
                text = stringResource(R.string.settings_export_sensor_data_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Available Export Formats:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Continuous Stream readings (.tsv / .csv)\n• Manual NFC scans & calibration logs\n• Insulin, carb & medication amounts\n• Abbott LibreView cloud-compatible format\n• HTML summary reports",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onExportData,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.export_glucose_data), fontWeight = FontWeight.Bold)
            }
        }

        // LEGACY OPENGL CANVAS
        SettingsCard(
            title = stringResource(R.string.settings_legacy_canvas),
            icon = Icons.Default.Timeline,
            categorySubtitle = "CLASSIC INTERFACE"
        ) {
            Text(
                text = stringResource(R.string.settings_legacy_canvas_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onOpenLegacyView,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.settings_btn_switch))
            }
        }

        // STORAGE STATUS CARD
        SettingsCard(
            title = "Local Database",
            icon = Icons.Default.Storage,
            categorySubtitle = "STORAGE"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recorded Data Span",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dateRangeInfo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
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
