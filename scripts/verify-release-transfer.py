"""Verify Release transfers and preserve releases other than explicitly replaced v1.3."""
import argparse
import hashlib
import json
from pathlib import Path


def verify(directory, snapshot):
    release = next(r for r in snapshot if r['tag_name'] == 'v1.3')
    assets = {a['name']: a for a in release['assets']}
    files = {p.name: p for p in directory.iterdir() if p.is_file()}
    if files.keys() != assets.keys():
        raise RuntimeError('Release file set does not match transfer')
    for name, p in files.items():
        digest = 'sha256:' + hashlib.file_digest(p.open('rb'), 'sha256').hexdigest()
        if assets[name]['size'] != p.stat().st_size or assets[name]['digest'] != digest:
            raise RuntimeError('Release file corrupted: ' + name)
    print('PASS: complete Release transfer, sizes and SHA-256')


def historical(before, after):
    def snapshot(releases):
        fields = ('id', 'name', 'size', 'digest', 'updated_at')
        return {r['tag_name']: sorted(tuple(a.get(k) for k in fields) for a in r['assets'])
                for r in releases if r['tag_name'] != 'v1.3'}
    if snapshot(before) != snapshot(after):
        raise RuntimeError('Historical Release assets changed')
    print('PASS: historical releases preserved')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('directory', type=Path, nargs='?')
    p.add_argument('--snapshot', type=Path)
    p.add_argument('--historical', type=Path, nargs=2)
    args = p.parse_args()
    if args.historical:
        historical(*(json.loads(path.read_text()) for path in args.historical))
    elif args.directory and args.snapshot:
        verify(args.directory, json.loads(args.snapshot.read_text()))
    else:
        p.error('Specify directory --snapshot or --historical before after')
