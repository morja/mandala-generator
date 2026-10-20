# ADR-0007 · Free app with a one-time Pro unlock; public-domain quotes only

Status: accepted, 20 October 2026 (Mathis' decision)

## Decision

- **Free:** everything the app does, including saving and sharing at 1024 px.
- **Pro, a one-time in-app purchase** (Google Play Billing, about 1 to 3 euros, no subscription): saving at higher resolution (2048 px). More Pro features may follow. Mathis' Play Console account and payments profile exist.
- **Quotes:** the app ships only quotes in the public domain: authors and translators who died more than 70 years ago, quoted word for word from Project Gutenberg editions (source per group in `Quotes.kt`). The macOS app's collection (modern translations, living authors) is not shipped. Users can write their own text with an author and keep it in "Your quotes" (`my-quotes.json` in the app's files). Starter favourites with quotes use public-domain ones too.

## How it is built

- **Large export:** 2048 px drawn without supersampling (`RenderOptions.supersample = false`). The normal export already draws at 2048 internally and scales down to 1024; the large one keeps that image. Same memory as the normal export (checked in `MemoryBudgetTest`), and it looks like the preview with twice the detail. The macOS way (draw at 4096, scale down) does not fit into a phone's memory; 4096 px would need tiled rendering.
- **Purchase:** Google Play Billing Library 8.3.0, one-time product id `pro` (`ProPurchases.kt`). Ownership is cached in shared preferences and checked with Google Play on every start; purchases are acknowledged. Debuggable builds count as Pro so the feature can be tried without a Play install.
- **Menu:** "Save large (2048 px)" opens the Pro sheet (what Pro adds, price from Google Play) until Pro is owned.

## Reasons

- A paywall on saving at all would feel like a trap and stop people from sharing their images, which is the app's best advertising. Higher resolution (prints, wallpapers) is a natural extra and costs real work to make fit into a phone's memory.
- 512 px for free was considered and rejected for now: blurry on a 1080 px phone screen.
- Quotes from living authors or in modern translations are fine in a private app, but not clearly in one that sells something.

## Open

- The price (set in the Play Console for product `pro`).
- ~~Store signing: release builds are still signed with the local debug key; Play needs an upload key.~~ Upload signing through `android/keystore.properties` (20 October 2026, STORE-1); the key itself is Mathis'.
- Whether 4096 px is worth tiled rendering later.
