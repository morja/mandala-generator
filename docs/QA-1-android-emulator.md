# QA-1 · Android app, clicked through in the emulator

20 October 2026, debug build of `main` (df9174d) plus the message fix, Android emulator (1080×1920, Android 16), light and dark system setting.

## What was checked

| Area | Result |
|---|---|
| Start, first preview | Works. The first preview takes about 10 s in the emulator; complex favourites with four layers 15–30 s (the S23 is much faster). The old image stays visible with a thin progress bar meanwhile. |
| Favourites: open, apply | Works; "Favourite applied" with Undo. |
| Save to gallery (1024 px) | Works; spinner in the save button, then "Saved to Pictures/Mandala as …". |
| Save large (2048 px) | Works (debug builds count as Pro); "Saved 2048 px to …". |
| Emblem tab | Works: switch on, motif chips, Organic, style and frame, shape, colour, blending. Om with ring renders correctly. |
| Double-tap reset | Works: Rotate 57° → 0°, Weight 0.13 → 0.50. |
| Scene, Text, Draw tabs | Open without errors. Drawing a line, then Clear: "Lines cleared" with Undo. |
| About | App name, version 1.0 (1), licence, GPL section 7, source and privacy links, libraries. |
| Dark system setting | Uses the dark tokens (DM-1). |

## Found and fixed

1. **Messages with Undo never went away.** Compose keeps a snackbar that has an action until it is tapped. Because messages queue, every later message waited behind it, so "Saved to Pictures" never showed after a favourite was applied. Fixed: messages with Undo disappear after about 10 s, others after a few seconds (commit on `fix/snackbar-and-dark-theme`). Checked in the emulator with "Favourite applied" and "Lines cleared".

## Fixed after the walkthrough (same day, Mathis' decisions)

2. Emblem on bright mandalas: the emblem's default blend is now Normal in both apps (Screen stays selectable; the parity fixtures that relied on the old default now set Screen explicitly).
3. Deleting a favourite shows "Favourite deleted" with Undo, which puts it back at its place. Messages show at the top of the favourites sheet while it is open (behind it they were hidden).
4. Save messages are short: "Saved to Pictures/Mandala", "Saved 2048 px to Pictures/Mandala".
5. DM-3 is the new default (ADR-0006): always dark unless "Light theme" is chosen in the menu; light status bar icons on the dark theme.

## Found, not fixed yet (original list)

2. **Emblem disappears on very bright mandalas.** With the default blend Screen, an emblem cannot brighten a white centre (seen with the favourite Floral, Wave Interference, Moiré, Sphere Grid). Options: a soft dark halo behind the emblem (new setting, needs a Mac change first), or Normal as the emblem's default blend. To decide.
3. **No feedback when a favourite is deleted** by long press; it is gone without Undo. Suggestion: "Favourite deleted" with Undo.
4. **Long file names in the save message.** "Saved to Pictures/Mandala as mandala-flor+wave-p6-x8-831f5d29.png" fills two lines; "Saved to Pictures/Mandala" would do.

## Dark theme proposal (DM-1 to DM-3)

Comparison: `docs/images/DM-1-3-dark-theme.png`.

- **DM-1** (today): follows the system; the dark variant is a warm grey (#151413) with strong amber controls.
- **DM-2**: always dark, whatever the system setting; background near-black (#0A0908), surfaces a step lighter, muted text and outlines, a slightly softer amber. The image becomes the brightest thing on screen.
- **DM-3**: DM-2 plus a larger image on phones (half the screen height instead of 42 %).

Needed with DM-2/DM-3: light status bar icons even when the phone is set to light (today they turn dark on black), and a decision whether the light theme stays as an option. Changing the default from "follow the system" deviates from ADR-0006 and needs Mathis' approval.
