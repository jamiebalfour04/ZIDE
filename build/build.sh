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
