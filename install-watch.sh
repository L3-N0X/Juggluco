#!/usr/bin/env bash
# ==============================================================================
# Juggluco Watch Quick Installer
# ==============================================================================
# Builds the Wear OS variant and installs it to a connected watch via adb.
#
# Usage:
#   ./install-watch.sh                  # Auto-detect watch or single device
#   ./install-watch.sh 192.168.1.50:5555 # Install to specific IP or serial
# ==============================================================================

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -n "$1" ]; then
    exec "$SCRIPT_DIR/build.sh" watch -s "$1"
else
    exec "$SCRIPT_DIR/build.sh" watch --install
fi
