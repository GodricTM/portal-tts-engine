# Portal TTS workspace consolidation

`portal_tts_engine/` is the single source of truth for Portal TTS.

The older `portal-tts-src/` folder was a full upstream sherpa-onnx checkout used during the initial
forking work. It is now reference material only. Do not build or install Portal TTS from that folder.

Assets migrated from `portal-tts-src/`:

- `tts_icon_pack/` -> `app/src/main/res/` and `artifacts/tts_icon_pack/`
- `portal_tts.png` -> `artifacts/portal_tts.png`

Build and install from this folder:

```bash
./gradlew :app:assembleDebug
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
adb shell settings put secure tts_default_synth com.portal.tts
```
