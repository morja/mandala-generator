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
| Website | https://www.tearoom.one/en/apps/dahlia |
| Privacy policy | https://www.tearoom.one/en/apps/privacy/dahlia |
| Package name | `one.tearoom.mandala` (`applicationId` in `android/app/build.gradle.kts`; the Play Console app exists with it since 20 October 2026 and it cannot change. The Kotlin namespace stays `com.mathis.mandala.app`.) |

The two tearoom.one pages exist in the website repository (commit "Add Mandala Generator to the apps, with its privacy policy") but are not deployed yet.

On 23 October 2026 the pages moved to `/apps/dahlia` and `/apps/privacy/dahlia` (both languages), renamed after ADR-0012, with 301 redirects from the `mandala-generator` addresses in the site's `routes.php`. The Play Console was pointed at the new addresses the same day: the website through the API (`edits.details`), the privacy policy URL by hand under App content.

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
> Save your mandalas to the gallery as a square, in the shape of your screen or in portrait and landscape ratios such as 4:5, 2:3 and 16:9, showing the whole mandala or filling the picture, set one as your wallpaper, or share it. Start with 23 example favourites, keep your own, and move them between your phone and the Mac version.
>
> The app is free: every style and palette, all effects, three layers, as many favourites as you like, and saving and sharing at screen size without a watermark. Pro is an optional one-time purchase, no subscription: up to five layers, emblems, text and quotes, and moving layers and the emblem. It saves at 2048 px for prints and wallpapers, with the mandala placed by hand and brightness, contrast and saturation fine-tuned, and exports an SVG line drawing. Pro also brings wallpapers that change: a live wallpaper that turns slowly, a new wallpaper from your favourites every morning, and home and lock screen as a matching pair. Try more layers, emblems and quotes in the editor before you buy.
>
> No account, no ads, no tracking. Your mandalas stay on your device.
>
> Guide and answers to common questions: https://www.tearoom.one/en/apps/dahlia/help
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
> Speichere deine Mandalas quadratisch, im Format deines Bildschirms oder in Hoch- und Querformaten wie 4:5, 2:3 und 16:9 in der Galerie, ganz sichtbar oder bildfüllend, setze eines als Hintergrundbild oder teile es. Starte mit 23 Beispiel-Favoriten, behalte eigene und tausche sie zwischen Handy und Mac-Version aus.
>
> Die App ist kostenlos: alle Stile und Paletten, alle Effekte, drei Ebenen, beliebig viele Favoriten und Speichern und Teilen in Bildschirmgröße ohne Wasserzeichen. Pro ist ein optionaler einmaliger Kauf ohne Abo: bis zu fünf Ebenen, Embleme, Text und Zitate sowie Ebenen und Emblem verschieben. Damit speicherst du mit 2048 px für Drucke und Hintergrundbilder, richtest das Mandala im Bildausschnitt aus, stimmst Helligkeit, Kontrast und Sättigung fein ab und exportierst eine SVG-Strichzeichnung. Dazu kommen Hintergründe, die sich verändern: ein Live-Hintergrund, der sich langsam dreht, jeden Morgen ein neues Hintergrundbild aus deinen Favoriten und Start- und Sperrbildschirm als passendes Paar. Mehr Ebenen, Embleme und Zitate kannst du im Editor vor dem Kauf ausprobieren.
>
> Kein Konto, keine Werbung, kein Tracking. Deine Mandalas bleiben auf deinem Gerät.
>
> Anleitung und Antworten auf häufige Fragen: https://www.tearoom.one/de/apps/dahlia/help
>
> Dahlia ist Open Source unter der GNU GPL 3.0: https://github.com/morja/mandala-generator

## In-app product

| Field | Value |
|---|---|
| Product ID | `pro`, purchase option `buy` (must match `Pro.kt`, ADR-0022) |
| Type | One-time product, non-consumable |
| Name (EN / DE) | Dahlia Pro |
| Description (EN) | Up to five layers, emblems, text, moving layers, 2048 px, framing and finish, SVG, wallpaper pair, daily and live wallpaper. One-time purchase. |
| Beschreibung (DE) | Bis zu fünf Ebenen, Embleme, Text, Ebenen verschieben, 2048 px, Ausschnitt und Feinschliff, SVG, Hintergrund-Paar, täglich und live. Einmaliger Kauf. |
| Price | 2.99 € in Germany from version 1.3 (before 1.99 €): base price 2.51 € for all 174 countries, set in the Play Console on 22 October 2026 (US 2.79 $) |

In the Play Console since 22 October 2026: active. The descriptions above (ADR-0016 as amended for 1.4.1) were written there through the API on 22 October 2026 (`oneTimeProducts:batchUpdate`; the single PATCH answers with an HTML 404), together with both full listing descriptions.

On 23 October 2026 the closing sentence of the full description and of the 1.4.1 release notes changed from "Try every Pro feature before you buy" to "Try more layers, emblems and quotes in the editor before you buy" (German accordingly): only the four editor features of `ProFeatures` can be tried; 2048 px, framing, finish, SVG and the wallpapers are simply behind Pro. The in-app Pro sheet (`EditorScreen.kt`) had the old claim ("Try everything before you buy") until 1.4.1 and says the same as the listing from 1.5.0 on.

Google Play lets you create in-app products only after a build that contains the billing permission has been uploaded (any testing track).

## Listing languages and search terms

Since 23 October 2026 the listing exists in seven languages: English (default), German, Spanish (es-ES), Brazilian Portuguese, French, Simplified Chinese and Japanese, each with its own title, short and full description and captioned screenshots; release notes and the Pro product stay English and German (Play falls back to the default language). Texts in `docs/store/play/listings.py`, written through the API. The short descriptions carry the search terms people use: English "Mandala maker and live wallpaper: curves, layers, sacred geometry.", German "Mandala-Maker und Live-Hintergrund: Kurven, Ebenen, heilige Geometrie."; the first line of the full description names "mandala maker", wallpaper and live wallpaper. The listing texts quoted above are the versions before that change, apart from the try-before-you-buy sentence.

The Reddit and launch plan is [MKT-1](../MKT-1-reddit-and-launch-plan.md).

## Release notes for 1.8.0 (code 15)

**On the internal track since 27 October 2026** (`bin/release internal`, `completed`, notes in English and German; a fresh API read verified code 15 on internal). Production: pending Mathis' OK. Source tag: `android-v1.8.0` at `dc990cd`. Validation: `bin/check-full` on `main` (parity test and release build), signed `bundleRelease` (upload key CN=Mathis Koblin), signature verified. Bundle SHA-256: `6a470d0e13e6abf51dbbb517dd61460847c6708aca1d4aa50faa70b4af64fb8f`. Changes since 1.7.1: the play field (compass in the top bar; landscape and compass, DM-19 to DM-21), pictures beside the emblem motifs with a switch under More, the effects die rolls a new look and the arrangement sits per card, the quote is edited right in the text tab (DM-13 B), shorter style names, two preview sizes with a free pull, Pro through the shared tearoom-android-kit (ADR-0022). Notes: `store/android/en-US/changelogs/15.txt` and `de-DE/changelogs/15.txt`.

## Release notes for 1.7.1 (code 14)

**Submitted to production on 26 October 2026** through the API (`docs/store/play/play_release.py`), full rollout (`completed`), replacing 1.7.0 on the track; a fresh API read verified code 14 on production. Public store availability remains subject to Google's review. Source tag: `android-v1.7.1` at `e59634b`. Validation: `android/scripts/check-full` on `main`, signed `bundleRelease` (upload key CN=Mathis Koblin), package and version checked in the bundle manifest. Bundle SHA-256: `3b95015fd10431510f6e049a92eac2fc290b53432017e61dac4ffe24eafb944c`. Prepared on Mathis' release request the same evening. Changes since 1.7.0: the notes on Pro say that trying and keeping as a favourite are free (ADR-0016), and the Pro marks fit a 360 dp phone. Since 1.7.0 may still be in review, the notes repeat its changes. Notes: `docs/store/play/release-notes-1.7.1-en.txt` and `-de.txt`.

## Release notes for 1.7.0 (code 13)

**Submitted to production on 26 October 2026** through the API (`docs/store/play/play_release.py`), with a full rollout (`completed`). A fresh API read verified code 13 on the production track with English and German notes. Public store availability remains subject to Google's review. Public source tag: `android-v1.7.0` at `725db7d`. Validation: `android/scripts/check-full` on `main`, signed `bundleRelease` (upload key CN=Mathis Koblin), package and version checked in the bundle manifest. Bundle SHA-256: `bd354593ac4bd22be776ae5259e2628cb2d4a199a8d32eb09044f20d24f0e198`.

The same evening every store listing (29 languages) got a paragraph with the guide link before the open-source line (`docs/store/play/play_guide_line.py`); German links to the German guide, the others to the English one, saying so. A fresh read verified the link in all 29. Changes since 1.6.0: UI in ten languages (ADR-0017, ADR-0019), home screen widget (ADR-0020), own palettes (ADR-0021), moving picture as MP4 and live wallpaper layers turning on their own (Pro, ADR-0014), calmer save sheet, Scene and Text tabs, and a Guide entry in the menu that opens the website guide.

English:

> Dahlia now speaks ten languages. New: a home screen widget with a mandala of the day, and your own palettes, also taken from a photo. With Pro, save a moving mandala as a video and let the live wallpaper turn its layers on their own. Saving, Scene and Text are calmer, and the menu links to the new guide.

German:

> Dahlia spricht jetzt zehn Sprachen. Neu: ein Widget für den Startbildschirm mit dem Mandala des Tages und eigene Paletten, auch aus einem Foto. Mit Pro speicherst du ein bewegtes Mandala als Video, und im Live-Hintergrund drehen sich die Ebenen auf Wunsch einzeln. Speichern, Szene und Text sind übersichtlicher, und das Menü führt zur neuen Anleitung.

## Release notes for 1.6.0 (code 12)

**Submitted to production on 25 October 2026** through the API (`docs/store/play/play_release.py`), with a full rollout (`completed`). Google Play accepted and committed the release; a fresh API read verified code 12, both release notes and the uploaded bundle hash. Public store availability remains subject to Google's review and propagation. Public source tag: `android-v1.6.0` at `cbc4321f28ab37854435d637eb568a7413dd8a05`.

Changes: consistent editor sections and spacing, inline style previews and clearer selection, labelled Seed/Variation actions, consistent dice colours, Smoother, and text above Scene effects (ADR-0017). Validation: `android/scripts/check-full` (including parity and release build), signed `bundleRelease`, package/version/debuggability checks and upload-certificate verification. Bundle SHA-256: `9594c41724a5d361a9f1794032e247e787c531664aa0112471ed9e52bffd91e4`.

English:

> A clearer editor with consistent sections, more space around controls and a small preview for every style. The selected style and dice are easier to spot. New Smoother softens the artwork while text stays clear above scene effects. Seed and Variation buttons now explain what they change.

German:

> Der Editor ist übersichtlicher: einheitliche Abschnitte, mehr Platz an den Reglern und eine kleine Vorschau für jeden Stil. Auswahl und Würfel sind deutlicher markiert. Neu: Smoother zeichnet das Motiv weich, während Text über den Szenen-Effekten klar bleibt. Seed und Variation zeigen verständlicher, was sie verändern.

## Release notes for 1.5.1 (code 11)

**Released to production on 24 October 2026** through the API (`docs/store/play/play_release.py`). Git tag `android-v1.5.1`. Fix: the live wallpaper's check for a new mandala every three seconds restarted a first render that took longer than that, so the wallpaper could stay black (commit `b5d9f77`). The app now also renders the image first, with its progress showing, and opens Android's preview only then, which shows the mandala at once (`193b8a4`). Tested on the phone with uncached mandalas.

English:

> The live wallpaper no longer stays black the first time it is set: the app prepares it first and shows its progress, then the preview opens with the mandala.

German:

> Der Live-Hintergrund bleibt beim ersten Festlegen nicht mehr schwarz: Die App bereitet ihn erst vor und zeigt den Fortschritt, dann öffnet sich die Vorschau mit dem Mandala.

## Release notes for 1.5.0 (code 10)

**Released to production on 24 October 2026** through the API (`docs/store/play/play_release.py`: bundle upload, track with the notes below, status completed); Google's review follows. Git tag `android-v1.5.0`. Favourites keep their Pro features (ADR-0016 amended the same day), the dice move the effect patterns, the effects die and a reset sit in the Effects heading, the effect subgroups are inset, and four controls of the macOS app came over: the seed die per layer, the Spokes slider for Radial Mesh, a die and reset for the text, and tracking, cloud colour and shadow colour in the Text tab. The Pro sheet now ends with the listing's sentence ("Try more layers, emblems and quotes in the editor before you buy").

English:

> Favourites now keep everything you built, including Pro features, and open for everyone. The dice also move the highlights, dimming and glitter to new places. New in the editor: a die that rearranges one layer, a Spokes slider for Radial Mesh, a die and reset for the text, letter spacing, and colours for the text's shadow and cloud. Effects have a reset and clearer groups.

German:

> Favoriten behalten jetzt alles, was du gebaut hast, auch Pro-Funktionen, und öffnen sich für alle. Der Würfel verteilt auch Lichter, Abdunklung und Glitzer neu. Neu im Editor: ein Würfel, der eine Ebene neu anordnet, ein Speichen-Regler für Radial Mesh, Würfel und Zurücksetzen für den Text, Zeichenabstand sowie Farben für Schatten und Wolke des Texts. Effekte haben ein Zurücksetzen und klarere Gruppen.

## Release notes for 1.4.1 (code 9)

The free line moved (ADR-0016, amended): three layers free instead of two, no limit on favourites. Same notes as 1.4.0, since 1.4.0 never went public.

On the internal track and **released to production** on 22 October 2026 (status completed, through the API; Mathis chose the countries in the Play Console beforehand). It replaced the unsent 1.3.1 draft; Google's review follows. The first public version.

**Live since 23 October 2026:** the production track reports "1.4.1 (9)", status completed (checked through the API on 23 October), and the public store page at https://play.google.com/store/apps/details?id=one.tearoom.mandala shows 1.4.1 without a login. Git tag `android-v1.4.1`. Accounts enrolled in the internal test (Mathis among them) see the same build labelled "internal beta" in the Play Store app; that label is only shown to testers.

## Release notes for 1.4.0 (code 8)

The first public release (ADR-0016); 1.3.1 was never sent for review. On the internal track since 22 October 2026 (uploaded through the API with these notes); git tag `android-v1.4.0`. Production still has the unsent 1.3.1 draft.

English:

> Dahlia makes mandalas: choose styles and palettes, add a background and effects, draw by hand, and save or set them as your wallpaper. Pro adds more layers, emblems, quotes, large images and wallpapers that change every day or move slowly. Try more layers, emblems and quotes in the editor before you buy.

German:

> Dahlia macht Mandalas: Wähle Stile und Paletten, füge Hintergrund und Effekte hinzu, zeichne von Hand und speichere sie oder setze sie als Hintergrundbild. Pro bringt mehr Ebenen, Embleme, Zitate, große Bilder und Hintergründe, die jeden Tag wechseln oder sich langsam bewegen. Mehr Ebenen, Embleme und Zitate kannst du im Editor vor dem Kauf ausprobieren.

## Release notes for 1.3.1 (code 7)

The first production release (1.3.0 never left the draft). The 1.3.0 notes plus the calmer save sheet (DM-11 A).

English (296 characters, max. 500):

> Version 1.3: Pro now dims or brightens your wallpaper, sets home and lock screen as a pair, changes the wallpaper every morning and adds a live wallpaper that turns slowly. Saved portraits now fill the screen or show the whole mandala, without hard edges. Saving is calmer and fits on one screen.

German (360 characters):

> Version 1.3: Pro dunkelt dein Hintergrundbild ab oder hellt es auf, setzt Start- und Sperrbildschirm als Paar, wechselt das Hintergrundbild jeden Morgen und bringt einen Live-Hintergrund, der sich langsam dreht. Hochformate füllen jetzt den Bildschirm oder zeigen das ganze Mandala, ohne harte Kanten. Speichern ist aufgeräumter und passt auf einen Bildschirm.

## Promo video

The live wallpaper (Pro), 30 seconds, shown sped up and labelled so: https://www.youtube.com/watch?v=I7TlzGe-Bcg (unlisted on Mathis' channel, not made for kids, no age restriction), entered in the default listing's video field on 22 October 2026; the German listing uses it too. The videos (1920×1080 for YouTube, a portrait one) are kept outside the repository in `Mandala-Generator/media/`; the README has a small GIF (`docs/images/live-wallpaper.gif`). Recorded in Android's live wallpaper preview with a demo build turning about six times faster (local branch `demo/live-video`, not for `main`).

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
  IARC Live Rating Notice of 23 October 2026 for "Dahlia – Mandala Maker" (storefront Google Play): Global Rating ID `df6c1666-36d7-8f9f-8b00-3fb0c14bde99`. Enter this ID on other storefronts that license IARC instead of filling in the questionnaire again. If a later change would alter the answers (for example user-generated content or ads), the questionnaire must be filled in again.
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

On 23 October 2026 the seven screenshots were replaced in every listing language by captioned versions (1080 × 1920: a caption and a muted second line on the app's dark background, the screenshot below with rounded corners), rendered with `docs/store/play/Caption.java` from `captions.json` and uploaded with `play_shots.py`. The originals below stay the source.

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

**Open (Mathis, 26 October 2026), not urgent:** the screenshots no longer match the app in several places (scene tab, text tab with the Words card, palette group with own palettes, preview sizes, menu), and some listing texts are no longer exact. Take new screenshots, render the captions again and check the listing texts against the current app before the next store update.

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
   State on 22 October 2026: a production release **draft** "6 (1.3.0)" with the release notes above (English and German) is saved in the Play Console. Open before sending it for review: choose the production countries (Production → Countries / regions; none yet, which the release reports as an error); the warning about missing native debug symbols comes from libraries and does not block. The listing texts and the Pro product description above (English and German) are in the Play Console, entered the same day.
   *Later on 22 October 2026:* version 1.3.1 (code 7) replaced it: internal track rolled out, production **draft** "7 (1.3.1)" with the 1.3.1 notes above, uploaded through the API; git tag `android-v1.3.1` and a GitHub release without binaries. Still open for Mathis: production countries, then "Send for review".
   *Done on 22 October 2026:* 1.4.1 (code 9) replaced the 1.3.1 draft, countries chosen, released to production through the API. **Live since 23 October 2026** (see the 1.4.1 notes above). GitHub release for `android-v1.4.1` without binaries created on 23 October 2026.

Tablet screenshots are recommended, not required.
