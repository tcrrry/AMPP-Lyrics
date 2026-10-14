"""Add vivo discovery to the accepted routing preview, reusing its exact module."""
import argparse
import json
import os
from pathlib import Path
import runpy
import shutil
import subprocess
import tempfile
import zipfile

BASE = runpy.run_path(str(Path(__file__).with_name('package-single-apk.py')))
INPUT_SHA = 'b8745fa56cf53fc89712178f7905a591da3bc0633d9bf5a41b184beeac855ba0'
MODULE_SHA = 'd4c84101e2350c3e4d611ca75042d463be6c6adf7c89c35efef2d3f3bd9e2ab8'
MODULE_ENTRY = 'assets/npatch/modules/dev.amenhancer.module.coexist.apk'


def output(*command):
    return subprocess.check_output([str(value) for value in command], text=True, encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('input', 'output', 'npatch', 'aapt2', 'apksigner', 'editor', 'javac'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Refusing to overwrite an existing artifact')
    if BASE['digest'](args.input) != INPUT_SHA:
        parser.error('Input must be the accepted routing-preview-1 APK')
    for name, expected in BASE['TOOLS'].items():
        if BASE['digest'](getattr(args, name)) != expected:
            parser.error('Unexpected ' + name + ' tool')
    with tempfile.TemporaryDirectory(prefix='ampp-coexist-vivo-') as directory:
        work = Path(directory)
        original, prepared = work / 'original.apk', work / 'vivo-origin.apk'
        module = work / 'module.apk'
        BASE['extract_nested'](args.input, 'assets/npatch/origin.apk', original)
        BASE['extract_nested'](args.input, MODULE_ENTRY, module)
        assert BASE['digest'](module) == MODULE_SHA, 'Accepted module changed'
        classes = work / 'classes'
        classes.mkdir()
        subprocess.run([str(args.javac), '-encoding', 'UTF-8', '-cp', str(args.editor), '-d', str(classes),
                        str(Path(__file__).with_name('VivoAtomicManifest.java')),
                        str(Path(__file__).with_name('VerifyCoexistenceLayouts.java'))], check=True)
        classpath = str(classes) + os.pathsep + str(args.editor)
        subprocess.run(['java', '-cp', classpath, 'VivoAtomicManifest', str(original), str(prepared)], check=True)
        with zipfile.ZipFile(original) as before, zipfile.ZipFile(prepared) as after:
            kept = lambda archive: {n for n in archive.namelist() if not n.startswith('META-INF/') and not n.endswith('/')}
            assert kept(before) == kept(after), 'Host entries changed'
            changed = [name for name in sorted(kept(before)) if before.read(name) != after.read(name)]
            assert changed == ['AndroidManifest.xml'], changed
        security = work / 'bc.security'
        security.write_text('security.provider.13=org.bouncycastle.jce.provider.BouncyCastleProvider\n')
        subprocess.run(['java', f'-Djava.security.properties={security}', '-jar', str(args.npatch),
                        str(prepared), '-m', str(module), '-l', '2', '-o', str(work / 'patched')], check=True)
        results = list((work / 'patched').glob('*-741-npatched.apk'))
        assert len(results) == 1
        result = results[0]
        cert = BASE['certificate'](args.apksigner, result, min_sdk=31)
        assert cert == BASE['certificate'](args.apksigner, args.input, min_sdk=31), 'Signer changed'
        badging = output(args.aapt2, 'dump', 'badging', result)
        assert "name='com.tcrrry.ampplyrics.coexist' versionCode='1609' versionName='7.0.0-beta'" in badging
        assert "minSdkVersion:'31'" in badging
        current = json.loads(output('unzip', '-p', result, 'assets/npatch/config.json'))
        previous = json.loads(output('unzip', '-p', args.input, 'assets/npatch/config.json'))
        assert current == previous, 'NPatch configuration or original signature changed'
        cached, embedded = work / 'cached.apk', work / 'embedded.apk'
        BASE['extract_nested'](result, 'assets/npatch/origin.apk', cached)
        BASE['extract_nested'](result, MODULE_ENTRY, embedded)
        assert BASE['digest'](cached) == BASE['digest'](prepared)
        assert BASE['digest'](embedded) == MODULE_SHA
        with zipfile.ZipFile(args.input) as before, zipfile.ZipFile(result) as after:
            for name in before.namelist():
                if name.startswith('lib/') and not name.endswith('/'):
                    assert before.read(name) == after.read(name), name
        for apk in (result, cached):
            subprocess.run(['java', '-cp', classpath, 'VivoAtomicManifest', '--verify', str(apk)], check=True)
        subprocess.run(['java', '-cp', classpath, 'VerifyCoexistenceLayouts', str(result), str(cached)], check=True)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(result, args.output)
    sha = BASE['digest'](args.output)
    report = {'previewOnly': True, 'deviceVerified': False, 'host': '7.0.0-beta/1609',
              'baseSha256': INPUT_SHA, 'onlyChangedHostEntry': 'AndroidManifest.xml',
              'addedAction': 'com.vivo.musicwidgetmix.support.service',
              'existingService': 'com.apple.android.music.player.MediaPlaybackService',
              'embeddedModuleUnchanged': MODULE_SHA, 'signerSha256': cert,
              'npatchConfigurationUnchanged': True, 'nativeLibrariesUnchanged': True,
              'sha256': sha, 'bytes': args.output.stat().st_size}
    Path(str(args.output) + '.sha256').write_text(f'{sha}  {args.output.name}\n')
    Path(str(args.output) + '.validation.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
