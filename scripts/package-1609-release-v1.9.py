"""Produce all four v1.9 forms using the unchanged 1609 host/coexist release gates."""
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location('release', Path(__file__).with_name('package-1609-release-v1.8.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)

if __name__ == '__main__':
    release.main(release_version='1.9', module_code=153, plugin_version='1.0.8')
