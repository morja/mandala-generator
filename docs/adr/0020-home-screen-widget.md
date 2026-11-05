# ADR-0020 · Home screen widget "Mandala of the day"

Status: accepted, 26 October 2026 (Mathis, UX-1 14, DM-14). Android only.

## Decision

1. One widget, "Mandala of the day". Square or upright sizes (2×2 to 4×4) show the picture alone, rendered in the widget's own shape so the whole mandala shows and its background goes on. Wide sizes (width over height above 1.3, e.g. 4×2) show the picture without its text at the left and the words at the right: the favourite's own quote, otherwise a quote of the day from the chosen quote languages.
2. It shows the daily wallpaper's turn: the same ticked favourites (ADR-0014), chosen on the daily wallpaper page also while the daily wallpaper is off. A tap opens that favourite in the editor.
3. Free for everyone; without Pro it stays on the first of the chosen favourites. With Pro it moves on every morning in the same job as the daily wallpaper, which now also runs while only a widget is placed (it then only moves the turn on).
4. Plain `AppWidgetProvider` and `RemoteViews`, no new package (Glance would be one). RemoteViews cannot use the Compose tokens, so `res/values*/widget.xml` repeats the surface and text colours of `Tokens.kt`; keep them in step.

## Consequences

The widget renders a 512 px picture when it is placed, resized or moved on, off the main thread. The morning job is planned while the daily wallpaper is on or a widget is placed (with Pro) and cancelled when neither is.

## Amendment, 5 November 2026 (Mathis)

On Mathis' phone one of three widgets showed the empty text for days while favourites existed: all widgets were rendered one after another inside the update broadcast, which Android ends after about ten seconds. Changes to points 2, 3 and 4:

- **Settings per widget.** Each placed widget has its own settings, kept per `appWidgetId` and removed with the widget: *Mandala* is the daily turn (default, as in point 2 and 3) or one fixed favourite, free for everyone; a deleted fixed favourite falls back to the daily turn. *Appearance* is "Like the app" (default, the app's own light or dark setting), "Light" or "Dark". The settings page (`WidgetSettingsActivity`, Compose) is registered as `reconfigurable|configuration_optional`: placing needs no settings, a long press offers them, and the menu's Dahlia page lists the widgets under "Widgets".
- **Colours.** `res/values/widget.xml` holds the light and the dark values, and the widget sets them on its views itself; `values-night` only decides the card before the first picture.
- **Tap.** A tap opens the app as it was. Only when the widget shows another mandala than the editor, a message offers it with "Open"; nothing is replaced without that.
- **Rendering in a job.** The receiver only asks for a redraw. A JobScheduler job (expedited from Android 12 on) draws the waiting widgets one by one, each with its own try/catch, updates each as soon as its picture is ready and falls back to the favourite's thumbnail when rendering fails. The empty text shows only when there is no favourite at all.

Consequences, in addition: a widget is drawn again when it is placed, resized or reconfigured, after the morning run, and when what it would show changes (a favourite deleted, the daily choice, the app's theme, Pro, the starter favourites written on the first start). Each widget remembers what it last showed, so only the affected ones are drawn.
