# ADR-0012 · App name: Dahlia – Mandala Maker

Status: accepted, 22 October 2026 (Mathis' decision on the name and the icon; the macOS app follows as recommended).

## Decision

1. **Store title "Dahlia – Mandala Maker"** (22 characters) in Google Play, English and German listing alike.
2. **"Dahlia" under the icon.** The launcher label (`app_name`, today "Mandala") becomes "Dahlia".
3. **Pro is "Dahlia Pro".** The in-app product `pro` (ADR-0007) keeps its id; only its title changes.
4. **The icon stays.** The current icon, a mandala rendered by the app, is kept; no dahlia motif.
5. **The macOS app follows** with the same name at its next release, so favourites move between two apps of one name (ADR-0003). Until then it stays "Mandala Generator".
6. **Ids stay.** The Android application id (`one.tearoom.mandala`), the Play product id and the repository name do not change.

## Reasons

- "Mandala" in the title keeps the app findable; it is the search word that counts. "Dahlia" gives it a name of its own among dozens of "Mandala …" apps, and a short label under the icon.
- A dahlia is the flower that looks most like a mandala: strictly radial, petals in spirals. It also fits next to Gazania, another app by tearoom.one.
- "Maker" says that people design the mandala themselves. "Generator" reads as an AI image generator in 2026; "Art" is the word of colouring-book apps.
- The current icon looks modern next to the many traditional mandala apps in the store; the dahlia candidates looked conventional (Mathis, 22 October 2026, after [DM-10](../mocks/DM-10-dahlia-icons.html)). The name carries the flower; the icon does not have to repeat it.

## Checked on 22 October 2026

- Google Play and App Store: no art or drawing app named Dahlia. Apps with the name are a GLP-1 tracker, jewellery, dahlia growers' apps and a device companion.
- Trademarks in TMview (EU, Germany, WIPO; classes 9 and 42): no live mark for apps or graphics software. Live marks: DAHLIA by Gewiss (electrical switches), DAHLIA HEALTH (medical devices), DahliaMatic (IT consulting). Others have expired. This is a search, not legal advice; registering "Dahlia" in class 9 later would settle it.

## Rejected

- **Mandala Generator** (today): findable but generic, and "Generator" suggests AI.
- **Mandala Studio**: taken three times, among them an App Store app with nearly the same features (petals, symmetry, layers, SVG export) and "Mandala Studio: Draw & Color" in Google Play.
- **Mandala Designer**: the label under the icon is cut off ("Mandala Desi…"), see [DM-9](../mocks/DM-9-app-names.html).
- **Dahlia – Mandala Art**: "Mandala Art" brings colouring-book expectations.
- **Zinnia**: a registered US mark of Pixite for a journaling app and an adult colouring-book app.
- **Cosmea**: in English the flower is "cosmos"; the name means little there.
- **Bindu**: common among yoga and meditation apps; a flower name fits the family better.
- **Dahlia icons** (six candidates in DM-10): kept as rendered settings in `docs/mocks/dm-10-icons/`, not to be proposed again.
