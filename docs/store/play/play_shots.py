import sys, json, os, urllib.request; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__))); from play_api import *
S=sys.argv[1]; UP=f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PKG}"
def upload(eid, lang, path):
    r=urllib.request.Request(f"{UP}/edits/{eid}/listings/{lang}/phoneScreenshots?uploadType=media",method="POST",data=open(path,"rb").read(),headers={"Authorization":"Bearer "+TOK,"Content-Type":"image/png"})
    return json.load(urllib.request.urlopen(r))
eid=call("POST","/edits",{})["id"]
try:
    for lang in sorted(os.listdir(f"{S}/shots")):
        files=sorted(os.listdir(f"{S}/shots/{lang}"))
        assert len(files)==7, (lang, files)
        call("DELETE",f"/edits/{eid}/listings/{lang}/phoneScreenshots",raw=True)
        for f in files: upload(eid, lang, f"{S}/shots/{lang}/{f}")
        print(lang, "uploaded", len(files))
    call("POST",f"/edits/{eid}:validate",{}); print("commit:", call("POST",f"/edits/{eid}:commit",{}))
except Exception as e:
    print("failed, deleting edit:", e); call("DELETE",f"/edits/{eid}",raw=True); raise
