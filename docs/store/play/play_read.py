import sys, json; sys.path.insert(0, sys.argv[1]); from play_api import *
eid=call("POST","/edits",{})["id"]
try:
    for l in call("GET",f"/edits/{eid}/listings")["listings"]:
        print("==", l["language"], "| short:", l.get("shortDescription")); print(l.get("fullDescription")); print()
finally:
    call("DELETE",f"/edits/{eid}",raw=True)
p=call("GET","/oneTimeProducts/pro"); print("== PRODUCT"); print(json.dumps(p.get("listings"),indent=1,ensure_ascii=False))
