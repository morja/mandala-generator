"""Upload an app bundle and release it on a track with release notes.

    python3 -I docs/store/play/play_release.py docs/store/play <track> <aab> <notes-en.txt> <notes-de.txt>

Languages of the notes follow the track's current release (en-US and de-DE when it has none). The
release is a full rollout (status completed), as the first public release was.
"""
import sys, json, urllib.request; sys.path.insert(0, sys.argv[1]); from play_api import *
track, aab, en, de = sys.argv[2:6]
notes = {"en-US": open(en, encoding="utf-8").read().strip(), "de-DE": open(de, encoding="utf-8").read().strip()}
eid = call("POST", "/edits", {})["id"]
try:
    current = call("GET", f"/edits/{eid}/tracks/{track}")
    print("current:", json.dumps([{k: r.get(k) for k in ("name", "versionCodes", "status")} for r in current.get("releases", [])]))
    langs = [n["language"] for r in current.get("releases", []) for n in r.get("releaseNotes", [])]
    langs = {l for l in langs if l.startswith("en") or l.startswith("de")}
    for l in langs:
        notes[l] = notes["en-US"] if l.startswith("en") else notes["de-DE"]
    url = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PKG}/edits/{eid}/bundles?uploadType=media"
    r = urllib.request.Request(url, method="POST", data=open(aab, "rb").read(), headers={"Authorization": "Bearer " + TOK, "Content-Type": "application/octet-stream"})
    bundle = json.loads(urllib.request.urlopen(r).read())
    print("uploaded:", bundle)
    code = str(bundle["versionCode"])
    body = {"releases": [{"versionCodes": [code], "status": "completed",
                          "releaseNotes": [{"language": l, "text": t} for l, t in notes.items() if l in langs or l in ("en-US", "de-DE") and not langs]}]}
    print("track:", json.dumps(call("PUT", f"/edits/{eid}/tracks/{track}", body)))
    print("validate:", call("POST", f"/edits/{eid}:validate", {}))
    print("commit:", call("POST", f"/edits/{eid}:commit", {}))
except Exception as e:
    print("failed, deleting edit:", e); call("DELETE", f"/edits/{eid}", raw=True); raise
