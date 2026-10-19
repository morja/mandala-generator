# ADR-0002 · Renderer as a plain Kotlin module shared by app and JVM tool

Status: decided, 19 October 2026 (user decision)

## Decision

All rendering lives in the Gradle module `core`, written in plain Kotlin: stdlib, `java.util.concurrent` and kotlinx.serialization only. No Android, AWT, ImageIO or coroutine APIs. Image input and output belong to the caller (`cli` today, the Android app later).

Core Image filters of the macOS app are rebuilt on the CPU in `Filters.kt` and `LayerEffects.kt`.

## Reasons

- The same code runs on the Mac in the JVM, so it can be compared against macOS output without a phone.
- Unit tests run in seconds without an emulator.
- A later move of hot paths to C++ (NDK) stays possible behind the same API.

## Memory rules

A phone app gets 256–512 MB of heap. The renderer therefore:
- keeps images between steps as 8-bit `Image8`, float only inside a step;
- enlarges a layer buffer to the full radius only for rotated layers, otherwise by the effect margin;
- draws with threads in bands of one shared buffer, never one buffer per thread;
- blurs in place and resamples row by row;
- computes the painted effect in strips of 512 rows with an overlap as tall as displacement and blurs reach (bit-identical to the whole-image result).

`MemoryBudgetTest` (fast check) renders the worst case the app allows, five layers with strong glow, wash and painted effect, at 1024 px under a 256 MB heap limit.

## Consequences

- `ArchitectureTest` fails on forbidden imports.
- Display P3 and sRGB handling is done in `ColorSpaces`, not by the platform.
