# ADR-0022 · Pro purchase through the shared tearoom-android-kit

Status: proposed, 26 October 2026. The library and the composite build are Mathis' decision of 26 October 2026 (tearoom-android-kit ADR-0001, ADR-0002); open for Mathis: the public build question under Consequences (`docs/APPROVALS.md`). Replaces the purchase part of ADR-0007 ("How it is built").

## Decision

1. **Shared code.** The Pro purchase uses `one.tearoom:unlock` from `~/Work/Projects/tearoom-android-kit`, the hardened Play Billing code of Lazy Moves (timeouts, bounded retries, late answers ignored, acknowledgement retries, refunds, pending payments, restore). `ProPurchases.kt` is gone; `Pro.kt` holds the one app-wide instance and maps the library's codes to Dahlia's strings.
2. **Build.** `android/settings.gradle.kts` includes the library with `includeBuild`; the path comes from the Gradle property `tearoomKitPath` (e.g. in `~/.gradle/gradle.properties`), else `~/Work/Projects/tearoom-android-kit`. Google Play Billing moves from 8.3.0 to 9.1.0 (plain `billing`, through the library).
3. **Unchanged:** product `pro` (purchase option `buy`, legacy compatible, checked read-only through the Play API on 26 October 2026); the cache in shared preferences `pro`, key `owned`, so a phone that owns Pro keeps it on update, also offline; `pro_for_testing` (debug and fast count as Pro, free does not) as the library's `debugUnlocked`; the daily wallpaper and the widget read the cache (`Pro.isUnlocked`); what is free and what is Pro (ADR-0016).
4. **New for users:**
   - Play is asked on every resume, not only at start (Google's guidance: purchases on another device, completed pending payments, refunds).
   - The Pro sheet has "Restore purchase". If Play knows no purchase, the text says that Pro may have been bought with another Google account and how to switch.
   - The sheet says why there is no price (Play unreachable, Play Billing unavailable, product not offered to this account) and offers "Try again"; the price shown stays while it reloads.
   - A pending payment shows in the sheet and blocks a second purchase; it unlocks once paid.
   - A refund or revocation removes Pro the next time Play answers; an unreachable Play never removes it.
   - Purchase and restore feedback shows inside the sheet while it is open, otherwise as a message. Google's raw error text and code are no longer shown.
5. **Signature check:** the library can check purchases against the Play licence key (`publicKeyBase64` in `Pro.kt`). It stays off (`null`) until Mathis copies the key from the Play Console (Monetisation setup, Licensing); a wrong key would hide every purchase, so it needs a licence-test purchase before release.

## Reasons

- Dahlia is the only live app and had the simplest billing: no timeouts, no retries, no restore, no acknowledgement retry, a price that could stay missing until the next start. Lazy Moves' version was reviewed and tested against these failures (billing hardening, 16 October 2026).
- One shared implementation instead of three copies (Lazy Moves, Weatherfold, Dahlia).
- Nothing has been sold yet, so this is the cheapest moment to switch; the old cache key is kept anyway, since it costs nothing.

## Consequences

- **Public source.** The repository is public under GPL-3.0 (ADR-0008, ADR-0009), but tearoom-android-kit is local only. A fresh clone does not build (the build stops with a message naming the missing path), and "anyone may build the app from source" (ADR-0008) no longer holds until the library is published, for example as a public repository under a GPL-compatible licence. Mathis decides before this branch is released (`docs/APPROVALS.md`).
- The app builds against the current state of the library checkout; note the library commit with each release.
- Before the next release: license-test purchase on a Play-installed build (purchase, pending with the slow test card, cancel, restore with the same and with another account, refund/revocation, offline), and a look at the Pro sheet in a long language (German, Russian) at 360 dp.
