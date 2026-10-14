"""Embed the released ZIP unchanged except for the main project's update channel."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('plugin', type=Path)
parser.add_argument('--main-version', required=True)
args = parser.parse_args()
target = Path(__file__).resolve().parents[1] / 'app/src/main/assets/bundled/tcrrry-lyrics.zip'
with zipfile.ZipFile(args.plugin) as source:
    manifest = json.loads(source.read('plugin.json'))
    assert manifest['id'] == 'com.tcrrry.lyrics'
    channel = {'repository': 'tcrrry/AMPP-Lyrics', 'tagPrefix': 'v',
               'version': args.main_version, 'label': 'AM++ Lyrics'}
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as output:
        for name in source.namelist():
            output.writestr(name, json.dumps(channel, ensure_ascii=False, indent=2).encode('utf-8')
                            if name == 'assets/update-channel.json' else source.read(name))
    with zipfile.ZipFile(target) as output:
        assert source.namelist() == output.namelist()
        for name in source.namelist():
            if name != 'assets/update-channel.json':
                assert source.read(name) == output.read(name), name
    print(json.dumps({'pluginVersion': manifest['versionName'], 'pluginCode': manifest['versionCode'],
                      'codeSha256': hashlib.sha256(source.read('code.jar')).hexdigest(),
                      'mainUpdateChannel': channel}, ensure_ascii=False))
