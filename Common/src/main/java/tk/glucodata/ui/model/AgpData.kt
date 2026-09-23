package tk.glucodata.ui.model

import androidx.annotation.StringRes
import tk.glucodata.R
import java.util.Calendar

data class StatsPeriod(
    val days: Int,
    @StringRes val labelRes: Int? = null,
    val isCustom: Boolean = false
) {
    val durationMillis: Long
        get() = days * 24 * 3600 * 1000L

    companion object {
        val ONE_DAY = StatsPeriod(1, R.string.stats_period_1d)
        val SEVEN_DAYS = StatsPeriod(7, R.string.stats_period_7d)
        val FOURTEEN_DAYS = StatsPeriod(14, R.string.stats_period_14d)
        val THIRTY_DAYS = StatsPeriod(30, R.string.stats_period_30d)
        val NINETY_DAYS = StatsPeriod(90, R.string.stats_period_90d)

        val PRESETS = listOf(ONE_DAY, SEVEN_DAYS, FOURTEEN_DAYS, THIRTY_DAYS, NINETY_DAYS)

        fun fromDays(days: Int): StatsPeriod {
            val d = days.coerceIn(1, 365)
            return StatsPeriod(
                days = d,
                isCustom = true
            )
        }
    }
}

data class HourlyPercentiles(
    val hour: Int, // 0..23
    val p10: Float,
    val p25: Float,
    val p50: Float, // Median
    val p75: Float,
    val p90: Float,
    val count: Int
) {
    val hasData: Boolean get() = count > 0
}

data class AgpProfile(
    val hourlyPercentiles: List<HourlyPercentiles>,
    val period: StatsPeriod = StatsPeriod.FOURTEEN_DAYS,
    val overallMedian: Float = 0f,
    val daysAnalyzed: Int = 14,
    val totalReadings: Int = 0
) {
    val hasAnyData: Boolean get() = totalReadings > 0 && hourlyPercentiles.any { it.hasData }

    companion object {
        fun calculate(readings: List<GlucosePoint>, period: StatsPeriod): AgpProfile {
            val validReadings = readings.filter { it.valueMgDl > 0f }
            if (validReadings.isEmpty()) {
                val defaultHourly = (0..23).map { hour ->
                    HourlyPercentiles(
                        hour = hour,
                        p10 = 0f,
                        p25 = 0f,
                        p50 = 0f,
                        p75 = 0f,
                        p90 = 0f,
                        count = 0
                    )
                }
                return AgpProfile(defaultHourly, period, 0f, period.days, 0)
            }

            val cal = Calendar.getInstance()
            val buckets = Array(24) { ArrayList<Float>() }

            for (pt in validReadings) {
                cal.timeInMillis = pt.timestamp
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                buckets[hour].add(pt.valueMgDl)
            }

            val result = ArrayList<HourlyPercentiles>(24)
            for (hour in 0..23) {
                val list = buckets[hour]
                if (list.size >= 3) {
                    list.sort()
                    val p10 = getPercentile(list, 10f)
                    val p25 = getPercentile(list, 25f)
                    val p50 = getPercentile(list, 50f)
                    val p75 = getPercentile(list, 75f)
                    val p90 = getPercentile(list, 90f)
                    result.add(HourlyPercentiles(hour, p10, p25, p50, p75, p90, list.size))
                } else if (list.isNotEmpty()) {
                    list.sort()
                    val p10 = list.first()
                    val p25 = getPercentile(list, 25f)
                    val p50 = getPercentile(list, 50f)
                    val p75 = getPercentile(list, 75f)
                    val p90 = list.last()
                    result.add(HourlyPercentiles(hour, p10, p25, p50, p75, p90, list.size))
                } else {
                    result.add(
                        HourlyPercentiles(
                            hour = hour,
                            p10 = 0f,
                            p25 = 0f,
                            p50 = 0f,
                            p75 = 0f,
                            p90 = 0f,
                            count = 0
                        )
                    )
                }
            }

            val allSorted = validReadings.map { it.valueMgDl }.sorted()
            val overallMedian = if (allSorted.isNotEmpty()) getPercentile(allSorted, 50f) else 0f

            return AgpProfile(
                hourlyPercentiles = result,
                period = period,
                overallMedian = overallMedian,
                daysAnalyzed = period.days,
                totalReadings = validReadings.size
            )
        }

        private fun getPercentile(sortedList: List<Float>, percentile: Float): Float {
            if (sortedList.isEmpty()) return 120f
            if (sortedList.size == 1) return sortedList[0]
            val index = (percentile / 100f) * (sortedList.size - 1)
            val lower = index.toInt()
            val upper = (lower + 1).coerceAtMost(sortedList.size - 1)
            val weight = index - lower
            return sortedList[lower] * (1f - weight) + sortedList[upper] * weight
        }
    }
}
