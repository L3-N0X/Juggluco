package tk.glucodata.ui

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tk.glucodata.ui.components.AddEntryBottomSheet
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.NavigationTab
import tk.glucodata.ui.navigation.JugglucoBottomNavBar
import tk.glucodata.ui.navigation.JugglucoNavigationRail
import tk.glucodata.ui.screens.ExportScreen
import tk.glucodata.ui.screens.GlucoseScreen
import tk.glucodata.ui.screens.SensorsScreen
import tk.glucodata.ui.screens.SettingsScreen
import tk.glucodata.ui.screens.StatsScreen
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
import tk.glucodata.ui.screens.settings.TurnServerSettingsScreen
import tk.glucodata.ui.screens.settings.VoiceSettingsScreen
import tk.glucodata.ui.screens.settings.WebServerSettingsScreen
import tk.glucodata.ui.theme.JugglucoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JugglucoApp(
    repository: GlucoseRepository,
    onTriggerNfcScan: () -> Unit = {},
    onOpenLegacyView: () -> Unit = {},
    onExportData: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(NavigationTab.GLUCOSE) }
    var isExportScreenOpen by rememberSaveable { mutableStateOf(false) }
    var activeSettingsDestination by rememberSaveable { mutableStateOf<SettingsDestination?>(null) }
    var selectedMirrorIndex by rememberSaveable { mutableStateOf(-1) }
    var showAddEntrySheet by rememberSaveable { mutableStateOf(false) }
    var isFullscreenGraph by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val systemDark = isSystemInDarkTheme()
    val displayConfig by repository.displayConfig.collectAsState()
    var isDarkThemeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val darkTheme = isDarkThemeOverride ?: if (displayConfig.invertColors) true else systemDark

    val handleOpenExport = {
        isExportScreenOpen = true
    }

    JugglucoTheme(darkTheme = darkTheme) {
        if (isExportScreenOpen) {
            ExportScreen(
                repository = repository,
                onNavigateBack = { isExportScreenOpen = false }
            )
        } else if (activeSettingsDestination != null) {
            when (activeSettingsDestination) {
                SettingsDestination.GLUCOSE_TARGETS -> GlucoseTargetsSettingsScreen(
                    repository = repository,
                    onNavigateBack = { activeSettingsDestination = null }
                )
                SettingsDestination.ALARMS -> AlarmsSettingsScreen(
                    repository = repository,
                    onNavigateBack = { activeSettingsDestination = null }
                )
                SettingsDestination.DISPLAY -> DisplaySettingsScreen(
                    repository = repository,
                    darkThemeOverride = isDarkThemeOverride,
                    onDarkThemeChanged = {
                        isDarkThemeOverride = it
                        repository.setInvertColors(it ?: systemDark)
                    },
                    onOpenFloatingWidgetConfig = { activeSettingsDestination = SettingsDestination.FLOATING_WIDGET },
                    onOpenCalibrationConfig = { activeSettingsDestination = SettingsDestination.CALIBRATION },
                    onNavigateBack = { activeSettingsDestination = null }
                )
                SettingsDestination.FLOATING_WIDGET -> FloatingWidgetSettingsScreen(
                    repository = repository,
                    onNavigateBack = { activeSettingsDestination = SettingsDestination.DISPLAY }
                )
                SettingsDestination.CALIBRATION -> CalibrationSettingsScreen(
                    repository = repository,
                    onNavigateBack = { activeSettingsDestination = SettingsDestination.DISPLAY }
                )
                SettingsDestination.VOICE -> VoiceSettingsScreen(
                    repository = repository,
                    onNavigateBack = { activeSettingsDestination = null }
                )
                SettingsDestination.BROADCASTS -> BroadcastsSettingsScreen(
                    repository = repository,
                    onOpenWebServerConfig = { activeSettingsDestination = SettingsDestination.WEB_SERVER },
                    onOpenLibreViewConfig = { activeSettingsDestination = SettingsDestination.LIBRE_VIEW },
                    onNavigateBack = { activeSettingsDestination = null }
                )
                SettingsDestination.MIRROR -> MirrorSettingsScreen(
                    repository = repository,
                    onOpenConnectionEdit = { idx ->
                        selectedMirrorIndex = idx
                        activeSettingsDestination = SettingsDestination.MIRROR_CONNECTION_EDIT
                    },
                    onOpenTurnServerConfig = { activeSettingsDestination = SettingsDestination.TURN_SERVER },
                    onNavigateBack = { activeSettingsDestination = null }
                )
                SettingsDestination.HARDWARE -> HardwareSettingsScreen(
                    repository = repository,
                    onNavigateBack = { activeSettingsDestination = null }
                )
                SettingsDestination.DATA -> DataSettingsScreen(
                    repository = repository,
                    onOpenLegacyView = onOpenLegacyView,
                    onExportData = handleOpenExport,
                    onNavigateBack = { activeSettingsDestination = null }
                )
                SettingsDestination.ABOUT -> AboutSettingsScreen(
                    repository = repository,
                    onNavigateBack = { activeSettingsDestination = null }
                )
                SettingsDestination.WEB_SERVER -> WebServerSettingsScreen(
                    repository = repository,
                    onNavigateBack = { activeSettingsDestination = SettingsDestination.BROADCASTS }
                )
                SettingsDestination.LIBRE_VIEW -> LibreViewSettingsScreen(
                    repository = repository,
                    onNavigateBack = { activeSettingsDestination = SettingsDestination.BROADCASTS }
                )
                SettingsDestination.MIRROR_CONNECTION_EDIT -> MirrorConnectionEditScreen(
                    repository = repository,
                    connectionIndex = selectedMirrorIndex,
                    onNavigateBack = { activeSettingsDestination = SettingsDestination.MIRROR }
                )
                SettingsDestination.TURN_SERVER -> TurnServerSettingsScreen(
                    repository = repository,
                    onNavigateBack = { activeSettingsDestination = SettingsDestination.MIRROR }
                )
                null -> {}
            }
        } else {
            val showTopBar = !isFullscreenGraph && (!isLandscape || selectedTab != NavigationTab.GLUCOSE) && selectedTab != NavigationTab.SENSORS
            val showBottomBar = !isFullscreenGraph && !isLandscape
            val showNavRail = !isFullscreenGraph && isLandscape

            Scaffold(
                topBar = {
                    if (showTopBar) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = if (selectedTab == NavigationTab.GLUCOSE) "Juggluco" else stringResource(selectedTab.titleRes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                },
                bottomBar = {
                    if (showBottomBar) {
                        JugglucoBottomNavBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )
                    }
                },
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState)
                }
            ) { innerPadding ->
                val contentPadding = when {
                    isFullscreenGraph -> PaddingValues(0.dp)
                    selectedTab == NavigationTab.SENSORS -> PaddingValues(
                        bottom = innerPadding.calculateBottomPadding()
                    )
                    else -> innerPadding
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                ) {
                    if (showNavRail) {
                        JugglucoNavigationRail(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        when (selectedTab) {
                            NavigationTab.GLUCOSE -> {
                                GlucoseScreen(
                                    repository = repository,
                                    onOpenAddEntry = { showAddEntrySheet = true },
                                    isFullscreen = isFullscreenGraph,
                                    onToggleFullscreen = { isFullscreenGraph = !isFullscreenGraph }
                                )
                            }
                            NavigationTab.STATS -> {
                                StatsScreen(
                                    repository = repository,
                                    onExportData = handleOpenExport
                                )
                            }
                            NavigationTab.SENSORS -> {
                                SensorsScreen(
                                    repository = repository,
                                    onTriggerNfcScan = onTriggerNfcScan
                                )
                            }
                            NavigationTab.SETTINGS -> {
                                SettingsScreen(
                                    repository = repository,
                                    isDarkTheme = darkTheme,
                                    darkThemeOverride = isDarkThemeOverride,
                                    onDarkThemeChanged = {
                                        isDarkThemeOverride = it
                                        repository.setInvertColors(it ?: systemDark)
                                    },
                                    onOpenLegacyView = onOpenLegacyView,
                                    onExportData = handleOpenExport,
                                    currentDestination = activeSettingsDestination,
                                    onNavigateToDestination = { activeSettingsDestination = it },
                                    selectedMirrorIndex = selectedMirrorIndex,
                                    onSelectMirrorIndex = { selectedMirrorIndex = it }
                                )
                            }
                        }
                    }
                }

                if (showAddEntrySheet) {
                    val currentUnit = repository.unit.value
                    AddEntryBottomSheet(
                        sheetState = sheetState,
                        unit = currentUnit,
                        onDismiss = { showAddEntrySheet = false },
                        onSave = { type, value, note ->
                            repository.addLogEntry(type, value, note)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Logged ${type.label}: $value")
                            }
                        }
                    )
                }
            }
        }
    }
}
