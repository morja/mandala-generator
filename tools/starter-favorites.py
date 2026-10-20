#!/usr/bin/env python3
"""Builds app/src/main/assets/starter-favorites.json from the macOS favourites file.

Usage: tools/starter-favorites.py [path to favorites.json]
Picks favourites by index (Mathis' Mac favourites, 20 October 2026), keeps their ids so importing
the Mac file later does not add them twice, and drops strokes of drawing layers that are off.
"""
import base64
import json
import os
import subprocess
import sys
import tempfile

# Shown first: the icon's mandala. Left out: near-duplicates, one with a custom palette, and quotes
# in fonts Android has no close match for (Copperplate, Zapfino).
PICKS = [3, 5, 2, 17, 0, 4, 6, 7, 8, 9, 12, 10, 19, 20, 21, 23, 24, 25, 15, 26, 27, 28, 29]

# The app ships only public-domain quotes (ADR-0007), so the text favourites get new ones; their
# thumbnails are rendered again with the command-line renderer (./gradlew :cli:installDist first).
NEW_TEXT = {
    12: ("I cannot choose the best. The best chooses me.", "Rabindranath Tagore"),
    17: ("Beauty is eternity gazing at itself in a mirror.", "Kahlil Gibran"),
    15: ("Let life be beautiful like summer flowers and death like autumn leaves.", "Rabindranath Tagore"),
}
# Favourite 12 was saved with an older Mac version; today's renderer (Mac and Android alike) puts its
# hard shadow far off, so the starter gets a closer, softer one.
TEXT_TWEAKS = {12: {"shadowOffsetX": 0.12, "shadowOffsetY": -0.18, "shadowBlur": 0.6, "shadowOpacity": 0.8}}
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
CLI = os.path.join(ROOT, "cli", "build", "install", "mandala", "bin", "mandala")


def thumbnail(params):
    """Renders a 128 px JPEG like the app's own thumbnails, as base64."""
    with tempfile.TemporaryDirectory() as tmp:
        settings, png, jpg = (os.path.join(tmp, n) for n in ("s.json", "t.png", "t.jpg"))
        json.dump(params, open(settings, "w"))
        subprocess.run([CLI, "render", settings, png, "--size", "512"], check=True, capture_output=True)
        subprocess.run(["sips", "-Z", "128", "-s", "format", "jpeg", "-s", "formatOptions", "85", png, "--out", jpg],
                       check=True, capture_output=True)
        return base64.b64encode(open(jpg, "rb").read()).decode()


src = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser(
    "~/Library/Application Support/MandalaGenerator/favorites.json")
favorites = json.load(open(src))
out = []
for i in PICKS:
    fav = json.loads(json.dumps(favorites[i]))
    params = fav["parameters"]
    for key in ("drawingLayer", "graffitiLayer"):
        layer = params.get(key)
        if layer and not layer.get("isEnabled"):
            layer["strokes"] = []
    if i in NEW_TEXT:
        params["textLayer"]["text"], params["textLayer"]["customAuthor"] = NEW_TEXT[i]
        params["textLayer"].update(TEXT_TWEAKS.get(i, {}))
        fav["thumbnailData"] = thumbnail(params)
    out.append(fav)
dest = os.path.join(ROOT, "app", "src", "main", "assets", "starter-favorites.json")
with open(dest, "w") as f:
    json.dump(out, f, separators=(",", ":"))
print(f"{len(out)} favourites, {os.path.getsize(dest) // 1024} KB -> {os.path.normpath(dest)}")
