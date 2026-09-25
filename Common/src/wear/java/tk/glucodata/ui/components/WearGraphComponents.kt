package tk.glucodata.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.theme.ClinicalColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun WearGraphHeader(
    selectedPoint: GlucosePoint?,
    unit: GlucoseUnit,
    selectedHours: Int,
    windowEnd: Long,
    isLive: Boolean,
    clinicalColors: ClinicalColors,
    onNow: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(top = 18.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selectedPoint != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = selectedPoint.formatted(unit),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = glucoseStatusColor(selectedPoint.status, clinicalColors)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${unit.symbol} • ${timeFormatter.format(Date(selectedPoint.timestamp))}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isLive) "History (${selectedHours}h)" else timeFormatter.format(Date(windowEnd)),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isLive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NOW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onNow)
                    )
                }
            }
        }
    }
}

@Composable
internal fun WearTimeRangeSelector(
    selectedHours: Int,
    onSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(1, 3, 6, 12).forEach { hours ->
            WearTimeRangePill(
                hours = hours,
                isSelected = selectedHours == hours,
                onClick = { onSelected(hours) }
            )
        }
    }
}

@Composable
private fun WearTimeRangePill(
    hours: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    CompactButton(
        onClick = onClick,
        colors = if (isSelected) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        }
    ) {
        Text(
            text = "${hours}h",
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private fun glucoseStatusColor(status: GlucoseStatus, colors: ClinicalColors) = when (status) {
    GlucoseStatus.VERY_LOW -> colors.veryLow
    GlucoseStatus.LOW -> colors.low
    GlucoseStatus.IN_RANGE -> colors.inRange
    GlucoseStatus.HIGH -> colors.high
    GlucoseStatus.VERY_HIGH -> colors.veryHigh
}
