package tk.glucodata.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import tk.glucodata.ui.screens.settings.DataSettingsScreen
import tk.glucodata.ui.screens.settings.DisplaySettingsScreen
import tk.glucodata.ui.screens.settings.GlucoseTargetsSettingsScreen
import tk.glucodata.ui.screens.settings.HardwareSettingsScreen
import tk.glucodata.ui.screens.settings.LibreViewSettingsScreen
import tk.glucodata.ui.screens.settings.MirrorConnectionEditScreen
import tk.glucodata.ui.screens.settings.MirrorSettingsScreen
import tk.glucodata.ui.screens.settings.SettingsDestination
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
                onNavigateBack = { handleNavigate(null) }
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
    val displayConfig by repository.displayConfig.collectAsState()
    val mirrorConnections by repository.mirrorConnections.collectAsState()

    val isReceiverActive = mirrorConnections.any { it.isReceiver }
    val isSenderActive = mirrorConnections.any { !it.isReceiver }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Page subtitle (page title is already shown in the top app bar)
        Text(
            text = stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Quick Status Summary Bar
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Unit badge
                Column {
                    Text(
                        text = "Unit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
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
                        color = MaterialTheme.colorScheme.outline
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
                        color = MaterialTheme.colorScheme.outline
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
                        color = MaterialTheme.colorScheme.outline
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

        // Group 1: Glucose Targets & Units
        SettingsGroupNavButton(
            title = stringResource(R.string.settings_group_glucose_title),
            subtitle = stringResource(R.string.settings_group_glucose_desc),
            icon = SettingsDestination.GLUCOSE_TARGETS.icon,
            onClick = { handleNavigate(SettingsDestination.GLUCOSE_TARGETS) }
        )

        // Group 2: Alarms & Audio
        SettingsGroupNavButton(
            title = stringResource(R.string.settings_group_alarms_title),
            subtitle = stringResource(R.string.settings_group_alarms_desc),
            icon = SettingsDestination.ALARMS.icon,
            onClick = { handleNavigate(SettingsDestination.ALARMS) }
        )

        // Group 3: Display & Appearance
        SettingsGroupNavButton(
            title = stringResource(R.string.settings_group_display_title),
            subtitle = stringResource(R.string.settings_group_display_desc),
            icon = SettingsDestination.DISPLAY.icon,
            onClick = { handleNavigate(SettingsDestination.DISPLAY) }
        )

        // Group 4: Voice & Speech
        SettingsGroupNavButton(
            title = stringResource(R.string.settings_group_voice_title),
            subtitle = stringResource(R.string.settings_group_voice_desc),
            icon = SettingsDestination.VOICE.icon,
            onClick = { handleNavigate(SettingsDestination.VOICE) }
        )

        // Group 5: Broadcasts & Integrations
        SettingsGroupNavButton(
            title = stringResource(R.string.settings_group_broadcasts_title),
            subtitle = stringResource(R.string.settings_group_broadcasts_desc),
            icon = SettingsDestination.BROADCASTS.icon,
            onClick = { handleNavigate(SettingsDestination.BROADCASTS) }
        )

        // Group 6: Mirror & Network Sharing
        SettingsGroupNavButton(
            title = stringResource(R.string.settings_group_mirror_title),
            subtitle = stringResource(R.string.settings_group_mirror_desc),
            icon = SettingsDestination.MIRROR.icon,
            onClick = { handleNavigate(SettingsDestination.MIRROR) }
        )

        // Group 7: NFC & Hardware
        SettingsGroupNavButton(
            title = stringResource(R.string.settings_group_hardware_title),
            subtitle = stringResource(R.string.settings_group_hardware_desc),
            icon = SettingsDestination.HARDWARE.icon,
            onClick = { handleNavigate(SettingsDestination.HARDWARE) }
        )

        // Group 8: Data Management
        SettingsGroupNavButton(
            title = stringResource(R.string.settings_group_data_title),
            subtitle = stringResource(R.string.settings_group_data_desc),
            icon = SettingsDestination.DATA.icon,
            onClick = { handleNavigate(SettingsDestination.DATA) }
        )

        // Group 9: About Juggluco
        SettingsGroupNavButton(
            title = stringResource(R.string.settings_group_about_title),
            subtitle = stringResource(R.string.settings_group_about_desc),
            icon = SettingsDestination.ABOUT.icon,
            onClick = { handleNavigate(SettingsDestination.ABOUT) }
        )
    }
}

@Composable
private fun SettingsGroupNavButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon in colored circle container
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
