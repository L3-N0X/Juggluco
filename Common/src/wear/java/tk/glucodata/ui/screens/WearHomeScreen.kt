package tk.glucodata.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import tk.glucodata.ui.components.WearAlertIcon
import tk.glucodata.ui.components.WearGlucoseHero
import tk.glucodata.ui.components.WearMiniGraph
import tk.glucodata.ui.data.GlucoseRepository

@Composable
fun WearHomeScreen(
    repository: GlucoseRepository,
    onNavigateToGraph: () -> Unit,
    onNavigateToLog: () -> Unit,
    onNavigateToSensors: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAlerts: () -> Unit
) {
    val currentReading by repository.currentReading.collectAsState()
    val readings by repository.readings.collectAsState()
    val unit by repository.unit.collectAsState()
    val targetLow by repository.targetLow.collectAsState()
    val targetHigh by repository.targetHigh.collectAsState()

    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = 0,
        initialCenterItemScrollOffset = 0
    )

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            anchorType = ScalingLazyListAnchorType.ItemStart,
            autoCentering = null,
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
            contentPadding = PaddingValues(start = 10.dp, top = 20.dp, end = 10.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Hero Glucose Readout
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WearAlertIcon(onOpenAlerts = onNavigateToAlerts)
                    Spacer(modifier = Modifier.height(2.dp))
                    WearGlucoseHero(
                        currentReading = currentReading,
                        readings = readings,
                        unit = unit,
                        targetLow = targetLow,
                        targetHigh = targetHigh,
                        onClick = onNavigateToGraph
                    )
                }
            }

            // 2. Mini Sparkline Graph Card
            item {
                Card(
                    onClick = onNavigateToGraph,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 3.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        WearMiniGraph(
                            readings = readings,
                            targetLow = targetLow,
                            targetHigh = targetHigh,
                            hoursToShow = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "2h Trend • Tap for graph",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Prominent Action: Quick Log
            item {
                Button(
                    onClick = onNavigateToLog,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                    },
                    label = {
                        Text(
                            text = "Quick Log",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            // 4. Action: History Graph
            item {
                FilledTonalButton(
                    onClick = onNavigateToGraph,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                    },
                    label = {
                        Text(
                            text = "Graph",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            // 5. Action: Sensors & Status
            item {
                FilledTonalButton(
                    onClick = onNavigateToSensors,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                    },
                    label = {
                        Text(
                            text = "Sensors",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            // 6. Action: Settings
            item {
                FilledTonalButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                    },
                    label = {
                        Text(
                            text = "Settings",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}
