#!/bin/sh
# Builds a command-line build of the macOS renderer from the Mac app's sources and renders
# fixtures/reference/*.png from fixtures/settings/*.json. macOS only.
# Runs in android/ from any folder; the sources default to the macOS app in ../mac.
# Usage: tools/reference-renderer/render-references.sh [--style-previews] [path to Mac sources]
set -eu
cd "$(dirname "$0")/../.."
mode="references"
if [ "${1:-}" = "--style-previews" ]; then
    mode="style-previews"
    shift
fi
src="${1:-../mac/Sources/MandalaGenerator}"
here="tools/reference-renderer"
work="build/reference-renderer"
mkdir -p "$work/src"
find "$src" -name '*.swift' ! -name main.swift -exec cp {} "$work/src/" \;
cp "$here/main.swift" "$work/src/main.swift"
swiftc -O -module-name MandalaRef -o "$work/mandala-ref" "$work"/src/*.swift
if [ "$mode" = "style-previews" ]; then
    "$work/mandala-ref" --style-previews "$src/Resources/StylePreviews"
    python3 - "$src/Resources/StylePreviews" <<'PYTHON'
from pathlib import Path
import re
import shutil
import sys

source = Path(sys.argv[1])
model = Path("core/src/main/kotlin/com/mathis/mandala/core/Model.kt").read_text().split("/** How a layer")[0]
styles = re.findall(r'@SerialName\("([^"]+)"\) ([A-Z_]+)\(', model)
target = Path("app/src/main/res/drawable-nodpi")
target.mkdir(exist_ok=True)
copied = 0
for raw, name in styles:
    # Styles built on Android first have no Mac preview yet; theirs stay as rendered by the core.
    if (source / (raw + ".png")).exists():
        shutil.copy(source / (raw + ".png"), target / ("style_preview_" + name.lower() + ".png"))
        copied += 1
print(f"Copied {copied} Mac previews to Android")
PYTHON
else
    for f in fixtures/settings/*.json; do
        name="$(basename "$f" .json)"
        "$work/mandala-ref" "$f" "fixtures/reference/$name.png"
    done
fi
