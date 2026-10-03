"""Build an isolated coexist test APK without replacing regular release assets."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import runpy
import shutil
import subprocess
import tempfile
import zipfile

BASE = runpy.run_path(str(Path(__file__).with_name("package-single-apk.py")))
TARGET = "com.tcrrry.ampplyrics.coexist"
ORIGINAL = "com.apple.android.music"
MODULE_PACKAGE = "dev.amenhancer.module.coexist"


def output(command):
    return subprocess.check_output([str(value) for value in command], text=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apks", type=Path)
    parser.add_argument("module", type=Path)
    parser.add_argument("output", type=Path)
    for name in (*BASE["TOOLS"], "apksigner", "aapt2"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Output already exists")
    for name, checksum in BASE["TOOLS"].items():
        if BASE["digest"](getattr(args, name)) != checksum:
            parser.error(f"Unexpected {name} checksum")
    module_badging = output([args.aapt2, "dump", "badging", args.module])
    if f"name='{MODULE_PACKAGE}'" not in module_badging:
        parser.error("Build :app:assembleDebug -PamppCoexistence=true; do not use the standard module")
    with tempfile.TemporaryDirectory(prefix="ampp-coexist-") as directory:
        work = Path(directory)
        patched, original = work / "patched", work / "original"
        patched.mkdir()
        original.mkdir()
        with zipfile.ZipFile(args.apks) as archive:
            if set(archive.namelist()) != set(BASE["SPLITS"]) or archive.testzip() is not None:
                parser.error("Invalid arm64/xxxhdpi APKS")
            for name in BASE["SPLITS"]:
                (patched / name).write_bytes(archive.read(name))
                BASE["extract_nested"](patched / name, "assets/npatch/origin.apk", original / name)
        merged, isolated = work / "merged.apk", work / "isolated.apk"
        subprocess.run(["java", "-jar", str(args.editor), "m", "-i", str(original),
                        "-o", str(merged), "-validate-modules"], check=True)
        subprocess.run(["java", "-cp", str(args.editor),
                        str(Path(__file__).with_name("CoexistenceManifest.java")),
                        str(merged), str(isolated)], check=True)
        old_config = json.loads(output(["unzip", "-p", patched / "base.apk", "assets/npatch/config.json"]))
        security = work / "bc.security"
        security.write_text("security.provider.13=org.bouncycastle.jce.provider.BouncyCastleProvider\n")
        subprocess.run(["java", f"-Djava.security.properties={security}", "-jar", str(args.npatch),
                        str(isolated), "-m", str(args.module), "-o", str(work / "single")], check=True)
        result = work / "single" / "isolated-741-npatched.apk"
        if BASE["certificate"](args.apksigner, result) != BASE["certificate"](args.apksigner, patched / "base.apk"):
            raise RuntimeError("Unexpected test signer")
        config = json.loads(output(["unzip", "-p", result, "assets/npatch/config.json"]))
        if config.get("newPackage") != TARGET or config.get("originalSignature") != old_config["originalSignature"]:
            raise RuntimeError("Incorrect host identity / original certificate")
        manifest = output([args.aapt2, "dump", "xmltree", result, "--file", "AndroidManifest.xml"])
        if "sharedUserId" in manifest or "BROWSABLE" in manifest or "isSplitRequired=true" in manifest:
            raise RuntimeError("Shared UID, external links or split dependency remain")
        badging = output([args.aapt2, "dump", "badging", result])
        if f"name='{TARGET}' versionCode='1599' versionName='6.5.3'" not in badging:
            raise RuntimeError("Unexpected APK identity")
        if "application-label:'AM++ Lyrics 共存测试版'" not in badging:
            raise RuntimeError("Launcher name is not distinguishable")
        if re.search(r'authorities[^\n]*="com\.apple\.android\.music', manifest):
            raise RuntimeError("Original Provider identity remains")
        expected = set()
        for name in BASE["SPLITS"]:
            expected.update(BASE["resources"](args.aapt2, original / name))
        expected = {item for item in expected if item[1] != "xml/splits0"}
        if not expected <= BASE["resources"](args.aapt2, result):
            raise RuntimeError("Host resource IDs / names changed")
        cached = work / "cached.apk"
        BASE["extract_nested"](result, "assets/npatch/origin.apk", cached)
        with zipfile.ZipFile(cached) as cache, zipfile.ZipFile(original / "base.apk") as base:
            for name in base.namelist():
                if re.fullmatch(r"classes\d*\.dex", name) and cache.read(name) != base.read(name):
                    raise RuntimeError(f"Original DEX changed: {name}")
        with zipfile.ZipFile(result) as apk, zipfile.ZipFile(original / BASE["SPLITS"][1]) as native:
            for name in native.namelist():
                if name.startswith("lib/") and not name.endswith("/") and apk.read(name) != native.read(name):
                    raise RuntimeError(f"Native library changed: {name}")
        embedded = work / "module.apk"
        BASE["extract_nested"](result, f"assets/npatch/modules/{MODULE_PACKAGE}.apk", embedded)
        if BASE["digest"](embedded) != BASE["digest"](args.module):
            raise RuntimeError("Coexist module differs from build input")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(result, args.output)
    checksum = BASE["digest"](args.output)
    Path(str(args.output) + ".sha256").write_text(f"{checksum}  {args.output.name}\n")
    print(f"Coexist APK statically verified: {args.output}\nSHA-256: {checksum}")
    print("Installation, login, DRM, offline playback and lyrics require real-device verification.")


if __name__ == "__main__":
    main()
