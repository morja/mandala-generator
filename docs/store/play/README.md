# Play Console tools

## Release order

Before a Play release, merge the completed changes into `main`, verify the integrated source with the required checks and push `main`. Build the signed bundle from that verified `main` commit, then create its source tag and upload the bundle. Do not release an unmerged feature branch. An explicit release request authorizes the prerequisite merge and push; it does not permit skipping checks. This order was requested by Mathis on 26 October 2026 and also applies to macOS releases (see `AGENTS.md`).

## Tools (since 27 October 2026)

The Python scripts that lived here became the shared Cockpit commands (`~/Work/Cockpit/bin/android/play`, `store-shots`; docs in `~/Work/Cockpit/docs/android-scripts.md`). The listing texts are no longer in `listings.py` but in `store/android/<lang>/` (title, short and full description, `changelogs/<versionCode>.txt`, `images/`), pulled from the store on 27 October 2026 (29 languages).

```
bin/release status                 # releases per track
bin/release read | pull            # print the listings, or write them into store/android/
bin/release listing [--dry-run]    # upload texts, icon, feature graphic and phone screenshots
bin/release internal               # build the bundle and upload it to internal testing with the release notes
bin/release production [CODE] [--rollout N]   # the code on internal (or CODE) to production, with Mathis' OK
bin/store-shots [--lang L]         # captioned screenshots from store/android/captions.json and store/android/screenshots/
```

Production: `bin/release production` (the code on internal with the same notes, full rollout, or `--rollout N` percent) on Mathis' OK; the Play Console only for what the API does not do. The service account key is `~/.keystore/google-keys.json`. The old `release-notes-<version>-<lang>.txt` files here are the record of earlier releases; new notes go to `store/android/<lang>/changelogs/<code>.txt` (1.7.1 is code 14).
