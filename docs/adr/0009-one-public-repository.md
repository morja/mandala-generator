# ADR-0009 · One public repository for both apps

Status: proposed, 20 October 2026 (draft, waiting for Mathis' approval; plan in [PLAN-1](../PLAN-1-one-repository.md))

## Decision

- The macOS app and the Android app live in one public repository, **`morja/mandala-generator`**, under GPL-3.0 with the section 7 permission (ADR-0008).
- One folder per platform: **`mac/`** for the Swift package, **`android/`** for the Gradle project (`core`, `cli`, `app`, `fixtures`, `scripts`, `tools`). Shared at the root: `docs/` (ADRs with one numbering, approvals, reports), `LICENSE`, `README.md`, `AGENTS.md`, `CLAUDE.md`, `.github/`, `.githooks/`.
- Both histories are kept without a rewrite: each repository moves its files into its folder in one commit, then the Android history is merged with `--allow-unrelated-histories`. Commit hashes, tags and releases of the Mac app stay valid.
- The private repository `morja/mandala-generator-android` is archived after the move and not developed further.

## Reasons

- New features are built Mac first and then ported (ADR-0008); one repository lets one change, one review and one ADR cover both.
- The Mac repository already has the public URL, the releases and the star; moving the Android history there keeps them.
- No rewrite means no force push of a public `main` and no broken commit links.

## Consequences

- The parity tools find the Mac renderer at `../mac` from `android/` instead of a second checkout next to the repository (changes ADR-0004's `../Mandala-Generator`).
- The Android stack rules move to `android/AGENTS.md`; the root `AGENTS.md` holds the rules for the whole repository.
- ADR-0001 to ADR-0008 keep their text; their paths are relative to `android/`.
- The release workflow runs in `mac/`. Android releases go through Google Play, not GitHub releases.
- `git log` on a folder starts at the move commit; `--follow` and `blame` see across it.
