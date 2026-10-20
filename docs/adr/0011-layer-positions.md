# ADR-0011 · Layer positions: each layer and the emblem placed on their own

Status: accepted, 20 October 2026 (Mathis: "everything as recommended" in [RES-2](../RES-2-moving-layers.md))

## Decision

1. **Positions are part of the mandala.** Every mandala layer and the emblem get `offsetX` and `offsetY`: where their centre sits, in fractions of the mandala's square from its centre (right and down positive). 0 means centred, as today. They are saved with the settings, in favourites and in the history, under the same names on both apps (ADR-0003).
2. **Mac first** (ADR-0008): the macOS app gets the fields, the renderer change and the sliders first; Android follows and is measured against Mac reference images (ADR-0004).
3. **Editing positions is Pro in the Android app** (ADR-0007), like moving the mandala in the export (ADR-0010). Mandalas with moved layers render the same for everyone; without Pro the position controls are visible with a Pro mark and open the Pro sheet. The macOS app has no Pro; there positions are simply part of the app.
4. **Unchanged:** text keeps its own position settings; hand layers stay around the mandala's centre; background and effects layer follow the whole composition (ADR-0010, point 6). The export framing moves and zooms the whole composition on top of the layer positions.
5. **Later, after trying positions:** more elements (a higher layer limit, copies of a layer, several emblems) and a layer cache for live dragging.

## How it is built

- Settings: `offsetX`, `offsetY` in `StyleLayer` and `EmblemLayerSettings`, decoded with default 0, so older settings look as before; an older app version shows such a mandala centred.
- Renderer: the layer's centre moves by its offset. A moved layer is drawn in its full square (as a rotated layer is), cut to the canvas plus effect margin, so no edge of the centred square shows. Centred layers take the old path; existing images stay byte-identical. On the Mac the expanded layer buffer is cut at the offset instead of the middle. Prototype: local branch `spike/layer-offsets`.
- Editor: a "Position" group per layer (X, Y, Centre) and a "Move" mode on the canvas (drag moves, pinch sizes, two fingers turn the selected layer). While a finger moves, an outline of the layer is drawn over the old preview; the preview is redrawn when it lifts.
- Checks: parity fixtures with moved layers and the emblem moved; a `MemoryBudgetTest` case with moved layers.
- **macOS, done on 20 October 2026** (branch `feature/mac-layer-positions`): fields with default 0; the expanded layer buffer is cut at the offset (`cropShifted`, black beyond it), whole pixels moving the window and the rest the drawing centre, rounded as on Android; the emblem is drawn and coloured around its moved centre; "Position X" and "Position Y" sliders in the layer card and the emblem card (−1 to 1, double-click resets); a move mode (four-arrow button above the canvas) with a numbered handle and a faint ring per layer and an "E" handle for the emblem, rendered again when a drag ends. Checked with the reference renderer against the Android prototype: moved, rotated and multiply layers land in the same place and look alike.

## Reasons

- In the export only, positions would be invisible while editing, lost in favourites and unknown to the Mac.
- The renderer already draws every layer around a centre of its own (PLAN-2 step 1), so the cost is small and memory stays within the checked bound.
- Pro: positions are a creative extra like the framing, and together they give a clearer reason to buy than resolution alone.
