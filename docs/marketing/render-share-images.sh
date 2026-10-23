#!/bin/sh
# Renders every variant and image set of MKT-2-share-images.html in English and German to
# share/<text set>/<variant>-<set>-<lang>.jpg with headless Google Chrome (the Inter font must be
# installed) and ImageMagick. Run from anywhere:
#   render-share-images.sh            # text set "friends" (texts/friends.js)
#   render-share-images.sh <name>     # text set texts/<name>.js
# The set counts below must match SETS in the HTML.
set -eu
cd "$(dirname "$0")"
text="${1:-friends}"
[ -f "texts/$text.js" ] || { echo "texts/$text.js not found" >&2; exit 1; }
chrome="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
page="file://$(pwd)/MKT-2-share-images.html"
mkdir -p "share/$text"
for v in portrait:1080x1350:5 story:1080x1920:4 landscape:1920x1080:3 square:1080x1080:3 mandala:1080x1350:3 wide:1920x1080:4 teaser:1080x1920:6; do
    name="${v%%:*}"; rest="${v#*:}"; size="${rest%%:*}"; count="${rest##*:}"
    i=1
    while [ "$i" -le "$count" ]; do
        for lang in en de; do
            out="share/$text/$name-$i-$lang"
            "$chrome" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
                --window-size="$(echo "$size" | tr x ,)" --virtual-time-budget=8000 \
                --screenshot="$out.png" "$page?v=$name&s=$i&lang=$lang&t=$text" >/dev/null 2>&1
            magick "$out.png" -quality 90 "$out.jpg" && rm "$out.png"
            echo "$out.jpg"
        done
        i=$((i + 1))
    done
done
