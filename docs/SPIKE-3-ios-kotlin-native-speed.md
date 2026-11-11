# Dahlia – Mandala Maker

Session: SPIKE-3 iOS Kotlin/Native speed · 11 November 2026

## SPIKE-3: Is the Kotlin core fast enough on an iPhone?

Question from ADR-0015 ("Open"): does the shared Kotlin `core`, compiled with Kotlin/Native into an XCFramework, render fast enough on an iPhone, or should the iPhone app fall back to the Swift renderer of the macOS app (PLAN-4, path A)?

Short answer: **IPHONE_SUMMARY**

### Setup

- `android/core` is now a Kotlin Multiplatform module with the targets JVM, `iosArm64` and `iosSimulatorArm64` (approved 11 November 2026). The XCFramework `MandalaCore` is built with `ios/SpikeBench/build-core.sh`.

- Benchmark app `ios/SpikeBench` (SwiftUI, one screen, bundle id `one.tearoom.dahlia.spike3`, signed automatically with the existing wildcard development profile, so no bundle id was registered). It renders three settings at three sizes, one warm-up and three timed runs each, and logs the median, the fastest run and the peak memory footprint. `run-sim.sh` and `run-device.sh` build, install and run it.

- The same cases, sizes and repetitions run on the JVM and on Android through `android/cli/src/test/.../SpikeBench.kt` (a `main`, not a test).

- **Settings** (in `ios/SpikeBench/Bench/`): *light* is the fixture `android/fixtures/settings/spirograph-basic.json` (one spirograph layer); *favourite* is starter favourite 16 of the Android app (waveInterference, sunburst and tesseract layers, the effects layer, a gradient background), a typical three-layer favourite; *worst* is the `MemoryBudgetTest` case (five heavy layers with glow, wash and the heavy painted effect, gradient background, all memory-hungry effects, hand drawing, spray paint and an emblem with glow). None of them uses text or an image background, so `TextRenderer` and `ImageLoader` are not needed (null); nothing is stubbed.

- **Sizes:** *preview* is the Android editor's preview (512 px, supersampled, fast glow at 800 px, the default "Fast preview"); *1024* is the free export (supersampled, drawn at 2048 internally); *2048* is the Pro export (not supersampled). Same `RenderOptions` as the Android app uses.

- **Machines:** iPhone 15 (A16, 2 performance + 4 efficiency cores, 6 GB), iOS IPHONE_IOS, release build; the iOS simulator (iPhone 17, iOS 27.2) on a MacBook Pro M3 Pro (12 cores, 36 GB), which runs the same arm64 Kotlin/Native code natively; the JVM tool on the same Mac (OpenJDK 21, HotSpot, 12 threads); the Galaxy S23 (SM-S911B, 8 cores), the core run through `app_process` on ART (JIT, the same runtime the installed fast build gets, without its profile-guided compilation).

### Findings

1. **Times (median of three runs after a warm-up, milliseconds).**

   | Settings | Size | iPhone 15 | Simulator (M3 Pro) | JVM (M3 Pro) | Android S23 |
   |---|---|---:|---:|---:|---:|
   | light | preview 512 | P_L1 | 54 | 46 | 304 |
   | light | 1024 | P_L2 | 201 | 117 | 761 |
   | light | 2048 | P_L3 | 175 | 104 | 779 |
   | favourite | preview 512 | P_F1 | 494 | 298 | 2 540 |
   | favourite | 1024 | P_F2 | 1 768 | 1 196 | 6 838 |
   | favourite | 2048 | P_F3 | 1 979 | 1 160 | 9 246 |
   | worst | preview 512 | P_W1 | 1 375 | 836 | 6 269 |
   | worst | 1024 | P_W2 | 5 343 | 3 662 | 24 147 |
   | worst | 2048 | P_W3 | 5 761 | 4 406 | 22 484 |

   Kotlin/Native against HotSpot on the same Mac: 1.2 to 1.7 times the time (median about 1.6). PHONE_COMPARISON

   Runs vary by about ±10 % between repetitions on the Mac (other work on the machine); single slow runs (for example 8.8 s once for worst at 2048 in the simulator) are left out by the median. The S23 runs were not cooled and show the same spread.

2. **Memory: Grand Central Dispatch made the footprint explode; a fixed worker pool fixed it.** The first version split the work with `dispatch_apply` on a global queue, as PLAN-4 suggested. The renders were correct, but the app's footprint grew to 8.9 GB for the favourite at 1024 and to 21.5 GB for the worst case, and the simulator was eventually shut down. The cause is not the renderer's working set: with one thread the worst case peaks at 307 MB. The renderer allocates a lot of short-lived memory (measured on the JVM: about 3.7 GB per favourite at 1024 and 9 GB per worst case, mostly per-row `FloatArray` buffers in `Filters.resampleRows` and the blur, and full-image copies), which HotSpot's young generation absorbs; with Grand Central Dispatch's pool threads the Kotlin/Native collector did not free it during a render. With a fixed set of Kotlin/Native workers that live as long as the app (plus the calling thread), the peaks are back to the working set, at the same speed:

   | Settings | preview 512 | 1024 | 2048 |
   |---|---:|---:|---:|
   | light, simulator | 85 MB | 236 MB | 229 MB |
   | favourite, simulator | 128 MB | 329 MB | 349 MB |
   | worst, simulator | 122 MB | 350 MB | 379 MB |
   | worst, iPhone 15 | PM_W1 | PM_W2 | PM_W3 |

   Peak `phys_footprint` of the whole app, sampled every 2 ms during the four renders, including about 20–100 MB the app holds outside the renders. iOS ends a foreground app at about 3 GB on a 6 GB iPhone and about 2 GB on a 4 GB one (iPhone 13 and 13 mini), so even the worst case fits with room to spare.

3. **Parity: the iPhone draws the same picture as the JVM.** The 1024 px exports from the simulator compared with `mandala render … --size 1024` on the JVM: *light* and *worst* are identical to the byte (PSNR infinite), *favourite* differs by at most 1/255 in a few pixels (96.3 dB, float rounding). PARITY_DEVICE

4. **What brought what (Kotlin/Native levers).**

   - Release (optimised) framework instead of debug: 5 to 8 times faster (light 1024: 2 190 ms debug against about 200 ms release; favourite 1024: 18.9 s against 1.8 s). Only the release framework is usable.

   - Memory manager: the default collector (concurrent mark and sweep) with `gcMutatorsCooperate=true`, so render threads help with marking instead of waiting for it: 5 to 20 % faster (favourite preview 600 → 466 ms, worst 1024 5 577 → 5 359 ms), kept in `android/core/build.gradle.kts`. Switching the collector to stop-the-world (`gc=stwms`), a fixed collection threshold (`GC.autotune = false`, 64 MB target) and the other settings did not fix the Grand Central Dispatch footprint and were slower; the fix was the thread pool (finding 2).

   - Threads through the platform `Parallel`: the worst case at 1024 takes 25.9 s on one thread and 5.3 s on twelve in the simulator (4.9 times). THREADS_DEVICE

5. **What had to change in `core`.** About 100 changed lines in common code plus 170 new lines for iOS, as PLAN-4 section 6 estimated:

   - Sources moved to `src/commonMain` (renderer), `src/jvmMain` (the old `Parallel` and a `ThreadLocal`), `src/iosMain` (new) and `src/jvmTest` (all existing tests, unchanged). `:core:test` stays as an alias of `jvmTest`, so the scripts, the reference renderer and the Android build did not change.

   - `expect`/`actual` for `Parallel`, for a per-thread scratch value (`PerThread`, was `ThreadLocal` in two places) and for the core count; common replacements for `System.arraycopy`, `Math.round`, `Math.PI` and `System.nanoTime`.

   - `IosBridge` (iOS only): settings from JSON, a render call without Kotlin default arguments, RGBA bytes as `NSData` for a `CGImage`, and two collector helpers for the benchmark.

   - `ArchitectureTest` checks all `core` source sets for platform APIs and `commonMain` for `java.*`.

   - `scripts/check` and `scripts/check-full` (parity and release build) are green; the Android app's behaviour is untouched.

6. **Estimate for an iPhone 13 (A15) — estimate, not measured.** The A15 has the same core layout as the A16 (2 + 4) and is about 15 % slower per core and about 15–20 % slower with all cores (published benchmarks). Expected times are therefore about 1.2 times the iPhone 15 column: ESTIMATE_13. Its 4 GB (13 and 13 mini) leave about 2 GB for the app, five times the measured worst-case peak.

7. **Widgets need their own plan on iOS.** A widget extension may use only about 30 MB, so it cannot render a mandala itself as the Android widget does (512 px through `core`, ADR-0020); the app (or the Shortcuts action) has to render the pictures ahead and share them through an App Group. This also affects the "Mandala of the day" intent, which runs in the app's process only if it opens the app.

8. **Recommendation: RECOMMENDATION**

### PLAN-4 against today's Android app

What PLAN-4 (22 October 2026) does not know yet, as far as noticed during this spike; the plan itself is not rewritten:

- **Android first:** since 27 October 2026 new features are built in the Android app first (AGENTS.md); ADR-0015's "Mac-first rule stays" and PLAN-4's order (Mac, then `core`, then both phones) are outdated.

- **Widgets with settings per widget** (ADR-0020, amendment of 5 November 2026: daily turn or a fixed favourite, appearance, rendering in a job). PLAN-4 has one iPhone widget "today's mandala" and assumes it renders itself (see finding 7).

- **Onboarding tour** (`Tour.kt`, `TourOverlay.kt`): not in PLAN-4.

- **Own palettes** (ADR-0021, free, carried inside the settings): not in PLAN-4; the format is in `core`, so the iPhone gets the drawing for free but needs its own editor UI.

- **Ten UI languages** and quotes per language (ADR-0019): PLAN-4 plans English and German listings only. The quotes are Kotlin files in `core` and reach the iPhone; the UI strings (`strings.xml`) need an iOS string catalogue.

- **Moving picture (MP4)** is Pro on Android since 26 October 2026 (ADR-0016 point 4, ADR-0010 point 10); PLAN-4 says "no video export". If the iPhone gets it, `core` already draws the frames (`LayerAnimation`, `VideoFrames`) and AVFoundation would write the file.

- **Free line amended:** three free layers and no favourite limit (ADR-0016, 22 and 24 October 2026); PLAN-4's decision list still says "up to five favourites for free".

- **Apple Developer account and devices:** the account exists (team 6735ZK5WKV) and the lowest device Mathis wants is about the iPhone 13; PLAN-4 question 5 is answered, and "iOS 17 or later" can be reconsidered against today's iOS 27.

- **Speed spike:** PLAN-4 section 7 step 1 also asks to check the Shortcuts "Set wallpaper" automation; that was not part of this spike and is still open.

### Selbst entschieden

- SELF_DECIDED
