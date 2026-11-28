#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BUILD_TOOLS="$ANDROID_SDK/build-tools/android-15"
ANDROID_JAR="$ANDROID_SDK/platforms/android-35/android.jar"

if [[ ! -f "$ANDROID_JAR" ]]; then
  echo "ERROR: android.jar not found at $ANDROID_JAR"
  exit 1
fi

BUILD_DIR="$SCRIPT_DIR/build"
GEN_DIR="$BUILD_DIR/gen"
OBJ_DIR="$BUILD_DIR/obj"
DEX_DIR="$BUILD_DIR/dex"
DIST_DIR="$SCRIPT_DIR/dist"
KEYSTORE="$SCRIPT_DIR/mirearpet.jks"

rm -rf "$BUILD_DIR"
mkdir -p "$GEN_DIR" "$OBJ_DIR" "$DEX_DIR" "$DIST_DIR"

echo "[1/6] Generating Shizuku AIDL Java stubs..."
for aidl_file in "$SCRIPT_DIR"/aidl/moe/shizuku/server/*.aidl; do
  "$BUILD_TOOLS/aidl" \
    -p"$ANDROID_SDK/platforms/android-35/framework.aidl" \
    -I"$SCRIPT_DIR/aidl" \
    -o"$GEN_DIR" \
    "$aidl_file"
done

echo "[2/6] Compiling & linking Android resources (aapt2)..."
"$BUILD_TOOLS/aapt2" compile --dir "$SCRIPT_DIR/res" -o "$BUILD_DIR/res.zip"
"$BUILD_TOOLS/aapt2" link \
  -o "$BUILD_DIR/unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$SCRIPT_DIR/AndroidManifest.xml" \
  --java "$GEN_DIR" \
  --min-sdk-version 29 \
  --target-sdk-version 35 \
  "$BUILD_DIR/res.zip"

echo "[3/6] Compiling Java sources (JDK 17 -> Java 11 bytecode)..."
JAVA_FILES=()
while IFS= read -r f; do
  JAVA_FILES+=("$f")
done < <(find "$SCRIPT_DIR/src" "$GEN_DIR" -name "*.java" | sort)

javac \
  --release 11 \
  -encoding UTF-8 \
  -classpath "$ANDROID_JAR" \
  -d "$OBJ_DIR" \
  "${JAVA_FILES[@]}"

echo "[4/6] Converting .class bytecode to classes.dex (d8)..."
CLASS_FILES=()
while IFS= read -r f; do
  CLASS_FILES+=("$f")
done < <(find "$OBJ_DIR" -name "*.class" | sort)

"$BUILD_TOOLS/d8" \
  --min-api 29 \
  --lib "$ANDROID_JAR" \
  --output "$DEX_DIR" \
  "${CLASS_FILES[@]}"

echo "[5/6] Packaging classes.dex & aligning APK..."
(cd "$DEX_DIR" && zip -q -u "$BUILD_DIR/unsigned.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f -p 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"

echo "[6/6] Signing APK (v1 + v2 + v3)..."
if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -storepass mirearpet2026 \
    -keypass mirearpet2026 \
    -alias mirearpet \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=MiRearPet, OU=DIY, O=MiRearPet, L=Shanghai, S=Shanghai, C=CN" >/dev/null 2>&1
fi

"$BUILD_TOOLS/apksigner" sign \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --ks "$KEYSTORE" \
  --ks-pass pass:mirearpet2026 \
  --key-pass pass:mirearpet2026 \
  --ks-key-alias mirearpet \
  --out "$DIST_DIR/MiRearPet.apk" \
  "$BUILD_DIR/aligned.apk"

"$BUILD_TOOLS/apksigner" verify --verbose "$DIST_DIR/MiRearPet.apk" | head -n 6
ls -lh "$DIST_DIR/MiRearPet.apk"
echo "DONE: $DIST_DIR/MiRearPet.apk"
