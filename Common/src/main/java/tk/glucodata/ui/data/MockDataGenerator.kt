package tk.glucodata.ui.data

import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.SensorDetail
import tk.glucodata.ui.model.SensorInfo
import tk.glucodata.ui.model.SensorState
import tk.glucodata.ui.model.SensorStatus
import tk.glucodata.ui.model.SignalQuality
import java.util.Calendar
import java.util.Random
import kotlin.math.sin

object MockDataGenerator {

    /**
     * Generates a comprehensive multi-day dataset of glucose readings including:
     * - Continuous stream data (5-minute intervals)
     * - Calibrated stream data (slight physiological calibration delta)
     * - Periodic NFC scan data (diamond markers)
     * - 15-minute sensor history data (dashed line)
     */
    fun generateReadings(days: Int = 7, targetLow: Float = 70f, targetHigh: Float = 180f): List<GlucosePoint> {
        val list = ArrayList<GlucosePoint>(days * 288 * 3)
        val now = System.currentTimeMillis()
        val totalMinutes = days * 24 * 60
        val baseStartTime = now - (totalMinutes * 60 * 1000L)
        val random = Random(42) // Deterministic seed for reproducible testing

        var glucose = 110f
        var prevValue = glucose

        val cal = Calendar.getInstance()

        // 1. Generate 5-minute continuous stream points and calibrated points
        var minute = 0
        while (minute <= totalMinutes) {
            val timestamp = baseStartTime + (minute * 60 * 1000L)
            cal.timeInMillis = timestamp
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minOfHour = cal.get(Calendar.MINUTE)

            // Circadian rhythm baseline: slightly lower overnight, dawn phenomenon rise at 6-8 AM
            val circadian = when (hour) {
                in 0..5 -> -15f + sin(hour / 6f * Math.PI.toFloat()) * 5f
                in 6..8 -> 10f // Dawn phenomenon
                in 9..11 -> 0f
                in 12..14 -> 15f // Post-lunch baseline
                in 15..17 -> -5f
                in 18..21 -> 20f // Dinner baseline
                else -> 0f
            }

            // Post-prandial meal excursions
            val mealBump = when {
                hour == 8 && minOfHour in 15..55 -> 45f * sin((minOfHour - 15) / 40f * Math.PI.toFloat()) // Breakfast
                hour == 9 && minOfHour in 0..45 -> 30f * sin(minOfHour / 45f * Math.PI.toFloat())
                hour == 12 && minOfHour in 30..55 -> 55f * sin((minOfHour - 30) / 25f * Math.PI.toFloat()) // Lunch
                hour == 13 && minOfHour in 0..55 -> 40f * sin(minOfHour / 55f * Math.PI.toFloat())
                hour == 19 && minOfHour in 0..55 -> 65f * sin(minOfHour / 55f * Math.PI.toFloat()) // Dinner
                hour == 20 && minOfHour in 0..55 -> 45f * sin(minOfHour / 55f * Math.PI.toFloat())
                // Occasional nighttime dip or excursion
                hour == 3 && minOfHour in 10..40 -> -20f * sin((minOfHour - 10) / 30f * Math.PI.toFloat())
                else -> 0f
            }

            // Micro-variations
            val jitter = (random.nextFloat() - 0.5f) * 4f
            val rawValue = (112f + circadian + mealBump + jitter).coerceIn(58f, 265f)
            val rate = (rawValue - prevValue) / 5f
            prevValue = rawValue

            // Add standard raw stream point
            list.add(
                GlucosePoint(
                    timestamp = timestamp,
                    valueMgDl = rawValue,
                    rate = rate,
                    isScan = false,
                    isHistory = false,
                    isCalibrated = false,
                    status = GlucoseStatus.fromValue(rawValue, targetLow, targetHigh)
                )
            )

            // Add calibrated stream point (slight offset reflecting calibrated blood glucose alignment)
            val caliOffset = 4.5f * sin(minute / 300f) + 3f
            val caliValue = (rawValue + caliOffset).coerceIn(55f, 270f)
            list.add(
                GlucosePoint(
                    timestamp = timestamp,
                    valueMgDl = caliValue,
                    rate = rate,
                    isScan = false,
                    isHistory = false,
                    isCalibrated = true,
                    status = GlucoseStatus.fromValue(caliValue, targetLow, targetHigh)
                )
            )

            // Add 15-minute sensor history point
            if (minute % 15 == 0) {
                list.add(
                    GlucosePoint(
                        timestamp = timestamp,
                        valueMgDl = rawValue,
                        rate = rate,
                        isScan = false,
                        isHistory = true,
                        isCalibrated = false,
                        status = GlucoseStatus.fromValue(rawValue, targetLow, targetHigh)
                    )
                )
            }

            // Periodic NFC scans (e.g. 4-5 times a day at morning, lunch, afternoon, dinner, night)
            if ((hour == 8 && minOfHour == 5) ||
                (hour == 12 && minOfHour == 25) ||
                (hour == 16 && minOfHour == 40) ||
                (hour == 19 && minOfHour == 10) ||
                (hour == 22 && minOfHour == 30)
            ) {
                val scanVal = (rawValue + (random.nextFloat() - 0.5f) * 3f).coerceIn(55f, 270f)
                list.add(
                    GlucosePoint(
                        timestamp = timestamp + 15_000L, // 15s offset
                        valueMgDl = scanVal,
                        rate = rate,
                        isScan = true,
                        isHistory = false,
                        isCalibrated = false,
                        status = GlucoseStatus.fromValue(scanVal, targetLow, targetHigh)
                    )
                )
            }

            minute += 5
        }

        list.sortBy { it.timestamp }
        return list
    }

    fun generateSensor(): SensorInfo {
        val now = System.currentTimeMillis()
        val start = now - (3 * 24 * 3600 * 1000L) // 3 days ago
        val end = start + (14 * 24 * 3600 * 1000L) // 14-day wear
        return SensorInfo(
            id = "0M007894K2L",
            name = "FreeStyle Libre 3",
            state = SensorState.ACTIVE,
            startTime = start,
            endTime = end,
            lastReadingTime = now - (2 * 60 * 1000L),
            sensorType = "Libre 3 (BLE Streaming)",
            isStreaming = true,
            isConnected = true,
            batteryPercent = 94
        )
    }

    fun generateSensorDetail(): SensorDetail {
        val now = System.currentTimeMillis()
        val start = now - (3 * 24 * 3600 * 1000L) // 3 days ago
        val end = start + (14 * 24 * 3600 * 1000L)
        return SensorDetail(
            id = "0M007894K2L",
            name = "FreeStyle Libre 3",
            sensorPtr = 1001L,
            status = SensorStatus.CONNECTED_STREAMING,
            sensorGen = 3,
            sensorTypeName = "FreeStyle Libre 3",
            macAddress = "D4:36:39:B2:A1:8F",
            rssi = -64,
            signalQuality = SignalQuality.EXCELLENT,
            startTime = start,
            endTime = end,
            lastReadingTime = now - (45 * 1000L),
            warmupMinutes = 60,
            minWarmupMinutes = 60,
            isConnected = true,
            isStreaming = true,
            isHidden = false,
            hasCalibration = false,
            batteryPercent = null,
            connectionStatusStr = "Connected",
            lastConnectTime = now - (36 * 3600 * 1000L),
            lastDisconnectTime = 0L,
            handshakeStatusStr = "Authenticated",
            lastHandshakeTime = now - (36 * 3600 * 1000L),
            rawDiagnosticText = "<h1>&nbsp;&nbsp;0M007894K2L</h1><br><br>Sensor started:\t\t${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(start))}<br><br>Last stream:\t\t${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(now - 45000))}<br><br>Expected end:\t\t${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(end))}"
        )
    }
}
