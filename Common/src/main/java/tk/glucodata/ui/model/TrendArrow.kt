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
        /**
         * Maps a rate of change in mg/dL per minute onto an arrow.
         *
         * The thresholds are the Dexcom/xDrip ones, deliberately: this is the same table the native
         * `getdeltaindex` uses for the xDrip trend name and the same one GlucoDataHandler applies
         * to the rate we broadcast, so an arrow shown here matches what every other app derives
         * from the very same number. The rising side is inclusive of its threshold and the falling
         * side exclusive, because that asymmetry is what the reference implementations do - keeping
         * it means a value sitting exactly on a boundary (e.g. -1.0) is classified identically here
         * and there.
         *
         * Note this is a rate, not a delta: it is the sensor's own mg/dL/min slope and is
         * independent of the 1 or 5 minute delta display setting.
         */
        fun fromRate(rate: Float): TrendArrow {
            if (rate.isNaN() || rate.isInfinite()) return UNKNOWN
            return when {
                rate >= 3.0f -> RAPIDLY_RISING
                rate >= 2.0f -> RISING
                rate >= 1.0f -> SLIGHTLY_RISING
                rate > -1.0f -> STABLE
                rate > -2.0f -> SLIGHTLY_FALLING
                rate > -3.0f -> FALLING
                else -> RAPIDLY_FALLING
            }
        }
    }
}
