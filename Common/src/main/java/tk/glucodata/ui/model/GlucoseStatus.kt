package tk.glucodata.ui.model

import androidx.annotation.StringRes
import tk.glucodata.R

enum class GlucoseStatus(
    val label: String,
    @StringRes val labelRes: Int
) {
    VERY_LOW("Urgent Low", R.string.status_very_low),
    LOW("Low", R.string.status_low),
    IN_RANGE("In Range", R.string.status_in_range),
    HIGH("High", R.string.status_high),
    VERY_HIGH("Very High", R.string.status_very_high);

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
