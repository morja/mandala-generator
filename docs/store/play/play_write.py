import sys, json; sys.path.insert(0, sys.argv[1]); from play_api import *
REPL = {"Try every Pro feature before you buy.": "Try more layers, emblems and quotes in the editor before you buy.",
        "Alles aus Pro kannst du vor dem Kauf ausprobieren.": "Mehr Ebenen, Embleme und Zitate kannst du im Editor vor dem Kauf ausprobieren."}
def fix(s):
    for a,b in REPL.items(): s=s.replace(a,b)
    return s
eid=call("POST","/edits",{})["id"]
try:
    for l in call("GET",f"/edits/{eid}/listings")["listings"]:
        new=fix(l["fullDescription"])
        if new!=l["fullDescription"]:
            l["fullDescription"]=new
            call("PUT",f"/edits/{eid}/listings/{l['language']}",l); print("listing updated:",l["language"],len(new))
    for t in call("GET",f"/edits/{eid}/tracks")["tracks"]:
        if "releases" not in t: continue
        changed=False
        for r in t["releases"]:
            for n in r.get("releaseNotes",[]):
                new=fix(n["text"])
                if new!=n["text"]: n["text"]=new; changed=True; print("notes:",t["track"],n["language"],len(new),"chars")
        if changed: call("PUT",f"/edits/{eid}/tracks/{t['track']}",t); print("track updated:",t["track"])
    print("validate:", call("POST",f"/edits/{eid}:validate",{}))
    print("commit:", call("POST",f"/edits/{eid}:commit",{}))
except Exception as e:
    print("failed, deleting edit:",e); call("DELETE",f"/edits/{eid}",raw=True); raise
