# Marketing material

Images to share with friends, on WhatsApp and elsewhere (MKT-1 names this folder for the Reddit assets as well).

- `MKT-2-share-images.html`: the share images as one HTML template with a switcher (variant, image set, language). Opened without parameters it is the viewer; with `?v=<variant>&s=<set>&lang=<en|de>` it shows one card at its pixel size. Each variant has several image sets (`SETS` in the HTML): different screenshots and a different background mandala.
- `texts/<name>.js`: one text set per audience (`friends` is the first). The `message`, `facts`, `link` and `mandala` lines and the `teaser` lines in English and German.
- `render-share-images.sh [name]`: renders every variant and set in English and German to `share/<name>/<variant>-<set>-<lang>.jpg` with headless Google Chrome and ImageMagick (default `friends`). Needs the Inter font installed; the landscape QR code loads a small library from cdnjs, so render it online.
- `share/<name>/`: the rendered images of one text set, ready to send; kept in git.
- `renders/`: large mandalas rendered from the starter favourites (`android/app/src/main/assets/starter-favorites.json`, the `parameters` of one entry saved as a settings file) with the CLI, used as backgrounds: portrait `mandala render <settings> <out.png> --width 1080 --height 1920 --zoom 1.15 --centre-y 0.40`, landscape `--width 1920 --height 1080 --zoom 0.52 --centre-x 0.72`, square `--size 1400`, teaser stories `--width 1080 --height 1920` with the centre at 0.5; saved as JPEG quality 92. Favourites with an image or gradient background show hard edges on non-square canvases; the hypocycloid, Flower of Life and ribbon ones work.

## New text, same images

1. Copy `texts/friends.js` to `texts/<name>.js` and change the lines.
2. Run `docs/marketing/render-share-images.sh <name>` (about three minutes).
3. The images are in `share/<name>/`. The viewer shows a text set by its name in the "Text set" field.

New screenshots or backgrounds: change `SETS` in the HTML (and the set counts in the script), new screenshots go to `../store/screenshots/`.

| Variant | Size | For |
|---|---|---|
| A portrait | 1080 × 1350 | WhatsApp chats, Instagram feed |
| B story | 1080 × 1920 | WhatsApp status, Instagram story |
| C landscape | 1920 × 1080 | Desktop chats, slides, e-mail; has a QR code to the Play page |
| D square | 1080 × 1080 | Group chats, Mastodon |
| E mandala portrait | 1080 × 1350 | A calmer one: a large mandala with a short line, no screenshot |
| F wide | 1920 × 1080 | Landscape with one or two phones and larger text, readable on small screens |
| G teaser story | 1080 × 1920 | The mandala alone with one quiet line (`teaser` in the text set), no app, no title: post two or three of these first, then B |

The images print the short address `tearoom.one/dahlia`, a redirect in the website repository (`site/config/routes.php`, every app and plugin page has one). Texts live in `texts/`; the screenshots come from `../store/screenshots/`, the icon from `../store/play-icon-512.png`.
