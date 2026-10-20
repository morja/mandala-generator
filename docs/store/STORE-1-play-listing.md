# STORE-1 · Google Play listing and Play Console answers

Prepared 20 October 2026 for the first Android release; entered in the Play Console the same day. Graphics in this folder: `play-icon-512.png` (512×512, 32-bit PNG), `play-feature-graphic.png` (1024×500), and six phone screenshots in `screenshots/` (1080×1920, cropped from Mathis' Galaxy S23 without the status bar): Flower of Life, Flow Field, Lotus emblem, quote, favourites, drawing. AI asset declaration: "Don't label assets" (icon and feature graphic are rendered by the app, screenshots are real).

## App details

| Field | Value |
|---|---|
| App name | Mandala Generator |
| Default language | English (United States); German as a translation |
| App or game | App |
| Free or paid | Free (with one in-app product) |
| Category | Art & Design |
| Contact email | apps@tearoom.one |
| Website | https://www.tearoom.one/en/apps/mandala-generator |
| Privacy policy | https://www.tearoom.one/en/apps/privacy/mandala-generator |
| Package name | `one.tearoom.mandala` (`applicationId` in `android/app/build.gradle.kts`; the Play Console app exists with it since 20 October 2026 and it cannot change. The Kotlin namespace stays `com.mathis.mandala.app`.) |

The two tearoom.one pages exist in the website repository (commit "Add Mandala Generator to the apps, with its privacy policy") but are not deployed yet.

## Store listing, English

**Short description** (max. 80 characters, here 63):

> Create glowing mandalas from curves, layers and sacred symbols.

**Full description:**

> Mandala Generator draws glowing mandalas from mathematical curves, light and symbols.
>
> Build each mandala from up to five layers. Every layer has its own style – spirographs, rose curves, sacred geometry, flow fields, 3D forms and more, 36 in all – with its own symmetry, palette and glow. Roll the dice for new combinations; the history arrows take you back.
>
> Place a symbol large in the centre: Om, the Tree of Life, the Flower of Life, a lotus, yin and yang, the Dharma wheel, an unalome, the triple moon or the Sri Yantra. Draw it as a line, an outline, filled or in beads, frame it with a ring, a lotus ring, square gates or an ensō, and give it a more organic, hand-drawn character.
>
> Choose a gradient, a sunburst, grain or one of your own pictures as background. Add bloom, stars or a vignette, a quote with a soft glow, or draw by hand in rotated symmetry.
>
> Save your mandalas to the gallery or share them. Keep favourites, and move them between your phone and the Mac version.
>
> The app is free. Pro is an optional one-time purchase, no subscription: it saves your mandalas at 2048 px for prints and wallpapers.
>
> No account, no ads, no tracking. Your mandalas stay on your device.
>
> Mandala Generator is open source under the GNU GPL 3.0: https://github.com/morja/mandala-generator

## Store listing, German

**Kurzbeschreibung** (max. 80 Zeichen, hier 71):

> Leuchtende Mandalas aus Kurven, Ebenen und heiligen Symbolen gestalten.

**Ausführliche Beschreibung:**

> Mandala Generator zeichnet leuchtende Mandalas aus mathematischen Kurven, Licht und Symbolen.
>
> Jedes Mandala entsteht aus bis zu fünf Ebenen. Jede Ebene hat ihren eigenen Stil – Spirographen, Rosenkurven, heilige Geometrie, Strömungsfelder, 3D-Formen und mehr, insgesamt 36 – mit eigener Symmetrie, Farbpalette und eigenem Leuchten. Der Würfel schlägt neue Kombinationen vor, mit den Verlaufspfeilen kommst du zurück.
>
> Setze ein Symbol groß in die Mitte: Om, Lebensbaum, Blume des Lebens, Lotus, Yin und Yang, das Dharma-Rad, ein Unalome, den dreifachen Mond oder das Sri Yantra. Zeichne es als Linie, als Umriss, gefüllt oder aus Perlen, rahme es mit einem Ring, einem Lotusring, einem Quadrat mit Toren oder einem Ensō und gib ihm einen organischeren, handgezeichneten Charakter.
>
> Wähle einen Verlauf, Strahlen, Körnung oder ein eigenes Bild als Hintergrund. Füge Bloom, Sterne oder eine Vignette hinzu, ein Zitat mit weichem Leuchten, oder zeichne von Hand in gedrehter Symmetrie.
>
> Speichere deine Mandalas in der Galerie oder teile sie. Behalte Favoriten und tausche sie zwischen Handy und Mac-Version aus.
>
> Die App ist kostenlos. Pro ist ein optionaler einmaliger Kauf ohne Abo: Damit speicherst du deine Mandalas mit 2048 px für Drucke und Hintergrundbilder.
>
> Kein Konto, keine Werbung, kein Tracking. Deine Mandalas bleiben auf deinem Gerät.
>
> Mandala Generator ist Open Source unter der GNU GPL 3.0: https://github.com/morja/mandala-generator

## In-app product

| Field | Value |
|---|---|
| Product ID | `pro` (must match `ProPurchases.kt`) |
| Type | One-time product, non-consumable |
| Name (EN / DE) | Mandala Generator Pro |
| Description (EN) | Save your mandalas at 2048 px for prints and wallpapers. One-time purchase, no subscription. |
| Beschreibung (DE) | Speichere deine Mandalas mit 2048 px für Drucke und Hintergrundbilder. Einmaliger Kauf, kein Abo. |
| Price | Open (ADR-0007 suggests 1 to 3 euros) |

Google Play lets you create in-app products only after a build that contains the billing permission has been uploaded (any testing track).

## App content (Policy → App content)

These answers follow what the app does today (checked in `android/app` on 20 October 2026). Check them against Google's questions when filling in; the wording in the console changes.

- **Privacy policy:** the URL above.
- **Ads:** No, the app contains no ads.
- **App access:** All functionality is available without special access (no login). Pro needs a purchase; reviewers can use the free features.
- **Content rating (IARC questionnaire):** Category "All other app types". No violence, sexual content, profanity, drugs, gambling or user-generated content shared with others; no user interaction or data sharing; digital purchases: yes (Pro). Expected rating: Everyone / PEGI 3 / USK 0.
- **Target audience:** 13 and over. (Choosing younger age groups brings the app under the Families policy, which the app does not need.)
- **News app:** No.
- **Data safety:**
  - Does the app collect or share user data? **No.** The app's own code uses no network and sends nothing to the developer; all data stays on the device. The built app does contain the permissions `INTERNET` and `ACCESS_NETWORK_STATE`: they come from the Play Billing Library and Google Play services, which talk to Google Play for the purchase (checked in the merged manifest of the 1.0 release build, 20 October 2026).
  - Purchases go through Google Play; Google's own processing is covered by Google, not declared by the app. Check Google's current guidance on the Play Billing Library when filling in.
  - Is data encrypted in transit? Not applicable (no data transferred by the app).
  - Can users request deletion? Not applicable (no data collected); data is deleted with the app's storage.
- **Government app, financial features, health:** No.

## Building the app bundle (AAB)

Version 1.0 (`versionName` "1.0", `versionCode` 1, in `android/app/build.gradle.kts`). Raise `versionCode` by one for every upload.

1. Create `android/keystore.properties` (git-ignored, like `*.jks`; never commit it):

   ```properties
   storeFile=~/.keystore/mandalagenerator-upload.jks
   storePassword=…
   keyAlias=upload
   keyPassword=…
   ```

   `storeFile` defaults to `~/.keystore/mandalagenerator-upload.jks` and `keyAlias` to `upload`; `keyPassword` defaults to `storePassword`. A leading `~/` means the home folder.
2. Build the bundle from the repository root:

   ```bash
   android/gradlew -p android :app:bundleRelease
   ```

3. The bundle lands in `android/app/build/outputs/bundle/release/app-release.aab`. Check that it is signed with the upload key: `jarsigner -verify -verbose -certs android/app/build/outputs/bundle/release/app-release.aab` shows the key's name (CN).

Without `keystore.properties`, release builds are signed with the local debug key, so `assembleRelease` keeps working for tests on devices; Google Play rejects a bundle signed that way. Release builds are shrunk with R8 (minify and resource shrinking); the R8 mapping file is part of the bundle, so the Play Console shows readable stack traces.

## What Mathis does himself

1. Create the upload key (keep it outside the repository and back it up), unless it exists already, and fill `android/keystore.properties` (see above):
   `keytool -genkeypair -v -keystore ~/.keystore/mandalagenerator-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
2. In the Play Console: create the app (name, default language, app, free) and accept the declarations.
3. Enrol in Play App Signing with the first upload of an app bundle (AAB).
4. Deploy tearoom.one so that the privacy policy URL works.
5. Closed test if the account needs it (personal accounts created after 13 November 2023: 12 testers for 14 days; verify on Google's help pages).
6. Create the product `pro` and set its price after the first upload.
7. Screenshots: at least two phone screenshots (and tablet screenshots, recommended) once the emblem layer is in the app.
