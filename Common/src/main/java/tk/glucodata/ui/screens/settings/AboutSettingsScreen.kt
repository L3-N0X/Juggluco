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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

@Composable
fun AboutSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    SettingsDetailScaffold(
        title = stringResource(R.string.settings_group_about_title),
        subtitle = stringResource(R.string.settings_cat_about),
        onNavigateBack = onNavigateBack
    ) {
        // APP VERSION & IDENTITY
        SettingsCard(
            title = stringResource(R.string.settings_version_title),
            icon = Icons.Default.Info,
            categorySubtitle = "APPLICATION"
        ) {
            Text(
                text = stringResource(R.string.settings_about_details),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Built for independent diabetes management without proprietary cloud dependencies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // SENSOR COMPATIBILITY
        SettingsCard(
            title = "Supported Continuous Sensors",
            icon = Icons.Default.Sensors,
            categorySubtitle = "HARDWARE ECOSYSTEM"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "• Abbott FreeStyle Libre 1, 2 (European & US), 3\n• Dexcom G7 & Dexcom ONE+\n• Sibionics CGM sensors\n• Direct Bluetooth LE streaming & decryption\n• NFC direct scan & activation support",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
            }
        }

        // PRIVACY & ARCHITECTURE
        SettingsCard(
            title = "Privacy & Local-First Philosophy",
            icon = Icons.Default.Security,
            categorySubtitle = "DATA OWNERSHIP"
        ) {
            Text(
                text = "Juggluco operates entirely offline on your device. Your sensitive medical records and glucose telemetry are never uploaded to foreign servers unless you explicitly configure third-party services like LibreView or Health Connect.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }

        // DEVELOPMENT & OPEN SOURCE
        SettingsCard(
            title = "Open Source & Development",
            icon = Icons.Default.Code,
            categorySubtitle = "DEVELOPMENT"
        ) {
            Text(
                text = "Juggluco is free software licensed under GNU General Public License v3 (GPLv3). Source code is freely available for inspection and audit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}
