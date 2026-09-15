#!/bin/bash
set -e
cd "$(dirname "$0")"

JPACKAGE="${JPACKAGE:-$(command -v jpackage)}"
APP_INPUT="../build/package"

# Where your macOS JavaFX native libs live:
JFX_NATIVE_SRC="${JFX_NATIVE_SRC:-/Users/jamiebalfour/Sync/Programs/JARs/jfx}"

# Where they will live inside the packaged app:
JFX_NATIVE_DEST="${APP_INPUT}/jfx"

mkdir -p "$JFX_NATIVE_DEST"
if [ -d "$JFX_NATIVE_SRC" ] && [ "$(cd "$JFX_NATIVE_SRC" && pwd -P)" != "$(cd "$JFX_NATIVE_DEST" && pwd -P)" ]; then
  cp -f "$JFX_NATIVE_SRC"/*.{dylib,jar} "$JFX_NATIVE_DEST"/
fi

if [ -d "../package/ZIDE.app" ]; then
  echo "ZIDE.app exists"
  rm -r ../package/ZIDE.app
else
  echo "ZIDE.app does NOT exist"
fi

"$JPACKAGE" \
  --type app-image \
  --name ZIDE \
  --input "$APP_INPUT" \
  --main-jar zide.jar \
  --main-class jamiebalfour.zide.core.ZIDE \
  --dest ../package \
  --java-options "-Djava.library.path=\$APPDIR/jfx --module-path=\$APPDIR/jfx --add-modules=javafx.controls,javafx.fxml,javafx.swing" \
  --icon ../ZIDE.icns
