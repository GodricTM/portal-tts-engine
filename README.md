# Sherpa-ONNX TTS Engine for Portal

A standalone Android TTS engine built on [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) that provides offline neural text-to-speech on Meta Portal devices (no Google Play Services required).

## What this is

A companion app to the [Immortal launcher](https://github.com/starbrightlab/immortal). It registers as an Android `TextToSpeech` engine so any app on the Portal (Immortal's chimes, welcome overlay, spoken time) can use high-quality offline voices without cloud services.

## Features

- **Kokoro multilingual v1.0** — 28 English voices (US + GB), VERY_HIGH quality
- **Piper Romanian Mihai** — Romanian voice, HIGH quality
- Fully offline (ONNX runtime, no network needed after model install)
- ARM64 native libs (Portal hardware)
- Standard Android TTS API (works with any app that uses `TextToSpeech`)

## Voice models

Voice models (.onnx) are not bundled (too large for git). Download from [sherpa-onnx releases](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models) and push to the device:

```bash
adb push kokoro-multi-lang-v1_0/ /sdcard/Android/data/com.portal.tts/files/
adb push vits-piper-ro_RO-mihai-medium/ /sdcard/Android/data/com.portal.tts/files/
```

The engine auto-detects installed models via `VoiceCatalog`.

## Building

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After install: Settings > Accessibility > Text-to-speech > select "Portal TTS (Sherpa-ONNX)".

## License & attribution

**Apache-2.0.** This app is a derivative of the [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
Android TTS engine (Apache-2.0) by the Next-gen Kaldi / k2-fsa authors. It is repackaged as
`com.portal.tts` with Portal-specific branding, voice auto-detection, and sideload tweaks. See
[LICENSE](LICENSE) and [NOTICE](NOTICE) for the full license and a summary of changes. The bundled
native libraries (onnxruntime, sherpa-onnx-jni) remain under their respective licenses.

Not affiliated with Meta. "Portal" refers to the Meta Portal hardware this app targets.
