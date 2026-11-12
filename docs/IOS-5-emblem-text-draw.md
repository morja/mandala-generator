# Dahlia – Mandala Maker

Session: iOS WP-6 emblem, text, draw · 12 November 2026

## IOS-5: Emblem, Text and Draw tabs, moving on the picture (PLAN-4 WP-6)

What WP-6 of [PLAN-4](PLAN-4-iphone-app.md) built: the Emblem, Text and Draw tabs, the Core Text renderer behind core's `TextRenderer`, the quote list with language chips, moving layers, the emblem and the text on the picture with snapping, drawing, the thermal-state handling, and the two fixes from WP-5's screenshots. Checks: `ios/scripts/check`, `ios/scripts/check-full` (parity and text parity included), `android/scripts/check` and `android/scripts/check-full` are green. Screenshots (local, ignored by Git): `docs/qa/shots/ios-wp6-2026-11-12/` in the main checkout, with the text comparisons in `text-parity/`.

### What was built

1. **Emblem tab** (`ios/Dahlia/Sources/UI/EmblemTab.swift`): the switch row (label switches too, die and Reset while on, "· Pro" while off), the off note, the Pro note, then Motif (nine motifs with Android's small pictures, variant for the Flower of Life, petals for the Lotus, Organic where the motif uses it, shape number, "New shape", Branches and Roots for the tree), Style and frame, Shape (size, weight, rotate in degrees), Position with "Move on image", Colour (the palette group with own palettes, then colour position, spread, glow and the glow chips), Blending. Ranges, defaults, conditions and order as Android. The emblem pictures are Android's files, copied by `ios/scripts/gen-style-previews` (now for styles and motifs).

2. **Text tab** (`TextTab.swift`): the switch row (switching on with no words takes a random quote, as on Android), the Words card with both fields always open in the picture's own font, the bookmark to keep the words, Collection and New quote in the card's head, "Show author"; then Font (Android's fourteen chips), Size and place with "Move on image" (free, like the height slider), Light (glow, cloud, cloud size, shadow, their colours only while they show) and Colour. The quote list is a sheet: language chips (English, Deutsch), "Your quotes" with a remove button, the collections switched on, the public-domain note. Own quotes are kept in `my-quotes.json`, the languages across starts, as on Android.

3. **Draw tab** (`DrawTab.swift`): Lines and Spray chips with their stroke counts, "Draw on image" / "Done", Undo and Clear as round icon buttons, "Show lines/spray" once there are strokes, symmetry 1–16, the colour group open, and the line and spray groups with their blend chips. On the picture: guide rays per copy, the stroke in progress with its turned copies, a single tap paints a dot.

4. **Moving on the picture**: "Move on image" in the Position groups of a layer and of the emblem, and in the text's Size and place. A ring (a line for the text) and a dot show where the part is at once, the preview follows when the fingers rest; drag moves, pinch sizes, two fingers turn (not the text). On a centre line a guide crosses the picture and the phone ticks once (`sensoryFeedback(.selection)`). The gestures are UIKit's pan, pinch and rotation recognisers, which follow the middle of two fingers like Android's `detectTransformGestures`; one step per touch event goes to the shared rule. While moving or drawing, holding the picture (without effects) is off; leaving the tab ends moving or drawing, as on Android. The status line under the picture says what the fingers do.

5. **Shared rules, not copied**: the move rule with its snapping (`MoveGesture`, `Moving.mark`), the own quotes (`MyQuotes`, now with `contains` and `removeWords`) and the quote languages (`QuoteChoice`) moved from the Android app into `android/editing`; three literal ranges of Android's Draw and Emblem tabs became `Editing.Ranges` (`emblemRotation`, `drawSymmetry`, `sprayBrush`). Android calls them from there, behaviour unchanged; `MovingAndQuotesTest` covers them on the JVM and iOS. Swift reaches them through `IosEditingParts` (new, next to `IosEditing`) with `EmblemSlider`, `TextSlider`, `DrawSlider`.

6. **Pro marks as on Android**, still without the purchase: "· Pro" on the switch rows while off, the Pro notes, "Moved · Pro" in the position summaries, and the "Pro" mark on the picture while it uses Pro features (core's `ProFeatures`). The mark does not open anything yet (the Pro sheet comes with WP-9).

7. **Thermal state** (PLAN-4 section 3.2): `EditorModel` watches `ProcessInfo.thermalState`; from "serious" on the editor's previews use the fast glow and half the worker threads, and the kept previews drawn with the fast glow are dropped when it cools down while the full glow is chosen. Exports keep all threads and their pixels. Each change is logged ("DAHLIA thermal state=…"). Debug builds can force it in the simulator (`-thermalOverride serious`). **Not measured on the device**: the iPhone 15 was not reachable today, so whether half the threads is the right step is still open (PLAN-4 says "measured on the device in WP-6 before it is tuned").

8. **The two WP-5 fixes**: the tab row has the controls' side inset (16 pt); when a label does not fit its fifth, all five take the next smaller text role together (F13), and at the largest text sizes they may shrink further instead of overlapping. In German and Russian at 390 pt this is the case ("Zeichnen", "Эмблема"), on the 440 pt Pro Max German fits at the normal role. The layer row's second line keeps the percentage whole and shortens only the mode's name ("Negativ multiplizier… · 100 %"); the cut follows each language's own format.

### Text: Core Text renderer, fonts and parity

1. **The renderer** (`ios/Dahlia/Sources/Engine/CoreTextRenderer.swift`): core keeps line breaking and placement; Swift answers metrics and widths and draws the placed lines into an 8-bit mask per call (own bitmap context, so render threads share only the font cache behind a lock); `IosBridge.coverage` turns the bytes into core's coverage floats without a copy. Metrics are rounded as AWT's `FontMetrics` rounds them, and lines are shaped as Java2D shapes them (no pair kerning, only required ligatures, tracking in pixels after every letter), so lines sit where the JVM puts them. The author's italic is the family's italic face, or the upright face slanted by about 12° where there is none. Previews, the quick picture and the save all pass the renderer: the starter favourites with quotes now show their words in the editor and in saved pictures.

2. **Font mapping**. All fourteen fonts of the text tab are installed on iOS 26 under their macOS names (listed in the simulator with `-listFonts YES`), so iOS uses the Mac's own fonts. Android has none of them and maps by family:

   | Settings name | Mac and JVM | iOS (Core Text) | Android | iOS italic for the author |
   |---|---|---|---|---|
   | Zapfino | Zapfino | Zapfino | cursive | slanted |
   | Palatino-Roman | Palatino | Palatino-Roman | serif | Palatino-Italic |
   | Didot | Didot | Didot | serif | Didot-Italic |
   | Baskerville | Baskerville | Baskerville | serif | Baskerville-Italic |
   | Georgia | Georgia | Georgia | serif | Georgia-Italic |
   | Optima-Regular | Optima | Optima-Regular | sans-serif | Optima-Italic |
   | GillSans | Gill Sans | GillSans | sans-serif | GillSans-Italic |
   | Futura-Medium | Futura | Futura-Medium | sans-serif | Futura-MediumItalic |
   | HoeflerText-Regular | Hoefler Text | HoeflerText-Regular | serif | HoeflerText-Italic |
   | Copperplate | Copperplate | Copperplate | serif with small caps | slanted |
   | AmericanTypewriter | American Typewriter | AmericanTypewriter | monospace | slanted |
   | Papyrus | Papyrus | Papyrus | casual | slanted |
   | TimesNewRomanPSMT | Times New Roman | TimesNewRomanPSMT | serif | TimesNewRomanPS-Italic |
   | HelveticaNeue | Helvetica Neue | HelveticaNeue | sans-serif | HelveticaNeue-Italic |

   Any other name (from a file made elsewhere) falls back by core's family: Helvetica Neue, Courier New, Snell Roundhand or Georgia. No font is bundled. Installed families on the simulator: Academy Engraved LET, American Typewriter, Arial, Avenir, Avenir Next, Baskerville, Bodoni 72, Bradley Hand, Chalkboard SE, Chalkduster, Charter, Cochin, Copperplate, Courier New, DIN, Didot, Futura, Georgia, Gill Sans, Helvetica, Helvetica Neue, Hoefler Text, Marker Felt, Menlo, Noteworthy, Optima, Palatino, Papyrus, Party LET, Rockwell, Savoye LET, Snell Roundhand, STIX Two Text, Times New Roman, Trebuchet MS, Verdana, Zapfino, plus the script families (Hiragino, PingFang, Apple SD Gothic Neo, Kohinoor, Sangam, Noto and others).

3. **Text parity against the JVM** (the iPhone's reference, PLAN-4 section 3.11; the JVM on the Mac draws with the same fonts through Java2D). Fourteen cases in `ios/Dahlia/Tests/TextCases/`: the four starter favourites with quotes and ten fixtures on `style-floral` (one per font family, with glow, cloud, shadow, tracking, Multiply, a German quote with umlauts and the author in italic). `ParityReference.kt` renders them with `AwtTextRenderer`, `ParityTests.testTextCasesMatchTheJVM` with the Core Text renderer, at 512 px:

   | Case | PSNR | Pixels differing | Text box, worst edge |
   |---|---|---|---|
   | starter 2C46D01D (Hoefler Text, glow) | 22.8 dB | 12.6 % | 4 px |
   | starter EE248A2A (Palatino) | 32.2 dB | 23.5 % | 1 px |
   | starter F9B7E148 (Copperplate, shadow, cloud) | 27.8 dB | 19.9 % | 1 px |
   | starter FEEEEAC5 (Palatino, glow) | 27.8 dB | 31.9 % | 5 px |
   | Copperplate | 21.7 dB | 4.3 % | 2 px |
   | Didot with glow | 29.0 dB | 64.3 % | 6 px |
   | Futura with tracking | 28.3 dB | 5.2 % | 1 px |
   | Georgia | 26.7 dB | 6.1 % | 1 px |
   | Helvetica Neue, Multiply | 45.2 dB | 3.7 % | 0 px |
   | Hoefler Text, author in italic | 23.7 dB | 6.6 % | 1 px |
   | Palatino, German | 23.9 dB | 5.5 % | 1 px |
   | Papyrus with cloud | 25.1 dB | 6.7 % | 0 px |
   | American Typewriter with shadow | 24.9 dB | 9.7 % | 2 px |
   | Zapfino | 15.0 dB | 13.8 % | 20 px |

   Where the masks differ: the lines start, break and sit on the same pixels (no shift of the iPhone picture matches the JVM better than none), but Core Text and Java2D smooth the glyph edges differently. White letters on a dark mandala make every edge count, so the PSNR of the whole picture is low even where the side-by-side pictures look the same; with glow or shadow the difference spreads into the soft light (Didot: 64 % of the pixels differ slightly, the text box 6 px wider at the glow's edge). **Zapfino** is the one real difference: Core Text applies the font's contextual swashes, as the Mac's AppKit does, Java2D does not, so the lines are wider and break after other words; the iPhone is closer to the Mac here than the JVM. The test now holds text cases to 19 dB plus a check that the text's box lies within 8 px of the JVM's (a line out of place would be about 30 px), with Zapfino as a named exception at 13 dB. Comparison pictures (JVM | iPhone | difference): `docs/qa/shots/ios-wp6-2026-11-12/text-parity/`.

4. **Against Android**: Android draws all fonts with its system families (table above), so its text differs from the JVM and the iPhone in shape, width and line breaks for every font but the closest serif; no pixel comparison with the Android emulator was made, since the iPhone's contract is the JVM (PLAN-4 section 3.11). The rest of the picture (mandala, emblem, effects) is core's and matches the JVM as before: the 96 fixtures of the main parity test, 74 identical, worst 82.9 dB apart from the known strange attractor.

### Differences from Android and why

1. **The Pro mark on the picture does not open the Pro sheet**, and the Pro notes have no "About Pro" link: the sheet and the purchase are WP-9.

2. **The quote list is a sheet** with medium and large heights and Close at the top, instead of Android's dialog: iOS's usual form for a long list.

3. **The words fields use the picture's font at a Dynamic Type size** (Title 3 for the words, Body italic for the author), so large system text works; Android uses its type scale with the font family. These two lines carry `token-exempt`, since the font is the picture's, not the app's.

4. **The tab row shrinks to the note role in German and Russian on the 390 pt iPhones**, as F13 asks when a label does not fit; Android's tab row fits at a narrower inset.

5. **Moving shows the status line under the picture**, which pushes the tabs down by one line while moving or drawing (Android does the same below its canvas; on Android the folded picture hides it).

6. **Haptics**: one selection tick when a part comes onto a centre line; Android uses `TextHandleMove`.

### What the review found

An Opus subagent reviewed the diff (text renderer, gestures against the hold-to-compare and the scroll view, preview pipeline, controls against Android). No crash or data loss. Fixed:

1. Core Text kerned pairs and formed ligatures that Java2D does not, and any tracking switched the kerning off, so widths jumped; now shaped as Java2D (finding 1 of the text part). The text PSNR rose slightly (Hoefler 20.7 → 23.7 dB, Papyrus 23.2 → 25.1 dB).

2. The 19 dB threshold alone would let a misplaced line pass; the text-box check was added.

3. A two-finger gesture changed the settings up to three times per touch event (pan, pinch and turn separately); now added up and sent as one step, and the last movement at the end counts.

4. A drawing stroke cancelled by the system kept its points for the next stroke; a new start now begins a new stroke.

5. Saving own quotes (and palettes) from detached tasks could write an older list last; both now go through one serial queue.

6. The font cache only grew while sizes were dragged; it is limited and dropped on a memory warning.

7. `IosBridge.coverage` copied the mask bytes once more; it reads them in place.

8. At the largest text sizes the tab labels could overlap; a third step lets them shrink.

Checked and fine according to the review: thread safety of the renderer, the coordinate flip and row order, the italic slant, the metrics rounding, the rotation sign, hold-to-compare off during overlays, the latest-wins preview with the new heat logic, Android's behaviour after the move of the rules, ranges, defaults and Pro marks of the three tabs. Not changed: the review's note that the JVM measures with whole-pixel advances and draws with fractional ones is in the JVM reference, not in this package.

### Steps for Mathis on the iPhone

The iPhone 15 was not reachable today ("unavailable" in `devicectl`), so the new build is **not installed**. With the phone unlocked and connected, from the repository:

```
ios/Dahlia/scripts/run-device 00008120-0012416C2105A01E
```

Then in "Dahlia Debug":

1. Emblem tab: switch "Show emblem" on, scroll to Position, tap "Move on image". Drag the emblem away and slowly back to the middle: it snaps onto the centre lines, a line crosses the picture and the phone ticks once. Pinch to make it larger, turn it with two fingers. "Done" ends it.

2. Layers tab: Position, "Move on image" for the selected layer, same as above.

3. Text tab: switch the text on (a quote comes in), tap the list icon in the Words card, switch "Deutsch" on, pick a quote. Under "Size and place" tap "Move on image" and drag the text up and down; it snaps at the middle. Type in the text field; the picture follows.

4. Draw tab: "Draw on image", draw a few strokes, Undo, switch to Spray, spray, Done.

5. Leave the app busy for a few minutes (keep moving a heavy favourite) and say whether the picture becomes sluggish; the log line "DAHLIA thermal state" tells when the phone counts as hot.

### Selbst entschieden

- `IosEditingParts` as a second Swift-facing object next to `IosEditing`, so neither file grows past readability.
- Moved `MyQuotes` and the quote language rule into `android/editing` (`QuoteChoice`), and three literal ranges into `Editing.Ranges`, so iOS calls the same rules (Android unchanged).
- Thermal handling: half the worker threads while hot (at least one), fast glow forced, exports unchanged; a debug launch argument to force it.
- The words fields use the picture's font at Dynamic Type sizes (Title 3 and Body).
- The quote list as a sheet with medium and large heights.
- The Pro mark on the picture shown without an action until WP-9.
- Text parity: fourteen cases in `ios/Dahlia/Tests/TextCases` (starters plus one fixture per family) rather than new fixtures in `android/fixtures/settings`, which also feed the Android–Mac parity; threshold 19 dB plus the 8 px text-box check, Zapfino as a named exception.
- Debug-only launch arguments for screenshots and the font list (`-startTab`, `-shotFavourite`, `-shotEmblem`, `-shotMove`, `-shotDraw`, `-listFonts`, `-thermalOverride`).
- Simulators: iPhone 17e (390 pt, smallest) and iPhone 18 Pro Max (440 pt); dark and light; English, German, Russian (light in English and German).
