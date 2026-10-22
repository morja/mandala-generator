# ADR-0013 · SVG line drawing as a Pro export

Status: accepted, 22 October 2026 (Mathis: build it, Pro on Android, say plainly what it leaves out; the rest as recommended in [SPIKE-2](../SPIKE-2-svg-export.md)).

## Decision

1. **"Save as line drawing (SVG)"**, a Pro feature on Android (ADR-0007). Without Pro it is visible with a Pro mark and opens the Pro sheet, like "Save large".
2. **Named as a line drawing, not as the image.** The save step says in one sentence what the SVG holds: the lines of the mandala layers and the emblem, without glow, background effects, quote and painted look. Strange Attractor layers are left out, with a note when the mandala has one.
3. **Two looks:** "Colour on black" (palette colours, lines added like light, a soft blur for glow) and "Dark on white" (one colour, for plotters, cutters and colouring pages).
4. **The renderer writes it.** `VectorExport` in `core` uses the same style code as the pixel renderer (ADR-0002), so geometry never drifts from the PNG. No new library.
5. **Square only, the mandala centred.** Framing and formats (ADR-0010) stay with the pixel export; a vector file can be cropped and scaled anywhere.
6. **Android first.** The macOS app can follow later with the same `core` logic ported to Swift; it is an export option, not a motif, like ADR-0010.
7. **Saved through Android's file picker** ("Save line drawing · SVG", then a folder such as Downloads or Drive), not the gallery, which does not show SVG. Sharing can follow from the file app; a share button can come later if it is missed.

## Reasons

- The spike showed exact geometry, small files (15 KB to 3 MB for single layers) and writing in under 0.2 s, while the glowing look cannot be reproduced in SVG. Calling it a line drawing sets the right expectation.
- Line drawings serve uses the PNG cannot: plotting, cutting, embroidery, colouring pages and large prints. That fits Pro ("for prints").

## Open points

- Emblem as paths: line and outline modes first; filled and beads as filled shapes.
- Path simplification to roughly halve file size; decide after measuring five-layer mandalas.

## How it is built (22 October 2026)

- `VectorExport` in `core`: layers through `MandalaRenderer.collectStyle`, the emblem through `EmblemMask.writeSvgMask` (the same shapes as the pixel mask: round-capped segments, discs, rings and even-odd polygons; outline cuts are black in the mask), filled with its palette as a radial gradient. Colours are converted from P3 to sRGB. 1024 user units, coordinates to a tenth.
- App: in the save sheet below "Set as wallpaper", a button with the two looks and one line on what is left out (plus Strange Attractor when the mandala has one). `EditorViewModel.saveLineDrawing` streams the file to the picked URI.
- Tests: `VectorExportTest` (every style, both looks, every emblem motif and rendering, determinism, what is left out); `MemoryBudgetTest` streams the five-layer worst case within 256 MB.
