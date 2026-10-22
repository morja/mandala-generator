# PLAN-5 · One line between free and Pro for Android and iPhone

22 October 2026. Status: approved by Mathis the same day, question by question (see the end); the effects layer free and the price 2.99 € differ from the first draft. Built for Android in 1.4.0 ([QA-3](QA-3-pro-line.md)). Amended the same day for 1.4.1 (Mathis): three free layers instead of two, no limit on favourites; ADR-0016 has the current line, the tables below are the first draft. Decision: [ADR-0016](adr/0016-one-pro-line.md), which replaces the free part of ADR-0007 and point 1 of ADR-0014.

Thought through as if both apps were started today (Mathis, 22 October 2026): one product "Dahlia" on two platforms. The free app keeps the full fun of making mandalas, and both apps give a real reason to buy Pro. No one has bought Pro yet, so the line can still move.

## 1. The problem with today's line

On Android today almost only the result is Pro: saving large, framing by hand, the finish, wallpaper pair, daily and live wallpaper (ADR-0007, ADR-0014). People who just play never meet Pro. The line belongs in the making, without taking the fun away.

## 2. Principle: everyone may try, keeping it is Pro

- Pro features are visible in the editor with a Pro mark and can be tried live on the mandala.
- As soon as the mandala uses a Pro feature, a small Pro mark shows on the mandala itself, so nobody finds out only when saving.
- **Saving, sharing, keeping as a favourite or setting as wallpaper** a mandala that uses a Pro feature opens the Pro sheet instead. The sheet names what is Pro in this mandala and offers to remove it ("Save without the emblem").
- Mandalas without Pro features save freely, as today, without a watermark.

## 3. The line

| | Free | Pro |
|---|---|---|
| a) Layers | up to 2 | up to 5 |
| b) Styles and palettes | all | all |
| c) Emblems | – | yes |
| d) Text and quotes | – | yes |
| e) Effects layer | all | all |
| f) Moving layers and the emblem (ADR-0011) | – | yes |
| g) Favourites | 5 | unlimited |
| h) Saving | screen size, every format, "Show whole" and "Fill" | 2048 px, framing by hand, finish, SVG line drawing |
| i) Wallpaper | set by hand (Android), save for it (iPhone) | pair, daily; live (Android); widgets and Shortcuts action (iPhone) |
| j) Surprise and starters | all; starters with Pro features can be tried | – |

- **All styles and the whole effects layer free:** variety and the finished look are the fun, and the images people share are the app's best advertising. Three or more layers, emblems, text and positions are fine work, which is what people pay for.
- **Two layers** make good mandalas on their own; richer starters show what more layers do.
- **Starters**, counted on 22 October 2026: 10 of the 21 built-in starters stay fully free (with a Pro effects layer it would have been 5). The rest use a third layer, an emblem or text and show what Pro adds.
- **Surprise mandalas** for free users use at most two layers and no Pro features, so they always save.
- The same line on both platforms; differences only where the system forces them (row i).

## 4. Price

One price on both platforms: **2.99 €**, one-time, no subscription, with Family Sharing on the iPhone (Android has no equivalent for in-app products). 2.99 € is a spontaneous purchase; 3.99 € already makes people hesitate (Mathis). Raising the price later is easy with a one-time purchase, since buyers keep Pro; 3.99 € once sales show it.

Both stores take 15 % in their small business programmes; Apple's has to be joined once.

## 5. The Android release

State on 22 October 2026: the production release 7 (1.3.1) is saved, but Mathis has not sent it for review. It stays unsent. Android 1.4 with the new line replaces it and is the first public version, so no early installers need a special case.

## 6. Android 1.4 (about 2–3 days)

- Pro checks in `Editing.kt` and the save, favourite and wallpaper paths: count of layers, emblem, text layer, moved layers. One function `proFeaturesIn(settings)` in `core`, so the iPhone app uses the same rule.
- Pro marks in the layer list, emblem and text panels and on the mandala; the Pro sheet lists what this mandala uses and offers to remove it.
- Favourites limit of 5 with a gentle hint at the sixth.
- Surprise mandalas within the free line when Pro is not owned.
- Starter favourites stay as they are; those with Pro features are marked.
- Pro sheet and store listing: the new list; the price of product `pro` stays 2.99 €.
- Tests for `proFeaturesIn` in `core`; QA report on the phone.

## 7. Effect on the iPhone plan

PLAN-4 and ADR-0015 take this line and price instead of their own; everything else there stays (shared Kotlin core, SwiftUI, Shortcuts action and widgets, iOS 17).

## Questions (answered 22 October 2026)

1. **Principle:** try freely, keeping needs Pro? **Mathis: yes, with the Pro mark on the mandala from the first Pro feature.**
2. **Two layers free, or three?** **Mathis: two.**
3. **Free effects:** five basic effects, the whole effects layer free, or the whole layer Pro? The starters use the layer as one look (bloom, erasure, highlights, dimming and grain together), so a split inside the layer helps little. **Mathis: the whole effects layer free.**
4. **Price:** 3.99 € on both was the first recommendation. **Mathis: 2.99 € on both**, see section 4.
5. **Hold the Android launch, 1.4 as the first public version?** **Settled:** the production release had not been sent for review yet; it stays unsent.
6. **Early installers keep today's free scope?** Not needed, since 1.3.1 never went public.
