# PLAN-6 · Android localization and Play translations

26 October 2026. Status: next step requested by Mathis; implementation has not started.

## Agreed scope

- Localize the Android app into a small set of important languages, with translations maintained in the repository. English remains the default and fallback language. Start with German; agree on the remaining priority languages before translating them.
- The macOS desktop app stays in English. Mathis explicitly excludes desktop localization from this work. Android localization does not require a matching macOS feature.
- Adjust Google Play's automatic app-string translation settings so languages maintained by us are excluded. Google may manage other selected languages. Do not assume existing bundled translations take priority: Google replaces translations for languages selected for automatic translation.

## Implementation sequence

1. Inventory Android UI text, including accessibility labels, errors, formatted values and plurals. Most UI text is currently embedded in Kotlin; `strings.xml` currently contains only the app name and wallpaper description.
2. Extract user-facing text into English Android string resources and use resource lookups in the UI. Keep stored data, enum identifiers and settings JSON unchanged. Names and user-authored content must not be translated automatically.
3. Prepare a small terminology glossary for recurring controls such as Seed, Variation, layers and effects. Add reviewed translations for the agreed languages. Handle the bundled quotation collection separately from UI translation.
4. Test translated layouts on phone and tablet, including long labels, dialogs, accessibility and English fallback. Do not add untested right-to-left support merely by enabling another language.
5. Before the next translated release, remove our maintained languages from Play Console's automatic app-string translation selection. Upload a new bundle to a draft release and check the resulting translations before publishing.

## Store listing and product text

Mathis also asked whether we should maintain storefront translations ourselves. Recommendation, pending agreement on languages: maintain the priority languages in the repository alongside the app terminology, including title, short/full description and relevant Pro product text. Review the existing English and German listing first, then extend to the agreed languages. Treat screenshot text as a separate localization task.

The screenshot supplied on 26 October shows a completed machine-translation order for 28 languages. This records the screenshot only; it does not independently establish which translations are currently published. Existing Google translations can be reviewed and used as drafts rather than rewritten without inspection. Do not apply or replace store text as part of recording this plan.

Store listing/product translations and automatic app-string translations are separate Play Console workflows. Changes to one must not be assumed to configure the other.

Reference: [Google Play translation documentation](https://support.google.com/googleplay/android-developer/answer/9844778?hl=en).
