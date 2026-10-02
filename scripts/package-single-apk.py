"""Merge original v1.1 splits, then embed the unchanged module with NPatch."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile


TOOLS = {
    "editor": "a9cd40df818845456be6d696de6110c89edf4b0a0580cb83438ed6b25a366e67",
    "npatch": "855c6499ac871663ae6c02dcb3a32cbbdfa88d67a312094249cc54e7f3ec8252",
}
SPLITS = ("base.apk", "split_config.arm64_v8a.apk", "split_config.xxxhdpi.apk")
MODULE = "assets/npatch/modules/dev.amenhancer.module.debug.apk"


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def extract_nested(apk, entry, target):
    # NPatch deliberately overlaps outer ZIP entries with its embedded original.
    # Python zipfile rejects these entries; unzip understands this layout.
    with target.open("wb") as stream:
        subprocess.run(["unzip", "-p", str(apk), entry], stdout=stream, check=True)


def certificate(apksigner, apk):
    output = subprocess.check_output(
        [str(apksigner), "verify", "--print-certs", str(apk)], text=True
    )
    return re.search(r"certificate SHA-256 digest: ([0-9a-f]{64})", output).group(1)


def resources(aapt2, apk):
    with zipfile.ZipFile(apk) as archive:
        if "resources.arsc" not in archive.namelist():
            return set()
    output = subprocess.check_output([str(aapt2), "dump", "resources", str(apk)], text=True)
    return set(re.findall(r"resource (0x[0-9a-f]+) ([^\s]+)", output))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apks", type=Path)
    parser.add_argument("output", type=Path)
    for name in (*TOOLS, "apksigner", "aapt2"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    for name, expected in TOOLS.items():
        if digest(getattr(args, name)) != expected:
            parser.error(f"Unexpected {name} tool checksum")
    if args.output.exists():
        parser.error("Output already exists")

    with tempfile.TemporaryDirectory(prefix="ampp-single-") as directory:
        work = Path(directory)
        patched, original = work / "patched", work / "original"
        patched.mkdir()
        original.mkdir()
        with zipfile.ZipFile(args.apks) as archive:
            if set(archive.namelist()) != set(SPLITS) or archive.testzip() is not None:
                parser.error("Expected the complete arm64/xxxhdpi APKS")
            for name in SPLITS:
                (patched / name).write_bytes(archive.read(name))
        for name in SPLITS:
            extract_nested(patched / name, "assets/npatch/origin.apk", original / name)
        module = work / "module.apk"
        extract_nested(patched / "base.apk", MODULE, module)
        old_config = json.loads(subprocess.check_output(
            ["unzip", "-p", str(patched / "base.apk"), "assets/npatch/config.json"]
        ))
        merged = work / "merged.apk"
        subprocess.run(["java", "-jar", str(args.editor), "m", "-i", str(original),
                        "-o", str(merged), "-validate-modules"], check=True)
        expected_resources = set()
        for name in SPLITS:
            expected_resources.update(resources(args.aapt2, original / name))
        # APKEditor removes the obsolete Play split-list resource in a standalone APK.
        expected_resources = {item for item in expected_resources if item[1] != "xml/splits0"}
        if not expected_resources <= resources(args.aapt2, merged):
            raise RuntimeError("Merged APK lost resource IDs or names")
        with zipfile.ZipFile(merged) as output, zipfile.ZipFile(original / "base.apk") as base:
            for name in base.namelist():
                if re.fullmatch(r"classes\d*\.dex", name) and output.read(name) != base.read(name):
                    raise RuntimeError(f"Host DEX changed: {name}")
        # Register the provider bundled in NPatch for its BKS test keystore.
        security = work / "bc.security"
        security.write_text("security.provider.13=org.bouncycastle.jce.provider.BouncyCastleProvider\n")
        subprocess.run(["java", f"-Djava.security.properties={security}", "-jar",
                        str(args.npatch), str(merged), "-m", str(module),
                        "-o", str(work / "single")], check=True)
        result = work / "single" / "merged-741-npatched.apk"
        if certificate(args.apksigner, result) != certificate(args.apksigner, patched / "base.apk"):
            raise RuntimeError("Signature differs from existing APKS")
        new_config = json.loads(subprocess.check_output(
            ["unzip", "-p", str(result), "assets/npatch/config.json"]
        ))
        if new_config != old_config:
            raise RuntimeError("NPatch configuration differs from existing APKS")
        embedded = work / "embedded.apk"
        extract_nested(result, MODULE, embedded)
        if digest(embedded) != digest(module):
            raise RuntimeError("Embedded module changed")
        badging = subprocess.check_output([str(args.aapt2), "dump", "badging", str(result)], text=True)
        if "name='com.apple.android.music' versionCode='1599' versionName='6.5.3'" not in badging:
            raise RuntimeError("Unexpected host identity")
        manifest = subprocess.check_output([str(args.aapt2), "dump", "xmltree", str(result),
                                            "--file", "AndroidManifest.xml"], text=True)
        if 'isSplitRequired=false' not in manifest or 'isSplitRequired=true' in manifest:
            raise RuntimeError("Host still requires splits")
        with zipfile.ZipFile(result) as output, zipfile.ZipFile(original / SPLITS[1]) as native:
            for name in native.namelist():
                if name.startswith("lib/") and not name.endswith("/"):
                    if output.read(name) != native.read(name):
                        raise RuntimeError(f"Native library changed: {name}")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(result, args.output)
    checksum = digest(args.output)
    Path(str(args.output) + ".sha256").write_text(f"{checksum}  {args.output.name}\n")
    print(f"Verified single APK: {args.output}\nSHA-256: {checksum}")
    print("Static validation only; installation, login and playback require a real device.")


if __name__ == "__main__":
    main()
