package tk.glucodata.ui.graph

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.R
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.TrendArrow
import tk.glucodata.ui.theme.ClinicalColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Single-line header above the plot showing which day is on screen (the whole point of it: when you
 * jump back a few days you can still tell where you are) and the live/jump control. Both states
 * occupy the same fixed height, so the graph below never moves.
 */
@Composable
internal fun GraphHeader(
    viewportState: GraphViewportState,
    inspected: InspectedReading?,
    unit: GlucoseUnit,
    clinicalColors: ClinicalColors,
    minimalistUnits: Boolean,
    onDismissInspection: () -> Unit
) {
    val dayKey = viewportState.dayKey
    val isLive = viewportState.isLive
    val locale = Locale.getDefault()
    val context = LocalContext.current
    val windowLabel = remember(dayKey, isLive, locale, context) {
        Snapshot.withoutReadObservation {
            formatWindowLabel(context, viewportState.startTimeMillis, viewportState.endTimeMillis, isLive, locale)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (inspected != null) {
            InspectionSummary(
                reading = inspected,
                unit = unit,
                clinicalColors = clinicalColors,
                minimalistUnits = minimalistUnits,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                    .size(28.dp)
                    .clickableNoRipple(onDismissInspection)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.closename),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (!isLive) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = windowLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = windowLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
            LiveIndicator(
                isLive = isLive,
                clinicalColors = clinicalColors,
                onJumpToNow = { viewportState.jumpToNow() }
            )
        }
    }
}

@Composable
private fun LiveIndicator(
    isLive: Boolean,
    clinicalColors: ClinicalColors,
    onJumpToNow: () -> Unit
) {
    if (isLive) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(clinicalColors.inRange, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.live_label),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier.clickableNoRipple(onJumpToNow)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Update,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.now),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun InspectionSummary(
    reading: InspectedReading,
    unit: GlucoseUnit,
    clinicalColors: ClinicalColors,
    minimalistUnits: Boolean,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val timeLabel = remember(reading.timestamp, locale) {
        val sameDay = isSameDay(reading.timestamp, System.currentTimeMillis())
        val skeleton = if (sameDay) "Hm" else "MMMd, Hm"
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(Date(reading.timestamp))
    }
    val statusColor = statusColor(reading.statusOrdinal, clinicalColors)
    val trend = TrendArrow.fromRate(reading.ratePerMinute)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = unit.format(reading.valueMgDl),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )
        if (!minimalistUnits) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = stringResource(unit.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        if (trend != TrendArrow.UNKNOWN && trend != TrendArrow.STABLE) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = trend.symbol,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
        if (reading.hasDelta) {
            Spacer(modifier = Modifier.width(8.dp))
            val delta = reading.valueMgDl - reading.previousValueMgDl
            val sign = if (delta >= 0f) "+" else "-"
            Text(
                text = stringResource(R.string.graph_delta_minutes, sign, unit.format(abs(delta)), reading.deltaMinutes),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun EmptyDataState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.graph_no_data_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.graph_no_data_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val cal = Calendar.getInstance()
    cal.timeInMillis = a
    val dayA = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    cal.timeInMillis = b
    return dayA == cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
}

/**
 * Human label for the visible window: "Today", "Yesterday", "Mon, 15 Sep" or a range across days.
 */
internal fun formatWindowLabel(
    context: android.content.Context,
    startMillis: Long,
    endMillis: Long,
    isLive: Boolean,
    locale: Locale
): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = endMillis
    val endDay = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    cal.timeInMillis = startMillis
    val startDay = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    cal.timeInMillis = System.currentTimeMillis()
    val today = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)

    val dayFormat = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)
    val shortFormat = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMM"), locale)
    val yearFormat = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "yEEEMMMd"), locale)

    fun dayName(dayIndex: Int, timeMillis: Long): String = when (dayIndex) {
        today -> context.getString(R.string.loc_graph_today)
        today - 1 -> context.getString(R.string.loc_graph_yesterday)
        else -> {
            val targetCal = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
            if (targetCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR)) {
                dayFormat.format(Date(timeMillis))
            } else {
                yearFormat.format(Date(timeMillis))
            }
        }
    }

    return when {
        isLive -> context.getString(R.string.loc_graph_today_date, shortFormat.format(Date(System.currentTimeMillis())))
        startDay == endDay -> {
            val name = dayName(endDay, endMillis)
            if (endDay == today || endDay == today - 1) {
                context.getString(R.string.loc_graph_day_time, name, shortFormat.format(Date(endMillis)))
            } else {
                name
            }
        }
        else -> "${shortFormat.format(Date(startMillis))} – ${shortFormat.format(Date(endMillis))}"
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}
