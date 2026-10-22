#!/usr/bin/env python3
"""Builds app/src/main/assets/starter-favorites.json from the macOS favourites file.

Usage: tools/starter-favorites.py [path to favorites.json]
Picks favourites by index (Mathis' Mac favourites, chosen again 22 October 2026), keeps their ids so
importing the Mac file later does not add them twice, and keeps only what is visible: layers that are
off are dropped, and switched-off drawing, graffiti and text layers lose their strokes and text.
Adds the emblem favourites composed for the app (tools/starter-favorites/*.json). Every thumbnail is
rendered again with the command-line renderer (./gradlew :cli:installDist first), so it shows what
the app draws.
"""
import base64
import json
import os
import subprocess
import sys
import tempfile
import uuid

# Mac favourites by index, or a file name in tools/starter-favorites/. Shown first: the icon's mandala.
PICKS = [3, "om.json", 5, 17, 1, "flower-of-life.json", 14, 6, "lotus-gibran.json", 8, 9, 10, 11,
         0, 2, 19, 20, 23, 25, 26, 29]

# The app ships only public-domain quotes (ADR-0007), so the text favourites get new ones.
NEW_TEXT = {
    11: ("I exist as I am, that is enough.", "Walt Whitman"),
    14: ("Embrace the Soul which is embraced by nothing.", "Rumi"),
    17: ("This is the House of Love, which has no bound or end.", "Rumi"),
}
# Favourite 1 uses Mathis' own Mac palette "Fun", which the app does not have; Bio Glow comes closest.
PALETTES = {1: 9}
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
CLI = os.path.join(ROOT, "cli", "build", "install", "mandala", "bin", "mandala")
COMPOSED = os.path.join(ROOT, "tools", "starter-favorites")
# Seconds since 1 January 2001, as the Mac stores dates: 22 October 2026.
COMPOSED_DATE = 814_320_000.0


def thumbnail(params):
    """Renders a 128 px JPEG like the app's own thumbnails, as base64."""
    with tempfile.TemporaryDirectory() as tmp:
        settings, png, jpg = (os.path.join(tmp, n) for n in ("s.json", "t.png", "t.jpg"))
        json.dump(params, open(settings, "w"))
        subprocess.run([CLI, "render", settings, png, "--size", "512"], check=True, capture_output=True)
        subprocess.run(["sips", "-Z", "128", "-s", "format", "jpeg", "-s", "formatOptions", "85", png, "--out", jpg],
                       check=True, capture_output=True)
        return base64.b64encode(open(jpg, "rb").read()).decode()


def visible_only(params):
    params["layers"] = [layer for layer in params["layers"] if layer.get("isEnabled", True)]
    for key in ("drawingLayer", "graffitiLayer"):
        layer = params.get(key)
        if layer and not layer.get("isEnabled"):
            layer["strokes"] = []
    text = params.get("textLayer")
    if text and not text.get("isEnabled"):
        text["text"], text["customAuthor"] = "", ""


src = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser(
    "~/Library/Application Support/MandalaGenerator/favorites.json")
favorites = json.load(open(src))
out = []
for pick in PICKS:
    if isinstance(pick, str):
        params = json.load(open(os.path.join(COMPOSED, pick)))
        fav = {"id": str(uuid.uuid5(uuid.NAMESPACE_URL, "mandala-starter/" + pick)).upper(), "name": "",
               "createdAt": COMPOSED_DATE, "parameters": params}
    else:
        fav = json.loads(json.dumps(favorites[pick]))
        params = fav["parameters"]
        if pick in NEW_TEXT:
            params["textLayer"]["text"], params["textLayer"]["customAuthor"] = NEW_TEXT[pick]
        if pick in PALETTES:
            for layer in params["layers"]:
                if layer.get("paletteIndex", 0) >= 18:
                    layer["paletteIndex"] = PALETTES[pick]
    visible_only(params)
    fav["thumbnailData"] = thumbnail(params)
    out.append(fav)
dest = os.path.join(ROOT, "app", "src", "main", "assets", "starter-favorites.json")
with open(dest, "w") as f:
    json.dump(out, f, separators=(",", ":"))
print(f"{len(out)} favourites, {os.path.getsize(dest) // 1024} KB -> {os.path.normpath(dest)}")
