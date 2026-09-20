package tk.glucodata.ui.model

enum class SensorStatus(val label: String) {
    CONNECTED_STREAMING("Connected & Streaming"),
    CONNECTED_IDLE("Connected"),
    CONNECTING("Connecting..."),
    WARMING_UP("Warming Up"),
    DISCONNECTED("Disconnected"),
    EXPIRED("Expired"),
    ENDED("Ended"),
    HIDDEN("Hidden");
}

enum class SignalQuality(val label: String, val bars: Int) {
    EXCELLENT("Excellent", 4),
    GOOD("Good", 3),
    FAIR("Fair", 2),
    POOR("Poor", 1),
    LOST("No Signal", 0);

    companion object {
        fun fromRssi(rssi: Int?): SignalQuality {
            if (rssi == null || rssi >= 999 || rssi == 0) return LOST
            return when {
                rssi >= -65 -> EXCELLENT
                rssi >= -75 -> GOOD
                rssi >= -85 -> FAIR
                rssi >= -95 -> POOR
                else -> LOST
            }
        }
    }
}

data class ConnectionStep(
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val timestamp: Long? = null,
    val details: String? = null
)

data class SensorDetail(
    val id: String,
    val name: String,
    val sensorPtr: Long = 0L,
    val status: SensorStatus = SensorStatus.DISCONNECTED,
    val sensorGen: Int = 3,
    val sensorTypeName: String = "CGM Sensor",
    val macAddress: String? = null,
    val rssi: Int? = null,
    val signalQuality: SignalQuality = SignalQuality.LOST,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val lastReadingTime: Long = 0L,
    val warmupMinutes: Int = 60,
    val minWarmupMinutes: Int = 60,
    val isConnected: Boolean = false,
    val isStreaming: Boolean = false,
    val isHidden: Boolean = false,
    val hasCalibration: Boolean = false,
    val batteryPercent: Int? = null,
    val connectionStatusStr: String = "Disconnected",
    val lastConnectTime: Long = 0L,
    val lastDisconnectTime: Long = 0L,
    val handshakeStatusStr: String = "",
    val lastHandshakeTime: Long = 0L,
    val rawDiagnosticText: String = ""
) {
    val daysRemaining: Float
        get() {
            if (endTime <= 0L) return 14f
            val remainingMs = endTime - System.currentTimeMillis()
            return if (remainingMs <= 0) 0f else (remainingMs / (1000f * 60f * 60f * 24f))
        }

    val progressPercent: Float
        get() {
            val effectiveStart = if (startTime > 0L) startTime else if (endTime > 0L) endTime - 14 * 24 * 3600 * 1000L else 0L
            if (endTime <= effectiveStart || effectiveStart <= 0L) return 0f
            val total = (endTime - effectiveStart).toFloat()
            val elapsed = (System.currentTimeMillis() - effectiveStart).toFloat()
            return (elapsed / total).coerceIn(0f, 1f)
        }

    val formattedExpectedEnd: String
        get() {
            if (endTime <= 0L) return ""
            return try {
                java.text.SimpleDateFormat("EEE, MMM d, yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(endTime))
            } catch (_: Throwable) {
                ""
            }
        }

    val formattedStartTime: String
        get() {
            if (startTime <= 0L) return ""
            return try {
                java.text.SimpleDateFormat("EEE, MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(startTime))
            } catch (_: Throwable) {
                ""
            }
        }

    val warmupRemainingMinutes: Int
        get() {
            if (startTime <= 0L) return 0
            val elapsedMinutes = ((System.currentTimeMillis() - startTime) / (60 * 1000L)).toInt()
            val remaining = warmupMinutes - elapsedMinutes
            return remaining.coerceAtLeast(0)
        }

    val isCurrentlyWarmingUp: Boolean
        get() = warmupRemainingMinutes > 0 && status != SensorStatus.EXPIRED && status != SensorStatus.ENDED

    val connectionPipeline: List<ConnectionStep>
        get() = listOf(
            ConnectionStep(
                title = "Bluetooth Link",
                description = if (isConnected) "Connected to device" else "Disconnected",
                isCompleted = isConnected,
                timestamp = if (lastConnectTime > 0) lastConnectTime else null,
                details = macAddress ?: "Address pending"
            ),
            ConnectionStep(
                title = "Security Handshake",
                description = if (handshakeStatusStr.isNotEmpty()) handshakeStatusStr else "Keys exchanged",
                isCompleted = isConnected && handshakeStatusStr.contains("Fail", ignoreCase = true).not(),
                timestamp = if (lastHandshakeTime > 0) lastHandshakeTime else null,
                details = if (sensorGen == 3) "AES-128 Session Established" else "BLE Security Handshake"
            ),
            ConnectionStep(
                title = "Glucose Data Stream",
                description = if (isStreaming) "Real-time stream active" else "Stream idle",
                isCompleted = isStreaming,
                timestamp = if (lastReadingTime > 0) lastReadingTime else null,
                details = "Readings delivered every 60s"
            )
        )
}
