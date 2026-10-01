#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

# Prefer the Liberica NIK/JavaFX installation used by the packaged build.
# build/librca is a symlink to its java executable.
if [[ -z "${JAVA_HOME:-}" && -L "$SCRIPT_DIR/librca" ]]; then
  LIBERICA_JAVA="$(readlink "$SCRIPT_DIR/librca")"
  if [[ "$LIBERICA_JAVA" != /* ]]; then
    LIBERICA_JAVA="$SCRIPT_DIR/$LIBERICA_JAVA"
  fi
  export JAVA_HOME="$(cd -- "$(dirname -- "$LIBERICA_JAVA")/.." && pwd)"
fi

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

command -v native-image >/dev/null 2>&1 || {
  echo "native-image was not found in the selected Liberica JDK or on PATH." >&2
  exit 1
}

echo "Using Java: $(command -v java)"
echo "Using native-image: $(command -v native-image)"

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

native-image \
  -O3 \
  -march=compatibility \
  -H:+UnlockExperimentalVMOptions \
  -H:ReflectionConfigurationFiles=build/reflect-config.json \
  -H:JNIConfigurationFiles=build/jni-config-macos.json \
  -H:ResourceConfigurationFiles=build/resource-config.json \
  --enable-native-access=javafx.graphics \
  '--initialize-at-run-time=com.sun.jna,com.sun.jna.NativeLibrary,com.sun.jna.CallbackReference,jamiebalfour.helpers.MacApplicationMenuJNA' \
  '--initialize-at-run-time=com.sun.glass.ui.mac.MacAccessible,com.sun.glass.ui.mac.MacAccessible$MacAttribute,com.sun.glass.ui.mac.MacAccessible$MacAction,com.sun.glass.ui.mac.MacAccessible$MacRole,com.sun.glass.ui.mac.MacAccessible$MacSubrole,com.sun.glass.ui.mac.MacAccessible$MacNotification,com.sun.glass.ui.mac.MacAccessible$MacOrientation,com.sun.glass.ui.mac.MacAccessible$MacText,com.sun.glass.ui.mac.MacGestureSupport' \
  -H:Name=build/native/zide-aarch64 \
  -jar build/zide.jar \
  --no-fallback

test -x build/native/zide-aarch64
file build/native/zide-aarch64
echo "Built build/native/zide-aarch64"
