package tk.glucodata.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TitleCard
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.model.DeltaCalculation
import tk.glucodata.ui.model.GlucoseUnit

@Composable
fun WearSettingsScreen(
    repository: GlucoseRepository
) {
    val unit by repository.unit.collectAsState()
    val targetLow by repository.targetLow.collectAsState()
    val targetHigh by repository.targetHigh.collectAsState()
    val displayConfig by repository.displayConfig.collectAsState()

    val listState = rememberScalingLazyListState()

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ListHeader {
                    Text("Settings")
                }
            }

            // Glucose Unit Section
            item {
                ListSubHeader {
                    Text("Glucose Unit")
                }
            }
            item {
                RadioButton(
                    selected = unit == GlucoseUnit.MG_DL,
                    onSelect = { repository.setUnit(GlucoseUnit.MG_DL) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("mg/dL") }
                )
            }
            item {
                RadioButton(
                    selected = unit == GlucoseUnit.MMOL_L,
                    onSelect = { repository.setUnit(GlucoseUnit.MMOL_L) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("mmol/L") }
                )
            }

            // Delta Interval Section
            item {
                ListSubHeader {
                    Text("Delta Interval")
                }
            }
            item {
                RadioButton(
                    selected = displayConfig.deltaCalculation == DeltaCalculation.ONE_MINUTE,
                    onSelect = { repository.setDeltaCalculation(DeltaCalculation.ONE_MINUTE) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("1 Minute") }
                )
            }
            item {
                RadioButton(
                    selected = displayConfig.deltaCalculation == DeltaCalculation.FIVE_MINUTES,
                    onSelect = { repository.setDeltaCalculation(DeltaCalculation.FIVE_MINUTES) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("5 Minutes") }
                )
            }

            // Target Range Section
            item {
                ListSubHeader {
                    Text("Target Range")
                }
            }
            item {
                TitleCard(
                    onClick = {},
                    title = { Text("${unit.format(targetLow)} - ${unit.format(targetHigh)} ${unit.label}") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Configured in mobile app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Complications Section
            item {
                ListSubHeader {
                    Text("Complications")
                }
            }
            item {
                TitleCard(
                    onClick = {},
                    title = { Text("Watch Face") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Add glucose complications via your watch face customization menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
