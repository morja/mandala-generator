# ADR-0017 · App languages and quotes per language

Status: accepted, 26 October 2026 (Mathis, in conversation). Adds to ADR-0007 (public-domain quotes) and deviates from ADR-0008 (Mac first) for this feature.

## Decision

1. **Android first.** This feature is built in the Android app first; the macOS app follows where it is easy. A deliberate exception to "Mac first" (ADR-0008), decided by Mathis.
2. **Ten UI languages** in the Android app, chosen by store markets, none written right to left: English (default), German, Spanish, French, Portuguese (Brazil), Italian, Russian, Japanese, Korean, Chinese (Simplified). All UI text lives in `strings.xml`; Android picks the language from the device.
3. **One quote file per language** in `core` (`core/.../quotes/QuotesEn.kt`, `QuotesDe.kt`, …), quotes grouped by edition, each group naming its source (work, translator, year). Author names are written as usual in that language ("Laotse" in German, "Lao Tzu" in English). Still public domain only (ADR-0007).
4. **Which quotes are offered:** those of the device's preferred languages plus English, always. Chips at the top of the quote list switch languages on and off; the choice is kept. "Random quote" draws only from the languages switched on and the user's own quotes. A language with no quote file offers no chip; its users see English.
5. **Across languages:** the author of a quote in a saved mandala is found in every language file, so a favourite with a German quote shows its author on an English phone too. The user's own quotes are not tied to a language.

## Reasons

- Many people who set their phone to German, Dutch or Japanese read English well, and the English collection is the largest; hiding it would make the list poor for them. People who set no second language still get English, the one most likely understood.
- Chips instead of a setting buried in the menu: the choice is made where the quotes are.
- Kotlin files rather than JSON resources: `core` is meant to become a Kotlin Multiplatform module for the iPhone app (ADR-0015), where reading Java resources does not work; source files compile on every target and need no loader. A named source per edition makes the public-domain check visible in review.
- Markets rather than speaker counts: Hindi, Bengali and Urdu users mostly use apps in English, and Arabic and Urdu would need the whole layout mirrored and checked.

## Consequences

- Japanese, Korean and Chinese UI texts are machine-quality until a native speaker has read them; to do before they are promoted in the store.
- Quote files exist for English and German first; other languages get one only with enough verified public-domain quotes (originals of authors dead for more than 70 years, or translations by translators dead for that long).
- The macOS app keeps its English list until it is ported.
