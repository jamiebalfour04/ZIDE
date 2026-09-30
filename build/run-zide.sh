#!/bin/sh

set -eu

BUILD_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$BUILD_DIR/.." && pwd)

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA=$(command -v java || true)
fi

if [ -z "$JAVA" ]; then
  echo "Java was not found. Set JAVA_HOME or add java to PATH." >&2
  exit 1
fi

if [ -n "${JAVAFX_HOME:-}" ]; then
  JAVAFX_DIR=$JAVAFX_HOME
else
  JAVAFX_DIR="$PROJECT_DIR/lib/jfx21"
  if [ ! -d "$JAVAFX_DIR" ]; then
    JAVAFX_DIR="$PROJECT_DIR/package/ZIDE.app/Contents/app/jfx"
    if [ ! -d "$JAVAFX_DIR" ]; then
      JAVAFX_DIR="$BUILD_DIR/javafx"
    fi
  fi
fi

if [ ! -f "$JAVAFX_DIR/javafx.controls.jar" ]; then
  echo "JavaFX was not found at $JAVAFX_DIR." >&2
  echo "Set JAVAFX_HOME to the directory containing javafx.controls.jar." >&2
  exit 1
fi

BALFLAFFX_PATH=""
for candidate in \
  "$PROJECT_DIR/lib/BalfLafFX.jar" \
  "$PROJECT_DIR/../BalfLafFX/out/production/BalfLafFX" \
  "$BUILD_DIR/BalfLafFX.jar" \
  "$PROJECT_DIR/../BalfLafFX/build/dist/BalfLafFX.jar" \
  "$PROJECT_DIR/../BalfLafFX/build/BalfLafFX.jar"; do
  if [ -d "$candidate" ] || [ -f "$candidate" ]; then
    BALFLAFFX_PATH=$candidate
    break
  fi
done

if [ -z "$BALFLAFFX_PATH" ]; then
  echo "BalfLafFX was not found. Build BalfLafFX or place it in $BUILD_DIR." >&2
  exit 1
fi

exec "$JAVA" \
  --module-path "$JAVAFX_DIR" \
  --add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED \
  --add-modules javafx.controls,javafx.fxml,javafx.swing,javafx.web,jdk.jdi,jdk.httpserver \
  --enable-native-access=javafx.graphics,javafx.web,ALL-UNNAMED \
  -Djava.library.path="$JAVAFX_DIR" \
  -Djavafx.suppressPreviewWarning=true \
  -cp "$BALFLAFFX_PATH:$BUILD_DIR/zide.jar:$BUILD_DIR/zide-plugin-api.jar" \
  jamiebalfour.zide.core.ZIDE "$@"
