# Edge AI pipeline — implementation plan

Live working document. Tick boxes as phases land; keep the "Decisions" and "Traps"
sections current, because they are what stops a later session re-deriving them wrong.

Everything runs on the phone. No cloud, no network calls, at any stage.

## Direction

- **ESP32 badge = Vietnamese worker.** Speaks VI, hears VI.
- **Manager phone = Korean supervisor.** Speaks KO, hears KO.

Both directions are in scope.

```
Flow A  worker -> supervisor    VI audio -> VI text -> <TAG> -> KO text -> KO speech
Flow B  supervisor -> worker    KO audio -> KO text -> <TAG> -> VI text -> VI speech -> badge
```

## The models

All four live in `/Users/kenziefubrianto/Viko-Ai/onevoice_e2e/models/`, all in PyTorch
format, none usable on Android until exported. That repo is the source of truth; the
converted copies in `~/Downloads` are **out of scope** and must not be used.

| Model | Stage | Used by | fp32 | int8 target |
|---|---|---|---|---|
| `whisper-vi-all` | ASR — VI speech to VI text | Flow A | 924 MB | ~450 MB |
| `whisper-ko-v8` | ASR — KO speech to KO text | Flow B | 927 MB | ~450 MB |
| `viko_tagger_minilm` | tagger — text to SOS/BUSY/ERROR/ROUTINE | both | 470 MB | ~120 MB |
| `nllb600m-FT-INT8` | MT — VI<->KO, one model both ways | both | 1.6 GB | ~1.8 GB * |

\* NLLB gets *bigger* than its source: the fp32 checkpoint stores int8 weights packed,
while the ONNX export writes encoder + decoder + decoder_with_past as three separate
graphs. See the merged-decoder note under Decisions.

## Where the models live

**Not in this repo, and not in the APK.** "Edge" means inference happens on the phone with
no network; it says nothing about how the files got there. 2.78 GB in `assets/` would mean
a 2.78 GB APK — Gradle will not practically build it, Play caps the base artifact at
200 MB, and git would choke.

They are built on a workstation by `Viko-Ai/onevoice_e2e/export_onnx.py` and pushed once:

```
./scripts/push-models.sh              # all four
./scripts/push-models.sh nllb tagger  # just these
```

Landing at `/sdcard/Android/data/com.example.viko/files/models/<name>/`, which is exactly
`context.getExternalFilesDir(null)` from Kotlin — app-scoped, writable by `adb push` with
no root, and deleted on uninstall (so re-push after a fresh install).

| on device | |
|---|---|
| `models/nllb/` | 1.8 GB |
| `models/whisper-vi/` | 451 MB |
| `models/whisper-ko/` | 451 MB |
| `models/tagger/` | 134 MB |
| | **2.78 GB** |

`onnx/nllb-fp32/` is a 3.3 GB dequantized intermediate the export needs. It never goes to
the phone, and the push script excludes it.

**A phone with no models must still work.** Every stage reports not-ready and the app falls
back to relaying badge speech untranslated — a working voice link, just not a translated
one. That is also the shipping default, since nothing here runs until someone pushes 2.78 GB
by hand.

For production rather than demo, the options are Play Asset Delivery (needs the Play Store)
or a sideloaded archive unpacked on first run. Neither is worth building until the app
works with `adb push`.

That number is the largest unretired risk in this plan. If it does not fit the target
device, vocabulary pruning on NLLB (256k -> ~40k rows) is the first lever, worth roughly
600-700 MB across the three graphs.

There is no TTS model. `pipeline.py`'s `load_tts()` is a stub reading "Not built."
Android's built-in `TextToSpeech` fills that gap in both directions.

## Decisions already made

- **Fresh implementation.** `feat/edge-ai-pipeline` is NOT merged. Its
  `TranslationPipeline.kt` is worth reading for the KV-cache handling, but the code here
  is written new.
- **TTS is Google's built-in `TextToSpeech`**, both directions. No TTS model ships.
- **ASR is our own Whisper fine-tunes, both directions.** Android's `SpeechRecognizer`
  was considered for Korean and rejected — keeps recognition off Google language packs.
- **Tee the audio, never intercept it.** `BadgeAudioServer.play()` is left alone; the
  utterance buffer accumulates alongside it. Every failure (no models, ASR throws, TTS
  voice missing) degrades to exactly today's working voice link.
- **Lazy-load each stage**, as `cascade.py` does. A phone that only ever hears badge
  audio never maps the Korean ASR's 450 MB.
- **Do NOT use the merged decoder.** It looks like the obvious size win — one graph
  instead of two near-duplicates — and it is backwards with this toolchain.
  `quantize_dynamic` skips initializers inside the `If` subgraphs that implement the merge,
  so `decoder_model_merged.onnx` stays fp32 and ends up *larger* than the quantized
  unmerged pair (measured on PhoWhisper: 738 MB merged vs 356 MB for the pair). Export the
  pair, quantize it, delete the merged graph. The real NLLB size win is vocabulary pruning
  — the 256k-row embedding table is ~40% of the model.
- **Greedy decode everywhere** (`num_beams=1`), matching the reference. Beam-5 costs
  3.3x on ASR and 1.32x on MT for 1-2 WER / 0.72 chrF++.

## Phases

### Phase 0 — exports  (runs in `Viko-Ai`, nothing lands in this repo)

- [x] **0a** `uv venv --python 3.12` in `onevoice_e2e/` + torch, transformers==4.45.2,
      optimum[exporters], onnx, onnxruntime, sentencepiece, soundfile, scikit-learn.
      **Done.** Lives at `Viko-Ai/onevoice_e2e/.venv`. Three version traps, all of which
      produce a working-looking install that cannot export:
      - Python 3.12 specifically — optimum's export breaks on the machine default 3.14
        (3.14 made `functools.partial` a descriptor).
      - **`optimum` must be pinned to the 1.x line** (`optimum[exporters]==1.23.3`). On
        2.x the exporter moved to a separate package and `[exporters]` is a dead extra
        that only warns.
      - **torch must be pinned to 2.5.1.** 2.13 routes `torch.onnx.export` through the
        dynamo exporter, which names external-weight files differently than optimum
        1.23.3 expects. Also install `onnxscript`, which torch needs but does not declare.
- [x] **0b** `nllb600m-FT-INT8` -> dequant to fp32 -> ONNX -> int8. **Done**, via
      `Viko-Ai/onevoice_e2e/export_onnx.py nllb --selftest`. Output: `onnx/nllb`,
      **1894 MB** (encoder 419, decoder 732, decoder_with_past 707).
      Encoder parity against torch: max |Δ| 0.066, 1.96% of peak activation.
      Notes for anyone re-running it:
      - 193 Linear layers dequantized, matching the count the reference documents.
      - The fp32 intermediate is 3.51 GB / 878M params, not 615M: weight tying did not
        survive the pickle, so `lm_head.weight` is a second 262M copy of the embedding.
      - `lm_head.bias` appears in the fp32 checkpoint and is dropped on load. It is all
        zeros — `quantize_dynamic` gives every QLinear a bias slot and M2M100's lm_head
        has none. Dropping it is a no-op.
      - External-weight files are named `<graph>.onnx_data` by optimum but `<graph>.onnx.data`
        by the quantizer. Match by prefix, never by a fixed suffix.
- [x] **0c** both Whispers -> ONNX int8. **Done** — `onnx/whisper-vi` and
      `onnx/whisper-ko`, **451 MB each** (encoder 92, decoder 195, decoder_with_past 180).
      Byte-identical sizes, as expected from identical architectures.
      `whisper-vi-all`'s missing `tokenizer.json` was generated during export (3.6 MB).
- [x] **0d** `viko_tagger_minilm` -> ONNX int8. **Done** — `onnx/tagger`, 113 MB graph
      + 21 MB tokenizer. Parity checked against torch on four real utterances in both
      languages, all matching, and `vi_sample`'s transcript returns `BUSY` exactly as the
      README fixture says. Writes `labels.json` beside the model so Android reads the tag
      order from a file rather than a Kotlin literal that can drift.
      **The graph requires a `token_type_ids` input that the tokenizer never produces.**
      The checkpoint is a `BertForSequenceClassification` (segment ids) paired with an
      `XLMRobertaTokenizer` (no segment concept); torch defaults them to zeros, ONNX makes
      them mandatory. Feed a zero tensor shaped like `input_ids`.
- [x] **0e** golden fixtures. **Done** — `Viko-Ai/onevoice_e2e/fixtures/golden.json`,
      both directions, carrying `asr` / `asr_normalized` / `tag` / `mt_input` / `mt` per
      stage. `vi_sample.wav` reproduces the README's documented output **exactly**, which
      is what confirms this venv reproduces the reference environment.
      `ko_sample.wav` differs slightly from the README (`무낭이` where the README has
      `모냥이`, and a different Vietnamese rendering). That is the direction the README
      already documents as broken — the ASR mishears the verb and everything downstream
      compounds it, so a marginal transcription flips on small numerical differences.
      Tag still matches. **Validate Android against the vi2ko fixture; treat ko2vi as
      indicative only.**

### Phase 1 — MT on device  <- go/no-go

- [x] `data/ai/TranslationPipeline.kt`, written fresh. ONNX Runtime + DJL tokenizer,
      greedy, KV cache carried between steps, cross-KV computed once per utterance.
- [x] `translate(text, src, tgt, tag)` — both directions off one set of weights.
- [x] Prepends `"<TAG> "`. Caps at 200 new tokens, source truncated at 256.
- [x] Language ids read from `tokenizer_config.json`.
- [x] Deps wired: `onnxruntime-android:1.22.0`, `ai.djl.huggingface:tokenizers:0.33.0`,
      and `ai.djl.android:tokenizer-native:0.33.0` as `runtimeOnly` — the native `.so`
      only ships in the Android artifact, and without it the tokenizer throws
      `UnsatisfiedLinkError` at runtime while compiling perfectly.
- [x] Wired into `AppContainer` as `translationPipeline`. Constructed eagerly: the
      constructor only resolves a path, and every ONNX session inside is `by lazy`, so a
      phone with no models never opens one.
- [x] `app/src/androidTest/.../TranslationPipelineTest.kt` — asserts the golden vi2ko
      fixture **exactly**, smoke-tests ko2vi, and logs cold and warm latency under the
      `VikoBench` logcat tag. Skips (does not fail) when no models are pushed.
- [x] `:app:compileDebugKotlin` and `:app:compileDebugAndroidTestKotlin` both clean.
- [x] **Benchmarked on real hardware. GO.**

**Device: Xiaomi Redmi 13 (23124RA7EO), Snapdragon SM6225 / 685, 8 cores, 7.8 GB RAM,
Android 15, arm64-v8a.** A mid-range 6nm part with no usable NPU — deliberately not a
flagship, and notably weaker than the QCS6490 in `ANDROID.md`.

| | measured | reference |
|---|---|---|
| MT warm median | **1062 ms** (1062 / 1059 / 1068) | 1.22 s on M-series MPS |
| MT cold, incl. mapping 1.8 GB | 10.3 s | — |
| Tagger warm | 81–105 ms | ~6 ms desktop CPU |
| tag + MT per utterance | **~1.15 s** | — |

The phone beats the desktop reference on MT. That retires the biggest risk in the plan:
the remaining budget for ASR and TTS is real, not hypothetical. Cold start is the cost to
watch — 10 s of graph mapping, which is why the sessions must be opened once and held,
never per-utterance.

```
./scripts/push-models.sh nllb
./gradlew :app:connectedDebugAndroidTest --tests '*TranslationPipelineTest*'
adb logcat -s VikoBench
```

### Phase 2 — tagger

- [x] `data/ai/TagClassifier.kt`. ONNX + DJL tokenizer (XLM-Roberta, Unigram).
- [x] `asrNormalize` runs **inside** `classify()`, matching the reference's `predict()`.
      Callers pass the raw transcript; MT still gets the unnormalised text.
- [x] Fixed `[1, 64]` input, padded with `<pad>` = 1, plus the zero `token_type_ids`
      the graph requires.
- [x] Tag order read from `labels.json` beside the model — **not** a Kotlin constant.
      The checkpoint names its classes `LABEL_0..LABEL_3` and carries no mapping, so a
      literal here could drift from the export with no symptom but wrong register.
- [x] Wired into `AppContainer` as `tagClassifier`, separate from the translator so the
      two can be pushed and fail independently.
- [x] `app/src/test/.../AsrNormalizeTest.kt` — 8 JVM tests, no device needed, pinned
      against `pipeline.asr_normalize` run directly. **All passing.**
- [x] `app/src/androidTest/.../TagClassifierTest.kt` — the same four utterances the
      Python export verified, two per language. Skips without a pushed model.
- [x] Instrumented test **passing on device**: all 5 tests, all four tags matching the
      reference in both languages (`SOS`/`BUSY` Vietnamese, `SOS`/`ROUTINE` Korean).

One thing the JVM test caught: `asr_normalize` does **not** collapse interior
whitespace. `"불이야! 대피해!"` normalises to `"불이야  대피해"` with two spaces — the `!`
becomes a space and the one already there stays. Tidying that to a single space would
stop matching what the tagger was trained on.

### Phase 3 — ASR

- [x] `data/ai/LogMel.kt` — log-mel in Kotlin, since it is not in the graph. Pinned to
      `WhisperFeatureExtractor` by `LogMelTest` (6 tests, passing on device): matches the
      reference mean/std/min/max to full float precision.
- [x] `data/ai/WhisperAsr.kt`, parameterized by language.
- [x] Forced prefix `<|vi|>`(50278) / `<|ko|>`(50264) + `<|transcribe|>`(50359) +
      `<|notimestamps|>`(50363). Never auto-detect.
- [x] `WhisperAsrTest` passing on device: **CER 0.032** against the reference transcript
      (one word, `đục` -> `đợt`), well inside the reference's own 4.14 CER on clean audio.
- [x] `data/ai/SpeechPipeline.kt` — the port of `Cascade.run()`, with its LRU cache keyed
      on the normalised transcript and lazy per-language ASR.
- [x] Korean encoder re-exported dynamic and pushed. On-device set is now **2.8 GB**.

**Measured, and how it got there.** First working version was **13.7 s** — unusable. Three
changes took it to **2.19 s**:

| | encoder | decode | total |
|---|---|---|---|
| as first written (2 threads, 30 s window) | 11.5 s | 2.0 s | 13.7 s |
| 4 threads | 6.0 s | 1.7 s | 7.8 s |
| + variable window | **1.27 s** | **0.88 s** | **2.19 s** |

- **Threads 2 -> 4.** The encoder is 85% of ASR and parallelises well on 8 cores. Kept
  below the core count so badge audio playback is not starved.
- **Variable-length encoder.** `export_encoder_dynamic.py` re-exports with a dynamic time
  axis; `LogMel.framesFor` then sizes the window to the audio. optimum bakes in
  `[batch, 80, 3000]`, so a 4.6 s utterance paid for 30 s. Attention is quadratic in
  length, hence 10x on the encoder alone.
- **The mel filterbank is sparse.** Mel filters are triangles, so 80 x 201 x frames is
  mostly multiplying by zero. Walking only the non-zero span halved mel extraction, 614 ms
  -> 319 ms, bit-identical output.

### Whole cascade, measured on the Redmi 13

| stage | warm |
|---|---|
| ASR (incl. mel) | 2.19 s |
| tagger | 0.09 s |
| MT | 1.06 s |
| **total** | **~3.3 s** |

The Python reference is 1.94 s on an M-series Mac. TTS is still to come but is Google's and
runs on its own thread.

### Phase 4 — the seam

- [x] Utterance buffer in `BadgeAudioServer`, per `device_id`, flushed on `ptt_end` via
      an `onUtterance` callback. Capped at 30s so a lost `ptt_end` cannot grow it without
      bound.
- [x] Driven by `ptt_start`/`ptt_end` from `BadgeRegistry`, **not** `IDLE_TIMEOUT_MS` —
      that is a clock-drift heuristic and a pause in speech is not the end of a turn.
- [x] **Tee, not intercept.** `play()` is untouched and still gets every chunk the moment
      it arrives, so the live voice link survives any failure downstream.
- [x] Silence gate in `SpeechPipeline`, before ASR. Whisper does not return empty for
      silence, it **invents** — fed one second of zeros this build produced
      *"một tình yêu thích nhất là tình yêu thích nhất là tình yêu."* Speaking words
      nobody said is the worst failure available here. The threshold is deliberately low
      (RMS 200): it catches a dead mic or a tapped button, not a quiet voice under
      factory noise.

### Phase 5 — output (Flow A complete)

- [x] `data/ai/Speaker.kt` — `TextToSpeech` ko-KR, `QUEUE_FLUSH` so a newer utterance
      interrupts a stale one, `USAGE_MEDIA` so it comes out of the loudspeaker rather
      than the earpiece. Language checked at construction and exposed as `status`, not
      discovered on first use: a missing voice fails **silently**, which on this channel
      is the worst behaviour available.
- [x] `BadgeRegistry` consumes `onUtterance`, runs the cascade on `Dispatchers.Default`
      (CPU-bound inference, and it must not compete with audio for IO workers), persists
      a `TranslationMessage` and speaks the Korean. No schema change needed.
- [x] `DirectCallScreen` shows the transcript. `TranscriptCard` leads with the Korean and
      keeps the Vietnamese underneath — the original is the only way to notice the
      translation went wrong, and it does: ASR mishears and MT loses ~12 chrF++ on real
      ASR text.
      **Not gated on `SPEAKING`.** The old condition was
      `translation != null && isMemberTalking`, which could never fire: the cascade runs
      on the whole turn and lands seconds after the button is released.
      The tag is deliberately **not** shown. It exists to condition MT, and an `SOS`
      badge a supervisor might come to trust is worse than none when the reference
      measures 26 of 105 real emergencies missed.

### Phase 6 — Flow B

- [ ] Supervisor mic -> `whisper-ko-v8` -> tagger -> NLLB ko->vi.
- [ ] Google `vi-VN` TTS -> `synthesizeToFile` -> strip 44-byte WAV header -> resample to
      16 kHz -> 320-sample frames -> `BadgeAudioServer.sendTo()`.
- [ ] Read the synthesis rate from the WAV header; do not assume. Google TTS is typically
      22050 or 24000 Hz and the firmware is pinned to 16 kHz.
- [ ] `vi-VN` offline voice must be pre-installed on the deployed phone.

## Exported graph signatures

Read off the actual exports, not from docs. This is what the Kotlin has to feed.

**`onnx/tagger/model.onnx`** — 3 inputs, all `[batch, seq]` int64:
`input_ids`, `attention_mask`, **`token_type_ids`** (zeros — see 0d). Out: `logits`
`[batch, 4]`, indexed by `labels.json` = `["SOS", "BUSY", "ERROR", "ROUTINE"]`.

**`onnx/whisper-*/encoder_model.onnx`** — `input_features` `[batch, 80, 3000]` ->
`last_hidden_state`. The log-mel is NOT in the graph; Kotlin must produce that tensor.
The decoder pair mirrors NLLB's shape exactly, but with **12 heads** (`[batch, 12,
past_len, 64]`) and `d_model` 768.

**`onnx/nllb/encoder_model.onnx`** — `input_ids`, `attention_mask` -> `last_hidden_state`
`[batch, seq, 1024]`.

**`onnx/nllb/decoder_model.onnx`** — `encoder_attention_mask`, `input_ids`,
`encoder_hidden_states`. **49 outputs**: `logits` + 12 layers x 4
(`present.<i>.decoder.{key,value}` and `present.<i>.encoder.{key,value}`).

**`onnx/nllb/decoder_with_past_model.onnx`** — **50 inputs**: `encoder_attention_mask`,
`input_ids` `[batch, 1]`, and 12 layers x 4 `past_key_values.<i>.{decoder,encoder}.{key,value}`.
**25 outputs**: `logits` + 12 x 2 decoder `present` only.

Two things follow, and they are not optional:

- **KV cache tensors are rank 4: `[batch, 16, past_len, 64]`** — 16 heads, head_dim 64.
- **The step graph consumes encoder KV but never re-emits it.** The encoder `present.*`
  tensors from `decoder_model` must be carried unchanged through every decode step, while
  the decoder ones are replaced each step from the previous output. This is the
  "cross-KV once per utterance" rule enforced by the graph shape — there is no way to
  write the slow version by accident.

## Device gotchas — all of these cost real time, none are guessable

**1. `adb shell mkdir` creates directories the app cannot enter.** They land owned by uid
2000 (shell) as `drwxrws---`, giving "other" nothing, so the app's own uid cannot traverse
in. The pushed *files* are `-rw-rw-rw-` and would be readable; only the directories block.
The symptom is `isReady` staying false while `adb shell ls` cheerfully lists every file.
`scripts/push-models.sh` now ends with `chmod -R 777`.

**2. `./gradlew connectedAndroidTest` deletes the models.** It uninstalls both APKs, and
uninstall wipes `/sdcard/Android/data/<pkg>/`. Never run it after pushing. Install and
instrument by hand instead:

```
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
./scripts/push-models.sh nllb tagger
adb shell am instrument -w -e class com.example.viko.data.ai.TranslationPipelineTest \
    com.example.viko.test/androidx.test.runner.AndroidJUnitRunner
```

Also note `connectedDebugAndroidTest` rejects `--tests`; filtering uses
`-Pandroid.testInstrumentationRunnerArguments.class=` or `am instrument -e class`.

**3. DJL ships a native library without its C++ runtime.**
`ai.djl.android:tokenizer-native` includes `libdjl_tokenizer.so`, which links against
`libc++_shared.so` and does not bundle it. The failure surfaces as
`NoClassDefFoundError: ...jni.LibUtils` — misleading, because the real error is an
`UnsatisfiedLinkError` in the static initialiser one frame earlier. Fixed by vendoring the
NDK's `libc++_shared.so` into `app/src/main/jniLibs/arm64-v8a/`; see the README there.
ONNX Runtime is unaffected, it links its C++ runtime statically.

**4. int8 output is not bit-reproducible across CPUs.** See Traps #8.

**5. Running several tests that each open and close the 1.8 GB sessions crashes the
process.** Run one test class at a time. Production holds a single long-lived pipeline
from `AppContainer`, so this is a test-harness artifact rather than an app bug — but it is
a reminder that these sessions must never be opened per-utterance.

## The TTS trap that nearly shipped

**`setLanguage` returning success does not mean the phone can speak offline**, and this
is the single most dangerous finding in the project so far.

Google's engine reports a language as available on the strength of a **server-side**
voice, with no local data at all. `isLanguageAvailable` and `setLanguage` both return
success. On a desk with Wi-Fi everything works. On the factory floor — which has no
internet *by design* — synthesis fails with a bare `error status code: -4`, and the
supervisor's reply silently never reaches the badge.

Measured on the test device, which reported both languages available:

```
vi: 11 voice(s), all need a network (vi-vn-x-vic-server, vi-vn-x-vie-local, ...)
ko:  9 voice(s), all need a network (ko-kr-x-koc-network, ko-kr-x-kod-local, ...)
```

Note the entries named `-local`: advertised, never downloaded. So the check is not "is
there a voice" but "is there a voice that does not need a network" —
`Voice.isNetworkConnectionRequired` plus `KEY_FEATURE_NOT_INSTALLED`. That is what
`OfflineVoice.kt` does, and both `Speaker` and `VietnameseVoice` refuse to report Ready
without one.

**Deployment step, mandatory, and easy to forget:** every phone needs Korean *and*
Vietnamese voice data installed **while it still has internet**, before it goes to the
floor. `adb shell am start -a android.speech.tts.engine.INSTALL_TTS_DATA` opens the right
screen. Roughly 20-50MB per language, one time, then fully offline.

Before this check existed, `Speaker` reported Ready on a phone with no Korean voice and
Flow A's speech would have failed with nothing logged.

## Traps

Each of these is measured in the reference repo, not speculative.

1. **The tagger's label order exists only as a Python list literal.** `config.json` says
   `LABEL_0..LABEL_3` with no names. The mapping is `TAGS[argmax]` where
   `TAGS = ["SOS", "BUSY", "ERROR", "ROUTINE"]`. Get it wrong and MT still emits fluent
   Korean, just in the wrong register — it looks fine until you diff against the fixture.

2. **`asr_normalize` runs inside the tagger, not upstream of it.** Python's `predict()`
   normalizes its own input, so MT still receives the *unnormalized* transcript with the
   tag prefixed. Normalizing once and feeding both changes what MT sees.
   Regex: `[!?.,;:~…"'()\[\]]+` -> `" "` (a space, not empty), then `.lower().trim()`.
   Skipping it drops SOS recall from 0.676 to 0.486.

3. **NLLB language ids must come from `tokenizer.json`.** `kor_Hang` = 256098,
   `vie_Latn` = 256193, verified in this checkpoint. The sentencepiece fallback is off by
   one (`kor_Hang` 256099) and silently emits the wrong target language — observed output
   in Kurdish.

4. **MT input is literally `"<TAG> text"`**, e.g. `"<SOS> máy ép bị kẹt"`. Only safe for
   this fine-tune. On stock NLLB the tag leaked into the output 14/15 times.

5. **Never let Whisper auto-detect the language.** The direction is always known. Forcing
   it is a design requirement — Qualcomm's English-forced demo loop hallucinates on VI/KO.
   Also: fine-tuned checkpoints ship a `generation_config.json` missing `lang_to_id`, so
   set the forced prefix explicitly rather than trusting that file.

6. **Cross-attention K/V run once per utterance, not per token.** They depend only on the
   encoder output, which is fixed for the whole decode. Recomputing in the loop measured
   56.46 ms/step against 28.64 for byte-identical output.

7. **Attention masks are additive floats** — `0.0` keep, `-100.0` mask. Not booleans, and
   not `-3.4e38`; the large sentinel destroys the int8 range.

8. **Whisper will not stop without trailing silence.** Cut the window to exactly the
   speech and it never emits `<|endoftext|>` — it loops the same sentence to the token
   cap. Measured on the 4.6 s reference clip:

   ```
     +0.0s padding -> 220 tokens, ran away, sentence repeated 9 times
     +1.4s         ->  24 tokens, terminated, one word off
     +5.4s         ->  23 tokens, terminated, exact match with the reference
    +25.4s (full)  ->  23 tokens, terminated, exact match
   ```

   `LogMel.PAD_FRAMES` (5 s) is therefore load-bearing, not a safety margin. Everything
   past it buys only encoder time. `WhisperAsr` also stops when the last 12 tokens repeat
   the 12 before them — the padding fixes the cause, that bounds the damage.

9. **`quantize_dynamic` on Whisper's convolutions emits `ConvInteger`**, which ORT's CPU
   provider has no kernel for below 1.29 — the session fails to load rather than running
   slowly. Quantize `op_types_to_quantize=["MatMul"]`, as optimum does.

10. **Encoder activation drift is not a useful correctness signal under int8.** The
    optimum-exported encoder that transcribes at CER 0.032 measures **56%** drift against
    fp32 torch. Judge an export by the transcript, not by activation distance.

11. **int8 matmul is not bit-reproducible across CPUs, and it changes the words.**
   The golden fixture's `이 물건 급해요...` comes out of this phone as
   `이 물건 빨리 올라와요...` — same sentence shape and ending, one phrase different,
   diverging at generated token index 2 on a single marginal argmax.

   **This is not a port bug.** Verified by running the identical exported graphs through
   ONNX Runtime in Python, which reproduces the phone's output token for token
   (`532, 86777, 51465, 63188, ...`). Apple Silicon has the ARM dot-product and i8mm
   instructions; the Snapdragon 685's Cortex-A73/A53 cores do not, so int8 accumulation
   order differs and near-ties flip. Bumping ONNX Runtime 1.22 -> 1.29 on Android changed
   nothing, which rules the runtime version out.

   Consequence for testing: assert against a **closed set** of known-correct renderings,
   never a single string and never merely "is not blank". A wrong language id or a
   mishandled KV cache also yields fluent Korean, so the assertion has to stay tight
   enough to catch those while tolerating a single flipped token.

## Do not oversell

Carried from the reference repo. These are its own words about its own numbers.

- **`<SOS>` is not safety-grade.** 26 of 105 true emergencies missed. Never wire it to an
  alarm, an e-stop, or anything actuating.
- **`BUSY` is unreliable** — 7 examples in the test set.
- **Korean ASR is fine-tuned only on synthetic Apple TTS speech.** CER halves on held-out
  synthetic audio yet it still mistranscribed the one real human clip tested. Tuned for
  shouted factory commands, not conversation.
- **MT loses ~12 chrF++ on real ASR text** — trained on punctuated text, ASR emits almost
  none.
- **Long speech exceeds budget**: ~10 s of audio costs ~3 s on-device.
- Domain training audio was generated with Apple TTS, whose macOS SLA restricts it to
  non-commercial use. Regenerate through a commercial TTS before shipping or public demos.

## Validating a phase

The reference documents one end-to-end case, in `Viko-Ai/onevoice_e2e/README.md`:

```
input: samples/vi_sample.wav
ASR : hàng đục này đang gấp em làm nhanh tay hơn một chút được không.
TAG : <BUSY>
MT  : 이 물건 급해요. 조금 더 빨리 할 수 있어요.
```

Compare **stage by stage**, never end to end only. A tokenizer off-by-one or a scrambled
label order produces fluent, plausible Korean — the failure is invisible unless each stage
is diffed separately.
