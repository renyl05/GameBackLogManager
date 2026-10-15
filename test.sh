#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
./build.sh
mkdir -p build/test
javac --release 17 -encoding UTF-8 -cp build/main -d build/test test/gbm/*.java
java -cp build/main:build/test gbm.IntegrationTest
java -cp build/main:build/test gbm.ProviderTest

if [ -n "${GBM_TEST_H2_JAR:-}" ]; then
  java -cp "build/main:build/test:$GBM_TEST_H2_JAR" gbm.DatabaseTest
fi
