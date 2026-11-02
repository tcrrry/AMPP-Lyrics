"""Add the optional external-media proxy to already verified v1.11 installation forms."""
import argparse, hashlib, io, json, re, subprocess, tempfile, zipfile
from pathlib import Path

p=argparse.ArgumentParser(description=__doc__)
for name in ("input", "output", "ordinary-module", "coexist-module", "tools", "editor", "signer", "npatch", "signer-properties", "shim", "zipalign", "apksigner", "aapt2", "java"):
    p.add_argument("--"+name, type=Path, required=True)
a=p.parse_args()
if a.output.exists() and any(a.output.iterdir()): p.error("Refusing to overwrite existing output")
a.output.mkdir(parents=True, exist_ok=True)
def run(*args): return subprocess.check_output([str(x) for x in args], text=True)
def call(*args): subprocess.run([str(x) for x in args], check=True)
def digest(path):
    with path.open("rb") as f: return hashlib.file_digest(f,"sha256").hexdigest()
def nested(path,name):
    with zipfile.ZipFile(path) as z:
        i=z.getinfo(name);i._end_offset=None
        return z.read(i)
def patch(source,module,dest,work,coexist=False):
    work.mkdir(); manifest=work/"manifest.bin"
    call(a.java,"-cp",str(a.tools)+__import__("os").pathsep+str(a.editor),"ExternalMediaManifest",source,work/"scratch.apk",manifest)
    call(a.java,"-cp",a.tools,"ReembedExternalMedia",source,module,a.shim,manifest,work/"rewrite",work/"unsigned.apk",a.zipalign)
    call(a.zipalign,"-P","16","-f","4",work/"unsigned.apk",work/"aligned.apk")
    cp=__import__("os").pathsep.join(map(str,[a.tools,a.signer,a.npatch]))
    call(a.java,"-cp",cp,"SignWithPublicNPatchIdentity",a.signer_properties,work/"aligned.apk",dest)
    call(a.zipalign,"-c","-P","16","4",dest)
    cert=run(a.apksigner,"verify","--min-sdk-version","30","--print-certs",dest)
    expected="08b00b38cd98762bf261952c2c1014c09208ac2c278fd3085421994a516c3e23"
    if expected not in cert: raise RuntimeError("Unexpected host certificate")
    package="com.tcrrry.ampplyrics.coexist" if coexist else "com.apple.android.music"
    badging=run(a.aapt2,"dump","badging",dest)
    if f"name='{package}' versionCode='1614' versionName='7.0.0'" not in badging: raise RuntimeError("Unexpected host identity")
    manifest_text=run(a.aapt2,"dump","xmltree",dest,"--file","AndroidManifest.xml")
    for required in ("dev.amenhancer.hostproxy.ExternalMediaService","com.vivo.musicwidgetmix.support.service","ampp.external.media.bootstrap_root"):
        if required not in manifest_text:raise RuntimeError("Missing external media manifest entry: "+required)
    with zipfile.ZipFile(dest) as z:
        mods=[x for x in z.namelist() if x.startswith("assets/npatch/modules/") and x.endswith(".apk")]
        if len(mods)!=1 or nested(dest,mods[0])!=module.read_bytes():raise RuntimeError("Wrong embedded module")
        newdex=[n for n in z.namelist() if re.fullmatch(r"classes\d*\.dex",n) and z.read(n)==a.shim.read_bytes()]
        if len(newdex)!=1:raise RuntimeError("Missing or duplicated external proxy DEX")
    origin=nested(dest,"assets/npatch/origin.apk");originfile=work/"origin-final.apk";originfile.write_bytes(origin)
    with zipfile.ZipFile(io.BytesIO(origin)) as z:
        if sum(1 for n in z.namelist() if re.fullmatch(r"classes\d*\.dex",n) and z.read(n)==a.shim.read_bytes())!=1:raise RuntimeError("Missing proxy in embedded origin")
    if coexist:
        cp=str(a.tools)+__import__("os").pathsep+str(a.editor)
        for item in (dest,originfile):call(a.java,"-cp",cp,"VivoAtomicManifest","--verify",item)
        call(a.java,"-cp",cp,"VerifyCoexistenceLayouts",dest,originfile)
ordinary="AMPP-Lyrics-AppleMusic-7.0.0-arm64.apk";coexist="AMPP-Lyrics-AppleMusic-7.0.0-arm64-coexist.apk";splits="AMPP-Lyrics-AppleMusic-7.0.0-arm64.apks"
with tempfile.TemporaryDirectory(prefix="ampp-vivo-final-") as folder:
    work=Path(folder)
    patch(a.input/ordinary,a.ordinary_module,a.output/ordinary,work/"ordinary")
    patch(a.input/coexist,a.coexist_module,a.output/coexist,work/"coexist",True)
    with zipfile.ZipFile(a.input/splits) as z:
        base=work/"split-base.apk";base.write_bytes(z.read("base.apk"))
        rest={n:z.read(n) for n in z.namelist() if n!="base.apk"}
    patched=work/"split-patched.apk";patch(base,a.ordinary_module,patched,work/"splits")
    with zipfile.ZipFile(a.output/splits,"x",compression=zipfile.ZIP_STORED) as z:
        z.write(patched,"base.apk")
        for n,data in rest.items():z.writestr(n,data)
    with zipfile.ZipFile(a.output/splits) as z:
        if z.testzip() is not None or any(z.read(n)!=data for n,data in rest.items()):raise RuntimeError("Split archive check failed")
    (a.output/"AMPP-Lyrics-module.apk").write_bytes(a.ordinary_module.read_bytes())
files={x.name:{"sha256":digest(x),"bytes":x.stat().st_size} for x in a.output.iterdir() if x.suffix in (".apk",".apks")}
if len(files)!=4:raise RuntimeError("Expected four artifacts")
(a.output/"SHA256SUMS.txt").write_text("".join(info["sha256"]+"  "+name+"\n" for name,info in sorted(files.items())),encoding="utf8")
(a.output/"v1.11-validation.json").write_text(json.dumps({"release":"v1.11", "revision":"stability-vivo", "host":"7.0.0/1614", "deviceVerified":False, "vivoOriginalServiceAndProxyVerified":True, "files":files},indent=2)+"\n",encoding="utf8")
print("PASS: four installation forms, host signatures, embedded modules, proxy and coexist gates")
