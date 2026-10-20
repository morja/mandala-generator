#!/bin/bash
# Builds AppIcon.icns, AppIcon.iconset and AppIcon_filled.png from a rendered mandala.
# The icon is the same as the Android app's: the settings in icon-settings.json, rendered at
# 1536 px (open the file in the app and save), centre 1024 px cut out like Android's adaptive
# icon mask, set into the macOS rounded square with a soft shadow.
# Usage: ./make_icon.sh rendered-1536.png     Needs ImageMagick (magick) and python3.
set -euo pipefail
cd "$(dirname "$0")"
src="$1"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Superellipse (n = 5) close to the macOS icon shape: 824 px wide on a 1024 px canvas.
points=$(python3 -c '
import math
n, a, c = 5.0, 412, 512
pts = []
for i in range(720):
    t = i / 720 * 2 * math.pi
    ct, st = math.cos(t), math.sin(t)
    pts.append("%.2f,%.2f" % (c + a * math.copysign(abs(ct) ** (2 / n), ct),
                              c + a * math.copysign(abs(st) ** (2 / n), st)))
print(" ".join(pts))')
magick -size 1024x1024 xc:black -fill white -draw "polygon $points" "$tmp/mask.png"

magick "$src" -gravity center -crop 66.667%x66.667%+0+0 +repage -resize 824x824 \
    -background none -gravity center -extent 1024x1024 "$tmp/art.png"
magick "$tmp/art.png" "$tmp/mask.png" -alpha off -compose CopyOpacity -composite "$tmp/masked.png"
magick "$tmp/masked.png" \( +clone -background black -shadow 45x10+0+10 \) +swap \
    -background none -layers merge +repage -gravity center -crop 1024x1024+0+0 +repage AppIcon_filled.png

for s in 16 32 128 256 512; do
    magick AppIcon_filled.png -filter Lanczos -resize "${s}x${s}" "AppIcon.iconset/icon_${s}x${s}.png"
    magick AppIcon_filled.png -filter Lanczos -resize "$((s * 2))x$((s * 2))" "AppIcon.iconset/icon_${s}x${s}@2x.png"
done
iconutil -c icns AppIcon.iconset -o AppIcon.icns
echo "Done: AppIcon.icns"
