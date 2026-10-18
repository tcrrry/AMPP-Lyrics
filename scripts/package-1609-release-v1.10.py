"""Produce v1.10 using the existing 1609 signature/content/coexist release gates."""
import importlib.util
import json
from pathlib import Path

spec = importlib.util.spec_from_file_location('release', Path(__file__).with_name('package-1609-release-v1.8.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)

if __name__ == '__main__':
    release.main(release_version='1.10', module_code=154, plugin_version='1.0.9')
    # The retained historical packager describes its original translation backend.
    # Update only this new release's report to the actual bundled SDK extensions.
    import sys
    output = Path(sys.argv[sys.argv.index('--output') + 1])
    report = output / 'v1.10-validation.json'
    value = json.loads(report.read_text())
    value['offlineTranslation'] = 'bundled SDK translation plugin 1.0.0; Chinese model included; no companion APK'
    value['pluginSourceBaseline'] = 'formal 1.0.9; preview.15 Korean/Cantonese alignment accepted by user; final integrated binaries require device acceptance'
    report.write_text(json.dumps(value, indent=2) + '\n')
