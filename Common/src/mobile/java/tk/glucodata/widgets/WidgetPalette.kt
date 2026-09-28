package tk.glucodata.widgets

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.theme.AppThemePreferences
import tk.glucodata.ui.theme.ClinicalColors
import tk.glucodata.ui.theme.DarkClinicalColors
import tk.glucodata.ui.theme.LightClinicalColors
import tk.glucodata.ui.theme.jugglucoColorScheme

/**
 * Resolved colors for one widget.
 *
 * Everything is derived from the app's own theme: the scheme (custom accent, wallpaper colors or
 * the built-in palette) for surfaces and accents, and [ClinicalColors] for the glucose ranges, so a
 * widget reads like a piece of the app. The light or dark variant is chosen by what sits behind
 * the widget's content, which for a transparent widget is the wallpaper.
 */
class WidgetPalette(
    /** Background fill including opacity, or fully transparent. */
    val background: Int,
    val content: Int,
    val contentVariant: Int,
    /** Graph line and dial knob when range colors are off. */
    val accent: Int,
    val chip: Int,
    val onChip: Int,
    /** Text shadow for legibility straight on the wallpaper. */
    val shadow: Boolean,
    val darkSurface: Boolean,
    private val clinical: ClinicalColors,
    /** On a range-tinted background everything draws in the one content color. */
    private val monochrome: Boolean
) {
    val track: Int get() = ColorUtils.setAlphaComponent(content, 0x24)
    val subtle: Int get() = ColorUtils.setAlphaComponent(content, 0x14)
    val warning: Int get() = if (monochrome) content else clinical.low.toArgb()

    fun rangeColor(status: GlucoseStatus): Int = if (monochrome) content else when (status) {
        GlucoseStatus.VERY_LOW -> clinical.veryLow
        GlucoseStatus.LOW -> clinical.low
        GlucoseStatus.IN_RANGE -> clinical.inRange
        GlucoseStatus.HIGH -> clinical.high
        GlucoseStatus.VERY_HIGH -> clinical.veryHigh
    }.toArgb()

    fun rangeContainer(status: GlucoseStatus): Int = if (monochrome) ColorUtils.setAlphaComponent(content, 0x22) else when (status) {
        GlucoseStatus.VERY_LOW -> clinical.veryLowContainer
        GlucoseStatus.LOW -> clinical.lowContainer
        GlucoseStatus.IN_RANGE -> clinical.inRangeContainer
        GlucoseStatus.HIGH -> clinical.highContainer
        GlucoseStatus.VERY_HIGH -> clinical.veryHighContainer
    }.toArgb()

    fun onRangeContainer(status: GlucoseStatus): Int = if (monochrome) content else when (status) {
        GlucoseStatus.VERY_LOW -> clinical.onVeryLowContainer
        GlucoseStatus.LOW -> clinical.onLowContainer
        GlucoseStatus.IN_RANGE -> clinical.onInRangeContainer
        GlucoseStatus.HIGH -> clinical.onHighContainer
        GlucoseStatus.VERY_HIGH -> clinical.onVeryHighContainer
    }.toArgb()

    companion object {
        private const val LIGHT_CONTENT = 0xFFF7F9FA.toInt()
        private const val DARK_CONTENT = 0xFF171C1F.toInt()

        fun systemIsDark(): Boolean =
            (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        /** Whether the home screen wallpaper is dark, when the system can tell. */
        fun wallpaperIsDark(context: Context): Boolean? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
            return try {
                val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                    ?: return null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) == 0
                } else {
                    ColorUtils.calculateLuminance(colors.primaryColor.toArgb()) < 0.5
                }
            } catch (_: Throwable) {
                null
            }
        }

        fun resolve(
            context: Context,
            config: WidgetConfig,
            status: GlucoseStatus?,
            systemDark: Boolean = systemIsDark(),
            wallpaperDark: Boolean? = wallpaperIsDark(context)
        ): WidgetPalette {
            val overWallpaper = config.backgroundIsWallpaper
            val darkSurface = when {
                config.content == WidgetContentColor.LIGHT && (overWallpaper || config.background == WidgetBackground.CUSTOM) -> true
                config.content == WidgetContentColor.DARK && (overWallpaper || config.background == WidgetBackground.CUSTOM) -> false
                overWallpaper -> wallpaperDark ?: true
                config.background == WidgetBackground.CUSTOM -> ColorUtils.calculateLuminance(config.customColor) < 0.45
                else -> systemDark
            }
            AppThemePreferences.ensureLoaded(context)
            val customAccent = AppThemePreferences.customColorArgb.value
            val scheme = jugglucoColorScheme(context, darkSurface, customAccent)
            val clinical = if (darkSurface) DarkClinicalColors else LightClinicalColors
            val alpha = (config.opacity.coerceIn(0, 100) * 255 + 50) / 100
            val range = status ?: GlucoseStatus.IN_RANGE

            val neutralContent = if (darkSurface) LIGHT_CONTENT else DARK_CONTENT
            val neutralVariant = ColorUtils.setAlphaComponent(neutralContent, if (darkSurface) 0xC4 else 0xB8)
            val hasSchemeSurface = customAccent != null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            val fill: Int
            val content: Int
            val contentVariant: Int
            var accent = scheme.primary.toArgb()
            var chip = scheme.secondaryContainer.toArgb()
            var onChip = scheme.onSecondaryContainer.toArgb()
            var monochrome = false
            when (config.background) {
                WidgetBackground.SURFACE -> {
                    fill = if (hasSchemeSurface) scheme.surfaceContainer.toArgb()
                    else ColorUtils.blendARGB(scheme.surface.toArgb(), scheme.surfaceVariant.toArgb(), 0.35f)
                    content = scheme.onSurface.toArgb()
                    contentVariant = scheme.onSurfaceVariant.toArgb()
                }
                WidgetBackground.TONAL -> {
                    fill = scheme.primaryContainer.toArgb()
                    content = scheme.onPrimaryContainer.toArgb()
                    contentVariant = ColorUtils.setAlphaComponent(content, 0xC0)
                    chip = scheme.surface.toArgb()
                    onChip = scheme.primary.toArgb()
                }
                WidgetBackground.RANGE -> {
                    fill = when (range) {
                        GlucoseStatus.VERY_LOW -> clinical.veryLowContainer
                        GlucoseStatus.LOW -> clinical.lowContainer
                        GlucoseStatus.IN_RANGE -> clinical.inRangeContainer
                        GlucoseStatus.HIGH -> clinical.highContainer
                        GlucoseStatus.VERY_HIGH -> clinical.veryHighContainer
                    }.toArgb()
                    content = when (range) {
                        GlucoseStatus.VERY_LOW -> clinical.onVeryLowContainer
                        GlucoseStatus.LOW -> clinical.onLowContainer
                        GlucoseStatus.IN_RANGE -> clinical.onInRangeContainer
                        GlucoseStatus.HIGH -> clinical.onHighContainer
                        GlucoseStatus.VERY_HIGH -> clinical.onVeryHighContainer
                    }.toArgb()
                    contentVariant = ColorUtils.setAlphaComponent(content, 0xC0)
                    accent = content
                    chip = ColorUtils.setAlphaComponent(content, 0x22)
                    onChip = content
                    monochrome = true
                }
                WidgetBackground.CUSTOM -> {
                    fill = config.customColor
                    content = neutralContent
                    contentVariant = neutralVariant
                    chip = ColorUtils.setAlphaComponent(neutralContent, 0x26)
                    onChip = neutralContent
                }
                WidgetBackground.NONE -> {
                    fill = Color.TRANSPARENT
                    content = neutralContent
                    contentVariant = neutralVariant
                    accent = neutralContent
                    chip = ColorUtils.setAlphaComponent(neutralContent, 0x2E)
                    onChip = neutralContent
                }
            }
            return WidgetPalette(
                background = if (config.background == WidgetBackground.NONE) Color.TRANSPARENT
                else ColorUtils.setAlphaComponent(fill, alpha * Color.alpha(fill) / 255),
                content = content,
                contentVariant = contentVariant,
                accent = accent,
                chip = chip,
                onChip = onChip,
                shadow = overWallpaper && darkSurface,
                darkSurface = darkSurface,
                clinical = clinical,
                monochrome = monochrome
            )
        }
    }
}
