# Building Juggluco

This document outlines how to build Juggluco, the available variants, and how to use the build helper script.

---

## Quick Start

To build the standard development APK (**`mobileLibre3SiDexNogoogleDebugdub`**):

```bash
./build.sh
```

Or use:

```bash
./debug.sh
```

The APK will be generated at:
```
Common/build/outputs/apk/mobileLibre3SiDexNogoogle/debugdub/Common-mobile-libre3-si-dex-nogoogle-debugdub.apk
```

---

## Build Script (`./build.sh`)

The `./build.sh` script automates finding the JDK, executing Gradle with the correct variant, and locating the produced APK.

### Common Targets

| Target | Command | Description |
|---|---|---|
| **Debugdub (Default)** | `./build.sh` or `./build.sh debugdub` | Mobile Libre3 + Sibionics + Dexcom without Google Play services, using the debugdub configuration (includes logging & debug symbols). |
| **Debug** | `./build.sh debug` | Mobile Libre3 + Sibionics + Dexcom (No Google, standard debug build). |
| **Google Debug** | `./build.sh google` | Mobile Libre3 + Sibionics + Dexcom with Google Play services (Google flavor, debug build). |
| **Release Log** | `./build.sh release` | Optimized release APK with logging enabled (`assembleMobileLibre3SiDexNogoogleReleaseLog`). |
| **Wear OS** | `./build.sh wear` | Wear OS watch app variant. |
| **Clean** | `./build.sh clean` | Cleans build artifacts (`./gradlew clean`). |
| **Custom** | `./build.sh <GradleTask>` | Runs any arbitrary Gradle task. |

---

## Installing Directly to a Device

If an Android device is connected via USB or wireless ADB:

```bash
./build.sh --install
```

Or manually install the APK after building:

```bash
adb install -r Common/build/outputs/apk/mobileLibre3SiDexNogoogle/debugdub/Common-mobile-libre3-si-dex-nogoogle-debugdub.apk
```

---

## Environment Requirements

- **Java**: OpenJDK 17 or higher (e.g. Temurin 17 at `~/.jdks/temurin-17`). `./build.sh` automatically checks and exports `JAVA_HOME`.
- **Android SDK**: Configured in `local.properties` (`sdk.dir=/home/lenox/Android/Sdk`).
- **CMake**: Version 4.1.2 or compatible in SDK (`cmake.dir=/home/lenox/Android/Sdk/cmake/4.1.2`).
- **NDK**: Version `30.0.16138531` (or matching version configured in `Common/build.gradle`).
