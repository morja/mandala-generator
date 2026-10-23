#!/bin/sh
# Renders every variant and image set of MKT-2-share-images.html in English and German to
# share/<text set>/<folder>/<file>.jpg with headless Google Chrome (the Inter font must be installed)
# and ImageMagick. Run from anywhere:
#   render-share-images.sh            # text set "friends" (texts/friends.js)
#   render-share-images.sh <name>     # text set texts/<name>.js
# The list of images and their file names comes from the HTML itself (?list=1), so SETS there is the
# only place to edit. Nothing is deleted: files of renamed or removed sets stay until you remove them.
# Ready-made story sequences (stories/story-<n>-<lang>/) follow the `stories` plan in the text set.
set -eu
cd "$(dirname "$0")"
text="${1:-friends}"
[ -f "texts/$text.js" ] || { echo "texts/$text.js not found" >&2; exit 1; }
chrome="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
page="file://$(pwd)/MKT-2-share-images.html"
list=$("$chrome" --headless=new --disable-gpu --dump-dom "$page?list=1" 2>/dev/null | grep -o 'IMG [^<]*' | cut -c5-)
[ -n "$list" ] || { echo "could not read the image list from the HTML" >&2; exit 1; }
echo "$list" | while read -r name i w h lang seq file; do
    out="share/$text/${file%.jpg}"
    mkdir -p "$(dirname "$out")"
    "$chrome" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
        --window-size="$w,$h" --virtual-time-budget=8000 \
        --screenshot="$out.png" "$page?v=$name&s=$i&lang=$lang&t=$text&seq=$seq" >/dev/null 2>&1
    magick "$out.png" -quality 90 "$out.jpg" && rm "$out.png"
    echo "$out.jpg"
done
