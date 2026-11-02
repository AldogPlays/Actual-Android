#!/usr/bin/env bash
set -euo pipefail
# Passwords are prompted by apksigner. Never store a signing key in the repository.
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}"
: "${ACTUAL_KEYSTORE:?Set ACTUAL_KEYSTORE to an existing private keystore outside this repository}"
: "${ACTUAL_KEY_ALIAS:?Set ACTUAL_KEY_ALIAS to its signing alias}"
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
expected_certificate="${ACTUAL_CERT_SHA256:-$(tr -d '\r\n' < "$project_dir/docs/SIGNING_CERTIFICATE_SHA256")}"
[[ "$expected_certificate" =~ ^[0-9a-f]{64}$ ]] || { printf 'Invalid signing certificate pin.\n' >&2; exit 1; }
version="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts)"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { printf 'Cannot read release version.\n' >&2; exit 1; }
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease
release_dir="$project_dir/release-artifacts"
mkdir -p "$release_dir"
tools="$ANDROID_HOME/build-tools/${ANDROID_BUILD_TOOLS_VERSION:-36.0.0}"
staging="$(mktemp -d "$release_dir/.signing.XXXXXX")"
trap 'rm -rf -- "$staging"' EXIT
apk_name="actual-budget-$version-arm64.apk"
"$tools/zipalign" -P 16 -f 4 app/build/outputs/apk/release/app-release-unsigned.apk "$staging/aligned.apk"
password_args=()
if [[ -n "${ACTUAL_KS_PASS_FILE:-}" ]]; then
    password_args=(--ks-pass "file:$ACTUAL_KS_PASS_FILE")
fi
"$tools/apksigner" sign --ks "$ACTUAL_KEYSTORE" --ks-key-alias "$ACTUAL_KEY_ALIAS" "${password_args[@]}" --out "$staging/$apk_name" "$staging/aligned.apk"
"$tools/apksigner" verify --verbose "$staging/$apk_name"
actual_certificate="$("$tools/apksigner" verify --print-certs "$staging/$apk_name" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
if [[ "$actual_certificate" != "$expected_certificate" ]]; then
    printf 'Signing certificate mismatch. Restore the retained release key before distributing this APK.\n' >&2
    exit 1
fi
"$tools/zipalign" -c -P 16 4 "$staging/$apk_name"
mv -- "$staging/$apk_name" "$release_dir/$apk_name"
if [[ -f "$staging/$apk_name.idsig" ]]; then
    mv -- "$staging/$apk_name.idsig" "$release_dir/$apk_name.idsig"
fi
(cd "$release_dir" && sha256sum "$apk_name" > "$apk_name.sha256")
cp -- "$release_dir/$apk_name.sha256" "$release_dir/SHA256SUMS"
printf 'Release APK and checksum: %s\n' "$release_dir"
