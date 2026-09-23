package tk.glucodata.ui.model

import android.content.Context
import androidx.annotation.StringRes
import tk.glucodata.R
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogType(@StringRes val labelRes: Int, val unitLabel: String) {
    RAPID_INSULIN(R.string.log_type_rapid_insulin, "U"),
    BASAL_INSULIN(R.string.log_type_basal_insulin, "U"),
    CARBS(R.string.log_type_carbs, "g"),
    BLOOD_GLUCOSE(R.string.log_type_finger_prick, ""),
    MEAL(R.string.log_type_meal, "g"),
    NOTE(R.string.log_type_note, "");
}

private val idGenerator = AtomicLong(System.currentTimeMillis())

data class LogRecord(
    val id: Long = idGenerator.incrementAndGet(),
    val timestamp: Long,
    val type: LogType,
    val value: Float,
    val note: String = ""
) {
    fun formattedValue(context: Context, unit: GlucoseUnit): String {
        val locale = Locale.getDefault()
        return when (type) {
            LogType.RAPID_INSULIN, LogType.BASAL_INSULIN -> context.getString(
                R.string.log_value_insulin,
                String.format(locale, "%.1f", value)
            )
            LogType.CARBS, LogType.MEAL -> context.getString(
                R.string.log_value_carbs,
                String.format(locale, "%.0f", value)
            )
            LogType.BLOOD_GLUCOSE -> context.getString(
                R.string.log_glucose_value,
                unit.format(value),
                context.getString(unit.labelRes)
            )
            LogType.NOTE -> note
        }
    }
}
