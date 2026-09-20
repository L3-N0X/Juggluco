# AI Agent Guidelines & Architecture for Juggluco

## Design & UX Vision
- **Modern, Polished Consumer Experience:** Juggluco's Compose UI prioritizes a clean, modern, and delightful UX using Material 3 principles. The app should feel like a premier modern Android application, explicitly avoiding stereotypical, dated "clinical" or "medical" aesthetics.
- **No Forced Medical/Clinical Colors:** Do not restrict color choices to clinical stereotypes or force medical color rules. Strive for harmonious, contemporary Material 3 color palettes with clear visual hierarchy, balanced contrast, and accessible typography.
- **First-Class Dark Theme Support:**
  - Never use hardcoded light/white backgrounds or fixed vibrant colors that look glaring or out of place in dark mode.
  - Pair status and category indicators using dynamic container/content color tokens (e.g., `container` and `onContainer`/`primary`).
  - Dark mode surfaces should use dark, tinted tonal elevations rather than harsh stark fills.
- **Logbook & Graph Aesthetics:**
  - Log events (Bolus, Basal, Carbs, Blood Glucose checks, Notes) use centralized, dark-mode adaptive theme tokens in `tk.glucodata.ui.theme.LogbookColors` (`MaterialTheme.logbookColors`).
  - Graph curves, markers, and axis elements must respect dynamic theme tokens and avoid visual clutter or overlapping labels.

---

## Technical Stack & Architecture
- **UI Framework:** Jetpack Compose with Material 3 (`androidx.compose.material3`).
- **Target Modules:** Android Mobile & Wear OS. Code shared across platforms resides primarily in `Common/src/main/java/tk/glucodata/ui/`.
- **State & Performance:**
  - Viewport state is decoupled between interactive gesture drawing and settled calculation windows (`rememberSettledWindow`).
  - Heavy metric recalculations (TIR, GMI, averages, log filtering) must run off the main thread on `Dispatchers.Default` to prevent frame drops or recomposition churn during panning/zooming.
- **Theme Tokens:**
  - Palette definitions: `Common/src/main/java/tk/glucodata/ui/theme/Color.kt`
  - CompositionLocals and theme wrapper: `Common/src/main/java/tk/glucodata/ui/theme/Theme.kt` (`LocalClinicalColors`, `LocalLogbookColors`, `MaterialTheme.logbookColors`).

---

## Verification & Build Commands
Before finalizing UI or logic changes, always verify both Mobile and Wear OS builds:
```bash
# Mobile target compilation
./gradlew compileMobileLibre3SiDexGoogleDebugSources

# Wear OS target compilation
./gradlew compileWearLibre3SiDexGoogleDebugSources
```
