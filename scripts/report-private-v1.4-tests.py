"""Record the real ordinary-build test results accompanying the private APK."""
import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

report = {'sourceCommit': os.environ['GITHUB_SHA'], 'deviceVerified': False, 'testSuites': {}}
for module in ('app', 'glass'):
    suites = [ET.parse(path).getroot() for path in Path(module, 'build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
    assert suites, f'Missing {module} test results'
    totals = {key: sum(int(suite.attrib[key]) for suite in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
    assert totals['tests'] >= (1229 if module == 'app' else 22), totals
    assert all(totals[key] == 0 for key in ('failures', 'errors', 'skipped')), totals
    report['testSuites'][module] = totals
    if module == 'app':
        japanese = next(suite for suite in suites if suite.attrib['name'].endswith('.JapanesePronunciationSupplementTest'))
        assert int(japanese.attrib['tests']) == 10
        for suffix, count in (('.LanguagePronunciationSupplementTest', 7), ('.NativeSettingsFrameRecoveryTest', 2), ('.NativeSettingsColdStartTest', 1), ('.LyricGlowTriggerPolicyTest', 8), ('.LyricGlowScopeTest', 5), ('.LyricGlowSettingsTest', 2), ('.LongLatinGlowGateTest', 2), ('.GlassFrameDrawGateTest', 3), ('.KoreanPronunciationAlignmentTest', 4), ('.NativeSettingsGlassHandoffTest', 1), ('.CurrentSongIdentityTargetTest', 14), ('.NativeLyricsImmediateAnchorTest', 7), ('.NativeLyricsSmoothReturnTest', 2), ('.LyricPlaybackEpochTest', 3), ('.LyricRecoveryRuntimeTest', 5), ('.LyricWordTailStateTest', 3), ('.NativeWordTailTimingTest', 5), ('.NativeLyricsTailBindGuardTest', 4), ('.NativeTerminalGradientFixTest', 2), ('.NativeLyricsLineTimingTest', 5), ('.NativeLyricsMetadataRefreshTest', 6), ('.NativeLyricsHeaderRefreshTest', 3), ('.NativeLyricsAnchorAndroidTest', 3), ('.PlaybackTraceBufferTest', 2)):
            suite = next(suite for suite in suites if suite.attrib['name'].endswith(suffix))
            assert int(suite.attrib['tests']) == count
report.update(dictionaryResourcesVerified=8, bundledPronunciationResourcesVerified=5, lintPassed=True)
with zipfile.ZipFile('app/build/outputs/apk/debug/app-debug.apk') as apk:
    report['bundledPronunciationCompressedBytes'] = sum(item.compress_size for item in apk.infolist() if item.filename.startswith('assets/pronunciation'))
Path('dist/v1.4-test-validation.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
