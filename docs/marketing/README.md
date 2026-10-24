# Marketing material

Images to share with friends, on WhatsApp and elsewhere (MKT-1 names this folder for the Reddit assets as well).

- `MKT-2-share-images.html`: the share images as one HTML template with a switcher (variant, image set, language). Opened without parameters it is the viewer; with `?v=<variant>&s=<set>&lang=<en|de>` it shows one card at its pixel size. Each variant has several image sets (`SETS` in the HTML): different screenshots and a different background mandala.
- `texts/<name>.js`: one text set per audience (`friends`: the plain lines; `friends-story`: the narrative series for stories). The `message`, `facts`, `link` and `mandala` lines and the `teaser` lines (with alternatives) in English and German, plus the `stories` plan.
- `render-share-images.sh [name] [variant]`: renders every variant and set (or only one variant: portrait, story, landscape, square, mandala, wide, teaser, wallpaper) in English (Mathis posts in English; `LANGS="en de"` adds German) into `share/<name>/` with headless Google Chrome and ImageMagick (default `friends`). `render-share-images.sh <name> proof` is the proof run while deciding: only story sequence 1 in English, five images plus its overview, in about half a minute; the full run comes once everything is decided. The list of images comes from the HTML (`?list=1`); nothing is deleted. Needs the Inter font installed; the landscape QR code loads a small library from cdnjs, so render it online.
- `share/<name>/`: the rendered images of one text set, ready to send; **not in git** (`.gitignore`), sorted by use:
  - `stories/story-<n>-<lang>/`: ready-made story sequences, one folder each, images numbered in posting order (for example `1-teaser-hypocycloid-1`, `2-teaser-rumi-quote-2`, `3-teaser-ribbon-0`, `4-wallpaper-1`, `5-app-1`), plus `00-overview.jpg` with all of them side by side for comparing. The plan (`stories` in the text set: how many, which steps) decides the shape; a teaser with a line takes one of the plain mandalas, the teaser without text prefers a quote mandala (those never get a line, so there is no text on and under the image at once), nothing repeats within a sequence, the teaser lines vary between sequences, the app image cycles through its sets. Start here.
  - `story/`: the whole pool of story images: `1-teaser-<mandala>-<1|2|0>` (first line, second line, no text), `2-wallpaper-<n>` (variant H, the lock screen on the phone) and `3-app-<n>` (variant B, the app with title and link).
  - `portrait/`: variants A (`portrait-<n>`) and E (`mandala-<n>`) for chats and feeds.
  - `square/`: variant D.
  - `landscape/`: variants C (`landscape-<n>`, three phones and QR code) and F (`wide-<n>`, one or two phones, large text).
- `share/archive/`: earlier renders, kept on disk for reference (README there), not in git.
- `posted/`: what was actually posted, copied there by hand, in git (README there).
- `renders/`: large mandalas rendered from the starter favourites (`android/app/src/main/assets/starter-favorites.json`, the `parameters` of one entry saved as a settings file) with the CLI, used as backgrounds: portrait `mandala render <settings> <out.png> --width 1080 --height 1920 --zoom 1.15 --centre-y 0.40`, landscape `--width 1920 --height 1080 --zoom 0.52 --centre-x 0.72`, square `--size 1400`, teaser stories `--width 1080 --height 1920` with the centre at 0.5; saved as JPEG quality 92. Favourites with an image or gradient background show hard edges on non-square canvases; the hypocycloid, Flower of Life and ribbon ones work.

## New text, same images

1. Copy `texts/friends.js` to `texts/<name>.js` and change the lines.
2. Run `docs/marketing/render-share-images.sh <name>` (about seven minutes).
3. The images are in `share/<name>/`, sorted into `stories/`, `story/`, `portrait/`, `square/` and `landscape/`. The viewer shows a text set by its name in the "Text set" field and the file name of the shown image; "Stories" shows a whole sequence side by side.

New screenshots or backgrounds: change `SETS` in the HTML, new screenshots go to `../store/screenshots/`. English copy: no comma splices (two main clauses need a full stop).

## Safe zones in the story format

Instagram Stories and WhatsApp Status draw their own interface over the image: name and progress bar at the top, a caption (with the clickable link) and the reply field at the bottom. On a tall phone the 1080 × 1920 image sits between black bars and most of that interface falls into the bars: measured on Mathis' Galaxy S23 on 24 October 2026, about 60 px of the image are covered at the top and about 150 px at the bottom with a one-line caption (WhatsApp), about 150 px and 120 px on Instagram. The story-format cards (B, G, H) keep all text between `--safe-top` (150 px) and `--safe-bottom` (250 px), set in the HTML, with some reserve for shorter screens: the teaser and wallpaper lines sit just above the bottom band, the app card has its title below the top band, the link above the phone and the phone above the bottom band. The quote mandalas sit centred at full size, so a quote's author line may reach into the bottom band (Mathis' choice). The guides on the web quote 250 px top and 250 to 400 px bottom; those apply to screens that show the image without bars.
