# Repository rules (binding, set up 20 October 2026)

Mandala Generator for macOS and Android in one repository (ADR-0009): `mac/` is the Swift package of the macOS app, `android/` the Gradle project of the Android port (`core`, `cli`, `app`). Decisions for both are in `docs/adr/`, open approvals in `docs/APPROVALS.md`. Read the affected ADRs before building; do not reinvent decisions already made. Rules for the Android code are in `android/AGENTS.md`.

- **Mac first, then port:** new features are built in the macOS app first, then ported to Android (ADR-0008). Mac favourites may change with new motifs; the Android parity references are re-rendered from `mac/` (`android/tools/reference-renderer/render-references.sh`).
- **Licence:** GPL-3.0 with the section 7 permission (`LICENSE`, `README.md`, ADR-0008). Ship only content that may be shipped in a public, sold app (public-domain quotes, ADR-0007).
- **Language:** code, identifiers, UI and docs in English. Conversation with Mathis in German.
- **Approval needed** for new packages, new design tokens, new root folders (in the repository and in `android/`) and any deviation from an ADR. **Stop rule:** do not build a workaround; add the question to `docs/APPROVALS.md` and continue elsewhere.
- **Checks:** the git hook `.githooks/pre-commit` (`git config core.hooksPath .githooks`) runs `android/scripts/check` for changes in `android/` and `swift build` in `mac/` for changes there. `android/scripts/check-full` (adds the parity test and a release build) before merging Android changes into `main`.
- **Releases:** the macOS app through a `mac-v*` tag (`.github/workflows/mac-release.yml`), the Android app through Google Play. No release, tag or store upload unless Mathis asks.
- **Agents** build in worktrees, never merge themselves and never publish.
- **Docs:** decisions as ADRs, open approvals in `docs/APPROVALS.md`, reports with an ID (`POC-1`, `PLAN-1`, …) in `docs/`.
- **Marketing:** share images and other promotion material live in `docs/marketing/` (README there). New texts are a text set in `docs/marketing/texts/<name>.js`, rendered with `docs/marketing/render-share-images.sh <name>` into `docs/marketing/share/<name>/`; do not rebuild the template for a new text.
- **Commit often**, after every larger change.
