package tk.glucodata.ui.meters

/**
 * The Bluetooth half of the glucose meter feature: scanning for meters, talking to them and
 * reporting what the connection is doing.
 *
 * Only the phone builds can do any of this, so [tk.glucodata.MeterBleSupport] has a do-nothing
 * twin there, built on [UnsupportedMeterBle]. Everything above this line therefore only ever talks
 * to the interface, which is why the meter settings screen lives in the shared source set.
 */
interface MeterBle {
    /** False on builds without meter support, so the settings screen can say so instead of failing. */
    val supported: Boolean

    /** Whether the radio is on and the app may scan. */
    val bluetoothEnabled: Boolean

    /** Starts searching for meters, reporting every one it sees through [onFound]. */
    fun startDiscovery(aidexX: Boolean, onFound: (MeterFound) -> Unit)

    fun stopDiscovery()

    /**
     * Starts talking to a meter. [found] is the scanned device when the meter was just discovered,
     * which saves looking the address up a second time.
     */
    fun activate(index: Int, found: MeterFound?)

    /** Stops talking to a meter, leaving its stored settings alone. */
    fun deactivate(index: Int)

    /** Connects every active meter again, after the meter list changed. */
    fun restart()

    /** The live connection state of the meters that are currently being talked to. */
    fun runtimeStates(): List<MeterRuntime>
}

/**
 * The meter Bluetooth stack of a build that has none. Every call is a no-op, so callers do not
 * have to check [supported] before using them.
 */
object UnsupportedMeterBle : MeterBle {
    override val supported = false
    override val bluetoothEnabled = false

    override fun startDiscovery(aidexX: Boolean, onFound: (MeterFound) -> Unit) = Unit

    override fun stopDiscovery() = Unit

    override fun activate(index: Int, found: MeterFound?) = Unit

    override fun deactivate(index: Int) = Unit

    override fun restart() = Unit

    override fun runtimeStates(): List<MeterRuntime> = emptyList()
}
