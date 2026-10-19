# Approvals

What needs approval: new packages, new design tokens, new folders in the root directory, deviations from an ADR. Agents add open requests here instead of building a workaround.

## Open

- **Packages of the proof of concept** · Already in use: Kotlin Gradle plugin 2.4.20, kotlinx-serialization-json 1.11.0 (settings JSON), kotlin-test with JUnit 5 (tests) · Alternative without approval: none sensible · Recommendation: approve.
- **ADR-0003 and ADR-0004** · Proposed with the proof of concept · Recommendation: accept.
- **ADR-0005 and ADR-0006** · App shell (minSdk 29, one screen, gallery export) and design v1 · Recommendation: accept after trying the app on a phone.

Format: `- **What** · What for and why · Alternative without approval · Recommendation`

## Approved

19 October 2026 (Mathis asked for the Compose app shell): root folder `app`; Android Gradle plugin 9.4.1, Kotlin Compose plugin 2.4.20, Compose BOM 2026.09.00 (ui, material3), activity-compose 1.13.0, core-ktx 1.19.1, lifecycle-runtime-compose and lifecycle-viewmodel-compose 2.11.0, JUnit 4.13.2 for app unit tests.

19 October 2026: root folders `core`, `cli`, `docs`, `fixtures`, `scripts`, `tools`, `gradle`, `.githooks` (project setup).
