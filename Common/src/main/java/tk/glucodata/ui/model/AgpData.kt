package tk.glucodata.ui.model

import java.util.Calendar

enum class StatsPeriod(val label: String, val days: Int) {
    ONE_DAY("1 Day", 1),
    SEVEN_DAYS("7 Days", 7),
    FOURTEEN_DAYS("14 Days", 14),
    THIRTY_DAYS("30 Days", 30),
    NINETY_DAYS("90 Days", 90);

    val durationMillis: Long
        get() = days * 24 * 3600 * 1000L
}

data class HourlyPercentiles(
    val hour: Int, // 0..23
    val p10: Float,
    val p25: Float,
    val p50: Float, // Median
    val p75: Float,
    val p90: Float,
    val count: Int
)

data class AgpProfile(
    val hourlyPercentiles: List<HourlyPercentiles>,
    val period: StatsPeriod = StatsPeriod.FOURTEEN_DAYS,
    val overallMedian: Float = 120f,
    val daysAnalyzed: Int = 14,
    val totalReadings: Int = 0
) {
    companion object {
        fun calculate(readings: List<GlucosePoint>, period: StatsPeriod): AgpProfile {
            if (readings.isEmpty()) {
                // Return synthetic empty profile with clean 24h baseline
                val defaultHourly = (0..23).map { hour ->
                    HourlyPercentiles(
                        hour = hour,
                        p10 = 85f,
                        p25 = 100f,
                        p50 = 120f,
                        p75 = 145f,
                        p90 = 165f,
                        count = 0
                    )
                }
                return AgpProfile(defaultHourly, period, 120f, period.days, 0)
            }

            val cal = Calendar.getInstance()
            val buckets = Array(24) { ArrayList<Float>() }

            for (pt in readings) {
                if (pt.valueMgDl <= 0f) continue
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
                } else {
                    // Fallback to neighboring interpolation or default
                    result.add(
                        HourlyPercentiles(
                            hour = hour,
                            p10 = 85f,
                            p25 = 100f,
                            p50 = 120f,
                            p75 = 145f,
                            p90 = 165f,
                            count = list.size
                        )
                    )
                }
            }

            val allSorted = readings.map { it.valueMgDl }.filter { it > 0f }.sorted()
            val overallMedian = if (allSorted.isNotEmpty()) getPercentile(allSorted, 50f) else 120f

            return AgpProfile(
                hourlyPercentiles = result,
                period = period,
                overallMedian = overallMedian,
                daysAnalyzed = period.days,
                totalReadings = readings.size
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
