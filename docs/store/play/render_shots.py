import json, subprocess, sys, os
S=sys.argv[1]; SRC=os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "screenshots")
files=["phone-1-layers.png","phone-2-om-emblem.png","phone-3-wallpaper.png","phone-4-quote.png","phone-5-favourites.png","phone-6-save-formats.png","phone-7-new-mandala-preview.png"]
HERE=os.path.dirname(os.path.abspath(__file__)); caps=json.load(open(f"{HERE}/captions.json")); env=dict(os.environ, JAVA_TOOL_OPTIONS="-Djava.awt.headless=true")
for lang,d in caps.items():
    os.makedirs(f"{S}/shots/{lang}",exist_ok=True)
    for i,(t,s) in enumerate(d["c"]):
        out=f"{S}/shots/{lang}/{i+1}-{files[i][8:]}"
        r=subprocess.run(["java",f"{HERE}/Caption.java",out,f"{SRC}/{files[i]}",d["tf"],d["sf"],t,s],capture_output=True,text=True,env=env)
        if r.returncode: print(lang,i+1,r.stderr[-400:]); sys.exit(1)
    print(lang,"ok")
