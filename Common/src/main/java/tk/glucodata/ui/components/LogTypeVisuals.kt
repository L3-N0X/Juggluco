package tk.glucodata.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import tk.glucodata.R
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.LabelConfig
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.model.LogType

/** One icon per log type, shared by the logbook rows and the entry editor. */
val LogType.icon: ImageVector
    get() = when (this) {
        LogType.RAPID_INSULIN -> Icons.Default.Vaccines
        LogType.BASAL_INSULIN -> Icons.Default.Timelapse
        LogType.CARBS, LogType.MEAL -> Icons.Default.Restaurant
        LogType.BLOOD_GLUCOSE -> Icons.Default.WaterDrop
        LogType.CUSTOM -> Icons.AutoMirrored.Filled.Label
    }

/** The short everyday name ("Bolus", "Carbs") used wherever space is tight. */
@get:StringRes
val LogType.shortLabelRes: Int
    get() = when (this) {
        LogType.RAPID_INSULIN -> R.string.log_short_bolus
        LogType.BASAL_INSULIN -> R.string.log_short_basal
        LogType.CARBS, LogType.MEAL -> R.string.log_short_carbs
        LogType.BLOOD_GLUCOSE -> R.string.log_type_finger_prick
        LogType.CUSTOM -> R.string.log_type_custom
    }

/**
 * What a logged entry is called: the full name of the label it is saved under, so "NovoRapid"
 * reads as NovoRapid. Only an entry whose label has no name falls back to its type.
 */
@Composable
fun LogRecord.title(): String =
    labelName.ifBlank { stringResource(if (type == LogType.CUSTOM) R.string.log_type_custom else type.shortLabelRes) }

/** What entries of [type] are called: the full name of their label, or the type's name without one. */
@Composable
fun LabelConfig.displayName(type: LogType): String =
    nameFor(type).ifBlank { stringResource(type.shortLabelRes) }

/**
 * The same where space is tight (totals, filter chips, the watch): the label's short name, or
 * [fallback] while there is no label.
 */
@Composable
fun LabelConfig.shortDisplayName(type: LogType, @StringRes fallback: Int = type.shortLabelRes): String =
    shortNameFor(type).ifBlank { stringResource(fallback) }

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
    LogType.CUSTOM -> AmountSpec(maxIntegerDigits = 4, decimals = 2, min = 0f, max = 9999f)
}

/** The unit suffix shown next to a typed amount. */
@StringRes
internal fun amountFormatRes(type: LogType): Int? = when (type) {
    LogType.RAPID_INSULIN, LogType.BASAL_INSULIN -> R.string.log_value_insulin
    LogType.CARBS, LogType.MEAL -> R.string.log_value_carbs
    else -> null
}
