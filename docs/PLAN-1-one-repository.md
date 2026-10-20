# PLAN-1 · Both apps in one public repository

20 October 2026. Status: approved by Mathis on 20 October 2026, all recommendations taken (see "Decisions" at the end). Decision: [ADR-0009](adr/0009-one-public-repository.md).

## Secret check of the Android history (done)

All 29 commits on every local branch and `origin/main` (448 blobs, 250 paths ever added) were searched on 20 October 2026.

| Looked for | Result |
|---|---|
| Key and certificate files (`.jks`, `.keystore`, `.p12`, `.pem`, `.key`, `.p8`), `local.properties`, `.env`, `google-services.json`, service-account or Play Console JSON | none ever committed (`local.properties` is in `.gitignore`) |
| Signing settings in Gradle files (`storePassword`, `keyPassword`, `keyAlias`, `storeFile`) | none; release builds use `signingConfigs.getByName("debug")` |
| API keys and tokens (Google `AIza…`, GitHub `ghp_…`, AWS `AKIA…`, Slack, `sk-…`), private key blocks, base64 licence keys | none |
| E-mail addresses and local paths (`/Users/…`) in files | none; author on all commits is the GitHub no-reply address |
| Binary files | PNG/WebP/JPG images, `gradle-wrapper.jar` only |

**Nothing to remove; no history rewrite needed for secrets.**

One thing becomes public with the history that is not in today's tree: commits `63dfaea` to `20410fa` contain the macOS app's quote collection (modern translations, living authors) in `Quotes.kt`. The same text is already public in the macOS repository (`QuoteDatabase.swift`), so nothing new is exposed. Open question 5.

## Home repository

**`morja/mandala-generator` (public)** becomes the home. It keeps its URL, its star, the releases v1.4.0 and v1.5.0 with their downloads, and the "latest release" link in its README. The Android history is merged into it.

Rejected: renaming `morja/mandala-generator-android` and making it public. The Mac URL, releases and star would stay behind in a second repository.

## Folder layout

```
mac/                     Swift package, moved as is: Package.swift, Sources/, build_app.sh, Info.plist,
                         AppIcon*, generate_icon.py, samples/, sample-image.jpg, README.md (Mac details), .gitignore
android/                 Gradle project, moved as is: settings.gradle.kts, gradlew, gradle/, app/, core/, cli/,
                         fixtures/, scripts/, tools/, README.md (Android details), AGENTS.md, CLAUDE.md, .gitignore
docs/                    shared: adr/ (one numbering for the whole repository), APPROVALS.md, reports, images/
.github/workflows/       mac-release.yml (today's build.yml)
.githooks/pre-commit     runs the checks of the platform whose files are staged
LICENSE                  GPL-3.0, once (both copies are byte-identical today)
README.md                short overview: what the app is, downloads, links to mac/ and android/, licence with the
                         section 7 permission
AGENTS.md, CLAUDE.md     repository-wide rules; CLAUDE.md is "@AGENTS.md"
```

`fixtures/` and `tools/reference-renderer/` stay under `android/`: they are the Android parity tests' data and tools, the Mac renderer is only their source.

## How both histories are kept

No rewrite of either history. Both keep their commit hashes, so the Mac tags, releases and every public commit link (for example `morja/mandala-generator 4492043` in POC-1) stay valid, and nothing needs a force push.

1. Fresh clones in a new folder (default `/Users/mathis/Work/Projects/mandala-generator`), so the existing checkouts, worktrees and the other session are not touched.
2. Mac clone: one commit "Move the macOS app into mac/" (`git mv` of all tracked files except `LICENSE` and `.github/`).
3. Android clone: one commit "Move the Android app into android/" (`git mv` into `android/`, `docs/` stays at the root, `LICENSE` stays at the root).
4. In the Mac clone: `git fetch <android clone> main` and `git merge --allow-unrelated-histories FETCH_HEAD`. No conflicts expected: the only path both sides have at the root is `LICENSE`, with identical content.
5. One commit for the shared root and the path fixes (next section).

`git log --follow` and `git blame` follow files across the move. Plain `git log android/core` starts at the move commit; that is the price of keeping the hashes. Alternative: `git filter-repo --to-subdirectory-filter` gives clean path histories but changes every Mac hash, moves the release tags and needs a force push of the public `main` (open question 2).

## Changes to scripts and settings

| File | Change |
|---|---|
| `android/tools/reference-renderer/render-references.sh` | default source `../mac/Sources/MandalaGenerator` (run from `android/`, as today from the repository root) |
| `android/scripts/check`, `check-full` | start with `cd "$(dirname "$0")/.."`, so they work from the hook and from any folder |
| `.githooks/pre-commit` | `android/scripts/check` when files under `android/` are staged; `swift build --package-path mac` when files under `mac/` are staged; nothing for docs-only commits |
| `.github/workflows/mac-release.yml` | `defaults.run.working-directory: mac`, release file `mac/Mandala-Generator.zip`, tag pattern per open question 3, plus `workflow_dispatch` that builds without releasing (to test the move) |
| `android/settings.gradle.kts`, `build.gradle.kts` | none: Gradle's root becomes `android/`, all paths are relative to it |
| `ArchitectureTest` | none needed (finds `android/` as root). Optional: a second test for the repository's root folders |
| `.gitignore` | each moves with its platform (patterns stay relative to their folder); a short root `.gitignore` for `.DS_Store`, `.idea/`, `.claude/` |
| `android/README.md`, `AGENTS.md`, `docs/POC-1-results.md` | `../Mandala-Generator` replaced by `../mac`; links to `docs/` become `../docs/` |
| `mac/README.md` | build commands run in `mac/`; the licence section points to the root `LICENSE` |
| `docs/adr/README.md` | note that ADR-0001 to ADR-0008 were written before the move and their paths are relative to `android/` |

Not in this move: an Android CI workflow, and moving the Mac app's local `.claude/` notes (ignored by git, they stay local).

## Rules, docs and licence

- **Root `AGENTS.md`:** what is in the repository, Mac first then port (ADR-0008/0009), worktrees, approvals and the stop rule, commit often, checks per platform, docs and report IDs.
- **`android/AGENTS.md`:** today's stack rules (Compose, plain Kotlin `core`, memory budget, JSON compatibility, parity, tokens), paths unchanged. Claude Code reads it when working in `android/`.
- **ADRs and approvals** move to the root `docs/` with their numbers. ADR-0009 records this move.
- **`LICENSE`** once at the root (GitHub shows the licence only from there). The section 7 permission stays in the root README; the platform READMEs link to it.

## Local setup afterwards

- New checkout at `/Users/mathis/Work/Projects/mandala-generator`, with `git config core.hooksPath .githooks`.
- Claude Code memory lives per folder: copy `~/.claude/projects/-Users-mathis-Work-Projects-mandala-app/memory/` to the new folder's project entry.
- The old checkouts stay until Mathis deletes them; the Android Studio project is opened from `android/`.

## The old Android repository

After the push and a working check: **archive `morja/mandala-generator-android`, kept private** (read-only, nothing lost). Optionally first one commit in its README pointing to the new place. Deleting it is possible later.

## Order of work after approval

1. Prerequisites: `feature/emblem-layer` merged into the Mac `main` and pushed; all Android work merged into its `main` and pushed (today the `claude/*` worktree branches have no extra commits; another session may still be working).
2. Steps 1 to 5 above, locally, then `android/scripts/check-full`, `render-references.sh` (the references must come out unchanged: `git status` clean), `swift build` and `./build_app.sh` in `mac/`.
3. Show Mathis the result (diff, tree, check output). **Stop.**
4. Only on his yes: push `main` to `morja/mandala-generator` (fast-forward, no force), run the workflow once by hand, update the repository description, archive the Android repository.

## Risks

| Risk | Handling |
|---|---|
| Public is permanent: once pushed, the Android history may be cached or forked | secret check above; Mathis reads the docs (POC-1, RES-1, ADRs) once before the push |
| Release workflow breaks after the move | `workflow_dispatch` run before the next release tag |
| Work in progress on old branches or worktrees | done before the move; later merges still work through rename detection, but are fiddly |
| Mac `feature/white-bg` (two "working on" commits from April) | open question 6 |
| Tools or habits with old paths (`../Mandala-Generator`) | all known places listed above; a grep for `Mandala-Generator` after the move |
| `releases/latest` points to whatever was released last | only the Mac app publishes GitHub releases; Android goes through Google Play |
| Larger clone | both histories together are about 87 MB packed (Mac 57 MB, Android 30 MB, measured 20 October 2026); the largest file is the 3.9 MB `AppIcon.icns` |

## Open questions for Mathis

1. Home: `morja/mandala-generator` as above? (Recommended.)
2. History: keep both hashes with `git mv` and a merge (recommended), or rewrite into subfolders with `git filter-repo` and force-push the public `main`?
3. Release tags for the Mac app: keep `v*` or use `mac-v*` from the next release (recommended, leaves room for `android-v*`; old tags stay)?
4. `docs/` at the root for both apps (recommended), or under `android/`?
5. The old quote collection in the Android history: accept (recommended, already public in the Mac repository), or rewrite the Android history before the merge (breaks open Android branches)?
6. Mac `feature/white-bg`: push it along as a branch, keep it only locally, or drop it?
7. Local folder `/Users/mathis/Work/Projects/mandala-generator` for the new checkout?
8. Old Android repository: archive privately (recommended), with or without a pointer commit?
9. Repository description, for example "Mandala generator for macOS and Android"?
10. Pre-commit hook: also `swift build` for commits that touch `mac/`?

## Decisions (Mathis, 20 October 2026)

All recommendations above, with these details: `feature/white-bg` stays only in the local Mac checkout; the old Android repository is **deleted** instead of archived (its whole history is in the shared repository); the new checkout is `/Users/mathis/Work/Projects/Mandala-Generator/mandala-generator`, next to the project files that stay out of git (`generated/`, `samples/`); the hook also runs `swift build` for changes in `mac/`.
