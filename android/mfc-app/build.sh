#!/usr/bin/env bash
# Rebuilds the mfc-app APK (Java UI + AIDL stubs + assets + libmfcbridge.so)
# from src/ + assets/ + lib/, and installs it if a device is attached.
#
# No build system was committed for the app itself (only
# android/mifare-native/build.sh, which rebuilds libmfcbridge.so). The APKs
# already sitting under build/ were built by hand with this same toolchain
# and predate any source change made after they were generated -- rerun
# this script whenever src/ changes.
#
# Requires: an Android SDK with cmdline-tools + a platform + build-tools
# (see docs/tms_protocol.md section 6: this project was built against
# platform 34 under ~/Android/Sdk) and a JDK (javac) on PATH.
set -euo pipefail
cd "$(dirname "$0")"

: "${ANDROID_SDK:=$HOME/Android/Sdk}"
: "${PLATFORM:=34}"

ANDROID_JAR="$ANDROID_SDK/platforms/android-$PLATFORM/android.jar"
if [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: $ANDROID_JAR not found. Set ANDROID_SDK/PLATFORM, or install" >&2
    echo "  platform $PLATFORM: \"\$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager\" \"platforms;android-$PLATFORM\"" >&2
    exit 1
fi

BUILD_TOOLS_DIR=$(ls -d "$ANDROID_SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)
if [ -z "$BUILD_TOOLS_DIR" ]; then
    echo "ERROR: no build-tools found under $ANDROID_SDK/build-tools/" >&2
    echo "  install one: \"\$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager\" \"build-tools;34.0.0\"" >&2
    exit 1
fi
AAPT2="${BUILD_TOOLS_DIR}aapt2"
D8="${BUILD_TOOLS_DIR}d8"
ZIPALIGN="${BUILD_TOOLS_DIR}zipalign"
APKSIGNER="${BUILD_TOOLS_DIR}apksigner"
echo "using build-tools: $BUILD_TOOLS_DIR"

OUT=build/out
rm -rf "$OUT" build/classes
mkdir -p "$OUT" build/classes

echo "javac ..."
find src -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
    -d build/classes @"$OUT/sources.txt"

echo "d8 (dex) ..."
find build/classes -name '*.class' > "$OUT/classes.txt"
"$D8" --release --min-api 26 --output "$OUT" @"$OUT/classes.txt"

echo "aapt2 link (manifest + assets, no res/) ..."
"$AAPT2" link -o "$OUT/app-unsigned.apk" \
    -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    -A assets \
    --min-sdk-version 26 --target-sdk-version 27 \
    -0 dic -0 lz4

echo "adding classes.dex + native libs ..."
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
cp "$OUT/app-unsigned.apk" "$WORK/app.apk"
cp "$OUT/classes.dex" "$WORK/classes.dex"
mkdir -p "$WORK/lib/arm64-v8a"
cp lib/arm64-v8a/libmfcbridge.so "$WORK/lib/arm64-v8a/"
( cd "$WORK" && zip -q app.apk classes.dex lib/arm64-v8a/libmfcbridge.so )
cp "$WORK/app.apk" "$OUT/app-unsigned.apk"

echo "zipalign ..."
"$ZIPALIGN" -f 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"

echo "apksigner (debug keystore) ..."
"$APKSIGNER" sign --ks build/debug.keystore --ks-pass pass:android \
    --out "$OUT/app-signed.apk" "$OUT/app-aligned.apk"

echo
echo "built: $OUT/app-signed.apk"

if command -v adb >/dev/null 2>&1 && adb get-state >/dev/null 2>&1; then
    echo "device found, installing ..."
    adb install -r "$OUT/app-signed.apk"
    echo "installed. Launch \"Iceman MFC\" on the device."
else
    echo "no adb device detected -- install manually:"
    echo "  adb install -r $OUT/app-signed.apk"
fi
