# Marketing material

Images to share with friends, on WhatsApp and elsewhere (MKT-1 names this folder for the Reddit assets as well).

- `MKT-2-share-images.html`: the share images as one HTML template with a switcher (variant, image set, language). Opened without parameters it is the viewer; with `?v=<variant>&s=<set>&lang=<en|de>` it shows one card at its pixel size. Each variant has several image sets (`SETS` in the HTML): different screenshots and a different background mandala.
- `texts/<name>.js`: one text set per audience (`friends`: the plain lines; `friends-story`: the narrative series for stories). The `message`, `facts`, `link` and `mandala` lines and the `teaser` lines (with alternatives) in English and German, plus the `stories` plan.
- `render-share-images.sh [name] [variant]`: renders every variant and set (or only one variant: portrait, story, landscape, square, mandala, wide, teaser, wallpaper) in English and German into `share/<name>/` with headless Google Chrome and ImageMagick (default `friends`). The list of images comes from the HTML (`?list=1`); nothing is deleted. Needs the Inter font installed; the landscape QR code loads a small library from cdnjs, so render it online.
- `share/<name>/`: the rendered images of one text set, ready to send; **not in git** (`.gitignore`), sorted by use:
  - `stories/story-<n>-<lang>/`: ready-made story sequences, one folder each, images numbered in posting order (for example `1-teaser-hypocycloid-1`, `2-teaser-rumi-quote-2`, `3-teaser-ribbon-0`, `4-wallpaper-1`, `5-app-1`), plus `00-overview.jpg` with all of them side by side for comparing. The plan (`stories` in the text set: how many, which steps) decides the shape; mandalas rotate so none repeats within a sequence, the teaser lines vary between sequences, the app image cycles through its sets. Start here.
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

Instagram Stories and WhatsApp Status draw their own interface over a 1080 × 1920 image: the name and progress bar over the top (about 250 px), the reply field over the bottom (Instagram about 250 to 340 px) and, on WhatsApp, the caption you type (the clickable link goes there) above the reply field, together about 400 px. The figures are community measurements, not published by Meta, and vary a little by device. The story-format cards (B, G, H) therefore keep all text between `--safe-top` (270 px) and `--safe-bottom` (420 px), set in the HTML: the teaser and wallpaper lines sit just above the bottom band, the app card has its title below the top band and the link above the phone, and the phone may run off the bottom. The quote mandalas are rendered with the quote above the bottom band (`--centre-y 0.42 --text follows`). Check once on a real phone with a caption; if WhatsApp covers more, raise `--safe-bottom`.

| Variant | Size | For |
|---|---|---|
| A portrait | 1080 × 1350 | WhatsApp chats, Instagram feed |
| B story | 1080 × 1920 | WhatsApp status, Instagram story |
| C landscape | 1920 × 1080 | Desktop chats, slides, e-mail; has a QR code to the Play page |
| D square | 1080 × 1080 | Group chats, Mastodon |
| E mandala portrait | 1080 × 1350 | A calmer one: a large mandala with a short line, no screenshot |
| F wide | 1920 × 1080 | Landscape with one or two phones and larger text, readable on small screens |
| G teaser story | 1080 × 1920 | The mandala alone, no app, no title; each mandala with the first `teaser` line, the second, and without text. Post two or three first, then H and B |
| H wallpaper story | 1080 × 1920 | The phone with a mandala as lock screen over a mandala background, one quiet line (`wallpaper` in the text set) |

The images print the short address `tearoom.one/dahlia`, a redirect in the website repository (`site/config/routes.php`, every app and plugin page has one). Texts live in `texts/`; the screenshots come from `../store/screenshots/`, the icon from `../store/play-icon-512.png`.
