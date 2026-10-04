#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

export JAVA_HOME="/Library/Java/LibericaNativeImageKit/liberica-vm-full-25.0.4-openjdk25/Contents/Home"

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

NATIVE_IMAGE="$JAVA_HOME/bin/native-image"

echo "Using Java: $(command -v java)"
echo "Using native-image: $NATIVE_IMAGE"

if ! "$NATIVE_IMAGE" --version 2>&1 | grep -q 'Liberica\|BellSoft'; then
  echo "The selected native-image is not Liberica Native Image Kit." >&2
  echo "Refusing to build with a different GraalVM distribution." >&2
  exit 1
fi

for required_file in \
  build/zide.jar \
  build/reflect-config.json \
  build/jni-config-macos.json \
  build/resource-config.json; do
  if [[ ! -f "$required_file" ]]; then
    echo "Required file not found: $required_file" >&2
    exit 1
  fi
done

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This script builds the macOS native image and must run on macOS." >&2
  exit 1
fi

mkdir -p build/native

"$NATIVE_IMAGE" \
  -O3 \
  -march=compatibility \
  -H:+UnlockExperimentalVMOptions \
  -H:ReflectionConfigurationFiles=build/reflect-config.json \
  -H:JNIConfigurationFiles=build/jni-config-macos.json \
  -H:ResourceConfigurationFiles=build/resource-config.json \
  -J--module-path="$JAVA_HOME/jmods" \
  -J--add-modules=javafx.controls,javafx.fxml,javafx.swing,javafx.web \
  --enable-native-access=javafx.graphics,ALL-UNNAMED \
  --add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED \
  '--initialize-at-run-time=jamiebalfour.balflaf_fx.MacNativeWindowSupport,com.sun.glass.ui.mac.MacAccessible,com.sun.glass.ui.mac.MacAccessible$MacAttribute,com.sun.glass.ui.mac.MacAccessible$MacAction,com.sun.glass.ui.mac.MacAccessible$MacRole,com.sun.glass.ui.mac.MacAccessible$MacSubrole,com.sun.glass.ui.mac.MacAccessible$MacNotification,com.sun.glass.ui.mac.MacAccessible$MacOrientation,com.sun.glass.ui.mac.MacAccessible$MacText,com.sun.glass.ui.mac.MacGestureSupport' \
  -H:Name=build/native/zide-aarch64 \
  -jar build/zide.jar \
  --no-fallback

test -x build/native/zide-aarch64
file build/native/zide-aarch64
echo "Built build/native/zide-aarch64"

# Package the native executable as a macOS application bundle.
APP_BUNDLE="build/ZIDE.app"
APP_CONTENTS="$APP_BUNDLE/Contents"
APP_MACOS="$APP_CONTENTS/MacOS"
APP_RESOURCES="$APP_CONTENTS/Resources"
APP_PLIST_SOURCE=""

if [[ -f package/ZIDE.app/Contents/Info.plist ]]; then
  APP_PLIST_SOURCE="package/ZIDE.app/Contents/Info.plist"
elif [[ -f build/Info.plist ]]; then
  APP_PLIST_SOURCE="build/Info.plist"
fi

rm -rf "$APP_BUNDLE"
mkdir -p "$APP_MACOS" "$APP_RESOURCES"
cp build/native/zide-aarch64 "$APP_MACOS/ZIDE"
chmod 755 "$APP_MACOS/ZIDE"

if [[ -n "$APP_PLIST_SOURCE" ]]; then
  cp "$APP_PLIST_SOURCE" "$APP_CONTENTS/Info.plist"
else
  cat > "$APP_CONTENTS/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleDevelopmentRegion</key><string>English</string>
  <key>CFBundleExecutable</key><string>ZIDE</string>
  <key>CFBundleIdentifier</key><string>jamiebalfour.zide.core</string>
  <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
  <key>CFBundleName</key><string>ZIDE</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>1.0</string>
  <key>CFBundleVersion</key><string>1.0</string>
  <key>LSMinimumSystemVersion</key><string>10.11</string>
  <key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
PLIST
fi

if [[ -f ZIDE.icns ]]; then
  cp ZIDE.icns "$APP_RESOURCES/ZIDE.icns"
  /usr/libexec/PlistBuddy -c 'Delete :CFBundleIconFile' "$APP_CONTENTS/Info.plist" 2>/dev/null || true
  /usr/libexec/PlistBuddy -c 'Add :CFBundleIconFile string ZIDE.icns' "$APP_CONTENTS/Info.plist"
fi

/usr/bin/plutil -lint "$APP_CONTENTS/Info.plist"
test -x "$APP_MACOS/ZIDE"

if [[ -n "${APPLE_SIGNING_IDENTITY:-}" ]]; then
  codesign --force --deep --options runtime --sign "$APPLE_SIGNING_IDENTITY" "$APP_BUNDLE"
fi

if [[ -n "${APPLE_NOTARIZE_PROFILE:-}" ]]; then
  xcrun notarytool submit "$APP_BUNDLE" --keychain-profile "$APPLE_NOTARIZE_PROFILE" --wait
  xcrun stapler staple "$APP_BUNDLE"
fi

echo "Built $APP_BUNDLE"
