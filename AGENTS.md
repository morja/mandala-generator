# Stack rules (binding, set up 19 October 2026)

Mandala Generator for Android: a port of the macOS app in `../Mandala-Generator`. Reasons and details are in `docs/adr/`. Read the affected ADRs before building; do not reinvent decisions already made.

- **Kotlin, native Android with Jetpack Compose** for the app (ADR-0001). The app module does not exist yet; the proof of concept has `core` and `cli` only.
- **Renderer in `core`, plain Kotlin:** no `android.*`, `java.awt`, `javax.*` or coroutines there; only Kotlin stdlib, `java.util.concurrent` and kotlinx.serialization. The JVM command and the Android app call `core`; rendering logic lives nowhere else (ADR-0002). Enforced by `ArchitectureTest`.
- **Memory budget:** a 1024 px export of two layers must render within 256 MB of heap (`MemoryBudgetTest`). Intermediates are 8-bit `Image8`; no full-size float copies or per-thread buffers (ADR-0002).
- **Settings JSON stays compatible with the macOS app:** same field names, same defaults, unknown values fall back like the Swift decoders, unknown fields are ignored, unported layers are kept as raw JSON (ADR-0003).
- **Port faithfully, then measure:** every ported style or filter gets a fixture in `fixtures/settings/` and a macOS reference image from `tools/reference-renderer/`. Deviations get a PSNR threshold in `ParityTest` and a line in ADR-0004, never a silent change of look.
- **Language:** code, identifiers, UI and docs in English. Conversation with Mathis in German.
- **No horizontal scrolling** in any app screen (applies once the app exists).
- **Approval needed** for new packages, new design tokens, new root folders and any deviation from an ADR. **Stop rule:** do not build a workaround; add the question to `docs/APPROVALS.md` and continue elsewhere.
- **Checks:** `scripts/check` (fast, in the git hook `.githooks/pre-commit`); `scripts/check-full` (adds the parity test) before merging into `main`. Rule tests: `core/src/test/.../ArchitectureTest.kt`.
- **Agents** build in worktrees, never merge themselves and never publish. No release or store upload unless Mathis asks.
- **Docs:** decisions as ADRs, open approvals in `docs/APPROVALS.md`, reports with an ID (`POC-1`, …) in `docs/`.
- **Commit often**, after every larger change.
