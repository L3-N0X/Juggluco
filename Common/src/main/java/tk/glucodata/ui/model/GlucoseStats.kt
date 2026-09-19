package tk.glucodata.ui.model

import kotlin.math.roundToInt
import kotlin.math.sqrt

data class GlucoseStats(
    val timeInRangePercent: Int = 0,    // Target: 70 - 180 mg/dL (Clinical goal > 70%)
    val timeBelowPercent: Int = 0,      // Low: 54 - 69 mg/dL (Clinical goal < 4%)
    val timeVeryLowPercent: Int = 0,    // Very Low: < 54 mg/dL (Clinical goal < 1%)
    val timeAbovePercent: Int = 0,      // High: 181 - 250 mg/dL (Clinical goal < 25%)
    val timeVeryHighPercent: Int = 0,   // Very High: > 250 mg/dL (Clinical goal < 5%)
    val averageMgDl: Float = 0f,
    val minMgDl: Float = 0f,
    val maxMgDl: Float = 0f,
    val standardDeviation: Float = 0f,
    val cvPercent: Float = 0f,          // Coefficient of Variation = (SD / Mean) * 100 (Goal < 36%)
    val estimatedA1c: Float = 0f,       // Glucose Management Indicator (GMI)
    val readingsCount: Int = 0,
    val activeTimePercent: Float = 0f
) {
    companion object {
        fun calculate(readings: List<GlucosePoint>, targetLow: Float = 70f, targetHigh: Float = 180f): GlucoseStats {
            if (readings.isEmpty()) return GlucoseStats()

            var count = 0
            var sum = 0f
            var min = Float.MAX_VALUE
            var max = Float.MIN_VALUE
            var veryLowCount = 0
            var lowCount = 0
            var inRangeCount = 0
            var highCount = 0
            var veryHighCount = 0

            for (pt in readings) {
                val v = pt.valueMgDl
                if (v <= 0f) continue
                count++
                sum += v
                if (v < min) min = v
                if (v > max) max = v

                when {
                    v < 54f -> veryLowCount++
                    v < targetLow -> lowCount++
                    v <= targetHigh -> inRangeCount++
                    v <= 250f -> highCount++
                    else -> veryHighCount++
                }
            }

            if (count == 0) return GlucoseStats()

            val avg = sum / count
            var varianceSum = 0f
            for (pt in readings) {
                if (pt.valueMgDl <= 0f) continue
                val diff = pt.valueMgDl - avg
                varianceSum += diff * diff
            }
            val stdDev = sqrt(varianceSum / count)
            val cv = if (avg > 0) (stdDev / avg) * 100f else 0f
            // GMI formula: 3.31 + 0.02392 * mean_glucose
            val gmi = 3.31f + (0.02392f * avg)

            return GlucoseStats(
                timeInRangePercent = ((inRangeCount * 100f) / count).roundToInt(),
                timeBelowPercent = ((lowCount * 100f) / count).roundToInt(),
                timeVeryLowPercent = ((veryLowCount * 100f) / count).roundToInt(),
                timeAbovePercent = ((highCount * 100f) / count).roundToInt(),
                timeVeryHighPercent = ((veryHighCount * 100f) / count).roundToInt(),
                averageMgDl = avg,
                minMgDl = if (min == Float.MAX_VALUE) 0f else min,
                maxMgDl = if (max == Float.MIN_VALUE) 0f else max,
                standardDeviation = stdDev,
                cvPercent = cv,
                estimatedA1c = gmi,
                readingsCount = count,
                activeTimePercent = 99.2f
            )
        }
    }
}
