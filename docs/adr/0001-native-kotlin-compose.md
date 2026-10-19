# ADR-0001 · Native Android app in Kotlin with Jetpack Compose

Status: decided, 19 October 2026 (user decision, following the evaluation in this session)

## Decision

The Android version is a native app written in Kotlin, with the UI in Jetpack Compose.

Rejected:
- **Flutter / React Native:** the renderer would still have to be ported, to Dart or JavaScript; no gain.
- **Progressive web app:** possible, but less native for saving to the gallery, sharing and setting wallpapers.
- **Shared Rust/C++ core for macOS and Android:** would mean rebuilding the working macOS app.
- **Java** (as in `android-create-event`): the UI holds a lot of state (layers, sliders, palettes); Compose fits better.

## Reasons

The macOS app is mostly pure math in Swift; Swift and Kotlin are close enough for a near line-by-line port.

## Consequences

Two code bases (Swift and Kotlin) must be kept in step by hand. ADR-0004 makes drift visible.
