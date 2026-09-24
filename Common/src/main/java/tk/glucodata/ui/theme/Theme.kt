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

private fun customAccentColorScheme(
    base: ColorScheme,
    seed: Color,
    darkTheme: Boolean
): ColorScheme {
    val hsl = seed.toHsl()
    val primary = accentColor(hsl.hue, hsl.saturation, if (darkTheme) 0.78f else 0.40f)
    val primaryContainer = accentColor(hsl.hue, hsl.saturation, if (darkTheme) 0.30f else 0.90f)
    val onPrimaryContainer = accentColor(hsl.hue, hsl.saturation, if (darkTheme) 0.90f else 0.12f)
    val secondarySaturation = hsl.saturation * 0.55f
    val secondary = accentColor(hsl.hue, secondarySaturation, if (darkTheme) 0.78f else 0.40f)
    val secondaryContainer = accentColor(hsl.hue, secondarySaturation, if (darkTheme) 0.30f else 0.90f)
    val onSecondaryContainer = accentColor(hsl.hue, secondarySaturation, if (darkTheme) 0.90f else 0.12f)
    val tertiaryHue = hsl.hue + 60f
    val tertiary = accentColor(tertiaryHue, hsl.saturation, if (darkTheme) 0.78f else 0.40f)
    val tertiaryContainer = accentColor(tertiaryHue, hsl.saturation, if (darkTheme) 0.30f else 0.90f)
    val onTertiaryContainer = accentColor(tertiaryHue, hsl.saturation, if (darkTheme) 0.90f else 0.12f)

    return base.copy(
        primary = primary,
        onPrimary = contrastingContent(primary, hsl.hue, hsl.saturation),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = contrastingContent(secondary, hsl.hue, secondarySaturation),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = contrastingContent(tertiary, tertiaryHue, hsl.saturation),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        inversePrimary = accentColor(hsl.hue, hsl.saturation, if (darkTheme) 0.40f else 0.80f),
        surfaceTint = primary
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
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val customColorScheme = remember(customColorArgb, darkTheme, baseColorScheme) {
        customColorArgb?.let { colorArgb ->
            customAccentColorScheme(
                base = baseColorScheme,
                seed = Color(colorArgb),
                darkTheme = darkTheme
            )
        }
    }
    val colorScheme = customColorScheme ?: baseColorScheme

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
