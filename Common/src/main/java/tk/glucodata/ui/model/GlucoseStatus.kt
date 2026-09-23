package tk.glucodata.ui.model

import androidx.annotation.StringRes
import tk.glucodata.R

enum class GlucoseStatus(
    @get:StringRes val labelRes: Int,
    val symbol: String = ""
) {
    VERY_LOW(R.string.status_very_low, "↓↓"),
    LOW(R.string.status_low, "↓"),
    IN_RANGE(R.string.status_in_range, "✓"),
    HIGH(R.string.status_high, "↑"),
    VERY_HIGH(R.string.status_very_high, "↑↑");

    companion object {
        fun fromValue(valueMgDl: Float, targetLow: Float = 70f, targetHigh: Float = 180f): GlucoseStatus {
            return when {
                valueMgDl < 54f -> VERY_LOW
                valueMgDl < targetLow -> LOW
                valueMgDl > 250f -> VERY_HIGH
                valueMgDl > targetHigh -> HIGH
                else -> IN_RANGE
            }
        }
    }
}
