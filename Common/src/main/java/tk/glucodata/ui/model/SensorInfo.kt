package tk.glucodata.ui.model

import androidx.annotation.StringRes
import tk.glucodata.R

enum class SensorState(@StringRes val labelRes: Int) {
    ACTIVE(R.string.loc_state_active),
    WARMING_UP(R.string.loc_model_warming_up),
    EXPIRED(R.string.loc_model_expired),
    ENDED(R.string.loc_model_ended),
    DISCONNECTED(R.string.sensor_status_disconnected);
}

data class SensorInfo(
    val id: String,
    val name: String,
    val state: SensorState = SensorState.ACTIVE,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val lastReadingTime: Long = 0L,
    val sensorType: String = "FreeStyle Libre",
    val isStreaming: Boolean = true,
    val isConnected: Boolean = true,
    val batteryPercent: Int? = null
) {
    val daysRemaining: Float
        get() {
            if (endTime <= 0L) return 14f
            val remainingMs = endTime - System.currentTimeMillis()
            return if (remainingMs <= 0) 0f else (remainingMs / (1000f * 60f * 60f * 24f))
        }

    val progressPercent: Float
        get() {
            if (endTime <= startTime || startTime <= 0L) return 0f
            val total = (endTime - startTime).toFloat()
            val elapsed = (System.currentTimeMillis() - startTime).toFloat()
            return (elapsed / total).coerceIn(0f, 1f)
        }
}
