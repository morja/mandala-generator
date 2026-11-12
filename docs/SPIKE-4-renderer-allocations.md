# Dahlia – Mandala Maker

Session: SPIKE-4 renderer allocations · 12 November 2026

## SPIKE-4: Fewer short-lived allocations in the renderer

Question from SPIKE-3 (finding 2 and the last point of finding 8): a heavy render allocates 4 to 9 GB of short-lived memory. Does reusing the per-row and per-step buffers make heavy renders faster on Kotlin/Native (iPhone) and ART (Android), without changing a pixel? The estimate before this spike was 20 to 40 % on the iPhone.

Short answer: **allocations per render fell by 57 to 74 % (worst case 8.9 → 2.3 GB, favourite 3.7 → 1.0 GB), every pixel is unchanged, the live working set is lower, but the time gained on the iPhone is small: about 3 % on the heavy cases (up to 5 %), not 20 to 40 %. The iPhone's render time is spent computing, not collecting. The Galaxy S23 got too warm during the runs for a statement (finding 2).**

### Method

- Same cases, sizes and repetitions as SPIKE-3: *light*, *favourite*, *worst* at preview 512, export 1024 and Pro 2048; one warm-up and three timed runs per size, median. Tools: `android/cli/src/test/.../SpikeBench.kt` (JVM and ART through `app_process`) and `ios/SpikeBench` (simulator and iPhone).

- Before and after were measured in the same session, alternating: the old core (commit `ec20ad8`) and the new one were built side by side (a second worktree for the old XCFramework, two dex files for the phone) and run in turn, case by case, with the order swapped every round, three rounds on iOS. A measurement against the SPIKE-3 table would mix in other load: the Mac was busy with other builds during this spike (load average 10 to 56), and both phones warm up over a run.

- **Pixels:** `android/cli/src/test/.../RenderHashes.kt` (new, a `main`, not a test) prints a SHA-256 of the pixels of every fixture in `android/fixtures/settings/` (93) and of the three benchmark cases in 12 variants each: preview 512 with fast glow, export 1024, Pro 2048, a render with 3 threads, a portrait canvas (540 × 960) with framing, a finish, and three frames of the moving picture through the layer cache with their I420 video planes. 1 152 hashes before, 1 152 after.

- **Allocations:** bytes allocated per render on HotSpot (`ThreadMXBean`), and where they come from with Java Flight Recorder (`jdk.ObjectAllocationSample`).

- **Peak memory:** the peak `phys_footprint` the iOS benchmark samples; on the JVM the heap still in use after each full collection with a tight heap (`-Xmx` 220 to 340 MB, serial collector), and `MemoryBudgetTest`.

### Findings

1. **Pixels are identical.** All 1 152 hashes are the same before and after (`diff` empty), for every fixture, size, canvas shape, finish, thread count and moving-picture frame. `scripts/check` and `scripts/check-full` (parity test against the macOS references and release build included) are green.

2. **Times (median of the per-size medians, milliseconds; "paired" is the median of the per-round ratio new/old).**

   | Case | Size | iPhone 15 old → new | paired | Simulator old → new | paired | JVM old → new | Galaxy S23 old → new | paired |
   |---|---|---:|---:|---:|---:|---:|---:|---:|
   | light | preview 512 | 162 → 166 | +7 % | 58 → 55 | −4 % | 39 → 42 | 317 → 336 | (−5 %) |
   | light | 1024 | 656 → 699 | −2 % | 218 → 223 | −6 % | 112 → 99 | 826 → 824 | (+6 %) |
   | light | 2048 | 720 → 748 | +4 % | 177 → 188 | +7 % | 95 → 91 | 611 → 841 | (+38 %) |
   | favourite | preview 512 | 1 814 → 1 770 | −2 % | 532 → 432 | −19 % | 271 → 254 | 2 964 → 2 990 | (+16 %) |
   | favourite | 1024 | 7 089 → 6 774 | −3 % | 2 010 → 1 872 | −7 % | 1 076 → 1 120 | 9 040 → 6 940 | (−23 %) |
   | favourite | 2048 | 7 119 → 6 671 | −3 % | 1 810 → 1 694 | −8 % | 1 136 → 1 068 | 8 480 → 8 187 | (−4 %) |
   | worst | preview 512 | 4 797 → 4 499 | −5 % | 1 367 → 1 316 | −5 % | 840 → 779 | 8 232 → 6 082 | (−20 %) |
   | worst | 1024 | 21 047 → 20 098 | −3 % | 5 514 → 5 962 | +7 % | 3 465 → 3 326 | 26 427 → 25 520 | (−4 %) |
   | worst | 2048 | 21 084 → 20 146 | −3 % | 5 459 → 5 395 | −3 % | 3 268 → 3 106 | 26 606 → 27 508 | (−3 %) |

   - **iPhone 15:** the heavy cases are 2 to 5 % faster, consistently in every round; light is within the noise (its runs last a fraction of a second, and the phone throttles: the old worst case at 1024 took 19.3, 21.0 and 21.5 s in the three rounds, against 16.2 s in SPIKE-3 on a cool phone).

   - **Simulator:** favourite 7 to 19 % faster, worst and light within ±7 %; the Mac was busy with other work, so single rounds vary by up to 15 %.

   - **JVM (HotSpot):** unchanged within the noise (−12 to +8 %); the young generation took the old garbage almost for free.

   - **Galaxy S23 (ART): no conclusion.** Four rounds through `app_process` (two of them with an intermediate state that differs only in two row buffers), with two minutes of cooling before each run. The phone still went from 29.8 to 35.7 °C and was put on the charger during the run; the per-round ratios new/old range from 0.4 to 2.6, so the medians in brackets say nothing either way. A fair comparison needs the installed fast build on a cool phone, ideally several short runs per case.

3. **Allocations per render (JVM, MB).**

   | Case | preview 512 | 1024 | 2048 |
   |---|---:|---:|---:|
   | light | 107 → 38 (−64 %) | 367 → 134 (−63 %) | 298 → 127 (−57 %) |
   | favourite | 1 004 → 291 (−71 %) | 3 722 → 985 (−74 %) | 3 711 → 982 (−74 %) |
   | worst | 2 282 → 671 (−71 %) | 8 910 → 2 343 (−74 %) | 8 972 → 2 340 (−74 %) |

   Where the old allocations came from (all cases together, flight recorder): the source rows `Filters.resampleRows` read into a new array per row and chunk (24 %), copies of float images for the blur passes of glow, wash and bloom (21 %), new float strips and images (16 %), the blur's per-chunk line and column buffers (21 %), the drawing strips of each layer (6 %), and per-pixel boxing of the background fill (2 %). What is left now is mostly the float images at the 1 600 px reference size that glow and wash blur (about 50 %), the scene effects' strips (about 10 %), the painted effect's strip and colour bleed (7 %) and the layer images themselves (4 %).

4. **Memory: the live working set is lower; the iOS footprint with the default collector setting is not always.**

   | | old | new |
   |---|---:|---:|
   | JVM, heap in use after a full collection (max), favourite 2048 | 232 MB | 200 MB |
   | JVM, same, worst 1024 | 115 MB | 75 MB |
   | JVM, same, worst 2048 | 248 MB | 218 MB |
   | Simulator, footprint with a fixed collector target (64 MB), light 2048 | 166 MB | 147 MB |
   | Simulator, same, worst 1024 | 250 MB | 215 MB |
   | Simulator, same, worst 2048 | 264 MB | 226 MB |
   | iPhone 15, footprint (default setting), worst 1024 | 349 MB | 332 MB |
   | iPhone 15, same, worst 2048 | 383 MB | 321 MB |
   | iPhone 15, same, favourite 2048 | 336 MB | 327 MB |
   | iPhone 15, same, light 2048 | 206 MB | 256 MB |
   | Simulator, footprint (default setting), light 2048 | 232 MB | 274 MB |

   - Full collections on the JVM dropped to a third or a quarter (worst 2048: 118 → 34).

   - With the default setting Kotlin/Native's collector starts after a share of new allocations; with a quarter of the garbage it starts less often, so large dead arrays (the layer image, the glow copies) lie around longer. This raises the footprint of the light case at 2048 by about 45 MB (and the worst preview by about 8 MB) while the heavy cases' peaks fall by up to 60 MB. With a fixed collector target, which takes the scheduling out of the comparison, every peak is lower than before. The highest peak of all cases stays the worst case's (now 332 to 343 MB against 349 to 389 MB before), far below the 2 to 3 GB iOS allows.

   - `MemoryBudgetTest` (all nine renders within a 256 MB heap) is green.

   - The smallest `-Xmx` with which a render still finishes (serial collector, 4 MB steps) moved by −9 to +15 MB between the cases. That number also depends on how long objects survive (reused buffers are promoted to the old generation, short-lived ones die young), so it is not a measure of the working set; the heap in use after full collections above is.

5. **What changed in `core` (about 210 added and 55 removed lines, no API change for the app or the iOS bridge).**

   - `Scratch` (new): growing work arrays per thread, owned by `Parallel` (`Parallel.scratch`, a `PerThread`), so they live as long as one render. Only row-sized arrays go there (rows, a ring of source rows, a block of 16 columns), never full-size buffers (ADR-0002). `PerThread` got `remove()`, and the JVM `Parallel.close()` drops the calling thread's arrays.

   - `Filters.resampleRows`: source rows in a ring as long as the filter's taps instead of a `HashMap` with a new array per row and chunk; each row is still read once per chunk, the sums are the same.

   - `Filters.blurInPlace`: the kernel (or the box radii) is made once per call instead of once per chunk; line and column buffers come from the scratch.

   - `LayerEffects.referenceBlurOverlay`: the middle blur passes reuse one spare copy instead of a new copy each (wash: 3 instead of 5 reference-size images per call, glow unchanged at 3). The tiled variant, the painted effect and the drawing strips (`PixelBuffer.drawInStrips`) reuse one set of strip arrays for all strips of a step; `composite`, `rotate`, the row mapping, the scene effects and the emblem's blend take their rows from the scratch.

   - `Background`: the per-pixel fill is a `fun interface` instead of a Kotlin function type, whose generic `invoke` boxed both coordinates for every pixel.

   - Same order of every float operation as before, which is why the hashes match.

6. **Tried and dropped.**

   - Reusing the scene effects' strip arrays (`EffectsLayer.chain`) as well: their steps shrink strip by strip (local contrast, smoother), and one set of arrays at the tallest height kept more alive at once than the old code; `memoryTest` ran out of its 256 MB heap. Taken back; only the row buffers there come from the scratch.

   - An earlier intermediate state of that change was what the first simulator round and the minimum-heap search measured; both were repeated with the final code, and only those numbers are in this report.

7. **Not done: ideas that would change pixels, retain memory or need approval.**

   - **One pool of reference-size float images per render** (glow, wash, bloom, painted strips borrow and return them): would remove most of the remaining 2.3 GB, but keeps about 90 MB alive between steps (three 1 600 px float images), so the peak would rise in the phases that do not blur. Given finding 2 (cutting three quarters of the allocations bought 3 % on the iPhone), the remaining quarter is unlikely to buy much more; not worth the memory without a measurement that says otherwise.

   - **Collector setting for the iPhone app:** with a fixed target of 64 MB (instead of the autotuned one) the simulator rendered the worst case 16 to 23 % faster with this core and with a lower peak (finding 4). That is a setting of the app (or of `IosBridge`), not of `core`, and needs its own measurement on the iPhone and a decision when the iPhone app is built.

   - **Platform filters on iOS** (vImage/Accelerate for blur and resampling): the blur passes are where the time goes now; vector code would be much faster but rounds differently, so pixels change (parity, ADR-0004) and it needs approval (ADR-0002, ADR-0015).

   - **Cheaper blur arithmetic in common code** (single-precision prefix sums in the box blur, fewer passes for large sigma): faster on every platform, but changes pixels.

   - Observation outside the scope: building the XCFramework prints "Mutators cooperation is not yet supported in CMS GC", so `gcMutatorsCooperate=true` (SPIKE-3 finding 4 and condition) is ignored by the Kotlin version in use; the 5 to 20 % SPIKE-3 credited to it are probably measurement noise. Worth a check before the iPhone app relies on it.

### Selbst entschieden

- Report kind SPIKE (next free number 4), because it answers the open "worth doing later" of SPIKE-3 with a measurement; no PERF kind exists in the repository.

- `RenderHashes.kt` stays in the cli test sources as a tool for later pixel-identity checks, next to `SpikeBench.kt`.

- The per-thread arrays belong to `Parallel` (one render) instead of a global object, so neither the iOS workers nor a calling thread keeps them after a render.

- Before and after were measured alternately in this session instead of against the SPIKE-3 table; the "paired" column is the median of the per-round ratios, which takes most of the phones' warming out of the comparison.

- The scene effects' strips were left as they were (finding 6), and the reference-size pool was not built (finding 7).
