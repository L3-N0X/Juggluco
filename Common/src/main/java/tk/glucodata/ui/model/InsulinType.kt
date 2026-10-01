package tk.glucodata.ui.model

import androidx.annotation.StringRes
import tk.glucodata.R

/**
 * The insulin a logbook label holds, the native `Insulin` enum of `settings.hpp`.
 *
 * Only the seven rapid acting insulins have an onboard (IOB) curve; [NONE] means the label takes
 * no part in the calculation. The values are the ones native stores, and the order is the one the
 * classic picker offered, so a label's type survives every move between the two views.
 */
enum class InsulinType(val nativeValue: Int, @StringRes val labelRes: Int) {
    NONE(0, R.string.not),
    HUMAN(1, R.string.humaninsulin),
    ASPART(2, R.string.aspart),
    LISPRO(3, R.string.lispro),
    GLULISINE(4, R.string.glulisine),
    FIASP(5, R.string.fiasp),
    ULTRA_RAPID(6, R.string.urli),
    AFREZZA(7, R.string.afrezza);

    /** Whether entries under a label of this type are summed into the insulin on board. */
    val countsTowardsIob: Boolean get() = nativeValue != NONE.nativeValue

    companion object {
        /** The type native holds for a label; an unknown value is treated as [NONE]. */
        fun of(nativeValue: Int): InsulinType =
            entries.firstOrNull { it.nativeValue == nativeValue } ?: NONE
    }
}
