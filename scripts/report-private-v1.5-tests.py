"""Report actual CI results and package the independently buildable plugin source."""
from pathlib import Path
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
dist = root / 'dist'
dist.mkdir(exist_ok=True)
report = {'version': 'v1.5-test-r7', 'deviceVerified': False, 'tests': {}, 'lint': {},
          'pluginRegression': 6, 'pluginScope': 'spacing and optional short-unit smoothing; not full third-party migration',
          'pluginImport': 'Validated locally using the author plugin-runtime Store fixture; not repeated in CI'}
for module in ('app', 'glass'):
    stats = {key: 0 for key in ('tests', 'failures', 'errors', 'skipped')}
    for path in (root / module / 'build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
        suite = ET.parse(path).getroot()
        for key in stats:
            stats[key] += int(suite.get(key, '0'))
    assert stats['tests'] > 0 and stats['failures'] == stats['errors'] == stats['skipped'] == 0
    report['tests'][module] = stats
for module in ('app', 'glass', 'glass-lab'):
    issues = list(ET.parse(root / module / 'build/reports/lint-results-debug.xml').getroot())
    errors = sum(issue.get('severity') in ('Error', 'Fatal') for issue in issues)
    assert errors == 0
    report['lint'][module] = {'errors': errors, 'warnings': sum(issue.get('severity') == 'Warning' for issue in issues)}
plugin = root / 'plugins/tcrrry-lyrics'
shutil.copyfile(plugin / 'README.md', dist / 'Plugin-README.md')
with zipfile.ZipFile(dist / 'Tcrrry-Lyrics-Plugin-0.1.0-source.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in plugin.rglob('*'):
        if path.is_file() and not any(part in ('build', '.gradle') for part in path.relative_to(plugin).parts):
            archive.write(path, 'tcrrry-lyrics-plugin/' + str(path.relative_to(plugin)))
report['artifacts'] = {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
                       for path in dist.iterdir() if path.suffix in ('.apk', '.zip')}
(dist / 'validation-v1.5.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
