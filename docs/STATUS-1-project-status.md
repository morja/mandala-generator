# STATUS-1 · Project status and road to the Play Store

20 October 2026.

## Where things are

**Repository.** Both apps live in this public GPL-3.0 repository (ADR-0008, ADR-0009): the macOS app in `mac/`, the Android app in `android/`. The macOS test build on GitHub runs green. The old private repository `morja/mandala-generator-android` still exists and can be deleted once Mathis has checked this one.

**macOS app.** All 36 layer styles and every layer type. Emblem layer (RES-1): nine motifs (Om, Tree of Life, Flower of Life with Seed/Flower/Fruit/Metatron, Lotus, Yin Yang, Dharma Wheel, Unalome, Triple Moon, Sri Yantra), four renderings (line, outline, filled, beads), six frames (none, ring, double ring, lotus ring, square gates, ensō), Organic and rotation. Public-domain quotes only, plus the user's own quotes. App icon shared with Android. Every slider resets to its default on a double-click.

**Android app.** Renderer fully ported and parity-tested against the macOS renderer: 36 styles, all backgrounds, effects, text, drawing and graffiti layers. Editor for phone and tablet, favourites compatible with the macOS app (23 starter favourites), history, saving to the gallery and sharing, Pro unlock for saving at 2048 px (ADR-0007), public-domain quotes, app icon.

**In progress.** Port of the emblem layer to Android, with double-tap-to-reset on the Android sliders.

## Missing for the Play Store

Ordered by lead time.

1. **Closed test, if it applies.** According to several guides (not yet checked against Google's own pages), personal developer accounts created after 13 November 2023 must run a closed test with 12 testers for 14 days in a row before the first production release. Open: which kind of account Mathis has and when it was created.
2. **Signing.** Release builds are still signed with the debug key. Needed: an upload key kept outside the repository, Play App Signing, and an app bundle (AAB).
3. **Play Console declarations.** Privacy policy URL (required even though the app collects nothing), data safety form, content rating, target audience.
4. **GPL inside the app.** There is no About screen with the licence, a link to the source and the licences of the bundled libraries (AndroidX, Play Billing).
5. **Store listing.** Title, short and full description (English and German), 512 px icon, 1024×500 feature graphic, phone and tablet screenshots.
6. **Pro product.** Product `pro` in the Play Console and its price (open in ADR-0007). Play only allows in-app products after a build with the billing permission has been uploaded.
7. **Smaller items.** Version 0.1 → 1.0, R8 shrinking for release builds, tests on real devices. `targetSdk` is 36; to be checked against Google's current requirement.
8. **Housekeeping.** Older open approvals in `docs/APPROVALS.md` (ADR-0003 to ADR-0006, packages, fonts).

## Next steps

1. Finish and merge the emblem port for Android.
2. Store preparation in code: About screen, privacy policy, release configuration with upload signing, store texts, a checklist of Mathis' own steps in the Play Console.
3. Play Console: create the app entry and fill in what can be prepared before the first upload.
4. Clarify the account type, and start the closed test early if it applies.
