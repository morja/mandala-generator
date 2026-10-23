import json, time, base64, subprocess, urllib.request, urllib.parse, tempfile, os, sys
key = json.load(open(os.path.expanduser("~/.keystore/google-keys.json")))
b64 = lambda b: base64.urlsafe_b64encode(b).rstrip(b"=").decode()
now = int(time.time())
hdr = b64(json.dumps({"alg":"RS256","typ":"JWT"}).encode())
claim = b64(json.dumps({"iss":key["client_email"],"scope":"https://www.googleapis.com/auth/androidpublisher","aud":key["token_uri"],"iat":now,"exp":now+600}).encode())
with tempfile.NamedTemporaryFile("w",suffix=".pem",delete=False) as f: f.write(key["private_key"]); pem=f.name
sig = subprocess.run(["openssl","dgst","-sha256","-sign",pem],input=f"{hdr}.{claim}".encode(),capture_output=True,check=True).stdout
os.unlink(pem)
jwt = f"{hdr}.{claim}.{b64(sig)}"
data = urllib.parse.urlencode({"grant_type":"urn:ietf:params:oauth:grant-type:jwt-bearer","assertion":jwt}).encode()
TOK = json.load(urllib.request.urlopen(urllib.request.Request(key["token_uri"],data=data)))["access_token"]
PKG="one.tearoom.mandala"; BASE=f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PKG}"
def call(m,path,body=None,raw=False):
    r=urllib.request.Request(BASE+path,method=m,data=json.dumps(body).encode() if body is not None else None,headers={"Authorization":"Bearer "+TOK,"Content-Type":"application/json"})
    try:
        resp=urllib.request.urlopen(r); t=resp.read()
        return t.decode() if raw or not t else json.loads(t)
    except urllib.error.HTTPError as e:
        print("HTTP",e.code,e.read().decode()[:800],file=sys.stderr); raise
