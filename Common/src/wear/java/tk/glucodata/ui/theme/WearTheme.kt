package tk.glucodata.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

private data class WearAccentColors(
    val primary: Color,
    val primaryDim: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val onPrimaryContainer: Color
)

private fun wearAccentColors(preset: WearColorPreset): WearAccentColors? = when (preset) {
    WearColorPreset.WATCH_DEFAULT -> null
    WearColorPreset.BLUE -> WearAccentColors(
        primary = Color(0xFF8CD3FF),
        primaryDim = Color(0xFF5ABFEF),
        primaryContainer = Color(0xFF004A70),
        onPrimary = Color(0xFF00344E),
        onPrimaryContainer = Color(0xFFC7E8FF)
    )
    WearColorPreset.TEAL -> WearAccentColors(
        primary = Color(0xFF82D5CB),
        primaryDim = Color(0xFF57B8AE),
        primaryContainer = Color(0xFF005048),
        onPrimary = Color(0xFF003732),
        onPrimaryContainer = Color(0xFFB9F2EA)
    )
    WearColorPreset.PURPLE -> WearAccentColors(
        primary = Color(0xFFD2BBFF),
        primaryDim = Color(0xFFB69DF8),
        primaryContainer = Color(0xFF523B7C),
        onPrimary = Color(0xFF2B1450),
        onPrimaryContainer = Color(0xFFEBDDFF)
    )
    WearColorPreset.ORANGE -> WearAccentColors(
        primary = Color(0xFFFFB77C),
        primaryDim = Color(0xFFE7934E),
        primaryContainer = Color(0xFF6B3900),
        onPrimary = Color(0xFF4A2600),
        onPrimaryContainer = Color(0xFFFFDCC2)
    )
    WearColorPreset.GREEN -> WearAccentColors(
        primary = Color(0xFFA6D388),
        primaryDim = Color(0xFF82B66A),
        primaryContainer = Color(0xFF315A22),
        onPrimary = Color(0xFF123800),
        onPrimaryContainer = Color(0xFFC2F0A8)
    )
    WearColorPreset.PINK -> WearAccentColors(
        primary = Color(0xFFFFB1C8),
        primaryDim = Color(0xFFEA8BA9),
        primaryContainer = Color(0xFF6B2942),
        onPrimary = Color(0xFF48001F),
        onPrimaryContainer = Color(0xFFFFD9E2)
    )
}

private fun ColorScheme.withAccent(colors: WearAccentColors): ColorScheme = copy(
    primary = colors.primary,
    primaryDim = colors.primaryDim,
    primaryContainer = colors.primaryContainer,
    onPrimary = colors.onPrimary,
    onPrimaryContainer = colors.onPrimaryContainer,
    secondary = colors.primary,
    secondaryDim = colors.primaryDim,
    secondaryContainer = colors.primaryContainer,
    onSecondary = colors.onPrimary,
    onSecondaryContainer = colors.onPrimaryContainer,
    tertiary = colors.primary,
    tertiaryDim = colors.primaryDim,
    tertiaryContainer = colors.primaryContainer,
    onTertiary = colors.onPrimary,
    onTertiaryContainer = colors.onPrimaryContainer
)

private val WearDarkColorScheme = ColorScheme(
    primary = PrimaryDark,
    primaryDim = Color(0xFF5ABFEF),
    primaryContainer = PrimaryContainerDark,
    onPrimary = OnPrimaryDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    secondaryDim = Color(0xFF9EAFBC),
    secondaryContainer = SecondaryContainerDark,
    onSecondary = OnSecondaryDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    tertiaryDim = Color(0xFFAFA7D0),
    tertiaryContainer = TertiaryContainerDark,
    onTertiary = OnTertiaryDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    surfaceContainerLow = Color(0xFF14191C),
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = Color(0xFF22282C),
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    background = Color.Black,
    onBackground = OnBackgroundDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF4C1818),
    onErrorContainer = Color(0xFFFECACA)
)

@Composable
fun WearJugglucoTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    WearThemePreferences.ensureLoaded(context)
    val colorPreset by WearThemePreferences.colorPreset.collectAsState()
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            try {
                dynamicColorScheme(context) ?: WearDarkColorScheme
            } catch (_: Throwable) {
                WearDarkColorScheme
            }
        }
        else -> WearDarkColorScheme
    }
    val colorScheme = wearAccentColors(colorPreset)?.let(baseColorScheme::withAccent) ?: baseColorScheme

    CompositionLocalProvider(
        LocalClinicalColors provides DarkClinicalColors,
        LocalLogbookColors provides DarkLogbookColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
