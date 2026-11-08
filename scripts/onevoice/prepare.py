#!/usr/bin/env python3
"""Build a checked model pack and optional test inputs from the local OneVoice source."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import wave

MANIFEST_SHA = "154953926359a5efc73b9de9e379162d254fc6c80cc9cd5f4d545e8ac68d7dd2"


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def fixtures(source, output):
    output.mkdir(exist_ok=False)
    validation = json.loads((source / "validation/both_directions.json").read_text())["directions"]
    expected = []
    for file, name, direction in (("vi_sample", "vi2ko", "Worker"), ("vi_manual", None, "Worker"),
                                  ("ko_sample", "ko2vi", "Manager")):
        with wave.open(str(source / "samples" / f"{file}.wav"), "rb") as wav:
            if (wav.getnchannels(), wav.getsampwidth(), wav.getframerate()) != (1, 2, 16000):
                raise ValueError(f"{file} is not 16 kHz mono PCM16")
            (output / f"{file}.pcm").write_bytes(wav.readframes(wav.getnframes()))
        if name is None:
            # The third bundled recording is outside the saved bidirectional
            # report. These values are from the local Python reference run.
            text = ("lối thoát hiểm bị chặn hoặc khóa cửa sổ bị che chắn khiến người lao động "
                    "không thể thoát ra khỏi nhà máy khi xảy ra sự cố.")
            tag = "ROUTINE"
            translation = ("위험 출구가 막혀서 창문 잠금이 막혀 있어 노동자가 사고가 발생할 때 "
                           "공장에서 탈출할 수 없게 해요.")
        else:
            row = validation[name]
            text, tag, translation = row["asr"][0], row["tags"][0], row["mt"][0]
        expected.append({"file": f"{file}.pcm", "direction": direction, "transcript": text,
                         "tag": tag, "translation": translation})
    write_json(output / "expected.json", expected)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--fixtures", type=Path)
    args = parser.parse_args()
    source = args.source.resolve()
    manifest = source / "MANIFEST.json"
    if hashlib.sha256(manifest.read_bytes()).hexdigest() != MANIFEST_SHA:
        raise ValueError("Expected the November OneVoice release manifest")
    for rel, expected in json.loads(manifest.read_text()).items():
        with (source / rel).open("rb") as f:
            if hashlib.file_digest(f, "sha256").hexdigest() != expected:
                raise ValueError(f"Source file differs from its manifest: {rel}")
    args.output.mkdir(parents=True, exist_ok=False)
    exporter = Path(__file__).with_name("export.py")
    for stage in ("vi", "ko", "tagger", "mt"):
        subprocess.run([sys.executable, str(exporter), str(source), str(args.output), "--stage", stage], check=True)

    write_json(args.output / "pack.json", {"version": 1, "source": MANIFEST_SHA,
               "files": {f.relative_to(args.output).as_posix(): f.stat().st_size
                         for f in sorted(args.output.rglob("*")) if f.is_file()}})
    if args.fixtures:
        fixtures(source, args.fixtures)


if __name__ == "__main__":
    main()
