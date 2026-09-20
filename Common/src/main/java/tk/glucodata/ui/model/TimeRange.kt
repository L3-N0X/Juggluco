package tk.glucodata.ui.model

import androidx.annotation.StringRes
import tk.glucodata.R
import kotlin.math.abs

data class TimeRange(
    val label: String,
    val durationMillis: Long,
    @StringRes val labelRes: Int? = null,
    val isCustom: Boolean = false
) {
    companion object {
        val ONE_HOUR = TimeRange("1h", 1 * 3600 * 1000L, R.string.timerange_1h)
        val THREE_HOURS = TimeRange("3h", 3 * 3600 * 1000L, R.string.timerange_3h)
        val SIX_HOURS = TimeRange("6h", 6 * 3600 * 1000L, R.string.timerange_6h)
        val TWELVE_HOURS = TimeRange("12h", 12 * 3600 * 1000L, R.string.timerange_12h)
        val TWENTY_FOUR_HOURS = TimeRange("24h", 24 * 3600 * 1000L, R.string.timerange_24h)
        val SEVEN_DAYS = TimeRange("7d", 7 * 24 * 3600 * 1000L, R.string.timerange_all)

        val PRESETS = listOf(ONE_HOUR, THREE_HOURS, SIX_HOURS, TWELVE_HOURS, TWENTY_FOUR_HOURS, SEVEN_DAYS)

        fun matchPreset(durationMillis: Long): TimeRange? {
            for (preset in PRESETS) {
                val presetMillis = preset.durationMillis
                if (preset == SEVEN_DAYS) {
                    // For SEVEN_DAYS (labeled "All" / 7d), match anything from ~6 days and up
                    if (durationMillis >= (presetMillis * 0.85).toLong()) {
                        return preset
                    }
                } else {
                    val tolerance = (presetMillis * 0.15).toLong()
                    if (abs(durationMillis - presetMillis) <= tolerance) {
                        return preset
                    }
                }
            }
            return null
        }

        fun fromHours(hours: Int): TimeRange {
            val h = hours.coerceIn(1, 168)
            val label = when {
                h >= 24 && h % 24 == 0 -> "${h / 24}d"
                else -> "${h}h"
            }
            return TimeRange(label = label, durationMillis = h * 3600 * 1000L, isCustom = true)
        }

        fun fromDays(days: Int): TimeRange {
            val d = days.coerceIn(1, 90)
            return TimeRange(label = "${d}d", durationMillis = d * 24 * 3600 * 1000L, isCustom = true)
        }

        fun fromDuration(durationMillis: Long, customLabel: String? = null): TimeRange {
            val clamped = durationMillis.coerceIn(1_800_000L, 90 * 86_400_000L)
            val hours = (clamped / (3600 * 1000L)).toInt()
            val label = customLabel ?: when {
                hours >= 24 && hours % 24 == 0 -> "${hours / 24}d"
                hours > 0 -> "${hours}h"
                else -> "${clamped / 60_000L}m"
            }
            return TimeRange(label = label, durationMillis = clamped, isCustom = true)
        }
    }
}
