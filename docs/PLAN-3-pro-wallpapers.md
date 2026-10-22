# PLAN-3 · More for Pro: finishing, wallpaper pair, daily and live wallpaper

22 October 2026. Status: draft for Mathis, nothing built. Decision draft: [ADR-0014](adr/0014-pro-wallpapers-and-finish.md).

Decided by Mathis today: "Show whole" and "Fill" are free in every format, a wallpaper from the menu fills the screen (ADR-0010), and there is no third option for a deeper fill (Pro users zoom by hand). Pro should get more by adding features, not by taking free ones away (ADR-0007: no paywall on saving, no watermark, no limit on layers or styles, which would also lock Mac files with five layers).

## 1. Finish (Pro, small)

A last adjustment of the saved image or wallpaper: **brightness** (down to about 10 %, for a dark home screen, and up to about 150 %), **contrast** and **saturation**.

- Part of the save choice like format and framing: not in the settings JSON, favourites or history, so ADR-0003 is untouched and the Mac sees nothing of it.
- Not the same as the effects layer's brightness and contrast: those belong to the mandala, follow the Mac and have a narrow range. Finish is per image and strong.
- Built in `core` as the last step after scaling down (`RenderOptions.finish`), per pixel on the 8-bit image, so it costs well under a second and no memory. The frame in the save sheet shows it.
- Without Pro the sliders show with a Pro mark and open the Pro sheet, like "Save large".

## 2. Home and lock screen as a pair (Pro, small to medium)

A fourth entry under "Set as wallpaper": **Home and lock, as a pair**. The lock screen gets the mandala filled; the home screen the same mandala with its own finish, darker by default (about 60 %) so icons stay readable, and optionally zoomed in further.

- Before setting, a sheet shows both side by side as two small phone screens, with a slider each for brightness. This is the preview Mathis asked for.
- Two renders, or one render and two finishes when only the finish differs; about 1 to 2 times today's wallpaper time.

## 3. Daily wallpaper (Pro, medium)

A switch **New wallpaper every day** in the menu. Every morning the app sets a new wallpaper by itself, filled, at 2048 px wide, with the pair's settings.

- Source, one of: the favourites in turn (recommended), a new variation of one chosen favourite, or a surprise mandala.
- Target: home, lock or both (the pair from 2).
- Runs once a day through Android's `JobScheduler` (no new package): early in the morning, preferably while charging; about 10 to 20 seconds of rendering. If the phone was off, the next run catches up.
- Kept across restarts of the phone, which needs the permission `RECEIVE_BOOT_COMPLETED` (approval).
- The app shows which mandala is today's and lets you save it as a favourite.

## 4. Live wallpaper (Pro, larger)

A **moving mandala** as wallpaper, through Android's `WallpaperService` (no new package; the service needs the `BIND_WALLPAPER` declaration).

- Rendering a frame takes seconds, so the mandala is rendered once, as a square as large as the screen's diagonal (so no corner shows while it turns), and then only moved on the graphics chip: **slow rotation** (one turn in a few minutes), optionally **breathing** (brightness about ±10 % over several seconds) and **colour drift** (hue turning through a colour matrix).
- 20 to 30 frames a second only while the wallpaper is visible; nothing while the screen is off or an app covers it. About 25 MB for the image.
- Combines with 3: a new mandala every day, moving.
- Battery use is measured on Mathis' phone before release (QA report).

## Order and approvals

Order: 1 Finish, 2 pair (uses 1), 3 daily (uses 2), 4 live (uses 3's source). Each is its own branch, checked on the phone with the fast build.

Needs approval: the extension of Pro in ADR-0007 (this plan), the permission `RECEIVE_BOOT_COMPLETED` (3), the wallpaper service declaration (4).

## Open questions for Mathis

1. **Finish sliders:** brightness, contrast and saturation, or also warmth or a vignette? Recommendation: the three; vignette exists in the effects layer.
2. **Finish only for Pro?** Recommendation: yes; a dark home screen is a typical reason to buy.
3. **Daily wallpaper source by default:** favourites in turn, variations of one favourite, or surprise? Recommendation: favourites in turn, since they are mandalas the user liked.
4. **Daily time:** early morning, or at the first unlock of the day? Recommendation: early morning while charging, with the first unlock as fallback.
5. **Live wallpaper movements:** rotation always, breathing and colour drift as switches? Recommendation: yes, rotation slow by default.
6. **Price:** stays 1.99 € with these features, or higher (for example 2.99 €) once 3 and 4 are in? Recommendation: raise it when the live wallpaper ships; existing buyers keep everything.
7. **Order** as above?
