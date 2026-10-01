package tk.glucodata.ui.model

import android.content.Context
import androidx.annotation.StringRes
import tk.glucodata.R
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogType(@StringRes val labelRes: Int, @StringRes val unitLabelRes: Int?) {
    RAPID_INSULIN(R.string.log_type_rapid_insulin, R.string.unit_insulin_short),
    BASAL_INSULIN(R.string.log_type_basal_insulin, R.string.unit_insulin_short),
    CARBS(R.string.log_type_carbs, R.string.unit_carbs_short),
    BLOOD_GLUCOSE(R.string.log_type_finger_prick, null),
    MEAL(R.string.log_type_meal, R.string.unit_carbs_short),

    /** Any other native label ("Bike", "Dextro", ...), shown under the label's own name. */
    CUSTOM(R.string.log_type_custom, null);
}

enum class NumberStore(val nativeIndex: Int) {
    WATCH(0),
    HERE(1)
}

data class NumberStoreSource(
    val store: NumberStore,
    val position: Int
)

private val idGenerator = AtomicLong(System.currentTimeMillis())

data class LogRecord(
    val id: Long = idGenerator.incrementAndGet(),
    val timestamp: Long,
    val type: LogType,
    val value: Float,
    val note: String = "",
    val nativeSource: NumberStoreSource? = null,
    val nativeLabel: Int? = null,
    val mealPointer: Int = 0,
    /** The configured name of [nativeLabel], so a custom label reads the way the user named it. */
    val labelName: String = "",
    /** The ingredients of an attached meal, comma separated, or empty without one. */
    val mealSummary: String = ""
) {
    /** Whether [mealPointer] refers to a composed meal (on the blood label it is the exclude flag). */
    val hasMeal: Boolean get() = type == LogType.CARBS && mealPointer > 0 && mealSummary.isNotEmpty()

    fun formattedValue(context: Context, unit: GlucoseUnit): String {
        val locale = Locale.getDefault()
        return when (type) {
            LogType.RAPID_INSULIN, LogType.BASAL_INSULIN -> context.getString(
                R.string.log_value_insulin,
                String.format(locale, "%.1f", value)
            )
            LogType.CARBS, LogType.MEAL -> context.getString(
                R.string.log_value_carbs,
                String.format(locale, "%.0f", value)
            )
            LogType.BLOOD_GLUCOSE -> context.getString(
                R.string.log_glucose_value,
                unit.format(value),
                context.getString(unit.labelRes)
            )
            LogType.CUSTOM -> formatCustomAmount(value)
        }
    }

    companion object {
        /** A custom label's amount without a unit, trimmed to what it holds ("30", "2.5"). */
        fun formatCustomAmount(value: Float): String =
            java.math.BigDecimal(value.toDouble()).setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString()

        /**
         * Stable identifier for an entry that came from a native log store.
         *
         * The default [id] mints a fresh value on every construction, which meant a reload produced a
         * list that could never compare equal to the previous one - so every collector of the
         * logbook recomposed each time it was refreshed, and the fingerprint check that is supposed
         * to make that a no-op could never fire. Entries sourced from (store, position) instead get
         * an id derived from that pair, so a reload is value-equal to its predecessor. Locally
         * created entries still use the generator, because those are genuinely new rows.
         */
        fun nativeId(store: NumberStore, position: Int): Long =
            (store.nativeIndex.toLong() + 1L) * 1_000_000_000_000L + position
    }
}
