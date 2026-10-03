"""Verify our pronunciation/menu extension's exact 1606 DEX and resource contracts."""
import argparse
import importlib.util
from pathlib import Path
import re
import subprocess
import zipfile

spec = importlib.util.spec_from_file_location('host', Path(__file__).with_name('verify-host-profile.py'))
host = importlib.util.module_from_spec(spec)
spec.loader.exec_module(host)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('apk', type=Path)
    p.add_argument('--aapt2', type=Path, required=True)
    args = p.parse_args()
    badging = subprocess.check_output([str(args.aapt2), 'dump', 'badging', str(args.apk)], text=True)
    if "name='com.apple.android.music' versionCode='1606' versionName='7.0.0-beta'" not in badging:
        p.error('Requires exact original 7.0.0-beta/1606')
    classes = {}
    with zipfile.ZipFile(args.apk) as apk:
        for name in apk.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                classes.update(host.dex_classes(apk.read(name)))
    checks = 0
    def method(owner, descriptor, static=False):
        nonlocal checks
        checks += 1
        c = classes['L'+owner+';']
        if descriptor not in c['methods'] or bool(c['method_access'][descriptor] & 8) != static:
            raise RuntimeError(f'Wrong method contract: {owner}.{descriptor}')
    def field(owner, name, descriptor, static=False):
        nonlocal checks
        checks += 1
        c = classes['L'+owner+';']
        if c['fields'].get(name) != descriptor or bool(c['field_access'][name] & 8) != static:
            raise RuntimeError(f'Wrong field contract: {owner}.{name}')
    base = 'com/apple/android/music/player/n1'
    adapter = 'com/apple/android/music/player/A'
    flex = 'Lcom/apple/android/music/common/views/FullWidthAlphaGradientFlexboxLayout;'
    holder = 'Landroidx/recyclerview/widget/RecyclerView$D;'
    method(adapter, 'U(Ljava/lang/String;'+flex+'IZI)V')
    method(adapter, 'b0(Lcom/apple/android/music/ttml/javanative/model/LyricsWordVector;Landroid/util/ArrayMap;'+flex+'IIZZ)Landroid/util/ArrayMap;')
    method(adapter, 'y()Lcom/apple/android/music/ttml/javanative/model/SongInfo$SongInfoPtr;')
    method(adapter, 'f(I)I')
    method(adapter, 'E(IZ)I')
    method(adapter, 'k('+holder+'I)V')
    method(adapter, 'l('+holder+'ILjava/util/List;)V')
    method(base, 'B()Z')
    field(adapter, 'p', 'Lcom/apple/android/music/ttml/j;')
    for name in ('d','e'): field(base, name, 'Z')
    for name in ('h','i'): field(base, name, 'Ljava/lang/String;', True)
    field(base+'$b', 'y', 'Ljava/util/LinkedHashSet;')
    field(base+'$b', 'v', 'Ljava/lang/String;')
    field('androidx/recyclerview/widget/RecyclerView$D', 'a', 'Landroid/view/View;')
    method('com/apple/android/music/ttml/j', 'a(I)Lcom/apple/android/music/ttml/javanative/model/LyricsLine$LyricsLinePtr;')
    method('com/apple/android/music/ttml/javanative/model/LyricsLine$LyricsLinePtr', 'get()Lcom/apple/android/music/ttml/javanative/model/LyricsLine$LyricsLineNative;')
    line = 'com/apple/android/music/ttml/javanative/model/LyricsLine$LyricsLineNative'
    method(line, 'getLineId()I')
    for name in ('getHtmlPronunciationLineText', 'getHtmlPronunciationBackgroundVocalsLineText'):
        method(line, name+'()Ljava/lang/String;')
    constraints = 'androidx/constraintlayout/widget/ConstraintLayout$b'
    method(constraints, '<init>(Landroid/view/ViewGroup$LayoutParams;)V')
    for name in ('i','j','k','l','t','v'): field(constraints, name, 'I')
    # Research against the SHA-pinned 1606 mapper: these subtitle bindings
    # derive from the two types accepted by A.U; ruby word bindings do not.
    for concrete, parent in (('E9', 'D9'), ('c9', 'b9')):
        checks += 1
        if classes['Lq8/'+concrete+';']['super'] != 'Lq8/'+parent+';':
            raise RuntimeError('Native auxiliary subtitle binding changed: '+concrete)
    vector = 'com/apple/android/mediaservices/javanative/common/StringVector$StringVectorNative'
    method(vector, 'size()J')
    method(vector, 'get(J)Ljava/lang/String;')
    method('com/apple/android/music/playback/util/LocaleUtil', 'matchToSystemLyricsScript(L'+vector+';)Ljava/lang/String;', True)
    resources = subprocess.check_output([str(args.aapt2), 'dump', 'resources', str(args.apk)], text=True)
    for name in ('layout/lyrics_word_karaoke','layout/lyrics_word_karaoke_bg',
                 'layout/lyrics_translation_line_karaoke','layout/lyrics_bg_translation_line_karaoke',
                 'color/white_alpha_35','id/translations_button','id/translations_popup_menu'):
        checks += 1
        if not re.search(r'resource 0x[0-9a-f]+ '+name+r'\b', resources):
            raise RuntimeError(f'Missing extension resource: {name}')
    print(f'PASS: {checks} pronunciation/menu extension contracts for 1606')


if __name__ == '__main__':
    main()
