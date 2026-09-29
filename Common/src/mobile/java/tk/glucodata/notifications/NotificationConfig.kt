package tk.glucodata.notifications

import android.content.Context
import androidx.annotation.StringRes
import org.json.JSONObject
import tk.glucodata.R
import tk.glucodata.widgets.WIDGET_GRAPH_HOURS
import tk.glucodata.widgets.WidgetBackground
import tk.glucodata.widgets.WidgetConfig
import tk.glucodata.widgets.WidgetFont
import tk.glucodata.widgets.WidgetStatsPeriod

/** What a status bar icon shows. */
enum class StatusIconKind(@StringRes val labelRes: Int) {
    VALUE(R.string.notif_icon_value),
    VALUE_ARROW(R.string.notif_icon_value_arrow),
    ARROW(R.string.notif_icon_arrow),
    DELTA(R.string.notif_icon_delta),
    /** Juggluco's own mark, for when the glucose notification should not carry a number. */
    APP(R.string.notif_icon_app);
}

enum class StatusIconWeight(@StringRes val labelRes: Int, val weight: Int) {
    REGULAR(R.string.notif_icon_weight_regular, 500),
    BOLD(R.string.notif_icon_weight_bold, 700),
    HEAVY(R.string.notif_icon_weight_heavy, 900);
}

/** Text of the Live Update chip in the status bar. The system shows up to about seven characters. */
enum class LiveChipText(@StringRes val labelRes: Int) {
    VALUE(R.string.notif_icon_value),
    VALUE_ARROW(R.string.notif_icon_value_arrow),
    VALUE_DELTA(R.string.notif_chip_value_delta),
    DELTA(R.string.notif_icon_delta),
    NONE(R.string.notif_chip_none);
}

enum class NotificationGraphHeight(@StringRes val labelRes: Int, val dp: Float) {
    SMALL(R.string.notif_graph_small, 88f),
    MEDIUM(R.string.notif_graph_medium, 120f),
    LARGE(R.string.notif_graph_large, 156f);
}

/**
 * Look and content of the glucose notification and of the extra status bar icons.
 *
 * Whether the glucose notification is shown at all stays a native setting (`showalways`), shared
 * with the rest of Juggluco. Add new options with a default so stored settings keep working.
 */
data class NotificationConfig(
    val font: WidgetFont = WidgetFont.MODERN,
    /** Text size in percent of the size that fits the notification. */
    val textScale: Int = 100,
    val rangeColors: Boolean = true,
    val showArrow: Boolean = true,
    val showDelta: Boolean = true,
    val showTime: Boolean = true,
    val showUnit: Boolean = false,
    val showTimeInRange: Boolean = false,
    /** A small graph at the end of the collapsed notification. */
    val sparkline: Boolean = false,
    val sparklineHours: Int = 3,
    val expandedGraph: Boolean = true,
    val graphHours: Int = 3,
    val graphHeight: NotificationGraphHeight = NotificationGraphHeight.MEDIUM,
    val showTargetBand: Boolean = true,
    val showTimeAxis: Boolean = true,
    val expandedStats: Boolean = false,
    val statsPeriod: WidgetStatsPeriod = WidgetStatsPeriod.TODAY,
    val mainIcon: StatusIconKind = StatusIconKind.VALUE,
    /** Icons of the extra status bar notifications; null switches that notification off. */
    val secondIcon: StatusIconKind? = StatusIconKind.ARROW,
    val thirdIcon: StatusIconKind? = null,
    /** Icon size in percent of the default. */
    val iconScale: Int = 100,
    val iconWeight: StatusIconWeight = StatusIconWeight.BOLD,
    /** Android 16 Live Update: the glucose notification becomes a chip in the status bar. */
    val liveUpdate: Boolean = false,
    val liveChipIcon: StatusIconKind = StatusIconKind.ARROW,
    val liveChipText: LiveChipText = LiveChipText.VALUE,
    /** The system's progress bar as a gauge through the glucose ranges. */
    val liveRangeBar: Boolean = true
) {
    val extraIcons: List<StatusIconKind?> get() = listOf(secondIcon, thirdIcon)

    val hasExpandedView: Boolean get() = expandedGraph || expandedStats

    /** How much history the notification draws from. */
    fun historyMillis(now: Long): Long {
        var millis = 30 * 60_000L
        if (sparkline) millis = maxOf(millis, sparklineHours * 3_600_000L)
        if (expandedGraph) millis = maxOf(millis, graphHours * 3_600_000L)
        if (showTimeInRange || expandedStats) millis = maxOf(millis, now - tk.glucodata.widgets.WidgetSnapshot.periodStart(statsPeriod, now))
        return millis
    }

    /** The same options in the form the widget renderer takes. */
    fun widgetConfig(): WidgetConfig = WidgetConfig(
        background = WidgetBackground.NONE,
        font = font,
        textScale = textScale,
        rangeColors = rangeColors,
        showArrow = showArrow,
        showDelta = showDelta,
        showTime = showTime,
        showUnit = showUnit,
        graphHours = graphHours,
        showTargetBand = showTargetBand,
        showTimeAxis = showTimeAxis,
        statsPeriod = statsPeriod
    )

    fun toJson(): JSONObject = JSONObject()
        .put("font", font.name)
        .put("textScale", textScale)
        .put("rangeColors", rangeColors)
        .put("showArrow", showArrow)
        .put("showDelta", showDelta)
        .put("showTime", showTime)
        .put("showUnit", showUnit)
        .put("showTimeInRange", showTimeInRange)
        .put("sparkline", sparkline)
        .put("sparklineHours", sparklineHours)
        .put("expandedGraph", expandedGraph)
        .put("graphHours", graphHours)
        .put("graphHeight", graphHeight.name)
        .put("showTargetBand", showTargetBand)
        .put("showTimeAxis", showTimeAxis)
        .put("expandedStats", expandedStats)
        .put("statsPeriod", statsPeriod.name)
        .put("mainIcon", mainIcon.name)
        .put("secondIcon", secondIcon?.name ?: OFF)
        .put("thirdIcon", thirdIcon?.name ?: OFF)
        .put("iconScale", iconScale)
        .put("iconWeight", iconWeight.name)
        .put("liveUpdate", liveUpdate)
        .put("liveChipIcon", liveChipIcon.name)
        .put("liveChipText", liveChipText.name)
        .put("liveRangeBar", liveRangeBar)

    companion object {
        private const val OFF = "OFF"
        val ICON_SCALE_RANGE = 80..130
        val TEXT_SCALE_RANGE = 70..130

        private inline fun <reified E : Enum<E>> JSONObject.enumOr(key: String, fallback: E): E =
            optString(key, "").let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: fallback

        private fun JSONObject.slot(key: String, fallback: StatusIconKind?): StatusIconKind? {
            if (!has(key)) return fallback
            val name = optString(key, OFF)
            return StatusIconKind.entries.firstOrNull { it.name == name && it != StatusIconKind.APP }
        }

        private fun JSONObject.hours(key: String, fallback: Int): Int =
            optInt(key, fallback).let { if (it in WIDGET_GRAPH_HOURS) it else fallback }

        fun fromJson(json: JSONObject): NotificationConfig {
            val defaults = NotificationConfig()
            return NotificationConfig(
                font = json.enumOr("font", defaults.font),
                textScale = json.optInt("textScale", defaults.textScale).coerceIn(TEXT_SCALE_RANGE.first, TEXT_SCALE_RANGE.last),
                rangeColors = json.optBoolean("rangeColors", defaults.rangeColors),
                showArrow = json.optBoolean("showArrow", defaults.showArrow),
                showDelta = json.optBoolean("showDelta", defaults.showDelta),
                showTime = json.optBoolean("showTime", defaults.showTime),
                showUnit = json.optBoolean("showUnit", defaults.showUnit),
                showTimeInRange = json.optBoolean("showTimeInRange", defaults.showTimeInRange),
                sparkline = json.optBoolean("sparkline", defaults.sparkline),
                sparklineHours = json.hours("sparklineHours", defaults.sparklineHours),
                expandedGraph = json.optBoolean("expandedGraph", defaults.expandedGraph),
                graphHours = json.hours("graphHours", defaults.graphHours),
                graphHeight = json.enumOr("graphHeight", defaults.graphHeight),
                showTargetBand = json.optBoolean("showTargetBand", defaults.showTargetBand),
                showTimeAxis = json.optBoolean("showTimeAxis", defaults.showTimeAxis),
                expandedStats = json.optBoolean("expandedStats", defaults.expandedStats),
                statsPeriod = json.enumOr("statsPeriod", defaults.statsPeriod),
                mainIcon = json.enumOr("mainIcon", defaults.mainIcon),
                secondIcon = json.slot("secondIcon", defaults.secondIcon),
                thirdIcon = json.slot("thirdIcon", defaults.thirdIcon),
                iconScale = json.optInt("iconScale", defaults.iconScale).coerceIn(ICON_SCALE_RANGE.first, ICON_SCALE_RANGE.last),
                iconWeight = json.enumOr("iconWeight", defaults.iconWeight),
                liveUpdate = json.optBoolean("liveUpdate", defaults.liveUpdate),
                liveChipIcon = json.enumOr("liveChipIcon", defaults.liveChipIcon),
                liveChipText = json.enumOr("liveChipText", defaults.liveChipText),
                liveRangeBar = json.optBoolean("liveRangeBar", defaults.liveRangeBar)
            )
        }
    }
}

/** The one stored [NotificationConfig]. Saving it redraws the notifications. */
object NotificationConfigStore {
    private const val PREFS = "glucose_notification"
    private const val KEY = "config"

    @Volatile
    private var cached: NotificationConfig? = null

    fun load(context: Context): NotificationConfig {
        cached?.let { return it }
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        val config = try {
            raw?.let { NotificationConfig.fromJson(JSONObject(it)) } ?: NotificationConfig()
        } catch (_: Throwable) {
            NotificationConfig()
        }
        cached = config
        return config
    }

    fun save(context: Context, config: NotificationConfig) {
        cached = config
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, config.toJson().toString()).apply()
        NotificationRefresher.request(context)
    }
}
