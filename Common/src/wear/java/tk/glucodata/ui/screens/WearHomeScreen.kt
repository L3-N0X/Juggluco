package tk.glucodata.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
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
    onTriggerNfcScan: () -> Unit
) {
    val currentReading by repository.currentReading.collectAsState()
    val readings by repository.readings.collectAsState()
    val unit by repository.unit.collectAsState()
    val targetLow by repository.targetLow.collectAsState()
    val targetHigh by repository.targetHigh.collectAsState()

    val listState = rememberScalingLazyListState()

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Hero Glucose Readout
            item {
                WearGlucoseHero(
                    currentReading = currentReading,
                    readings = readings,
                    unit = unit,
                    targetLow = targetLow,
                    targetHigh = targetHigh,
                    onClick = onNavigateToGraph,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
            }

            // 2. Mini Sparkline Graph Card
            item {
                Card(
                    onClick = onNavigateToGraph,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        WearMiniGraph(
                            readings = readings,
                            targetLow = targetLow,
                            targetHigh = targetHigh,
                            hoursToShow = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap for full graph",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Quick Action: Log Carbs & Insulin
            item {
                WearActionChip(
                    icon = Icons.Default.Add,
                    label = "Quick Log",
                    subtitle = "Insulin & Carbs",
                    onClick = onNavigateToLog
                )
            }

            // 4. Quick Action: Detailed Graph
            item {
                WearActionChip(
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    label = "History Graph",
                    subtitle = "Zoom & Inspect",
                    onClick = onNavigateToGraph
                )
            }

            // 5. Quick Action: Scan NFC Sensor
            item {
                WearActionChip(
                    icon = Icons.Default.Nfc,
                    label = "Scan NFC",
                    subtitle = "Read sensor directly",
                    onClick = onTriggerNfcScan
                )
            }

            // 6. Quick Action: Sensor Status
            item {
                WearActionChip(
                    icon = Icons.Default.Sensors,
                    label = "Sensors & Sync",
                    subtitle = "Device health",
                    onClick = onNavigateToSensors
                )
            }

            // 7. Settings
            item {
                WearActionChip(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    subtitle = "Units & targets",
                    onClick = onNavigateToSettings
                )
            }
        }
    }
}

@Composable
fun WearActionChip(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
