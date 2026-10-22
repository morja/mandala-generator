# ADR-0016 · One line between free and Pro for Android and iPhone

Status: proposed, 22 October 2026 ([PLAN-5](../PLAN-5-one-pro-line.md)). Would replace the free part of ADR-0007 and point 1 of ADR-0014 ("Nothing free is taken away for Pro").

## Decision

1. **One line for both apps.** Android and iPhone share what is free and what is Pro; they differ only where the system forces it (live wallpaper on Android; widgets and a Shortcuts action on the iPhone).
2. **Everyone may try, keeping is Pro.** Pro features can be used live in the editor. Saving, sharing, keeping as a favourite or setting as wallpaper a mandala that uses one needs Pro; the Pro sheet offers to remove them instead.
3. **Free:** up to two layers, all styles and palettes, the effects vignette, brightness, contrast, fade and grain, five favourites, saving at screen size in every format with "Show whole" and "Fill", setting a wallpaper once, all surprise mandalas and starters.
4. **Pro:** up to five layers, emblems, text and quotes, all other effects, moving layers and the emblem, unlimited favourites, 2048 px, framing by hand, the finish, SVG line drawing, the wallpaper pair and daily wallpaper, the live wallpaper (Android), widgets and the Shortcuts action (iPhone).
5. **One rule in `core`:** `proFeaturesIn(settings)` lists the Pro features a mandala uses; both apps ask it.
6. **Price:** 3.99 €, one-time, on both platforms. No subscription, no ads, no watermark.
7. **Early installers** of Android 1.3.1, if it goes live before 1.4, keep the free scope they had.

## Reasons

- With only the result behind Pro, people who play never meet it. The making is where the wish for more arises: a third layer, an emblem, a quote.
- Styles stay free, since variety is what makes the app fun; two layers already make good mandalas.
- Trying before buying sells better than hidden features and does not feel like a trap, since nothing made for free is ever locked.
- One line on both platforms keeps store texts, the Pro sheet and support the same.

## Consequences

- Mac files with more than two layers or with an emblem open and can be edited; saving them needs Pro. Accepted: the macOS app has had a handful of downloads.
- Surprise mandalas stay within the free line when Pro is not owned.
- Anyone can still build Pro from source (ADR-0008).
