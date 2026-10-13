"""Validate the exact released plugin code and its intentional integrated offline adapter."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import zipfile
import host_dex

CODE_SHA256 = 'ae22a36eebe1cc6f402845c168399509633bab45a8dd49cb49c5f317526861ec'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('module', type=Path)
    args = parser.parse_args()
    with zipfile.ZipFile(args.module) as module:
        with zipfile.ZipFile(io.BytesIO(module.read('assets/bundled/tcrrry-lyrics.zip'))) as plugin:
            manifest = json.loads(plugin.read('plugin.json'))
            assert manifest['id'] == 'com.tcrrry.lyrics'
            assert manifest['versionName'] == '1.0.6' and manifest['versionCode'] == 22
            assert hashlib.sha256(plugin.read('code.jar')).hexdigest() == CODE_SHA256
            channel = json.loads(plugin.read('assets/update-channel.json'))
            assert channel['repository'] == 'tcrrry/AMPP-Lyrics' and channel['version'] == '1.7'
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
    print(json.dumps({'module': args.module.name, 'releasedPluginCodeUnchanged': True,
                      'offlineAdapterMethods': len(required), 'mainUpdateChannel': channel,
                      'sdkInParentOnly': True, 'embeddedNativeTranslation': True}, ensure_ascii=False))


if __name__ == '__main__':
    main()
