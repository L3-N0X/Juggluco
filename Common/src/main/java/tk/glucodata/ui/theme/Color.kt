package tk.glucodata.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import tk.glucodata.ui.model.LogType

// Primary & Brand Colors (Modern Material 3 palette)
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

// Glucose Target Range Colors (Accessible, High Contrast)
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
    inRange = Color(0xFF2E6B4F), // Muted sage / forest green
    inRangeContainer = Color(0xFFE2EFE7),
    onInRangeContainer = Color(0xFF133825),
    low = Color(0xFFB45309), // Muted warm amber
    lowContainer = Color(0xFFFEF3C7),
    onLowContainer = Color(0xFF78350F),
    veryLow = Color(0xFFB91C1C), // Deep crimson
    veryLowContainer = Color(0xFFFEE2E2),
    onVeryLowContainer = Color(0xFF7F1D1D),
    high = Color(0xFFC2410C), // Muted burnt orange
    highContainer = Color(0xFFFFEDD5),
    onHighContainer = Color(0xFF7C2D12),
    veryHigh = Color(0xFF9F1239), // Muted wine / rose
    veryHighContainer = Color(0xFFFFE4E6),
    onVeryHighContainer = Color(0xFF701A31),
    targetRangeShade = Color(0x142E6B4F),
    graphGrid = Color(0x1870787D)
)

val DarkClinicalColors = ClinicalColors(
    inRange = Color(0xFF7CB69D), // Soft muted sage, never neon
    inRangeContainer = Color(0xFF1B382B),
    onInRangeContainer = Color(0xFFD1E8DC),
    low = Color(0xFFE0A34E), // Muted gold/amber
    lowContainer = Color(0xFF4A3315),
    onLowContainer = Color(0xFFFDE68A),
    veryLow = Color(0xFFE57373), // Soft muted coral
    veryLowContainer = Color(0xFF4C1D1D),
    onVeryLowContainer = Color(0xFFFECACA),
    high = Color(0xFFE08E55), // Muted soft orange
    highContainer = Color(0xFF4A2612),
    onHighContainer = Color(0xFFFED7AA),
    veryHigh = Color(0xFFE57388), // Soft muted rose
    veryHighContainer = Color(0xFF4C1625),
    onVeryHighContainer = Color(0xFFFECDD3),
    targetRangeShade = Color(0x1A7CB69D),
    graphGrid = Color(0x208A9297)
)

// Logbook Event & Tracking Color Tokens (Material 3 container pairs, dark-mode adaptive)
@Immutable
data class LogTypeColors(
    val primary: Color,
    val container: Color,
    val onContainer: Color
)

@Immutable
data class LogbookColors(
    val bolus: LogTypeColors,
    val basal: LogTypeColors,
    val carbs: LogTypeColors,
    val bloodGlucose: LogTypeColors,
    val note: LogTypeColors
) {
    fun forType(type: LogType): LogTypeColors = when (type) {
        LogType.RAPID_INSULIN -> bolus
        LogType.BASAL_INSULIN -> basal
        LogType.CARBS, LogType.MEAL -> carbs
        LogType.BLOOD_GLUCOSE -> bloodGlucose
        LogType.NOTE -> note
    }

    val rapidInsulin: Color get() = bolus.primary
    val rapidInsulinContainer: Color get() = bolus.container
    val onRapidInsulinContainer: Color get() = bolus.onContainer

    val basalInsulin: Color get() = basal.primary
    val basalInsulinContainer: Color get() = basal.container
    val onBasalInsulinContainer: Color get() = basal.onContainer

    val carbsPrimary: Color get() = carbs.primary
    val carbsContainer: Color get() = carbs.container
    val onCarbsContainer: Color get() = carbs.onContainer

    val bloodGlucosePrimary: Color get() = bloodGlucose.primary
    val bloodGlucoseContainer: Color get() = bloodGlucose.container
    val onBloodGlucoseContainer: Color get() = bloodGlucose.onContainer

    val notePrimary: Color get() = note.primary
    val noteContainer: Color get() = note.container
    val onNoteContainer: Color get() = note.onContainer

    fun colorFor(type: LogType): Color = forType(type).primary
    fun containerColorFor(type: LogType): Color = forType(type).container
    fun onContainerColorFor(type: LogType): Color = forType(type).onContainer
}

val LightLogbookColors = LogbookColors(
    bolus = LogTypeColors(
        primary = Color(0xFF0277BD), // Cerulean blue
        container = Color(0xFFE1F0F8),
        onContainer = Color(0xFF00384E)
    ),
    basal = LogTypeColors(
        primary = Color(0xFF564AB1), // Slate violet
        container = Color(0xFFECE8F8),
        onContainer = Color(0xFF2C2263)
    ),
    carbs = LogTypeColors(
        primary = Color(0xFFB45309), // Warm amber
        container = Color(0xFFFEF3C7),
        onContainer = Color(0xFF78350F)
    ),
    bloodGlucose = LogTypeColors(
        primary = Color(0xFFB91C1C), // Crimson
        container = Color(0xFFFEE2E2),
        onContainer = Color(0xFF7F1D1D)
    ),
    note = LogTypeColors(
        primary = Color(0xFF63567A), // Slate plum
        container = Color(0xFFEFEBF3),
        onContainer = Color(0xFF322846)
    )
)

val DarkLogbookColors = LogbookColors(
    bolus = LogTypeColors(
        primary = Color(0xFF67B5E8), // Soft sky blue, never blinding
        container = Color(0xFF12344D), // Dark slate blue container, blends into dark surface
        onContainer = Color(0xFFBAE6FD)
    ),
    basal = LogTypeColors(
        primary = Color(0xFFA594F9), // Soft muted periwinkle
        container = Color(0xFF2A244E), // Dark slate violet container
        onContainer = Color(0xFFE2DCFB)
    ),
    carbs = LogTypeColors(
        primary = Color(0xFFE0A34E), // Soft warm gold
        container = Color(0xFF452E10), // Dark warm amber container
        onContainer = Color(0xFFFDE68A)
    ),
    bloodGlucose = LogTypeColors(
        primary = Color(0xFFE57373), // Soft muted coral
        container = Color(0xFF4C1D24), // Dark wine container
        onContainer = Color(0xFFFECDD6)
    ),
    note = LogTypeColors(
        primary = Color(0xFFBFAFD9), // Soft muted mauve
        container = Color(0xFF332A42), // Dark slate mauve container
        onContainer = Color(0xFFEDE5F6)
    )
)
