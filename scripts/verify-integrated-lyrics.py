"""Validate the exact released plugin code and its intentional integrated offline adapter."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import zipfile
import host_dex

CODE_SHA256 = 'b77e0f3ad1bf0672acab87ab2b533071ef46b5e7799df18bc5116a7fe4d60fee'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('module', type=Path)
    parser.add_argument('--preview-plugin', type=Path, help='Explicit preview ZIP; default is pinned to the formal 1.0.7 release')
    args = parser.parse_args()
    with zipfile.ZipFile(args.module) as module:
        with zipfile.ZipFile(io.BytesIO(module.read('assets/bundled/tcrrry-lyrics.zip'))) as plugin:
            manifest = json.loads(plugin.read('plugin.json'))
            assert manifest['id'] == 'com.tcrrry.lyrics'
            if args.preview_plugin:
                with zipfile.ZipFile(args.preview_plugin) as expected:
                    assert plugin.read('plugin.json') == expected.read('plugin.json')
                    assert plugin.read('code.jar') == expected.read('code.jar')
            else:
                assert manifest['versionName'] == '1.0.7' and manifest['versionCode'] == 27
                assert hashlib.sha256(plugin.read('code.jar')).hexdigest() == CODE_SHA256
            channel = json.loads(plugin.read('assets/update-channel.json'))
            assert channel['repository'] == 'tcrrry/AMPP-Lyrics' and channel['version'] == '1.8'
        assert 'lib/arm64-v8a/libtranslate_jni.so' in module.namelist()
        classes = {}
        for name in module.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                classes.update(host_dex.dex_classes(module.read(name)))
        adapter = classes['Lcom/tcrrry/lyrics/plugin/OfflineTranslationClient;']
        required = {
            'getLanguages()Ljava/util/List;', 'installed(Landroid/content/Context;)Z',
            'statusText(Landroid/content/Context;)Ljava/lang/String;',
            'requireInstalled(Landroid/content/Context;)V',
            'translate(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;',
            'openManager(Landroid/app/Activity;)V',
        }
        assert required <= set(adapter['methods'])
        assert 'Ldev/amenhancer/plugin/api/AmppPlugin;' in classes
        assert not any(name.startswith('Lcom/tcrrry/lyrics/engine/') for name in classes)
    print(json.dumps({'module': args.module.name, 'releasedPluginCodeUnchanged': not bool(args.preview_plugin),
                      'explicitPreviewCodeVerified': bool(args.preview_plugin),
                      'offlineAdapterMethods': len(required), 'mainUpdateChannel': channel,
                      'sdkInParentOnly': True, 'embeddedNativeTranslation': True}, ensure_ascii=False))


if __name__ == '__main__':
    main()
