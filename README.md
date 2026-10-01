# Juggluco

![Juggluco](valuemmolL.png)

A continuous glucose monitor client for Android phones and Wear OS watches.

Juggluco reads glucose values straight from your CGM sensor over Bluetooth and
turns them into something you can actually work with: a real graph, real
statistics, a full treatment logbook, and alerts that behave the way you asked
them to.

This repository is a fork of [j-kaltes/Juggluco](https://github.com/j-kaltes/Juggluco),
built around a from-scratch **Jetpack Compose + Material 3** user interface.

---

## What this fork changes

The upstream project established the sensor protocol work — talking to Libre,
Dexcom and SiBionics hardware, the native algorithms behind calibration, the
xDrip-compatible integrations. This fork rebuilds the entire interface layer on
top of it and reworks several subsystems.

### Portrait mode, done properly

Upstream Juggluco is a landscape-first app; the glucose curve gets the space and
the rest is arranged around it. Here, portrait is a first-class layout rather
than a squeezed-up landscape screen.

- The Glucose tab builds its own layout from `BoxWithConstraints`: landscape puts
  the current reading, stats and graph side by side; portrait stacks them into a
  natural vertical scroll — reading, targets, graph, statistics, logbook.
- Navigation adapts too. Portrait gets a top app bar and a bottom navigation bar;
  landscape swaps the bar for a `NavigationRail` and drops the redundant top bar.
- Nothing is locked to an orientation. `MainActivity` is `unspecified` and
  `resizeableActivity`, so the app follows the system, handles folding devices
  and works in split-screen and freeform windows.

### Material You, end to end

The UI is Jetpack Compose on Material 3 throughout, not a Material 2 skin.

- One seed colour derives the whole tonal palette
  (`ui/theme/Theme.kt`), with the error hue deliberately pinned so semantic
  meaning never shifts.
- Theme mode is a three-way choice — System, Light, Dark — plus a custom accent
  colour picker. On Android 12+ the default is **Material You dynamic colour**,
  so the app takes on the user's wallpaper palette. Your choice of custom colour
  overrides that.
- Glucose range hues (very low → very high) and logbook type hues (bolus,
  carbs, basal, finger-prick) live in their own dark-mode-adaptive tokens,
  `LocalClinicalColors` and `LocalLogbookColors`. They use `container` /
  `onContainer` pairs rather than fixed vibrant colours, so nothing glares in
  dark mode and event markers can never be mistaken for a reading's status.
- Widgets and the notification draw their colours from the *same*
  `jugglucoColorScheme()` function, so what you picked in the app is what you
  get on your home screen.

### More plots, more insight

Every plot and metric below is real and recomputed, not decoration.

| Where | What you get |
|---|---|
| **Glucose tab** | Interactive graph with pan, zoom and a spline curve (or a per-pixel min/max envelope where points can't be resolved). Two sensors draw as interleaved curves. |
| **Glucose tab** | Stats card: average, GMI, min, max, and the time-in-range split. |
| **Stats tab** | Clinical KPI grid, a full time-in-range breakdown, and the **AGP** — ambulatory glucose profile, with p10/p25/p50/p75/p90 bands, median line, IQR band and outer range across 24 hourly buckets. |
| **Notification** | Sparkline when collapsed, a larger configurable graph plus a time-in-range bar and statistics row when expanded. |
| **Wear OS** | Mini graph on the watch home screen and a full interactive history graph. |
| **Home screen** | Trend-graph, dial and time-in-range widgets. |
| **Full-screen alert** | Sparkline of recent history against the threshold that triggered. |

Statistics are time-weighted rather than a naive average over samples, with a
15-minute maximum gap so a sensor pause does not distort the result. Periods
range from 1 day to 180, plus custom windows.

The graph has toggleable layers — raw and calibrated streams, scans, history,
insulin amounts, meals and alert lines — plus search across readings and log
entries with match-to-match navigation, and a fullscreen mode.

### A rebuilt alarm system

Alerts used to be a pair of threshold numbers. They are now an engine of
independent, per-rule `AlertRule` objects, each configurable on its own.

- **Kinds:** `LOW`, `HIGH`, `FALLING`, `RISING`, `SIGNAL_LOSS`, `REMINDER`.
- **Triggers:** threshold in mg/dL or mmol/L, optional **forecast minutes ahead**
  (predictive alerts), rate of change in mg/dL/min, minutes of signal loss, and
  an option to skip the alert when you are already recovering.
- **Schedule:** pick specific days of the week and an all-day or time-window
  schedule. Windows may wrap past midnight, so an overnight low alert works
  without a special case.
- **Sound:** output stream (alarm / notification / media / silent), a custom
  sound URI, volume, a delay before it fires, a **ramp-up** duration to fade in,
  and an override-Do-Not-Disturb switch.
- **Vibration:** off/on, intensity, a delay, and eight built-in patterns (urgent,
  pulse, gentle, heartbeat, rapid, wave, SOS, short) or a **custom waveform**
  you type in as `on,off,on` milliseconds.
- **Behaviour:** repeat interval while the condition persists, how long playback
  lasts, flash the torch, speak the value aloud, and stop automatically once
  the condition clears.
- **Priority:** when several rules match at once, a fixed priority order decides
  the winner — a falling low beats a high, a signal loss outranks both, and a
  reminder outranks all of them.

Globally you control the master switch, snooze-everything-until, the snooze
durations offered, whether sound plays on this device, whether the watch uses
your phone's alerts, whether alerts are mirrored to the watch, and whether
full-screen alerts appear while you are already using the phone.

Alerts are evaluated on **both** devices by the same `AlertEngine`, so a rule set
behaves identically on phone and watch. Native alarm codes now only drive the
value-available chime — they no longer decide what alerts you.

### Full-screen alarms

When an alert is serious enough, it takes over the screen.

- The phone shows a `AlertActivity` with `showWhenLocked`, `turnScreenOn` and
  keep-screen-on: the alert's name, the value at 88sp with a trend chip, or the
  minutes of signal lost, a sparkline of recent history against the threshold,
  your custom detail text, up to three snooze buttons of your chosen durations,
  and Dismiss.
- The watch gets its own `WearAlertActivity` with Dismiss, Taken and Skip.
- Both are opt-in per rule and per device, and the app links you straight to the
  system "allow full-screen alerts" permission when it is missing.

### Medication reminders

Medication is not a glucose alert, so it is no longer modelled as one.
`ReminderSpec` is a first-class type with a dedicated scheduler.

- Any dose text and note, and **multiple times per day** from one reminder.
- Repeats by day of the week, every N days from a start date, or once only.
- A maximum repeat count, or repeat until taken.
- Snooze, skip and taken actions, each of which can be logged straight into the
  book.
- **Logbook linkage:** a dose you already logged within the look-back window
  suppresses the reminder, and confirming a reminder can write the dose into the
  book for you.
- Optional full-screen presentation, spoken announcement and a test button.
- Reminders sync to the watch, where the alert screen offers Taken and Skip.

Existing numeric alarms are imported once, so nothing configured in the old
version is lost.

### A Wear OS app you can actually use

The watch app is a full app, not a complication host.

Eight swipeable screens: **home** (hero reading, two-hour mini graph, alert
state, action buttons), **graph** (interactive history), **quick log**, **sensors**
(including NFC scan and phone/carrier connection status), **settings**,
**appearance** (colour, display, 24-hour clock, NFC sound), **alerts** with an
alert editor, and the full-screen alert activity.

Quick log keeps a dial-based entry screen — carbs, bolus, basal and blood checks
behind a semicircle category picker — tinted with the same type colours as the
phone and starting from the amount you logged last.

Beyond that: 15 watch-face complication data sources (value, arrow, value plus
arrow, monotonic variants, timestamps, icons), and a full watch face with its own
configuration activity.

### A better watch connection

Connecting the watch is treated as a subsystem with its own health and
diagnostics, not a hidden setting.

- **Per-watch direct sensor connection.** Let a watch pair with the sensor
  itself instead of relaying through the phone, toggled per device.
- **Transport selection.** Wear OS messages, automatic, TCP or direct BLE, with
  a one-time migration off the old automatic transport that was prone to silent
  failures.
- **Connection health.** Each watch shows Connected or Registered, its node ID
  and IP addresses, and the app refreshes automatically as nodes come and go.
  Actions for Sync, Init app and reset to defaults sit alongside a diagnostics
  section.
- **Alert and reminder sync** over a dedicated `/alerts` message path, carrying
  ring, stop, snooze, snooze-all and reminder events plus the phone's rule list.
  Received events are applied without being echoed back, and duplicates are
  dropped by event ID.
- **Mirror** provides a direct Bluetooth link between phone and watch, with a
  TURN server for connections that need relaying, shared passwords, ICE
  candidate management and QR quick-pair codes.

### Notifications, status bar and Android 16

The notification is drawn by the same renderer as the widgets, so the value,
arrow and graph in your shade look identical to your home screen. It shows the
value, trend arrow, delta over the reading interval and a sparkline collapsed;
expanded adds a larger graph, a time-in-range bar and a statistics row. Every
element is configurable — font, text scale, range colours, graph hours and
height, target band, time axis, which stats and over what period. It redraws
itself when the system theme flips.

**Status bar icons** are drawn as alpha-only glyphs — value, arrow, value plus
arrow, delta — centred on the actual outline of the digits rather than in a box.
Stale readings are dimmed and struck through. Up to two additional icons are
carried on silent local-only notifications, with adjustable scale and weight.

**Android 16 Live Update** is supported and opt-in. On API 36 devices that allow
promoted notifications, the drawn content is replaced by a system template: the
value and arrow as the title, delta and time-in-range as the text, a
`ProgressStyle` range gauge with a marker at your current value, a chip icon with
short critical text, and the promoted-ongoing request. Updates are applied in
place rather than cancel-and-repost, so the chip never blinks. With the setting
off, or on a device that cannot promote, it falls back to the drawn notification.

### Everything else the app can do

- **Logbook** — bolus, basal, carbs, meals, finger-pricks and custom entries.
  Carbs and bolus are entered side by side and saved together at one timestamp.
  One full-screen editor handles new entries, edits and deletes; rows and graph
  markers open it on tap. Quick values, the usual basal dose and note suggestions
  are computed from **your own history**, never hardcoded, so they get better the
  more you log. Meals compose from a custom ingredient database.
- **Five home screen widgets** — Minimal, Compact, Trend graph, Dial and Time in
  range, each a separate widget so launchers list them individually, with
  per-widget background, shape, content colour, font, text scale, range colours
  and graph/stat periods. All are drawn, and the configuration screen previews
  with the same renderer, so preview and home screen always match. The original
  widget remains available as "Classic".
- **Floating glucose** — an overlay above other apps, with opacity, position,
  touch-through and display-time controls.
- **Sensors screen** — current sensor state, starting a new sensor by NFC or by
  photographing a data matrix, and per-sensor history with a lifetime gauge,
  expected end date, MAC, generation, calibration status and diagnostics.
- **Integrations** — Nightscout upload, LibreView upload with treatment mapping,
  Health Connect blood-glucose records, xDrip+-compatible broadcasts, LibreLinkUp
  broadcasts, an xDrip-compatible web server with an xDrip web report, file
  export, and phone-to-phone or phone-to-watch transfer over local network,
  internet or QR/ICE codes.
- **Garmin** — Libre 3 direct support with provisioning, status, configuration
  and shortcut screens, plus Gadgetbridge and other watch targets.
- **19 languages** — English, Dutch, German, French, Italian, Spanish, Portuguese,
  Polish, Russian, Ukrainian, Belarusian, Swedish, Turkish, Arabic, Uzbek, Hindi,
  Chinese, Japanese and Korean, with an in-app language picker.

---

## Supported hardware

**Sensors**

- FreeStyle Libre 1, Libre 2 (Europe and US) and Libre 3 / 3+ — direct BLE
  streaming and NFC scans
- Dexcom G7 and Dexcom ONE+ — direct Bluetooth Low Energy
- SiBionics GS1 and GS3 — continuous readings and Bluetooth telemetry
- Accu-Chek SmartGuide, CareSens Air, Aidex X

**Other**

- NovoPen 6 and NovoPen Echo Plus insulin pen scanning
- Bluetooth finger-prick glucose meters, including Contour Next — off by
  default, enabled from Settings → Connectivity, since a meter is a rarity next
  to a CGM sensor

Sensor support is partly compile-time gated by product flavor, so a given build
contains only the protocol implementations you asked for.

---

## Getting started

Scan the sensor with Juggluco, then wait — the first reading takes two to ten
minutes to arrive over Bluetooth. To keep it flowing, force-stop apps that have
used the sensor before, allow Juggluco background activity, exempt it from
battery optimisation, and leave its notification visible.

**Libre 3 and Abbott's own app.** To take over a Libre 3 from the Libre 3-only
app, enter the same LibreView account under Settings → Exchange data →
Libreview and press *Get account ID*, then scan. If you activate the sensor with
Juggluco itself, any number will do. Libre 3 sensors can still be used with
Abbott's app afterwards, but you have to stop Juggluco, rescan with Abbott's app,
and rescan again when you return. European Libre 2 sensors can be read by both
apps simultaneously, which sometimes causes connection trouble.

**Photo-activated sensors.** Photograph the data matrix for Dexcom G7/ONE+, the
package for SiBionics GS1 and Aidex X, the inner package for CareSens Air, and
the blue cap for Accu-Chek SmartGuide (enter the PIN when asked). Note that
CareSens Air sensors can only be switched to once.

**Wear OS.** Let the phone scan the sensor first, then send it to the watch. Keep
Bluetooth on for both devices and Wi-Fi on for faster transfer. Once initialised,
the watch can take over the sensor connection directly.

---

## Building

```bash
git clone --recurse-submodules https://github.com/L3-N0X/Juggluco.git
cd Juggluco
```

Some native libraries are not in the repository. Unzip a release APK for the
architecture you need from the
[download page](https://www.juggluco.nl/Juggluco/download.html) and place them as
follows.

`libcalibrat2.so` and/or `libcalibrate.so` from the APK's `lib/*` into the
matching directory in:

```
./Common/src/main/jniLibs/armeabi-v7a/
./Common/src/main/jniLibs/arm64-v8a/
./Common/src/main/jniLibs/x86/
./Common/src/main/jniLibs/x86_64/
```

`libCALCULATION.so`, `libnative-algorithm-jni-v116A.so`,
`libnative-algorithm-v1_1_6A.so`, `libnative-sensitivity-v110.so`,
`libnative-algorithm-jni-v115G.so`, `libnative-algorithm-v1_1_5G.so`,
`libnative-encrypy-decrypt-v110.so` and `libnative-struct2json.so` into:

```
./Common/src/mobileSi/jniLibs/armeabi-v7a/
./Common/src/mobileSi/jniLibs/arm64-v8a/
```

Then build the default development APK:

```bash
./build.sh              # or ./debug.sh
```

See [BUILDING.md](BUILDING.md) for all available variants, direct-to-device
installs, and the JDK / SDK / CMake / NDK requirements.

---

## Credits and licence

Juggluco is based on [j-kaltes/Juggluco](https://github.com/j-kaltes/Juggluco),
itself drawing on xDrip+ and other open CGM work. Licensed under the
[GPL-3.0](LICENSE.txt). This is not a medical device and not a substitute for
professional advice. Use it at your own risk.