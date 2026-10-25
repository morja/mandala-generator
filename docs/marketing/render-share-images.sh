#!/bin/sh
# Renders the share images of MKT-2-share-images.html with headless Google Chrome (the Inter font must be
# installed) and ImageMagick. Run from anywhere:
#   render-share-images.sh                     # text set "default": the story sequences into a new dated folder
#   render-share-images.sh <name>              # same for text set texts/<name>.js
#   render-share-images.sh proof               # proof run: only sequence 1, into stories/proof/ (overwritten)
#   render-share-images.sh all                 # sequences (dated folder) plus the single images and the story pool
#   render-share-images.sh <variant>           # only that variant into its usual folder (portrait, story, landscape,
#                                              # square, mandala, wide, teaser, wallpaper); put the text set first for another set
# Every run of the sequences gets its own folder share/<name>/stories/<date-time>/, so earlier runs stay.
# Single images (portrait/, square/, landscape/, story/) are overwritten in place. Nothing else is deleted.
# English only; LANGS="en de" adds German. The list of images and file names comes from the HTML (?list=1).
set -eu
cd "$(dirname "$0")"
# Arguments: [text set] [proof | all | variant]; the text set may be left out ("render-share-images.sh proof").
if [ -n "${1:-}" ] && [ ! -f "texts/$1.js" ] && [ -z "${2:-}" ]; then text="default"; only="$1"; else text="${1:-default}"; only="${2:-}"; fi
run="$(date +%Y-%m-%d-%H%M)"
[ "$only" != "proof" ] || run="proof"
langs="${LANGS:-en}"
[ -f "texts/$text.js" ] || { echo "texts/$text.js not found" >&2; exit 1; }
chrome="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
timeout="$(command -v gtimeout || command -v timeout)" || { echo "needs GNU timeout (brew install coreutils)" >&2; exit 1; }
page="file://$(pwd)/MKT-2-share-images.html"
# Teaser backgrounds come from two folders: renders/story/ (a line goes under the image) and
# renders/story-quote/ (the image carries its own text, no line). Every JPEG or PNG there is used, any size; this
# writes the list the HTML reads. SHUFFLE=1 puts them in random order for this run.
names() { ls "$1" 2>/dev/null | grep -i -E '\.(jpe?g|png)$' | { if [ "${SHUFFLE:-}" = 1 ]; then sort -R; else sort; fi; } | sed 's/.*/"&"/' | paste -sd, -; }
printf 'window.MKT_TEASERS = { plain: [%s], quote: [%s] };\n' "$(names renders/story)" "$(names renders/story-quote)" > renders/story-list.js
cat renders/story-list.js
list=$("$chrome" --headless=new --disable-gpu --dump-dom "$page?list=1" 2>/dev/null | grep -o 'IMG [^<]*' | cut -c5-)
[ -n "$list" ] || { echo "could not read the image list from the HTML" >&2; exit 1; }
echo "$list" | while read -r name i w h lang seq file; do
    case " $langs " in *" $lang "*) ;; *) continue ;; esac
    case "$only" in
        proof) case "$file" in stories/story-1-en/*) ;; *) continue ;; esac ;;
        "") case "$file" in stories/*) ;; *) continue ;; esac ;;
        all) ;;
        *) [ "$name" = "$only" ] || continue ;;
    esac
    case "$file" in stories/*) file="stories/$run/${file#stories/}" ;; esac
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
for d in "share/$text/stories/$run"/story-*; do
    [ -d "$d" ] || continue
    overview="$d/00-overview.jpg"
    rm -f "$overview"
    magick montage "$d"/*.jpg -tile x1 -geometry 540x960+10+10 -background '#0a0908' -quality 88 "$overview"
    echo "$overview"
done
