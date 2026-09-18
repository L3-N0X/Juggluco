package tk.glucodata.ui.model

enum class GlucoseStatus(val label: String) {
    VERY_LOW("Urgent Low"),
    LOW("Low"),
    IN_RANGE("In Range"),
    HIGH("High"),
    VERY_HIGH("Very High");

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
