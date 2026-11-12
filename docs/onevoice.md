# OneVoice on Viko Android

In Direct Call, the manager speaks Korean and the Viko badge plays the Vietnamese reply. When an enrolled badge sends Vietnamese speech, the phone shows the Korean translation and plays the Korean voice. Translation begins when the speaker ends their turn. The badge sends and receives 16 kHz mono PCM16 over the factory Wi-Fi.

The phone uses the November OneVoice checkpoints: Vietnamese PhoWhisper, Korean Zipformer, MiniLM context tagging and one shared M2M translator. The tag controls translation tone; it never creates an SOS alert. The optional neural voice pack uses [Supertonic 3](https://huggingface.co/Supertone/supertonic-3) for Korean and [VieNeu v3 Turbo](https://huggingface.co/pnnbao-ump/VieNeu-TTS-v3-Turbo) with the author's Hải Đăng preset for Vietnamese. Both synthesize locally. Devices provisioned with Android offline voices can keep using them until the neural pack is installed. The phone and badge need to share a local Wi-Fi network.

In **Settings → AI translation**, switch translation on for Vietnamese and Korean or off for original voice. The app saves the selection and applies it to the next push-to-talk turn without a restart. The row shows when models or offline voices are missing.

## Provision a manager phone

1. Build and install the debug app on a 64-bit Android phone. Allow the microphone, local network and Bluetooth permissions when prompted, and pair the badge on the factory Wi-Fi.
2. Install the approximately 2.3 GiB OneVoice model pack and the 420 MB neural voice pack using USB debugging. For a model update, allow enough free space for a second copy while the transfer completes.
3. Open Viko and enable **Settings → AI translation**.

With the November `onevoice_pipeline` folder, Python 3.12, and `adb` on PATH:

```sh
python3.12 -m venv /tmp/viko-onevoice-export
source /tmp/viko-onevoice-export/bin/activate
python -m pip install -r scripts/onevoice/requirements.txt
python scripts/onevoice/prepare.py ~/onevoice_pipeline /tmp/viko-onevoice-models \
  --fixtures /tmp/viko-onevoice-fixtures
./gradlew :app:installDebug
scripts/onevoice/install.sh /tmp/viko-onevoice-models
python scripts/onevoice/prepare-tts.py /tmp/viko-neural-voices
bash scripts/onevoice/install-tts.sh /tmp/viko-neural-voices
```

The exporter checks the release manifest before opening the local checkpoints. The voice preparation script verifies pinned downloads and prepares the Vietnamese preset embeddings. The packs are separate from the APK and Git. The installers stage files before activating them. Set `ANDROID_SERIAL` when multiple devices are connected or `ADB` if the executable is not on PATH.

The Korean pack uses Sherpa-ONNX’s int8 conversion of Supertonic 3 under the upstream OpenRAIL-M model license included in the pack. The Vietnamese int8 model, [MOSS codec](https://huggingface.co/OpenMOSS-Team/MOSS-Audio-Tokenizer-Nano-ONNX), and [SEA-G2P](https://github.com/pnnbao97/sea-g2p/tree/v0.10.0) use Apache 2.0. The bundled 64-bit SEA-G2P libraries can be rebuilt with Android NDK r28c, Rust 1.89, and the upstream v0.10.0 source using `bash scripts/onevoice/build-phonemizer.sh ANDROID_NDK_DIR SEA_G2P_SOURCE_DIR`.

The current voice pack adds the incremental MOSS decoder and its state metadata. Rebuild and install the voice pack to enable within-sentence streaming for Vietnamese. Existing packs still play Vietnamese using the full decoder. Supertonic plays each Korean sentence as it becomes available. Viko sends UDP frames at the audio sample cadence and keeps speech replies ordered while the recognizer works on the next turn. Devices with enough memory prepare and retain both voices when Direct Call opens.

## Verify both directions

```sh
./gradlew :app:installDebugAndroidTest
scripts/onevoice/install.sh /tmp/viko-onevoice-models /tmp/viko-onevoice-fixtures
adb shell am instrument -w \
  -e class com.example.viko.data.ai.onevoice.OneVoiceInstrumentedTest \
  -e requireOneVoice true -e requireNeuralVoices true -e requireStreamingCodec true \
  -e prepareOneVoice true \
  com.example.viko.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s OneVoice OneVoiceTest VikoNeuralTts VikoTurn
```

The Android test checks the transcript, tag and translated text from the three bundled recordings against the Python reference. It repeats a clip in each direction to measure warm inference separately. `requireNeuralVoices` also requires both neural models to generate 16 kHz mono PCM. For an offline inference check, turn off Wi-Fi and mobile data. For a wearable test, use a local Wi-Fi network without internet, speak in both directions, listen to the phone and badge, and time from PTT release to first translated audio.

`VikoTurn` records time to text and first audio dispatch, with turn finalization, queue time, and time waiting for the recognizer or translator. The phone measures from the stop tap for manager speech and from the last received audio packet for badge speech. Dispatch precedes actual playback. `VikoNeuralTts` records first PCM and total generation time. Compare cold starts, warm uncached replies, and alternating directions separately.

Keep Android ONNX Runtime pinned until the reference tests pass on the replacement version. Opening Direct Call starts loading the shared translation models and the Vietnamese recognizer. Phones with sufficient free memory also load Korean ahead of the call. The first turn still waits for any models that are loading. Phones with limited free memory load the other recognizer when the direction changes. The 30-second capture cap applies in both directions. Model parity on a few clips does not establish translation accuracy for factory noise, accents or unfamiliar phrases.

Vietnamese recognition uses the float32 Whisper encoder with 3.2 seconds of trailing silence for turns up to ten seconds and five seconds for longer turns. It retries the full reference window if the decoder cannot finish. This reduces unnecessary attention work on short calls. The decoder reuses attention state at each token, and the tagger reads only the embedding rows used by the transcript. Exact transcripts reuse their recent translation in the same direction, and identical Vietnamese replies reuse their offline synthesized audio. These caches are bounded and cleared when the app process exits. The Korean encoder is the verified Zipformer export with its large tensors in a separate weights file.

Measured on the connected 8 GB Android 15 phone: 4.6 s warm for the short Vietnamese recording and 2.1 s warm for the Korean recording. The first turn and first switch to Korean took 15.6 s and 17.4 s respectively, including model loading but excluding TTS and delivery. The earlier full-window Vietnamese version took 10.2 s warm on that phone. All three recordings matched the Python transcript, context tag and translation on both Android targets. The ARM64 emulator also passed offline voice synthesis with Wi-Fi and mobile data disabled.

These are sample-level checks. The exported translator matched 11 of 12 additional saved reference texts in the host ONNX comparison. A live badge and the manager phone should be tested together before rollout; it adds Wi-Fi packet loss, microphone quality, TTS setup and speaker playback to the measurement.

On the 8 GB ARM64 emulator, preparing both languages and the shared translation models took 5.5 s. After preparation, the first short Vietnamese and Korean fixtures took 0.69 s and 0.45 s. Their repeats took 0.42 s and 0.24 s with translation reuse. Repeating Vietnamese synthesis for identical text took 2 ms after the first synthesis took 3.5 s. The earlier phone measurements used the previous pipeline.
