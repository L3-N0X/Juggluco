package tk.glucodata.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import tk.glucodata.ui.components.AddEntryBottomSheet
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.NavigationTab
import tk.glucodata.ui.navigation.JugglucoBottomNavBar
import tk.glucodata.ui.screens.GlucoseScreen
import tk.glucodata.ui.screens.LogbookScreen
import tk.glucodata.ui.screens.SensorsScreen
import tk.glucodata.ui.screens.SettingsScreen
import tk.glucodata.ui.screens.StatsScreen
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
    var showAddEntrySheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val systemDark = isSystemInDarkTheme()
    var isDarkThemeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val darkTheme = isDarkThemeOverride ?: systemDark

    JugglucoTheme(darkTheme = darkTheme) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (selectedTab == NavigationTab.GLUCOSE) "Juggluco" else selectedTab.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    navigationIcon = {
                        IconButton(onClick = onTriggerNfcScan) {
                            Icon(
                                imageVector = Icons.Default.Nfc,
                                contentDescription = "Quick NFC Scan",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                isDarkThemeOverride = !darkTheme
                            }
                        ) {
                            Icon(
                                imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Dark Mode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                repository.refreshAll()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Data refreshed")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Data",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            },
            bottomBar = {
                JugglucoBottomNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    NavigationTab.GLUCOSE -> {
                        GlucoseScreen(
                            repository = repository,
                            onOpenAddEntry = { showAddEntrySheet = true }
                        )
                    }
                    NavigationTab.STATS -> {
                        StatsScreen(
                            repository = repository,
                            onExportData = onExportData
                        )
                    }
                    NavigationTab.LOGBOOK -> {
                        LogbookScreen(
                            repository = repository,
                            onOpenAddEntry = { showAddEntrySheet = true }
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
                            onOpenLegacyView = onOpenLegacyView,
                            onExportData = onExportData
                        )
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
