package tk.glucodata.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import tk.glucodata.R
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LogType

/** One icon per log type, shared by the logbook rows and the entry editor. */
val LogType.icon: ImageVector
    get() = when (this) {
        LogType.RAPID_INSULIN -> Icons.Default.Vaccines
        LogType.BASAL_INSULIN -> Icons.Default.Timelapse
        LogType.CARBS, LogType.MEAL -> Icons.Default.Restaurant
        LogType.BLOOD_GLUCOSE -> Icons.Default.WaterDrop
        LogType.NOTE -> Icons.AutoMirrored.Filled.Notes
    }

/** The short everyday name ("Bolus", "Carbs") used wherever space is tight. */
@get:StringRes
val LogType.shortLabelRes: Int
    get() = when (this) {
        LogType.RAPID_INSULIN -> R.string.log_short_bolus
        LogType.BASAL_INSULIN -> R.string.log_short_basal
        LogType.CARBS, LogType.MEAL -> R.string.log_short_carbs
        LogType.BLOOD_GLUCOSE -> R.string.log_type_finger_prick
        LogType.NOTE -> R.string.log_type_note
    }

/**
 * Input rules for an amount of [type], in the unit the user types it in.
 *
 * [decimals] is also the precision the editor rounds history values to, so a remembered dose
 * shows up exactly the way it would be typed.
 */
internal data class AmountSpec(
    val maxIntegerDigits: Int,
    val decimals: Int,
    val min: Float,
    val max: Float
)

internal fun amountSpec(type: LogType, unit: GlucoseUnit): AmountSpec = when (type) {
    LogType.CARBS, LogType.MEAL -> AmountSpec(maxIntegerDigits = 3, decimals = 0, min = 1f, max = 500f)
    LogType.RAPID_INSULIN, LogType.BASAL_INSULIN ->
        AmountSpec(maxIntegerDigits = 3, decimals = 2, min = 0.05f, max = 200f)
    LogType.BLOOD_GLUCOSE -> if (unit == GlucoseUnit.MMOL_L) {
        AmountSpec(maxIntegerDigits = 2, decimals = 1, min = 1.1f, max = 33.3f)
    } else {
        AmountSpec(maxIntegerDigits = 3, decimals = 0, min = 20f, max = 600f)
    }
    LogType.NOTE -> AmountSpec(maxIntegerDigits = 4, decimals = 1, min = 0f, max = 9999f)
}

/** The unit suffix shown next to a typed amount. */
@StringRes
internal fun amountFormatRes(type: LogType): Int? = when (type) {
    LogType.RAPID_INSULIN, LogType.BASAL_INSULIN -> R.string.log_value_insulin
    LogType.CARBS, LogType.MEAL -> R.string.log_value_carbs
    else -> null
}
