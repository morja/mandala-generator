# Mandala Generator for Android

Android port of the macOS [Mandala Generator](../Mandala-Generator). Current state: proof of concept of the renderer in plain Kotlin, with a command-line tool. There is no Android app module yet.

Results so far: [POC-1](docs/POC-1-results.md). Decisions: [docs/adr](docs/adr/README.md). Rules for agents: [AGENTS.md](AGENTS.md).

## Requirements

JDK 21 (via jenv) and macOS for the reference renderer. Gradle comes with the wrapper.

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
| `core` | Renderer, settings model and JSON; plain Kotlin, shared with the future Android app |
| `cli` | JVM command `mandala` (render, compare) and the parity test |
| `fixtures` | Settings files and macOS reference images |
| `tools/reference-renderer` | Builds the macOS renderer as a command-line tool |
| `docs` | ADRs, approvals, reports |
