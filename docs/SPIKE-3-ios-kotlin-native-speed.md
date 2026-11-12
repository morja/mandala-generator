# Dahlia – Mandala Maker

Session: SPIKE-3 iOS Kotlin/Native speed · 11 November 2026

## SPIKE-3: Is the Kotlin core fast enough on an iPhone?

Question from ADR-0015 ("Open"): does the shared Kotlin `core`, compiled with Kotlin/Native into an XCFramework, render fast enough on an iPhone, or should the iPhone app fall back to the Swift renderer of the macOS app (PLAN-4, path A)?

Short answer: **yes, with conditions. In a release build the iPhone 15 renders the typical favourite's editor preview in 1.3 s, its 1024 px export in 5.2 s and the worst case at 1024 in 16 s, 1.2 to 1.9 times faster than the Galaxy S23 runs the same core on Android today, with a peak of 385 MB and the same picture as the JVM. The conditions are the release framework, the worker-pool `Parallel` and the cooperating collector (finding 8).**

### Setup

- `android/core` is now a Kotlin Multiplatform module with the targets JVM, `iosArm64` and `iosSimulatorArm64` (approved 11 November 2026). The XCFramework `MandalaCore` is built with `ios/SpikeBench/build-core.sh`.

- Benchmark app `ios/SpikeBench` (SwiftUI, one screen, bundle id `one.tearoom.dahlia.spike3`, signed automatically with the existing wildcard development profile, so no bundle id was registered). It renders three settings at three sizes, one warm-up and three timed runs each, and logs the median, the fastest run and the peak memory footprint. `run-sim.sh` and `run-device.sh` build, install and run it.

- The same cases, sizes and repetitions run on the JVM and on Android through `android/cli/src/test/.../SpikeBench.kt` (a `main`, not a test).

- **Settings** (in `ios/SpikeBench/Bench/`): *light* is the fixture `android/fixtures/settings/spirograph-basic.json` (one spirograph layer); *favourite* is starter favourite 16 of the Android app (waveInterference, sunburst and tesseract layers, the effects layer, a gradient background), a typical three-layer favourite; *worst* is the `MemoryBudgetTest` case (five heavy layers with glow, wash and the heavy painted effect, gradient background, all memory-hungry effects, hand drawing, spray paint and an emblem with glow). None of them uses text or an image background, so `TextRenderer` and `ImageLoader` are not needed (null); nothing is stubbed.

- **Sizes:** *preview* is the Android editor's preview (512 px, supersampled, fast glow at 800 px, the default "Fast preview"); *1024* is the free export (supersampled, drawn at 2048 internally); *2048* is the Pro export (not supersampled). Same `RenderOptions` as the Android app uses.

- **Machines:** iPhone 15 (A16, 2 performance + 4 efficiency cores, 6 GB), iOS 26.6.1, release build; the iOS simulator (iPhone 17, iOS 27.2) on a MacBook Pro M3 Pro (12 cores, 36 GB), which runs the same arm64 Kotlin/Native code natively; the JVM tool on the same Mac (OpenJDK 21, HotSpot, 12 threads); the Galaxy S23 (SM-S911B, 8 cores), the core run through `app_process` on ART (JIT, the same runtime the installed fast build gets, without its profile-guided compilation).

### Findings

1. **Times (median of three runs after a warm-up, milliseconds).**

   | Settings | Size | iPhone 15 | Simulator (M3 Pro) | JVM (M3 Pro) | Android S23 |
   |---|---|---:|---:|---:|---:|
   | light | preview 512 | 197 | 54 | 46 | 304 |
   | light | 1024 | 633 | 201 | 117 | 761 |
   | light | 2048 | 511 | 175 | 104 | 779 |
   | favourite | preview 512 | 1 322 | 494 | 298 | 2 540 |
   | favourite | 1024 | 5 234 | 1 768 | 1 196 | 6 838 |
   | favourite | 2048 | 5 392 | 1 979 | 1 160 | 9 246 |
   | worst | preview 512 | 3 570 | 1 375 | 836 | 6 269 |
   | worst | 1024 | 16 169 | 5 343 | 3 662 | 24 147 |
   | worst | 2048 | 17 927 | 5 761 | 4 406 | 22 484 |

   Kotlin/Native against HotSpot on the same Mac: 1.2 to 1.7 times the time (median about 1.6). The iPhone 15 takes about 3 times as long as the simulator on the M3 Pro (2.6 to 3.6) and about 4.4 times as long as the JVM there (4.1 to 5.4); it is 1.2 to 1.9 times faster than the S23 (median about 1.5). The S23 figures come from `app_process` without the installed app's profile-guided compilation, so the shipped Android app is probably somewhat faster than this column; the iPhone is still at least on par with the phone the Android app is used on every day. The three timed runs on the iPhone were within 1–2 % of each other except one slow first run per case (for example 17.8 s against 16.0 s for worst at 1024), so the phone did not throttle noticeably over the roughly three minutes of the whole run.

   Runs vary by about ±10 % between repetitions on the Mac (other work on the machine); single slow runs (for example 8.8 s once for worst at 2048 in the simulator) are left out by the median. The S23 runs were not cooled and show the same spread.

2. **Memory: Grand Central Dispatch made the footprint explode; a fixed worker pool fixed it.** The first version split the work with `dispatch_apply` on a global queue, as PLAN-4 suggested. The renders were correct, but the app's footprint grew to 8.9 GB for the favourite at 1024 and to 21.5 GB for the worst case, and the simulator was eventually shut down. The cause is not the renderer's working set: with one thread the worst case peaks at 307 MB. The renderer allocates a lot of short-lived memory (measured on the JVM: about 3.7 GB per favourite at 1024 and 9 GB per worst case, mostly per-row `FloatArray` buffers in `Filters.resampleRows` and the blur, and full-image copies), which HotSpot's young generation absorbs; with Grand Central Dispatch's pool threads the Kotlin/Native collector did not free it during a render. With a fixed set of Kotlin/Native workers that live as long as the app (plus the calling thread), the peaks are back to the working set, at the same speed:

   | Settings | preview 512 | 1024 | 2048 |
   |---|---:|---:|---:|
   | light, simulator | 85 MB | 236 MB | 229 MB |
   | favourite, simulator | 128 MB | 329 MB | 349 MB |
   | worst, simulator | 122 MB | 350 MB | 379 MB |
   | worst, iPhone 15 | 107 MB | 350 MB | 385 MB |

   Peak `phys_footprint` of the whole app, sampled every 2 ms during the four renders, including about 20–100 MB the app holds outside the renders. iOS ends a foreground app at about 3 GB on a 6 GB iPhone and about 2 GB on a 4 GB one (iPhone 13 and 13 mini), so even the worst case fits with room to spare.

3. **Parity: the iPhone draws the same picture as the JVM.** The 1024 px exports from the simulator compared with `mandala render … --size 1024` on the JVM: *light* and *worst* are identical to the byte (PSNR infinite), *favourite* differs by at most 1/255 in a few pixels (96.3 dB, float rounding). The same three exports copied off the iPhone 15 give the same result: *light* and *worst* identical to the byte, *favourite* 96.3 dB. Kotlin/Native on the A16 and in the simulator produce the same pixels.

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

6. **Estimate for an iPhone 13 (A15) — estimate, not measured.** The A15 has the same core layout as the A16 (2 + 4) and is about 15 % slower per core and about 15–20 % slower with all cores (published benchmarks). Expected times are therefore about 1.2 times the iPhone 15 column: light about 240 / 760 / 610 ms, favourite about 1.6 / 6.3 / 6.5 s, worst about 4.3 / 19 / 22 s (preview / 1024 / 2048). Its 4 GB (13 and 13 mini) leave about 2 GB for the app, five times the measured worst-case peak.

7. **Widgets need their own plan on iOS.** A widget extension may use only about 30 MB, so it cannot render a mandala itself as the Android widget does (512 px through `core`, ADR-0020); the app (or the Shortcuts action) has to render the pictures ahead and share them through an App Group. This also affects the "Mandala of the day" intent, which runs in the app's process only if it opens the app.

8. **Recommendation: accept ADR-0015 with conditions.** The shared Kotlin core is fast enough on an iPhone: an iPhone 15 is at least as fast as the Galaxy S23 on which the Android app is used and sold today, an iPhone 13 (estimated) is about as fast as the S23, the memory peak is a fifth of what iOS allows on a 4 GB phone, and the pictures are identical. Path A (porting the Swift renderer of the macOS app) is not needed for speed. The conditions, all of which are in place in this branch:

   - Ship only the release (optimised) framework; the debug framework is 5 to 8 times slower and is useful only for debugging.

   - Keep the worker-pool `Parallel` in `iosMain`; do not go back to Grand Central Dispatch, which let the footprint grow to 21 GB (finding 2).

   - Keep `gcMutatorsCooperate=true` in `android/core/build.gradle.kts`.

   - The iPhone editor needs the same handling as the Android editor: the preview of a heavy favourite takes 1–3.5 s, so it must render off the main thread, show the last picture until the new one is ready and drop outdated renders. Exports of heavy settings (up to about 18 s at 2048 on an iPhone 15, about 22 s estimated on an iPhone 13) need a progress indicator and must survive a short trip to the background.

   - Widgets render in the app and share pictures through an App Group (finding 7).

   Worth doing later for both platforms, not a condition: the renderer allocates 4 to 9 GB of short-lived memory per heavy render (finding 2); reusing the per-row buffers in `Filters.resampleRows` and the blur would cut work for both collectors.**

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

- Bundle id `one.tearoom.dahlia.spike3`, signed with the existing wildcard development profile of team 6735ZK5WKV; nothing registered or created in App Store Connect.

- The Xcode project is generated with `xcodegen` from `ios/SpikeBench/project.yml`, and the generated `SpikeBench.xcodeproj` is committed so the app opens without the tool.

- The iOS `Parallel` uses a fixed pool of Kotlin/Native workers instead of Grand Central Dispatch (finding 2); `gcMutatorsCooperate=true` stays in the build.

- The "typical favourite" is starter favourite 16 of the Android app; the preview uses the Android editor's fast-glow default.

- The JVM and Android benchmark is a `main` in the cli test sources (`SpikeBench.kt`), so it ships in no build; Android was measured through `app_process` with the core dexed, not through the installed app.

- `:core:test` is kept as an alias of `jvmTest`, so scripts and the reference renderer did not need to change.

- The iPhone 13 figures are an estimate from the A16 numbers (factor 1.2), not a measurement.
