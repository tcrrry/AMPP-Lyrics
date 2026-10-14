"""Validate the other three installation forms using the same settings preview code."""
import argparse
import json
import runpy
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

BASE = runpy.run_path(str(Path(__file__).with_name('package-single-apk.py')))
PINS = {
    'ordinary': '49a571e6f7afe619e2693dd9efaa38a0aa816fe613531778c9b5078fc9a8ee82',
    'apks': '2d14dbc9556f18433b2f144fc1fa2c25e28729cd76117c7288a470b521407525',
}
MODULE = 'assets/npatch/modules/dev.amenhancer.module.debug.apk'


def output(*command):
    return subprocess.check_output([str(value) for value in command], text=True, encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('ordinary', 'apks', 'module', 'coexist-module', 'plugin', 'output', 'npatch', 'aapt2', 'apksigner'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--preview-label', default='settings-preview-3')
    args = parser.parse_args()
    import re
    assert re.fullmatch(r'[a-z0-9-]+', args.preview_label), 'Invalid preview label'
    for name, sha in PINS.items():
        assert BASE['digest'](getattr(args, name)) == sha, name
    assert BASE['digest'](args.npatch) == BASE['TOOLS']['npatch']
    assert "name='dev.amenhancer.module.debug' versionCode='151' versionName='1.7'" in output(args.aapt2, 'dump', 'badging', args.module)
    assert "minSdkVersion:'31'" in output(args.aapt2, 'dump', 'badging', args.module)
    # Both identities must embed exactly the same plugin payload, not two independently rebuilt DEXes.
    with zipfile.ZipFile(args.module) as ordinary, zipfile.ZipFile(getattr(args, 'coexist_module')) as coexist:
        assert ordinary.read('assets/bundled/tcrrry-lyrics.zip') == coexist.read('assets/bundled/tcrrry-lyrics.zip')
    for module in (args.module, getattr(args, 'coexist_module')):
        subprocess.run([sys.executable, str(Path(__file__).with_name('verify-integrated-lyrics.py')),
                        str(module), '--preview-plugin', str(args.plugin)], check=True)
    args.output.mkdir(parents=True, exist_ok=True)
    outputs = [args.output / name for name in (
        f'AMPP-Lyrics-1609-ordinary-{args.preview_label}.apk',
        f'AMPP-Lyrics-1609-{args.preview_label}.apks', f'AMPP-Lyrics-module-{args.preview_label}.apk')]
    assert not any(path.exists() for path in outputs), 'Refusing to overwrite existing previews'
    with tempfile.TemporaryDirectory(prefix='ampp-other-settings-') as directory:
        work = Path(directory)
        security = work / 'bc.security'
        security.write_text('security.provider.13=org.bouncycastle.jce.provider.BouncyCastleProvider\n')
        def patch(source, label):
            origin = work / (label + '-origin.apk')
            BASE['extract_nested'](source, 'assets/npatch/origin.apk', origin)
            destination = work / label
            subprocess.run(['java', f'-Djava.security.properties={security}', '-jar', str(args.npatch),
                            str(origin), '-m', str(args.module), '-l', '2', '-o', str(destination)], check=True)
            results = list(destination.glob('*-741-npatched.apk'))
            assert len(results) == 1
            result = results[0]
            assert BASE['certificate'](args.apksigner, result, min_sdk=31) == BASE['certificate'](args.apksigner, source, min_sdk=31)
            assert "name='com.apple.android.music' versionCode='1609' versionName='7.0.0-beta'" in output(args.aapt2, 'dump', 'badging', result)
            assert "minSdkVersion:'31'" in output(args.aapt2, 'dump', 'badging', result)
            for entry, expected in [('assets/npatch/origin.apk', origin), (MODULE, args.module)]:
                extracted = work / ('verify-' + label + '-' + expected.name)
                BASE['extract_nested'](result, entry, extracted)
                assert BASE['digest'](extracted) == BASE['digest'](expected), entry
            before = json.loads(output('unzip', '-p', source, 'assets/npatch/config.json'))
            after = json.loads(output('unzip', '-p', result, 'assets/npatch/config.json'))
            assert after['originalSignature'] == before['originalSignature']
            assert after.get('newPackage') == before.get('newPackage') and not after.get('useManager')
            with zipfile.ZipFile(source) as original, zipfile.ZipFile(result) as updated:
                for entry in original.namelist():
                    if entry.startswith('lib/') and not entry.endswith('/'):
                        assert original.read(entry) == updated.read(entry), entry
            return result
        single = patch(args.ordinary, 'ordinary')
        shutil.copyfile(single, outputs[0])
        with zipfile.ZipFile(args.apks) as archive:
            assert archive.testzip() is None
            names = archive.namelist()
            assert set(names) == set(BASE['SPLITS'])
            base = work / 'base.apk'
            base.write_bytes(archive.read('base.apk'))
            patched = patch(base, 'split-base')
            with zipfile.ZipFile(outputs[1], 'x', zipfile.ZIP_STORED) as updated:
                for name in names:
                    updated.writestr(name, patched.read_bytes() if name == 'base.apk' else archive.read(name))
            with zipfile.ZipFile(outputs[1]) as updated:
                assert updated.testzip() is None and updated.namelist() == names
                for name in names:
                    if name != 'base.apk':
                        assert updated.read(name) == archive.read(name)
        shutil.copyfile(args.module, outputs[2])
    report = {'previewOnly': True, 'deviceVerified': False, 'identicalBundledPluginAcrossVariants': True,
              'ordinaryApkApksAndStandaloneModuleShareExactModule': True,
              'hostOriginalDexResourcesAndNativeLibrariesUnchanged': True,
              'apksAbiDensitySplitsUnchanged': True,
              'files': {path.name: {'sha256': BASE['digest'](path), 'bytes': path.stat().st_size} for path in outputs}}
    for path in outputs:
        Path(str(path) + '.sha256').write_text(f"{report['files'][path.name]['sha256']}  {path.name}\n")
    (args.output / 'other-three-preview-validation.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
