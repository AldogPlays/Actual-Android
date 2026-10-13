#!/usr/bin/env bash
set -euo pipefail
# Passwords are prompted by apksigner. Never store a signing key in the repository.
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}"
: "${ACTUAL_KEYSTORE:?Set ACTUAL_KEYSTORE to an existing private keystore outside this repository}"
: "${ACTUAL_KEY_ALIAS:?Set ACTUAL_KEY_ALIAS to its signing alias}"
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
./gradlew testDebugUnitTest lintDebug assembleRelease
release_dir="$project_dir/release-artifacts"
mkdir -p "$release_dir"
tools="$ANDROID_HOME/build-tools/36.0.0"
"$tools/zipalign" -P 16 -f 4 app/build/outputs/apk/release/app-release-unsigned.apk "$release_dir/actual-aligned.apk"
"$tools/apksigner" sign --ks "$ACTUAL_KEYSTORE" --ks-key-alias "$ACTUAL_KEY_ALIAS" --out "$release_dir/actual-budget-1.0.0-arm64.apk" "$release_dir/actual-aligned.apk"
"$tools/apksigner" verify --verbose "$release_dir/actual-budget-1.0.0-arm64.apk"
"$tools/zipalign" -c -P 16 4 "$release_dir/actual-budget-1.0.0-arm64.apk"
(cd "$release_dir" && sha256sum actual-budget-1.0.0-arm64.apk > SHA256SUMS)
printf 'Release APK and checksum: %s\n' "$release_dir"
