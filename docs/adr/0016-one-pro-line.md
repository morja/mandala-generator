# ADR-0016 · One line between free and Pro for Android and iPhone

Status: accepted, 22 October 2026 (Mathis, [PLAN-5](../PLAN-5-one-pro-line.md), question by question). Amended the same day (Mathis): three free layers instead of two, and no limit on favourites; first in Android 1.4.1. Point 4 amended 12 November 2026 for the iPhone widgets (Mathis, PLAN-4 F7). Replaces the free part of ADR-0007 and point 1 of ADR-0014 ("Nothing free is taken away for Pro").

## Decision

1. **One line for both apps.** Android and iPhone share what is free and what is Pro; they differ only where the system forces it (live wallpaper on Android; widgets and a Shortcuts action on the iPhone).
2. **Everyone may try, keeping is Pro.** Pro features can be used live in the editor; a Pro mark shows on the mandala as soon as it uses one. Saving, sharing or setting as wallpaper a mandala that uses one needs Pro; the Pro sheet offers to remove them instead. Favourites are not gated (amended 24 October 2026, Mathis): a favourite keeps its Pro features and opens for everyone, like the starters; the line is met again when it is saved, shared or set as wallpaper. Before, the sheet removed the Pro features when keeping, and the favourite got the Pro preview with a stripped mandala, which lost the work just done.
3. **Free:** up to three layers, all styles and palettes (own palettes too, ADR-0021), the whole effects layer, as many favourites as you like, saving at screen size in every format with "Show whole" and "Fill", setting a wallpaper by hand (as often as you like), all surprise mandalas and starters.
4. **Pro:** up to five layers, emblems, text and quotes, moving layers and the emblem, 2048 px, framing by hand, the finish, SVG line drawing, the wallpaper pair and daily wallpaper, the live wallpaper (Android), the Shortcuts action (iPhone). *Amended 12 November 2026 (Mathis, PLAN-4 F7 (a)):* the iPhone widgets are free as on Android (ADR-0020), staying on the first ticked favourite or one fixed favourite; only the daily turn of the widgets is Pro, on both phones. Before, the iPhone widgets were Pro as a whole, which would have split the two apps against point 1 and kept the widget from advertising the app on free phones. *Since 26 October 2026 (Mathis, UX-1 12):* the moving picture, a video of the layers turning; anyone may switch the save sheet to "Moving" and watch it, saving and sharing the video is Pro. Also since then: the quote moving with the mandala, now a switch inside "Adjust" (rarely used, Mathis).
5. **One rule in `core`:** `proFeaturesIn(settings)` lists the Pro features a mandala uses; both apps ask it.
6. **Price:** 2.99 €, one-time, on both platforms. No subscription, no ads, no watermark. A rise to 3.99 € later is open, since buyers keep Pro. *Amended 12 November 2026 (Mathis):* the iPhone app starts at 3.99 € (base country Germany, Apple's comparable prices elsewhere); Android stays at 2.99 €. The reason given below for one price (same store texts, 3.99 € makes people hesitate) is accepted as the cost: a price is easier to lower than to raise, and the App Store listing starts without reviews either way.
7. **Android 1.4 is the first public version** with this line; the unsent production release 1.3.1 is not published.

## Reasons

- With only the result behind Pro, people who play never meet it. The making is where the wish for more arises: a third layer, an emblem, a quote.
- Styles and effects stay free, since variety and the finished look are what make the app fun and what people share. Three layers make a finished-looking mandala, so the free app looks like the screenshots; two left the free mandalas visibly thinner (changed from two the same day). Favourites are not limited: a cap there annoys exactly the engaged users who would buy, adds little next to emblems, text and the wallpapers, and invites "cannot even save" reviews. The starters use the effects layer as one look, so a line inside it would make most of them Pro for little gain.
- Trying before buying sells better than hidden features and does not feel like a trap, since nothing made for free is ever locked; the mark on the mandala avoids a surprise at saving.
- One line and one price on both platforms keep store texts, the Pro sheet and support the same. 2.99 € is a spontaneous purchase; 3.99 € makes people hesitate.

## Consequences

- 14 of the 21 built-in starters are fully free (10 with two layers); the others show Pro features and can be tried.
- Mac files with more than three layers, an emblem or text open and can be edited; saving them needs Pro. Accepted: the macOS app has had a handful of downloads.
- Surprise mandalas stay within the free line when Pro is not owned.
- Anyone can still build Pro from source (ADR-0008).
