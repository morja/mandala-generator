#!/bin/sh
# Renders every variant and image set of MKT-2-share-images.html in English and German to
# share/<text set>/<folder>/<file>.jpg with headless Google Chrome (the Inter font must be installed)
# and ImageMagick. Run from anywhere:
#   render-share-images.sh            # text set "friends" (texts/friends.js)
#   render-share-images.sh <name>     # text set texts/<name>.js
#   render-share-images.sh <name> <variant>   # only that variant (portrait, story, landscape, square, mandala, wide, teaser, wallpaper)
#   render-share-images.sh <name> proof       # proof run: only story sequence 1 in English (five images and its overview)
# The list of images and their file names comes from the HTML itself (?list=1), so SETS there is the
# only place to edit. Nothing is deleted: files of renamed or removed sets stay until you remove them.
# Ready-made story sequences (stories/story-<n>-<lang>/) follow the `stories` plan in the text set; each
# folder also gets 00-overview.jpg with all its images side by side, for comparing.
set -eu
cd "$(dirname "$0")"
text="${1:-friends}"
only="${2:-}"
[ -f "texts/$text.js" ] || { echo "texts/$text.js not found" >&2; exit 1; }
chrome="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
timeout="$(command -v gtimeout || command -v timeout)" || { echo "needs GNU timeout (brew install coreutils)" >&2; exit 1; }
page="file://$(pwd)/MKT-2-share-images.html"
list=$("$chrome" --headless=new --disable-gpu --dump-dom "$page?list=1" 2>/dev/null | grep -o 'IMG [^<]*' | cut -c5-)
[ -n "$list" ] || { echo "could not read the image list from the HTML" >&2; exit 1; }
echo "$list" | while read -r name i w h lang seq file; do
    if [ "$only" = "proof" ]; then case "$file" in stories/story-1-en/*) ;; *) continue ;; esac
    else [ -z "$only" ] || [ "$name" = "$only" ] || continue; fi
    out="share/$text/${file%.jpg}"
    mkdir -p "$(dirname "$out")"
    # Headless Chrome occasionally hangs; give each image 90 seconds, then skip it and say so.
    if "$timeout" 90 "$chrome" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
        --window-size="$w,$h" --virtual-time-budget=8000 \
        --screenshot="$out.png" "$page?v=$name&s=$i&lang=$lang&t=$text&seq=$seq" >/dev/null 2>&1 && [ -s "$out.png" ]; then
        magick "$out.png" -quality 90 "$out.jpg" && rm "$out.png"
        echo "$out.jpg"
    else
        rm -f "$out.png"; echo "FAILED $out.jpg (timeout or Chrome error), run again" >&2
    fi
done

# Overview sheet per story folder: all images of the sequence side by side.
for d in "share/$text"/stories/story-*; do
    [ -d "$d" ] || continue
    [ "$only" != "proof" ] || [ "$d" = "share/$text/stories/story-1-en" ] || continue
    overview="$d/00-overview.jpg"
    rm -f "$overview"
    magick montage "$d"/*.jpg -tile x1 -geometry 540x960+10+10 -background '#0a0908' -quality 88 "$overview"
    echo "$overview"
done
