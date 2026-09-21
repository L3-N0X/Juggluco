package tk.glucodata.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import tk.glucodata.R
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.TrendArrow
import tk.glucodata.ui.theme.LocalClinicalColors

@Composable
fun CurrentGlucoseHeroCard(
    currentReading: GlucosePoint?,
    previousReading: GlucosePoint?,
    unit: GlucoseUnit,
    sensorName: String? = null,
    targetLow: Float = 70f,
    targetHigh: Float = 180f,
    minimalistUnits: Boolean = true,
    modifier: Modifier = Modifier
) {
    val clinicalColors = LocalClinicalColors.current

    val isConnected = currentReading != null
    val statusLabel = if (currentReading != null) stringResource(currentReading.status.labelRes) else stringResource(R.string.no_reading)
    val statusColor = if (currentReading != null) {
        when (currentReading.status) {
            GlucoseStatus.VERY_LOW -> clinicalColors.veryLow
            GlucoseStatus.LOW -> clinicalColors.low
            GlucoseStatus.IN_RANGE -> clinicalColors.inRange
            GlucoseStatus.HIGH -> clinicalColors.high
            GlucoseStatus.VERY_HIGH -> clinicalColors.veryHigh
        }
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val trendArrow = if (currentReading != null) TrendArrow.fromRate(currentReading.rate) else TrendArrow.UNKNOWN

    // Calculate time elapsed
    val timeAgoText = if (currentReading != null) {
        val diffMinutes = ((System.currentTimeMillis() - currentReading.timestamp) / (1000 * 60)).toInt()
        when {
            diffMinutes <= 1 -> stringResource(R.string.just_now)
            diffMinutes < 60 -> stringResource(R.string.min_ago, diffMinutes)
            else -> stringResource(R.string.hours_min_ago, diffMinutes / 60, diffMinutes % 60)
        }
    } else {
        stringResource(R.string.waiting_for_readings)
    }

    // Delta from previous reading (clean number, omitting redundant unit)
    val deltaText = if (currentReading != null && previousReading != null && currentReading != previousReading) {
        val deltaMgDl = currentReading.valueMgDl - previousReading.valueMgDl
        val sign = if (deltaMgDl >= 0) "+" else ""
        val numStr = when (unit) {
            GlucoseUnit.MG_DL -> "$sign${deltaMgDl.toInt()}"
            GlucoseUnit.MMOL_L -> "$sign${String.format(java.util.Locale.US, "%.1f", deltaMgDl * unit.factor)}"
        }
        if (!minimalistUnits) "$numStr ${unit.label}" else numStr
    } else null

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Top Row: Sensor Name & Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = "Sensor",
                        tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = sensorName ?: (if (isConnected) stringResource(R.string.cgm_sensor) else stringResource(R.string.no_sensor_connected)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = statusLabel,
                color = statusColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Middle Row: Big Glucose Value + Unit + Trend Arrow
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                val formattedValue = currentReading?.formatted(unit) ?: "—"
                Text(
                    text = formattedValue,
                    fontSize = 50.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-1).sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = unit.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (minimalistUnits) 11.sp else 14.sp,
                    fontWeight = if (minimalistUnits) FontWeight.Normal else FontWeight.Medium,
                    color = if (minimalistUnits) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = if (minimalistUnits) 8.dp else 10.dp)
                )
            }

            // Trend Arrow Box
            Surface(
                shape = CircleShape,
                color = if (isConnected) statusColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isConnected && trendArrow != TrendArrow.UNKNOWN) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = trendArrow.label,
                            tint = statusColor,
                            modifier = Modifier
                                .size(30.dp)
                                .rotate(trendArrow.angleDegrees)
                        )
                    } else {
                        Text(
                            text = "—",
                            color = MaterialTheme.colorScheme.outline,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Row: Time ago, Delta
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timeAgoText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (deltaText != null) {
                Text(
                    text = "  •  $deltaText",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
