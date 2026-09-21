package tk.glucodata.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.screens.settings.AboutSettingsScreen
import tk.glucodata.ui.screens.settings.AlarmsSettingsScreen
import tk.glucodata.ui.screens.settings.BroadcastsSettingsScreen
import tk.glucodata.ui.screens.settings.CalibrationSettingsScreen
import tk.glucodata.ui.screens.settings.DataSettingsScreen
import tk.glucodata.ui.screens.settings.DisplaySettingsScreen
import tk.glucodata.ui.screens.settings.FloatingWidgetSettingsScreen
import tk.glucodata.ui.screens.settings.GlucoseTargetsSettingsScreen
import tk.glucodata.ui.screens.settings.HardwareSettingsScreen
import tk.glucodata.ui.screens.settings.LibreViewSettingsScreen
import tk.glucodata.ui.screens.settings.MirrorConnectionEditScreen
import tk.glucodata.ui.screens.settings.MirrorSettingsScreen
import tk.glucodata.ui.screens.settings.SettingsDestination
import tk.glucodata.ui.screens.settings.SettingsNavRow
import tk.glucodata.ui.screens.settings.SettingsSection
import tk.glucodata.ui.screens.settings.TurnServerSettingsScreen
import tk.glucodata.ui.screens.settings.VoiceSettingsScreen
import tk.glucodata.ui.screens.settings.WebServerSettingsScreen

@Composable
fun SettingsScreen(
    repository: GlucoseRepository,
    isDarkTheme: Boolean = false,
    darkThemeOverride: Boolean? = null,
    onDarkThemeChanged: (Boolean?) -> Unit = {},
    onOpenLegacyView: () -> Unit = {},
    onExportData: () -> Unit = {},
    currentDestination: SettingsDestination? = null,
    onNavigateToDestination: (SettingsDestination?) -> Unit = {},
    onSelectMirrorIndex: (Int) -> Unit = {},
    selectedMirrorIndex: Int = -1,
    modifier: Modifier = Modifier
) {
    // Internal state fallback if parent does not manage navigation
    var internalDestination by rememberSaveable { mutableStateOf<SettingsDestination?>(null) }
    var internalMirrorIndex by rememberSaveable { mutableStateOf(-1) }

    // If parent handles destination, clear internal state when returning
    LaunchedEffect(currentDestination) {
        if (currentDestination == null) {
            internalDestination = null
        }
    }

    val activeDestination = currentDestination ?: internalDestination
    val activeMirrorIndex = if (selectedMirrorIndex != -1) selectedMirrorIndex else internalMirrorIndex

    val handleNavigate: (SettingsDestination?) -> Unit = { dest ->
        internalDestination = dest
        onNavigateToDestination(dest)
    }

    val handleMirrorSelect: (Int) -> Unit = { idx ->
        internalMirrorIndex = idx
        onSelectMirrorIndex(idx)
    }

    if (activeDestination != null) {
        when (activeDestination) {
            SettingsDestination.GLUCOSE_TARGETS -> GlucoseTargetsSettingsScreen(
                repository = repository,
                onNavigateBack = { handleNavigate(null) }
            )
            SettingsDestination.ALARMS -> AlarmsSettingsScreen(
                repository = repository,
                onNavigateBack = { handleNavigate(null) }
            )
            SettingsDestination.DISPLAY -> DisplaySettingsScreen(
                repository = repository,
                darkThemeOverride = darkThemeOverride,
                onDarkThemeChanged = onDarkThemeChanged,
                onOpenFloatingWidgetConfig = { handleNavigate(SettingsDestination.FLOATING_WIDGET) },
                onOpenCalibrationConfig = { handleNavigate(SettingsDestination.CALIBRATION) },
                onNavigateBack = { handleNavigate(null) }
            )
            SettingsDestination.FLOATING_WIDGET -> FloatingWidgetSettingsScreen(
                repository = repository,
                onNavigateBack = { handleNavigate(SettingsDestination.DISPLAY) }
            )
            SettingsDestination.CALIBRATION -> CalibrationSettingsScreen(
                repository = repository,
                onNavigateBack = { handleNavigate(SettingsDestination.DISPLAY) }
            )
            SettingsDestination.VOICE -> VoiceSettingsScreen(
                repository = repository,
                onNavigateBack = { handleNavigate(null) }
            )
            SettingsDestination.BROADCASTS -> BroadcastsSettingsScreen(
                repository = repository,
                onOpenWebServerConfig = { handleNavigate(SettingsDestination.WEB_SERVER) },
                onOpenLibreViewConfig = { handleNavigate(SettingsDestination.LIBRE_VIEW) },
                onNavigateBack = { handleNavigate(null) }
            )
            SettingsDestination.MIRROR -> MirrorSettingsScreen(
                repository = repository,
                onOpenConnectionEdit = { idx ->
                    handleMirrorSelect(idx)
                    handleNavigate(SettingsDestination.MIRROR_CONNECTION_EDIT)
                },
                onOpenTurnServerConfig = { handleNavigate(SettingsDestination.TURN_SERVER) },
                onNavigateBack = { handleNavigate(null) }
            )
            SettingsDestination.HARDWARE -> HardwareSettingsScreen(
                repository = repository,
                onNavigateBack = { handleNavigate(null) }
            )
            SettingsDestination.DATA -> DataSettingsScreen(
                repository = repository,
                onOpenLegacyView = onOpenLegacyView,
                onExportData = onExportData,
                onNavigateBack = { handleNavigate(null) }
            )
            SettingsDestination.ABOUT -> AboutSettingsScreen(
                repository = repository,
                onNavigateBack = { handleNavigate(null) }
            )
            SettingsDestination.WEB_SERVER -> WebServerSettingsScreen(
                repository = repository,
                onNavigateBack = { handleNavigate(SettingsDestination.BROADCASTS) }
            )
            SettingsDestination.LIBRE_VIEW -> LibreViewSettingsScreen(
                repository = repository,
                onNavigateBack = { handleNavigate(SettingsDestination.BROADCASTS) }
            )
            SettingsDestination.MIRROR_CONNECTION_EDIT -> MirrorConnectionEditScreen(
                repository = repository,
                connectionIndex = activeMirrorIndex,
                onNavigateBack = { handleNavigate(SettingsDestination.MIRROR) }
            )
            SettingsDestination.TURN_SERVER -> TurnServerSettingsScreen(
                repository = repository,
                onNavigateBack = { handleNavigate(SettingsDestination.MIRROR) }
            )
        }
        return
    }

    val unit by repository.unit.collectAsState()
    val targetLow by repository.targetLow.collectAsState()
    val targetHigh by repository.targetHigh.collectAsState()
    val alarms by repository.alarms.collectAsState()
    val mirrorConnections by repository.mirrorConnections.collectAsState()

    val isReceiverActive = mirrorConnections.any { it.isReceiver }
    val isSenderActive = mirrorConnections.any { !it.isReceiver }

    ScreenContent(modifier = modifier) {
        // Quick Status Summary Bar
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenLayout.CardPadding, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Unit badge
                Column {
                    Text(
                        text = "Unit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = unit.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Range badge
                Column {
                    Text(
                        text = "Target Range",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${unit.format(targetLow)} - ${unit.format(targetHigh)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Alarms badge
                Column {
                    Text(
                        text = "Alarms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (alarms.lowAlarmEnabled || alarms.highAlarmEnabled) "Active" else "Off",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (alarms.lowAlarmEnabled || alarms.highAlarmEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }

                // Role badge
                Column {
                    Text(
                        text = "Role",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            isReceiverActive -> "Receiver"
                            isSenderActive -> "Sender"
                            else -> "Standalone"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Section: Glucose & alerts
        SettingsSection(title = "Glucose & alerts") {
            SettingsNavRow(
                title = stringResource(R.string.settings_group_glucose_title),
                subtitle = stringResource(R.string.settings_group_glucose_desc),
                icon = SettingsDestination.GLUCOSE_TARGETS.icon,
                onClick = { handleNavigate(SettingsDestination.GLUCOSE_TARGETS) }
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_group_alarms_title),
                subtitle = stringResource(R.string.settings_group_alarms_desc),
                icon = SettingsDestination.ALARMS.icon,
                onClick = { handleNavigate(SettingsDestination.ALARMS) }
            )
        }

        // Section: Display & speech
        SettingsSection(title = "Display & speech") {
            SettingsNavRow(
                title = stringResource(R.string.settings_group_display_title),
                subtitle = stringResource(R.string.settings_group_display_desc),
                icon = SettingsDestination.DISPLAY.icon,
                onClick = { handleNavigate(SettingsDestination.DISPLAY) }
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_group_voice_title),
                subtitle = stringResource(R.string.settings_group_voice_desc),
                icon = SettingsDestination.VOICE.icon,
                onClick = { handleNavigate(SettingsDestination.VOICE) }
            )
        }

        // Section: Connectivity & sharing
        SettingsSection(title = "Connectivity & sharing") {
            SettingsNavRow(
                title = stringResource(R.string.settings_group_broadcasts_title),
                subtitle = stringResource(R.string.settings_group_broadcasts_desc),
                icon = SettingsDestination.BROADCASTS.icon,
                onClick = { handleNavigate(SettingsDestination.BROADCASTS) }
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_group_mirror_title),
                subtitle = stringResource(R.string.settings_group_mirror_desc),
                icon = SettingsDestination.MIRROR.icon,
                onClick = { handleNavigate(SettingsDestination.MIRROR) }
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_group_hardware_title),
                subtitle = stringResource(R.string.settings_group_hardware_desc),
                icon = SettingsDestination.HARDWARE.icon,
                onClick = { handleNavigate(SettingsDestination.HARDWARE) }
            )
        }

        // Section: System & data
        SettingsSection(title = "System & data") {
            SettingsNavRow(
                title = stringResource(R.string.settings_group_data_title),
                subtitle = stringResource(R.string.settings_group_data_desc),
                icon = SettingsDestination.DATA.icon,
                onClick = { handleNavigate(SettingsDestination.DATA) }
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_group_about_title),
                subtitle = stringResource(R.string.settings_group_about_desc),
                icon = SettingsDestination.ABOUT.icon,
                onClick = { handleNavigate(SettingsDestination.ABOUT) }
            )
        }
    }
}
