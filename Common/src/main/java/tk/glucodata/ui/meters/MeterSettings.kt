package tk.glucodata.ui.meters

import android.content.Context
import tk.glucodata.Natives

/**
 * The switch that decides whether the glucose meter feature is used at all.
 *
 * Reading a Bluetooth finger-prick meter is a rarity next to a CGM sensor, so the feature ships
 * switched off and stays out of the way. It is off for everyone who has no meter in the list, and
 * on for whoever already had one, so nobody loses a setup that used to work.
 */
object MeterSettings {
    private const val PREFS = "meters_prefs"
    private const val KEY_ENABLED = "meters_enabled"

    /** True when meters are in use, so the phone should be talking to them. */
    @JvmStatic
    fun isEnabled(context: Context): Boolean = try {
        prefs(context).getBoolean(KEY_ENABLED, hasInventory())
    } catch (_: Throwable) {
        hasInventory()
    }

    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        try {
            prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        } catch (_: Throwable) {
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hasInventory(): Boolean = try {
        Natives.GlucoseMeterCount() > 0
    } catch (_: Throwable) {
        false
    }
}
