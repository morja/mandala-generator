#!/bin/sh
# Builds the benchmark for the booted simulator, runs it and prints the SPIKE3 lines.
# Usage: run-sim.sh [Release|Debug] [app arguments, e.g. -gcTarget 64 -cases worst]
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
config="${1:-Release}"
[ $# -gt 0 ] && shift
xcodebuild -project "$here/SpikeBench.xcodeproj" -scheme SpikeBench -configuration "$config" \
    -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath "$here/build/dd" -quiet build
xcrun simctl install booted "$here/build/dd/Build/Products/$config-iphonesimulator/SpikeBench.app"
xcrun simctl launch --console-pty --terminate-running-process booted one.tearoom.dahlia.spike3 -autorun -exit "$@" 2>&1 | grep --line-buffered -E "${SPIKE3_FILTER:-SPIKE3}"
