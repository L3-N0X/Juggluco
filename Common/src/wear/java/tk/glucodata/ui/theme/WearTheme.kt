package tk.glucodata.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

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
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            try {
                dynamicColorScheme(context) ?: WearDarkColorScheme
            } catch (_: Throwable) {
                WearDarkColorScheme
            }
        }
        else -> WearDarkColorScheme
    }

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
