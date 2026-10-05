"""Verify Release transfers and preserve historical assets outside the selected tag."""
import argparse
import hashlib
import json
from pathlib import Path


def verify(directory, snapshot, tag="v1.3"):
    release = next(r for r in snapshot if r['tag_name'] == tag)
    assets = {a['name']: a for a in release['assets']}
    files = {p.name: p for p in directory.iterdir() if p.is_file()}
    if files.keys() != assets.keys():
        raise RuntimeError('Release file set does not match transfer')
    for name, p in files.items():
        digest = 'sha256:' + hashlib.file_digest(p.open('rb'), 'sha256').hexdigest()
        if assets[name]['size'] != p.stat().st_size or assets[name]['digest'] != digest:
            raise RuntimeError('Release file corrupted: ' + name)
    print('PASS: complete Release transfer, sizes and SHA-256')


def historical(before, after, replaced="v1.3"):
    def snapshot(releases):
        fields = ('id', 'name', 'size', 'digest', 'updated_at')
        return {r['tag_name']: sorted(tuple(a.get(k) for k in fields) for a in r['assets'])
                for r in releases if r['tag_name'] != replaced}
    if snapshot(before) != snapshot(after):
        raise RuntimeError('Historical Release assets changed')
    print('PASS: historical releases preserved')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('directory', type=Path, nargs='?')
    p.add_argument('--snapshot', type=Path)
    p.add_argument('--tag', default='v1.3')
    p.add_argument('--historical', type=Path, nargs=2)
    args = p.parse_args()
    if args.historical:
        historical(*(json.loads(path.read_text()) for path in args.historical), replaced=args.tag)
    elif args.directory and args.snapshot:
        verify(args.directory, json.loads(args.snapshot.read_text()), tag=args.tag)
    else:
        p.error('Specify directory --snapshot or --historical before after')
