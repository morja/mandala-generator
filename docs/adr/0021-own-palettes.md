# ADR-0021 · Own palettes on Android

Status: accepted, 26 October 2026 (Mathis, [DM-15](../mocks/DM-15-own-palettes.html), UX-1 15). Android first; departs from ADR-0008 (Mac first) and adds one field to the settings JSON of ADR-0003.

## Decision

1. **Free, at the palette.** Own palettes are not Pro (amends ADR-0016 point 3: "all styles and palettes" includes own ones). They live in the layer's Palette group and the emblem's Colour group, not in the menu.
2. **Place in the group:** the offered built-in palettes, then "In this picture" for an own palette that came with a favourite or a file (with "Add to my palettes"), then "My palettes" with a plus at the end. Under a chosen own palette its name and "Edit".
3. **Making and editing in place:** the plus opens the editor inside the group, so the picture above shows every change. It starts with the colours of the palette in use; "From picture" replaces them with five colours from the picture, dark to bright. Name, two to eight colours (hue, saturation, brightness, as on the Mac; drag to move), Save, Cancel, and Delete when editing.
4. **Storage:** `palettes.json` in the app's files, in the Mac's `CustomPalette` format (`id`, `name`, `stops` with `id`, `position`, `hue`, `saturation`, `brightness`).
5. **The palette travels inside the settings:** a layer or the emblem that uses an own palette carries it whole in a new optional field `customPalette` (same format). `paletteIndex` then holds the built-in palette that looks most like it. Favourites keep their copy; editing or deleting an own palette later changes only the current picture.
6. **Other apps:** the Mac ignores the field and draws the built-in fallback; another phone draws the own palette and offers to keep it. Mac favourites that use a Mac own palette (`paletteIndex` from 21) are not handled; they still show the last built-in palette on Android.

## Reasons

A palette is a few numbers, so carrying it is cheap and a favourite or a shared file always looks the same on another phone. The index stays meaningful for the Mac, so nothing breaks there. Editing in place avoids a sheet that would cover the picture.

## Consequences

`OwnPalettes.resolve` picks the palette for layers, the emblem, the auto background and the SVG export. The dice, a new layer and the live wallpaper's palette shift go back to built-in palettes. Tests: `OwnPalettesTest` in `core` (file format, field, drawing, fallback, colours from a picture) and in `app` (choose, save again, delete, dice).
