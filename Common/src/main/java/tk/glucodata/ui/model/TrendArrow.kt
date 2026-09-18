package tk.glucodata.ui.model

enum class TrendArrow(val symbol: String, val angleDegrees: Float, val label: String) {
    RAPIDLY_RISING("↑↑", -90f, "Rising rapidly"),
    RISING("↑", -60f, "Rising"),
    SLIGHTLY_RISING("↗", -30f, "Rising slowly"),
    STABLE("→", 0f, "Steady"),
    SLIGHTLY_FALLING("↘", 30f, "Falling slowly"),
    FALLING("↓", 60f, "Falling"),
    RAPIDLY_FALLING("↓↓", 90f, "Falling rapidly"),
    UNKNOWN("—", 0f, "No trend available");

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
