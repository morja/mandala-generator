# PLAN-5 · One line between free and Pro for Android and iPhone

22 October 2026. Status: draft, for Mathis to decide on the questions at the end. Decision draft: [ADR-0016](adr/0016-one-pro-line.md), which would replace the free part of ADR-0007 and point 1 of ADR-0014. Nothing is built yet.

Thought through as if both apps were started today (Mathis, 22 October 2026): one product "Dahlia" on two platforms. The free app keeps the full fun of making mandalas, and both apps give a real reason to buy Pro. No one has bought Pro yet, so the line can still move.

## 1. The problem with today's line

On Android today almost only the result is Pro: saving large, framing by hand, the finish, wallpaper pair, daily and live wallpaper (ADR-0007, ADR-0014). People who just play never meet Pro. The line belongs in the making, without taking the fun away.

## 2. Principle: everyone may try, keeping it is Pro

- Pro features are visible in the editor with a Pro mark and can be tried live on the mandala.
- **Saving, sharing, keeping as a favourite or setting as wallpaper** a mandala that uses a Pro feature opens the Pro sheet instead. The sheet names what is Pro in this mandala and offers to remove it ("Save without the emblem").
- Mandalas without Pro features save freely, as today, without a watermark.

## 3. The line

| | Free | Pro |
|---|---|---|
| a) Layers | up to 2 | up to 5 |
| b) Styles and palettes | all | all |
| c) Emblems | – | yes |
| d) Text and quotes | – | yes |
| e) Effects layer | vignette, brightness, contrast, fade, grain | all others (bloom, relief, stars, glitter, …) |
| f) Moving layers and the emblem (ADR-0011) | – | yes |
| g) Favourites | 5 | unlimited |
| h) Saving | screen size, every format, "Show whole" and "Fill" | 2048 px, framing by hand, finish, SVG line drawing |
| i) Wallpaper | set once (Android), save for it (iPhone) | pair, daily; live (Android); widgets and Shortcuts action (iPhone) |
| j) Surprise and starters | all; starters with Pro features can be tried | – |

- **All styles free:** variety is the fun. Three or more layers, emblems, text and positions are fine work, which is what people pay for.
- **Two layers** make good mandalas on their own; richer starters show what more layers do.
- **Surprise mandalas** for free users use at most two layers and no Pro features, so they always save.
- The same line on both platforms; differences only where the system forces them (row i).

## 4. Price

One price on both platforms: **3.99 €**, one-time, no subscription, with Family Sharing on the iPhone (Android has no equivalent for in-app products). With this scope 2.99 € is cheap and 4.99 € steep for Android.

## 5. The Android release in review

State on 22 October 2026, read through the Play API: the production track has release 7 (1.3.1), sent for review with full rollout. A release that passes review goes live by itself, unless **managed publishing** is on.

1. **Mathis turns on managed publishing** in the Play Console (Publishing overview → Managed publishing). The release in review is still reviewed, but waits for "Publish" afterwards. Costs nothing and keeps every option.
2. Android 1.4 gets the new line (section 6) and replaces 1.3.1 in production; the first public version then already has it. A second review usually takes hours to a few days.
3. If 1.3.1 went live before step 1: people who installed it keep today's free scope (the app marks installs before 1.4 on its first start after the update, from existing app data). Nobody loses something they had.

## 6. Android 1.4 (about 2–3 days)

- Pro checks in `Editing.kt` and the save, favourite and wallpaper paths: count of layers, emblem, text layer, Pro effects, moved layers. One function `proFeaturesIn(settings)` in `core`, so the iPhone app uses the same rule.
- Pro marks in the layer list, emblem, text and effects panels; the Pro sheet lists what this mandala uses and offers to remove it.
- Favourites limit of 5 with a gentle hint at the sixth.
- Surprise mandalas within the free line when Pro is not owned.
- Starter favourites checked: which use Pro features (they stay, marked).
- Pro sheet and store listing: the new list; price 3.99 € for product `pro` in the Play Console (Mathis).
- Tests for `proFeaturesIn` in `core`; QA report on the phone.

## 7. Effect on the iPhone plan

PLAN-4 and ADR-0015 take this line and price instead of their own; everything else there stays (shared Kotlin core, SwiftUI, Shortcuts action and widgets, iOS 17).

## Questions

1. **Principle:** try freely, keeping needs Pro (section 2)? Recommendation: yes.
2. **Two layers free?** Or three? Recommendation: two.
3. **Free effects:** vignette, brightness, contrast, fade and grain? Recommendation: yes.
4. **Price 3.99 € on both platforms?** Recommendation: yes.
5. **Managed publishing now and 1.4 as the first public version** (section 5)? Recommendation: yes; it is your step in the Play Console.
6. **Early installers keep today's free scope**, should 1.3.1 go live first? Recommendation: yes.
