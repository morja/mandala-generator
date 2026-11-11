#!/bin/sh
# Builds the Kotlin core as an XCFramework and puts it where the Xcode project expects it.
# Usage: build-core.sh [release|debug] [extra Gradle arguments, e.g. -Pkotlin.native.binary.gc=cms]
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
kind="${1:-release}"
[ $# -gt 0 ] && shift
case "$kind" in
    release) task=assembleMandalaCoreReleaseXCFramework ;;
    debug) task=assembleMandalaCoreDebugXCFramework ;;
    *) echo "release or debug" >&2; exit 2 ;;
esac
(cd "$here/../../android" && ./gradlew --quiet ":core:$task" "$@")
rm -rf "$here/Frameworks/MandalaCore.xcframework"
mkdir -p "$here/Frameworks"
cp -R "$here/../../android/core/build/XCFrameworks/$kind/MandalaCore.xcframework" "$here/Frameworks/"
echo "MandalaCore.xcframework ($kind) in $here/Frameworks"
