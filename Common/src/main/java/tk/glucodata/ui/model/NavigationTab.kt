package tk.glucodata.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavigationTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    GLUCOSE(
        title = "Glucose",
        selectedIcon = Icons.AutoMirrored.Filled.ShowChart,
        unselectedIcon = Icons.AutoMirrored.Outlined.ShowChart
    ),
    STATS(
        title = "Stats",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart
    ),
    LOGBOOK(
        title = "Logbook",
        selectedIcon = Icons.AutoMirrored.Filled.ListAlt,
        unselectedIcon = Icons.AutoMirrored.Outlined.ListAlt
    ),
    SENSORS(
        title = "Sensors",
        selectedIcon = Icons.Filled.Sensors,
        unselectedIcon = Icons.Outlined.Sensors
    ),
    SETTINGS(
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    );
}
