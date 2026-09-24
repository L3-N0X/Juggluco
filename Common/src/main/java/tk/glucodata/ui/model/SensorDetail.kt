package tk.glucodata.ui.model

import androidx.annotation.StringRes
import tk.glucodata.R

enum class SensorStatus(@StringRes val labelRes: Int) {
    CONNECTED_STREAMING(R.string.sensor_status_connected_streaming),
    CONNECTED_IDLE(R.string.sensor_status_connected),
    CONNECTING(R.string.sensor_status_connecting),
    WARMING_UP(R.string.sensor_status_warming_up),
    DISCONNECTED(R.string.sensor_status_disconnected),
    EXPIRED(R.string.sensor_status_expired),
    ENDED(R.string.sensor_status_ended),
    HIDDEN(R.string.sensor_status_hidden);
}

enum class SignalQuality(@StringRes val labelRes: Int, val bars: Int) {
    EXCELLENT(R.string.signal_excellent, 4),
    GOOD(R.string.signal_good, 3),
    FAIR(R.string.signal_fair, 2),
    POOR(R.string.signal_poor, 1),
    LOST(R.string.signal_lost, 0);

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
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val dynamicDescription: String? = null,
    val isCompleted: Boolean,
    val timestamp: Long? = null,
    @StringRes val detailsRes: Int,
    val dynamicDetails: String? = null
)

data class SensorDetail(
    val id: String,
    val name: String,
    val sensorPtr: Long = 0L,
    val status: SensorStatus = SensorStatus.DISCONNECTED,
    val sensorGen: Int = 3,
    val sensorTypeName: String? = null,
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
    val rawDiagnosticText: String = "",
    val isMirrored: Boolean = false
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
                java.text.SimpleDateFormat(
                    android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "yEEEMMMdHm"),
                    java.util.Locale.getDefault()
                ).format(java.util.Date(endTime))
            } catch (_: Throwable) {
                ""
            }
        }

    val formattedStartTime: String
        get() {
            if (startTime <= 0L) return ""
            return try {
                java.text.SimpleDateFormat(
                    android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "yEEEMMMd"),
                    java.util.Locale.getDefault()
                ).format(java.util.Date(startTime))
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
                titleRes = R.string.connection_bluetooth_link,
                descriptionRes = if (isConnected) R.string.connection_connected_device else R.string.sensor_status_disconnected,
                isCompleted = isConnected,
                timestamp = if (lastConnectTime > 0) lastConnectTime else null,
                detailsRes = if (macAddress != null) R.string.sensor_mac_address else R.string.connection_address_pending,
                dynamicDetails = macAddress
            ),
            ConnectionStep(
                titleRes = R.string.connection_security_handshake,
                descriptionRes = R.string.connection_keys_exchanged,
                dynamicDescription = handshakeStatusStr.takeIf { it.isNotEmpty() },
                isCompleted = isConnected && handshakeStatusStr.contains("Fail", ignoreCase = true).not(),
                timestamp = if (lastHandshakeTime > 0) lastHandshakeTime else null,
                detailsRes = if (sensorGen == 3) R.string.connection_aes_session else R.string.connection_ble_handshake
            ),
            ConnectionStep(
                titleRes = R.string.connection_glucose_stream,
                descriptionRes = if (isStreaming) R.string.connection_realtime_active else R.string.connection_stream_idle,
                isCompleted = isStreaming,
                timestamp = if (lastReadingTime > 0) lastReadingTime else null,
                detailsRes = R.string.connection_readings_interval
            )
        )
}
