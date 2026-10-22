# Architecture decisions

One file per decision, for both apps. A change is a new ADR; the old one is marked "replaced by ADR-NNNN".

ADR-0001 to ADR-0008 were written for the Android repository before both apps moved into this one (ADR-0009): their paths are relative to `android/`, and `docs/` means this folder.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-native-kotlin-compose.md) | Native Android app in Kotlin with Jetpack Compose | decided |
| [0002](0002-renderer-core-plain-kotlin.md) | Renderer as a plain Kotlin module shared by app and JVM tool | decided |
| [0003](0003-settings-json-compatible.md) | Settings JSON compatible with the macOS app | proposed |
| [0004](0004-parity-against-macos.md) | Parity tests against macOS reference images | proposed |
| [0005](0005-android-app-shell.md) | Android app shell: one screen, ViewModel, gallery export | proposed |
| [0006](0006-design-v1.md) | Design v1: warm neutrals around the mandala, one amber accent | proposed |
| [0007](0007-free-and-pro.md) | Free app with a one-time Pro unlock; public-domain quotes only | accepted |
| [0008](0008-open-source-gpl.md) | Both apps open source under GPL-3.0, sold as store builds | accepted |
| [0009](0009-one-public-repository.md) | One public repository for both apps | accepted |
| [0010](0010-export-formats-and-framing.md) | Export formats and framing: portrait images with the mandala placed by hand | accepted |
| [0011](0011-layer-positions.md) | Layer positions: each layer and the emblem placed on their own | accepted |
| [0012](0012-app-name-dahlia.md) | App name: Dahlia – Mandala Maker | accepted |
| [0013](0013-svg-line-drawing.md) | SVG line drawing as a Pro export | accepted |
| [0014](0014-pro-wallpapers-and-finish.md) | Pro wallpapers and a finishing step | accepted |
