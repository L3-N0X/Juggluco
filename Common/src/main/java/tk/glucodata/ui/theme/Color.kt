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
    // Warning tier (Level 1 deviation: Low & High share unified accessible amber token)
    low = Color(0xFFB45309), // Warm amber
    lowContainer = Color(0xFFFEF3C7),
    onLowContainer = Color(0xFF78350F),
    high = Color(0xFFB45309), // Warm amber (matching opposite warning area)
    highContainer = Color(0xFFFEF3C7),
    onHighContainer = Color(0xFF78350F),
    // Critical tier (Level 2 deviation: Very Low & Very High share unified accessible crimson token)
    veryLow = Color(0xFFBA1A1A), // Deep vibrant crimson
    veryLowContainer = Color(0xFFFFDAD6),
    onVeryLowContainer = Color(0xFF410002),
    veryHigh = Color(0xFFBA1A1A), // Deep vibrant crimson (matching opposite critical area)
    veryHighContainer = Color(0xFFFFDAD6),
    onVeryHighContainer = Color(0xFF410002),
    targetRangeShade = Color(0x142E6B4F),
    graphGrid = Color(0x1870787D)
)

val DarkClinicalColors = ClinicalColors(
    inRange = Color(0xFF7CB69D), // Soft muted sage, never neon
    inRangeContainer = Color(0xFF1B382B),
    onInRangeContainer = Color(0xFFD1E8DC),
    // Warning tier (Level 1 deviation: Low & High share unified radiant amber token)
    low = Color(0xFFF59E0B), // Warm radiant amber
    lowContainer = Color(0xFF452C06),
    onLowContainer = Color(0xFFFDE68A),
    high = Color(0xFFF59E0B), // Warm radiant amber (matching opposite warning area)
    highContainer = Color(0xFF452C06),
    onHighContainer = Color(0xFFFDE68A),
    // Critical tier (Level 2 deviation: Very Low & Very High share unified crisp coral red token)
    veryLow = Color(0xFFF87171), // Crisp coral red
    veryLowContainer = Color(0xFF4C1818),
    onVeryLowContainer = Color(0xFFFECACA),
    veryHigh = Color(0xFFF87171), // Crisp coral red (matching opposite critical area)
    veryHighContainer = Color(0xFF4C1818),
    onVeryHighContainer = Color(0xFFFECACA),
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
        LogType.CUSTOM -> note
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

// Type palette. Each type owns a hue that none of the glucose range colours use (sage, amber,
// coral), so an event marker is never mistaken for a reading's status: blue for the bolus and a
// complementary tangerine for carbs - the pair logged together most often - violet for the slow
// basal insulin and teal for the occasional finger-prick.
val LightLogbookColors = LogbookColors(
    bolus = LogTypeColors(
        primary = Color(0xFF2F62D6), // Azure
        container = Color(0xFFDDE6FF),
        onContainer = Color(0xFF0B245E)
    ),
    basal = LogTypeColors(
        primary = Color(0xFF7443D1), // Violet
        container = Color(0xFFEDE3FF),
        onContainer = Color(0xFF2B1162)
    ),
    carbs = LogTypeColors(
        primary = Color(0xFFC2410C), // Tangerine
        container = Color(0xFFFFE4D4),
        onContainer = Color(0xFF571B02)
    ),
    bloodGlucose = LogTypeColors(
        primary = Color(0xFF0B7A73), // Teal
        container = Color(0xFFCDF1EC),
        onContainer = Color(0xFF033A36)
    ),
    note = LogTypeColors(
        primary = Color(0xFF5E6470), // Graphite
        container = Color(0xFFE6E8EE),
        onContainer = Color(0xFF22262E)
    )
)

val DarkLogbookColors = LogbookColors(
    bolus = LogTypeColors(
        primary = Color(0xFF8DB2FF),
        container = Color(0xFF1B2F5C),
        onContainer = Color(0xFFD9E3FF)
    ),
    basal = LogTypeColors(
        primary = Color(0xFFC4A8FF),
        container = Color(0xFF34245E),
        onContainer = Color(0xFFEBDDFF)
    ),
    carbs = LogTypeColors(
        primary = Color(0xFFFFA66E),
        container = Color(0xFF4D2511),
        onContainer = Color(0xFFFFDCC8)
    ),
    bloodGlucose = LogTypeColors(
        primary = Color(0xFF5BD5C8),
        container = Color(0xFF113E3A),
        onContainer = Color(0xFFC4F3EC)
    ),
    note = LogTypeColors(
        primary = Color(0xFFB9BEC9),
        container = Color(0xFF2E323A),
        onContainer = Color(0xFFE2E5EC)
    )
)
