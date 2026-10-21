# QA-2 · Android app in the emulator, and preview speed

21 October 2026, `main` at 9ad6903 (save sheet with formats, layer and emblem positions, compact editor). Emulator: Android 14, 1080×2340, 4 cores, debug and release builds. Tablet checked with a 2560×1600 window.

## What was checked

All of it works:

- **Layers:** add, duplicate, delete, the limit of five, show and hide, reorder, randomize, style, palette, blending, double-tap reset, position sliders, Centre, move on the image.
- **Scene:** every background type, both colours, the effects switch and its sliders.
- **Emblem:** switch, motifs, random emblem, style and frame.
- **Text:** switch, random quote, choosing a quote.
- **Draw:** lines on the image, Done, Undo, Clear, the line count.
- **Favourites:** add, apply, delete with Undo.
- **Save sheet:** Square, This screen, 4:5, Save, Save large, Set as wallpaper (home screen). Saved files land in `Pictures/Mandala`.
- **Menu:** Share image, Open file, Save settings, New variation, history, Light theme, About.
- **Pro in the release build:** Save large and Position show the Pro hint, and the Pro sheet opens. Buying waits for the first Play upload.
- **Layout:** two panes on the tablet, light and dark.

## Found and fixed

1. A hidden layer could be moved on the image without any hint. Position now says the layer is hidden and Move on image is off.
2. Beside the folded image the selected layer was shown on every tab. Now it shows what the open tab is about (the background on Scene, the motif on Emblem, and so on).
3. "Favourite deleted" covered the title and Add current in the favourites sheet. Messages now sit below the title.
4. Only the switch itself turned Show emblem, Show text and the other switch rows on. Now the label does too.
5. The Pro sheet said "Mandala Pro". It now says "Mandala Generator Pro", like the store product.

## Preview speed

In the emulator a preview with four or five layers took about a minute. The debug build is about half as fast as the release build (1024 px save: 97 s against 51 s), and the emulator is far slower than a phone. So the measurements below come from the JVM: the 23 starter favourites at preview size (512 px), with 4 threads, after warming up.

A JFR profile showed where the time goes. Blurring for glow and wash takes 55–65 % (box and Gaussian passes, resampling to the 1600 px reference width), colour conversion of rows 20–30 %, and drawing the curves under 5 %.

| Step | All 23 favourites | Image |
|---|---|---|
| Before | 16.3 s | — |
| P1: four work ranges per thread instead of one | 16.3 s (no change with equal cores) | identical |
| P5: faster blur loops (black lines skipped, column blocks, hoisted indices) | 13.5 s | identical |
| P4: Fast preview, glow at 800 px | 7.5 s | at least 52.8 dB, mean 0.3/255 |
| P2: layer cache, after a change to one layer | 5.1 s (13.2 s without fast preview) | identical |

- **P1** helps on phones with fast and slow cores, like the Galaxy S23 (1 + 4 + 3 cores). Before, each step waited for its range on the slowest core. That cannot be measured on the Mac, so it still needs a check on the phone.
- **P2:** after changes to the emblem, text, background or effects, no layer is drawn again.
- **P3:** when a change redraws at least two layers and is expected to take more than a second, a small rough image (160 px, coarse glow) shows first. It has no switch, because the finished image is the same either way.
- **P4** is the switch "Fast preview" in the menu, on by default. Saved, shared and wallpaper images always use the full 1600 px glow (ADR-0004).

## Still open

- Measure P1 to P4 on the S23 with a release build.
