package tk.glucodata.ui.model

enum class AlarmSoundStream(val label: String, val id: Int) {
    ALARM("Alarm Stream", 0),
    NOTIFICATION("Notification Stream", 1),
    MEDIA("Media Stream", 2);

    companion object {
        fun fromId(id: Int): AlarmSoundStream = entries.find { it.id == id } ?: ALARM
    }
}

data class AlarmConfig(
    val lowAlarmEnabled: Boolean = true,
    val lowThreshold: Float = 70f,
    val lowSnoozeMinutes: Int = 15,
    val highAlarmEnabled: Boolean = true,
    val highThreshold: Float = 180f,
    val highSnoozeMinutes: Int = 30,
    val urgentLowEnabled: Boolean = true,
    val urgentLowThreshold: Float = 54f,
    val lossAlarmEnabled: Boolean = true,
    val lossWaitMinutes: Int = 20,
    val valueAvailableNotification: Boolean = false,
    val soundStream: AlarmSoundStream = AlarmSoundStream.ALARM
)

data class ExchangesConfig(
    val xdripBroadcast: Boolean = false,
    val glucodataBroadcast: Boolean = true,
    val librelinkBroadcast: Boolean = false,
    val everSenseBroadcast: Boolean = false,
    val healthConnect: Boolean = false,
    val libreViewEnabled: Boolean = false,
    val xdripWebServer: Boolean = false,
    val webServerPort: Int = 17580,
    val webServerSecret: String = ""
)

data class DisplayConfig(
    val floatingGlucose: Boolean = false,
    val statusBarNotification: Boolean = true,
    val systemUiFullscreen: Boolean = false,
    val invertColors: Boolean = false,
    val talkGlucose: Boolean = false,
    val showScans: Boolean = true,
    val showCalibratedScans: Boolean = false,
    val showStream: Boolean = true,
    val showCalibratedStream: Boolean = false,
    val showHistory: Boolean = true,
    val showCalibratedHistory: Boolean = false,
    val showAmounts: Boolean = true,
    val showMeals: Boolean = true,
    val minimalistUnits: Boolean = true,
    val deltaCalculation: DeltaCalculation = DeltaCalculation.ONE_MINUTE,
    val calibrationEnabled: Boolean = false,
    val calibratePastReadings: Boolean = false,
    val calibrateAllValues: Boolean = false
)

data class HardwareConfig(
    val nfcSound: Boolean = true,
    val globalScanStartsApp: Boolean = true,
    val googleScan: Boolean = false,
    val disableCameraKey: Boolean = false,
    val hasNfc: Boolean = true
)

data class WatchConfig(
    val wearOsEnabled: Boolean = true,
    val garminEnabled: Boolean = false,
    val watchdripEnabled: Boolean = false,
    val gadgetbridgeEnabled: Boolean = false,
    val separateAlerts: Boolean = false
)

data class MirrorConnection(
    val index: Int,
    val label: String,
    val ips: List<String>,
    val port: String,
    val isReceiver: Boolean,
    val sendAmounts: Boolean,
    val sendStream: Boolean,
    val sendScans: Boolean,
    val isActive: Boolean,
    val isPassive: Boolean,
    val isDeactivated: Boolean,
    val status: String
)
