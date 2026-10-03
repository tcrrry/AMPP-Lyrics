"""Embed a newly built standard module into the original splits preserved in an existing APKS."""
import argparse
from pathlib import Path
import subprocess
import tempfile
import zipfile

from importlib.util import spec_from_file_location, module_from_spec
spec = spec_from_file_location('single', Path(__file__).with_name('package-single-apk.py'))
single = module_from_spec(spec)
spec.loader.exec_module(single)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('source', 'module', 'output', 'npatch', 'apksigner'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    if single.digest(args.npatch) != single.TOOLS['npatch']:
        parser.error('Unexpected NPatch tool checksum')
    if args.output.exists():
        parser.error('Output already exists')
    with tempfile.TemporaryDirectory(prefix='ampp-apks-') as directory:
        work = Path(directory)
        original = work / 'original'
        original.mkdir()
        with zipfile.ZipFile(args.source) as archive:
            if set(archive.namelist()) != set(single.SPLITS) or archive.testzip() is not None:
                parser.error('Expected the complete arm64/xxxhdpi source APKS')
            for name in single.SPLITS:
                old = work / name
                old.write_bytes(archive.read(name))
                single.extract_nested(old, 'assets/npatch/origin.apk', original / name)
        security = work / 'bc.security'
        security.write_text('security.provider.13=org.bouncycastle.jce.provider.BouncyCastleProvider\n')
        subprocess.run(['java', f'-Djava.security.properties={security}', '-jar', str(args.npatch.resolve()),
                        '--embed', str(args.module.resolve()), '--npatch-keystore', '--output', str(work / 'patched'),
                        str(original / 'base.apk')], check=True)
        outputs = []
        for name in single.SPLITS:
            # ABI and density splits are unchanged; preserve their existing signatures.
            matches = (list((work / 'patched').glob('base-*-npatched.apk'))
                       if name == 'base.apk' else [work / name])
            if len(matches) != 1:
                raise RuntimeError(f'Expected one patched split: {name}')
            if single.certificate(args.apksigner, matches[0]) != single.certificate(args.apksigner, work / 'base.apk'):
                raise RuntimeError(f'Signing certificate changed: {name}')
            if name == 'base.apk':
                embedded = work / 'embedded.apk'
                single.extract_nested(matches[0], single.MODULE, embedded)
                if single.digest(embedded) != single.digest(args.module):
                    raise RuntimeError('Embedded module differs from tested module')
            outputs.append((matches[0], name))
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(args.output, 'x', compression=zipfile.ZIP_STORED) as archive:
            for path, name in outputs:
                archive.write(path, name)
        with zipfile.ZipFile(args.output) as archive:
            if archive.testzip() is not None:
                raise RuntimeError('APKS integrity verification failed')
        digest = single.digest(args.output)
        Path(str(args.output) + '.sha256').write_text(f'{digest}  {args.output.name}\n')
        print('APKS signatures, embedded module and archive verified: ' + digest)


if __name__ == '__main__':
    main()
