#!/bin/sh
# Renders every variant of MKT-2-share-images.html in English and German to share/<variant>-<lang>.png
# with headless Google Chrome (the Inter font must be installed). Run from anywhere.
set -eu
cd "$(dirname "$0")"
chrome="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
page="file://$(pwd)/MKT-2-share-images.html"
mkdir -p share
for v in portrait:1080x1350 story:1080x1920 landscape:1920x1080 square:1080x1080 emblem:1080x1350; do
    name="${v%%:*}"; size="${v##*:}"
    for lang in en de; do
        "$chrome" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
            --window-size="$(echo "$size" | tr x ,)" --virtual-time-budget=8000 \
            --screenshot="share/$name-$lang.png" "$page?v=$name&lang=$lang" >/dev/null 2>&1
        echo "share/$name-$lang.png"
    done
done
