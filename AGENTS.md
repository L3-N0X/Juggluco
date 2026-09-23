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

## Alerts
- **Engine, not native levels:** glucose alerts are user-defined `AlertRule`s in `Common/src/main/java/tk/glucodata/alerts/`. `AlertEngine` evaluates every reading (hooked in `SuperGattCallback.dowithglucose`) and signal loss (`GlucoseAlarms.handlealarm`) on both phone and watch; the native alarm codes only still drive the value-available chime.
- **Playback:** `AlertPlayer` owns sound, vibration, notification and the full-screen alert (`AlertActivity` on the phone, `WearAlertActivity` on the watch). Stop alerts through `AlertPlayer.dismiss()/snooze()`, which also sync; `Notify.stopAllAlarms()` is the entry point for legacy user actions.
- **Phone ↔ watch sync:** `AlertSync` sends ring/stop/snooze events and the phone's alert list over the `/alerts` Wear message path. Received events are applied without being re-sent. Device-local settings (on/off, sound here, mirror alerts, use phone's alerts) are never overwritten by a sync.

---

## Screen Layout & Headers
- **Shared layout module:** `Common/src/main/java/tk/glucodata/ui/screens/ScreenLayout.kt` owns the spacing scale (`Gutter` / `CardPadding` 16.dp, `SectionSpacing`, `TopPadding`, `BottomPadding` 96.dp) plus `ScreenContent { }` (standard scrolling tab body) and `SectionTitle(...)`. Use these instead of ad-hoc dp values.
- **One title per screen:** the persistent app bar supplies it — `JugglucoApp`'s `TopAppBar` for every tab, `SettingsDetailScaffold` for detail screens. Never repeat the page title in the content, and leave descriptions out unless they earn their space.
- **Two padding levels, never three:** screen gutter → card padding. Group content inside a card with spacing or `HorizontalDivider`, not another padded/tinted container — nesting a third level is the "double indent" bug.
- **No cards for screen sections:** sections (stats blocks, the current sensor, calls to action) sit directly on the screen background, separated by spacing, `SectionTitle`s or `HorizontalDivider`s. Cards are only for repeated list items, filled with `ScreenLayout.cardContainerColor`.
- **No badges:** don't put status pills/chips next to titles or values. Show status as plain text or by tinting the value.
- **Gutters belong to the parent:** screen-level Columns apply the gutter; cards and sections (hero card, stats card, time range pills, graph, logbook) fill the width and carry no horizontal padding of their own.

---

## Verification & Build Commands
Before finalizing UI or logic changes, always verify both Mobile and Wear OS builds:
```bash
# Mobile target compilation
./gradlew compileMobileLibre3SiDexGoogleDebugSources

# Wear OS target compilation
./gradlew compileWearLibre3SiDexGoogleDebugSources
```
