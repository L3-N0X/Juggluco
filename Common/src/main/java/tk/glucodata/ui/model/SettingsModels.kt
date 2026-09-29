package tk.glucodata.ui.model

import androidx.annotation.StringRes
import tk.glucodata.R

enum class AlarmSoundStream(@StringRes val labelRes: Int, val id: Int) {
    ALARM(R.string.loc_model_alarm_stream, 0),
    NOTIFICATION(R.string.loc_model_notification_stream, 1),
    MEDIA(R.string.loc_model_media_stream, 2);

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
    val urgentLowSnoozeMinutes: Int = 15,
    val veryHighEnabled: Boolean = false,
    val veryHighThreshold: Float = 250f,
    val veryHighSnoozeMinutes: Int = 30,
    val preLowEnabled: Boolean = false,
    val preLowThreshold: Float = 80f,
    val preLowSnoozeMinutes: Int = 15,
    val preHighEnabled: Boolean = false,
    val preHighThreshold: Float = 170f,
    val preHighSnoozeMinutes: Int = 15,
    val lossAlarmEnabled: Boolean = true,
    val lossWaitMinutes: Int = 20,
    val valueAvailableNotification: Boolean = false,
    val soundStream: AlarmSoundStream = AlarmSoundStream.ALARM
) {
    /** Number of sound-producing alarms currently enabled (excludes the value chime). */
    fun activeAlarmCount(): Int = listOf(
        lowAlarmEnabled,
        highAlarmEnabled,
        urgentLowEnabled,
        veryHighEnabled,
        preLowEnabled,
        preHighEnabled,
        lossAlarmEnabled
    ).count { it }
}

/**
 * Per-alarm sound behavior (legacy RingTones dialog equivalent, without the
 * ringtone picker). Kinds follow Notify: 0 low, 1 high, 2 value chime,
 * 4 signal loss, 5 urgent low, 6 very high, 7 pre low, 8 pre high.
 *
 * Note: on Wear OS the do-not-disturb override is always active
 * (Notify.mksound forces it for wearables), so there is no disturb flag here.
 */
data class AlarmBehavior(
    val kind: Int,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val durationSecs: Int = 60
)

data class BroadcastReceiverApp(
    val packageName: String,
    val label: String,
    val installed: Boolean = true
)

data class ExchangesConfig(
    val xdripBroadcast: Boolean = false,
    val xdripReceiverPackages: List<String> = emptyList(),
    val glucodataBroadcast: Boolean = true,
    val glucodataReceiverPackages: List<String> = emptyList(),
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
    val bloodLabelIndex: Int = -1,
    val calibratePastReadings: Boolean = false,
    val calibrateAllValues: Boolean = false,
    val use24Hour: Boolean = true
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
    val separateAlerts: Boolean = false,
    val notifyWatch: Boolean = false
)

data class WearWatchDevice(
    val id: String,
    val displayName: String,
    val isDirectSensor: Boolean,
    val isEnterNumsOnWatch: Boolean,
    val isGalaxy: Boolean,
    val mirrorIndex: Int = -1,
    val mirrorStatus: String = "",
    val mirrorIps: List<String> = emptyList(),
    val isConnected: Boolean = false,
    /** Mirror carrier (BleMirror.TRANSPORT_*), or -1 while no mirror row exists yet. */
    val transport: Int = -1
)

data class WearDiagnosticInfo(
    val phoneAppId: String = "",
    val phoneVersion: String = "",
    val mirrorPort: String = "",
    val isReceiverServiceEnabled: Boolean = false,
    val reachableWearNodesCount: Int = 0
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

data class MirrorHostEditState(
    val index: Int,
    val label: String,
    val hasLabel: Boolean,
    val ips: List<String>,
    val port: String,
    val receiveFrom: Int,
    val activeReceive: Int,
    val sendAmounts: Boolean,
    val sendStream: Boolean,
    val sendScans: Boolean,
    val sendPassive: Boolean,
    val restore: Boolean,
    val startTime: Long,
    val detect: Boolean,
    val testIp: Boolean,
    val hasHostname: Boolean,
    val iceLabel: String,
    val side: Boolean,
    val transport: Int,
    val bleClient: Boolean,
    val bleReverse: Boolean,
    val bleUnproven: Boolean,
    val wearOs: Boolean,
    val deactivated: Boolean,
    val hasPassword: Boolean
) {
    val isReceiver: Boolean get() = (receiveFrom and 2) != 0
    val isIce: Boolean get() = iceLabel.isNotEmpty()
}
