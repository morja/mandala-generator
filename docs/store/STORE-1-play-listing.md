# STORE-1 · Google Play listing and Play Console answers

Prepared 20 October 2026 for the first Android release; entered in the Play Console the same day. Updated 22 October 2026 for version 1.1 and later (save formats, wallpaper, what Pro adds, starter favourites); the Play Console still has the 20 October texts. Graphics in this folder: `play-icon-512.png` (512×512, 32-bit PNG), `play-feature-graphic.png` (1024×500), and six phone screenshots in `screenshots/` (1080×1920, cropped from Mathis' Galaxy S23 without the status bar): Flower of Life, Flow Field, Lotus emblem, quote, favourites, drawing. AI asset declaration: "Don't label assets" (icon and feature graphic are rendered by the app, screenshots are real).

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
> Save your mandalas to the gallery as a square, in the shape of your screen or in portrait and landscape ratios such as 4:5, 2:3 and 16:9, set one as your wallpaper, or share it. Start with 21 example favourites, keep your own, and move them between your phone and the Mac version.
>
> The app is free. Pro is an optional one-time purchase, no subscription: it saves your mandalas at 2048 px for prints and wallpapers, lets you move and zoom the mandala in the frame, and move layers and the emblem.
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
> Speichere deine Mandalas quadratisch, im Format deines Bildschirms oder in Hoch- und Querformaten wie 4:5, 2:3 und 16:9 in der Galerie, setze eines als Hintergrundbild oder teile es. Starte mit 21 Beispiel-Favoriten, behalte eigene und tausche sie zwischen Handy und Mac-Version aus.
>
> Die App ist kostenlos. Pro ist ein optionaler einmaliger Kauf ohne Abo: Damit speicherst du deine Mandalas mit 2048 px für Drucke und Hintergrundbilder, verschiebst und zoomst das Mandala im Bildausschnitt und verschiebst Ebenen und Emblem.
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
| Description (EN) | Save at 2048 px for prints and wallpapers, frame the mandala by hand, and move layers and the emblem. One-time purchase, no subscription. |
| Beschreibung (DE) | Speichere mit 2048 px für Drucke und Hintergrundbilder, richte das Mandala im Bild aus und verschiebe Ebenen und Emblem. Einmaliger Kauf, kein Abo. |
| Price | 1.99 € in Germany (US 1.89 $), set in the Play Console |

In the Play Console on 22 October 2026: the product exists and is active, with the 20 October English description only; the German listing and the new description are still to be entered.

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

Version and code are in `android/app/build.gradle.kts` (on 22 October 2026: 1.1.0, code 3, which is on the internal track). Raise `versionCode` by one for every upload.

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

## Screenshots with the starter favourites

The six screenshots from 20 October show favourites that are no longer among the starters and the editor before the fold button. New set, taken on the S23 like before (1080 × 1920 without the status bar), each from a starter favourite:

1. Om emblem (favourite 2), Emblem tab open.
2. Flower of Life emblem (favourite 6), Emblem tab open.
3. Lotus with the Gibran quote (favourite 9), Text tab open.
4. The Rumi quote "This is the House of Love" (favourite 4), Text tab open.
5. Favourites sheet, scrolled to the top.
6. Flow Field (favourite 17), Layers tab open.
7. Save sheet with the ratio menu and Set as wallpaper.
8. Drawing in rotated symmetry (as now).

Google Play takes up to eight phone screenshots. All quotes shown are public domain (ADR-0007): Gibran's The Prophet (1923, Gibran died 1931), Rumi in Nicholson's translation (1898, Nicholson died 1945).

## What Mathis does himself

State on 22 October 2026, read from the Play Console through the API: production, beta and alpha are empty; the internal track has 1.1.0 (code 3); the English listing has the icon, the feature graphic and six phone screenshots (the German listing uses them too); the product `pro` is active at 1.99 €. The developer account is older than November 2023, so Google's closed test with 12 testers does not apply.

1. Done: upload key and `android/keystore.properties`. To create one again:
   `keytool -genkeypair -v -keystore ~/.keystore/mandalagenerator-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
2. Done: the app in the Play Console, Play App Signing (with the first upload), tearoom.one with the privacy policy, the product `pro` with its price.
3. Take the new screenshots (list above) and replace the six in the console.
4. Enter the updated listing texts above (English and German) and the product's new description, also in German.
5. Fill in Data safety, Content rating and Target audience with the answers above, if not done yet (the API cannot read them).
6. Build a new bundle from `main` (raise the version, for example 1.2.0, code 4), put it on the internal track and buy Pro once with a licence tester.
7. Promote that release to production and send it for review.

Tablet screenshots are recommended, not required.
