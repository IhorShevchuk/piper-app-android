#!/usr/bin/env bash
# Stage the compiled espeak-ng-data directory into the app's assets.
#
# The native engine needs the compiled espeak-ng data at runtime; the app
# bundles it under app/src/main/assets/espeak-ng-data/ and
# :tts-service's EspeakDataInstaller copies it to <files>/espeak-ng-data
# on first launch.
#
# The data (~30 MB) is git-ignored - run this script after cloning,
# before building the app.
#
# Usage:
#   scripts/stage-espeak-data.sh [path-to-espeak-ng-source]
# Default source: ../piper-android/third-party/espeak-ng (sibling repo).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ESPEAK_SRC="${1:-$REPO_ROOT/../piper-android/third-party/espeak-ng}"
DEST="$REPO_ROOT/app/src/main/assets/espeak-ng-data"

if [ ! -f "$ESPEAK_SRC/CMakeLists.txt" ]; then
    echo "error: espeak-ng source not found at $ESPEAK_SRC" >&2
    echo "pass the path explicitly: $0 <path-to-espeak-ng>" >&2
    exit 1
fi
command -v cmake >/dev/null || { echo "error: cmake is required" >&2; exit 1; }

SCRATCH="$(mktemp -d)"
trap 'rm -rf "$SCRATCH"' EXIT

echo "building espeak-ng data in $SCRATCH ..."
cmake -S "$ESPEAK_SRC" -B "$SCRATCH/build" \
    -DCMAKE_BUILD_TYPE=Release \
    -DENABLE_TESTS=OFF -DWITH_FUZZER=OFF >/dev/null
cmake --build "$SCRATCH/build" -j"$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 2)"

DATA="$SCRATCH/build/espeak-ng-data"
for f in phondata phonindex phontab intonations; do
    [ -e "$DATA/$f" ] || { echo "error: $f missing from compiled data" >&2; exit 1; }
done

rm -rf "$DEST"
mkdir -p "$(dirname "$DEST")"
cp -r "$DATA" "$DEST"
echo "staged $(du -sh "$DEST" | cut -f1) to $DEST"
