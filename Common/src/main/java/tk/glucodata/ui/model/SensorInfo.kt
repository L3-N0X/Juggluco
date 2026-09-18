package tk.glucodata.ui.model

enum class SensorState(val label: String) {
    ACTIVE("Active"),
    WARMING_UP("Warming Up"),
    EXPIRED("Expired"),
    ENDED("Ended"),
    DISCONNECTED("Disconnected");
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
