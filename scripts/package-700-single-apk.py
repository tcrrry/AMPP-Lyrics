"""Build only the ordinary 7.0.0-beta/1606 arm64 single APK from pinned inputs."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

from importlib.util import spec_from_file_location, module_from_spec
_spec = spec_from_file_location('single', Path(__file__).with_name('package-single-apk.py'))
single = module_from_spec(_spec)
_spec.loader.exec_module(single)

INPUT_SHA256 = '1adae4761b292221189bd47f2f302fe48c7bb45863b2581663ed5efba9146093'
BASE_SHA256 = '3d09687ed752e48e73f2c72524e18cffff69c66b523096c2e97c8f9135980603'
CERTIFICATE_SHA256 = '08b00b38cd98762bf261952c2c1014c09208ac2c278fd3085421994a516c3e23'
SPLITS = ('base.apk', 'split_config.arm64_v8a.apk', 'split_config.xxhdpi.apk')
IDENTITY = "name='com.apple.android.music' versionCode='1606' versionName='7.0.0-beta'"


def run(*args):
    return subprocess.check_output([str(x) for x in args], text=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path)
    parser.add_argument('module', type=Path)
    parser.add_argument('output', type=Path)
    for name in (*single.TOOLS, 'aapt2', 'apksigner'):
        parser.add_argument('--'+name, type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Refusing to overwrite an existing APK')
    if single.digest(args.input) != INPUT_SHA256:
        parser.error('Input differs from upstream 103 embedded release')
    for name, expected in single.TOOLS.items():
        if single.digest(getattr(args, name)) != expected:
            parser.error(f'Unexpected {name} checksum')
    if "name='dev.amenhancer.module.debug'" not in run(args.aapt2, 'dump', 'badging', args.module):
        parser.error('Expected the ordinary debug module, never the coexist module')
    single.certificate(args.apksigner, args.module)
    report = {'host': 'com.apple.android.music/7.0.0-beta/1606',
              'inputSha256': INPUT_SHA256, 'baseSha256': BASE_SHA256,
              'moduleSha256': single.digest(args.module), 'tools': single.TOOLS,
              'deviceVerified': False}
    with tempfile.TemporaryDirectory(prefix='ampp-700-single-') as folder:
        work = Path(folder)
        original = work/'original'
        original.mkdir()
        with zipfile.ZipFile(args.input) as archive:
            if set(archive.namelist()) != set(SPLITS) or archive.testzip() is not None:
                parser.error('Expected complete arm64/xxhdpi APKS')
            for name in SPLITS:
                patched = work/name
                patched.write_bytes(archive.read(name))
                with zipfile.ZipFile(patched) as apk:
                    nested = 'assets/npatch/origin.apk' in apk.namelist()
                if nested:
                    single.extract_nested(patched, 'assets/npatch/origin.apk', original/name)
                else:
                    shutil.copyfile(patched, original/name)
        if single.digest(original/'base.apk') != BASE_SHA256:
            parser.error('Unexpected original base APK')
        # Original signatures rotate from Apple (API30–32) to Play (API33+).
        # Verify every original split rather than trusting its archive filename.
        for name in SPLITS:
            single.certificate(args.apksigner, original/name, min_sdk=30)
        subprocess.run(['python3', str(Path(__file__).with_name('verify-host-profile.py')),
                        str(original/'base.apk'), '--glass'], check=True)
        subprocess.run(['python3', str(Path(__file__).with_name('verify-700-lyrics-extension.py')),
                        str(original/'base.apk'), '--aapt2', str(args.aapt2)], check=True)
        merged = work/'merged.apk'
        subprocess.run(['java', '-jar', str(args.editor), 'm', '-i', str(original),
                        '-o', str(merged), '-validate-modules'], check=True)
        expected = set().union(*(single.resources(args.aapt2, original/name) for name in SPLITS))
        expected = {x for x in expected if x[1] != 'xml/splits0'}
        if not expected <= single.resources(args.aapt2, merged):
            raise RuntimeError('Merged APK lost host resources')
        with zipfile.ZipFile(merged) as apk, zipfile.ZipFile(original/'base.apk') as base:
            for name in base.namelist():
                if re.fullmatch(r'classes\d*\.dex', name) and apk.read(name) != base.read(name):
                    raise RuntimeError(f'Host DEX changed: {name}')
        security = work/'bc.security'
        security.write_text('security.provider.13=org.bouncycastle.jce.provider.BouncyCastleProvider\n')
        subprocess.run(['java', f'-Djava.security.properties={security}', '-jar', str(args.npatch),
                        str(merged), '-m', str(args.module), '-l', '2', '-o', str(work/'single')], check=True)
        result = work/'single/merged-741-npatched.apk'
        report['certificateSha256'] = single.certificate(args.apksigner, result)
        if report['certificateSha256'] != CERTIFICATE_SHA256:
            raise RuntimeError('Signature differs from the existing project APKs')
        if IDENTITY not in run(args.aapt2, 'dump', 'badging', result):
            raise RuntimeError('Unexpected final host identity')
        manifest = run(args.aapt2, 'dump', 'xmltree', result, '--file', 'AndroidManifest.xml')
        if 'isSplitRequired=false' not in manifest or 'isSplitRequired=true' in manifest:
            raise RuntimeError('Single APK still requires splits')
        module = work/'embedded-module.apk'
        single.extract_nested(result, single.MODULE, module)
        if single.digest(module) != single.digest(args.module):
            raise RuntimeError('Embedded module changed')
        origin = work/'embedded-origin.apk'
        single.extract_nested(result, 'assets/npatch/origin.apk', origin)
        if single.digest(origin) != single.digest(merged):
            raise RuntimeError('Embedded original changed')
        config = json.loads(run('unzip', '-p', result, 'assets/npatch/config.json'))
        if config.get('useManager') or config.get('newPackage') != 'com.apple.android.music' or config.get('sigBypassLevel') != 2:
            raise RuntimeError('Unexpected embedding configuration')
        with zipfile.ZipFile(result) as apk, zipfile.ZipFile(original/SPLITS[1]) as native:
            for name in native.namelist():
                if name.startswith('lib/') and not name.endswith('/') and apk.read(name) != native.read(name):
                    raise RuntimeError(f'Native library changed: {name}')
        args.output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(result, args.output)
    checksum = single.digest(args.output)
    Path(str(args.output)+'.sha256').write_text(f'{checksum}  {args.output.name}\n')
    report.update(apkSha256=checksum, staticVerified=True)
    Path(str(args.output)+'.validation.json').write_text(json.dumps(report, indent=2)+'\n')
    print(f'PASS: {args.output.name} sha256={checksum}; device validation pending')


if __name__ == '__main__':
    main()
