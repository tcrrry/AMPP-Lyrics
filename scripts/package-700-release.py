"""Produce v1.3 ordinary APK/APKS/module and coexist APK from the verified 1606 inputs."""
import argparse
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

spec = importlib.util.spec_from_file_location('beta', Path(__file__).with_name('package-700-single-apk.py'))
beta = importlib.util.module_from_spec(spec)
spec.loader.exec_module(beta)
single = beta.single


def run(*args):
    return subprocess.check_output([str(x) for x in args], text=True)


def check_apk(args, path, identity, expected_cert=beta.CERTIFICATE_SHA256, min_sdk=30):
    cert = single.certificate(args.apksigner, path, min_sdk=min_sdk)
    if cert != expected_cert:
        raise RuntimeError(f'Unexpected signer: {path.name}')
    if identity and identity not in run(args.aapt2, 'dump', 'badging', path):
        raise RuntimeError(f'Unexpected identity: {path.name}')


def checksum(path):
    value = single.digest(path)
    Path(str(path)+'.sha256').write_text(f'{value}  {path.name}\n')
    return value


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('input','module','coexist-module','output','editor','npatch','aapt2','apksigner','javac'):
        p.add_argument('--'+name, type=Path, required=True)
    args = p.parse_args()
    if args.output.exists() and any(args.output.iterdir()):
        p.error('Refusing to overwrite release artifacts')
    if single.digest(args.input) != beta.INPUT_SHA256:
        p.error('Wrong 103 input archive')
    for name, expected in single.TOOLS.items():
        if single.digest(getattr(args,name)) != expected:
            p.error('Unexpected '+name+' tool')
    args.output.mkdir(parents=True, exist_ok=True)
    module = args.output/'AMPP-Lyrics-module.apk'
    shutil.copyfile(args.module, module)
    module_identity = "name='dev.amenhancer.module.debug' versionCode='117' versionName='1.6.7-debug'"
    if module_identity not in run(args.aapt2,'dump','badging',module):
        p.error('Wrong ordinary v1.3 module')
    if "name='dev.amenhancer.module.coexist' versionCode='117' versionName='1.6.7-coexist'" not in run(args.aapt2,'dump','badging',args.coexist_module):
        p.error('Wrong coexist v1.3 module')
    checksum(module)
    apk = args.output/'AMPP-Lyrics-AppleMusic-7.0.0-beta-arm64.apk'
    subprocess.run(['python3', str(Path(__file__).with_name('package-700-single-apk.py')),
                    str(args.input), str(module), str(apk),
                    *[item for name in ('editor','npatch','aapt2','apksigner') for item in ('--'+name,str(getattr(args,name)))]],check=True)
    with tempfile.TemporaryDirectory(prefix='ampp-v13-') as folder:
        work = Path(folder)
        security = work/'bc.security'
        security.write_text('security.provider.13=org.bouncycastle.jce.provider.BouncyCastleProvider\n')
        def patch(host, embedded, output):
            subprocess.run(['java',f'-Djava.security.properties={security}','-jar',str(args.npatch),
                            str(host),'-m',str(embedded),'-l','2','-o',str(output)],check=True)
            matches=list(output.glob('*-741-npatched.apk'))
            if len(matches)!=1: raise RuntimeError('Expected one NPatch output')
            return matches[0]
        # The 103 ABI/density splits are already signed with the project's NPatch certificate.
        # They contain no embedded origin and no module. Keep them byte-identical.
        with zipfile.ZipFile(args.input) as archive:
            splits=[]
            for name in beta.SPLITS:
                path=work/name;path.write_bytes(archive.read(name));splits.append(path)
        original=work/'base-original.apk'
        single.extract_nested(splits[0],'assets/npatch/origin.apk',original)
        if single.digest(original)!=beta.BASE_SHA256:raise RuntimeError('Wrong original base')
        patched_base=patch(original,module,work/'apks-base')
        check_apk(args,patched_base,beta.IDENTITY)
        original_check=work/'apks-origin.apk'
        single.extract_nested(patched_base,'assets/npatch/origin.apk',original_check)
        if single.digest(original_check)!=beta.BASE_SHA256:raise RuntimeError('Split base DEX/origin changed')
        embedded=work/'apks-module.apk'
        single.extract_nested(patched_base,single.MODULE,embedded)
        if single.digest(embedded)!=single.digest(module):raise RuntimeError('Split module changed')
        for split in splits[1:]:check_apk(args,split,None)
        apks=args.output/'AMPP-Lyrics-AppleMusic-7.0.0-beta-arm64.apks'
        with zipfile.ZipFile(apks,'x',compression=zipfile.ZIP_STORED) as archive:
            archive.write(patched_base,'base.apk')
            for split in splits[1:]:archive.write(split,split.name)
        with zipfile.ZipFile(apks) as archive:
            if archive.testzip() is not None or set(archive.namelist())!=set(beta.SPLITS):
                raise RuntimeError('Incomplete or invalid APKS')
            for split in splits[1:]:
                if archive.read(split.name)!=split.read_bytes():raise RuntimeError('ABI/density split changed')
        checksum(apks)
        # Isolate the freshly validated merged origin, retaining its code/resource namespace.
        merged=work/'merged.apk';single.extract_nested(apk,'assets/npatch/origin.apk',merged)
        isolated=work/'isolated.apk'
        java_sources=[Path(__file__).with_name(name) for name in ('CoexistenceManifest.java','VerifyCoexistenceLayouts.java')]
        classes=work/'classes';classes.mkdir()
        subprocess.run([str(args.javac),'-cp',str(args.editor),'-d',str(classes),*map(str,java_sources)],check=True)
        classpath=str(classes)+__import__('os').pathsep+str(args.editor)
        subprocess.run(['java','-cp',classpath,'CoexistenceManifest',str(merged),str(isolated),'AM++ Lyrics 共存版'],check=True)
        coex=patch(isolated,args.coexist_module,work/'coexist')
        target='com.tcrrry.ampplyrics.coexist'
        check_apk(args,coex,f"name='{target}' versionCode='1606' versionName='7.0.0-beta'")
        manifest=run(args.aapt2,'dump','xmltree',coex,'--file','AndroidManifest.xml')
        if any(x in manifest for x in ('sharedUserId','BROWSABLE','isSplitRequired=true')):
            raise RuntimeError('Coexist shared UID, external links or split dependency remain')
        if re.search(r'authorities[^\n]*="com\.apple\.android\.music',manifest):
            raise RuntimeError('Original provider authority remains')
        if "application-label:'AM++ Lyrics 共存版'" not in run(args.aapt2,'dump','badging',coex):
            raise RuntimeError('Missing distinct coexist label')
        config=json.loads(run('unzip','-p',coex,'assets/npatch/config.json'))
        normal_config=json.loads(run('unzip','-p',apk,'assets/npatch/config.json'))
        if config.get('newPackage')!=target or config.get('originalSignature')!=normal_config.get('originalSignature') or config.get('useManager'):
            raise RuntimeError('Unexpected coexist embedding configuration')
        origin=work/'coex-origin.apk';single.extract_nested(coex,'assets/npatch/origin.apk',origin)
        if single.digest(origin)!=single.digest(isolated):raise RuntimeError('Isolated original changed')
        subprocess.run(['java','-cp',classpath,'VerifyCoexistenceLayouts',str(coex),str(origin)],check=True)
        if not single.resources(args.aapt2,merged)<=single.resources(args.aapt2,origin):
            raise RuntimeError('Coexist lost original resource IDs/names')
        with zipfile.ZipFile(merged) as before,zipfile.ZipFile(origin) as after,zipfile.ZipFile(coex) as final:
            for name in before.namelist():
                if re.fullmatch(r'classes\d*\.dex',name) and before.read(name)!=after.read(name):
                    raise RuntimeError('Coexist original DEX changed: '+name)
                if name.startswith('lib/') and not name.endswith('/') and before.read(name)!=final.read(name):
                    raise RuntimeError('Coexist native library changed: '+name)
        single.extract_nested(coex,'assets/npatch/modules/dev.amenhancer.module.coexist.apk',embedded)
        if single.digest(embedded)!=single.digest(args.coexist_module):raise RuntimeError('Coexist module changed')
        coexist=args.output/'AMPP-Lyrics-AppleMusic-7.0.0-beta-arm64-coexist.apk'
        shutil.copyfile(coex,coexist);checksum(coexist)
    report={'release':'v1.3','host':'7.0.0-beta/1606','staticVerified':True,
            'deviceVerified':{'test-r1-ordinary-apk':'User reports normal use except missing separate lyrics settings entry; entry fixed in v1.3',
                              'v1.3-new-packages':False},
            'files':{path.name:{'sha256':single.digest(path),'bytes':path.stat().st_size}
                     for path in args.output.iterdir() if path.suffix in ('.apk','.apks')}}
    (args.output/'v1.3-validation.json').write_text(json.dumps(report,indent=2)+'\n')
    print('PASS: four v1.3 installation artifacts, signatures, identities and original host contents verified')


if __name__=='__main__':main()
