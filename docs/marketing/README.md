# Marketing material

Images to share with friends, on WhatsApp and elsewhere (MKT-1 names this folder for the Reddit assets as well).

- `MKT-2-share-images.html`: the share images as one HTML template with a switcher (variant, image set, language). Opened without parameters it is the viewer; with `?v=<variant>&s=<set>&lang=<en|de>` it shows one card at its pixel size. Each variant has several image sets (`SETS` in the HTML): different screenshots and a different background mandala.
- `texts/<name>.js`: one text set per audience (`friends` is the first). The `message`, `facts`, `link` and `mandala` lines in English and German.
- `render-share-images.sh [name]`: renders every variant and set in English and German to `share/<name>/<variant>-<set>-<lang>.jpg` with headless Google Chrome and ImageMagick (default `friends`). Needs the Inter font installed; the landscape QR code loads a small library from cdnjs, so render it online.
- `share/<name>/`: the rendered images of one text set, ready to send; kept in git.

New text: copy `texts/friends.js` to `texts/<name>.js`, change the lines, run `docs/marketing/render-share-images.sh <name>`. The viewer shows a text set by its name in the "Text set" field.
- `renders/`: large mandalas rendered from the starter favourites with the CLI (`android/cli/build/install/mandala/bin/mandala render`), used as backgrounds.

| Variant | Size | For |
|---|---|---|
| A portrait | 1080 × 1350 | WhatsApp chats, Instagram feed |
| B story | 1080 × 1920 | WhatsApp status, Instagram story |
| C landscape | 1920 × 1080 | Desktop chats, slides, e-mail; has a QR code to the Play page |
| D square | 1080 × 1080 | Group chats, Mastodon |
| E mandala portrait | 1080 × 1350 | A calmer one: a large mandala with a short line, no screenshot |
| F wide | 1920 × 1080 | Landscape with one or two phones and larger text, readable on small screens |

The images print the short address `tearoom.one/dahlia`, a redirect in the website repository (`site/config/routes.php`, every app and plugin page has one). Texts live in `texts/`; the screenshots come from `../store/screenshots/`, the icon from `../store/play-icon-512.png`.
