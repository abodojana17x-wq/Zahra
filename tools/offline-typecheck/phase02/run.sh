#!/usr/bin/env bash
# تحقق تنفيذي حقيقي لمنطق المرحلة 2 (بلا Gradle، بلا Android SDK، بلا شبكة).
# المتطلبات (مرة واحدة):
#   pip install --break-system-packages jdk4py
#   npm pack kotlin-compiler@2.2.10 && tar xzf kotlin-compiler-2.2.10.tgz -C /tmp/kc
# التشغيل: ./tools/offline-typecheck/phase02/run.sh
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
KC_DIR=${KC_DIR:-/tmp/kc/package}
OUT=${OUT:-/tmp/verify-phase02}
export JAVA_HOME=${JAVA_HOME:-$(python3 -c "import jdk4py;print(jdk4py.JAVA_HOME)")}
export PATH="$JAVA_HOME/bin:$PATH"

rm -rf "$OUT"; mkdir -p "$OUT"
"$KC_DIR/bin/kotlinc" -nowarn -jvm-target 17 -d "$OUT" \
  "$ROOT"/app/src/main/java/com/example/zahra/math/*.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/tools/*.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/engines/MathEngine.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/model/ZahraModels.kt \
  "$ROOT"/app/src/main/java/com/example/zahra/intelligence/planner/TaskPlanModels.kt \
  "$ROOT"/tools/offline-typecheck/phase02/SelfCheck.kt

"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT:$KC_DIR/lib/kotlin-stdlib.jar" SelfCheckKt
