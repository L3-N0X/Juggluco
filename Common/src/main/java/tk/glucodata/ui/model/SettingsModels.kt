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

data class UploaderConfig(
    val url: String = "",
    val active: Boolean = false,
    val v3: Boolean = false,
    val postTreatments: Boolean = false,
    val canSendTreatments: Boolean = false
)

data class UploaderStatus(
    val text: String = "",
    val timeMillis: Long = 0L
)

data class TreatmentMapping(
    val index: Int,
    val label: String,
    val kind: Int,
    val weight: Float
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
    val showAlertLines: Boolean = false,
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
    val status: String,
    val transport: Int = 0,
    val isIce: Boolean = false
)

/** How far back a sender connection pushes data on its very first sync. */
enum class MirrorDataStart(@StringRes val labelRes: Int) {
    ALL(R.string.loc_mirror_data_start_all),
    FROM_NOW(R.string.loc_mirror_data_start_now),
    SCREEN_POSITION(R.string.loc_mirror_data_start_screen)
}

/**
 * Everything a mirror connection can be configured with. The editor edits one
 * of these and hands it to the repository in one go, so no stored field can be
 * lost because a screen forgot about it.
 */
data class MirrorConnectionDraft(
    val transport: Int = tk.glucodata.BleMirror.TRANSPORT_AUTOMATIC,
    val label: String = "",
    val addresses: List<String> = emptyList(),
    val port: String = "",
    val detectIp: Boolean = false,
    val testIp: Boolean = true,
    val useHostname: Boolean = false,
    val activeOnly: Boolean = false,
    val passiveOnly: Boolean = false,
    val ice: Boolean = false,
    val iceLabel: String = "",
    val iceSide: Boolean = false,
    val receiveFrom: Boolean = true,
    val sendAmounts: Boolean = true,
    val sendStream: Boolean = true,
    val sendScans: Boolean = true,
    val restore: Boolean = false,
    val dataStart: MirrorDataStart = MirrorDataStart.ALL,
    val startTime: Long = 0L,
    val usePassword: Boolean = false,
    val password: String = "",
    val side: Boolean = false,
    val bleReverse: Boolean = false,
    val bleClient: Boolean = true
) {
    val isNetworkTransport: Boolean
        get() = transport == tk.glucodata.BleMirror.TRANSPORT_AUTOMATIC ||
                transport == tk.glucodata.BleMirror.TRANSPORT_TCP

    /** Bluetooth and Messages identify a peer by label, so it is mandatory there. */
    val needsLabel: Boolean
        get() = transport == tk.glucodata.BleMirror.TRANSPORT_BLUETOOTH ||
                transport == tk.glucodata.BleMirror.TRANSPORT_MESSAGES

    val sendsAnything: Boolean get() = sendAmounts || sendStream || sendScans

    val hasAddresses: Boolean get() = addresses.any { it.isNotBlank() }
}

/** Why saving or importing a mirror connection did not work. */
enum class MirrorSaveError(@StringRes val messageRes: Int) {
    NONE(R.string.loc_connection_saved),
    FAILED(R.string.loc_failed_save_connection),
    NOT_AVAILABLE(R.string.loc_failed_load_connection),
    INVALID_PORT(R.string.loc_invalid_mirror_port),
    PARSE_ADDRESS(R.string.parseip),
    TOO_MANY_ADDRESSES(R.string.toomanyhosts),
    TOO_MANY_SENDERS(R.string.senthosts),
    HOSTNAME_TOO_LONG(R.string.loc_mirror_hostname_too_long),
    DATABASE_BUSY(R.string.loc_mirror_database_busy),
    INVALID_TRANSPORT(R.string.loc_mirror_invalid_transport),
    LABEL_IN_USE(R.string.loc_mirror_label_in_use),
    LABEL_TOO_LONG(R.string.loc_mirror_label_too_long),
    PASSWORD_TOO_LONG(R.string.loc_mirror_password_too_long),
    NO_ADDRESS(R.string.specifyip),
    LABEL_REQUIRED(R.string.transport_needs_label),
    PASSWORD_REQUIRED(R.string.transport_needs_password),
    ICE_LABEL_TOO_SHORT(R.string.ICElabeltooshort),
    ICE_LABEL_TOO_LONG(R.string.loc_mirror_ice_label_too_long),
    NOTHING_SELECTED(R.string.specifyreceiveordata),
    ALL_DATA_SENT(R.string.allsentnoreceive);

    companion object {
        /** Maps the native `changehost_*` result codes onto user facing errors. */
        fun fromNative(code: Int): MirrorSaveError = when (code) {
            -1 -> INVALID_PORT
            -2 -> PARSE_ADDRESS
            -3 -> TOO_MANY_ADDRESSES
            -4 -> TOO_MANY_SENDERS
            -5 -> HOSTNAME_TOO_LONG
            -6 -> DATABASE_BUSY
            -7 -> INVALID_TRANSPORT
            -9 -> LABEL_IN_USE
            -10 -> LABEL_TOO_LONG
            -11 -> PASSWORD_TOO_LONG
            -12 -> NO_ADDRESS
            else -> FAILED
        }
    }
}

/**
 * The data this device already holds that a receiving connection would send again
 * from the beginning. The native side asks before that happens, both when a
 * connection code is imported and when a receiving connection code is handed out.
 */
data class MirrorPresentData(
    val amounts: Boolean = false,
    val scans: Boolean = false,
    val stream: Boolean = false
) {
    val any: Boolean get() = amounts || scans || stream

    companion object {
        val NONE = MirrorPresentData()
    }
}

/** A payload read from a connection code, ready to become a real connection. */data class MirrorImportPreview(
    val draft: MirrorConnectionDraft,
    val present: MirrorPresentData = MirrorPresentData.NONE
) {
    val overwritesData: Boolean get() = present.any
}

/** The four one-tap connections a device can hand out as a connection code. */
enum class MirrorQuickCode(@StringRes val titleRes: Int, @StringRes val subtitleRes: Int) {
    LOCAL_SENDER(R.string.loc_mirror_code_local_sender, R.string.loc_mirror_code_local_sender_desc),
    LOCAL_RECEIVER(R.string.loc_mirror_code_local_receiver, R.string.loc_mirror_code_local_receiver_desc),
    INTERNET_SENDER(R.string.loc_mirror_code_internet_sender, R.string.loc_mirror_code_internet_sender_desc),
    INTERNET_RECEIVER(R.string.loc_mirror_code_internet_receiver, R.string.loc_mirror_code_internet_receiver_desc);

    /** A receiving connection replaces the data this device already holds. */
    val isReceiver: Boolean get() = this == LOCAL_RECEIVER || this == INTERNET_RECEIVER
}

/**
 * Outcome of writing a mirror connection. [code] carries the connection code of a
 * freshly created connection, [blocker] explains why a carrier cannot start yet and
 * [needsBluetoothPermission] tells the caller to ask for the nearby devices
 * permission before the mirror tries to use Bluetooth.
 */
data class MirrorSaveResult(
    val index: Int = -1,
    val error: MirrorSaveError = MirrorSaveError.FAILED,
    val code: String? = null,
    val partialData: Boolean = false,
    val blocker: String? = null,
    val needsBluetoothPermission: Boolean = false
) {
    val ok: Boolean get() = index >= 0
}

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

/**
 * The LibreLink configuration region a LibreView account lives in. Abbott ships one
 * configuration file per region, so the region decides which upload endpoints are used.
 * The order is the native `librecountry` order, which the native side also uses to
 * derive the glucose unit (even index mmol/L, odd index mg/dL).
 */
enum class LibreRegion(val nativeIndex: Int, @StringRes val labelRes: Int) {
    UNITED_KINGDOM(0, R.string.loc_libreview_region_uk),
    FRANCE(1, R.string.loc_libreview_region_fr),
    NETHERLANDS(2, R.string.loc_libreview_region_nl),
    POLAND(3, R.string.loc_libreview_region_pl),
    RUSSIA(4, R.string.loc_libreview_region_ru);

    companion object {
        fun fromNative(index: Int): LibreRegion = entries.find { it.nativeIndex == index } ?: UNITED_KINGDOM
    }
}

/**
 * What LibreView should do with the numbers of one logbook label. The values are the
 * native `librenums[].kind` codes, where 0 means "not mapped yet" and every other value
 * makes the native export write the entry as that kind of record.
 */
enum class LibreTreatmentKind(val nativeValue: Int, @StringRes val labelRes: Int) {
    UNSET(0, R.string.loc_libreview_kind_unset),
    RAPID_INSULIN(1, R.string.rapidinsulin),
    LONG_INSULIN(2, R.string.longinsulin),
    CARBS(3, R.string.carbo),
    NOTE(4, R.string.comments);

    val isMapped: Boolean get() = this != UNSET

    companion object {
        fun fromNative(value: Int): LibreTreatmentKind = entries.find { it.nativeValue == value } ?: UNSET
    }
}

/** The LibreView treatment mapping of a single logbook label. */
data class LibreLabelMapping(
    val index: Int,
    val label: String,
    val kind: LibreTreatmentKind,
    /** Grams of carbs a unit of this label stands for, only used for [LibreTreatmentKind.CARBS]. */
    val weight: Float
)

/**
 * The LibreView account and upload settings. The password is deliberately not part of
 * this: it is typed on the screen and handed to the repository on save only, so it does
 * not outlive the editor in a process wide state holder.
 */
data class LibreViewConfig(
    val region: LibreRegion = LibreRegion.UNITED_KINGDOM,
    /**
     * The account id as native reports it: the hand written number when there is one, otherwise
     * the value derived from the id LibreView sent after signing in. That derived value is a hash,
     * so it can be negative and only [hasAccountId] says whether there is an id at all.
     */
    val accountId: Long = -1L,
    val hasAccountId: Boolean = false,
    val manualAccountId: Boolean = false,
    val uploadCurrent: Boolean = false,
    val uploadViewed: Boolean = false,
    val sendAmounts: Boolean = false
)

/** Why saving the LibreView settings did not work. */
enum class LibreSaveError(@StringRes val messageRes: Int) {
    NONE(R.string.loc_libreview_saved),
    EMAIL_TOO_SHORT(R.string.emailaddresstooshort),
    EMAIL_TOO_LONG(R.string.emailaddresstoolong),
    PASSWORD_TOO_SHORT(R.string.password8),
    PASSWORD_TOO_LONG(R.string.password36),
    ACCOUNT_ID_INVALID(R.string.wrongformat),
    ACCOUNT_ID_MISSING(R.string.noaccountidspecified)
}
