# Marketing material

Images to share with friends, on WhatsApp and elsewhere (MKT-1 names this folder for the Reddit assets as well).

- `MKT-2-share-images.html`: the share images as one HTML template with a switcher (variant, language). Opened without parameters it is the viewer; with `?v=<variant>&lang=<en|de>` it shows one card at its pixel size.
- `render-share-images.sh`: renders every variant in English and German to `share/<variant>-<lang>.png` with headless Google Chrome. Needs the Inter font installed; the landscape QR code loads a small library from cdnjs, so render it online.
- `renders/`: large mandalas rendered from the starter favourites with the CLI (`android/cli/build/install/mandala/bin/mandala render`), used as backgrounds.
- `share/`: the rendered images, ready to send.

| Variant | Size | For |
|---|---|---|
| A portrait | 1080 × 1350 | WhatsApp chats, Instagram feed |
| B story | 1080 × 1920 | WhatsApp status, Instagram story |
| C landscape | 1920 × 1080 | Desktop chats, slides, e-mail; has a QR code to the Play page |
| D square | 1080 × 1080 | Group chats, Mastodon |
| E emblem portrait | 1080 × 1350 | A calmer one: the Om mandala with a short line |

Texts live in the `TEXT` object of the HTML; the screenshots come from `../store/screenshots/`, the icon from `../store/play-icon-512.png`.
