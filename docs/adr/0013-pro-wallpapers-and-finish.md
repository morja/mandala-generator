# ADR-0013 · Pro wallpapers and a finishing step

Status: proposed, 22 October 2026 ([PLAN-3](../PLAN-3-pro-wallpapers.md)); not decided yet.

## Decision (as recommended in PLAN-3, pending Mathis' answers)

1. **Free stays as it is:** every format, "Show whole" and "Fill", saving and sharing at 1024 px, wallpapers filling the screen (ADR-0010). Nothing free is taken away for Pro.
2. **Pro adds, besides 2048 px and placing by hand (ADR-0007, ADR-0010, ADR-0011):**
   - a **finish** for the saved image or wallpaper: brightness (about 10 to 150 %), contrast, saturation; part of the save choice, not of the mandala (ADR-0003 untouched);
   - **home and lock screen as a pair**, with a preview of both and the home screen darker by default;
   - a **daily wallpaper**, set by the app every morning from the favourites in turn;
   - a **live wallpaper**: the mandala rendered once and turned slowly on the graphics chip, optionally breathing and drifting in colour.
3. **No new packages:** `JobScheduler` for the daily wallpaper, `WallpaperService` for the live one. New in the manifest: `RECEIVE_BOOT_COMPLETED` and the wallpaper service.
4. **Order:** finish, pair, daily, live; each checked on the phone with the fast build, the live wallpaper's battery use measured before release.

## Reasons

- Wallpapers are the app's main use on a phone (ADR-0010), so Pro features there are the ones people use every day.
- Limiting layers, styles or favourites would hold back what the app is about and lock files from the Mac; a watermark would spoil shared images, the best advertising (ADR-0007).
- Rendering takes seconds, so a live wallpaper can only move a finished image; rotation, brightness and hue are cheap on the graphics chip.
