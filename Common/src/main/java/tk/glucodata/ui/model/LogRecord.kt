package tk.glucodata.ui.model

import java.util.concurrent.atomic.AtomicLong

enum class LogType(val label: String, val unitLabel: String) {
    RAPID_INSULIN("Rapid Insulin", "U"),
    BASAL_INSULIN("Basal Insulin", "U"),
    CARBS("Carbohydrates", "g"),
    BLOOD_GLUCOSE("Finger Prick", ""),
    MEAL("Meal", "g"),
    NOTE("Note", "");
}

private val idGenerator = AtomicLong(System.currentTimeMillis())

data class LogRecord(
    val id: Long = idGenerator.incrementAndGet(),
    val timestamp: Long,
    val type: LogType,
    val value: Float,
    val note: String = ""
) {
    fun formattedValue(unit: GlucoseUnit): String {
        return when (type) {
            LogType.RAPID_INSULIN, LogType.BASAL_INSULIN -> String.format(java.util.Locale.US, "%.1f %s", value, type.unitLabel)
            LogType.CARBS, LogType.MEAL -> String.format(java.util.Locale.US, "%.0f %s", value, type.unitLabel)
            LogType.BLOOD_GLUCOSE -> "${unit.format(value)} ${unit.label}"
            LogType.NOTE -> note
        }
    }
}
