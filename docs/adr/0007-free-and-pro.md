# ADR-0007 · Free app with a one-time Pro unlock; public-domain quotes only

Status: accepted, 20 October 2026 (Mathis' decision)

## Decision

- **Free:** everything the app does, including saving and sharing at 1024 px.
- **Pro, a one-time in-app purchase** (Google Play Billing, about 1 to 3 euros, no subscription): saving at higher resolution (2048 px). More Pro features may follow. Mathis' Play Console account and payments profile exist.
- **Quotes:** the app ships only quotes in the public domain: authors and translators who died more than 70 years ago, quoted word for word from Project Gutenberg editions (source per group in `Quotes.kt`). The macOS app's collection (modern translations, living authors) is not shipped. Users can write their own text with an author and keep it in "Your quotes" (`my-quotes.json` in the app's files). Starter favourites with quotes use public-domain ones too.

## Reasons

- A paywall on saving at all would feel like a trap and stop people from sharing their images, which is the app's best advertising. Higher resolution (prints, wallpapers) is a natural extra and costs real work to make fit into a phone's memory.
- 512 px for free was considered and rejected for now: blurry on a 1080 px phone screen.
- Quotes from living authors or in modern translations are fine in a private app, but not clearly in one that sells something.

## Open

- Price and the Play product id.
- Whether 4096 px is worth tiled rendering later.
