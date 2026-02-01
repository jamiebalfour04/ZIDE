#!/bin/bash
set -e
cd "$(dirname "$0")"

JPACKAGE="/Users/jamiebalfour/.sdkman/candidates/java/24.ea.3-graal/bin/jpackage"
APP_INPUT="../build/package"

# Where your macOS JavaFX native libs live:
JFX_NATIVE_SRC="/Users/jamiebalfour/Sync/Programs/JARs/jfx"

# Where they will live inside the packaged app:
JFX_NATIVE_DEST="${APP_INPUT}/jfx"

mkdir -p "$JFX_NATIVE_DEST"
cp -f "$JFX_NATIVE_SRC"/*.{dylib,jar} "$JFX_NATIVE_DEST"/

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
  --main-class jamiebalfour.zide.ZIDEMain \
  --dest ../package \
  --java-options "-Djava.library.path=\$APPDIR/jfx --module-path=\$APPDIR/jfx --add-modules=javafx.controls,javafx.fxml,javafx.swing" \
  --icon ../ZIDE.icns &