"""Package compiled DEX for the SDK v1 manager. The compiler APK is never installed."""
import argparse, hashlib, io, json, re, zipfile
from pathlib import Path

root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,required=True)
out=parser.parse_args().output;out.mkdir(parents=True,exist_ok=True)
manifest=json.loads((root/'plugin.json').read_text(encoding='utf-8'))
apk=root/'build/outputs/apk/release/vivo-plugin-release-unsigned.apk'
code=io.BytesIO()
with zipfile.ZipFile(apk) as compiled,zipfile.ZipFile(code,'w',zipfile.ZIP_DEFLATED) as jar:
    dex=sorted(n for n in compiled.namelist() if re.fullmatch(r'classes(?:\d+)?\.dex',n))
    assert dex
    for name in dex:
        payload=compiled.read(name);assert payload.startswith(b'dex\n')
        assert b'Ldev/amenhancer/plugin/api/PluginContext;' in payload  # referenced, not defined (PluginStore checks definitions)
        jar.writestr(name,payload)
target=out/f'Tcrrry-vivo-Media-{manifest["versionName"]}.zip'
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as archive:
    archive.write(root/'plugin.json','plugin.json');archive.writestr('code.jar',code.getvalue())
    archive.write(root/'README.md','assets/README.md')
print(json.dumps({'plugin':str(target),'bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest()},ensure_ascii=False))
