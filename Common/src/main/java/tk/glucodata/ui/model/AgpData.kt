package tk.glucodata.ui.model

import androidx.annotation.StringRes
import tk.glucodata.R
import java.util.TimeZone

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
        /**
         * Builds the hourly percentile profile.
         *
         * Values are bucketed into 24 primitive [FloatArray]s rather than 24 [ArrayList]s of boxed
         * [Float]s, and the hour is derived arithmetically instead of through a [Calendar] per
         * reading. Both matter: this runs over the entire sensor history - six figures of readings -
         * and the old version boxed every one of them twice and allocated a per-reading calendar.
         */
        fun calculate(readings: List<GlucosePoint>, period: StatsPeriod): AgpProfile {
            if (readings.isEmpty()) {
                return AgpProfile(emptyHourly(), period, 0f, period.days, 0)
            }

            // Growable primitive buckets, one per hour of day.
            var buckets = Array(24) { FloatArray(64) }
            var counts = IntArray(24)
            val overall = FloatArray(readings.size)
            var total = 0
            var minutesEast = TimeZone.getDefault().getOffset(readings[0].timestamp)

            for (pt in readings) {
                val value = pt.valueMgDl
                if (value <= 0f) continue
                if ((total and 0x3FF) == 0) {
                    // Re-derive the offset occasionally so a DST change or a zone change mid-history
                    // does not leave every later bucket an hour out.
                    minutesEast = TimeZone.getDefault().getOffset(pt.timestamp)
                }
                val local = pt.timestamp + minutesEast
                val hour = (((local / 60_000L) % 24L) + 24L).toInt() % 24
                var bucket = buckets[hour]
                if (counts[hour] == bucket.size) bucket = bucket.copyOf(bucket.size * 2)
                buckets[hour] = bucket
                bucket[counts[hour]++] = value
                overall[total++] = value
            }

            if (total == 0) {
                return AgpProfile(emptyHourly(), period, 0f, period.days, 0)
            }

            val result = ArrayList<HourlyPercentiles>(24)
            for (hour in 0..23) {
                val bucket = buckets[hour]
                val count = counts[hour]
                if (count == 0) {
                    result.add(HourlyPercentiles(hour, 0f, 0f, 0f, 0f, 0f, 0))
                } else {
                    java.util.Arrays.sort(bucket, 0, count)
                    val p10 = percentile(bucket, count, 10f)
                    val p25 = percentile(bucket, count, 25f)
                    val p50 = percentile(bucket, count, 50f)
                    val p75 = percentile(bucket, count, 75f)
                    val p90 = percentile(bucket, count, 90f)
                    result.add(
                        HourlyPercentiles(
                            hour = hour,
                            p10 = if (count >= 3) p10 else bucket[0],
                            p25 = p25,
                            p50 = p50,
                            p75 = p75,
                            p90 = if (count >= 3) p90 else bucket[count - 1],
                            count = count
                        )
                    )
                }
            }

            val allSorted = overall.copyOf(total)
            java.util.Arrays.sort(allSorted)
            val overallMedian = percentile(allSorted, total, 50f)

            return AgpProfile(
                hourlyPercentiles = result,
                period = period,
                overallMedian = overallMedian,
                daysAnalyzed = period.days,
                totalReadings = total
            )
        }

        private fun emptyHourly(): List<HourlyPercentiles> = (0..23).map { hour ->
            HourlyPercentiles(hour = hour, p10 = 0f, p25 = 0f, p50 = 0f, p75 = 0f, p90 = 0f, count = 0)
        }

        /** Linear-interpolated percentile of the first [count] entries of an already sorted array. */
        private fun percentile(sorted: FloatArray, count: Int, percentile: Float): Float {
            if (count == 0) return 120f
            if (count == 1) return sorted[0]
            val index = (percentile / 100f) * (count - 1)
            val lower = index.toInt()
            val upper = (lower + 1).coerceAtMost(count - 1)
            val weight = index - lower
            return sorted[lower] * (1f - weight) + sorted[upper] * weight
        }
    }
}
