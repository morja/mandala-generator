# ADR-0020 · Home screen widget "Mandala of the day"

Status: accepted, 26 October 2026 (Mathis, UX-1 14, DM-14). Android only.

## Decision

1. One widget, "Mandala of the day". Square or upright sizes (2×2 to 4×4) show the picture alone, rendered in the widget's own shape so the whole mandala shows and its background goes on. Wide sizes (width over height above 1.3, e.g. 4×2) show the picture without its text at the left and the words at the right: the favourite's own quote, otherwise a quote of the day from the chosen quote languages.
2. It shows the daily wallpaper's turn: the same ticked favourites (ADR-0014), chosen on the daily wallpaper page also while the daily wallpaper is off. A tap opens that favourite in the editor.
3. Free for everyone; without Pro it stays on the first of the chosen favourites. With Pro it moves on every morning in the same job as the daily wallpaper, which now also runs while only a widget is placed (it then only moves the turn on).
4. Plain `AppWidgetProvider` and `RemoteViews`, no new package (Glance would be one). RemoteViews cannot use the Compose tokens, so `res/values*/widget.xml` repeats the surface and text colours of `Tokens.kt`; keep them in step.

## Consequences

The widget renders a 512 px picture when it is placed, resized or moved on, off the main thread. The morning job is planned while the daily wallpaper is on or a widget is placed (with Pro) and cancelled when neither is.
