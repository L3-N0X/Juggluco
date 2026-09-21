package tk.glucodata.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.screens.WearGraphScreen
import tk.glucodata.ui.screens.WearHomeScreen
import tk.glucodata.ui.screens.WearQuickLogScreen
import tk.glucodata.ui.screens.WearSensorsScreen
import tk.glucodata.ui.screens.WearSettingsScreen
import tk.glucodata.ui.theme.WearJugglucoTheme

object WearNavRoutes {
    const val HOME = "home"
    const val GRAPH = "graph"
    const val LOG = "log"
    const val SENSORS = "sensors"
    const val SETTINGS = "settings"
}

@Composable
fun WearApp(
    repository: GlucoseRepository,
    onTriggerNfcScan: () -> Unit,
    onOpenLegacyView: (() -> Unit)? = null
) {
    val navController = rememberSwipeDismissableNavController()

    WearJugglucoTheme {
        AppScaffold {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = WearNavRoutes.HOME
            ) {
                composable(WearNavRoutes.HOME) {
                    WearHomeScreen(
                        repository = repository,
                        onNavigateToGraph = { navController.navigate(WearNavRoutes.GRAPH) },
                        onNavigateToLog = { navController.navigate(WearNavRoutes.LOG) },
                        onNavigateToSensors = { navController.navigate(WearNavRoutes.SENSORS) },
                        onNavigateToSettings = { navController.navigate(WearNavRoutes.SETTINGS) },
                        onTriggerNfcScan = onTriggerNfcScan
                    )
                }

                composable(WearNavRoutes.GRAPH) {
                    WearGraphScreen(
                        repository = repository,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(WearNavRoutes.LOG) {
                    WearQuickLogScreen(
                        repository = repository,
                        onSaved = { navController.popBackStack() }
                    )
                }

                composable(WearNavRoutes.SENSORS) {
                    WearSensorsScreen(
                        repository = repository,
                        onTriggerNfcScan = onTriggerNfcScan
                    )
                }

                composable(WearNavRoutes.SETTINGS) {
                    WearSettingsScreen(
                        repository = repository
                    )
                }
            }
        }
    }
}
