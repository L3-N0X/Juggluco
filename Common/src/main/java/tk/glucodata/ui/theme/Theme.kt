package tk.glucodata.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import tk.glucodata.MainActivity

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

val LocalClinicalColors = staticCompositionLocalOf { LightClinicalColors }
val LocalLogbookColors = staticCompositionLocalOf { LightLogbookColors }

val MaterialTheme.logbookColors: LogbookColors
    @Composable
    get() = LocalLogbookColors.current

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

private data class HslColor(val hue: Float, val saturation: Float)

private fun Color.toHsl(): HslColor {
    val maximum = maxOf(red, green, blue)
    val minimum = minOf(red, green, blue)
    val delta = maximum - minimum
    val hue = when {
        delta == 0f -> 0f
        maximum == red -> 60f * (((green - blue) / delta) % 6f)
        maximum == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    return HslColor(
        hue = (hue + 360f) % 360f,
        saturation = if (maximum == 0f) 0f else delta / maximum
    )
}

private fun accentColor(hue: Float, saturation: Float, lightness: Float): Color = Color.hsl(
    hue = (hue % 360f + 360f) % 360f,
    saturation = saturation.coerceIn(0f, 1f),
    lightness = lightness.coerceIn(0f, 1f)
)

/**
 * Picks the content color that reads best on [background], keeping the chosen hue so the `on*`
 * roles stay part of the same palette instead of snapping to pure black or white.
 */
private fun contrastingContent(background: Color, hue: Float, saturation: Float): Color {
    val lightContent = accentColor(hue, saturation, 0.98f)
    val darkContent = accentColor(hue, saturation, 0.12f)
    return if (
        ColorUtils.calculateContrast(lightContent.toArgb(), background.toArgb()) >=
        ColorUtils.calculateContrast(darkContent.toArgb(), background.toArgb())
    ) {
        lightContent
    } else {
        darkContent
    }
}

/**
 * Derives a complete Material 3 scheme from the user's chosen color.
 *
 * A custom accent replaces the wallpaper scheme outright instead of being layered on top of it,
 * so no role - accent, neutral surface, container, outline or error - can keep a hue from the
 * system Material You palette. Every role below is a pure function of the seed, and the neutrals
 * carry a trace of the seed hue so cards, icons and fills stay harmonious with it.
 *
 * The error family deliberately keeps its own red hue: an alarm colour that drifted with the
 * accent would stop reading as an alarm.
 */
private fun seedColorScheme(seed: Color, darkTheme: Boolean): ColorScheme {
    val (hue, saturation) = seed.toHsl()
    val tertiaryHue = hue + 60f
    val neutralSaturation = saturation * 0.14f
    val contentSaturation = saturation * 0.18f
    val errorHue = 4f
    val errorSaturation = 0.72f
    val secondarySaturation = saturation * 0.55f

    // Resolves the dark or light tone of a role, so every call site reads as one table.
    fun tone(h: Float, s: Float, darkTone: Float, lightTone: Float) =
        accentColor(h, s, if (darkTheme) darkTone else lightTone)

    fun accent(h: Float, s: Float) = tone(h, s, darkTone = 0.78f, lightTone = 0.40f)
    fun accentContainer(h: Float, s: Float) = tone(h, s, darkTone = 0.30f, lightTone = 0.90f)
    fun onAccentContainer(h: Float, s: Float) = tone(h, s, darkTone = 0.90f, lightTone = 0.12f)
    fun neutral(darkTone: Float, lightTone: Float) =
        tone(hue, neutralSaturation, darkTone, lightTone)
    fun content(darkTone: Float, lightTone: Float) =
        tone(hue, contentSaturation, darkTone, lightTone)

    val primary = accent(hue, saturation)
    val primaryContainer = accentContainer(hue, saturation)
    val secondary = accent(hue, secondarySaturation)
    val secondaryContainer = accentContainer(hue, secondarySaturation)
    val tertiary = accent(tertiaryHue, saturation)
    val tertiaryContainer = accentContainer(tertiaryHue, saturation)
    val error = tone(errorHue, errorSaturation, darkTone = 0.72f, lightTone = 0.42f)
    val onSurface = content(darkTone = 0.95f, lightTone = 0.12f)

    val scheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = primary,
        onPrimary = contrastingContent(primary, hue, saturation),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onAccentContainer(hue, saturation),
        inversePrimary = tone(hue, saturation, darkTone = 0.40f, lightTone = 0.80f),
        secondary = secondary,
        onSecondary = contrastingContent(secondary, hue, secondarySaturation),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onAccentContainer(hue, secondarySaturation),
        tertiary = tertiary,
        onTertiary = contrastingContent(tertiary, tertiaryHue, saturation),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onAccentContainer(tertiaryHue, saturation),
        background = neutral(darkTone = 0.10f, lightTone = 0.99f),
        onBackground = onSurface,
        surface = neutral(darkTone = 0.12f, lightTone = 1.00f),
        onSurface = onSurface,
        surfaceVariant = neutral(darkTone = 0.22f, lightTone = 0.90f),
        onSurfaceVariant = content(darkTone = 0.74f, lightTone = 0.35f),
        surfaceTint = primary,
        inverseSurface = content(darkTone = 0.90f, lightTone = 0.20f),
        inverseOnSurface = content(darkTone = 0.12f, lightTone = 0.95f),
        error = error,
        onError = contrastingContent(error, errorHue, errorSaturation),
        errorContainer = tone(errorHue, errorSaturation, darkTone = 0.28f, lightTone = 0.92f),
        onErrorContainer = onAccentContainer(errorHue, errorSaturation),
        outline = neutral(darkTone = 0.60f, lightTone = 0.45f),
        outlineVariant = neutral(darkTone = 0.32f, lightTone = 0.80f),
        scrim = Color.Black,
        surfaceBright = neutral(darkTone = 0.24f, lightTone = 0.99f),
        surfaceDim = neutral(darkTone = 0.10f, lightTone = 0.94f),
        surfaceContainerLowest = neutral(darkTone = 0.08f, lightTone = 1.00f),
        surfaceContainerLow = neutral(darkTone = 0.11f, lightTone = 0.975f),
        surfaceContainer = neutral(darkTone = 0.14f, lightTone = 0.955f),
        surfaceContainerHigh = neutral(darkTone = 0.17f, lightTone = 0.93f),
        surfaceContainerHighest = neutral(darkTone = 0.21f, lightTone = 0.91f)
    )
}

@Composable
fun JugglucoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    AppThemePreferences.ensureLoaded(context)
    val customColorArgb by AppThemePreferences.customColorArgb.collectAsState()
    val customSeed = customColorArgb?.let { Color(it) }
    val colorScheme = when {
        customSeed != null ->
            remember(customSeed, darkTheme) { seedColorScheme(customSeed, darkTheme) }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            remember(context, darkTheme) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
        else -> if (darkTheme) DarkColorScheme else LightColorScheme
    }

    val clinicalColors = if (darkTheme) DarkClinicalColors else LightClinicalColors
    val logbookColors = if (darkTheme) DarkLogbookColors else LightLogbookColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity != null) {
                val window = activity.window
                val insetsController = WindowCompat.getInsetsController(window, view)
                // When darkTheme is true, appearance light bars is false so status bar and
                // navigation bar icons/text appear light (white) on dark surfaces.
                // When darkTheme is false, appearance light bars is true so icons appear dark (black).
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme

                if (activity is MainActivity) {
                    activity.lightBars(!darkTheme)
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalClinicalColors provides clinicalColors,
        LocalLogbookColors provides logbookColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
