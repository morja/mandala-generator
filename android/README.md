# Mandala Generator for Android

Android port of the macOS [Mandala Generator](../mac/README.md), in the same repository. Current state: the renderer in plain Kotlin (all 36 styles, every background type, the full effects layer, the text, emblem, drawing and graffiti layers), a command-line tool, and an Android app with up to five layers, an emblem in the centre, drawing with the finger in rotated symmetry, live preview, 21 starter favourites, opening and saving the Mac app's settings and favourites files, saving to the gallery in square, screen and other ratios, setting a wallpaper, sharing, and the last state kept across restarts. The app is on Google Play's internal track as `one.tearoom.mandala` (store listing: [STORE-1](../docs/store/STORE-1-play-listing.md)).

Results so far: [POC-1](../docs/POC-1-results.md). Decisions: [docs/adr](../docs/adr/README.md). Rules for agents: [AGENTS.md](../AGENTS.md) and [android/AGENTS.md](AGENTS.md).

## Requirements

Commands below run in `android/`. The reference renderer builds the macOS app's renderer from `../mac`.

JDK 21 (via jenv), the Android SDK (`local.properties` with `sdk.dir`, or `ANDROID_HOME`), and macOS for the reference renderer. Gradle comes with the wrapper.

## Run the app

Android 10 or later. With an emulator or a phone connected:

```sh
./gradlew :app:installRelease
```

The release build is signed with the upload key when `keystore.properties` exists (STORE-1), otherwise with the local debug key. Pro (saving at 2048 px, framing the mandala, moving layers and the emblem; ADR-0007) is sold through Google Play as product `pro`; debug builds count as Pro. Debug builds work too (`installDebug`) but render about 2.5 times slower. Exports land in `Pictures/Mandala`.

## Render a settings file from the Mac app

```sh
./gradlew :cli:installDist
cli/build/install/mandala/bin/mandala render mandala-wave-p15-x4-fx-22ae9a7c.json out.png --size 1024
```

Ported so far: all 36 layer styles, with glow, wash, painted effect, colour grade, rotation, opacity and blend modes, plus every background type (auto, colour, gradient, sunburst, grain, picture), the text layer (quotes with cloud, shadow and glow), the emblem layer, the drawing and graffiti layers and the effects layer (vignette, brightness, contrast, bloom, highlights, wash, sepia, fade, grain, clarity, relief, chromatic, dimming, erasure, stars, glitter). Anything else is skipped with a warning.

Compare two images:

```sh
cli/build/install/mandala/bin/mandala compare a.png b.png --diff diff.png
```

## Checks

- `scripts/check`: fast, runs in the pre-commit hook for changes in `android/` (at the repository root: `git config core.hooksPath .githooks`).
- `scripts/check-full`: adds the comparison against the macOS reference images in `fixtures/reference/`.

To regenerate the references after a change in the macOS app (macOS only):

```sh
tools/reference-renderer/render-references.sh
```

## Layout

| Folder | Content |
|---|---|
| `app` | Android app (Compose): editor screen, preview, gallery export |
| `core` | Renderer, settings model and JSON; plain Kotlin, shared by app and command |
| `cli` | JVM command `mandala` (render, compare) and the parity test |
| `fixtures` | Settings files and macOS reference images |
| `tools/reference-renderer` | Builds the macOS renderer as a command-line tool |

## License

GPL-3.0 with an additional permission for app stores and the Google Play Billing Library; see the [README at the repository root](../README.md#license) and ADR-0008.
