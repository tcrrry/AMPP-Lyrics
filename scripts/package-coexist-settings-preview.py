"""Re-embed a preview module into the unchanged, verified v1.7 coexist host."""
import argparse
import hashlib
import io
import json
import runpy
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

BASE = runpy.run_path(str(Path(__file__).with_name('package-single-apk.py')))
FORMAL_SHA256 = 'e806d498c6130db621f968b210c5943a6612031bd3cf859e6f0c4370b947a2db'
MODULE = 'assets/npatch/modules/dev.amenhancer.module.coexist.apk'


def output(*command):
    return subprocess.check_output([str(value) for value in command], text=True, encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('input', 'module', 'plugin', 'output', 'npatch', 'aapt2', 'apksigner', 'editor', 'javac'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('Refusing to overwrite an existing artifact')
    if BASE['digest'](args.input) != FORMAL_SHA256:
        parser.error('Input must be the verified formal v1.7 coexist APK')
    for name, expected in BASE['TOOLS'].items():
        if BASE['digest'](getattr(args, name)) != expected:
            parser.error('Unexpected ' + name + ' tool')
    if "name='dev.amenhancer.module.coexist' versionCode='151' versionName='1.7-coexist'" not in output(args.aapt2, 'dump', 'badging', args.module):
        parser.error('Wrong coexist module')
    with zipfile.ZipFile(args.module) as module, zipfile.ZipFile(args.plugin) as expected:
        assert module.testzip() is None
        with zipfile.ZipFile(io.BytesIO(module.read('assets/bundled/tcrrry-lyrics.zip'))) as plugin:
            manifest = json.loads(plugin.read('plugin.json'))
            assert manifest['id'] == 'com.tcrrry.lyrics'
            assert manifest['versionCode'] == 25 and manifest['versionName'] == '1.0.7-preview.3'
            assert plugin.read('code.jar') == expected.read('code.jar')
            assert plugin.read('plugin.json') == expected.read('plugin.json')
            channel = json.loads(plugin.read('assets/update-channel.json'))
            assert channel['repository'] == 'tcrrry/AMPP-Lyrics' and channel['version'] == '1.7'
            code_sha = hashlib.sha256(plugin.read('code.jar')).hexdigest()
        assert 'lib/arm64-v8a/libtranslate_jni.so' in module.namelist()
    with tempfile.TemporaryDirectory(prefix='ampp-coexist-settings-') as directory:
        work = Path(directory)
        original = work / 'coexist-origin.apk'
        BASE['extract_nested'](args.input, 'assets/npatch/origin.apk', original)
        security = work / 'bc.security'
        security.write_text('security.provider.13=org.bouncycastle.jce.provider.BouncyCastleProvider\n')
        subprocess.run(['java', f'-Djava.security.properties={security}', '-jar', str(args.npatch),
                        str(original), '-m', str(args.module), '-l', '2', '-o', str(work / 'patched')], check=True)
        results = list((work / 'patched').glob('*-741-npatched.apk'))
        assert len(results) == 1
        result = results[0]
        assert BASE['certificate'](args.apksigner, result, min_sdk=31) == BASE['certificate'](args.apksigner, args.input, min_sdk=31)
        badging = output(args.aapt2, 'dump', 'badging', result)
        assert "name='com.tcrrry.ampplyrics.coexist' versionCode='1609' versionName='7.0.0-beta'" in badging
        assert "minSdkVersion:'31'" in badging
        current = json.loads(output('unzip', '-p', result, 'assets/npatch/config.json'))
        previous = json.loads(output('unzip', '-p', args.input, 'assets/npatch/config.json'))
        assert current['originalSignature'] == previous['originalSignature']
        assert current['newPackage'] == 'com.tcrrry.ampplyrics.coexist' and not current.get('useManager')
        cached = work / 'cached.apk'
        embedded = work / 'module.apk'
        BASE['extract_nested'](result, 'assets/npatch/origin.apk', cached)
        BASE['extract_nested'](result, MODULE, embedded)
        assert BASE['digest'](cached) == BASE['digest'](original)
        assert BASE['digest'](embedded) == BASE['digest'](args.module)
        with zipfile.ZipFile(args.input) as before, zipfile.ZipFile(result) as after:
            for name in before.namelist():
                if name.startswith('lib/') and not name.endswith('/'):
                    assert before.read(name) == after.read(name), name
        classes = work / 'classes'
        classes.mkdir()
        subprocess.run([str(args.javac), '-encoding', 'UTF-8', '-cp', str(args.editor), '-d', str(classes),
                        str(Path(__file__).with_name('VerifyCoexistenceLayouts.java'))], check=True)
        import os
        subprocess.run(['java', '-cp', str(classes) + os.pathsep + str(args.editor),
                        'VerifyCoexistenceLayouts', str(result), str(cached)], check=True)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(result, args.output)
    sha = BASE['digest'](args.output)
    Path(str(args.output) + '.sha256').write_text(f'{sha}  {args.output.name}\n')
    report = {'previewOnly': True, 'deviceVerified': False, 'host': '7.0.0-beta/1609',
              'originalHostUnchanged': True, 'nativeLibrariesUnchanged': True,
              'signerMatchesFormal': True, 'bundledPlugin': manifest, 'pluginCodeSha256': code_sha,
              'sha256': sha, 'bytes': args.output.stat().st_size,
              'nextFormalPluginVersionCodeMustExceed': 25}
    Path(str(args.output) + '.validation.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    main()
