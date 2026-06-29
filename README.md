# Portal TTS

Offline neural text-to-speech for Meta Portal devices, packaged as the companion TTS engine used by
[Immortal](https://github.com/starbrightlab/immortal).

Portal devices do not ship with a useful Google-backed system TTS stack. Portal TTS fills that gap by
registering as a normal Android `TextToSpeech` engine, so Immortal and other sideloaded Portal apps can
speak through the platform API without cloud services.

## Package

- App name: `Portal TTS`
- Android package: `com.portal.tts`
- Device target: Meta Portal / Portal+ / Portal Mini / Portal Go / Portal TV
- ABI: `arm64-v8a`
- Runtime: fully offline after model folders are installed

## Features

- Portal-branded launcher icon and settings UI.
- Unified voice picker across all installed model folders.
- Voice preview buttons.
- Recent generation history.
- Save/share generated WAV files.
- Model manager showing installed and missing model folders.
- Benchmark table that tests all installed voices and ranks realtime performance.
- Faster app startup: UI opens before the ONNX model finishes loading.
- Standard Android `TextToSpeech` integration for Immortal welcome speech, chimes, and spoken time.

## Installed model support

Model files are intentionally not bundled in the APK. Push model folders to:

```bash
adb push kokoro-multi-lang-v1_0/ /sdcard/Android/data/com.portal.tts/files/
adb push vits-piper-en_US-lessac-medium/ /sdcard/Android/data/com.portal.tts/files/
adb push vits-piper-ro_RO-mihai-medium/ /sdcard/Android/data/com.portal.tts/files/
```

The current catalog recognizes:

- Kokoro multilingual v1.0 English voices, US and UK speaker IDs.
- Piper English Lessac.
- Piper Romanian Mihai.

To add another voice, add a `TtsModelSpec` and `TtsVoiceSpec` in
`app/src/main/java/com/k2fsa/sherpa/onnx/tts/engine/VoiceCatalog.kt`, rebuild once, then push the
matching model folder.

## Build and install

```bash
./gradlew :app:assembleDebug
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
adb shell settings put secure tts_default_synth com.portal.tts
```

On Portal, you can also select it from:

`Settings > System > Languages & input > Text-to-speech output`

## Immortal integration

Immortal uses Portal TTS through Android's standard `TextToSpeech` API. No Immortal code needs to link
against the native TTS libraries directly. If Portal TTS is installed and selected as the device TTS
engine, Immortal can speak welcome messages, time announcements, and chime text offline.

## Benchmarking

Open Portal TTS, go to `Settings`, and run `Voice Performance`. The benchmark synthesizes a short
sample with every installed voice and records:

- realtime factor
- generation time
- audio duration
- time to first audio
- model name

This makes it easy to pick the best-performing voice for Portal hardware.

## Attribution

Portal TTS is a derivative of the Apache-2.0
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) Android TTS engine by the Next-gen Kaldi /
k2-fsa authors. Native libraries such as ONNX Runtime and sherpa-onnx JNI retain their respective
licenses.

See [LICENSE](LICENSE) and [NOTICE](NOTICE) for license details and attribution.

Not affiliated with Meta. "Portal" refers to the Meta Portal hardware this app targets.
