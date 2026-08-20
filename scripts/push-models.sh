#!/usr/bin/env bash
# Push the exported ONNX models onto a connected phone.
#
# The models are NOT in this repo and never will be: 2.78 GB does not go in an APK
# (Play caps the base at 200 MB) and does not go in git. They are built by
# Viko-Ai/onevoice_e2e/export_onnx.py and pushed here by hand.
#
# The app still runs without them -- every stage reports not-ready and the phone
# falls back to relaying badge speech untranslated, which is a working voice link.
#
#   ./scripts/push-models.sh              # everything
#   ./scripts/push-models.sh nllb tagger  # just these
#
# Uninstalling the app deletes all of it. Re-run this after a fresh install.
set -euo pipefail

SRC="${VIKO_MODELS_SRC:-$HOME/Viko-Ai/onevoice_e2e/onnx}"
PKG="${VIKO_PKG:-com.example.viko}"
DEST="/sdcard/Android/data/$PKG/files/models"

ADB="$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")"
[ -x "$ADB" ] || { echo "adb not found. Install platform-tools or set PATH." >&2; exit 1; }

[ -d "$SRC" ] || { echo "no exports at $SRC — run export_onnx.py first" >&2; exit 1; }

if [ "$("$ADB" devices | grep -c "device$")" -eq 0 ]; then
    echo "no device. Plug in a phone with USB debugging on, or start an emulator." >&2
    "$ADB" devices -l >&2
    exit 1
fi

# nllb-fp32 is the dequantized intermediate the export needs, not something the
# phone ever loads. Excluded even when the caller asks for everything.
MODELS=("${@:-nllb whisper-vi whisper-ko tagger}")
read -ra MODELS <<< "${MODELS[*]}"

echo "pushing to $DEST"
"$ADB" shell mkdir -p "$DEST"

for m in "${MODELS[@]}"; do
    [ -d "$SRC/$m" ] || { echo "  skip $m — not exported"; continue; }
    size=$(du -sh "$SRC/$m" | cut -f1)
    echo "  $m ($size) ..."
    # Trailing /. copies the CONTENTS into an existing dir rather than nesting
    # $m/$m on a re-push.
    "$ADB" shell mkdir -p "$DEST/$m"
    "$ADB" push "$SRC/$m/." "$DEST/$m/" >/dev/null
done

# Directories created over adb belong to uid 2000 (shell) with mode drwxrws---,
# which gives "other" nothing -- so the app, running as its own uid, cannot even
# traverse into them. The files land -rw-rw-rw- and would be readable; it is the
# directories that block. The symptom is isReady staying false while `adb shell ls`
# happily lists every file, so fix it here rather than rediscovering it each time.
"$ADB" shell chmod -R 777 "$DEST" 2>/dev/null || true

echo
echo "on device:"
"$ADB" shell du -sh "$DEST"/* 2>/dev/null || true
