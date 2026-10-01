package tk.glucodata.ui.data

import tk.glucodata.Applic
import tk.glucodata.Log
import tk.glucodata.Natives
import tk.glucodata.ui.model.InsulinType
import kotlin.math.roundToInt

/**
 * The native insulin on board (IOB) settings: whether the calculation runs at all, which insulin
 * each label holds, and what the calculation comes to right now.
 *
 * All of it lives in `settings.dat` rather than in Compose state, and two of the calls native
 * exports are phone only: a watch takes its insulin types from the phone (see `datbackup.cpp`) and
 * has no way to write them back. Every call therefore answers with the safe value instead of
 * throwing when the native side cannot serve it, which is also what a native library that predates
 * a call does.
 */
internal object NativeInsulinOnboard {
    private const val LOG_ID = "NativeInsulinOnboard"

    /** Whether native counts insulin doses into an onboard value. Off by default. */
    fun isEnabled(): Boolean = try {
        Applic.Nativesloaded && Natives.getIOB()
    } catch (_: Throwable) {
        false
    }

    /**
     * Turns the calculation on or off, and returns what native made of it.
     *
     * Native refuses to enable it while every label is still [InsulinType.NONE], because there is
     * nothing to calculate with, so false here means "no label has an insulin type yet" rather
     * than a failure. The classic screen showed its help page for the same refusal.
     */
    fun setEnabled(enabled: Boolean): Boolean = try {
        Applic.Nativesloaded && Natives.setIOB(enabled)
    } catch (th: Throwable) {
        Log.stack(LOG_ID, "setEnabled", th)
        false
    }

    /** The insulin [index] holds, or [InsulinType.NONE] where native cannot say. */
    fun insulinType(index: Int): InsulinType = try {
        InsulinType.of(Natives.getInsulinType(index))
    } catch (_: Throwable) {
        InsulinType.NONE
    }

    /** Stores the insulin [index] holds. Returns whether native kept it. */
    fun setInsulinType(index: Int, type: InsulinType): Boolean = try {
        if (!Applic.Nativesloaded) return false
        Natives.setInsulinType(index, type.nativeValue)
        true
    } catch (_: Throwable) {
        false
    }

    /**
     * Gives [index] [type] unless the label already holds one.
     *
     * This is the bridge between the two stores: Compose decides which label a bolus is saved
     * under in its own preferences, while the calculation reads native's insulin types. A bolus
     * label picked here would otherwise count as nothing at all. Aspart is the default because it
     * is what native itself assumes for a label mapped as rapid acting insulin, and a type the
     * user chose is never overwritten.
     */
    fun ensureInsulinType(index: Int, type: InsulinType): Boolean {
        if (!type.countsTowardsIob || insulinType(index) != InsulinType.NONE) return false
        return setInsulinType(index, type)
    }

    /**
     * The insulin still on board at [timeMillis], in insulin units, or null when the calculation
     * is off, native cannot answer (no native library, or one that predates the export) or nothing
     * is on board. Native answers NaN in the first case, which is also what a missing value
     * becomes.
     *
     * Rounded to a tenth, which is the precision a dose is given in anyway: the curve decays
     * continuously, so an unrounded value would republish a new number every refresh and set the
     * glucose screen recomposing for a difference no one can read.
     */
    fun valueAt(timeMillis: Long): Float? = try {
        if (!Applic.Nativesloaded) return null
        val value = Natives.getIOBvalue(timeMillis)
        if (!value.isFinite() || value < 0f) null else (value * 10f).roundToInt() / 10f
    } catch (_: Throwable) {
        null
    }
}
