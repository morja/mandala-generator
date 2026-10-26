# Play Console tools

## Release order

Before a Play release, merge the completed changes into `main`, verify the integrated source with the required checks and push `main`. Build the signed bundle from that verified `main` commit, then create its source tag and upload the bundle. Do not release an unmerged feature branch. An explicit release request authorizes the prerequisite merge and push; it does not permit skipping checks. This order was requested by Mathis on 26 October 2026 and also applies to macOS releases (see `AGENTS.md`).

## Tools

Small scripts for the Google Play Developer API, used for the listing of `one.tearoom.mandala`. They need the service account key at `~/.keystore/google-keys.json` and Python 3 without extra packages (the JWT is signed with `openssl`). Run them with the folder as the first argument, from anywhere:

```
python3 -I docs/store/play/play_read.py docs/store/play      # print listings and the Pro product
python3 -I docs/store/play/play_listings.py docs/store/play  # write the listing texts from listings.py
python3 -I docs/store/play/render_shots.py <out>             # render captioned screenshots from captions.json (Java 21, fonts Inter, PingFang SC, Hiragino Sans)
python3 -I docs/store/play/play_shots.py <out>               # replace the phone screenshots of every language in <out>/shots
python3 -I docs/store/play/play_release.py docs/store/play <track> <aab> <notes-en.txt> <notes-de.txt>  # upload a bundle and release it with notes (full rollout)
```

`render_shots.py` and `play_shots.py` read `captions.json` and `Caption.java` next to them when the first argument is this folder; `render_shots.py` writes to `<out>/shots/<language>/`. `play_write.py` and `play_details.py` were one-off edits (23 October 2026) kept as examples. The texts are the source of truth in `listings.py` and `captions.json`; STORE-1 holds the English and German listing as prose.
