package tk.glucodata.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Primary & Brand Colors (Medical / Clean Tech feel)
val PrimaryLight = Color(0xFF006688)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFC2E8FF)
val OnPrimaryContainerLight = Color(0xFF001E2B)

val SecondaryLight = Color(0xFF4E616D)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFD1E5F3)
val OnSecondaryContainerLight = Color(0xFF0B1D28)

val TertiaryLight = Color(0xFF605A7D)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFE6DEFF)
val OnTertiaryContainerLight = Color(0xFF1D1736)

val BackgroundLight = Color(0xFFF6FAFD)
val OnBackgroundLight = Color(0xFF181C1F)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF181C1F)
val SurfaceVariantLight = Color(0xFFDCE4E9)
val OnSurfaceVariantLight = Color(0xFF40484D)
val OutlineLight = Color(0xFF70787D)
val OutlineVariantLight = Color(0xFFC0C8CD)

// Dark Theme Colors
val PrimaryDark = Color(0xFF75D1FF)
val OnPrimaryDark = Color(0xFF003548)
val PrimaryContainerDark = Color(0xFF004D67)
val OnPrimaryContainerDark = Color(0xFFC2E8FF)

val SecondaryDark = Color(0xFFB5C9D7)
val OnSecondaryDark = Color(0xFF20333E)
val SecondaryContainerDark = Color(0xFF374955)
val OnSecondaryContainerDark = Color(0xFFD1E5F3)

val TertiaryDark = Color(0xFFC9C1EA)
val OnTertiaryDark = Color(0xFF322C4C)
val TertiaryContainerDark = Color(0xFF494264)
val OnTertiaryContainerDark = Color(0xFFE6DEFF)

val BackgroundDark = Color(0xFF0F1417)
val OnBackgroundDark = Color(0xFFDFE3E6)
val SurfaceDark = Color(0xFF12171A)
val OnSurfaceDark = Color(0xFFDFE3E6)
val SurfaceVariantDark = Color(0xFF40484D)
val OnSurfaceVariantDark = Color(0xFFC0C8CD)
val OutlineDark = Color(0xFF8A9297)
val OutlineVariantDark = Color(0xFF40484D)

// Clinical Status Colors (Accessible, High Contrast, Colorblind-Friendly)
@Immutable
data class ClinicalColors(
    val inRange: Color,
    val inRangeContainer: Color,
    val onInRangeContainer: Color,
    val low: Color,
    val lowContainer: Color,
    val onLowContainer: Color,
    val veryLow: Color,
    val veryLowContainer: Color,
    val onVeryLowContainer: Color,
    val high: Color,
    val highContainer: Color,
    val onHighContainer: Color,
    val veryHigh: Color,
    val veryHighContainer: Color,
    val onVeryHighContainer: Color,
    val targetRangeShade: Color,
    val graphGrid: Color
)

val LightClinicalColors = ClinicalColors(
    inRange = Color(0xFF059669), // Emerald
    inRangeContainer = Color(0xFFD1FAE5),
    onInRangeContainer = Color(0xFF065F46),
    low = Color(0xFFD97706), // Amber
    lowContainer = Color(0xFFFEF3C7),
    onLowContainer = Color(0xFF92400E),
    veryLow = Color(0xFFDC2626), // Red
    veryLowContainer = Color(0xFFFEE2E2),
    onVeryLowContainer = Color(0xFF991B1B),
    high = Color(0xFFEA580C), // Orange
    highContainer = Color(0xFFFFEDD5),
    onHighContainer = Color(0xFF9A3412),
    veryHigh = Color(0xFFBE123C), // Rose
    veryHighContainer = Color(0xFFFFE4E6),
    onVeryHighContainer = Color(0xFF881337),
    targetRangeShade = Color(0x18059669),
    graphGrid = Color(0x2070787D)
)

val DarkClinicalColors = ClinicalColors(
    inRange = Color(0xFF34D399),
    inRangeContainer = Color(0xFF064E3B),
    onInRangeContainer = Color(0xFFA7F3D0),
    low = Color(0xFFFBBF24),
    lowContainer = Color(0xFF78350F),
    onLowContainer = Color(0xFFFDE68A),
    veryLow = Color(0xFFF87171),
    veryLowContainer = Color(0xFF7F1D1D),
    onVeryLowContainer = Color(0xFFFECACA),
    high = Color(0xFFFB923C),
    highContainer = Color(0xFF7C2D12),
    onHighContainer = Color(0xFFFED7AA),
    veryHigh = Color(0xFFFB7185),
    veryHighContainer = Color(0xFF881337),
    onVeryHighContainer = Color(0xFFFECDD3),
    targetRangeShade = Color(0x2834D399),
    graphGrid = Color(0x288A9297)
)
