#!/usr/bin/env python3
"""Builds app/src/main/assets/starter-favorites.json from the macOS favourites file.

Usage: tools/starter-favorites.py [path to favorites.json]
Picks favourites by index (Mathis' Mac favourites, 20 October 2026), keeps their ids so importing
the Mac file later does not add them twice, and drops strokes of drawing layers that are off.
"""
import json
import os
import sys

# Shown first: the icon's mandala. Left out: near-duplicates, one with a custom palette, and quotes
# in fonts Android has no close match for (Copperplate, Zapfino).
PICKS = [3, 5, 2, 17, 0, 4, 6, 7, 8, 9, 12, 10, 19, 20, 21, 23, 24, 25, 15, 26, 27, 28, 29]

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
    out.append(fav)
dest = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "starter-favorites.json")
with open(dest, "w") as f:
    json.dump(out, f, separators=(",", ":"))
print(f"{len(out)} favourites, {os.path.getsize(dest) // 1024} KB -> {os.path.normpath(dest)}")
