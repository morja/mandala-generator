# Mandala Generator for Android

Android port of the macOS [Mandala Generator](https://github.com/morja/mandala-generator). Current state: the renderer in plain Kotlin (all 36 styles, every background type, the full effects layer, the text layer and the drawing and graffiti layers), a command-line tool, and an Android app with up to five layers, drawing with the finger in rotated symmetry, live preview, favourites, opening and saving the Mac app's settings and favourites files, export to the gallery, sharing, and the last state kept across restarts.

Results so far: [POC-1](docs/POC-1-results.md). Decisions: [docs/adr](docs/adr/README.md). Rules for agents: [AGENTS.md](AGENTS.md).

## Requirements

The tools and examples expect a checkout of the macOS app next to this one, at `../Mandala-Generator` (its Swift package in `MandalaGenerator/`).

JDK 21 (via jenv), the Android SDK (`local.properties` with `sdk.dir`, or `ANDROID_HOME`), and macOS for the reference renderer. Gradle comes with the wrapper.

## Run the app

Android 10 or later. With an emulator or a phone connected:

```sh
./gradlew :app:installRelease
```

The release build is signed with the local debug key (no store signing yet). Pro (saving at 2048 px, ADR-0007) is sold through Google Play as product `pro`; debug builds count as Pro. Debug builds work too (`installDebug`) but render about 2.5 times slower. Exports land in `Pictures/Mandala`.

## Render a settings file from the Mac app

```sh
./gradlew :cli:installDist
cli/build/install/mandala/bin/mandala render ../Mandala-Generator/generated/mandala-wave-p15-x4-fx-22ae9a7c.json out.png --size 1024
```

Ported so far: all 36 layer styles, with glow, wash, painted effect, colour grade, rotation, opacity and blend modes, plus every background type (auto, colour, gradient, sunburst, grain, picture), the text layer (quotes with cloud, shadow and glow), the drawing and graffiti layers and the effects layer (vignette, brightness, contrast, bloom, highlights, wash, sepia, fade, grain, clarity, relief, chromatic, dimming, erasure, stars, glitter). Anything else is skipped with a warning.

Compare two images:

```sh
cli/build/install/mandala/bin/mandala compare a.png b.png --diff diff.png
```

## Checks

- `scripts/check`: fast, runs in the pre-commit hook (`git config core.hooksPath .githooks`).
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
| `docs` | ADRs, approvals, reports |

## License

Copyright (C) 2026 Mathis Koblin

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 3 (see `LICENSE`).

Additional permission under GNU GPL version 3 section 7: If you modify this Program, or any covered work, by linking or combining it with the Google Play Billing Library or with the system and platform libraries of Android, macOS or iOS (or modified versions of those libraries), containing parts covered by the terms of their own licences, the licensors of this Program grant you additional permission to convey the resulting work. You may also convey the Program through app stores (such as Google Play and the Apple App Store) whose terms of service add restrictions on use or copying.

Contributions are accepted under the same terms. See ADR-0008.
