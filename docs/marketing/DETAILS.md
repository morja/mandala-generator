# Marketing material: details

Reference for the template, the script and the measurements behind [README.md](README.md).

## Template `MKT-2-share-images.html`

One HTML file, two jobs. Opened without parameters it is the viewer with a switcher (variant, image set, language, text set; "Stories" shows a whole sequence side by side and names the file of each image). With `?v=<variant>&s=<set>&lang=<en|de>&t=<text set>&seq=<n>` it shows one card at its pixel size, which the render script screenshots. `?list=1` prints the list of images and file names the script renders, so `SETS` in the HTML is the only place to edit.

Variants: A portrait 1080 × 1350 (phone in a mandala halo), B story 1080 × 1920 (app with title and link), C landscape 1920 × 1080 (three phones and a QR code to the Play page), D square 1080 × 1080, E mandala portrait 1080 × 1350 (one large mandala, no screenshot), F wide 1920 × 1080 (one or two phones, large text), G teaser story 1080 × 1920 (the mandala alone with one line or none), H wallpaper story 1080 × 1920 (lock screen on the phone).

Image sets (`SETS`): which screenshots (`../store/screenshots/phone-<n>.png`) and which background (`renders/<art>.jpg`) each variant uses. Teaser mandalas are not listed in the HTML: the script writes `renders/story-list.js` from the folders `renders/story/` (rendered with line 1, line 2 and without text, file names `…-1`, `…-2`, `…-0`) and `renders/story-quote/` (own text, rendered once without a line) before every run, alphabetically or with `SHUFFLE=1` in random order; JPEG or PNG, any size (the card uses `background-size: cover`, so a taller image is cropped top and bottom); the HTML reads that list.

Story sequences (`stories` in the text set, `count` and `steps`): `teaser:1` and `teaser:2` take a plain mandala with that line, `teaser:0` prefers a quote mandala, `wallpaper` the lock screen card, `app` the app card; nothing repeats within a sequence, the app card skips the lock screen screenshot when the sequence has a wallpaper card, lines with alternatives rotate by sequence number.

Style: ADR-0006 (near-black warm background, Inter, one amber accent). Safe zones for the story format: all text between `--safe-top` (150 px) and `--safe-bottom` (250 px); measured on Mathis' Galaxy S23 on 24 October 2026, WhatsApp Status covers about 60 px at the top and 150 px at the bottom with a one-line caption, Instagram about 150 px and 120 px, because the 9:16 image sits between black bars on a tall phone. The web guides' 250 and 400 px apply to screens without bars and were far too much. Quote mandalas sit centred at full size, so an author line may reach into the bottom band (Mathis' choice).

## Text sets `texts/<name>.js`

`message`, `facts`, `link`, `mandala` for the single images; `teaser` as two lists (line 1, line 2; several entries are alternatives by sequence number); `wallpaper` for the lock screen card; `stories` as the sequence plan. `default` holds the fixed narrative series ("Look what I've been working on." · "Another one. Made on my phone." · no text · "Decorate your lock screen." · app card).

## Script `render-share-images.sh`

```
render-share-images.sh [text set] [proof | all | variant]
LANGS="en de" render-share-images.sh             # German too (default English only)
SHUFFLE=1 render-share-images.sh                 # teaser backgrounds in random order
```

Without a second argument only the story sequences are rendered, into a new folder `share/<text set>/stories/<YYYY-MM-DD-HHMM>/`; `proof` renders sequence 1 into `stories/proof/`; `all` adds the single images and the story pool; a variant name renders just that variant. Single images and the pool are overwritten in place, run folders are never touched.

Renders with headless Google Chrome (Inter font must be installed; the landscape QR code loads a small library from cdnjs, so render online) and converts to JPEG with ImageMagick into `share/<text set>/`. Each image gets 90 seconds; a hung Chrome is reported and skipped, run again. Nothing is deleted: files of renamed or removed sets stay until removed by hand (earlier stands are in `share/archive/`, with a README). After rendering, every story folder gets `00-overview.jpg`. `proof` renders only story sequence 1 in English.

Output layout per text set: `stories/<run>/story-<n>-<lang>/` (sequences), `story/` (the whole pool: `1-teaser-<mandala>-<1|2|0>`, `2-wallpaper-<n>`, `3-app-<n>`), `portrait/` (A as `portrait-<n>`, E as `mandala-<n>`), `square/` (D), `landscape/` (C as `landscape-<n>`, F as `wide-<n>`).

## Backgrounds `renders/`

Teaser backgrounds live in `renders/story/` and `renders/story-quote/` (any JPEG there is used); the backgrounds of the other variants are the named files `<mandala>-portrait|landscape|square.jpg` referenced in `SETS`.

Rendered from the starter favourites (`android/app/src/main/assets/starter-favorites.json`, the `parameters` of one entry saved as a settings file) with the CLI `android/cli/build/install/mandala/bin/mandala render`: portrait `--width 1080 --height 1920 --zoom 1.15 --centre-y 0.40`, landscape `--width 1920 --height 1080 --zoom 0.52 --centre-x 0.72`, square `--size 1400`, teaser stories `--width 1080 --height 1920` with the centre at 0.5; saved as JPEG quality 92. Favourites with an image or gradient background show hard edges on non-square canvases, even rendered square and cropped (star, spiro, pyramid, maze); hypocycloid, Flower of Life, burst, ribbon, torus and the two quote ones work.

## Website cards `MKT-4-site-cards.html`

One card per `?c=<key>` (meera, advaita), 1080 × 1350, built from screenshots in `sites/`: desktop at 1440 px with headless Chrome, phone at 390 px through the Chrome DevTools MCP with mobile emulation (plain headless Chrome renders these sites wider than the window and cuts the right edge). Rendered to `share/sites/<key>-portrait.jpg`.

## Business Suite

Instagram @tearoom.one is linked to the Facebook Page "Tearoom One" (business portfolio "Tearoom"); Business Suite schedules feed posts, reels and stories without interactive stickers. Composer quirks: the hashtag autocomplete swallows clicks that land under it, so move the caret off the last hashtag (or click a heading) before clicking anything else; the file picker is native, Claude attaches the hidden input by script; decline the Boost dialog after scheduling. Scheduled posts are recorded in `posted/`.
