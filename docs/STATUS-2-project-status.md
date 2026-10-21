# STATUS-2 · Project status and road to the Play Store

21 October 2026. Replaces STATUS-1.

## Where things are

**Both apps** live in this public GPL-3.0 repository (ADR-0009).

**Android app, version 1.0.1 (code 2):**

- **Renderer:** all 36 styles, backgrounds, effects, text, drawing, graffiti and emblem layers. It is parity-tested against the macOS renderer (ADR-0004).
- **Editor:** phone and tablet layouts, with a compact phone layout (DM-4 A).
- **Positions:** layer and emblem positions with a move mode (ADR-0011, Pro).
- **Saving:** a save sheet with Square, This screen and 4:5, framing (Pro), 2048 px (Pro) and Set as wallpaper (ADR-0010).
- **Other:** favourites compatible with the Mac app, history, About with GPL and libraries, dark theme by default.
- **Preview speed (QA-2):** quicker blurs, layer cache, Fast preview and a rough first image.
- **Checks:** `scripts/check-full` passes.

**Store preparation (STORE-1):**

- Listing texts in English and German, icon, feature graphic, six phone screenshots.
- Answers for the App content section.
- The app exists in the Play Console.
- Release builds are signed with the upload key and shrunk with R8.
- The website and privacy policy are live: https://www.tearoom.one/en/apps/mandala-generator and https://www.tearoom.one/en/apps/privacy/mandala-generator

**Closed test:** none needed. The developer account predates November 2023.

## Left before the first release

Steps in the Play Console:

1. Build the bundle and upload it to a testing or production track. If version code 2 was uploaded before, raise `versionCode` first.
2. Play App Signing comes with that first upload.
3. Create the product `pro` (STORE-1) and set its price. ADR-0007 suggests 1 to 3 euros.
4. Fill in Data safety, Content rating and Target audience with the answers in STORE-1.
5. Buy Pro once from an internal test track (a licence tester gets it free) and check that Save large unlocks.

On the phone:

6. Try the release build on the S23, mainly the preview speed (QA-2, P1).

Housekeeping (no release blocker):

7. Old open approvals in `docs/APPROVALS.md`: ADR-0003 to ADR-0006, the proof-of-concept packages, the Core Image grain tile, bundled fonts.
8. Delete the old private repository `morja/mandala-generator-android` if it still exists.

Build command (from the repository root):

```bash
android/gradlew -p android :app:bundleRelease
```
