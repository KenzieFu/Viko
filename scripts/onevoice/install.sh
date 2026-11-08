#!/usr/bin/env bash
# ADB install without embedding or downloading model weights in the APK.
set -euo pipefail
if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 MODEL_PACK [TEST_FIXTURES]" >&2
  exit 2
fi
adb_bin="${ADB:-adb}"
package=com.example.viko
dest="/sdcard/Android/data/$package/files/models"
pack="$1"
python3 - "$pack" <<'PY'
import json
from pathlib import Path
import sys
root = Path(sys.argv[1])
manifest = json.loads((root / "pack.json").read_text())
assert manifest["version"] == 1
for name, size in manifest["files"].items():
    path = root / name
    if not path.is_file() or path.stat().st_size != size:
        raise SystemExit(f"Model pack is incomplete: {path}")
PY
"$adb_bin" shell pm path "$package" | grep -q '^package:'
"$adb_bin" shell am force-stop "$package"
# Use a staging directory so partial transfers can never activate translation.
"$adb_bin" shell "mkdir -p '$dest/onevoice-upload'"
"$adb_bin" push "$pack/." "$dest/onevoice-upload/"
"$adb_bin" shell "chmod 755 '$dest' &&
  find '$dest/onevoice-upload' -type d -exec chmod 755 {} \; &&
  rm -rf '$dest/onevoice-old' &&
  { [ ! -d '$dest/onevoice' ] || mv '$dest/onevoice' '$dest/onevoice-old'; } &&
  mv '$dest/onevoice-upload' '$dest/onevoice' &&
  rm -rf '$dest/onevoice-old'"
if [[ $# == 2 ]]; then
  "$adb_bin" shell "mkdir -p '$dest/onevoice-test'"
  "$adb_bin" push "$2/." "$dest/onevoice-test/"
  "$adb_bin" shell chmod 755 "$dest/onevoice-test"
fi
echo "OneVoice installed. Open Viko with Korean and Vietnamese offline voices installed."
