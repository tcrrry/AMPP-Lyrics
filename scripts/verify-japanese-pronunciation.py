"""Verify the pinned offline dictionary and its notices survived Android packaging."""
import hashlib
from pathlib import Path
import sys
import zipfile

EXPECTED = '24909fd751c0b439f7af5131b080eb65bc85062f0c4977ca4a71c76abe74e0b6'
module, dictionary = map(Path, sys.argv[1:])
assert hashlib.sha256(dictionary.read_bytes()).hexdigest() == EXPECTED, 'Unexpected IPADIC artifact'
with zipfile.ZipFile(module) as apk, zipfile.ZipFile(dictionary) as jar:
    resources = [name for name in jar.namelist() if name.startswith('com/atilika/kuromoji/ipadic/') and name.endswith('.bin')]
    assert len(resources) == 8
    for name in resources:
        assert apk.read(name) == jar.read(name), f'Dictionary changed or missing: {name}'
    for name in ('META-INF/LICENSE.md', 'META-INF/NOTICE.md', 'META-INF/CONTRIBUTORS.md'):
        assert jar.read(name).strip() in apk.read(name), f'Missing license: {name}'
    dex = b''.join(apk.read(name) for name in apk.namelist() if name.endswith('.dex'))
    assert b'Lcom/atilika/kuromoji/ipadic/Tokenizer;' in dex
print('PASS: 8 pinned IPADIC dictionary resources, tokenizer DEX and all third-party notices')
