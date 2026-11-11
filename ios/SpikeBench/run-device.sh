#!/bin/sh
# Builds the benchmark in Release for a connected iPhone (automatic signing), installs and runs it.
# Usage: run-device.sh <device id from `xcrun devicectl list devices`> [app arguments]
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
device="$1"
shift
xcodebuild -project "$here/SpikeBench.xcodeproj" -scheme SpikeBench -configuration Release -destination "id=$device" \
    -derivedDataPath "$here/build/dd" -allowProvisioningUpdates -quiet build
xcrun devicectl device install app --device "$device" "$here/build/dd/Build/Products/Release-iphoneos/SpikeBench.app" >/dev/null
xcrun devicectl device process launch --device "$device" --console --terminate-existing one.tearoom.dahlia.spike3 -- -autorun -exit "$@" 2>&1 \
    | grep --line-buffered -E "SPIKE3|ERROR|Locked"
