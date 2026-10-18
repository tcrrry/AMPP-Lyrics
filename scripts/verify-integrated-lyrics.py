"""Validate both released SDK bundles and exclude the obsolete parent-loader offline adapter."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import zipfile
import host_dex

PINS = Path(__file__).resolve().parents[1]/'docs/integration-v1.10.json'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('module', type=Path)
    args = parser.parse_args()
    pins = json.loads(PINS.read_text(encoding='utf8'))
    with zipfile.ZipFile(args.module) as module:
        with zipfile.ZipFile(io.BytesIO(module.read('assets/bundled/tcrrry-lyrics.zip'))) as plugin:
            manifest = json.loads(plugin.read('plugin.json'))
            assert manifest['id'] == 'com.tcrrry.lyrics'
            assert manifest['versionName'] == '1.0.9' and manifest['versionCode'] == 53
            assert hashlib.sha256(plugin.read('code.jar')).hexdigest() == pins['bundles']['tcrrry-lyrics.zip']['codeSha256']
            assert not any(n.startswith(('assets/pure-offline/', 'assets/bundled-models/')) for n in plugin.namelist())
            channel = json.loads(plugin.read('assets/update-channel.json'))
            assert channel['repository'] == 'tcrrry/AMPP-Lyrics' and channel['version'] == '1.10'
        for asset, expected in pins['bundles'].items():
            payload = module.read('assets/bundled/' + asset)
            assert hashlib.sha256(payload).hexdigest() == expected['sha256'], asset
            with zipfile.ZipFile(io.BytesIO(payload)) as bundle:
                descriptor = json.loads(bundle.read('plugin.json'))
                assert descriptor['id'] == expected['id'] and descriptor['versionCode'] == expected['versionCode']
                assert hashlib.sha256(bundle.read('code.jar')).hexdigest() == expected['codeSha256']
                if descriptor['id'] == 'com.tcrrry.translation':
                    assert hashlib.sha256(bundle.read('assets/bundled-models/zh.zip')).hexdigest() == pins['chineseModelSha256']
                    runtime = bundle.read('assets/pure-offline/runtime.apk')
                    assert hashlib.sha256(runtime).hexdigest() == json.loads(bundle.read('assets/pure-offline/runtime.json'))['sha256']
        classes = {}
        for name in module.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                classes.update(host_dex.dex_classes(module.read(name)))
        assert 'Lcom/tcrrry/lyrics/plugin/OfflineTranslationClient;' not in classes, 'Old parent adapter shadows SDK client'
        assert 'Ldev/amenhancer/plugin/api/AmppPlugin;' in classes
        assert not any(name.startswith(('Lcom/tcrrry/lyrics/engine/', 'Lcom/google/mlkit/')) for name in classes)
    print(json.dumps({'module': args.module.name, 'releasedPluginCodeUnchanged': True,
                      'mainUpdateChannel': channel, 'obsoleteAdapterAbsent': True,
                      'sdkInParentOnly': True, 'bundledTranslationAndChineseVerified': True}, ensure_ascii=False))


if __name__ == '__main__':
    main()
