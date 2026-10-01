package tk.glucodata.ui.model

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Aggregated statistics over the readings of one analysis window.
 *
 * Every band duration and percentage is weighted by elapsed time rather than counted per reading. A
 * CGM value describes the whole stretch until the next one arrives, so counting readings over-weights
 * a densely sampled stretch (a burst of finger pricks, a re-imported history block) and under-weights
 * the quiet ones. [MAX_GAP_MILLIS] bounds how much a single reading may stand for: a longer stretch
 * means the sensor stopped recording rather than that the glucose held still, so that time is
 * dropped from the percentages instead of being credited to whichever band happened to be last seen.
 *
 * The band durations are kept in milliseconds next to the rounded percentages because a screen has
 * to label them against the period it analysed, and `percent / 100 * span` drifts by hours once a
 * window covers more than a day.
 */
data class GlucoseStats(
    val timeInRangePercent: Int = 0,    // Target: low - high (Clinical goal > 70%)
    val timeBelowPercent: Int = 0,      // Low: veryLow - (low - 1)
    val timeVeryLowPercent: Int = 0,    // Very Low: < veryLow (Clinical goal < 1%)
    val timeAbovePercent: Int = 0,      // High: (high + 1) - veryHigh
    val timeVeryHighPercent: Int = 0,   // Very High: > veryHigh (Clinical goal < 5%)
    val averageMgDl: Float = 0f,
    val minMgDl: Float = 0f,
    val maxMgDl: Float = 0f,
    val standardDeviation: Float = 0f,
    val cvPercent: Float = 0f,          // Coefficient of Variation = (SD / Mean) * 100 (Goal < 36%)
    val estimatedA1c: Float = 0f,       // Glucose Management Indicator (GMI)
    val readingsCount: Int = 0,
    val activeTimePercent: Float = 0f,
    val timeVeryLowMillis: Long = 0L,
    val timeBelowMillis: Long = 0L,
    val timeInRangeMillis: Long = 0L,
    val timeAboveMillis: Long = 0L,
    val timeVeryHighMillis: Long = 0L,
    /** Length of the window the readings were selected from, which active time is measured against. */
    val expectedWindowMillis: Long = 0L
) {
    /** Time the readings actually cover, i.e. the sum of all weighted band durations. */
    val observedMillis: Long
        get() = timeVeryLowMillis + timeBelowMillis + timeInRangeMillis + timeAboveMillis + timeVeryHighMillis

    /** Weighted duration the readings spend in [status]. */
    fun millisIn(status: GlucoseStatus): Long = when (status) {
        GlucoseStatus.VERY_LOW -> timeVeryLowMillis
        GlucoseStatus.LOW -> timeBelowMillis
        GlucoseStatus.IN_RANGE -> timeInRangeMillis
        GlucoseStatus.HIGH -> timeAboveMillis
        GlucoseStatus.VERY_HIGH -> timeVeryHighMillis
    }

    companion object {
        /**
         * Interval a reading stands for when the window ends before another one arrives. Matches the
         * five minute cadence a CGM sensor reports at, so a window whose last reading is still fresh
         * is not charged for time nobody could have covered.
         */
        const val NOMINAL_SAMPLE_MILLIS = 5 * 60_000L

        /**
         * Longest stretch one reading may be credited with. Reporting every five minutes, a gap past
         * a quarter of an hour means the sensor lost the wearer rather than that the value repeated,
         * which is what separates a worn sensor from a stored one in the active time figure.
         */
        const val MAX_GAP_MILLIS = 15 * 60_000L

        /**
         * @param expectedWindowMillis length of the window the readings came from, used as the
         * denominator of [activeTimePercent]. When it is zero the span the readings themselves cover
         * is assumed, so a caller without a window still gets a meaningful coverage figure.
         */
        fun calculate(
            readings: List<GlucosePoint>,
            range: GlucoseRange = GlucoseRange.DEFAULT,
            expectedWindowMillis: Long = 0L
        ): GlucoseStats {
            if (readings.isEmpty()) return GlucoseStats()

            // Compact the readings into primitive arrays first: this runs over the whole sensor
            // history, and the weights are worked out in a pass of their own so the aggregation
            // below never has to re-derive a value or a timestamp.
            val times = LongArray(readings.size)
            val values = FloatArray(readings.size)
            var used = 0
            var min = Float.MAX_VALUE
            var max = -Float.MAX_VALUE
            var firstTimestamp = Long.MAX_VALUE
            var lastTimestamp = Long.MIN_VALUE
            for (point in readings) {
                val value = point.valueMgDl
                if (value <= 0f) continue
                times[used] = point.timestamp
                values[used] = value
                used++
                if (value < min) min = value
                if (value > max) max = value
                if (point.timestamp < firstTimestamp) firstTimestamp = point.timestamp
                if (point.timestamp > lastTimestamp) lastTimestamp = point.timestamp
            }
            if (used == 0) return GlucoseStats()

            val weights = LongArray(used)
            var observedMillis = 0L
            for (i in 0 until used) {
                val weight = coveredMillis(times, used, i)
                weights[i] = weight
                observedMillis += weight
            }

            // A source that never reports often enough to cover a single interval - a hand entered
            // history, an hourly import - still has readings worth summarising, so fall back to one
            // unit each instead of collapsing the bands to nothing. Active time keeps reporting the
            // zero coverage that source actually has.
            var basisMillis = observedMillis
            if (basisMillis == 0L) {
                weights.fill(1L)
                basisMillis = used.toLong()
            }

            var veryLowMillis = 0L
            var lowMillis = 0L
            var inRangeMillis = 0L
            var highMillis = 0L
            var veryHighMillis = 0L
            var weightedSum = 0.0
            var weightedSquareSum = 0.0
            for (i in 0 until used) {
                val value = values[i]
                val weight = weights[i].toDouble()
                weightedSum += value * weight
                weightedSquareSum += value.toDouble() * value * weight
                when (range.statusOf(value)) {
                    GlucoseStatus.VERY_LOW -> veryLowMillis += weights[i]
                    GlucoseStatus.LOW -> lowMillis += weights[i]
                    GlucoseStatus.IN_RANGE -> inRangeMillis += weights[i]
                    GlucoseStatus.HIGH -> highMillis += weights[i]
                    GlucoseStatus.VERY_HIGH -> veryHighMillis += weights[i]
                }
            }

            val mean = weightedSum / basisMillis
            val variance = (weightedSquareSum / basisMillis) - (mean * mean)
            val avg = mean.toFloat()
            val stdDev = sqrt(variance.coerceAtLeast(0.0)).toFloat()
            val cv = if (avg > 0) (stdDev / avg) * 100f else 0f
            // GMI formula: 3.31 + 0.02392 * mean_glucose
            val gmi = 3.31f + (0.02392f * avg)

            val expected = if (expectedWindowMillis > 0L) {
                expectedWindowMillis
            } else {
                (lastTimestamp - firstTimestamp + NOMINAL_SAMPLE_MILLIS).coerceAtLeast(1L)
            }

            return GlucoseStats(
                timeInRangePercent = percentOf(inRangeMillis, basisMillis),
                timeBelowPercent = percentOf(lowMillis, basisMillis),
                timeVeryLowPercent = percentOf(veryLowMillis, basisMillis),
                timeAbovePercent = percentOf(highMillis, basisMillis),
                timeVeryHighPercent = percentOf(veryHighMillis, basisMillis),
                averageMgDl = avg,
                minMgDl = min,
                maxMgDl = max,
                standardDeviation = stdDev,
                cvPercent = cv,
                estimatedA1c = gmi,
                readingsCount = used,
                activeTimePercent = (observedMillis * 100.0 / expected).coerceIn(0.0, 100.0).toFloat(),
                timeVeryLowMillis = veryLowMillis,
                timeBelowMillis = lowMillis,
                timeInRangeMillis = inRangeMillis,
                timeAboveMillis = highMillis,
                timeVeryHighMillis = veryHighMillis,
                expectedWindowMillis = expected
            )
        }

        /**
         * Time the reading at [index] stands for: the stretch up to the next reading, the nominal
         * interval for the last one, and nothing at all when the sensor cannot be assumed to have
         * kept recording across the gap.
         */
        private fun coveredMillis(times: LongArray, count: Int, index: Int): Long {
            val span = if (index + 1 < count) times[index + 1] - times[index] else NOMINAL_SAMPLE_MILLIS
            return if (span in 1..MAX_GAP_MILLIS) span else 0L
        }

        private fun percentOf(partMillis: Long, basisMillis: Long): Int =
            ((partMillis * 100.0) / basisMillis).roundToInt()
    }
}
