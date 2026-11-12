# Dahlia – Mandala Maker

Session: master chat · 12 November 2026

## STORE-2: App Store Connect record

What exists in Apple's consoles for the iPhone app, and what is still Mathis' to do there. Team 6735ZK5WKV.

### Created on 12 November 2026 (in Chrome, with Mathis' OK)

| What | Value |
|---|---|
| App ID (Certificates, Identifiers & Profiles) | `one.tearoom.mandala`, explicit, description "Dahlia Mandala Maker", no extra capabilities yet (App Groups follow with WP-10) |
| App record | "Dahlia – Mandala Maker", iOS, primary language English (U.S.), SKU `dahlia-ios`, Apple ID 6833903967, all users have access |
| Version | iOS 1.0, "Prepare for Submission", nothing filled in |
| In-app purchase | "Dahlia Pro", non-consumable, product id `one.tearoom.mandala.pro`, Apple ID 6833904728, status "Prepare for Submission" |
| Family Sharing | on (F4 a; cannot be turned off again) |
| Price | 3.99 € with Germany as base country, Apple's comparable prices in the other 174 countries (US 3.99 $), may adjust automatically (ADR-0016, amended 12 November 2026) |
| Availability | all 175 countries and regions |
| Display name and description, English (U.S.) | Dahlia Pro · "More layers, emblems, text and 2048 px. Pay once." |
| Display name and description, German | Dahlia Pro · "Mehr Ebenen, Embleme, Text und 2048 px. Einmal zahlen." |

The product id is `one.tearoom.mandala.pro`, not `pro` as on Play: an Apple product id cannot be reused once taken, and a bare `pro` would be in the way of Mathis' other apps. The app maps the id per platform (WP-9).

Apple allows 55 characters for the description, so the Play text (STORE-1) was shortened.

### Done by Mathis on 12 November 2026

- Trader declaration for the EU, Paid Apps Agreement, bank account and tax form submitted. The agreement shows "Processing" until Apple has checked it.

### Open, Mathis' own steps

- Wait until the Paid Apps Agreement shows "Active" (usually one to three days).

- App Store Small Business Program (15 % instead of 30 %).

### TestFlight

- Test information entered on 12 November 2026: English beta description, feedback and contact email apps@tearoom.one, marketing and privacy policy links (the privacy page still describes the Android app; it must name the iPhone before the store release), review notes on how to reach the Pro sheet.

- First build 1.0 (1) uploaded on 12 November 2026 from `main` at c33adfc (Release archive, automatic signing, App Group on device and widget extension). Internal group "Team" (Mathis, automatic distribution) and external group "Freunde" created the same day; build 1.0 (1) submitted to Beta App Review for "Freunde" ("Waiting for Review"), without sign-in, with English "What to Test" notes. The External Testing section only appeared after the first internal group existed.

- Upload note: `xcodebuild -exportArchive` fails with "Copy failed" when Homebrew's `rsync` comes first on the PATH; run it with `/usr/bin` first.

### Open, with the packages

- Review screenshot and review notes for the product, the app's privacy label, age rating, listing texts and screenshots (WP-13); the first in-app purchase is submitted together with the first app version.

- App Group `group.one.tearoom.mandala` and the capability on the App ID (WP-10).
