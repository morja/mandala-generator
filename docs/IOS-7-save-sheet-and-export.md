# Dahlia – Mandala Maker

Session: iOS WP-8 save sheet and export · 12 November 2026

## IOS-7: Save sheet, export in the background, Photos, share, the line drawing and the moving picture (PLAN-4 WP-8)

What WP-8 of [PLAN-4](PLAN-4-iphone-app.md) built on top of [IOS-6](IOS-6-favourites-history-menu-play-field.md) and [IOS-8](IOS-8-tour-about-languages.md): WP-1's small sheet ("Save to Photos · 1024 px") grew into Android's full save sheet (F14 a). Screenshots: `docs/qa/shots/ios-wp8-2026-11-12/` in the main checkout (ignored by Git).

### What was built

1. **Shared rules through one new Kotlin door** (`android/editing/src/iosMain/.../IosSave.kt`): formats, picture and video sizes (`Editing.exportSize`, `videoSize`, the 360 px frame), file names, the save choice and its per-format placements (`SaveChoice.withFormat`), Fill's zoom (`MandalaRenderer.fillZoom`), framing moves and zooms, `Editing.framingFor`, `ProFeatures.removed`, the renders a save needs (framed picture with progress; the frame before its finish, so the finish sliders only re-finish it; the turning frame preview; the video's frames), `VectorExport.svg`. The save choice is stored with Android's keys and fallbacks (`encodeChoice` / `decodeChoice`). No rule is written twice in Swift.

2. **The save sheet** (`UI/SaveSheet.swift`, laid out as Android's DM-18 A3): Picture | Video as tabs with the Format menu beside them (Square, This screen, the eight ratios with "portrait" or "landscape"); the frame as large as the room allows; Whole and Fill as two round icons beside tall frames and below the others, hidden for the square; drag to move, pinch to zoom, double tap to centre (Pro); one line under the frame (the hint, "Placed by hand · Reset", or without Pro the note with "About Pro"); "Adjust" with brightness, contrast, saturation and "Quote moves with the mandala" (Pro), its summary and Reset in the heading; the length 5, 10, 15 or 30 s for a video; Save with the size in a second line and an arrow that picks 1024 or 2048 px (720 or 1080 for a video); Share or Cancel beside Save on the Video tab, in Android's weights (1 : 1.4). Each format keeps its own placement. The sheet stays open while an export runs.

3. **Frame preview** (`Model/EditorModel+Save.swift`, `Model/SaveState.swift`): latest wins with a ticket, 60 ms rest, six recent frames, a finger moves and scales the last frame until the next one is drawn (Android's `graphicsLayer`). On the Video tab the frame turns as the video will (fast glow, layer cache) and pauses while a video is made.

4. **Pictures to Photos**: the chosen format and framing, 1024 px supersampled or 2048 px drawn at size (Pro), with the finish and the quote placement; add-only Photos access (A10) asked before drawing; progress at the top of the sheet in Android's `ExportNote` look; PNG encoded off the main thread; a mandala with Pro features goes through `mayKeep` first.

5. **Share** (menu, Android's KeepRow): the square 1024 px picture through the system share sheet; the menu closes first and the progress shows above the editor.

6. **The moving picture** (`Model/Export.swift`, `VideoWriter`): frames from core (`LayerAnimation`, `VideoFrames`), written by Kotlin straight into the encoder's NV12 pixel buffers, encoded by `AVAssetWriter` as H.264 in MP4 with Android's bit rate (0.15 bits per pixel), 24 fps and one key frame a second; 5, 10, 15 or 30 s, 720 or 1080 on the shorter side, at most 1920 on the longer one. Anyone may watch it turn; saving and sharing is Pro (ADR-0010 point 10). Progress with minutes left from the fifth frame on; Cancel stops it and removes the file. Saved to Photos as a video or handed to the share sheet.

7. **Background** (`BackgroundWork`): every export holds a background task assertion; a video also asks for a continued processing task (iOS 26) with the system's own progress, title "Creating the video" and the size and length as subtitle. When iOS ends the time, the export stops cleanly with "Saving stopped because Dahlia was in the background too long." (finding 3).

8. **Line drawing (SVG)** (`UI/MenuSheet.swift`): its own page under More, as on Android, with "Colour on black" and "Dark on white, for plotters and colouring", the note on Strange Attractor layers, and "Save to Files" through the system's file exporter (Photos does not take SVG). Pro.

9. **The Pro seam**: `EditorModel.proOwned` is the one value WP-9 fills; release builds unlock nothing until then, debug builds count as Pro like Android's test builds (`pro_for_testing`), and `-proOwned NO` shows the free app. Every gate calls `requestPro(ProRequest)`, which WP-9 turns into the Pro sheet; until then it shows the note as a message, and for a mandala with Pro features "Save without them" (Android's `continueWithoutPro`).

10. **Strings**: all labels are Android's catalogue entries in ten languages; two new iOS texts (`message_video_saved` for Photos, `ios_export_stopped_background`) in all ten. Catalogue regenerated with `ios/scripts/gen-strings`.

11. **Debug hooks for measuring** (`ShotSetup.swift`): `-shotSave`, `-shotFormat`, `-shotVideo`, `-shotFill`, `-shotAdjust` for screenshots, `-exportProbe image|image2048|video|video1080|video1080share` with `-probeSeconds` for the memory, time and colour measurements; the export logs its peak footprint (`phys_footprint`) and, for a video, a line per second of video.

### Findings

1. **Colours of the video match Android's.** The same starter mandala (settings hash `102c2de6`, the file names agree) was saved as a 720 × 720, 5 s video on Android (emulator, `MediaCodec`) and on iOS (simulator, `AVAssetWriter`). Both files are tagged BT.709 primaries, BT.709 transfer, BT.709 matrix, limited range (`color_range=tv`), 4:2:0, 120 frames. Decoded with the same BT.709 limited-range conversion, the mean colour of the two videos differs by at most 0.19 of 255 per channel (R 109.65 / 109.64, G 65.53 / 65.71, B 29.47 / 29.66). Frame by frame they differ by 26.6 to 29.6 dB PSNR: that is the two encoders' compression of the fine mesh at about 2 Mbit/s, not colour; a frame against its neighbour gives 22.4 dB, so the frames line up. Side by side: `colour-frame60-android-left-ios-right.png` in the screenshot folder.

   *Why BT.709 limited range:* core converts each sRGB frame with the BT.709 matrix into limited (video) range (`VideoFrames`, unchanged), and the iOS app hands those bytes to the encoder as NV12 (`420YpCbCr8BiPlanarVideoRange`), so nothing converts them again; the tags say exactly what the bytes are. Limited range is what Photos, browsers and messengers assume for H.264 without asking; full range is often shown washed out or crushed after a messenger re-encodes. The same tags as Android's `COLOR_STANDARD_BT709`, `COLOR_TRANSFER_SDR_VIDEO`, `COLOR_RANGE_LIMITED`.

2. **Memory.** Peak footprint (`phys_footprint`, as Xcode's gauge), measured in the simulator on the Mac (12 threads), starter mandala, about 100 MB at rest:

   - 2048 px picture in "This screen" (2048 × 4450 on the large iPhone): 481 MB, render 2.9 s.
   - 1080 video, 30 s, "This screen" (884 × 1920): 686 MB, 269 s; the same at 10 s: 676 MB, so it levels off and does not grow with the length.
   - 720 square video, 5 s: 256 MB, 9 s.

   On the iPhone 15 (6 threads) a 10 s 1080 video (886 × 1920) peaked at 280 MB. The 2048 px picture was not measured on the phone: it needs the Photos question answered on the device. The worker pool and the thermal handling of WP-6 apply as planned: exports always use all cores and keep their pixels. The video's share is mostly the turning layers kept at full size for all frames (`LayerAnimation` with the layer cache), as on Android's `MemoryBudgetTest`. The review found that the picture's RGBA bytes were copied once more on the way to Swift; they now reach Swift without a copy, and the Kotlin collector runs after each export.

3. **The continued processing task works on the iPhone, but slowly.** `BGContinuedProcessingTask` (iOS 26) is checked: it is not available in the simulator at all ("BGTaskScheduler is not available on this platform"); identifiers must be registered one by one before each request (the wildcard in `BGTaskSchedulerPermittedIdentifiers` only permits them; registering the wildcard itself ends the app when the task arrives). On the iPhone 15 the request was accepted, the task started, and the video went on with Dahlia in the background (another app in front): in the foreground about 0.9 s per frame at 1080, in the background about 5 s per frame. A 10 s video at 1080 (240 frames) that went to the background after 2 s of video was finished in the background after 1058 s (17.6 minutes), the app never in front again. A 30 s video at 1080 takes about 11 minutes in the foreground and would take close to an hour in the background. Without the continued task (when iOS refuses it), the assertion lasts about half a minute and the video then stops cleanly with the message; checked in the simulator.

4. **No account signed in to Xcode** on this Mac after the restart (and `Config/Signing.xcconfig` still holds the placeholder team), so Xcode's automatic signing fails for the device. For this test the app was built unsigned and signed by hand with the local development identity and the team's wildcard profile (a script in the session's scratchpad, not in the repo). For WP-9 and TestFlight, Xcode needs the account again (Mathis: Xcode, Settings, Accounts) and the team id in `Config/Signing.xcconfig`.

### Differences from Android and why

- **No Wallpaper button** beside Save on the Picture tab: setting a wallpaper is WP-11 (F5); Save takes the full width until then (sent to the master chat as F1, recommendation a).

- **Photos instead of Pictures/Mandala and Movies/Mandala**: the messages say "to Photos"; there is no own album, because creating one makes iOS ask for full library access (IOS-1).

- **The line drawing goes to Files**, not to the gallery: Photos does not take SVG (PLAN-4 inventory).

- **Pro sheet**: a message with the note until WP-9 builds the sheet (F2 to the master chat, recommendation a).

- **H.264 High profile** instead of the Constrained Baseline Android's encoder picked: same colours (finding 1), a little smaller at the same bit rate, plays on every iPhone and in messengers.

- **Background**: Android keeps making the video while the screen stays on; iOS continues it in the background through the continued processing task, more slowly (finding 3).

- **The size of "This screen"** is read from the screen before each export (the window is not up when the app starts).

### What the review found

An Opus subagent reviewed the diff (export cancellation and background, memory, the writer, the Pro seams). Fixed:

1. The continued task's expiration handler would have asserted the main actor on iOS's own queue (Swift 6) and ended the app when the user stopped the task in the system's progress; it is `@Sendable` now.

2. A touch on the frame (drag with distance 0) switched Whole or Fill to "Placed by hand" and swallowed the double tap; the drag now starts after a move, the double tap runs beside it.

3. The picture's RGBA bytes (`NSMutableData`) were copied when bridged to Swift (36 MB at 2048 px); they are now immutable `NSData` over `malloc`ed memory, bridged without a copy, and the collector runs after each export.

4. The video's last frame had no length (no `endSession`), so the loop skipped at the seam; fixed.

5. When the continued task expired, the background time ended before the export had cleaned up (half file, kept layers); now only the ticket is cancelled and the export's own clean-up ends the task (the assertion still ends at once).

6. Two file exporters on the menu's view (line drawing and "Save settings"); the line drawing's moved to its page.

7. Kotlin failures other than cancelling (a failed render) went through Swift and ended the app; they now return null or false and the app says the save failed, as Android does.

8. Cancel stayed visible while the finished video was added to Photos, and a late background expiry said "stopped" although the picture was saved; Cancel shows only while frames are made, the message only when nothing was kept.

9. The system's progress appeared before the Photos question; the continued task is now requested after it.

10. The large size on the Picture tab without Pro showed the video's Pro note; it opens the general Pro request now.

11. The recent frames stay no longer after the sheet closes; a task arriving after the export ended reports failure, not success.

Left as is: every video registers its own identifier (iOS offers no way to remove one; a few strings a session).

### Verified

- `ios/scripts/check` and `ios/scripts/check-full` green (parity against the JVM unchanged, unsigned Release build); Android `scripts/check` green through the pre-commit hook (`android/core` and `android/editing` iOS sources changed, no JVM code).

- Simulator, iPhone SE (3rd generation, 375 pt, the smallest iPhone iOS 26 supports) and iPhone 18 Pro Max (440 pt), dark and light, English, German and Russian: Picture and Video tabs, This screen, 4:5 and square, with and without Pro, Adjust open. Screenshots in `docs/qa/shots/ios-wp8-2026-11-12/`.

- Simulator: 2048 px picture saved to Photos; 30 s 1080 video saved to Photos; 720 square video for the colour check; background without the continued task stops cleanly with the message.

- iPhone 15: debug build installed; a 10 s 1080 video made entirely in the background after the first 2 s (finding 3), peak footprint 280 MB.

### Steps for Mathis

- On the iPhone 15 ("Dahlia Debug" is installed and counts as Pro, like Android's test builds): open the save sheet, Video tab, This screen, 30 s, choose 1080 with the arrow, Save. Allow Photos once when asked. Play the video in Photos and compare its colours with the editor's picture.

- Send that video through a messenger (Signal or WhatsApp) to yourself and look at the colours there; they should match the editor and Android's video.

- Optional: start a video, go to the home screen, watch iOS's progress (Dynamic Island or the lock screen) and come back; it goes on, about five times slower meanwhile.

- In Xcode, sign in again under Settings, Accounts (the Mac lost the account), so builds for the phone sign themselves again.

### Selbst entschieden

- Video tab: Share and Save in Android's widths 1 : 1.4; on the Picture tab Save takes the whole width until WP-11 adds the wallpaper entry.

- The interim Pro sheet is a message with Android's texts; "About Pro" shows the note of the place it is tapped.

- H.264 High profile at Android's bit rate, one key frame a second.

- The sheet cannot be swiped away while an export runs, so Cancel and the progress stay in reach.

- Nothing is reused between two saves (Android keeps the last export for the wallpaper; wallpapers are WP-11).

- The continued task asks with "fail" rather than "queue": a queued task would only start after the video is done.

- Debug builds count as Pro (Android's debug and fast builds do too), so the phone check of the video needs no launch argument; `-proOwned NO` shows the free app. Earlier packages' debug builds showed the free app.

- Debug builds' trace goes to standard error unbuffered, so a launch with `--stderr` has the lines at once.

- The smallest iPhone for the screenshots is the iPhone SE (3rd generation) at 375 pt, smaller than the iPhone 17e earlier packages used.
