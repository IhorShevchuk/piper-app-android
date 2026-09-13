# Piper App for Android

Piper for Android: offline neural text-to-speech. iOS twin: piper-app.

The engine library lives in the sibling `piper-kotlin` repo
(twin of the iOS piper-objc package). Check out both repos side by side:

    ~/workspace/piper-kotlin      - :piper-engine, :piper-utils, :piper-player
    ~/workspace/piper-app-android  - this repo (:app, :tts-service, :voice-download)

## Modules

- `:app` - the app shell, mirroring the iOS app's MainView flow:
  installed-voices list, voice detail (preview / set active / delete),
  voice catalog downloads, import from file, Help, About.
- `:tts-service` - Android `TextToSpeechService` backed by the JNI
  piper1-gpl engine, so any app on the device can use Piper voices.
- `:voice-download` - Hugging Face `piper-voices` catalog browser with
  resumable downloads, MD5 verification, and per-voice preview.

## Build

1. In piper-kotlin: `scripts/fetch-native-deps.sh`
2. Stage espeak-ng-data: `./scripts/stage-espeak-data.sh`
   (compiles the vendored espeak-ng dictionaries into
   `app/src/main/assets/espeak-ng-data/`; gitignored, ~31 MB)
3. `./gradlew :app:assembleDebug`

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`
(~93 MB with all assets). Install with
`adb install -r app/build/outputs/apk/debug/app-debug.apk`.

On first preview the app stages espeak-ng-data from assets into its
private filesDir; voices download into `<files>/voices/` (shared with
the TTS service).

## Notes

- Speech-rate mapping matches iOS: Android rates are physical
  multipliers (100 = 1x), clamped to 2.2x so `length_scale` never drops
  below 0.45 (PT-BR sibilant protection).
- All user-facing strings live in `res/values/strings.xml`.

License: GPL-3.0, matching piper1-gpl.
