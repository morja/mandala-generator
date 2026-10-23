import sys, json; sys.path.insert(0, sys.argv[1]); from play_api import *; from listings import *
for lang, d in NEW.items():
    assert len(d["title"])<=30 and len(d["short"])<=80 and len(d["full"])<=4000, (lang, len(d["title"]), len(d["short"]), len(d["full"]))
for lang, s in SHORT.items(): assert len(s)<=80, (lang, len(s))
eid=call("POST","/edits",{})["id"]
try:
    for l in call("GET",f"/edits/{eid}/listings")["listings"]:
        lang=l["language"]
        if lang in SHORT:
            old,new=FIRST_LINE[lang]; assert old in l["fullDescription"], lang
            l["shortDescription"]=SHORT[lang]; l["fullDescription"]=l["fullDescription"].replace(old,new)
            call("PUT",f"/edits/{eid}/listings/{lang}",l); print("updated",lang)
    for lang,d in NEW.items():
        call("PUT",f"/edits/{eid}/listings/{lang}",{"language":lang,"title":d["title"],"shortDescription":d["short"],"fullDescription":d["full"]}); print("created",lang)
    call("POST",f"/edits/{eid}:validate",{}); print("commit:",call("POST",f"/edits/{eid}:commit",{}))
except Exception as e:
    print("failed, deleting edit:",e); call("DELETE",f"/edits/{eid}",raw=True); raise
