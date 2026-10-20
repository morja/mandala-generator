# ADR-0008 · Both apps open source under GPL-3.0, sold as store builds

Status: accepted, 20 October 2026 (Mathis' decision)

## Decision

- The Android app (this repository) and the macOS app (`morja/mandala-generator`) are open source under the **GNU General Public License, version 3** (`LICENSE`), with the additional permission below.
- The apps are still sold: the Android app as a free app with the one-time Pro unlock (ADR-0007), the macOS app possibly later. People pay for a ready, signed and updated app, not for access to the code.
- Both apps belong in one public repository later (Mac first, then port; most changes touch both). Until that move, the Android repository stays private and is made public in the same step.

## Additional permission (GPL-3.0 section 7)

> If you modify this Program, or any covered work, by linking or combining it with the Google Play Billing Library or with the system and platform libraries of Android, macOS or iOS (or modified versions of those libraries), containing parts covered by the terms of their own licences, the licensors of this Program grant you additional permission to convey the resulting work. You may also convey the Program through app stores (such as Google Play and the Apple App Store) whose terms of service add restrictions on use or copying.

The same text stands in both READMEs.

## Reasons

- Mathis wants the work to stay open source. GPL rather than MIT: nobody may turn the code into a closed app; a copy must stay open.
- Keeping only one app closed protects almost nothing: new features are built in the public macOS app first, the Android app ports them.
- The Google Play Billing Library is proprietary, and the App Store's terms add restrictions; without the permission, others could not legally ship builds with them. Mathis, as the only author, can ship his own builds either way.
- Mathis is the only contributor of both repositories (checked 20 October 2026), so he can choose the licence and change it for later versions.

## Consequences

- Anyone may build the app from source, including the Pro feature (debuggable builds already count as Pro). Accepted: the store build is the convenient way.
- Someone may publish a copy in a store if it stays GPL. Mitigation: the app's name and regular updates; trademark only if it ever matters.
- Contributions from others come in under the same licence and the additional permission (note in the README); no separate contributor agreement for now.
- Before the Android repository becomes public, its history is checked for secrets (signing keys, Play Console credentials).

## Open

- ~~The macOS app's quote collection should follow ADR-0007 (public domain only).~~ Done 20 October 2026 (macOS app, merge of `feature/public-domain-quotes`).
- ~~Merging both apps into one public repository.~~ Decided in ADR-0009.
- This is not legal advice; for a larger commercial launch, a short check by a lawyer is cheap.
