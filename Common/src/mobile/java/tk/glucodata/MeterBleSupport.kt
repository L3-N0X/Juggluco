package tk.glucodata

import android.bluetooth.BluetoothDevice
import java.util.concurrent.ConcurrentHashMap
import tk.glucodata.ui.meters.MeterBle
import tk.glucodata.ui.meters.MeterFound
import tk.glucodata.ui.meters.MeterRuntime

/**
 * The phone's meter Bluetooth stack behind [MeterBle]. The watch has a twin that does nothing;
 * see MeterBle.
 */
object MeterBleSupport : MeterBle {
    private const val LOG_ID = "MeterBleSupport"

    /**
     * The devices the search turned up. Connecting needs the BluetoothDevice itself, while the
     * settings screen only shows a name and an address, so the devices are kept here between the
     * moment they are found and the moment the user picks one.
     */
    private val scanned = ConcurrentHashMap<String, BluetoothDevice>()

    override val supported = true

    override val bluetoothEnabled: Boolean
        get() = try {
            BluetoothGlucoseMeter.bluetoothIsEnabled()
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "bluetoothIsEnabled", th)
            false
        }

    override fun startDiscovery(aidexX: Boolean, onFound: (MeterFound) -> Unit) {
        scanned.clear()
        BluetoothGlucoseMeter.startAdapterScanner(object : MeterScanner.DeviceFoundListener {
            override fun deviceFound(device: BluetoothDevice, name: String, address: String?) {
                scanned[name] = device
                onFound(
                    MeterFound(
                        name = name,
                        address = address,
                        alreadyAdded = Natives.GlucoseMeterHasIndex(name, address) >= 0
                    )
                )
            }
        }, aidexX)
    }

    override fun stopDiscovery() {
        BluetoothGlucoseMeter.stopScanner()
        scanned.clear()
    }

    override fun activate(index: Int, found: MeterFound?) {
        val device = found?.name?.let { scanned[it] }
        BluetoothGlucoseMeter.addDevice(index, device)
    }

    override fun deactivate(index: Int) {
        BluetoothGlucoseMeter.removeDevice(index)
    }

    override fun restart() {
        BluetoothGlucoseMeter.restartDevices()
    }

    override fun runtimeStates(): List<MeterRuntime> {
        // The array is swapped while meters are added or dropped, so it is read once and copied.
        val gatts = BluetoothGlucoseMeter.meterGatts ?: return emptyList()
        return gatts.map { gatt ->
            MeterRuntime(
                index = gatt.meterIndex,
                connected = gatt.connectedTime > gatt.disconnectedTime,
                bonded = gatt.isBonded,
                connectedTime = gatt.connectedTime,
                disconnectedTime = gatt.disconnectedTime,
                receivedTime = gatt.receivedTime,
                hasNewValues = gatt.newvalues
            )
        }
    }
}
