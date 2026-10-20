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
"$tools/apksigner" sign --ks "$ACTUAL_KEYSTORE" --ks-key-alias "$ACTUAL_KEY_ALIAS" --out "$release_dir/actual-budget-1.0.1-arm64.apk" "$release_dir/actual-aligned.apk"
"$tools/apksigner" verify --verbose "$release_dir/actual-budget-1.0.1-arm64.apk"
expected_certificate="${ACTUAL_CERT_SHA256:-$(tr -d '\r\n' < "$project_dir/docs/SIGNING_CERTIFICATE_SHA256")}"
actual_certificate="$("$tools/apksigner" verify --print-certs "$release_dir/actual-budget-1.0.1-arm64.apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
if [[ "$actual_certificate" != "$expected_certificate" ]]; then
    printf 'Signing certificate mismatch. Restore the retained release key before distributing this APK.\n' >&2
    exit 1
fi
"$tools/zipalign" -c -P 16 4 "$release_dir/actual-budget-1.0.1-arm64.apk"
(cd "$release_dir" && sha256sum actual-budget-1.0.1-arm64.apk > SHA256SUMS)
printf 'Release APK and checksum: %s\n' "$release_dir"
