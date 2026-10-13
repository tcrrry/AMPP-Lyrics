"""Package NPatch arm64/xxxhdpi outputs as an APKS archive on any OS."""

import argparse
import hashlib
from pathlib import Path
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input_directory", type=Path)
    parser.add_argument("output_file", type=Path)
    args = parser.parse_args()
    entries = []
    for pattern, name in (
        ("origin-*-npatched.apk", "base.apk"),
        ("split_config.arm64_v8a-*-npatched.apk", "split_config.arm64_v8a.apk"),
        ("split_config.xxxhdpi-*-npatched.apk", "split_config.xxxhdpi.apk"),
    ):
        matches = list(args.input_directory.glob(pattern))
        if len(matches) != 1 or not matches[0].is_file():
            parser.error(f"Expected one file matching {pattern}; found {len(matches)}")
        entries.append((matches[0], name))
    args.output_file.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output_file, "x", compression=zipfile.ZIP_STORED) as archive:
        for path, name in entries:
            archive.write(path, name)
    with args.output_file.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    checksum = Path(str(args.output_file) + ".sha256")
    checksum.write_text(f"{digest}  {args.output_file.name}\n", encoding="ascii")
    print(f"Created {args.output_file}\nSHA-256 {digest}")


if __name__ == "__main__":
    main()
