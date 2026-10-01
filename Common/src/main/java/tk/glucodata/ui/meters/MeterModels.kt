package tk.glucodata.ui.meters

/**
 * One configured meter, as the native meter list knows it.
 *
 * [index] is the position in the native meter list, not a stable id: removing a meter shifts the
 * ones behind it, so a list of these is always read fresh instead of held on to.
 */
data class MeterInfo(
    val index: Int,
    val name: String,
    val address: String,
    val active: Boolean,
    /** Last reading the meter handed over, in milliseconds, or 0 when it never did. */
    val lastReadingTime: Long
)

/** What the live Bluetooth connection to a meter is doing right now. */
data class MeterRuntime(
    val index: Int,
    val connected: Boolean,
    val bonded: Boolean,
    val connectedTime: Long,
    val disconnectedTime: Long,
    /** When new readings last arrived, 0 when nothing arrived yet. */
    val receivedTime: Long,
    val hasNewValues: Boolean
)

/**
 * A meter seen while scanning. [address] is only set for the meters whose address identifies them,
 * which is what the native meter list is matched on; most are known by name alone.
 */
data class MeterFound(
    val name: String,
    val address: String?,
    val alreadyAdded: Boolean
)

enum class MeterScanStatus {
    IDLE,
    SCANNING,

    /** This build has no meter Bluetooth stack, so nothing can be scanned for. */
    UNSUPPORTED,

    /** The radio is off, or the nearby-devices permission is missing. */
    UNAVAILABLE
}

/** The state of the meter search: what it is doing and everything it has found so far. */
data class MeterScanState(
    val status: MeterScanStatus = MeterScanStatus.IDLE,
    val found: List<MeterFound> = emptyList()
)
