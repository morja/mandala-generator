# STORE-1 · Google Play listing and Play Console answers

Prepared 20 October 2026 for the first Android release; entered in the Play Console the same day. Updated 22 October 2026 for version 1.1 and later (save formats, wallpaper, what Pro adds, starter favourites) and entered in the Play Console the same day; the screenshots there are still the 20 October ones. Graphics in this folder: `play-icon-512.png` (512×512, 32-bit PNG), `play-feature-graphic.png` (1024×500), and seven phone screenshots in `screenshots/` (1080×2160, the longest side Google allows, cropped from Mathis' Galaxy S23 without the status bar and the gesture bar; taken 22 October 2026): layers, Om emblem, a mandala as lock screen wallpaper, quote, favourites, save formats, new mandala with preview. AI asset declaration: "Don't label assets" (icon and feature graphic are rendered by the app, screenshots are real).

Renamed on 22 October 2026 from "Mandala Generator" to "Dahlia – Mandala Maker" (ADR-0012): app name, both full descriptions and the Pro product name. The title (English and German) was set in the Play Console through the API the same day; the Pro product name (English and German) as well, with descriptions, prices and state unchanged. The full descriptions there still say "Mandala Generator". The website pages and their URLs still use "mandala-generator". The SVG line drawing (ADR-0013) was added to the Pro paragraph of both full descriptions on the branch `feature/svg-export`; not in the Play Console, and the Pro product description there does not mention it yet.

## App details

| Field | Value |
|---|---|
| App name | Dahlia – Mandala Maker (22 characters, max. 30; ADR-0012) |
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

> Dahlia draws glowing mandalas from mathematical curves, light and symbols.
>
> Build each mandala from up to five layers. Every layer has its own style – spirographs, rose curves, sacred geometry, flow fields, 3D forms and more, 36 in all – with its own symmetry, palette and glow. Roll the dice for new combinations; the history arrows take you back.
>
> Place a symbol large in the centre: Om, the Tree of Life, the Flower of Life, a lotus, yin and yang, the Dharma wheel, an unalome, the triple moon or the Sri Yantra. Draw it as a line, an outline, filled or in beads, frame it with a ring, a lotus ring, square gates or an ensō, and give it a more organic, hand-drawn character.
>
> Choose a gradient, a sunburst, grain or one of your own pictures as background. Add bloom, stars or a vignette, a quote with a soft glow, or draw by hand in rotated symmetry.
>
> Save your mandalas to the gallery as a square, in the shape of your screen or in portrait and landscape ratios such as 4:5, 2:3 and 16:9, showing the whole mandala or filling the frame, set one as your wallpaper, or share it. Start with 21 example favourites, keep your own, and move them between your phone and the Mac version.
>
> The app is free. Pro is an optional one-time purchase, no subscription: it saves your mandalas at 2048 px for prints and wallpapers, lets you move and zoom the mandala in the frame, move layers and the emblem, and save line drawings as SVG for plotters and prints. For your phone it adds a finish for brightness, contrast and saturation, home and lock screen as a pair with the home screen a little darker, a new wallpaper from your favourites every morning, and a live wallpaper that turns slowly, breathes and drifts through its colours.
>
> No account, no ads, no tracking. Your mandalas stay on your device.
>
> Dahlia is open source under the GNU GPL 3.0: https://github.com/morja/mandala-generator

## Store listing, German

**Kurzbeschreibung** (max. 80 Zeichen, hier 71):

> Leuchtende Mandalas aus Kurven, Ebenen und heiligen Symbolen gestalten.

**Ausführliche Beschreibung:**

> Dahlia zeichnet leuchtende Mandalas aus mathematischen Kurven, Licht und Symbolen.
>
> Jedes Mandala entsteht aus bis zu fünf Ebenen. Jede Ebene hat ihren eigenen Stil – Spirographen, Rosenkurven, heilige Geometrie, Strömungsfelder, 3D-Formen und mehr, insgesamt 36 – mit eigener Symmetrie, Farbpalette und eigenem Leuchten. Der Würfel schlägt neue Kombinationen vor, mit den Verlaufspfeilen kommst du zurück.
>
> Setze ein Symbol groß in die Mitte: Om, Lebensbaum, Blume des Lebens, Lotus, Yin und Yang, das Dharma-Rad, ein Unalome, den dreifachen Mond oder das Sri Yantra. Zeichne es als Linie, als Umriss, gefüllt oder aus Perlen, rahme es mit einem Ring, einem Lotusring, einem Quadrat mit Toren oder einem Ensō und gib ihm einen organischeren, handgezeichneten Charakter.
>
> Wähle einen Verlauf, Strahlen, Körnung oder ein eigenes Bild als Hintergrund. Füge Bloom, Sterne oder eine Vignette hinzu, ein Zitat mit weichem Leuchten, oder zeichne von Hand in gedrehter Symmetrie.
>
> Speichere deine Mandalas quadratisch, im Format deines Bildschirms oder in Hoch- und Querformaten wie 4:5, 2:3 und 16:9 in der Galerie, ganz sichtbar oder bildfüllend, setze eines als Hintergrundbild oder teile es. Starte mit 21 Beispiel-Favoriten, behalte eigene und tausche sie zwischen Handy und Mac-Version aus.
>
> Die App ist kostenlos. Pro ist ein optionaler einmaliger Kauf ohne Abo: Damit speicherst du deine Mandalas mit 2048 px für Drucke und Hintergrundbilder, verschiebst und zoomst das Mandala im Bildausschnitt, verschiebst Ebenen und Emblem und speicherst Linienzeichnungen als SVG für Plotter und Drucke. Fürs Handy kommen dazu: ein letzter Schliff für Helligkeit, Kontrast und Sättigung, Start- und Sperrbildschirm als Paar mit etwas dunklerem Startbildschirm, jeden Morgen ein neues Hintergrundbild aus deinen Favoriten und ein Live-Hintergrund, der sich langsam dreht, atmet und seine Farben wandelt.
>
> Kein Konto, keine Werbung, kein Tracking. Deine Mandalas bleiben auf deinem Gerät.
>
> Dahlia ist Open Source unter der GNU GPL 3.0: https://github.com/morja/mandala-generator

## In-app product

| Field | Value |
|---|---|
| Product ID | `pro` (must match `ProPurchases.kt`) |
| Type | One-time product, non-consumable |
| Name (EN / DE) | Dahlia Pro |
| Description (EN) | 2048 px images, framing by hand, moving layers, SVG line drawings, brightness and contrast, home and lock screen as a pair, a daily and a live wallpaper. One-time purchase. |
| Beschreibung (DE) | 2048 px, Mandala ausrichten, Ebenen verschieben, SVG-Linien, Helligkeit und Kontrast, Start- und Sperrbildschirm als Paar, tägliches und Live-Hintergrundbild. Einmaliger Kauf. |
| Price | 2.99 € in Germany from version 1.3 (before 1.99 €), set in the Play Console |

In the Play Console since 22 October 2026: active, with these descriptions in English and German.

Google Play lets you create in-app products only after a build that contains the billing permission has been uploaded (any testing track).

## Release notes for 1.3.0 (code 6)

English (255 characters, max. 500):

> Version 1.3: Pro now dims or brightens your wallpaper, sets home and lock screen as a pair, changes the wallpaper every morning and adds a live wallpaper that turns slowly. Saved portraits now fill the screen or show the whole mandala, without hard edges.

German (301 characters):

> Version 1.3: Pro dunkelt dein Hintergrundbild ab oder hellt es auf, setzt Start- und Sperrbildschirm als Paar, wechselt das Hintergrundbild jeden Morgen und bringt einen Live-Hintergrund, der sich langsam dreht. Hochformate füllen jetzt den Bildschirm oder zeigen das ganze Mandala, ohne harte Kanten.

No new declarations in App content: the daily wallpaper's `RECEIVE_BOOT_COMPLETED` and the live wallpaper service collect no data.

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

## Screenshots

Taken on 22 October 2026 with the starter favourites and the current editor. The order starts with what the app shows first, the layers (Mathis' choice):

1. Hypocycloid, Universe and Phyllotaxis (favourite 21), Layers tab.
2. Om emblem (favourite 2), Emblem tab.
3. A mandala set as lock screen wallpaper (Menu > Wallpaper), without widgets.
4. The Rumi quote "This is the House of Love" (favourite 4), Text tab.
5. Favourites sheet, from the top.
6. Save sheet in the screen format, with Save and Save large.
7. Menu > New layers: the new mandala next to the current one before it replaces it.

No drawing screenshot (Mathis, 22 October 2026). Samsung marks the lock screen with "AI-generated content" at the bottom; the app writes no such metadata (the saved PNG has only IHDR, sBIT and sRGB), and the crop removes the label.

`docs/mocks/DM-8-store-preview.html` shows the listing with these texts and screenshots as a local page (light and dark, English and German), since Google shows the real page only after the first review.

The German listing has no screenshots of its own and shows these. All quotes shown are public domain (ADR-0007): Rumi in Nicholson's translation (1898, Nicholson died 1945), and in the favourites Gibran's The Prophet (1923, Gibran died 1931) and Whitman.

## What Mathis does himself

State on 22 October 2026, read from the Play Console through the API: production, beta and alpha are empty; the internal track has 1.1.0 (code 3); the English listing has the icon, the feature graphic and six phone screenshots (the German listing uses them too); the product `pro` is active at 1.99 €; the listing texts and the product description were updated the same day. The developer account is older than November 2023, so Google's closed test with 12 testers does not apply.

1. Done: upload key and `android/keystore.properties`. To create one again:
   `keytool -genkeypair -v -keystore ~/.keystore/mandalagenerator-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
2. Done: the app in the Play Console, Play App Signing (with the first upload), tearoom.one with the privacy policy, the product `pro` with its price.
3. Done on 22 October 2026: the seven new screenshots, uploaded through the API in place of the six from 20 October; the same day replaced by the set above.
4. Done on 22 October 2026 (through the API): the listing texts above in English and German, and the product's new description in English and German.
5. Done on 20 October 2026 (checked in the console on 22 October): all ten App content declarations. Data safety "doesn't collect or share data" (data that Google Play's billing collects itself need not be declared, Play Console Help); target audience 13 and over; content ratings ESRB Everyone, PEGI 3, USK 0, and ClassInd 14+ in Brazil only because of in-app purchases.
   Sign in details (app access) says "restricted" with promo codes for Pro, which is right for a paid product; Google's reviewers read its instructions. Mathis updated them on 22 October 2026 to the save button in the top bar, then "Save large".
6. Build a new bundle from `main` (raise the version, for example 1.2.0, code 4), put it on the internal track and buy Pro once with a licence tester.
7. Promote that release to production and send it for review.

Tablet screenshots are recommended, not required.
