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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
    sensorName: String = "FreeStyle Libre 3",
    targetLow: Float = 70f,
    targetHigh: Float = 180f,
    modifier: Modifier = Modifier
) {
    val clinicalColors = LocalClinicalColors.current

    val status = currentReading?.status ?: GlucoseStatus.IN_RANGE
    val (statusColor, containerColor) = when (status) {
        GlucoseStatus.VERY_LOW -> Pair(clinicalColors.veryLow, clinicalColors.veryLowContainer)
        GlucoseStatus.LOW -> Pair(clinicalColors.low, clinicalColors.lowContainer)
        GlucoseStatus.IN_RANGE -> Pair(clinicalColors.inRange, clinicalColors.inRangeContainer)
        GlucoseStatus.HIGH -> Pair(clinicalColors.high, clinicalColors.highContainer)
        GlucoseStatus.VERY_HIGH -> Pair(clinicalColors.veryHigh, clinicalColors.veryHighContainer)
    }

    val trendArrow = if (currentReading != null) TrendArrow.fromRate(currentReading.rate) else TrendArrow.UNKNOWN

    // Calculate time elapsed
    val timeAgoText = if (currentReading != null) {
        val diffMinutes = ((System.currentTimeMillis() - currentReading.timestamp) / (1000 * 60)).toInt()
        when {
            diffMinutes <= 1 -> "Just now"
            diffMinutes < 60 -> "$diffMinutes min ago"
            else -> "${diffMinutes / 60}h ${diffMinutes % 60}m ago"
        }
    } else {
        "No reading"
    }

    // Delta from previous reading
    val deltaText = if (currentReading != null && previousReading != null && currentReading != previousReading) {
        val deltaMgDl = currentReading.valueMgDl - previousReading.valueMgDl
        val sign = if (deltaMgDl >= 0) "+" else ""
        when (unit) {
            GlucoseUnit.MG_DL -> "$sign${deltaMgDl.toInt()} mg/dL"
            GlucoseUnit.MMOL_L -> "$sign${String.format(java.util.Locale.US, "%.1f", deltaMgDl * unit.factor)} mmol/L"
        }
    } else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Top Row: Sensor Name & Status Chip
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
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Sensor",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = sensorName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = containerColor
                ) {
                    Text(
                        text = status.label,
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

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
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-1).sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = unit.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                // Trend Arrow Box
                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (trendArrow != TrendArrow.UNKNOWN) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = trendArrow.label,
                                tint = statusColor,
                                modifier = Modifier
                                    .size(32.dp)
                                    .rotate(trendArrow.angleDegrees)
                            )
                        } else {
                            Text(
                                text = "—",
                                color = statusColor,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Row: Time ago, Delta, Target Range info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

                Text(
                    text = "Target: ${unit.format(targetLow)}–${unit.format(targetHigh)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
