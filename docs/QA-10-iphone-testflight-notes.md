# Dahlia – Mandala Maker

Session: master chat · 12 November 2026

## QA-10: Mathis' notes from the first TestFlight build on the iPhone

Build 1.0 (1), installed through TestFlight on the iPhone 15 on 12 November 2026. Collected as Mathis reports them; fixed later in one cleanup package unless marked urgent.

### Notes

1. **The tour reacts with small delays between steps.** Mathis found this a little irritating; not urgent. Likely cause (IOS-8, finding 3): each step first switches the tab and scrolls the row under the tabs, and only then marks the step settled and draws the rings, so the card and rings appear a moment after the tap. To look at: show the card at once and let only the rings follow, or shorten the settling time; compare with Android's tour on the same steps.

2. **Restarting the tour** works from Menu › Dahlia › Tour; Mathis first looked under "Guide", which opens the website guide. Noted as a hint on wording or order, no change decided.
