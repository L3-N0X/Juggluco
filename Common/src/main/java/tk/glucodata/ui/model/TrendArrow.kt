package tk.glucodata.ui.model

import androidx.annotation.StringRes
import tk.glucodata.R

enum class TrendArrow(val symbol: String, val angleDegrees: Float, @StringRes val labelRes: Int) {
    RAPIDLY_RISING("↑↑", -90f, R.string.trend_rising_rapidly),
    RISING("↑", -60f, R.string.trend_rising),
    SLIGHTLY_RISING("↗", -30f, R.string.trend_rising_slowly),
    STABLE("→", 0f, R.string.trend_steady),
    SLIGHTLY_FALLING("↘", 30f, R.string.trend_falling_slowly),
    FALLING("↓", 60f, R.string.trend_falling),
    RAPIDLY_FALLING("↓↓", 90f, R.string.trend_falling_rapidly),
    UNKNOWN("—", 0f, R.string.trend_unknown);

    companion object {
        fun fromRate(rate: Float): TrendArrow {
            if (rate.isNaN() || rate.isInfinite()) return UNKNOWN
            return when {
                rate >= 2.0f -> RAPIDLY_RISING
                rate >= 1.0f -> RISING
                rate >= 0.5f -> SLIGHTLY_RISING
                rate <= -2.0f -> RAPIDLY_FALLING
                rate <= -1.0f -> FALLING
                rate <= -0.5f -> SLIGHTLY_FALLING
                else -> STABLE
            }
        }
    }
}
