#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/main dist
javac --release 17 -encoding UTF-8 -d build/main src/gbm/*.java
jar --create --file dist/GameBackLogManager.jar --main-class gbm.App --manifest manifest.mf -C build/main . -C resources .
