# Mandala Generator for Android

Android port of the macOS [Mandala Generator](https://github.com/morja/mandala-generator). Current state: the renderer in plain Kotlin (five of the 36 styles), a command-line tool, and a first Android app with up to five layers, live preview, export to the gallery, and the last state kept across restarts.

Results so far: [POC-1](docs/POC-1-results.md). Decisions: [docs/adr](docs/adr/README.md). Rules for agents: [AGENTS.md](AGENTS.md).

## Requirements

The tools and examples expect a checkout of the macOS app next to this one, at `../Mandala-Generator` (its Swift package in `MandalaGenerator/`).

JDK 21 (via jenv), the Android SDK (`local.properties` with `sdk.dir`, or `ANDROID_HOME`), and macOS for the reference renderer. Gradle comes with the wrapper.

## Run the app

Android 10 or later. With an emulator or a phone connected:

```sh
./gradlew :app:installRelease
```

The release build is signed with the local debug key (no store signing yet). Debug builds work too (`installDebug`) but render about 2.5 times slower. Exports land in `Pictures/Mandala`.

## Render a settings file from the Mac app

```sh
./gradlew :cli:installDist
cli/build/install/mandala/bin/mandala render ../Mandala-Generator/generated/mandala-wave-p15-x4-fx-22ae9a7c.json out.png --size 1024
```

Ported so far: Spirograph, Rose Curves, Hypocycloid, Phyllotaxis and Wave Interference layers, with glow, wash, painted effect, colour grade, rotation, opacity and blend modes, plus the solid colour background. Anything else is skipped with a warning.

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
