package tk.glucodata.ui.data

import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.model.LogType
import tk.glucodata.ui.model.SensorDetail
import tk.glucodata.ui.model.SensorInfo
import tk.glucodata.ui.model.SensorState
import tk.glucodata.ui.model.SensorStatus
import tk.glucodata.ui.model.SignalQuality
import kotlin.math.sin

object MockDataGenerator {
    fun generateReadings(count: Int = 180, intervalMinutes: Int = 5): List<GlucosePoint> {
        val list = ArrayList<GlucosePoint>(count)
        val now = System.currentTimeMillis()
        val baseTime = now - (count * intervalMinutes * 60 * 1000L)

        // Generate a smooth realistic glucose wave with meals and variations
        var currentGlucose = 115f
        for (i in 0 until count) {
            val time = baseTime + (i * intervalMinutes * 60 * 1000L)
            val angle = (i.toFloat() / count.toFloat()) * (2f * Math.PI.toFloat() * 3.5f)
            val oscillation = sin(angle) * 35f
            val mealBump = when {
                i in 25..55 -> (sin((i - 25).toFloat() / 30f * Math.PI.toFloat()) * 55f)
                i in 90..120 -> (sin((i - 90).toFloat() / 30f * Math.PI.toFloat()) * 40f)
                i in 140..170 -> (sin((i - 140).toFloat() / 30f * Math.PI.toFloat()) * 30f)
                else -> 0f
            }

            val value = (currentGlucose + oscillation + mealBump).coerceIn(65f, 230f)
            val rate = if (i > 0) (value - list.last().valueMgDl) / intervalMinutes else 0f
            list.add(
                GlucosePoint(
                    timestamp = time,
                    valueMgDl = value,
                    rate = rate,
                    status = GlucoseStatus.fromValue(value)
                )
            )
        }
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
            batteryPercent = 96,
            connectionStatusStr = "Connected",
            lastConnectTime = now - (36 * 3600 * 1000L),
            lastDisconnectTime = 0L,
            handshakeStatusStr = "AES Session Established",
            lastHandshakeTime = now - (36 * 3600 * 1000L),
            rawDiagnosticText = """
                <b>Sensor Model:</b> FreeStyle Libre 3<br>
                <b>Serial Number:</b> 0M007894K2L<br>
                <b>Firmware:</b> 3.4.1<br>
                <b>Active Connection:</b> Bluetooth Low Energy (LE 2M PHY)<br>
                <b>Stream Status:</b> 1-minute streaming active<br>
                <b>Security:</b> Cryptographic mutual authentication verified
            """.trimIndent()
        )
    }

    fun generatePreviousSensors(): List<SensorDetail> {
        val now = System.currentTimeMillis()
        val p1Start = now - (18 * 24 * 3600 * 1000L)
        val p1End = p1Start + (14 * 24 * 3600 * 1000L)
        val p2Start = now - (34 * 24 * 3600 * 1000L)
        val p2End = p2Start + (14 * 24 * 3600 * 1000L)

        return listOf(
            SensorDetail(
                id = "0M006432J1R",
                name = "FreeStyle Libre 3",
                sensorPtr = 998L,
                status = SensorStatus.EXPIRED,
                sensorGen = 3,
                sensorTypeName = "FreeStyle Libre 3",
                macAddress = "E1:12:54:9A:88:02",
                rssi = null,
                signalQuality = SignalQuality.LOST,
                startTime = p1Start,
                endTime = p1End,
                lastReadingTime = p1End,
                isConnected = false,
                isStreaming = false
            ),
            SensorDetail(
                id = "0M005118G9X",
                name = "FreeStyle Libre 3",
                sensorPtr = 985L,
                status = SensorStatus.ENDED,
                sensorGen = 3,
                sensorTypeName = "FreeStyle Libre 3",
                macAddress = "C8:33:17:F0:4B:11",
                rssi = null,
                signalQuality = SignalQuality.LOST,
                startTime = p2Start,
                endTime = p2End,
                lastReadingTime = p2End,
                isConnected = false,
                isStreaming = false
            )
        )
    }

    fun generateLogs(): List<LogRecord> {
        val now = System.currentTimeMillis()
        return listOf(
            LogRecord(
                timestamp = now - (45 * 60 * 1000L),
                type = LogType.RAPID_INSULIN,
                value = 4.5f,
                note = "Pre-meal bolus"
            ),
            LogRecord(
                timestamp = now - (50 * 60 * 1000L),
                type = LogType.MEAL,
                value = 48f,
                note = "Oatmeal & Berries"
            ),
            LogRecord(
                timestamp = now - (4 * 3600 * 1000L),
                type = LogType.RAPID_INSULIN,
                value = 6.0f,
                note = "Lunch"
            ),
            LogRecord(
                timestamp = now - (4 * 3600 * 1000L + 10 * 60 * 1000L),
                type = LogType.CARBS,
                value = 65f,
                note = "Sandwich & Apple"
            ),
            LogRecord(
                timestamp = now - (8 * 3600 * 1000L),
                type = LogType.BLOOD_GLUCOSE,
                value = 112f,
                note = "Finger prick check"
            ),
            LogRecord(
                timestamp = now - (14 * 3600 * 1000L),
                type = LogType.BASAL_INSULIN,
                value = 18f,
                note = "Lantus bedtime"
            )
        )
    }
}
