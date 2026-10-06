"""Record verified module tests for each formal v1.6 package identity."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('output', type=Path)
args = p.parse_args()
report = {}
for module in ('app', 'glass'):
    suites = [ET.parse(path).getroot() for path in Path(module, 'build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
    assert suites, f'Missing {module} test results'
    totals = {key: sum(int(s.get(key, 0)) for s in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
    assert totals['tests'] >= (1307 if module == 'app' else 22), totals
    assert all(totals[key] == 0 for key in ('failures', 'errors', 'skipped')), totals
    report[module] = totals
    if module == 'app':
        for suffix, count in (('.TcrrryGlowSettingsAndroidTest', 2), ('.LyricsSettingsSeparationTest', 2),
                              ('.LyricGlowScopeTest', 5), ('.NativeSettingsColdStartTest', 1)):
            suite = next(s for s in suites if s.get('name', '').endswith(suffix))
            assert int(suite.get('tests', 0)) >= count
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report))
