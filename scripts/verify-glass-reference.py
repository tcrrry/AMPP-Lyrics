"""Verify pinned upstream sources and declared AM++ patches without a reference checkout."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / "backdrop/upstream-sha256.json").read_text())
local_patches = json.loads((root / "backdrop/local-patches-sha256.json").read_text())
unexpected = local_patches.keys() - manifest.keys()
if unexpected:
    raise SystemExit("Unrecognized local Backdrop patches:\n" + "\n".join(sorted(unexpected)))
failed = []
for relative, upstream_hash in manifest.items():
    path = root / "backdrop" / relative
    expected = local_patches.get(relative, upstream_hash)
    if not path.is_file() or hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest() != expected:
        failed.append(relative)
if failed:
    raise SystemExit("Backdrop rendering source differs from its pinned hash:\n" + "\n".join(failed))
print(
    f"PASS: {len(manifest) - len(local_patches)} original Backdrop files match "
    "commit 65ab177e90e5c1d8c62e70cf7755841982da65f6; "
    f"{len(local_patches)} declared AM++ patch(es) match their pinned hashes"
)
