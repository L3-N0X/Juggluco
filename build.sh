#!/usr/bin/env bash
# ==============================================================================
# Juggluco Build Script
# ==============================================================================
# Quickly build, locate, and optionally install Juggluco APKs.
#
# Usage:
#   ./build.sh [target] [options]
#
# Examples:
#   ./build.sh                # Builds default: mobile Libre3 Si Dex Nogoogle Debugdub
#   ./build.sh --install      # Builds default and installs directly via adb
#   ./build.sh google         # Builds Google flavor (debug)
#   ./build.sh release        # Builds release log variant
#   ./build.sh clean          # Cleans build directory
#   ./build.sh --help         # Show help and all targets
# ==============================================================================

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ------------------------------------------------------------------------------
# Environment Setup
# ------------------------------------------------------------------------------
if [ -z "$JAVA_HOME" ] || [ ! -d "$JAVA_HOME" ]; then
    if [ -d "$HOME/.jdks/temurin-17" ]; then
        export JAVA_HOME="$HOME/.jdks/temurin-17"
    elif command -v java >/dev/null 2>&1; then
        JAVA_BIN="$(which java)"
        JAVA_REAL="$(readlink -f "$JAVA_BIN" 2>/dev/null || echo "$JAVA_BIN")"
        export JAVA_HOME="$(dirname "$(dirname "$JAVA_REAL")")"
    fi
fi

# Ensure Gradle wrapper exists
if [ ! -f "./gradlew" ]; then
    echo "Error: gradlew not found in $SCRIPT_DIR" >&2
    exit 1
fi
chmod +x ./gradlew

# ------------------------------------------------------------------------------
# Preset Definitions
# ------------------------------------------------------------------------------
DEFAULT_TARGET="debugdub"

show_help() {
    cat << 'EOF'
Juggluco Build Helper

Usage:
  ./build.sh [target] [options]

Targets:
  debugdub   (Default) Mobile Libre3 + Sibionics + Dexcom (No Google, Debugdub)
             APK: Common/build/outputs/apk/mobileLibre3SiDexNogoogle/debugdub/...
  debug      Mobile Libre3 + Sibionics + Dexcom (No Google, Debug)
             APK: Common/build/outputs/apk/mobileLibre3SiDexNogoogle/debug/...
  google     Mobile Libre3 + Sibionics + Dexcom (Google Play Services, Debug)
             APK: Common/build/outputs/apk/mobileLibre3SiDexGoogle/debug/...
  release    Mobile Libre3 + Sibionics + Dexcom (No Google, ReleaseLog)
             APK: Common/build/outputs/apk/mobileLibre3SiDexNogoogle/releaselog/...
  wear       Wear OS Libre3 + Sibionics + Dexcom (Google, Debug)
  clean      Runs Gradle clean
  <custom>   Any valid Gradle task name (e.g. assembleWearLibre3Release)

Options:
  -i, --install    Install the generated APK to a connected device via adb
  -h, --help       Show this help message

EOF
}

TARGET=""
INSTALL=false

for arg in "$@"; do
    case "$arg" in
        -h|--help)
            show_help
            exit 0
            ;;
        -i|--install)
            INSTALL=true
            ;;
        clean)
            TARGET="clean"
            ;;
        debugdub)
            TARGET="debugdub"
            ;;
        debug)
            TARGET="debug"
            ;;
        google|google-debug)
            TARGET="google"
            ;;
        release|releaselog)
            TARGET="release"
            ;;
        wear)
            TARGET="wear"
            ;;
        *)
            if [ -z "$TARGET" ]; then
                TARGET="$arg"
            else
                echo "Unknown option: $arg" >&2
                show_help
                exit 1
            fi
            ;;
    esac
done

TARGET="${TARGET:-$DEFAULT_TARGET}"

# Map friendly name to Gradle task and expected APK output pattern
case "$TARGET" in
    clean)
        echo "==> Cleaning project..."
        ./gradlew clean
        echo "==> Clean complete."
        exit 0
        ;;
    debugdub)
        GRADLE_TASK="assembleMobileLibre3SiDexNogoogleDebugdub"
        APK_SUBDIR="mobileLibre3SiDexNogoogle/debugdub"
        VARIANT_DESC="Mobile Libre3 Si Dex Nogoogle (Debugdub)"
        ;;
    debug)
        GRADLE_TASK="assembleMobileLibre3SiDexNogoogleDebug"
        APK_SUBDIR="mobileLibre3SiDexNogoogle/debug"
        VARIANT_DESC="Mobile Libre3 Si Dex Nogoogle (Debug)"
        ;;
    google)
        GRADLE_TASK="assembleMobileLibre3SiDexGoogleDebug"
        APK_SUBDIR="mobileLibre3SiDexGoogle/debug"
        VARIANT_DESC="Mobile Libre3 Si Dex Google (Debug)"
        ;;
    release)
        GRADLE_TASK="assembleMobileLibre3SiDexNogoogleReleaseLog"
        APK_SUBDIR="mobileLibre3SiDexNogoogle/releaselog"
        VARIANT_DESC="Mobile Libre3 Si Dex Nogoogle (ReleaseLog)"
        ;;
    wear)
        GRADLE_TASK="assembleWearLibre3SiDexGoogleDebug"
        APK_SUBDIR="wearLibre3SiDexGoogle/debug"
        VARIANT_DESC="Wear OS Libre3 Si Dex Google (Debug)"
        ;;
    *)
        GRADLE_TASK="$TARGET"
        APK_SUBDIR=""
        VARIANT_DESC="Custom task: $TARGET"
        ;;
esac

echo "=========================================================="
echo " Building: $VARIANT_DESC"
echo " Gradle Task: $GRADLE_TASK"
if [ -n "$JAVA_HOME" ]; then
    echo " Java Home:   $JAVA_HOME"
fi
echo "=========================================================="

START_TIME=$(date +%s)

./gradlew "$GRADLE_TASK"

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "=========================================================="
echo " Build successful! (took ${DURATION}s)"
echo "=========================================================="

APK_PATH=""
if [ -n "$APK_SUBDIR" ]; then
    TARGET_DIR="$SCRIPT_DIR/Common/build/outputs/apk/$APK_SUBDIR"
    if [ -d "$TARGET_DIR" ]; then
        APK_PATH=$(find "$TARGET_DIR" -maxdepth 1 -name "*.apk" ! -name "*unaligned*" | head -n 1)
    fi
fi

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
    # Fallback to the latest modified APK in build/outputs/apk
    APK_PATH=$(find "$SCRIPT_DIR/Common/build/outputs/apk" -name "*.apk" -type f -printf '%T@ %p\n' 2>/dev/null | sort -nr | head -n 1 | cut -d' ' -f2-)
fi

if [ -n "$APK_PATH" ] && [ -f "$APK_PATH" ]; then
    FILE_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
    echo " Output APK: $APK_PATH"
    echo " APK Size:   $FILE_SIZE"
    echo "=========================================================="

    if [ "$INSTALL" = true ]; then
        echo ""
        echo "==> Installing to device via adb..."
        if ! command -v adb >/dev/null 2>&1; then
            echo "Error: adb not found in PATH." >&2
            exit 1
        fi
        adb install -r "$APK_PATH"
        echo "==> Installed successfully!"
    else
        echo ""
        echo "To install to a connected device, run:"
        echo "  adb install -r \"$APK_PATH\""
        echo "Or run this script with --install:"
        echo "  ./build.sh --install"
    fi
else
    echo " Build finished, but output APK could not be automatically located."
fi
echo "=========================================================="
