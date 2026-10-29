#!/usr/bin/env bash
# Isolated pure-Kotlin smoke checks, NOT Android/Robolectric/Gradle tests.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
KC_DIR=${KC_DIR:-/tmp/kc/package}
OUT=${OUT:-/tmp/zahra-phase04-check}
export JAVA_HOME=${JAVA_HOME:-$(python3 -c 'import jdk4py; print(jdk4py.JAVA_HOME)')}
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p "$OUT"
"$KC_DIR/bin/kotlinc" -nowarn -jvm-target 17 -d "$OUT" \
  "$ROOT"/app/src/main/java/com/example/zahra/dialogue/*.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/core/ZahraNormalizer.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/intelligence/mood/MoodAnalyzer.kt \
  "$ROOT"/tools/offline-typecheck/phase04/SelfCheck.kt
"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -cp "$OUT:$KC_DIR/lib/kotlin-stdlib.jar" SelfCheckKt
