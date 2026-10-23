import sys, json; sys.path.insert(0, sys.argv[1]); from play_api import *
eid=call("POST","/edits",{})["id"]
try:
    d=call("GET",f"/edits/{eid}/details"); print("before:",d)
    d["contactWebsite"]="https://www.tearoom.one/en/apps/dahlia"
    print("after:",call("PUT",f"/edits/{eid}/details",d))
    call("POST",f"/edits/{eid}:validate",{}); print("commit:",call("POST",f"/edits/{eid}:commit",{}))
except Exception as e:
    print("failed, deleting edit:",e); call("DELETE",f"/edits/{eid}",raw=True); raise
