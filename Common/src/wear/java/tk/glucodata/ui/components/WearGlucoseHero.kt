package tk.glucodata.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import tk.glucodata.ui.model.DeltaCalculation
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.TrendArrow
import tk.glucodata.ui.theme.LocalClinicalColors

@Composable
fun WearGlucoseHero(
    currentReading: GlucosePoint?,
    readings: List<GlucosePoint>,
    unit: GlucoseUnit,
    targetLow: Float = 70f,
    targetHigh: Float = 180f,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clinical = LocalClinicalColors.current

    val status = currentReading?.status ?: GlucoseStatus.IN_RANGE
    val statusColor = when (status) {
        GlucoseStatus.IN_RANGE -> clinical.inRange
        GlucoseStatus.LOW, GlucoseStatus.HIGH -> clinical.low
        GlucoseStatus.VERY_LOW, GlucoseStatus.VERY_HIGH -> clinical.veryLow
    }

    val arrow = remember(currentReading?.rate) {
        currentReading?.let { TrendArrow.fromRate(it.rate) } ?: TrendArrow.UNKNOWN
    }

    // Delta calculation
    val deltaReading = remember(currentReading, readings) {
        DeltaCalculation.findDeltaReading(currentReading, readings, DeltaCalculation.FIVE_MINUTES)
    }
    val deltaText = remember(currentReading, deltaReading, unit) {
        if (currentReading != null && deltaReading != null) {
            val diff = currentReading.valueMgDl - deltaReading.valueMgDl
            val prefix = if (diff > 0) "+" else ""
            when (unit) {
                GlucoseUnit.MG_DL -> "$prefix${diff.toInt()}"
                GlucoseUnit.MMOL_L -> "$prefix${String.format(java.util.Locale.US, "%.1f", diff * unit.factor)}"
            }
        } else null
    }

    // Time elapsed string
    val timeAgo = remember(currentReading?.timestamp) {
        val ts = currentReading?.timestamp ?: return@remember "--"
        val elapsed = (System.currentTimeMillis() - ts).coerceAtLeast(0L)
        val mins = (elapsed / 60_000L).toInt()
        when {
            mins <= 0 -> "just now"
            mins < 60 -> "${mins}m ago"
            else -> "${mins / 60}h ago"
        }
    }

    val isStale = remember(currentReading?.timestamp) {
        val ts = currentReading?.timestamp ?: return@remember true
        System.currentTimeMillis() - ts > 15 * 60 * 1000L
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Value + Arrow
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = currentReading?.formatted(unit) ?: "---",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = if (isStale) MaterialTheme.colorScheme.onSurfaceVariant else statusColor,
                letterSpacing = (-1).sp
            )

            if (arrow != TrendArrow.UNKNOWN) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = arrow.symbol,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = statusColor
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Delta, Time Ago
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (deltaText != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Δ $deltaText",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = timeAgo,
                fontSize = 13.sp,
                color = if (isStale) clinical.low else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
