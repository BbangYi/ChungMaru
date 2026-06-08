#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXTENSION_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="${1:-"$EXTENSION_DIR/dist"}"

VERSION="$(python3 - "$EXTENSION_DIR/manifest.json" <<'PY'
import json
import sys
from pathlib import Path

manifest = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
print(manifest.get("version", "0.0.0"))
PY
)"

OUTPUT_PATH="$OUTPUT_DIR/chungmaru-chrome-extension-$VERSION.zip"

mkdir -p "$OUTPUT_DIR"

python3 - "$EXTENSION_DIR" "$OUTPUT_PATH" <<'PY'
import sys
import zipfile
from pathlib import Path

extension_dir = Path(sys.argv[1]).resolve()
output_path = Path(sys.argv[2]).resolve()

excluded_dirs = {
    ".git",
    "dist",
    "node_modules",
    "scripts",
}
excluded_files = {
    ".DS_Store",
}

if output_path.exists():
    output_path.unlink()

with zipfile.ZipFile(output_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(extension_dir.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(extension_dir)
        if any(part in excluded_dirs for part in relative.parts):
            continue
        if path.name in excluded_files:
            continue
        archive.write(path, relative.as_posix())

print(output_path)
PY
