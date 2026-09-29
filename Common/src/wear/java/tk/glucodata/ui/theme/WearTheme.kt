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

/**
 * A custom watch accent is stored as a single perceptual seed (OKLCH hue and chroma) instead of a
 * handful of hand picked hex values. Every accent role is then read off a Material 3 tonal ramp at
 * the same tones the dynamic scheme uses, so a preset behaves exactly like a wallpaper derived
 * scheme: [MaterialTheme] components keep working, tonal buttons sit below filled buttons instead
 * of duplicating them, and the containers stay muted enough to read as cards.
 */
private data class WearAccentSeed(val hue: Float, val chroma: Float)

private fun wearAccentSeed(preset: WearColorPreset): WearAccentSeed? = when (preset) {
    WearColorPreset.WATCH_DEFAULT -> null
    WearColorPreset.BLUE -> WearAccentSeed(hue = 250f, chroma = 0.125f)
    WearColorPreset.TEAL -> WearAccentSeed(hue = 195f, chroma = 0.095f)
    WearColorPreset.PURPLE -> WearAccentSeed(hue = 305f, chroma = 0.125f)
    WearColorPreset.ORANGE -> WearAccentSeed(hue = 65f, chroma = 0.14f)
    WearColorPreset.GREEN -> WearAccentSeed(hue = 145f, chroma = 0.13f)
    WearColorPreset.PINK -> WearAccentSeed(hue = 8f, chroma = 0.12f)
}

private fun linearToSrgb(value: Float): Float =
    if (value <= 0.0031308f) value * 12.92f else 1.055f * Math.pow(value.toDouble(), 1.0 / 2.4).toFloat() - 0.055f

/** Oklab to linear sRGB, by Ottosson's matrices. */
private fun oklabToLinearRgb(lightness: Float, a: Float, b: Float): FloatArray {
    val l = lightness + 0.3963377774f * a + 0.2158037573f * b
    val m = lightness - 0.1055613458f * a - 0.0638541728f * b
    val s = lightness - 0.0894841775f * a - 1.2914855480f * b
    val l3 = l * l * l
    val m3 = m * m * m
    val s3 = s * s * s
    return floatArrayOf(
        4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3,
        -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3,
        -0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3
    )
}

private fun inGamut(rgb: FloatArray): Boolean =
    rgb.all { it >= -0.0001f && it <= 1.0001f }

/**
 * Resolves one OKLCH coordinate to sRGB, reducing chroma until the colour fits the display gamut.
 * That is what keeps dark container tones from clipping into flat, oversaturated blocks.
 */
private fun oklch(lightness: Float, chroma: Float, hueDegrees: Float): Color {
    val radians = Math.toRadians(hueDegrees.toDouble())
    val cos = Math.cos(radians).toFloat()
    val sin = Math.sin(radians).toFloat()
    var resolved = oklabToLinearRgb(lightness, chroma * cos, chroma * sin)
    if (!inGamut(resolved)) {
        var low = 0f
        var high = chroma
        repeat(16) {
            val mid = (low + high) / 2f
            val candidate = oklabToLinearRgb(lightness, mid * cos, mid * sin)
            if (inGamut(candidate)) {
                low = mid
                resolved = candidate
            } else {
                high = mid
            }
        }
        resolved = oklabToLinearRgb(lightness, low * cos, low * sin)
    }
    return Color(
        red = linearToSrgb(resolved[0]).coerceIn(0f, 1f),
        green = linearToSrgb(resolved[1]).coerceIn(0f, 1f),
        blue = linearToSrgb(resolved[2]).coerceIn(0f, 1f),
        alpha = 1f
    )
}

/** One tone off an accent ramp, 0 = black through 100 = white. */
private fun tone(hue: Float, chroma: Float, tone: Float): Color = oklch(tone / 100f, chroma, hue)

private fun ColorScheme.withAccent(seed: WearAccentSeed): ColorScheme {
    val hue = seed.hue
    val primaryChroma = seed.chroma
    val secondaryChroma = (seed.chroma / 3f).coerceAtLeast(0.012f)
    val tertiaryHue = (hue + 60f) % 360f
    val tertiaryChroma = (seed.chroma / 2f).coerceAtLeast(0.012f)
    return copy(
        primary = tone(hue, primaryChroma, 80f),
        onPrimary = tone(hue, primaryChroma, 20f),
        primaryDim = tone(hue, primaryChroma, 60f),
        primaryContainer = tone(hue, primaryChroma, 30f),
        onPrimaryContainer = tone(hue, primaryChroma, 90f),

        secondary = tone(hue, secondaryChroma, 80f),
        onSecondary = tone(hue, secondaryChroma, 20f),
        secondaryDim = tone(hue, secondaryChroma, 60f),
        secondaryContainer = tone(hue, secondaryChroma, 30f),
        onSecondaryContainer = tone(hue, secondaryChroma, 90f),

        tertiary = tone(tertiaryHue, tertiaryChroma, 80f),
        onTertiary = tone(tertiaryHue, tertiaryChroma, 20f),
        tertiaryDim = tone(tertiaryHue, tertiaryChroma, 60f),
        tertiaryContainer = tone(tertiaryHue, tertiaryChroma, 30f),
        onTertiaryContainer = tone(tertiaryHue, tertiaryChroma, 90f)
    )
}

/**
 * The colour a preset would apply, for the appearance picker swatch. T80 of the primary ramp is the
 * same value the scheme ends up with, so the dot previews the real result; `WATCH_DEFAULT` falls
 * back to whatever the current primary already is.
 */
fun wearAccentColor(preset: WearColorPreset, current: Color): Color =
    wearAccentSeed(preset)?.let { tone(it.hue, it.chroma, 80f) } ?: current

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
    val colorScheme = wearAccentSeed(colorPreset)?.let(baseColorScheme::withAccent) ?: baseColorScheme

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
