# ADR-0006 · Design v1: warm neutrals around the mandala, one amber accent

Status: proposed, 19 October 2026

## Decision

The mandala brings all the colour. The interface around it stays calm:

- Dark by default (decided by Mathis on 20 October 2026, variant DM-3 in QA-1): a near-black warm background (#0A0908) with calmer controls, so the mandala is the brightest thing on screen. The light theme (warm off-white) is a choice in the menu ("Light theme") and is kept across starts; the system setting no longer decides. Status and navigation bar icons follow the app's theme.
- One soft amber accent for selection and the main action (the save icon in the top bar).
- The canvas is always black, because the renderer draws light on black.
- System sans serif (Material 3 type scale), Material 3 components mapped onto the tokens.
- Palettes are shown as round sweep-gradient swatches of their own colours.
- Controls in tabs and foldable groups with one-line sliders (20 October 2026), so the phone screen shows the canvas and one area at a time.
- On phones the canvas takes up to half the screen height (was 42 %, DM-3).
- Messages disappear on their own (about 10 s with Undo, a few seconds without); while the favourites sheet is open they show at its top.
- All values in `app/src/main/kotlin/com/mathis/mandala/app/ui/theme/Tokens.kt`.

## Reasons

Follows the `design-baseline` skill: the project's own imagery carries the character; no neon, glow, glass or purple gradients in the interface itself.

## Rejected

- Following the system's light or dark setting (until 20 October 2026, DM-1): the warm grey dark theme and the light theme let the mandala glow less.

- One long scrolling page with all sections and "Show sliders" buttons (v1, 19 October 2026): too cramped and confusing on a phone.
