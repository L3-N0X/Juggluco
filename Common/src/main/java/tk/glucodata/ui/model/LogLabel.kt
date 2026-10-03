package tk.glucodata.ui.model

/**
 * One label of the native logbook: the code entries are stored under, and how it is configured.
 *
 * Native storage only knows label codes. Which of them are carbs, bolus, basal or finger-pricks is
 * decided by [LabelConfig]; every other label is a [LogType.CUSTOM] one that keeps its own name.
 */
data class LogLabel(
    val index: Int,
    /** The name as the user wrote it, of any length. */
    val name: String,
    /**
     * The name where space is tight (watch, totals, filters). It is what native stores, so the
     * classic view, Garmin, Nightscout and mirrors show it too. Equal to [name] when that fits.
     */
    val shortName: String,
    val type: LogType,
    /**
     * Classic graph only: a weight above zero draws the amount at amount × weight on the glucose
     * scale instead of on the label's own row. In the display unit, like native hands it out.
     */
    val weight: Float,
    /** The step a Garmin watch rounds amounts of this label to. */
    val roundTo: Float
)

/**
 * The native labels together with the label each logbook entry type is saved under.
 *
 * Carbs is the native meal label and finger-prick the native blood label, so they are shared with
 * the classic view, meals, calibration and the integrations. Bolus and basal have no native
 * counterpart; they are chosen once (inferred from the native setup until the user picks one).
 */
data class LabelConfig(
    val labels: List<LogLabel> = emptyList(),
    val carbsLabel: Int = -1,
    val bolusLabel: Int = -1,
    val basalLabel: Int = -1,
    val bloodLabel: Int = -1,
    /** False on a device that mirrors the labels of another one (native static numbers). */
    val editable: Boolean = true,
    /** Whether a Garmin watch is in use, which is the only thing the rounding step is for. */
    val hasGarmin: Boolean = false
) {
    fun label(index: Int): LogLabel? = labels.getOrNull(index)

    fun nameOf(index: Int): String = labels.getOrNull(index)?.name.orEmpty()

    fun shortNameOf(index: Int): String = labels.getOrNull(index)?.shortName.orEmpty()

    /** The full name of the label [type] is saved under, or empty without one. */
    fun nameFor(type: LogType): String = nameOf(labelFor(type))

    /** The short name of the label [type] is saved under, or empty without one. */
    fun shortNameFor(type: LogType): String = shortNameOf(labelFor(type))

    /** The entry type of entries saved under [index]. */
    fun typeOf(index: Int): LogType = when {
        index < 0 || index >= labels.size -> LogType.CUSTOM
        index == bloodLabel -> LogType.BLOOD_GLUCOSE
        index == carbsLabel -> LogType.CARBS
        index == bolusLabel -> LogType.RAPID_INSULIN
        index == basalLabel -> LogType.BASAL_INSULIN
        else -> LogType.CUSTOM
    }

    /** The label new entries of [type] are saved under, or -1 when there is none. */
    fun labelFor(type: LogType): Int = when (type) {
        LogType.CARBS, LogType.MEAL -> carbsLabel
        LogType.RAPID_INSULIN -> bolusLabel
        LogType.BASAL_INSULIN -> basalLabel
        LogType.BLOOD_GLUCOSE -> bloodLabel
        LogType.CUSTOM -> -1
    }.takeIf { it in labels.indices } ?: -1

    /** The entry type [index] is assigned to, other than [except], if any. */
    fun roleOf(index: Int, except: LogType? = null): LogType? =
        ROLE_TYPES.firstOrNull { it != except && labelFor(it) == index && index >= 0 }

    val customLabels: List<LogLabel> get() = labels.filter { it.type == LogType.CUSTOM }

    val canAdd: Boolean get() = editable && labels.size < MAX_LABELS

    /** Native can only remove labels from the end, and a label in use has to be reassigned first. */
    fun canDelete(index: Int): Boolean =
        editable && index == labels.lastIndex && index > 0 && roleOf(index) == null

    companion object {
        /** `maxvarnr` in settings.hpp. */
        const val MAX_LABELS = 40

        /** The entry types that are tied to one label each, in the order they are shown. */
        val ROLE_TYPES = listOf(LogType.CARBS, LogType.RAPID_INSULIN, LogType.BASAL_INSULIN, LogType.BLOOD_GLUCOSE)
    }
}

/** Why a label could not be saved. */
enum class LabelSaveError { EMPTY, TOO_MANY, READ_ONLY, FAILED }
