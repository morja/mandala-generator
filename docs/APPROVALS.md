# Approvals

What needs approval: new packages, new design tokens, new folders in the root directory, deviations from an ADR. Agents add open requests here instead of building a workaround.

## Open

- **Packages of the proof of concept** · Already in use: Kotlin Gradle plugin 2.4.20, kotlinx-serialization-json 1.11.0 (settings JSON), kotlin-test with JUnit 5 (tests) · Alternative without approval: none sensible · Recommendation: approve.
- **ADR-0003 and ADR-0004** · Proposed with the proof of concept · Recommendation: accept.
- **ADR-0005 and ADR-0006** · App shell (minSdk 29, one screen, gallery export) and design v1 · Recommendation: accept after trying the app on a phone.
- **Core Image's random tile as a data file** · `CIRandomGenerator` turns out to be one 256 x 256 tile of 8-bit RGBA values repeated without end (measured 19 October 2026). Shipping that tile (256 KB, read once from Core Image on a Mac) would make the grain of the effects layer, the grain background and the heavy painted effect pixel-identical to the Mac; today they have the same strength but another pattern (Mac favourites with grain: 30–40 dB instead of about 45). Open point: the table is output of Apple's framework, so whether it may be shipped is your call · Alternative without approval: keep the own hash grain (same look, different pattern) · Recommendation: only if exact grain matters to you; visually the difference is not noticeable.
- **Bundled fonts for the text layer** · Android has none of the macOS fonts (Zapfino, Didot, Papyrus …), so they map to the closest system family. Open-licence look-alikes (for example from Google Fonts, OFL) could be shipped in the app · Alternative without approval: system families (current) · Recommendation: decide after looking at quotes on the phone.

Format: `- **What** · What for and why · Alternative without approval · Recommendation`

## Approved

19 October 2026 (Mathis asked for the Compose app shell): root folder `app`; Android Gradle plugin 9.4.1, Kotlin Compose plugin 2.4.20, Compose BOM 2026.09.00 (ui, material3), activity-compose 1.13.0, core-ktx 1.19.1, lifecycle-runtime-compose and lifecycle-viewmodel-compose 2.11.0, JUnit 4.13.2 for app unit tests.

19 October 2026: root folders `core`, `cli`, `docs`, `fixtures`, `scripts`, `tools`, `gradle`, `.githooks` (project setup).
