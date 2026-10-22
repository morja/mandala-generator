# TUNE-1 · Palettes, sliders and the dice

22 October 2026. Review of every palette and every slider in the Android app, so that a user gets good results quickly. About 1,200 renders with the command-line renderer (it draws exactly what the app draws), each slider swept over its range at min, 25 %, 50 %, 75 % and max on several styles and favourites. Analysis only; nothing is changed yet. Almost all findings apply to the macOS app as well, because the code is the same.

Two constraints shape every proposal:

- **Saved settings must keep their look** (ADR-0003). Palette indices stay valid; a palette can be hidden from the picker, not deleted. Changing a renderer formula changes how existing favourites look, on both apps.
- **Mac first.** Renderer changes and shared ranges go into the macOS app first and are ported, with new parity references (ADR-0004).

## 1. Palettes

18 palettes, all fully saturated. Four pairs are almost indistinguishable in every test image (distance in OKLab, 32 samples per palette):

| Pair | Distance |
|---|---|
| 2 Nebula – 4 Sunset | 0.019 |
| 3 Neon City – 12 Candy | 0.027 |
| 0 Psychedelic – 5 Prism | 0.031 |
| 8 Ocean – 9 Bio Glow | 0.046 |

Also weak: 10 Dragon (darkest, reads as Violet), 6 Cosmic (pale, turns lavender), 15 Synthwave (between Aurora and Violet), 8 Ocean (between Bio Glow and Ice).

![Floral in all 18 palettes](images/tune-1/palettes-floral.jpg)

![A four-layer favourite in all 18 palettes](images/tune-1/palettes-favourite.jpg)

**Proposal:** offer 11, in this order (colourful first, then once round the colour wheel): Psychedelic, Neon City, Tropical, Sunset, Lava, Gold, Toxic, Bio Glow, Ice, Aurora, Violet. Hide Prism, Candy, Nebula, Ocean, Synthwave, Dragon, Cosmic: they keep rendering in saved settings, and the picker shows a hidden palette as selected when a layer uses it. The dice and "add layer" (today `(last + 3) % 18`) draw only from the offered ones.

**Missing:** everything is neon. Worth adding (Mac first, needs approval): a neutral **Silver/Moon** (grey to warm white, elegant for emblems, line styles and prints) and an **Earth** palette (terracotta, ochre, olive, sage). A pastel palette would need testing first: additive glow on black turns pastels white quickly.

## 2. The dice

The dice ranges are the same on both apps (`Randomize.kt`, `AppState.swift`). About 4 in 20 throws come out blown out: a white disc, yellow over-saturation, pink mush.

![20 throws](images/tune-1/dice-20.jpg)

The main cause is **Wash**: the dice give every layer Wash 0–0.5, but Wash works like a switch (the whole jump happens between 0 and 0.05, see 3.10), and Android has no slider to turn it off. Second cause: Painted above 0.5 lifts everything towards white.

![Each row: a throw as it is, with Wash 0, with Wash 0 and Painted at most 0.4](images/tune-1/dice-wash-fix.jpg)

**Proposal:** Wash 0; Painted 0.1–0.5; Brightness 0.3–0.6; Symmetry 3–8 (1 and 2 come up in a quarter of throws today and look lopsided for most styles); a little less Complexity and Density from the second layer on.

## 3. Mandala layer sliders

1. **Brightness** (gain 3 × value): 0–0.2 is black, dense styles burn out from 0.5–0.75. Usable range about 0.2–0.7. Proposal: slider range 0.15–0.85.
2. **Painted:** nothing happens from 0 to 0.4 (the strong part starts at 0.3, the default sits at the end of the dead zone); at 1.0 the black background turns grey. Proposal: map the slider to 0.3–0.85.
3. **Complexity:** at 0, four of six styles are completely black (line width is multiplied by it, as in Swift); for Deep Nebula and Floral it changes little. Proposal: minimum 0.15.
4. **Density:** fine where it works; no effect on Spirograph and String Art. Proposal: hide it for styles where it does nothing.
5. **Ripple:** clear only on Flow Field, subtle on Floral and Spirograph, none elsewhere. Proposal: hide it per style like Density.
6. **Glow:** little happens below 0.5. Optional: a square-root mapping.
7. **Colour drift, Saturation, Opacity:** fine.
8. **Scale:** 0.1 is tiny. Proposal: 0.2–1.2. The Mac slider goes 0–1, so its first tenth does nothing and 1.0–1.2 cannot be reached; give it the same range.
9. **Symmetry 1–8** (Mac the same): fine. Higher values would need brightness normalisation in the renderer; not worth it now.
10. **Wash** (no slider on Android): a switch, not a slider, because only the blur radius scales with it, not its strength.

![Wash 0 to 0.2: the whole change happens at the first step](images/tune-1/layer-wash.jpg)

![Complexity: black at 0](images/tune-1/layer-complexity.jpg)

![Painted: dead from 0 to 0.4, grey at the top](images/tune-1/layer-painted.jpg)

## 4. Effects

![Rows 1–2 Relief: grey already at 0.01; rows 3–4 Vignette: hardly visible](images/tune-1/fx-relief-vignette.jpg)

1. **Relief is broken:** any value above 0, even 0.01, turns the whole image grey; above that the slider only adds ridges. Proposal: fix the blend so flat areas stay neutral, or hide it until then.
2. **Chromatic** leaves a green stripe at the left and right edges (it samples black outside the image) and is hard to see otherwise. Proposal: clamp at the edges; optionally make it radial.
3. **Vignette** is too weak: the default 0.3 is invisible, because the radius grows with the strength. Proposal: fixed radius and about 1.5 × the strength.
4. **Bloom** is barely visible on dark images. Proposal: double the gain.
5. **Grain** is hardly visible and vanishes in large exports (per-pixel noise). Proposal: about twice as strong and scaled with the image size.
6. **Glitter** does nothing up to 0.25 and is garish confetti at 1.0. Proposal: map the slider to 0.1–0.6.
7. **Wash and Fade** do almost the same (a lift towards white; Wash also desaturates). Proposal: one slider "Fade".
8. **Highlights, Dimming, Erasure** look like light leaks, blotches and burn holes. Proposal: move them, with Relief and Chromatic, into a "More" section.
9. **Sepia, Clarity, Stars, Brightness, Contrast** work well and give quick results.

Proposed order: *Light* Brightness, Contrast, Vignette, Bloom · *Tone* Sepia, Fade · *Texture* Clarity, Grain · *Sparkle* Stars, Glitter · *More* the rest.

![Sepia good, Fade weak, Grain invisible](images/tune-1/fx-sepia-fade-grain.jpg)

## 5. Background

1. The default colours are so dark (brightness 0.18 and 0.04) that Colour, Gradient and Grain look the same. Proposal: about 0.35 and 0.08.
2. **Sunburst has a seam** on the left horizontal axis, because the number of rays is not a whole number. Proposal: round it.
3. Above a brightness of about 0.6 a colour background drowns the mandala (lines are added with Screen). Proposal: limit the background brightness to about 0.6.
4. The **Grain** type disappears in large exports and only lifts the colour. Proposal: hide it (the effects layer has grain).

![Background types with the default colours](images/tune-1/background-types.jpg)

![Sunburst seam](images/tune-1/sunburst-seam.jpg)

## 6. Emblem

1. **Glow makes a dark halo** with the default blend Normal instead of light, and most of the change happens between 0 and 0.25. Proposal: fix the glow outside the shape and use a gentler curve.
2. **Size:** at 1.0 the ring is clipped, and the Gates and Lotus Ring frames reach the edge at about 0.75. Proposal: 0.2–0.9.
3. **Weight** with Line rendering is almost invisible at 0. Proposal: map to 0.1–1.
4. Position, Spread, Organic, Branches and all frames: fine. Outline on Flower of Life turns into a dense bright mass.

![Emblem glow 0, 0.5 and on a dark image: dark halo](images/tune-1/emblem-glow.jpg)

## 7. Text

1. **Size:** Android 0.02–0.2, Mac 0.02–0.105. Above about 0.13 a quote runs off the canvas. Proposal: 0.03–0.12.
2. **Width** below 0.3 gives one word per line. Proposal: 0.4–1.0.
3. **Glow is invisible** even at 1.0 (Mac the same). Proposal: about 3 × stronger or wider.
4. **Brightness:** Android 0–1.5, Mac 0–2; the starter favourites store 2.0, outside the Android slider. Proposal: 0–2 as on the Mac.
5. **Cloud** is the best lever for readability; Cloud size above 0.7 darkens most of the mandala. Proposal: 0–0.7. Shadow has only four visible steps; a continuous strength would be better.

![Text size overflow and invisible glow](images/tune-1/text-size-glow.jpg)

## 8. Side findings

- Starter favourites 7 and 8 use palette indices 18 and 19, Mathis' own Mac palettes; Android silently takes the last built-in palette (Gold). For favourite 7 that is close, for 8 it is a visible change.

## 9. Suggested order

1. **Palettes and dice** (Android picker and dice; dice ranges also on the Mac): the biggest felt improvement, no change to saved images.
2. **Fixes** in both renderers: Relief, Chromatic edge, Sunburst seam, emblem glow, text glow, Wash as a real slider. These change the look of saved settings that use them; new parity references.
3. **Slider ranges** (UI only, values in the JSON unchanged): Brightness, Painted, Complexity, Scale, emblem Size and Weight, text Size, Width, Brightness and Cloud size, Glitter.
4. **Tidier panels:** effects order with "More", Fade and Wash merged, Density and Ripple hidden per style, brighter background defaults, Grain type hidden.
5. **New palettes** Silver and Earth, Mac first.

## 10. What was changed: renderer

22 October 2026, branch `tune/renderer`. Fixed in the macOS renderer first, then ported to the Kotlin core; parity references re-rendered, details and thresholds in ADR-0004. No JSON field, default or range changed: saved settings load as before, but those that use one of these effects now look different, on both apps.

1. **Relief:** the emboss now convolves colour only and repeats the edge pixels, so flat areas stay neutral; 0.01 looks like the original and the slider adds ridges evenly. *Saved settings with relief* lose the grey veil: darker, more contrast, ridges as before. No starter favourite uses relief.
2. **Chromatic:** red and blue repeat the edge pixels instead of black. *Saved settings* lose the coloured stripe at the left and right edges; the rest is unchanged.
3. **Vignette:** fixed radius (the strongest CIVignette setting) and 1.5 × the intensity. *Saved settings* get darker corners: the default 0.3 is now visible, 0.6 clearly dark, 1.0 strong but not black. Starter favourites with vignette: 1, 5, 7, 17, 18, 19 (numbered from 1).
4. **Bloom:** twice the gain. *Saved settings* glow more and lift dark backgrounds visibly; at 1.0 bright images turn hazy. Ten starter favourites use bloom 0.13–0.52.
5. **Grain:** on the Mac grain did nothing at all (the noise was transparent); it now works there. Twice the amplitude on both apps, and above 1024 px the grain cells grow with the image, so a 4096 px export shows the same grain as the preview. *Saved settings with grain* show it for the first time on the Mac and twice as strong on Android. The same ten starter favourites use grain 0.09–0.49; at 0.5 the grain is now clearly visible.
6. **Glitter:** the stored value is mapped to 0.2–0.6 of the former amount inside the renderer (count and brightness), 0 stays off. *Saved settings* below about 0.33 get more glitter (0.11 now looks like 0.24 did), above it less (1.0 looks like 0.6 did); around 0.33 nothing changes. Starter favourites 18 and 19 (0.11) get more, 15 (0.31) stays.
7. **Sunburst background:** the number of rays is rounded to a whole number, which removes the seam on the left horizontal axis. *Saved settings* get at most half a ray more or fewer; the starter favourites with a sunburst (16.04 rays) look the same.
8. **Emblem glow:** the glow is now light around the shape, screened on top after the emblem is blended, instead of part of the overlay, whose blurred alpha made a dark halo with Normal. Strength follows the square root of the glow and the reach starts at a third, so 0.1 already shows a soft halo and the slider changes it evenly; the maximum reach is unchanged. With Multiply the glow stays as before. *Saved emblems with Normal* lose the dark halo, the mandala stays visible behind the glow, and the gaps inside line emblems no longer fill with colour; with Screen and Add the change is small. Starter favourites 2, 6 and 10 (emblem glow 0.6, Normal).
9. **Text glow:** the glow is light screened on top as well, with the two wide passes three times as strong (the narrow one unchanged, so the letters stay readable). Before it was invisible even at 1.0. With Multiply it stays as before. *Saved texts with glow* now get a clear halo in the text colour; starter favourite 15 (glow 0.66).

Wash (layer) is unchanged. Text and the emblem with Multiply keep their former glow on purpose.
