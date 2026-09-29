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
        fun fromValue(valueMgDl: Float, range: GlucoseRange = GlucoseRange.DEFAULT): GlucoseStatus =
            range.statusOf(valueMgDl)
    }
}
