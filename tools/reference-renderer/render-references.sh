#!/bin/sh
# Builds a command-line build of the macOS renderer from the Mac app's sources and renders
# fixtures/reference/*.png from fixtures/settings/*.json. macOS only.
# Usage: tools/reference-renderer/render-references.sh [path to MandalaGenerator sources]
set -eu
src="${1:-../Mandala-Generator/MandalaGenerator/Sources/MandalaGenerator}"
here="$(dirname "$0")"
work="build/reference-renderer"
mkdir -p "$work/src"
find "$src" -name '*.swift' ! -name main.swift -exec cp {} "$work/src/" \;
cp "$here/main.swift" "$work/src/main.swift"
swiftc -O -module-name MandalaRef -o "$work/mandala-ref" "$work"/src/*.swift
echo "Cores on this Mac: $(sysctl -n hw.activecpu) (update macCores in ParityTest if it differs)"
for f in fixtures/settings/*.json; do
    name="$(basename "$f" .json)"
    "$work/mandala-ref" "$f" "fixtures/reference/$name.png"
done
