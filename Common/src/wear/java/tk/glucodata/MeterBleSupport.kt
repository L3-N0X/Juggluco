package tk.glucodata

import tk.glucodata.ui.meters.MeterBle
import tk.glucodata.ui.meters.UnsupportedMeterBle

/** The watch has no meter Bluetooth stack; see the phone's MeterBleSupport. */
object MeterBleSupport : MeterBle by UnsupportedMeterBle
