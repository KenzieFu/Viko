#!/usr/bin/env python3
"""Export the selected local OneVoice checkpoints. See docs/onevoice.md."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import tempfile
from types import SimpleNamespace

os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TRANSFORMERS_OFFLINE"] = "1"

import onnx
import torch
from torch import nn
from torch.nn import functional as F
from onnxruntime.quantization import QuantType, quantize_dynamic
from transformers import (
    AutoTokenizer, BertForSequenceClassification, M2M100Tokenizer,
    WhisperForConditionalGeneration, WhisperFeatureExtractor,
)
from transformers.convert_slow_tokenizer import SpmConverter

from prepare import MANIFEST_SHA


def verify_source(root, model):
    manifest = (root / "MANIFEST.json").read_bytes()
    if hashlib.sha256(manifest).hexdigest() != MANIFEST_SHA:
        raise ValueError("Expected the November OneVoice release manifest")
    path = model.relative_to(root).as_posix()
    expected = json.loads(manifest)[path]
    with model.open("rb") as f:
        if hashlib.file_digest(f, "sha256").hexdigest() != expected:
            raise ValueError(f"Checkpoint differs from the OneVoice manifest: {path}")


def dump(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def graph(module, args, path, inputs, outputs, axes=None):
    print(f"Exporting {path.name}", flush=True)
    with torch.inference_mode():
        torch.onnx.export(module.eval(), args, str(path), input_names=inputs,
                          output_names=outputs, dynamic_axes=axes,
                          opset_version=17, dynamo=False)


def quantize(src, dst):
    print(f"Quantizing {dst.name}", flush=True)
    # Leave Conv and Gather in float. Dynamic ConvInteger is unavailable on
    # the mobile CPU provider, and quantized embeddings alter token inputs.
    quantize_dynamic(str(src), str(dst), weight_type=QuantType.QInt8,
                     op_types_to_quantize=["MatMul"],
                     extra_options={"MatMulConstBOnly": True})


class VariableWhisperEncoder(nn.Module):
    """Whisper inference layers with a position slice instead of fixed padding."""
    def __init__(self, encoder):
        super().__init__()
        self.encoder = encoder

    def forward(self, inputs, return_dict=False):
        e = self.encoder
        hidden = F.gelu(e.conv2(F.gelu(e.conv1(inputs)))).transpose(1, 2)
        hidden = hidden + e.embed_positions.weight[:hidden.shape[1]]
        for layer in e.layers:
            hidden = layer(hidden, None, None)[0]
        return (e.layer_norm(hidden),)


class Encoder(nn.Module):
    def __init__(self, encoder, layers):
        super().__init__()
        self.encoder = encoder
        self.layers = layers

    def forward(self, inputs):
        hidden = self.encoder(inputs, return_dict=False)[0]
        cross = []
        for layer in self.layers:
            attn = layer.encoder_attn
            for proj in (attn.k_proj, attn.v_proj):
                cross.append(proj(hidden).reshape(1, -1, attn.num_heads, attn.head_dim).transpose(1, 2))
        return tuple(cross)


class DecoderStep(nn.Module):
    """One-token decode; self KV grows, cross KV is computed once by Encoder."""
    def __init__(self, model, whisper):
        super().__init__()
        self.decoder = model.model.decoder
        self.projection = model.proj_out if whisper else model.lm_head
        self.whisper = whisper
        pos = self.decoder.embed_positions
        self.register_buffer("positions", pos.weight.detach() if whisper else pos.weights.detach())

    @staticmethod
    def attend(query, attn, k, v):
        q = attn.q_proj(query).reshape(1, 1, attn.num_heads, attn.head_dim).transpose(1, 2)
        weights = torch.softmax((q * (attn.head_dim ** -0.5)) @ k.transpose(-1, -2), dim=-1)
        return attn.out_proj((weights @ v).transpose(1, 2).reshape(1, 1, -1))

    def forward(self, token, position, *cache):
        d = self.decoder
        x = d.embed_tokens(token) + F.embedding(position, self.positions)
        if not self.whisper and hasattr(d, "embed_scale"):
            x = d.embed_tokens(token) * d.embed_scale + F.embedding(position, self.positions)
        present = []
        for i, layer in enumerate(d.layers):
            attn = layer.self_attn
            y = layer.self_attn_layer_norm(x)
            k = attn.k_proj(y).reshape(1, 1, attn.num_heads, attn.head_dim).transpose(1, 2)
            v = attn.v_proj(y).reshape(1, 1, attn.num_heads, attn.head_dim).transpose(1, 2)
            old_k, old_v, cross_k, cross_v = cache[4*i:4*i+4]
            k, v = torch.cat((old_k, k), 2), torch.cat((old_v, v), 2)
            present.extend((k, v))
            x = x + self.attend(y, attn, k, v)
            x = x + self.attend(layer.encoder_attn_layer_norm(x), layer.encoder_attn, cross_k, cross_v)
            x = x + layer.fc2(layer.activation_fn(layer.fc1(layer.final_layer_norm(x))))
        return (self.projection(d.layer_norm(x))[0, 0], *present)


def export_seq2seq(model, whisper, tmp, out):
    d = model.model.decoder
    layers = len(d.layers)
    heads = d.layers[0].self_attn.num_heads
    width = d.layers[0].self_attn.head_dim
    cross = [f"cross_{i}" for i in range(2*layers)]
    past = [f"past_{i}" for i in range(2*layers)]
    present = [f"present_{i}" for i in range(2*layers)]
    input_data = torch.zeros((1, 80, 1000)) if whisper else torch.tensor([[34943, 35004, 42, 2]])
    if whisper:
        encoder = VariableWhisperEncoder(model.model.encoder)
        axes = {"inputs": {2: "frames"}, **{n: {2: "cross_frames"} for n in cross}}
    else:
        encoder = model.model.encoder
        axes = {"inputs": {1: "source"}, **{n: {2: "source"} for n in cross}}
    # Keep Whisper's encoder in float32. With a shorter input the int8
    # encoder changed a reference word on Android; float32 retained parity.
    graph(Encoder(encoder, d.layers), (input_data,), out / "encoder.onnx",
          ["inputs"], cross, axes)
    cache = tuple(torch.zeros(1, heads, length, width)
                  for _ in range(layers) for length in (2, 2, 5, 5))
    inputs = [name for i in range(layers) for name in (*past[2*i:2*i+2], *cross[2*i:2*i+2])]
    axes = {**{n: {2: "past"} for n in past}, **{n: {2: "source"} for n in cross},
            **{n: {2: "present"} for n in present}}
    graph(DecoderStep(model, whisper), (torch.tensor([[2]]), torch.tensor([[3]]), *cache),
          (tmp if whisper else out) / "decoder.onnx",
          ["token", "position", *inputs], ["logits", *present], axes)
    if whisper:
        quantize(tmp / "decoder.onnx", out / "decoder.onnx")
    dump(out / "shape.json", {"layers": layers, "heads": heads, "width": width})


class DynamicMatMul(torch.autograd.Function):
    @staticmethod
    def forward(ctx, x, weight, scale, zero):
        # Trace with float values; the exported graph uses the original INT8
        # matrix and a dynamic per-tensor quantizer for the input.
        return x @ ((weight.float() - zero.float()) * scale)

    @staticmethod
    def symbolic(g, x, weight, scale, zero):
        q, xscale, xzero = g.op("DynamicQuantizeLinear", x, outputs=3)
        accum = g.op("MatMulInteger", q, weight, xzero, zero)
        return g.op("Mul", g.op("Cast", accum, to_i=1), g.op("Mul", xscale, scale)).setType(
            x.type().with_sizes([None, None, weight.type().sizes()[-1]])
        )


class ExportLinear(nn.Module):
    def __init__(self, linear):
        super().__init__()
        weight, bias = linear._weight_bias()
        self.register_buffer("weight", weight.int_repr().t().contiguous())
        self.register_buffer("scale", torch.tensor(weight.q_scale(), dtype=torch.float32))
        self.register_buffer("zero", torch.tensor(weight.q_zero_point(), dtype=torch.int8))
        self.register_buffer("bias", bias)

    def forward(self, x):
        y = DynamicMatMul.apply(x, self.weight, self.scale, self.zero)
        return y if self.bias is None else y + self.bias


def unpack_linears(module):
    for name, child in module.named_children():
        if isinstance(child, torch.ao.nn.quantized.dynamic.Linear):
            setattr(module, name, ExportLinear(child))
        else:
            unpack_linears(child)


def export_mt(src, tmp, out):
    tok = M2M100Tokenizer.from_pretrained(src, local_files_only=True)
    # Preserve the exact SentencePiece model IDs; map to the trimmed M2M
    # embedding IDs explicitly. They are not guaranteed to be identical.
    fast = SpmConverter(SimpleNamespace(vocab_file=str(src / "sentencepiece.bpe.model"))).converted()
    fast.save(str(out / "tokenizer.json"))
    mapping = [tok.convert_tokens_to_ids(fast.id_to_token(i)) for i in range(fast.get_vocab_size())]
    inverse = {str(m): i for i, m in enumerate(mapping) if m >= 4}
    for text in ("현장에 불이 났어요!", "Kiểm tra thiết bị.", "Mẫu này đang được đưa nhanh lên.", "물량이 급합니다."):
        assert [mapping[i] for i in fast.encode(text).ids] == tok.encode(text, add_special_tokens=False), text
    vocab = tok.get_vocab()
    suppress = [i for p, i in vocab.items() if any("぀" <= c <= "ヿ" or "㐀" <= c <= "鿿" for c in p)]
    dump(out / "tokens.json", {
        "to_model": mapping, "to_piece": inverse, "suppress": suppress + list(range(35003, 35008)),
        "languages": {lang: tok.get_lang_id(lang) for lang in ("vi", "ko")},
        "tags": json.loads((src / "tags.json").read_text()),
    })
    # Only load the verified, user-supplied checkpoint. It contains a pickled
    # torch Module; never use this exporter on a downloaded arbitrary .pt.
    torch.backends.quantized.engine = torch.backends.quantized.supported_engines[-1]
    model = torch.load(src / "model_int8.pt", map_location="cpu", weights_only=False).eval()
    model.config._attn_implementation = "eager"
    unpack_linears(model)
    export_seq2seq(model, False, tmp, out)


class Tagger(nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, embeddings, mask):
        return self.model(inputs_embeds=embeddings, attention_mask=mask, return_dict=False)[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--stage", choices=["vi", "ko", "tagger", "mt"], required=True)
    args = parser.parse_args()
    torch.set_num_threads(4)
    root = args.source.resolve()
    out = args.output.resolve() / args.stage
    out.mkdir(parents=True, exist_ok=True)
    models = root / "models"
    with tempfile.TemporaryDirectory(prefix="onevoice-export-") as scratch:
        tmp = Path(scratch)
        if args.stage == "vi":
            src = models / "whisper-vi-allin"
            verify_source(root, src / "model.safetensors")
            model = WhisperForConditionalGeneration.from_pretrained(src, attn_implementation="eager")
            export_seq2seq(model, True, tmp, out)
            extractor = WhisperFeatureExtractor.from_pretrained(src)
            extractor.mel_filters.T.astype("<f4").tofile(out / "mel_filters.bin")
            shutil.copyfile(src / "tokenizer.json", out / "tokenizer.json")
            shutil.copyfile(src / "generation_config.json", out / "generation_config.json")
        elif args.stage == "tagger":
            src = models / "viko_tagger_minilm"
            verify_source(root, src / "model.safetensors")
            model = BertForSequenceClassification.from_pretrained(src, attn_implementation="eager")
            embedding = model.bert.embeddings.word_embeddings.weight.detach()
            embedding.numpy().astype("<f4", copy=False).tofile(out / "embeddings.f32")
            dump(out / "embeddings.json", {"rows": embedding.shape[0], "width": embedding.shape[1]})
            graph(Tagger(model), (torch.zeros(1, 64, embedding.shape[1]), torch.ones(1, 64, dtype=torch.long)),
                  tmp / "tagger.onnx", ["embeddings", "mask"], ["logits"])
            quantize(tmp / "tagger.onnx", out / "tagger.onnx")
            tok = AutoTokenizer.from_pretrained(src)
            assert tok.pad_token_id == 1
            shutil.copyfile(src / "tokenizer.json", out / "tokenizer.json")
        elif args.stage == "mt":
            src = models / "m2m-finetuned-trimmed-int8"
            verify_source(root, src / "model_int8.pt")
            export_mt(src, tmp, out)
        else:
            src, = models.glob("*/onnx/export_report.json")
            for name in ("encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt", "export_report.json"):
                verify_source(root, src.parent / name)
                if name == "encoder.onnx":
                    # Exact same tensors, but keep the large weights out of the
                    # protobuf so ORT can map them directly at session startup.
                    (out / "encoder.weights").unlink(missing_ok=True)
                    onnx.save_model(onnx.load(src.parent / name), out / name,
                                    save_as_external_data=True, all_tensors_to_one_file=True,
                                    location="encoder.weights", size_threshold=4096)
                else:
                    shutil.copyfile(src.parent / name, out / name)


if __name__ == "__main__":
    main()
