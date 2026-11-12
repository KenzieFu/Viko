#!/usr/bin/env python3
"""Fetch pinned voice models and prepare the VieNeu speaker and embedding tables."""

import argparse
import hashlib
import json
import os
import subprocess
from pathlib import Path

import numpy as np


HF = "https://huggingface.co"
SUPER = f"{HF}/csukuangfj2/sherpa-onnx-supertonic-3-tts-int8-2026-05-11/resolve/cca5a0e6c96e1d2c720986bf7e75fcc81dee3ae4"
VIENEU = f"{HF}/pnnbao-ump/VieNeu-TTS-v3-Turbo/resolve/61b85e3d937fbbacb387714180e8182823512523/onnx_int8"
CODEC = f"{HF}/OpenMOSS-Team/MOSS-Audio-Tokenizer-Nano-ONNX/resolve/ceff0d0749bfb3fa2d61149794ec6feef0d1e1ae"
PRESET = ("https://raw.githubusercontent.com/pnnbao97/VieNeu-TTS/"
          "22dd2ef2a89bba24a77da6dbc84c75f7f4fda05b/src/vieneu/assets/voices_v3_turbo.json")
SEA = "https://github.com/pnnbao97/sea-g2p/releases/download/v0.10.0/sea_g2p.bin"
SUPER_LICENSE = f"{HF}/Supertone/supertonic-3/resolve/3cadd1ee6394adea1bd021217a0e650ede09a323/LICENSE"


def digest(path):
    sha = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            sha.update(chunk)
    return sha.hexdigest()


def download(url, path, sha):
    if path.is_file() and digest(path) == sha:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".part")
    subprocess.run(["curl", "-fLsS", "--retry", "3", url, "-o", str(temporary)], check=True)
    if digest(temporary) != sha:
        temporary.unlink()
        raise ValueError(f"Checksum mismatch for {url}")
    os.replace(temporary, path)


def prepare(output, cache):
    sources = json.loads(Path(__file__).with_name("tts-sources.json").read_text())
    stage = output.with_name(output.name + ".partial")
    stage.mkdir(parents=True, exist_ok=True)
    (stage / "pack.json").unlink(missing_ok=True)
    for group, base in (("supertonic", SUPER), ("vieneu", VIENEU), ("codec", CODEC)):
        for file, sha in sources[group].items():
            folder = "supertonic" if group == "supertonic" else "vieneu"
            download(f"{base}/{file}", stage / folder / file, sha)

    download(SEA, stage / "vieneu/sea_g2p.bin", sources["dictionary"])
    download(SUPER_LICENSE, stage / "supertonic/LICENSE",
             "0d944a9110fed9a9602d60e0423a272903e7bd21ab060490774efc77c2275e9f")
    download(f"{VIENEU}/vieneu_v3_heads.npz", cache / "heads.npz", sources["heads"])
    download(PRESET, cache / "voices.json", sources["presets"])
    voices = json.loads((cache / "voices.json").read_text())
    preset = voices["presets"][voices["default_voice"]]
    codes = preset["codes"]
    if len(codes) > 1 and codes[-1][0] == 455:
        codes = codes[:-1]

    with np.load(cache / "heads.npz", allow_pickle=False) as weights:
        text = weights["text_emb"]
        audio = weights["audio_emb"]
        xvector = np.asarray(preset["speaker_emb"], dtype=np.float32)
        anchor = weights["xvec_w"] @ xvector + weights["xvec_b"]
        anchor = (anchor - anchor.mean()) / np.sqrt(anchor.var() + weights["xvec_ln_eps"])
        anchor = anchor * weights["xvec_ln_w"] + weights["xvec_ln_b"]
        with (stage / "vieneu/embeddings.f32").open("wb") as destination:
            for array in (text, audio, anchor):
                array.astype("<f4").tofile(destination)
        (stage / "vieneu/preset.json").write_text(json.dumps({
            "name": voices["default_voice"],
            "audioVocabularySize": audio.shape[1],
            "codes": codes,
        }, ensure_ascii=False), encoding="utf-8")

    files = {str(file.relative_to(stage)): file.stat().st_size
             for file in stage.glob("*/*") if file.is_file() and not file.name.endswith(".part")}
    (stage / "pack.json").write_text(json.dumps({"version": 1, "files": files}, indent=2) + "\n")
    if output.exists():
        raise FileExistsError(f"Choose a new output directory: {output}")
    stage.rename(output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Choose a new output directory")
    prepare(args.output, args.output.with_name(args.output.name + ".cache"))
