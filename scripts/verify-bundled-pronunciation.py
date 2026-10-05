"""Check pinned bundled Korean/Cantonese data and notices, offline or in module APK."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

PINS = {
    ('ko', 'Latin-ConjoiningJamo.xml'): (19410, '82f2fc6ae1fca9c71fba7851c0b3ecc7e6e3cbd5aeb74ce80dd6c8c423cddfba'),
    ('ko', 'LICENSE'): (3193, '65eb33419e22a297550a811b5b535f5e532f9a7e0114b516c0ea18afd53c3804'),
    ('yue', 'jyut6ping3.chars.dict.yaml'): (345329, '9b053a594c80eae76545bcdd83239884a49b1ba48b89ca0ab9e8daac79f33013'),
    ('yue', 'jyut6ping3.words.dict.yaml'): (2630257, '54d174ad2bb997e4a678b7b076b84e4dc5914481b32467cdea3b43e88b7d5474'),
    ('yue', 'LICENSE-CC-BY'): (18657, '97d24386ff776d7160b87031b259a942bd03c9939796cab6724ad5d17d2cf785'),
}
if len(sys.argv) > 1:
    archive = zipfile.ZipFile(sys.argv[1])
    def read(name):
        return archive.read('assets/' + name)
else:
    def read(name):
        return Path('app/src/main/assets', name).read_bytes()
manifest = json.loads(read('pronunciation-modules.json'))
assert {(item['language'], item['name']) for item in manifest} == set(PINS)
for item in manifest:
    key = item['language'], item['name']
    data = read('pronunciation/' + '/'.join(key))
    assert (len(data), hashlib.sha256(data).hexdigest()) == PINS[key], key
    assert (item['size'], item['sha256']) == PINS[key], key
    commit = '5b159148f1390735049e296723f6b847229b23ef' if key[0] == 'ko' else '259f0e48bba840c3a2e0d117539e96937f3d89bc'
    assert item['url'].startswith('https://raw.githubusercontent.com/') and '/' + commit + '/' in item['url']
print('Verified 5 bundled Korean/Cantonese resources including licenses and source pins')
