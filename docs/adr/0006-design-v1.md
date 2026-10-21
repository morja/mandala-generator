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
- On phones a button in the image's corner folds it into a small square at the top left, with the selected layer named beside it, so the controls get the room; the button or a tap on the small image brings it back, and the size stays as chosen while the controls scroll (DM-4 variant B, chosen by Mathis on 21 October 2026 after trying variant A on the phone). Folding only scales the shown preview and never renders (a smaller preview while folded was tried and dropped on 21 October 2026: switching sizes restarted renders while scrolling). Drawing and moving keep the image large.
- Previews rendered recently (30, about 1 MB each) are kept by their settings, so stepping through the history, undoing or switching something back shows the image at once; a favourite shows its thumbnail until its preview is ready; the save sheet keeps its last six previews by format and framing.
- Messages disappear on their own (about 10 s with Undo, a few seconds without); while the favourites sheet is open they show at its top.
- All values in `app/src/main/kotlin/com/mathis/mandala/app/ui/theme/Tokens.kt`.

## Reasons

Follows the `design-baseline` skill: the project's own imagery carries the character; no neon, glow, glass or purple gradients in the interface itself.

## Rejected

- Folding the image while the controls scroll (DM-4 variant A, tried on the phone on 21 October 2026): every slider further down forced the small image, with no way to keep it large.
- A drag handle under the image (DM-4 variant C): any size, but harder to hit and easy to confuse with scrolling.

- Following the system's light or dark setting (until 20 October 2026, DM-1): the warm grey dark theme and the light theme let the mandala glow less.

- One long scrolling page with all sections and "Show sliders" buttons (v1, 19 October 2026): too cramped and confusing on a phone.
