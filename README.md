# Piper App for Android
#
# Sample / demo app for the Piper Android TTS library.
# iOS twin: piper-app.
#
# The library itself lives in the sibling `piper-android` repo
# (twin of the iOS piper-objc package). Check out both repos side by side:
#
#   ~/workspace/piper-android      - :piper-engine, :piper-utils, :piper-player
#   ~/workspace/piper-app-android  - this repo (:app)
#
# Build:
#   1. In piper-android: scripts/setup-android-sdk.sh, scripts/fetch-native-deps.sh
#   2. scripts/download-voice.sh [voice-id]   (e.g. en_US-lessac-medium)
#   3. Stage espeak-ng-data into app/src/main/assets/espeak-ng-data/
#      (see piper-android README "espeak-ng-data packaging")
#   4. ./gradlew assembleDebug
#
# On first run the app copies the voice and espeak-ng-data from assets into
# its private filesDir, then builds PiperCreateOptions pointing at them.
#
# License: GPL-3.0, matching piper1-gpl.
