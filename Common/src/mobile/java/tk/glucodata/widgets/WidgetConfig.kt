package tk.glucodata.widgets

import android.content.Context
import androidx.annotation.StringRes
import org.json.JSONObject
import tk.glucodata.R

/** The home screen widgets. Each kind has its own provider so it shows up separately in the launcher's picker. */
enum class WidgetKind(
    val provider: Class<out BaseGlucoseWidget>,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    /** Size used before the launcher reports one, and for the preview in the app. */
    val defaultWidthDp: Float,
    val defaultHeightDp: Float
) {
    MINIMAL(MinimalGlucoseWidget::class.java, R.string.widget_minimal, R.string.widget_minimal_description, 150f, 80f),
    COMPACT(CompactGlucoseWidget::class.java, R.string.widget_compact, R.string.widget_compact_description, 200f, 72f),
    TREND(TrendGlucoseWidget::class.java, R.string.widget_trend, R.string.widget_trend_description, 320f, 160f),
    DIAL(DialGlucoseWidget::class.java, R.string.widget_dial, R.string.widget_dial_description, 160f, 160f),
    RANGE(RangeGlucoseWidget::class.java, R.string.widget_range, R.string.widget_range_description, 320f, 150f);

    val hasGraph: Boolean get() = this == TREND
    val hasStats: Boolean get() = this == RANGE

    fun defaultConfig(): WidgetConfig = when (this) {
        MINIMAL -> WidgetConfig(background = WidgetBackground.NONE, showUnit = false)
        COMPACT -> WidgetConfig(shape = WidgetShape.PILL)
        TREND -> WidgetConfig()
        DIAL -> WidgetConfig(shape = WidgetShape.ROUND)
        RANGE -> WidgetConfig()
    }

    companion object {
        fun forProvider(className: String?): WidgetKind? = entries.firstOrNull { it.provider.name == className }
    }
}

enum class WidgetBackground(@StringRes val labelRes: Int) {
    /** Material You surface, follows the wallpaper colors and light/dark mode. */
    SURFACE(R.string.widget_bg_surface),
    /** Accent container color, a little more colorful than [SURFACE]. */
    TONAL(R.string.widget_bg_tonal),
    /** Tinted with the glucose range of the current reading. */
    RANGE(R.string.widget_bg_range),
    CUSTOM(R.string.widget_bg_custom),
    NONE(R.string.widget_bg_none);
}

enum class WidgetShape(@StringRes val labelRes: Int) {
    /** The launcher's own widget corner radius (Android 12+), 20dp before that. */
    SYSTEM(R.string.widget_shape_system),
    ROUND(R.string.widget_shape_round),
    PILL(R.string.widget_shape_pill),
    SQUARE(R.string.widget_shape_square);
}

/** Text color over a background whose lightness the widget cannot know (none, custom). */
enum class WidgetContentColor(@StringRes val labelRes: Int) {
    AUTO(R.string.widget_content_auto),
    LIGHT(R.string.widget_content_light),
    DARK(R.string.widget_content_dark);
}

enum class WidgetFont(@StringRes val labelRes: Int, val family: String, val weight: Int) {
    MODERN(R.string.widget_font_modern, "sans-serif", 500),
    LIGHT(R.string.widget_font_light, "sans-serif", 300),
    HEAVY(R.string.widget_font_heavy, "sans-serif", 800),
    CONDENSED(R.string.widget_font_condensed, "sans-serif-condensed", 600),
    SERIF(R.string.widget_font_serif, "serif", 400),
    MONO(R.string.widget_font_mono, "monospace", 400);
}

enum class WidgetAlignment(@StringRes val labelRes: Int) {
    START(R.string.widget_align_start),
    CENTER(R.string.widget_align_center);
}

/** Period for the time in range widget. */
enum class WidgetStatsPeriod(@StringRes val labelRes: Int, val hours: Int) {
    TODAY(R.string.widget_period_today, 0),
    DAY(R.string.widget_period_24h, 24),
    WEEK(R.string.widget_period_7d, 24 * 7);
}

val WIDGET_GRAPH_HOURS = intArrayOf(1, 3, 6, 12, 24)

data class WidgetConfig(
    val background: WidgetBackground = WidgetBackground.SURFACE,
    val customColor: Int = 0xFF1F2A30.toInt(),
    /** Background opacity in percent. */
    val opacity: Int = 100,
    val shape: WidgetShape = WidgetShape.SYSTEM,
    val content: WidgetContentColor = WidgetContentColor.AUTO,
    val font: WidgetFont = WidgetFont.MODERN,
    /** Text size in percent of the size that fits the widget. */
    val textScale: Int = 100,
    /** Time in range percentage size, separate from the glucose reading and in percent of the space that fits. */
    val rangeScale: Int = 70,
    val rangeColors: Boolean = true,
    val showArrow: Boolean = true,
    val showDelta: Boolean = true,
    val showTime: Boolean = true,
    val showUnit: Boolean = false,
    val alignment: WidgetAlignment = WidgetAlignment.CENTER,
    val graphHours: Int = 3,
    val showTargetBand: Boolean = true,
    val showTimeAxis: Boolean = true,
    val statsPeriod: WidgetStatsPeriod = WidgetStatsPeriod.DAY
) {
    /** Whether the background is see-through enough that the wallpaper decides the text color. */
    val backgroundIsWallpaper: Boolean
        get() = background == WidgetBackground.NONE || opacity < 35

    fun toJson(): JSONObject = JSONObject()
        .put("background", background.name)
        .put("customColor", customColor)
        .put("opacity", opacity)
        .put("shape", shape.name)
        .put("content", content.name)
        .put("font", font.name)
        .put("textScale", textScale)
        .put("rangeScale", rangeScale)
        .put("rangeColors", rangeColors)
        .put("showArrow", showArrow)
        .put("showDelta", showDelta)
        .put("showTime", showTime)
        .put("showUnit", showUnit)
        .put("alignment", alignment.name)
        .put("graphHours", graphHours)
        .put("showTargetBand", showTargetBand)
        .put("showTimeAxis", showTimeAxis)
        .put("statsPeriod", statsPeriod.name)

    companion object {
        private inline fun <reified E : Enum<E>> JSONObject.enumOr(key: String, fallback: E): E =
            optString(key, "").let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: fallback

        fun fromJson(json: JSONObject, defaults: WidgetConfig): WidgetConfig = WidgetConfig(
            background = json.enumOr("background", defaults.background),
            customColor = json.optInt("customColor", defaults.customColor),
            opacity = json.optInt("opacity", defaults.opacity).coerceIn(0, 100),
            shape = json.enumOr("shape", defaults.shape),
            content = json.enumOr("content", defaults.content),
            font = json.enumOr("font", defaults.font),
            textScale = json.optInt("textScale", defaults.textScale).coerceIn(70, 140),
            rangeScale = json.optInt("rangeScale", defaults.rangeScale).coerceIn(50, 100),
            rangeColors = json.optBoolean("rangeColors", defaults.rangeColors),
            showArrow = json.optBoolean("showArrow", defaults.showArrow),
            showDelta = json.optBoolean("showDelta", defaults.showDelta),
            showTime = json.optBoolean("showTime", defaults.showTime),
            showUnit = json.optBoolean("showUnit", defaults.showUnit),
            alignment = json.enumOr("alignment", defaults.alignment),
            graphHours = json.optInt("graphHours", defaults.graphHours).let { hours ->
                if (hours in WIDGET_GRAPH_HOURS) hours else defaults.graphHours
            },
            showTargetBand = json.optBoolean("showTargetBand", defaults.showTargetBand),
            showTimeAxis = json.optBoolean("showTimeAxis", defaults.showTimeAxis),
            statsPeriod = json.enumOr("statsPeriod", defaults.statsPeriod)
        )
    }
}

/** Per-widget settings, keyed by app widget id. */
object WidgetConfigStore {
    private const val PREFS = "home_widgets"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context, appWidgetId: Int, kind: WidgetKind): WidgetConfig {
        val defaults = kind.defaultConfig()
        val raw = prefs(context).getString(key(appWidgetId), null) ?: return defaults
        return try {
            WidgetConfig.fromJson(JSONObject(raw), defaults)
        } catch (_: Throwable) {
            defaults
        }
    }

    fun save(context: Context, appWidgetId: Int, config: WidgetConfig) {
        prefs(context).edit().putString(key(appWidgetId), config.toJson().toString()).apply()
    }

    fun delete(context: Context, appWidgetIds: IntArray) {
        val editor = prefs(context).edit()
        appWidgetIds.forEach { editor.remove(key(it)) }
        editor.apply()
    }

    fun move(context: Context, from: Int, to: Int) {
        val prefs = prefs(context)
        val raw = prefs.getString(key(from), null) ?: return
        prefs.edit().remove(key(from)).putString(key(to), raw).apply()
    }

    private fun key(appWidgetId: Int) = "widget_$appWidgetId"
}
