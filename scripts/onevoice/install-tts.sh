#!/usr/bin/env bash
# Install the prepared voice pack while retaining the phone's existing OneVoice models.
set -euo pipefail
if [[ $# != 1 ]]; then
  echo "Usage: bash $0 VOICE_PACK" >&2
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
if manifest["version"] != 1 or not manifest["files"]:
    raise SystemExit("Unsupported or empty voice pack")
for name, size in manifest["files"].items():
    file = root / name
    if not file.is_file() or file.stat().st_size != size:
        raise SystemExit(f"Voice pack is incomplete: {file}")
PY
"$adb_bin" shell pm path "$package" | grep -q '^package:'
"$adb_bin" shell am force-stop "$package"
"$adb_bin" shell "mkdir -p '$dest' && rm -rf '$dest/voices-upload' && mkdir '$dest/voices-upload'"
"$adb_bin" push "$pack/." "$dest/voices-upload/"
"$adb_bin" shell "set -e
    chmod 755 '$dest' && find '$dest/voices-upload' -type d -exec chmod 755 {} \;
    if [ ! -d '$dest/voices-v1' ] && [ -d '$dest/voices-old' ]; then
        mv '$dest/voices-old' '$dest/voices-v1'
    fi
    rm -rf '$dest/voices-old'
    if [ -d '$dest/voices-v1' ]; then mv '$dest/voices-v1' '$dest/voices-old'; fi
    if ! mv '$dest/voices-upload' '$dest/voices-v1'; then
        if [ -d '$dest/voices-old' ]; then mv '$dest/voices-old' '$dest/voices-v1'; fi
        exit 1
    fi
    rm -rf '$dest/voices-old'"
echo "Supertonic 3 and VieNeu v3 Turbo voice pack installed."
